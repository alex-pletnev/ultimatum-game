---
id: T-090
title: Prod-deploy readiness — externalize configs, Dockerfile, CORS/WS для GitHub Pages фронта
status: pending
priority: high
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/resources/application.properties
  - src/main/resources/application-prod.properties
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/SecurityConfiguration.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/WebSocketConfig.kt
  - build.gradle.kts
  - compose.yaml
related_docs:
  - docs/tasks/T-044-adopt-db-migrations.md
tags: [infra, deploy, security]
---

## Контекст

Фронт хостится на GitHub Pages (HTTPS, домен `https://<user>.github.io`), локально
поднимать бэк каждый раз возможности нет — нужен постоянно доступный remote deploy
на бесплатном PaaS (кандидаты: Fly.io + Neon PG, Render, Railway).

Текущее состояние бэка не деплоится «как есть»: нет prod datasource, CORS/WS зашиты
на localhost, порт фиксированный, Dockerfile отсутствует, схема БД накатывается
через `hibernate.ddl-auto=update` (без миграций — риск на проде).

Миграции (T-044) — hard blocker: без них любое поле, добавленное локально, приедет
на прод как «неизвестное изменение» и разъедется.

## Acceptance criteria

### Externalize prod-config
- [ ] `application-prod.properties` — `spring.datasource.{url,username,password,driver-class-name}` из env-vars (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
- [ ] `spring.docker.compose.enabled=false` в prod (гарантия что дев-плагин не сработает).
- [ ] `server.port=${PORT:8080}` для совместимости с PaaS (Render/Railway/Koyeb).
- [ ] `management.endpoints.web.exposure.include` в prod — только `health,prometheus` (не info).

### CORS / WebSocket origins
- [ ] `SecurityConfiguration.corsConfigurationSource` — origin-список из env (`APP_CORS_ORIGINS`, comma-separated), default `http://localhost:[*]` для dev.
- [ ] `WebSocketConfig.registerStompEndpoints` — `setAllowedOriginPatterns` (не `setAllowedOrigins("*")`) с тем же env-списком.
- [ ] В prod-профиле задокументировано что `APP_CORS_ORIGINS=https://<gh-user>.github.io`.

### Dockerfile
- [ ] Multi-stage: `gradle:jdk21` → build → `eclipse-temurin:21-jre` (или distroless-java21).
- [ ] `ENTRYPOINT` c `-XX:MaxRAMPercentage=75.0` для free-tier контейнеров 512MB.
- [ ] Локально проверено: `docker build -t ultimatum-game . && docker run -e ... -p 8080:8080 ultimatum-game` — старт до READY.

### Миграции (blocker)
- [ ] Закрыт T-044 (Flyway baseline от текущей schema, `ddl-auto=validate`). Без него prod-deploy не начинать.

### Хостинг + smoke-test
- [ ] Выбран и обоснован хостинг (по умолчанию Fly.io + Neon PG). Задокументирован в `docs/12-deploy.md` (или аналог).
- [ ] Задеплоено. Secrets заданы через CLI хостера (не в git).
- [ ] Прогнан smoke-test: `/actuator/health` 200, `/auth/register` → `/auth/login` → JWT работает, STOMP `wss://` handshake проходит.
- [ ] Frontend (github.io) сконфигурирован на прод URL, живой end-to-end сценарий: create session → join → offer/decision через WS.

### Docs
- [ ] `docs/12-deploy.md` (новый) — как задеплоить с нуля, env-vars, secrets, откат.
- [ ] `docs/11-known-gaps.md` — обновлён (убрать пункт «нет prod-конфига»).
- [ ] `CLAUDE.md` — если появились prod-специфичные правила (например «не коммитить prod URLs»), добавить в SPECIFIC_RULES.

## План

Порядок — от least-risky к most-risky, с явным gate'ом после каждой фазы:

1. **Config externalization** (safe, локально проверяется): datasource из env, CORS из env, port из env, WS origins из env. Локально — прогнать с `SPRING_PROFILES_ACTIVE=prod DB_URL=... APP_CORS_ORIGINS=... ./gradlew bootRun`.
2. **Dockerfile** (safe, локально проверяется): multi-stage build, локальный `docker run`.
3. **T-044 миграции** (изолированная задача, отдельным брейнштормом): Flyway baseline, `validate`. Без этого шаг 4 не начинать.
4. **Хостинг**: завести аккаунт Fly.io + Neon (или альтернатива), поднять PG, задеплоить app, задать secrets. Здесь high-stakes зона — pre-flight обязателен.
5. **Frontend cutover**: hardcode прод URL, redeploy Pages, end-to-end smoke.
6. **Docs**: `12-deploy.md` с runbook'ом (кто и как ротирует secrets, как логи смотреть).

## Лог

- 2026-07-16: заведено пользователем. Blocker — T-044 (миграции). Приоритет high — блокирует показ проекта фронтом.
