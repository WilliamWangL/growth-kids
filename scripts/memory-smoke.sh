#!/bin/bash
set -e
API=http://localhost:8082
EMAIL="smoke-memory-$(date +%s)@test.dev"
TOKEN=$(curl -s -X POST $API/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"StrongPassword123!\",\"displayName\":\"SmokeMemory\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
AUTH="Authorization: Bearer $TOKEN"
CHILD_ID=$(curl -s -X POST $API/api/v1/children -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"MemorySmoke","gender":"male","birthDate":"2025-01-01"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "1) child=$CHILD_ID"

# 2) 创建 memory（标题+内容）
M=$(curl -s -X POST $API/api/v1/children/$CHILD_ID/memories -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"memoryDate":"2026-10-01","title":"第一次走路","content":"今天他自己迈出了三步！"}')
MEMORY_ID=$(echo "$M" | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")
echo "2) create memory id=$MEMORY_ID title=$(echo "$M" | python3 -c "import sys,json;print(json.load(sys.stdin)['title'])") createdAt-present=$(echo "$M" | python3 -c "import sys,json;print('createdAt' in json.load(sys.stdin))")"

# 3) 未来日期 → 400
echo "3) future date -> $(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/memories -H "$AUTH" -H 'Content-Type: application/json' -d '{"memoryDate":"2027-01-01","content":"x"}') (expect 400)"

# 4) 早于出生 → 400
echo "4) before birth -> $(curl -s -o /dev/null -w '%{http_code}' -X POST $API/api/v1/children/$CHILD_ID/memories -H "$AUTH" -H 'Content-Type: application/json' -d '{"memoryDate":"2024-01-01","content":"x"}') (expect 400)"

# 5) presign 关联 memoryId + 直传 + finalize
SIZE=$(stat -c%s /tmp/test-photo.jpg)
PRESIGN=$(curl -s -X POST $API/api/v1/children/$CHILD_ID/photos/presign \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"filename\":\"walk.jpg\",\"mimeType\":\"image/jpeg\",\"size\":$SIZE,\"memoryId\":$MEMORY_ID}")
PHOTO_ID=$(echo "$PRESIGN" | python3 -c "import sys,json;print(json.load(sys.stdin)['photoId'])")
UPLOAD_URL=$(echo "$PRESIGN" | python3 -c "import sys,json;print(json.load(sys.stdin)['uploadUrl'])")
PUT_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$API$UPLOAD_URL" -H 'Content-Type: image/jpeg' --data-binary @/tmp/test-photo.jpg)
curl -s -o /dev/null -X POST $API/api/v1/photos/$PHOTO_ID/finalize -H "$AUTH"
sleep 2
echo "5) photo=$PHOTO_ID put=$PUT_CODE (expect 201)"

# 6) 列表 date DESC
LIST=$(curl -s "$API/api/v1/children/$CHILD_ID/memories" -H "$AUTH")
echo "6) list total=$(echo "$LIST" | python3 -c "import sys,json;print(json.load(sys.stdin)['pagination']['total'])") first-date=$(echo "$LIST" | python3 -c "import sys,json;print(json.load(sys.stdin)['items'][0]['memoryDate'])")"

# 7) timeline 该 memory 带 1 张照片
TL=$(curl -s "$API/api/v1/children/$CHILD_ID/timeline" -H "$AUTH")
echo "7) timeline memory-photos=$(echo "$TL" | python3 -c "import sys,json;items=json.load(sys.stdin)['items'];m=[i for i in items if i['type']=='memory'][0];print(len(m.get('photos',[])))") age-months=$(echo "$TL" | python3 -c "import sys,json;items=json.load(sys.stdin)['items'];m=[i for i in items if i['type']=='memory'][0];print(m.get('ageMonths'))") (expect 1/20)"

# 8) PATCH：改日期+内容，title 置空清空
U=$(curl -s -X PATCH $API/api/v1/memories/$MEMORY_ID -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"memoryDate":"2026-10-02","title":"","content":"更新：其实迈了五步"}')
echo "8) patch date=$(echo "$U" | python3 -c "import sys,json;print(json.load(sys.stdin)['memoryDate'])") title-absent=$(echo "$U" | python3 -c "import sys,json;print('title' not in json.load(sys.stdin))") content=$(echo "$U" | python3 -c "import sys,json;print(json.load(sys.stdin)['content'])") (expect 2026-10-02 True 更新：其实迈了五步)"

# 9) 非本人访问 → 404
T2=$(curl -s -X POST $API/api/v1/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"smoke-memory2-$(date +%s)@test.dev\",\"password\":\"StrongPassword123!\",\"displayName\":\"Other\"}" \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
echo "9) foreign patch -> $(curl -s -o /dev/null -w '%{http_code}' -X PATCH $API/api/v1/memories/$MEMORY_ID -H "Authorization: Bearer $T2" -H 'Content-Type: application/json' -d '{"content":"hack"}') (expect 404)"

# 10) 删除 memory → 204，照片解关联保留
DEL_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE $API/api/v1/memories/$MEMORY_ID -H "$AUTH")
PHOTO_LEFT=$(curl -s "$API/api/v1/children/$CHILD_ID/photos" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['pagination']['total'])")
echo "10) delete memory -> $DEL_CODE photos-left=$PHOTO_LEFT (expect 204/1)"

# 11) 空列表确认 + 清理照片
echo "11) list-after-delete total=$(curl -s "$API/api/v1/children/$CHILD_ID/memories" -H "$AUTH" | python3 -c "import sys,json;print(json.load(sys.stdin)['pagination']['total'])") (expect 0)"
echo "12) cleanup photo -> $(curl -s -o /dev/null -w '%{http_code}' -X DELETE $API/api/v1/photos/$PHOTO_ID -H "$AUTH")"
echo "SMOKE DONE"
