# syntax=docker/dockerfile:1
# GrowthKids API 生产镜像（TASK-020，§80）
# 构建上下文为仓库根：docker build -f apps/api/Dockerfile -t growthkids-api .
#
# 说明：
# - 多阶段构建：Maven 编译（含 growth-engine 子模块）→ JRE 21 运行
# - 依赖层单独缓存（先复制 POM 再 go-offline）
# - 运行用户非 root（§120 安全基线）；/app/storage 由 compose volume 挂载
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 1) 仅复制 POM，预热依赖下载层
COPY pom.xml ./
COPY packages/growth-engine/pom.xml packages/growth-engine/
COPY apps/api/pom.xml apps/api/
RUN mvn -B -q -pl apps/api -am dependency:go-offline

# 2) 复制源码并打包（测试在 CI 独立执行）
COPY packages/growth-engine packages/growth-engine
COPY apps/api apps/api
RUN mvn -B -pl apps/api -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime

# curl 仅用于容器健康检查；安装后清理 apt 缓存
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --create-home app

WORKDIR /app
COPY --from=build /build/apps/api/target/growthkids-api-*.jar app.jar
RUN mkdir -p /app/storage && chown -R app:app /app
USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"

EXPOSE 8082
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS http://localhost:8082/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
