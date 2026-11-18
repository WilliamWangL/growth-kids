#!/bin/bash
# 生产部署体检（TASK-020 补强，§80/§120）
#
# 用法：
#   bash scripts/preflight-prod.sh --env-file .env.production          # 静态体检（启动前）
#   bash scripts/preflight-prod.sh --env-file .env.production --post   # 运行态体检（启动后）
#
# 体检项：
#   1) .env.production 存在且必填密钥齐全、强度足够、互不重复、无占位值
#   2) NEXT_PUBLIC_API_URL / WEB_ORIGIN / NGINX_ORIGIN 同源且为 https
#   3) HTTPS 证书存在、未过期（<14 天告警）、私钥与证书匹配
#   4) WHO 生长标准指向官方数据（合成示例视为失败，可显式放行）
#   5) 管理员引导、备份目录、docker compose 配置可解析
#   6) --post：健康探针、WHO 数据来源、演示商品残留、管理员数量、存储卷可写
#
# 退出码：0 全部通过（允许 WARN），1 存在 FAIL。
# 注意：本脚本只读；不修改任何数据（演示数据清理见 scripts/clean-demo-data.sh）。
set -uo pipefail

ENV_FILE=".env.production"
POST=false
ALLOW_HTTP=false
ALLOW_SYNTHETIC_WHO=false
COMPOSE_FILE="docker-compose.prod.yml"
SSH_DIR_CERT="docker/nginx/ssl/fullchain.pem"
SSL_DIR_KEY="docker/nginx/ssl/privkey.pem"

PASS_N=0
WARN_N=0
FAIL_N=0

pass() { PASS_N=$((PASS_N + 1)); printf '[ OK ] %s\n' "$1"; }
warn() { WARN_N=$((WARN_N + 1)); printf '[WARN] %s\n' "$1"; }
fail() { FAIL_N=$((FAIL_N + 1)); printf '[FAIL] %s\n' "$1"; }
section() { printf '\n== %s ==\n' "$1"; }

usage() {
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit 0
}

while [ $# -gt 0 ]; do
  case "$1" in
  --env-file) ENV_FILE="${2:?--env-file 需要参数}"; shift 2 ;;
  --post) POST=true; shift ;;
  --allow-http) ALLOW_HTTP=true; shift ;;
  --allow-synthetic-who) ALLOW_SYNTHETIC_WHO=true; shift ;;
  -h | --help) usage ;;
  *) echo "未知参数：$1（-h 查看用法）" >&2; exit 2 ;;
  esac
done

val() { printf '%s' "${!1:-}"; }
mask() { local v; v=$(val "$1"); printf '%s' "${#v} chars"; }
is_placeholder() {
  local v=$1 low
  low=$(printf '%s' "$v" | tr '[:upper:]' '[:lower:]')
  case "$low" in
  *changeme* | *change-me* | *your-* | password | secret | xxx* | todo* | \<*\>*) return 0 ;;
  *) return 1 ;;
  esac
}
origin_host() {
  local u=$1
  u=${u#https://}
  u=${u#http://}
  printf '%s' "${u%%/*}"
}

# ---------- 1. 环境变量 ----------
section "环境变量（$ENV_FILE）"
if [ ! -f "$ENV_FILE" ]; then
  fail "$ENV_FILE 不存在：cp .env.production.example .env.production"
else
  perms=$(stat -c '%a' "$ENV_FILE" 2>/dev/null || echo "?")
  [ "$perms" = "600" ] && pass "$ENV_FILE 权限 600" || warn "$ENV_FILE 权限为 $perms，建议 chmod 600"
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

for v in MYSQL_PASSWORD MYSQL_ROOT_PASSWORD JWT_ACCESS_SECRET JWT_REFRESH_SECRET \
  STORAGE_SIGNING_SECRET PAYMENT_MOCK_SIGNING_SECRET; do
  raw=$(val "$v")
  if [ -z "$raw" ]; then
    fail "$v 为空（API 会启动失败）"
    continue
  fi
  if is_placeholder "$raw"; then
    fail "$v 仍是占位值"
    continue
  fi
  case "$v" in
  MYSQL_PASSWORD | MYSQL_ROOT_PASSWORD)
    # 数据库密码只在容器内网使用，强度要求低于签名密钥
    if [ "${#raw}" -lt 16 ]; then warn "$v 长度仅 ${#raw}，建议 ≥ 16"; else pass "$v 已设置（$(mask "$v")）"; fi
    ;;
  *)
    if [ "${#raw}" -lt 32 ]; then fail "$v 长度仅 ${#raw}，密钥应 ≥ 32（openssl rand -hex 48）"; else pass "$v 已设置（$(mask "$v")）"; fi
    ;;
  esac
done

for v in NEXT_PUBLIC_API_URL WEB_ORIGIN NGINX_ORIGIN; do
  raw=$(val "$v")
  if [ -z "$raw" ]; then
    fail "$v 为空（站点地址/ CORS 会失效）"
  elif is_placeholder "$raw"; then
    fail "$v 仍是模板占位值"
  else
    pass "$v 已设置"
  fi
done

# 三类签名/加密密钥不得复用：泄露一处等于全线失守
jwt_a=$(val JWT_ACCESS_SECRET)
jwt_r=$(val JWT_REFRESH_SECRET)
sig_s=$(val STORAGE_SIGNING_SECRET)
if [ -z "$jwt_a" ] || [ -z "$jwt_r" ] || [ -z "$sig_s" ]; then
  warn "密钥未填齐，跳过重复校验"
elif [ "$jwt_a" = "$jwt_r" ] || [ "$jwt_a" = "$sig_s" ] || [ "$jwt_r" = "$sig_s" ]; then
  fail "JWT_ACCESS_SECRET / JWT_REFRESH_SECRET / STORAGE_SIGNING_SECRET 存在重复值"
else
  pass "三类密钥互不重复"
fi

if [ "$(val PAYMENT_PROVIDER)" = "mock" ] || [ -z "$(val PAYMENT_PROVIDER)" ]; then
  warn "PAYMENT_PROVIDER=mock：可跑通下单/回调闭环，但不产生真实扣款（上线收款需接真实网关）"
fi

# ---------- 2. 站点与 CORS ----------
section "站点与 CORS"
scheme_ok=true
scheme_checked=true
for v in NEXT_PUBLIC_API_URL WEB_ORIGIN NGINX_ORIGIN; do
  u=$(val "$v")
  [ -z "$u" ] && scheme_checked=false
  case "$u" in
  https://*) : ;;
  http://*)
    if [ "$ALLOW_HTTP" = true ]; then warn "$v 使用 http（已放行，仅限非生产验收）"; else fail "$v 必须为 https（确属内网验收可加 --allow-http）"; scheme_ok=false; fi
    ;;
  "") : ;;
  *) fail "$v 不是完整 URL：$u" ;;
  esac
done
h1=$(origin_host "$(val NEXT_PUBLIC_API_URL)")
h2=$(origin_host "$(val WEB_ORIGIN)")
h3=$(origin_host "$(val NGINX_ORIGIN)")
if [ -n "$h1" ] && [ "$h1" = "$h2" ] && [ "$h2" = "$h3" ]; then
  pass "三个地址同域：$h1"
else
  fail "站点地址不同域：API=$h1 WEB=$h2 NGINX=$h3（跨域会导致 CORS 与签名 URL 失效）"
fi
if [ "$scheme_checked" = true ] && [ "$scheme_ok" = true ]; then
  pass "站点地址均为 https"
fi

# ---------- 3. HTTPS 证书 ----------
section "HTTPS 证书"
if [ ! -f "$SSH_DIR_CERT" ] || [ ! -f "$SSL_DIR_KEY" ]; then
  fail "缺少证书：$SSH_DIR_CERT 或 $SSL_DIR_KEY（见 docs/PRODUCTION.md §3）"
elif ! command -v openssl >/dev/null 2>&1; then
  warn "openssl 不可用，跳过证书解析"
else
  pass "证书与私钥文件存在"
  if openssl x509 -in "$SSH_DIR_CERT" -noout -checkend 0 >/dev/null 2>&1; then
    if openssl x509 -in "$SSH_DIR_CERT" -noout -checkend 1209600 >/dev/null 2>&1; then
      pass "证书 14 天内不会过期（到期：$(openssl x509 -in "$SSH_DIR_CERT" -noout -enddate | cut -d= -f2)）"
    else
      warn "证书将在 14 天内过期，请尽快续期并 nginx -s reload"
    fi
  else
    fail "证书已过期"
  fi
  cert_pub=$(openssl x509 -in "$SSH_DIR_CERT" -noout -pubkey 2>/dev/null |
    openssl pkey -pubin -outform DER 2>/dev/null | openssl dgst -sha256 2>/dev/null | awk '{print $NF}')
  key_pub=$(openssl pkey -in "$SSL_DIR_KEY" -pubout -outform DER 2>/dev/null |
    openssl dgst -sha256 2>/dev/null | awk '{print $NF}')
  if [ -z "$cert_pub" ] || [ -z "$key_pub" ]; then
    warn "无法解析证书/私钥，跳过匹配校验"
  elif [ "$cert_pub" = "$key_pub" ]; then
    pass "私钥与证书公钥匹配"
  else
    fail "私钥与证书不匹配（nginx 会启动失败）"
  fi
fi

# ---------- 4. WHO 生长标准 ----------
section "WHO 生长标准"
who=$(val APP_WHO_DATA_FILE)
if [ -z "$who" ]; then
  if [ "$ALLOW_SYNTHETIC_WHO" = true ]; then
    warn "APP_WHO_DATA_FILE 为空：将使用镜像内置合成示例（已放行）"
  else
    fail "APP_WHO_DATA_FILE 为空：将使用合成示例数据（source=growthkids-example-synthetic），百分位/z-score 不可用于真实用户；确需放行加 --allow-synthetic-who"
  fi
elif [ "${who#classpath:}" != "$who" ]; then
  if [ "$ALLOW_SYNTHETIC_WHO" = true ]; then
    warn "WHO 数据来自镜像内置文件（$who，已放行）"
  else
    fail "WHO 数据来自镜像内置示例（$who），生产应挂载官方 CSV"
  fi
else
  path=${who#file:}
  host_path=$path
  case "$path" in
  /data/who/*) host_path="data/who-official/${path#/data/who/}" ;;
  esac
  if [ ! -f "$host_path" ]; then
    fail "WHO 数据文件不存在：$who（宿主应为 $host_path）"
  elif [ ! -s "$host_path" ]; then
    fail "WHO 数据文件为空：$host_path"
  elif head -5 "$host_path" | grep -qi 'growthkids-example-synthetic'; then
    if [ "$ALLOW_SYNTHETIC_WHO" = true ]; then
      warn "WHO 文件为合成示例（已放行）：$host_path"
    else
      fail "WHO 文件仍是合成示例：$host_path（需替换为官方数据，见 data/who/README.md）"
    fi
  elif ! head -5 "$host_path" | grep -qi '^# *source:'; then
    warn "$host_path 缺少 '# source:' 元数据行（导入器会拒绝）"
  else
    pass "WHO 官方数据就绪：$host_path（$(wc -l <"$host_path") 行）"
  fi
fi

# ---------- 5. 引导账号 / 备份 / 编排 ----------
section "管理员、备份与编排"
if [ -z "$(val ADMIN_EMAIL)" ] || [ -z "$(val ADMIN_PASSWORD)" ]; then
  warn "ADMIN_EMAIL/ADMIN_PASSWORD 为空：不会创建首个管理员（已有管理员时属正常）"
else
  pass "管理员引导已配置：$(val ADMIN_EMAIL)"
  [ "${#ADMIN_PASSWORD}" -lt 12 ] && warn "ADMIN_PASSWORD 短于 12 位"
fi

if mkdir -p ./backups 2>/dev/null && [ -w ./backups ]; then
  pass "备份目录 ./backups 可写（保留 ${BACKUP_RETENTION_DAYS:-30} 天）"
else
  fail "备份目录 ./backups 不可写：backup 容器会持续失败"
fi

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  if out=$(docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" config -q 2>&1); then
    pass "docker compose 配置可解析（$COMPOSE_FILE）"
  else
    fail "docker compose 配置解析失败：$(printf '%s' "$out" | tail -3 | tr '\n' ' ')"
  fi
  port_busy() {
    local p=${1:-$2}
    ss -ltn 2>/dev/null | grep -qE "[:.]${p} +" && warn "宿主机 ${p} 端口已被占用，nginx 可能起不来：ss -ltnp | grep :${p}"
  }
  port_busy "$(val HTTP_PORT)" 80
  port_busy "$(val HTTPS_PORT)" 443
else
  warn "未检测到 docker compose，跳过编排校验"
fi

# ---------- 6. 运行态（--post） ----------
if [ "$POST" = true ]; then
  section "运行态体检"
  base=$(val NEXT_PUBLIC_API_URL)
  base=${base%/}
  if curl -fsS --max-time 10 "$base/api/v1/health" >/dev/null 2>&1; then
    pass "GET $base/api/v1/health 正常"
  else
    fail "健康探针不可达：$base/api/v1/health"
  fi

  db_query() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T mysql \
      bash -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -N -B -D "$MYSQL_DATABASE" -e "$1"' -- "$1" 2>/dev/null | tr -d '\r'
  }
  if ! command -v docker >/dev/null 2>&1; then
    warn "无 docker，跳过数据库检查"
  else
    who_src=$(db_query "SELECT GROUP_CONCAT(DISTINCT source) FROM who_growth_standards;" || true)
    who_rows=$(db_query "SELECT COUNT(*) FROM who_growth_standards;" || true)
    case "$who_src" in
    *growthkids-example-synthetic*)
      if [ "$ALLOW_SYNTHETIC_WHO" = true ]; then warn "库内 WHO 数据为合成示例（已放行）"; else fail "库内 WHO 数据为合成示例，百分位不可信"; fi
      ;;
    "") fail "无法读取 who_growth_standards（检查 MYSQL_* 与容器名）" ;;
    *) pass "库内 WHO 数据来源：$who_src（${who_rows:-?} 行）" ;;
    esac

    demo_deals=$(db_query "SELECT COUNT(*) FROM deals WHERE purchase_url LIKE 'https://example.com/%';" || true)
    case "$demo_deals" in
    0) pass "无演示商品残留" ;;
    "" | *[!0-9]*) warn "无法统计演示商品（deals 表可能尚未创建）" ;;
    *) fail "仍有 $demo_deals 条 V4 演示商品（example.com 外链）：bash scripts/clean-demo-data.sh --compose --apply" ;;
    esac

    admins=$(db_query "SELECT COUNT(*) FROM users WHERE role='admin';" || true)
    case "$admins" in
    0) warn "库内没有 admin 用户：检查 ADMIN_EMAIL/ADMIN_PASSWORD 后重启 api" ;;
    "") warn "无法统计管理员数量" ;;
    *) pass "admin 用户 ${admins} 个" ;;
    esac

    if docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" exec -T api test -w /app/storage >/dev/null 2>&1; then
      pass "storage-data 卷可写（/app/storage）"
    else
      fail "storage-data 卷不可写：照片/报告会写入失败"
    fi
  fi
fi

# ---------- 汇总 ----------
printf '\n== 汇总 ==\n通过 %d，告警 %d，失败 %d\n' "$PASS_N" "$WARN_N" "$FAIL_N"
if [ "$FAIL_N" -gt 0 ]; then
  echo "结论：存在阻塞项，请修复后重新体检（不要直接上线）"
  exit 1
fi
echo "结论：可以发布；WARN 项请人工确认"
[ "$POST" = true ] || echo "提示：启动后执行 --post 做运行态复核"
exit 0
