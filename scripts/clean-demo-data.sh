#!/bin/bash
# 清理 V4__seed_deals.sql 写入的演示商品（TASK-022 上线收尾）
#
# 背景：V4 是 Flyway 迁移，会把 7 条 purchase_url=https://example.com/deals/* 的演示
# 商品写进数据库；ENABLE_DEMO_SEED 之类的开关对已执行的 SQL 无效。迁移文件本身不可
# 回改（checksum 冲突会导致启动失败），因此演示数据只能按数据清理。
#
# 用法：
#   bash scripts/clean-demo-data.sh                                  # 默认 dry-run（只看）
#   bash scripts/clean-demo-data.sh --apply                          # 直连开发库执行删除
#   bash scripts/clean-demo-data.sh --compose --env-file .env.production --apply   # 生产
#
# 环境变量（直连模式）：MYSQL_HOST/MYSQL_PORT/MYSQL_DATABASE/MYSQL_USER/MYSQL_PASSWORD
# 退出码：0 正常；1 连接或执行失败。
set -euo pipefail

APPLY=false
USE_COMPOSE=false
ENV_FILE=".env.production"
COMPOSE_FILE="docker-compose.prod.yml"

while [ $# -gt 0 ]; do
  case "$1" in
  --apply) APPLY=true; shift ;;
  --compose) USE_COMPOSE=true; shift ;;
  --env-file) ENV_FILE="${2:?--env-file 需要参数}"; shift 2 ;;
  -h | --help)
    sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  *)
    echo "未知参数：$1（-h 查看用法）" >&2
    exit 2
    ;;
  esac
done

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3310}"
MYSQL_DATABASE="${MYSQL_DATABASE:-growthkids}"
MYSQL_USER="${MYSQL_USER:-growthkids}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-growthkids}"

DEMO_CONDITION="purchase_url LIKE 'https://example.com/%'"

if [ "$USE_COMPOSE" = true ]; then
  if [ ! -f "$ENV_FILE" ]; then
    echo "[clean] ERROR: $ENV_FILE 不存在" >&2
    exit 1
  fi
  echo "[clean] 目标：docker compose ($COMPOSE_FILE) → mysql 容器"
  db_exec() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
      bash -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -N -B -D "$MYSQL_DATABASE" -e "$1"' -- "$1"
  }
else
  echo "[clean] 目标：${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}"
  db_exec() {
    MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
      -N -B -D "$MYSQL_DATABASE" -e "$1"
  }
fi

COUNT=$(db_exec "SELECT COUNT(*) FROM deals WHERE ${DEMO_CONDITION};" | tr -d '\r')
echo "[clean] 匹配演示商品：${COUNT} 条"

if [ "$COUNT" != "0" ]; then
  db_exec "SELECT id, title, deal_price, purchase_url FROM deals WHERE ${DEMO_CONDITION} ORDER BY id;"
fi

# 顺带报告其它可能残留的演示痕迹（只读，不删除）
echo "[clean] 参考：example.com 邮箱用户 $(db_exec "SELECT COUNT(*) FROM users WHERE email LIKE '%@example.com';" | tr -d '\r') 个（不会删除）"

if [ "$APPLY" != true ]; then
  echo "[clean] dry-run：未做任何修改。确认无误后加 --apply 执行删除"
  exit 0
fi

if [ "$COUNT" = "0" ]; then
  echo "[clean] 无需清理"
  exit 0
fi

db_exec "DELETE FROM deals WHERE ${DEMO_CONDITION};"
LEFT=$(db_exec "SELECT COUNT(*) FROM deals WHERE ${DEMO_CONDITION};" | tr -d '\r')
TOTAL=$(db_exec "SELECT COUNT(*) FROM deals;" | tr -d '\r')

if [ "$LEFT" != "0" ]; then
  echo "[clean] ERROR: 删除后仍剩 ${LEFT} 条演示商品" >&2
  exit 1
fi
echo "[clean] 已删除 ${COUNT} 条演示商品；deals 现存 ${TOTAL} 条（均为后台上架的真实商品）"
echo "[clean] 提示：V4 迁移记录保留在 flyway_schema_history 中，属正常"
