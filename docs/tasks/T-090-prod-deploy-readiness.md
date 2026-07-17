---
id: T-090
title: Prod-deploy readiness — externalize configs, Dockerfile, CORS/WS для GitHub Pages фронта
status: in_progress
priority: high
created: 2026-07-16
updated: 2026-07-17
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
5. **Frontend cutover**: hardcode прод URL, redeploy Pages, end-to-end smoke. См. подробную спеку ниже.
6. **Docs**: `12-deploy.md` с runbook'ом (кто и как ротирует secrets, как логи смотреть).

## Phase 5 — Frontend deploy to GitHub Pages (спецификация)

Артефакт для интегратора фронта. Бэк уже задеплоен (Phase 4), известен базовый URL,
условно `https://ultimatum-game-api.fly.dev` (далее — `${API_BASE}`). Тот же хост
обслуживает и REST, и STOMP-over-WS через один порт под общим context-path `/api/v1`.

### 5.1 Environment / build-time конфиг

Фронт держит два endpoint'а как build-time переменные (не хардкод в JS):

| Переменная | Prod-значение | Dev-значение |
|------------|---------------|--------------|
| `VITE_API_BASE_URL` (или `REACT_APP_*`) | `https://ultimatum-game-api.fly.dev/api/v1` | `http://localhost:8080/api/v1` |
| `VITE_WS_URL` (или `REACT_APP_*`) | `wss://ultimatum-game-api.fly.dev/api/v1/ws` | `ws://localhost:8080/api/v1/ws` |

- Prod-values инжектятся в GitHub Actions build-step через `secrets`/`vars` (не в
  git). Пример: `env: { VITE_API_BASE_URL: ${{ vars.API_BASE_URL }} }`.
- Fetch/axios-клиент строится из `VITE_API_BASE_URL`. STOMP-клиент — из `VITE_WS_URL`.

### 5.2 GitHub Pages hosting particulars

- Прод-домен: `https://<gh-user>.github.io/<repo>/` (project-page) **или**
  `https://<gh-user>.github.io/` (user-page). Оба варианта — валидные CORS origin'ы.
- Роутер фронта — HashRouter (`#/session/123`) или BrowserRouter с
  `basename: '/<repo>'`. GH Pages не умеет SPA-fallback без `404.html`-хака —
  проще HashRouter.
- Static-asset paths — относительные (`base: './'` в Vite, `homepage: '.'` в CRA),
  чтобы работало под подпапкой `/<repo>/`.
- Build published из `gh-pages` бранча или GitHub Actions с `actions/deploy-pages`.

### 5.3 CORS + WebSocket handshake — бэковая сторона

Проверка что бэк принимает GitHub Pages origin:

- В Fly.io secrets задано `APP_CORS_ORIGINS=https://<gh-user>.github.io` (без
  path'а — origin это scheme+host+port). Comma-separated если origin'ов несколько
  (напр. staging + prod).
- WS: `setAllowedOriginPatterns` с тем же env-списком — StockJS/native WS
  handshake пойдёт с `Origin: https://<gh-user>.github.io`, бэк должен пропускать.
- Preflight (OPTIONS): `SecurityConfiguration.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()` — уже есть.

Smoke-проверка со стороны фронта (curl-заготовка):

```bash
curl -i -X OPTIONS "$API_BASE/auth/register" \
  -H "Origin: https://<gh-user>.github.io" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: content-type"
# ожидаем: HTTP/1.1 200, Access-Control-Allow-Origin: https://<gh-user>.github.io
```

### 5.4 Auth / JWT storage

- JWT кладётся в `localStorage` под ключом `ultimatum.jwt` (или через `sessionStorage`
  если требуется short-lived). Сервер JWT-only (T-010), CSRF отсутствует.
- Каждый REST-запрос — `Authorization: Bearer ${jwt}` в headers.
- STOMP-CONNECT — заголовок `Authorization: Bearer ${jwt}` через `stompClient.connectHeaders`
  (см. `frontend-integration/03-auth.md`, «STOMP auth»).
- 401 на REST/WS — фронт триггерит `/auth/refresh`; если и refresh 401 — редирект
  на login. Логика уже описана в `frontend-integration/07-error-handling.md`.

### 5.5 Deploy workflow (GitHub Actions)

Минимальный `.github/workflows/deploy.yml` на стороне фронта:

```yaml
on:
  push:
    branches: [main]
jobs:
  deploy:
    runs-on: ubuntu-latest
    permissions: { contents: read, pages: write, id-token: write }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20 }
      - run: npm ci
      - run: npm run build
        env:
          VITE_API_BASE_URL: ${{ vars.API_BASE_URL }}
          VITE_WS_URL: ${{ vars.WS_URL }}
      - uses: actions/upload-pages-artifact@v3
        with: { path: dist }
      - uses: actions/deploy-pages@v4
```

### 5.6 End-to-end smoke-scenario

После деплоя фронта — прогнать вручную:

1. Открыть `https://<gh-user>.github.io/<repo>/` — страница грузится, нет 4xx/5xx
   на static assets в DevTools/Network.
2. `POST /auth/register` → 201 + JWT. Токен виден в `localStorage`.
3. Reload — JWT сохранён, редирект на «залогинен».
4. `POST /session` (админом) → 201 с `sessionId`.
5. STOMP: `wss://…/api/v1/ws` handshake 101 Switching Protocols, CONNECT ok.
   SUBSCRIBE `/topic/session/{id}/*` — фрейм принят.
6. Играть один раунд (offer → decision) — фрейм `/topic/roundUpdated` приходит,
   UI обновляется. Отдельно проверить `/user/queue/errors` доставляет ошибки
   валидации (T-050).
7. `POST /auth/logout` → JWT revoked; повторный REST 401 → refresh 401 → редирект.

### 5.7 Acceptance для интегратора фронта

- [ ] Build читает `VITE_API_BASE_URL` / `VITE_WS_URL` из env, дефолт-значения — dev.
- [ ] `main`-push фронт-репо публикует `dist/` на GH Pages через Actions.
- [ ] Prod-URL забиты в repo `vars`, а не в git.
- [ ] Все 7 шагов smoke-сценария зелёные.
- [ ] Backend origin (`APP_CORS_ORIGINS`) обновлён на Fly.io: `flyctl secrets set APP_CORS_ORIGINS=https://<gh-user>.github.io`.
- [ ] Ссылка на прод-фронт добавлена в `README.md` этого репо (секция «Live demo»).

## Лог

- 2026-07-16: заведено пользователем. Blocker — T-044 (миграции). Приоритет high — блокирует показ проекта фронтом.
- 2026-07-17: T-044 закрыт (blocker снят). Статус → in_progress. Добавлена спецификация Phase 5 (Frontend deploy to GitHub Pages) — контракт для интегратора фронта: env-vars, GH Actions workflow, CORS/WS handshake детали, JWT storage/refresh, end-to-end smoke-сценарий.
