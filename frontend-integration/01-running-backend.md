# 01. Запуск и перезапуск бекенда

## Требования

- **JDK 21** — обязательно. Если несколько JDK — через `JAVA_HOME` явно указать 21.
- **Docker** — для PostgreSQL. На macOS удобнее всего `colima` (или Docker Desktop).
- **JWT_SIGNING_KEY** — env-переменная, любая base64-строка ≥256 бит.

## Одноразовая настройка (macOS)

```bash
# JDK 21 (пример через brew)
brew install openjdk@21

# в ~/.zshrc или ~/.bashrc (чтобы не выставлять каждый раз):
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home

# JWT secret — dev-ключ (для локальной разработки любой годится)
export JWT_SIGNING_KEY='dGVzdC1qd3Qtc2lnbmluZy1rZXktZm9yLXNwZWMtc25hcHNob3QtZ2VuZXJhdGlvbi1vbmx5LXVsdGltYXR1bS1nYW1lLXRlc3Q='

# Docker (если не Docker Desktop)
brew install colima
```

Свежий JWT-ключ, если хочется:
```bash
openssl rand -base64 64
```

## Запуск

```bash
# Docker daemon поднять если не работает
colima start

# Запуск бекенда (в отдельном терминале, оставить работать)
cd /path/to/ultimatum-game
./gradlew bootRun
```

- Spring Boot автоматически поднимает Postgres через `spring-boot-docker-compose` (файл `compose.yaml` в корне репо).
- Первый запуск после `git pull` может занять до минуты (kapt).
- Прогретый запуск — 15–20 секунд.
- Готовность: увидеть в логах `Started UltimatumGameApplication in X seconds`.

## Проверка что всё поднято

```bash
curl -s http://localhost:8080/api/v1/actuator/health
# → {"status":"UP"}

# Регистрация тестового пользователя
curl -s -X POST http://localhost:8080/api/v1/auth/quick-register \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"alice","role":"PLAYER"}'
# → {"accessToken":"eyJ...","refreshToken":"eyJ...","expiresIn":900}
```

## Точки входа

| Что | URL |
|-----|-----|
| REST base | `http://localhost:8080/api/v1` |
| WebSocket (STOMP) | `ws://localhost:8080/api/v1/ws` |
| Swagger UI (REST) | `http://localhost:8080/api/v1/swagger-ui.html` |
| Springwolf UI (WS) | `http://localhost:8080/api/v1/springwolf/asyncapi-ui.html` |
| OpenAPI JSON | `http://localhost:8080/api/v1/v3/api-docs` |
| Health-check | `http://localhost:8080/api/v1/actuator/health` |
| Prometheus метрики | `http://localhost:8080/api/v1/actuator/prometheus` |

## Перезапуск

Devtools не подключён, поэтому изменения в коде бекенда требуют ручного перезапуска:

```bash
# Ctrl+C в терминале с bootRun, потом снова:
./gradlew bootRun
```

Если поменял entity-класс — Hibernate `ddl-auto=update` подхватит новое поле на следующем старте (добавит колонку). Удалённые поля/колонки не сносятся.

## Сброс БД (если что-то в данных пошло не так)

```bash
docker-compose down -v   # -v удаляет volume, все данные пропадут
docker-compose up -d
./gradlew bootRun
```

## CORS — уже настроен для dev

Фронт заведётся из любого `http://localhost:*` — сужать/добавлять origin'ы не надо. Для prod настройки — отдельная задача (не в scope MVP).

## Типичные проблемы

**`java: cannot find symbol`** — не поднят JDK 21. Проверить `echo $JAVA_HOME`. Если пусто или ≠21 — переэкспортировать.

**`docker: command not found` или `Cannot connect to the Docker daemon`** — запустить `colima start` (или Docker Desktop).

**`token.signing.key must not be null`** — не установлен `JWT_SIGNING_KEY`. Экспортировать в текущем shell до `./gradlew bootRun`.

**Порт 8080 занят** — убить процесс: `lsof -ti :8080 | xargs kill -9`.

**Порт 5432 (Postgres) занят локальным Postgres** — убить локальный, или поменять маппинг в `compose.yaml`.

**Бек «висит» после запуска Gradle** — bootRun блокирующий. Оставить терминал открытым; открыть другой для curl'ов и разработки фронта.

## Заготовка для frontend dev-скрипта

Если хочется одну команду для «поднять всё»:

```bash
#!/bin/bash
# dev-up.sh
set -e
export JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home
export JWT_SIGNING_KEY='dGVzdC1qd3Qtc2lnbmluZy1rZXktZm9yLXNwZWMtc25hcHNob3QtZ2VuZXJhdGlvbi1vbmx5LXVsdGltYXR1bS1nYW1lLXRlc3Q='
colima status || colima start
cd /path/to/ultimatum-game
./gradlew bootRun
```
