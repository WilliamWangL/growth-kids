# syntax=docker/dockerfile:1
# GrowthKids Web 生产镜像（TASK-020，§80）
# 构建上下文为仓库根：docker build -f apps/web/Dockerfile -t growthkids-web .
#
# 说明：
# - Next.js standalone 输出（next.config.ts output: "standalone"），运行镜像不含构建工具链
# - pnpm + workspace：先装依赖层再复制源码
# - NEXT_PUBLIC_API_URL 在构建期注入（与 Nginx 同域推荐 "/" 或 https://域名）
FROM node:22-alpine AS deps
RUN corepack enable
WORKDIR /repo
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY apps/web/package.json apps/web/
COPY packages/types/package.json packages/types/
COPY packages/validation/package.json packages/validation/
COPY packages/ui/package.json packages/ui/
RUN pnpm install --frozen-lockfile

FROM node:22-alpine AS build
RUN corepack enable
WORKDIR /repo
COPY --from=deps /repo/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_API_URL=http://localhost:8082
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN pnpm --filter @growthkids/web build

FROM node:22-alpine AS runner
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app

# standalone 内含 apps/web/server.js 与追踪到的 workspace 依赖
COPY --from=build /repo/apps/web/.next/standalone ./
COPY --from=build /repo/apps/web/.next/static ./apps/web/.next/static
COPY --from=build /repo/apps/web/public ./apps/web/public

USER app
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -qO- http://127.0.0.1:3000/ >/dev/null || exit 1

CMD ["node", "apps/web/server.js"]
