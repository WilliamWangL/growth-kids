# GrowthKids 生产部署（TASK-020）

本文覆盖规范 §80（Docker 生产 / Nginx / HTTPS）、§82（日志）、§83（监控）、
§117（备份）、§118（恢复）。开发环境见根目录 `docker-compose.yml`，生产见
`docker-compose.prod.yml`。

## 1. 架构与端口

```text
Internet ── 80/443 ──> nginx（TLS 终止 / HTTPS 跳转 / 安全头）
                         ├── /api/、/media/ ──> api:8082（Spring Boot，容器内网）
                         ├── /              ──> web:3000（Next.js standalone，容器内网）
                         ├── mysql:3306     （仅内网，命名卷 mysql-data）
                         ├── redis:6379     （仅内网，命名卷 redis-data）
                         ├── backup         （每日 mysqldump，./backups 保留 30 天）
                         └── /actuator/* 对外 404（健康检查由容器直连 api:8082）
```

- 宿主机仅暴露 `HTTP_PORT`（默认 80）与 `HTTPS_PORT`（默认 443）。
- 上传/报告文件持久化在命名卷 `storage-data`（挂载到 api 的 `/app/storage`）。
- WHO 官方 CSV 由宿主 `./data/who-official` 只读挂载到 api 的 `/data/who`，
  由 `APP_WHO_DATA_FILE` 指向（见 2.2）；未放官方数据时回退镜像内置合成示例。

## 2. 首次部署

流程：体检 → 准备 WHO 官方数据 → 证书（第 3 节）→ 构建启动 → 清理演示商品。

### 2.1 环境变量与上线体检

```bash
# 1) 准备环境变量
cp .env.production.example .env.production
chmod 600 .env.production
#    填写 MYSQL_PASSWORD、MYSQL_ROOT_PASSWORD、JWT_ACCESS_SECRET、
#    JWT_REFRESH_SECRET、STORAGE_SIGNING_SECRET、PAYMENT_MOCK_SIGNING_SECRET、
#    NEXT_PUBLIC_API_URL、WEB_ORIGIN、NGINX_ORIGIN、APP_WHO_DATA_FILE、
#    ADMIN_EMAIL/ADMIN_PASSWORD；密钥生成：openssl rand -hex 48

# 2) 上线体检（存在 FAIL 时不要上线）
bash scripts/preflight-prod.sh --env-file .env.production
```

`scripts/preflight-prod.sh` 检查：必填密钥齐全/强度/互不重复、三个站点地址同域且为
https、证书存在未过期且私钥匹配、WHO 数据非合成、管理员引导、备份目录可写、
`docker compose config` 可解析、80/443 占用。

只用于内网验收时可临时放行：`--allow-http`、`--allow-synthetic-who`（生产禁用）。

### 2.2 WHO 官方生长标准（生产必填）

镜像内置的 `who-standards-example.csv` 是**合成示例**（`source=growthkids-example-synthetic`），
用于联调百分位链路；生产必须换成官方数据，否则身高/体重/BMI/头围的百分位与 z-score
对真实用户不成立。

```bash
# 官方 CSV（格式与转换见 data/who/README.md）放到宿主目录，无需重建镜像
# data/who-official/who-standards-official.csv   ← 已被 .gitignore 忽略
# compose 将 ./data/who-official 只读挂载到容器 /data/who
# .env.production: APP_WHO_DATA_FILE=file:/data/who/who-standards-official.csv
```

文件缺失、格式非法或数据级校验失败会**阻止 API 启动**（fail-fast）。若选择把官方数据
直接打进镜像，则需要重建 API 镜像，并把 `APP_WHO_DATA_FILE` 清空——但 preflight 会把
`classpath:` 判为失败，避免误用合成数据上线。

### 2.3 构建启动与验证

```bash
# 3) 放置 HTTPS 证书（见第 3 节）
#    docker/nginx/ssl/fullchain.pem
#    docker/nginx/ssl/privkey.pem

# 4) 构建并启动
docker compose -f docker-compose.prod.yml --env-file .env.production up -d --build

# 5) 验证
curl -fsS https://<域名>/api/v1/health          # {"status":"ok"...}
curl -fsS https://<域名>/actuator/health        # 404（不对外暴露，符合预期）
docker compose -f docker-compose.prod.yml ps    # api/web 为 healthy

# 6) 运行态复核（健康探针、库内 WHO 来源、演示商品残留、管理员数、存储卷可写）
bash scripts/preflight-prod.sh --env-file .env.production --post
```

首次启动时 Flyway 自动迁移；若 `ADMIN_EMAIL/ADMIN_PASSWORD` 非空且邮箱不存在，
`AdminBootstrap` 会创建首个管理员（角色 `admin`）。修改角色自下次登录生效。

### 2.4 清理演示商品（首次上线必做）

`V4__seed_deals.sql` 会把 7 条 `purchase_url=https://example.com/deals/*` 的演示商品
写进数据库。它是 Flyway 迁移而非运行时开关（compose 里已移除无效的
`ENABLE_DEMO_SEED`），迁移文件也不能回改（checksum 冲突会导致启动失败），
因此只能按数据清理：

```bash
bash scripts/clean-demo-data.sh --compose --env-file .env.production          # dry-run 看清单
bash scripts/clean-demo-data.sh --compose --env-file .env.production --apply  # 执行删除
```

也可登录 `/admin` →「优惠商品」逐条删除（后台 DELETE 为硬删除）。清理后
`flyway_schema_history` 中 V4 记录保留，属正常。

> 安全提示：生产环境不要保留 `.env.production` 的空白密钥——API 启动时会因为
> `${JWT_ACCESS_SECRET}` 等占位符缺失而失败（这是有意的失败即停设计）。

## 3. HTTPS 证书

### 3.1 已有证书（商业证书 / 已有 Let's Encrypt）

把证书链与私钥放到：

```text
docker/nginx/ssl/fullchain.pem
docker/nginx/ssl/privkey.pem
```

重载：

```bash
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
```

### 3.2 Let's Encrypt（webroot）

Nginx 已放行 `/.well-known/acme-challenge/`（根目录 `/var/www/certbot`，宿主
`docker/nginx/certbot-webroot`）。首次签发（80 端口需可达）：

```bash
docker run --rm -v "$PWD/docker/nginx/certbot-webroot:/var/www/certbot" \
  -v "$PWD/docker/nginx/ssl:/etc/letsencrypt" \
  certbot/certbot certonly --webroot -w /var/www/certbot \
  -d growthkids.example.com --email ops@example.com --agree-tos --non-interactive

# 证书位于 docker/nginx/ssl/live/<域名>/{fullchain.pem,privkey.pem}
# 将其软链/复制为 docker/nginx/ssl/fullchain.pem 与 privkey.pem 后 reload
```

续期建议由宿主 cron 每月执行 `certbot renew` 并 `nginx -s reload`。

### 3.3 本地验证（自签证书，仅测试）

```bash
openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
  -subj "/CN=localhost" \
  -keyout /tmp/gk-ssl/privkey.pem -out /tmp/gk-ssl/fullchain.pem
# 用测试目录替换挂载后 docker run --rm -v /tmp/gk-ssl:/etc/nginx/ssl:ro \
#   -v "$PWD/docker/nginx/nginx.prod.conf:/etc/nginx/nginx.conf:ro" nginx:1.27-alpine nginx -t
```

## 4. 日志与监控（§82/§83）

- **结构化日志**：`application-prod.yml` 开启 `logging.structured.format.console=ecs`，
  每行 JSON（timestamp/level/service/requestId…）。
  禁止输出 password/token/secret/signed URL；生产 SQL 日志降为 WARN。
- **API 延迟 / 5xx**：Nginx `access_log`（含 `$request_time` 可自行扩展）+ 后端 ECS 日志。
  建议宿主机 `docker compose logs -f api` 接入 Loki/ELK；后续可加 Prometheus/Sentry（§83 后续项）。
- **健康检查**：`GET /actuator/health`（容器内直连 8082；对外由 Nginx 返回 404）。
  数据库/Redis 连接状态包含在 health 聚合中（`management.health.db/redis`）。
- **队列失败**：异步任务（缩略图/报告）失败会落库为 `failed` 并记录 error，
  可在后台 `/admin` 的日志页与报告列表查看。

## 5. 备份与恢复（§117/§118）

### 5.1 每日备份

`docker-compose.prod.yml` 的 `backup` 服务启动即备份一次，此后每 24h 执行
`scripts/backup-mysql.sh`：

- `mysqldump --single-transaction --routines --triggers --databases growthkids | gzip -9`
- 输出 `./backups/growthkids-YYYYmmdd-HHMMSS.sql.gz`
- `gzip -t` 完整性校验 + 大小下限校验
- 自动删除超过 `BACKUP_RETENTION_DAYS`（默认 30）天的备份

也可以用宿主 cron 调用（开发环境示例）：

```bash
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3310 BACKUP_DIR=./backups bash scripts/backup-mysql.sh
```

### 5.2 恢复流程（每月至少演练一次）

```bash
# 演练：恢复到临时库并核对表数量（不影响生产库）
MYSQL_HOST=127.0.0.1 MYSQL_PORT=3310 \
  bash scripts/restore-mysql.sh backups/growthkids-20261029-020000.sql.gz growthkids_restore_test
mysql -h127.0.0.1 -P3310 -ugrowthkids -pgrowthkids -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='growthkids_restore_test'"

# 正式恢复：先停写流量，再恢复
docker compose -f docker-compose.prod.yml stop api
MYSQL_HOST=mysql MYSQL_PORT=3306 MYSQL_DATABASE=growthkids \
  bash scripts/restore-mysql.sh backups/<file>.sql.gz
docker compose -f docker-compose.prod.yml start api
```

恢复后检查：`/actuator/health`、关键表行数、管理员可登录、上传文件卷
`storage-data` 未被误删（文件在卷内，与数据库备份独立，需配合对象存储版本化）。

## 6. CI/CD

`.github/workflows/ci.yml` 包含三个 job：

1. **backend**：JDK 21 + MySQL 8 + Redis 7 service 容器，`mvn -B -pl apps/api -am test`
   （137 例单测/集成测试）。
2. **frontend**：pnpm 12 + Node 22，`pnpm lint` / `pnpm typecheck` /
   `pnpm --filter @growthkids/web build`。
3. **docker**：依赖前两者通过后构建 `apps/api/Dockerfile` 与 `apps/web/Dockerfile`
   （不推送，验证镜像可构建）。

生产发布建议：在 CI 中追加镜像推送（GHCR/私有 registry），服务器执行
`docker compose pull && up -d`；数据库迁移由 API 启动时 Flyway 完成，回滚需使用
对应版本的镜像与备份。

## 7. 安全清单（§120 要点）

- [x] API/Web 容器非 root 运行（Dockerfile `USER app`）
- [x] MySQL/Redis 不映射宿主机端口；仅 Nginx 对外
- [x] `/actuator/*` 对外 404；健康检查走内网
- [x] HSTS / nosniff / X-Frame-Options / Referrer-Policy
- [x] 生产密钥无默认值（缺失即启动失败）；首个管理员需显式配置
- [x] 日志不输出密码/token/signed URL（§82）
- [x] 上线体检脚本 `scripts/preflight-prod.sh`（密钥/同域/证书/WHO/编排，`--post` 做运行态复核）
- [x] 演示商品清理脚本 `scripts/clean-demo-data.sh`（V4 种子只清数据、不改迁移）
- [ ] 真实支付网关（PayPal/Stripe）凭据接入后替换 mock（架构决策 9）
- [ ] WHO 官方数据替换合成示例（TASK-008 已知限制，上线前必须完成；preflight 会拦截未替换状态）

## 8. 故障排查

| 现象 | 排查 |
| --- | --- |
| nginx 启动失败 | 证书文件缺失/路径错误：`docker compose logs nginx` |
| api unhealthy | `docker compose logs api`；确认 MySQL/Redis healthy、密钥环境变量齐全 |
| 前端 404 / API 跨域 | `NEXT_PUBLIC_API_URL` 必须为完整同域地址（构建期注入，改后需重建 web 镜像） |
| 备份为空 | 检查 `MYSQL_PASSWORD` 与 mysqldump 权限；脚本会因大小校验直接报错退出 |
| preflight 报 WHO 合成示例 | 把官方 CSV 放到 `data/who-official/` 并设置 `APP_WHO_DATA_FILE=file:/data/who/<文件名>` |
| api 启动即退出，日志含 `WHO data file not found` | `APP_WHO_DATA_FILE` 路径与宿主文件不一致（容器内为 `/data/who/*`） |
| 页面仍显示 example.com 商品 | `bash scripts/clean-demo-data.sh --compose --apply`（dry-run 先看清单） |
| preflight 报私钥与证书不匹配 | 重新放置配对的 `fullchain.pem` / `privkey.pem` |
| 8082 端口被其他项目占用（开发机） | 本项目已容器化隔离；开发冒烟可 `--server.port=8092`（见 IMPLEMENTATION_STATUS Known Issues） |
