# syntax=docker/dockerfile:1.7

# ============================================================
# Stage 1: Build — 利用 Maven 缓存层加速重复构建
# ============================================================
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# ============================================================
# Stage 2: Runtime — 最小化镜像 + 非 root 用户 + 健康检查
# ============================================================
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r blueprint \
    && useradd -r -g blueprint blueprint

COPY --from=builder /build/target/*.jar app.jar
RUN chown blueprint:blueprint app.jar

USER blueprint
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
