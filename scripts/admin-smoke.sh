#!/bin/bash
# TASK-019 Admin 冒烟（§77/§110）：admin 引导登录→权限门禁（匿名/普通用户）→
# 用户侧造数（孩子/记录/会员/捐赠）→总览统计→用户/孩子/支付/日志列表→
# 角色变更（审计写入/旧 token 403/重登生效）→支付过滤与非法值校验。
# 用法：API=http://localhost:8092 bash scripts/admin-smoke.sh
set -e
API="${API:-http://localhost:8092}"
RUN="smoke-ad-$(date +%s)"
USER_EMAIL="${RUN}@test.dev"
ROLE_EMAIL="${RUN}-role@test.dev"
PASSWORD="StrongPassword123!"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@growthkids.dev}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin123!}"
PASS=0
FAIL=0

jget() {
  python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('$1',''))"
}

jcode() {
  python3 -c "import json;print(json.load(open('$1'))['error']['code'])"
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

echo "== 0. admin 引导账号登录（AdminBootstrap） =="
ADMIN_TOKEN=$(curl -s -X POST $API/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jget accessToken)
check "admin-login" ok "$([ -n "$ADMIN_TOKEN" ] && echo ok)"
ADMIN_AUTH="Authorization: Bearer $ADMIN_TOKEN"

echo "== 1. 权限门禁：匿名 401 / 普通用户 403 =="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/admin/dashboard)
check "anonymous-401" 401 "$HTTP"
TOKEN=$(curl -s -X POST $API/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$PASSWORD\",\"displayName\":\"SmokeAdminUser\"}" | jget accessToken)
AUTH="Authorization: Bearer $TOKEN"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/admin/dashboard -H "$AUTH")
check "plain-user-403" 403 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/admin/users -H "$AUTH")
check "plain-user-users-403" 403 "$HTTP"

echo "== 2. 用户侧造数：孩子/记录/会员/捐赠 =="
CHILD_ID=$(curl -s -X POST $API/api/v1/children -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"冒烟后台娃","gender":"female","birthDate":"2024-05-01"}' | jget id)
check "child-created" ok "$([ -n "$CHILD_ID" ] && echo ok)"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/growth-records \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"recordDate\":\"$(date +%Y)-06-01\",\"heightCm\":88.5,\"weightKg\":12.3}")
check "record-created" 201 "$HTTP"
TX=$(curl -s -X POST $API/api/v1/membership/subscribe -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"planCode":"pro"}' | jget transactionId)
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/membership/mock-complete/$TX -H "$AUTH")
check "membership-mock-complete" 200 "$HTTP"
TX=$(curl -s -X POST $API/api/v1/donations/checkout -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"amount":5}' | jget transactionId)
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/donations/mock-complete/$TX -H "$AUTH")
check "donation-mock-complete" 200 "$HTTP"

echo "== 3. 总览统计（§77 全维度） =="
HTTP=$(curl -s -o /tmp/ad-dash.json -w '%{http_code}' $API/api/v1/admin/dashboard -H "$ADMIN_AUTH")
check "dashboard-200" 200 "$HTTP"
USERS=$(python3 -c "import json;print(json.load(open('/tmp/ad-dash.json'))['totalUsers'])")
check "dashboard-total-users" ok "$([ "$USERS" -ge 2 ] && echo ok)"
RECORDS=$(python3 -c "import json;print(json.load(open('/tmp/ad-dash.json'))['growthRecords'])")
check "dashboard-growth-records" ok "$([ "$RECORDS" -ge 1 ] && echo ok)"
WHO_VER=$(python3 -c "import json;print(json.load(open('/tmp/ad-dash.json'))['who']['version'] or '')")
check "dashboard-who-version" ok "$([ -n "$WHO_VER" ] && echo ok)"
MEMBERS=$(python3 -c "import json;print(json.load(open('/tmp/ad-dash.json'))['payments']['membershipSucceeded'])")
check "dashboard-membership-succeeded" ok "$([ "$MEMBERS" -ge 1 ] && echo ok)"

echo "== 4. 用户列表：套餐与孩子数 =="
curl -s "$API/api/v1/admin/users?page=1&pageSize=100" -H "$ADMIN_AUTH" > /tmp/ad-users.json
USER_ID=$(python3 -c "
import json
for u in json.load(open('/tmp/ad-users.json'))['items']:
    if u['email'] == '$USER_EMAIL':
        print(u['id'])
        break
")
check "user-in-list" ok "$([ -n "$USER_ID" ] && echo ok)"
PLAN=$(python3 -c "
import json
for u in json.load(open('/tmp/ad-users.json'))['items']:
    if u['email'] == '$USER_EMAIL':
        print(u['plan'])
        break
")
check "user-plan-pro" pro "$PLAN"
CHILD_COUNT=$(python3 -c "
import json
for u in json.load(open('/tmp/ad-users.json'))['items']:
    if u['email'] == '$USER_EMAIL':
        print(u['childCount'])
        break
")
check "user-child-count" 1 "$CHILD_COUNT"

echo "== 5. 孩子列表（admin 视角） =="
curl -s "$API/api/v1/admin/children?page=1&pageSize=100" -H "$ADMIN_AUTH" > /tmp/ad-children.json
IN_CHILDREN=$(python3 -c "
import json
print('ok' if any(c['id'] == int('$CHILD_ID') and c['ownerEmail'] == '$USER_EMAIL'
                  for c in json.load(open('/tmp/ad-children.json'))['items']) else 'missing')
")
check "child-in-list" ok "$IN_CHILDREN"

echo "== 6. 支付列表与过滤 =="
curl -s "$API/api/v1/admin/payments?page=1&pageSize=100&paymentType=membership&status=succeeded" \
  -H "$ADMIN_AUTH" > /tmp/ad-pay-mem.json
MEM_ROW=$(python3 -c "
import json
print('ok' if any(p['userEmail'] == '$USER_EMAIL' for p in json.load(open('/tmp/ad-pay-mem.json'))['items']) else 'missing')
")
check "payments-membership" ok "$MEM_ROW"
curl -s "$API/api/v1/admin/payments?page=1&pageSize=100&paymentType=donation" \
  -H "$ADMIN_AUTH" > /tmp/ad-pay-don.json
DON_ROW=$(python3 -c "
import json
print('ok' if any(p['userEmail'] == '$USER_EMAIL' for p in json.load(open('/tmp/ad-pay-don.json'))['items']) else 'missing')
")
check "payments-donation" ok "$DON_ROW"
HTTP=$(curl -s -o /tmp/ad-pay-bad.json -w '%{http_code}' \
  "$API/api/v1/admin/payments?paymentType=bogus" -H "$ADMIN_AUTH")
check "payments-invalid-type-400" 400 "$HTTP"
check "payments-invalid-type-code" VALIDATION_ERROR "$(jcode /tmp/ad-pay-bad.json)"

echo "== 7. 角色变更：审计 + 旧 token 失效 + 重登生效 =="
HTTP=$(curl -s -o /tmp/ad-promote.json -w '%{http_code}' -X PATCH \
  "$API/api/v1/admin/users/$USER_ID/role" -H "$ADMIN_AUTH" -H 'Content-Type: application/json' \
  -d '{"role":"admin"}')
check "promote-200" 200 "$HTTP"
check "promote-role" admin "$(jget role < /tmp/ad-promote.json)"
curl -s "$API/api/v1/admin/logs?page=1&pageSize=100&kind=audit" -H "$ADMIN_AUTH" > /tmp/ad-logs-audit.json
AUDIT_ROW=$(python3 -c "
import json
print('ok' if any(l['summary'] == 'admin.update_user_role' and l['actorId'] is not None
                  for l in json.load(open('/tmp/ad-logs-audit.json'))['items']) else 'missing')
")
check "audit-logged" ok "$AUDIT_ROW"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/admin/dashboard -H "$AUTH")
check "old-token-403" 403 "$HTTP"
NEW_TOKEN=$(curl -s -X POST $API/api/v1/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$USER_EMAIL\",\"password\":\"$PASSWORD\"}" | jget accessToken)
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/admin/dashboard \
  -H "Authorization: Bearer $NEW_TOKEN")
check "relogin-admin-200" 200 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH \
  "$API/api/v1/admin/users/$USER_ID/role" -H "$ADMIN_AUTH" -H 'Content-Type: application/json' \
  -d '{"role":"user"}')
check "demote-200" 200 "$HTTP"

echo "== 8. 日志：webhook 支付回调 =="
curl -s "$API/api/v1/admin/logs?page=1&pageSize=100&kind=webhook" -H "$ADMIN_AUTH" > /tmp/ad-logs-hook.json
HOOK_ROW=$(python3 -c "
import json
print('ok' if any(l['kind'] == 'webhook' and l['processed'] is True
                  for l in json.load(open('/tmp/ad-logs-hook.json'))['items']) else 'missing')
")
check "webhook-logged" ok "$HOOK_ROW"

echo
echo "SMOKE DONE pass=$PASS fail=$FAIL"
[ "$FAIL" -eq 0 ]
