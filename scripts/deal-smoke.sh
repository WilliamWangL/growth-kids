#!/bin/bash
# TASK-022 优惠商品冒烟：公开列表/分类/年龄过滤/404 → 匿名 admin 401 → admin 登录 →
# 新建商品 → 公开可见（折扣计算）→ PATCH 改价 → 下架后公开 404 → 非法载荷 400 → 删除。
# 用法：API=http://localhost:8082 bash scripts/deal-smoke.sh
set -e
API="${API:-http://localhost:8082}"
RUN="smoke-deal-$(date +%s)"
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

echo "== 0. 公开接口（无需登录） =="
HTTP=$(curl -s -o /tmp/deal-list.json -w '%{http_code}' "$API/api/v1/deals?pageSize=5")
check "public-list-200" 200 "$HTTP"
TOTAL=$(python3 -c "import json;print(json.load(open('/tmp/deal-list.json'))['pagination']['total'])")
check "public-list-total" ok "$([ "$TOTAL" -ge 1 ] && echo ok)"

HTTP=$(curl -s -o /tmp/deal-cats.json -w '%{http_code}' "$API/api/v1/deals/categories")
check "public-categories-200" 200 "$HTTP"
CATS=$(python3 -c "import json;print(len(json.load(open('/tmp/deal-cats.json'))))")
check "public-categories-count" 7 "$CATS"

AGE_TOTAL=$(curl -s "$API/api/v1/deals?ageMonths=24" |
  python3 -c "import sys,json;print(json.load(sys.stdin)['pagination']['total'])")
check "age-filter" ok "$([ "$AGE_TOTAL" -ge 1 ] && echo ok)"

HTTP=$(curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/deals/999999")
check "missing-detail-404" 404 "$HTTP"

echo "== 1. 权限门禁：匿名 401 =="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/admin/deals")
check "anonymous-admin-401" 401 "$HTTP"

echo "== 2. admin 登录并新建商品 =="
ADMIN_TOKEN=$(curl -s -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jget accessToken)
check "admin-login" ok "$([ -n "$ADMIN_TOKEN" ] && echo ok)"
ADMIN_AUTH="Authorization: Bearer $ADMIN_TOKEN"

DEAL_ID=$(curl -s -X POST "$API/api/v1/admin/deals" -H "$ADMIN_AUTH" -H 'Content-Type: application/json' \
  -d "{\"title\":\"冒烟优惠 $RUN\",\"category\":\"toys\",\"minAgeMonths\":12,\"maxAgeMonths\":36,\"originalPrice\":100,\"dealPrice\":60,\"purchaseUrl\":\"https://example.com/smoke\"}" |
  jget id)
check "admin-create" ok "$([ -n "$DEAL_ID" ] && echo ok)"
DISCOUNT=$(curl -s "$API/api/v1/deals/$DEAL_ID" | jget discountPercent)
check "public-visible-discount" 40 "$DISCOUNT"

echo "== 3. PATCH 改价 + 下架联动 =="
PRICE=$(curl -s -X PATCH "$API/api/v1/admin/deals/$DEAL_ID" -H "$ADMIN_AUTH" \
  -H 'Content-Type: application/json' -d '{"dealPrice":45}' | jget dealPrice)
check "patch-price" 45.0 "$PRICE"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH "$API/api/v1/admin/deals/$DEAL_ID" \
  -H "$ADMIN_AUTH" -H 'Content-Type: application/json' -d '{"status":"inactive"}')
check "patch-inactive" 200 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/deals/$DEAL_ID")
check "inactive-detail-404" 404 "$HTTP"

echo "== 4. 非法载荷：原价低于优惠价 =="
HTTP=$(curl -s -o /tmp/deal-bad.json -w '%{http_code}' -X POST "$API/api/v1/admin/deals" \
  -H "$ADMIN_AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"冒烟倒挂","category":"toys","originalPrice":50,"dealPrice":60,"purchaseUrl":"https://example.com/x"}')
check "price-inverted-400" 400 "$HTTP"
check "price-inverted-code" DEAL_INVALID_DATA "$(jcode /tmp/deal-bad.json)"

echo "== 5. 删除 =="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "$API/api/v1/admin/deals/$DEAL_ID" -H "$ADMIN_AUTH")
check "delete-204" 204 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/deals/$DEAL_ID")
check "deleted-detail-404" 404 "$HTTP"

echo "---- PASS=$PASS FAIL=$FAIL ----"
[ "$FAIL" -eq 0 ]
