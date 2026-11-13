#!/bin/bash
set -e
API=http://localhost:8082
EMAIL="smoke-mem-$(date +%s)@test.dev"
TOKEN=$(curl -s -X POST $API/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"StrongPassword123!\",\"displayName\":\"SmokeMembership\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"

# 1) 套餐列表公开：free + pro 价格与权益
PLANS=$(curl -s $API/api/v1/membership/plans)
echo "1) plans public: $(echo "$PLANS" | python3 -c "import sys,json;p=json.load(sys.stdin);print(len(p), p[0]['code'], p[1]['code'], p[1]['priceAmount'], p[1]['permissions']['childLimit'])") (expect 2 free pro 4.99 5)"

# 2) 新用户默认 free
ME=$(curl -s $API/api/v1/membership -H "$AUTH")
echo "2) default: $(echo "$ME" | python3 -c "import sys,json;m=json.load(sys.stdin);print(m['plan'], m['permissions']['childLimit'], m['permissions']['photoLimit'])") (expect free 1 100)"

# 3) Free 限额：第 1 个孩子 201，第 2 个 403
CHILD_ID=$(curl -s -X POST $API/api/v1/children -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"SmokeKid","gender":"male","birthDate":"2025-01-01"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
L2=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Second","gender":"male","birthDate":"2025-03-01"}')
echo "3) free child limit: first=ok($CHILD_ID) second=$L2 (expect 403)"

# 4) 订阅结算 → pending payment
SUB=$(curl -s -X POST $API/api/v1/membership/subscribe -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"planCode":"pro"}')
TX=$(echo "$SUB" | python3 -c "import sys,json;print(json.load(sys.stdin)['transactionId'])")
PAY_STATUS=$(mysql -h 127.0.0.1 -P 3310 -ugrowthkids -pgrowthkids growthkids -N -e \
  "SELECT status FROM payments WHERE transaction_id='$TX'")
echo "4) subscribe tx=$TX payment=$PAY_STATUS (expect pending)"

# 5) mock-complete → pro 激活 + 到期时间
MC=$(curl -s -X POST $API/api/v1/membership/mock-complete/$TX -H "$AUTH")
echo "5) after mock-complete: $(echo "$MC" | python3 -c "import sys,json;m=json.load(sys.stdin);print(m['plan'], m['status'], m['expiresAt'][:10], 'childLimit', m['permissions']['childLimit'])") (expect pro active +1月 5)"

# 6) Pro 限额：第 2 个孩子 201
P2=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Second","gender":"male","birthDate":"2025-03-01"}')
echo "6) pro second child: $P2 (expect 201)"

# 7) webhook 错误签名 → 400
WB=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/payments/webhook/mock \
  -H 'Content-Type: application/json' -H 'X-Signature: deadbeef' \
  -d '{"provider":"mock","eventType":"payment.succeeded","transactionId":"x"}')
echo "7) webhook bad signature: $WB (expect 400)"

# 8) 取消 → 回落 free；第 3 个孩子 403
CANCEL=$(curl -s -X POST $API/api/v1/membership/cancel -H "$AUTH")
C3=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Third","gender":"male","birthDate":"2025-05-01"}')
echo "8) cancel -> $(echo "$CANCEL" | python3 -c "import sys,json;print(json.load(sys.stdin)['plan'])") third-child=$C3 (expect free 403)"

# 9) 重订阅 → 201（可再次升级）
RE=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/membership/subscribe -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"planCode":"pro"}')
echo "9) re-subscribe: $RE (expect 201)"
echo "SMOKE DONE"
