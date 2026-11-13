#!/bin/bash
# TASK-017 Donation 冒烟（§65）：checkout→金额/币种校验→mock-complete→幂等→
# webhook 拒签→payment.failed 事件分发→history 隔离→审计日志。
set -e
API=http://localhost:8082
RUN="smoke-don-$(date +%s)"
EMAIL="${RUN}@test.dev"
OTHER="${RUN}-other@test.dev"
PASSWORD="StrongPassword123!"
# 与 application.yml 默认值一致（PAYMENT_MOCK_SIGNING_SECRET 未设置时）
SECRET="${PAYMENT_MOCK_SIGNING_SECRET:-dev-payment-secret-please-change-me}"
PASS=0
FAIL=0

jget() {
  python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('$1',''))"
}

check() {
  if [ "$2" = "$3" ]; then
    PASS=$((PASS + 1))
    echo "PASS $1"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL $1 (expected=$2 actual=$3)"
  fi
}

mysql_q() {
  mysql -h127.0.0.1 -P3310 -ugrowthkids -pgrowthkids growthkids -N -B -e "$1" 2>/dev/null
}

TOKEN=$(curl -s -X POST $API/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"displayName\":\"SmokeDonation\"}" \
  | jget accessToken)
OTHER_TOKEN=$(curl -s -X POST $API/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OTHER\",\"password\":\"$PASSWORD\",\"displayName\":\"SmokeOther\"}" \
  | jget accessToken)
AUTH="Authorization: Bearer $TOKEN"
OTHER_AUTH="Authorization: Bearer $OTHER_TOKEN"

echo "== 1. checkout $5 → 201 pending =="
HTTP=$(curl -s -o /tmp/dn-checkout.json -w '%{http_code}' -X POST $API/api/v1/donations/checkout \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"amount":5}')
check "checkout-status" 201 "$HTTP"
TX=$(jget transactionId < /tmp/dn-checkout.json)
PROVIDER=$(jget provider < /tmp/dn-checkout.json)
CURRENCY=$(jget currency < /tmp/dn-checkout.json)
check "checkout-provider" mock "$PROVIDER"
check "checkout-currency" USD "$CURRENCY"
check "checkout-tx-len" 41 "${#TX}"
check "db-pending" pending "$(mysql_q "SELECT status FROM payments WHERE transaction_id='$TX'")"
check "db-type" donation "$(mysql_q "SELECT payment_type FROM payments WHERE transaction_id='$TX'")"

echo "== 2. 金额/币种校验 =="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/donations/checkout \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"amount":0.3}')
check "amount-too-small" 400 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/donations/checkout \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"amount":20000}')
check "amount-too-large" 400 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/donations/checkout \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"amount":3.333}')
check "amount-scale" 400 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/donations/checkout \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"amount":5,"currency":"EUR"}')
check "currency-eur" 400 "$HTTP"

echo "== 3. mock-complete → succeeded（幂等） =="
HTTP=$(curl -s -o /tmp/dn-complete.json -w '%{http_code}' -X POST $API/api/v1/donations/mock-complete/$TX -H "$AUTH")
check "complete-status" 200 "$HTTP"
check "complete-succeeded" succeeded "$(jget status < /tmp/dn-complete.json)"
check "complete-amount" 5.0 "$(jget amount < /tmp/dn-complete.json)"
python3 -c "import json;d=json.load(open('/tmp/dn-complete.json'));assert d['paidAt']" && check "complete-paidAt" ok ok
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/donations/mock-complete/$TX -H "$AUTH")
check "complete-replay" 200 "$HTTP"
check "db-single-payment" 1 "$(mysql_q "SELECT COUNT(*) FROM payments WHERE transaction_id='$TX'")"
check "db-single-log" 1 "$(mysql_q "SELECT COUNT(*) FROM webhook_logs WHERE transaction_id='$TX' AND processed=1")"

echo "== 4. ownership：他人单据 404 =="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/donations/mock-complete/$TX -H "$OTHER_AUTH")
check "foreign-complete" 404 "$HTTP"

echo "== 5. webhook 拒签 400（含垃圾非 JSON 回调） =="
PAYLOAD='{"provider":"mock","eventType":"payment.succeeded","transactionId":"'"$TX"'","amount":5,"currency":"USD"}'
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/payments/webhook/mock \
  -H 'Content-Type: application/json' -d "$PAYLOAD")
check "webhook-no-signature" 400 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/payments/webhook/mock \
  -H 'Content-Type: text/plain' -d 'not-json-garbage')
check "webhook-garbage-payload" 400 "$HTTP"

echo "== 6. history：1 条 succeeded =="
HTTP=$(curl -s -o /tmp/dn-history.json -w '%{http_code}' $API/api/v1/donations -H "$AUTH")
check "history-status-code" 200 "$HTTP"
check "history-total" 1 "$(python3 -c "import json;d=json.load(open('/tmp/dn-history.json'));print(d['pagination']['total'])")"
check "history-item-status" succeeded "$(python3 -c "import json;d=json.load(open('/tmp/dn-history.json'));print(d['items'][0]['status'])")"

echo "== 7. payment.failed 事件（同端点按 payment_type 分发） =="
TX2=$(curl -s -X POST $API/api/v1/donations/checkout \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"amount":20}' | jget transactionId)
FAILED_PAYLOAD='{"provider":"mock","eventType":"payment.failed","transactionId":"'"$TX2"'","amount":20,"currency":"USD"}'
SIGNATURE=$(printf '%s' "$FAILED_PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.*= //')
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/payments/webhook/mock \
  -H "X-Signature: $SIGNATURE" -H 'Content-Type: application/json' -d "$FAILED_PAYLOAD")
check "webhook-failed-event" 200 "$HTTP"
check "db-failed-status" failed "$(mysql_q "SELECT status FROM payments WHERE transaction_id='$TX2'")"

echo "== 8. 未登录 401 =="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/donations/checkout \
  -H 'Content-Type: application/json' -d '{"amount":5}')
check "unauth-checkout" 401 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/donations)
check "unauth-history" 401 "$HTTP"

echo "== 9. 他人 history 隔离 =="
check "other-history-total" 0 "$(curl -s $API/api/v1/donations -H "$OTHER_AUTH" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['pagination']['total'])")"

echo
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
