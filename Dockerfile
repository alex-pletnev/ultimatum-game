# ---- Stage 1: build ----------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Wrapper фиксирует версию Gradle (см. gradle-wrapper.properties), поэтому base
# image — просто JDK, а не gradle:*. Warmup-слой зависимостей не выделяем: без
# BuildKit-cache-mount он даёт лишний Docker-layer без ощутимого выигрыша, а с
# `|| true` глотал бы ошибки download.
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./
COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

# ---- Stage 2: runtime --------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build --chown=app:app /workspace/build/libs/*.jar /app/app.jar
USER app

EXPOSE 8080
# T-090: MaxRAMPercentage=75 подгоняет heap под контейнерный memory-limit
# (актуально на free-tier'ах Fly.io/Render/Koyeb где RAM 256-512 MB).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
