#!/bin/bash
# TASK-018 Annual Report 冒烟（§98/§99/§110）：Pro 权益门禁→年份/数据校验→
# 创建→异步生成轮询→PDF 下载（magic bytes/Content-Type）→同年重生成幂等→越权 404。
set -e
API=http://localhost:8082
RUN="smoke-rp-$(date +%s)"
EMAIL="${RUN}@test.dev"
OTHER="${RUN}-other@test.dev"
PASSWORD="StrongPassword123!"
YEAR=$(date +%Y)
MONTH=$(date +%m)
if [ "$MONTH" -lt 6 ]; then YEAR=$((YEAR - 1)); fi
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

echo "== 0. 注册 + 创建孩子 + 成长记录 =="
TOKEN=$(curl -s -X POST $API/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"displayName\":\"SmokeReport\"}" | jget accessToken)
OTHER_TOKEN=$(curl -s -X POST $API/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$OTHER\",\"password\":\"$PASSWORD\",\"displayName\":\"SmokeOther\"}" | jget accessToken)
AUTH="Authorization: Bearer $TOKEN"
OTHER_AUTH="Authorization: Bearer $OTHER_TOKEN"

CHILD_ID=$(curl -s -X POST $API/api/v1/children -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"冒烟小明","gender":"male","birthDate":"2023-03-10","fatherHeightCm":175,"motherHeightCm":162}' | jget id)
check "child-created" ok "$([ -n "$CHILD_ID" ] && echo ok)"

HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/growth-records \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"recordDate\":\"$YEAR-02-15\",\"heightCm\":92.5,\"weightKg\":13.8,\"headCircumferenceCm\":49.5}")
check "record-1" 201 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/growth-records \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"recordDate\":\"$YEAR-06-10\",\"heightCm\":95.5,\"weightKg\":14.6,\"headCircumferenceCm\":49.8}")
check "record-2" 201 "$HTTP"

echo "== 1. Free 用户创建报告 403（§64 Pro 权益） =="
HTTP=$(curl -s -o /tmp/rp-free.json -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/reports \
  -H "$AUTH" -H 'Content-Type: application/json' -d "{\"year\":$YEAR}")
check "free-blocked" 403 "$HTTP"
check "free-code" MEMBERSHIP_REQUIRED "$(python3 -c "import json;print(json.load(open('/tmp/rp-free.json'))['error']['code'])")"

echo "== 2. 升级 Pro + 校验（年份越界 / 无数据年份 / 他人孩子） =="
SUB_TX=$(curl -s -X POST $API/api/v1/membership/subscribe -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"planCode":"pro"}' | jget transactionId)
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/membership/mock-complete/$SUB_TX -H "$AUTH")
check "pro-active" 200 "$HTTP"
# other 也升级（Free 先撞权限门禁 403，升级后才能触达归属检查 404）
OTHER_TX=$(curl -s -X POST $API/api/v1/membership/subscribe -H "$OTHER_AUTH" -H 'Content-Type: application/json' \
  -d '{"planCode":"pro"}' | jget transactionId)
curl -s -o /dev/null -X POST $API/api/v1/membership/mock-complete/$OTHER_TX -H "$OTHER_AUTH"

HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/reports \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"year":1899}')
check "year-too-old" 400 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/reports \
  -H "$AUTH" -H 'Content-Type: application/json' -d '{"year":2101}')
check "year-too-new" 400 "$HTTP"
HTTP=$(curl -s -o /tmp/rp-nodata.json -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/reports \
  -H "$AUTH" -H 'Content-Type: application/json' -d "{\"year\":$((YEAR + 4))}")
check "no-data-year" 400 "$HTTP"
check "no-data-code" REPORT_NO_DATA "$(python3 -c "import json;print(json.load(open('/tmp/rp-nodata.json'))['error']['code'])")"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/reports \
  -H "$OTHER_AUTH" -H 'Content-Type: application/json' -d "{\"year\":$YEAR}")
check "foreign-child" 404 "$HTTP"

echo "== 3. 创建报告 201 pending → 轮询 ready =="
HTTP=$(curl -s -o /tmp/rp-create.json -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/reports \
  -H "$AUTH" -H 'Content-Type: application/json' -d "{\"year\":$YEAR}")
check "create-status" 201 "$HTTP"
REPORT_ID=$(jget id < /tmp/rp-create.json)
check "create-pending" pending "$(jget status < /tmp/rp-create.json)"
check "create-type" annual "$(jget reportType < /tmp/rp-create.json)"
check "db-report-row" 1 "$(mysql_q "SELECT COUNT(*) FROM growth_reports WHERE id=$REPORT_ID")"

STATUS=pending
for _ in $(seq 1 40); do
  STATUS=$(mysql_q "SELECT status FROM growth_reports WHERE id=$REPORT_ID")
  if [ "$STATUS" = "ready" ] || [ "$STATUS" = "failed" ]; then break; fi
  sleep 1
done
check "generated-ready" ready "$STATUS"
STORAGE_KEY=$(mysql_q "SELECT storage_key FROM growth_reports WHERE id=$REPORT_ID")
check "storage-key" ok "$([ -n "$STORAGE_KEY" ] && echo ok)"

echo "== 4. 下载 PDF（magic bytes / Content-Type） =="
HTTP=$(curl -s -o /tmp/rp-report.pdf -w '%{http_code}' $API/api/v1/reports/$REPORT_ID/download -H "$AUTH")
check "download-status" 200 "$HTTP"
check "pdf-magic" %PDF "$(head -c 4 /tmp/rp-report.pdf)"
SIZE=$(stat -c %s /tmp/rp-report.pdf)
check "pdf-size-gt-1k" ok "$([ "$SIZE" -gt 1000 ] && echo ok)"
HEADER=$(curl -s -o /dev/null -w '%{content_type}' $API/api/v1/reports/$REPORT_ID/download -H "$AUTH")
check "pdf-content-type" "application/pdf" "$HEADER"
FILE_PATH=$(mysql_q "SELECT CONCAT('$(cd /root/workspace/growth-kids/apps/api && pwd)/storage/', storage_key) FROM growth_reports WHERE id=$REPORT_ID")
check "stored-pdf-exists" ok "$([ -f "$FILE_PATH" ] && echo ok)"
check "stored-pdf-size" ok "$([ -f "$FILE_PATH" ] && [ "$(stat -c %s "$FILE_PATH")" -gt 1000 ] && echo ok)"

echo "== 5. 同年重生成幂等（uq_child_report） =="
HTTP=$(curl -s -o /tmp/rp-regen.json -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/reports \
  -H "$AUTH" -H 'Content-Type: application/json' -d "{\"year\":$YEAR}")
check "regen-status" 201 "$HTTP"
check "regen-same-id" "$REPORT_ID" "$(jget id < /tmp/rp-regen.json)"
check "regen-pending" pending "$(jget status < /tmp/rp-regen.json)"
check "db-single-row" 1 "$(mysql_q "SELECT COUNT(*) FROM growth_reports WHERE child_id=$CHILD_ID AND year=$YEAR")"

echo "== 6. 他人下载 / 未登录 / 不存在报告 =="
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/reports/$REPORT_ID/download -H "$OTHER_AUTH")
check "foreign-download" 404 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/reports/$REPORT_ID/download)
check "unauth-download" 401 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/reports/999999/download -H "$AUTH")
check "missing-report" 404 "$HTTP"
HTTP=$(curl -s -o /dev/null -w '%{http_code}' $API/api/v1/children/$CHILD_ID/reports)
check "unauth-list" 401 "$HTTP"

echo "== 7. 列表与分页 =="
HTTP=$(curl -s -o /tmp/rp-list.json -w '%{http_code}' $API/api/v1/children/$CHILD_ID/reports -H "$AUTH")
check "list-status" 200 "$HTTP"
check "list-total" 1 "$(python3 -c "import json;print(json.load(open('/tmp/rp-list.json'))['pagination']['total'])")"

echo
echo "PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
