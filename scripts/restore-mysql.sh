#!/bin/bash
# 数据库恢复（TASK-020，§118 backup restore procedure）：
# 用法：
#   bash scripts/restore-mysql.sh backups/growthkids-20261029-020000.sql.gz
#   bash scripts/restore-mysql.sh <备份文件> growthkids_restore_test   # 演练到临时库
# 注意：会覆盖目标库同名表；生产执行前先停 API（docker compose stop api）。
set -euo pipefail

BACKUP_FILE="${1:?用法: restore-mysql.sh <backup.sql.gz> [目标库]}"
SOURCE_DB="${MYSQL_DATABASE:-growthkids}"
TARGET_DB="${2:-$SOURCE_DB}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3310}"
MYSQL_USER="${MYSQL_USER:-growthkids}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-growthkids}"

if [ ! -f "$BACKUP_FILE" ]; then
  echo "[restore] backup file not found: $BACKUP_FILE" >&2
  exit 1
fi
gzip -t "$BACKUP_FILE"

echo "[restore] ${BACKUP_FILE} -> ${TARGET_DB}@${MYSQL_HOST}:${MYSQL_PORT}"
if [ "$TARGET_DB" = "$SOURCE_DB" ]; then
  gunzip -c "$BACKUP_FILE" \
    | MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER"
else
  # 演练/迁移到其他库：先确保目标库存在（无建库权限时给出明确提示）
  MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
    -e "CREATE DATABASE IF NOT EXISTS \`${TARGET_DB}\` CHARACTER SET utf8mb4" || {
    echo "[restore] ERROR: 无法创建 ${TARGET_DB}（账号缺少建库权限），请先由 DBA 创建后重试" >&2
    exit 1
  }
  # 重写备份内的 CREATE DATABASE / USE 库名
  gunzip -c "$BACKUP_FILE" \
    | sed "s/\`${SOURCE_DB}\`/\`${TARGET_DB}\`/g" \
    | MYSQL_PWD="$MYSQL_PASSWORD" mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER"
fi
echo "[restore] done"
