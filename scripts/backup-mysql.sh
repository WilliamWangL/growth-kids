#!/bin/bash
# 每日 MySQL 备份（TASK-020，§117）：
# - mysqldump 单事务快照（--single-transaction）+ routines/triggers
# - gzip 压缩；保留 BACKUP_RETENTION_DAYS（默认 30）天
# - 由 docker-compose.prod.yml 的 backup 服务循环调用，也可由宿主 cron 调用
# 用法：MYSQL_HOST=127.0.0.1 MYSQL_PORT=3310 bash scripts/backup-mysql.sh
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3310}"
MYSQL_DATABASE="${MYSQL_DATABASE:-growthkids}"
MYSQL_USER="${MYSQL_USER:-growthkids}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-growthkids}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
BACKUP_RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

mkdir -p "$BACKUP_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
FILE="$BACKUP_DIR/${MYSQL_DATABASE}-${STAMP}.sql.gz"

echo "[backup] dump ${MYSQL_DATABASE} from ${MYSQL_HOST}:${MYSQL_PORT} -> ${FILE}"
MYSQL_PWD="$MYSQL_PASSWORD" mysqldump \
  -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
  --single-transaction --routines --triggers --no-tablespaces \
  --databases "$MYSQL_DATABASE" \
  | gzip -9 > "$FILE"

# 校验：gzip 完整性 + 文件大小下限（防截断/空备份）
gzip -t "$FILE"
SIZE=$(stat -c %s "$FILE" 2>/dev/null || stat -f %z "$FILE")
if [ "$SIZE" -lt 100 ]; then
  echo "[backup] ERROR: dump too small (${SIZE} bytes)" >&2
  exit 1
fi
echo "[backup] OK ${FILE} (${SIZE} bytes)"

# 保留策略：删除超过保留期的备份
DELETED=$(find "$BACKUP_DIR" -type f -name "${MYSQL_DATABASE}-*.sql.gz" \
  -mtime +"$BACKUP_RETENTION_DAYS" -print -delete | wc -l)
echo "[backup] pruned ${DELETED} file(s) older than ${BACKUP_RETENTION_DAYS}d"
