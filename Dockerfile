# syntax=docker/dockerfile:1.7

# ---- Stage 1: build ----------------------------------------------------------
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace

# Кэш-слой зависимостей: сначала манифесты, потом исходники.
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle gradlew gradlew.bat ./
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon dependencies -q || true

COPY --chown=gradle:gradle src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle \
    ./gradlew --no-daemon bootJar -x test

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
