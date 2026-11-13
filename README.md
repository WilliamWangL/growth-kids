# GrowthKids

儿童成长记录 + 身高预测平台。

- 记录孩子身高、体重、头围、成长照片、成长故事
- 基于 WHO 公开生长标准的百分位 / Z-score 分析
- 成长曲线、身高预测、年度报告

## 技术栈

| 端 | 技术 |
|---|---|
| Web | Next.js 16 + React 19 + TypeScript + Tailwind CSS |
| API | Java 21 + Spring Boot 3.5 + MyBatis Plus + Flyway |
| 数据库 | MySQL 8（utf8mb4 / utf8mb4_unicode_ci） |
| 缓存 | Redis |
| 图片 | 本地磁盘存储（v1）+ 签名 URL；后续可替换 S3 兼容对象存储 |
| 部署 | Docker + Nginx |

> 主开发规范见 `AI_DEVELOPMENT_SPEC.md`；实施进度见 `docs/IMPLEMENTATION_STATUS.md`。

## 目录结构

```
growth-kids/
├── apps/
│   ├── web/                 Next.js 前端
│   └── api/                 Spring Boot 后端
├── packages/
│   ├── growth-engine/       Java 计算引擎（Z-score / percentile / prediction）
│   ├── types/               TS 共享类型
│   ├── validation/          TS 共享 Zod schema
│   └── ui/                  TS 共享 UI 原语与设计 token
├── data/who/                WHO 官方生长标准数据（导入源）
├── docs/                    实施状态 / API / 数据库 / 引擎文档
├── docker/                  MySQL / Redis / Nginx 配置
├── docker-compose.yml
├── pom.xml                  Maven 聚合（growth-engine + api）
├── pnpm-workspace.yaml
└── package.json
```

## 本地开发（端口已避开本机其他项目）

| 服务 | 端口 |
|---|---|
| Web (dev) | 3005 |
| API | 8082 |
| MySQL | 3310 |
| Redis | 6385 |
| Nginx | 8090 |

```bash
# 1. 启动基础设施（MySQL / Redis / Nginx）
docker compose up -d

# 2. 前端
pnpm install
pnpm dev                       # http://localhost:3005

# 3. 后端
mvn -f pom.xml install -DskipTests   # 首次：先安装 growth-engine 到本地仓库
mvn -f apps/api/pom.xml spring-boot:run   # http://localhost:8082/api/v1/health
```

API 文档（开发环境）：`http://localhost:8082/api/docs`

## 常用命令

```bash
pnpm lint          # 前端 + packages ESLint
pnpm typecheck     # TS 类型检查
pnpm build         # 前端生产构建
mvn -f pom.xml test        # Java 单元测试
mvn -f pom.xml verify      # Java 构建 + 测试
```

## 环境变量

复制 `.env.example` 为 `.env` 并按需修改。生产环境禁止提交 `.env`。
