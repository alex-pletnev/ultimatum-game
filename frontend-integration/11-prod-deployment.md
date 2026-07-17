# 11. Prod deployment — как обращаться к backend'у в production

Документ для интегратора фронта. Backend уже задеплоен в Yandex.Cloud (T-090),
доступен по HTTPS без VPN из РФ. Ниже — всё что нужно, чтобы фронт с GitHub
Pages смог с ним работать.

## 11.1 URLs

| Что | URL |
|-----|-----|
| API base | `https://158-160-48-113.nip.io/api/v1` |
| WebSocket (STOMP over WS) | `wss://158-160-48-113.nip.io/api/v1/ws` |
| Health-check | `https://158-160-48-113.nip.io/api/v1/actuator/health` |

Домен `158-160-48-113.nip.io` — DNS-обёртка над static public IP через
[nip.io](https://nip.io/) сервис (`<ip-with-dashes>.nip.io` резолвится в этот IP).
HTTPS-сертификат — от Let's Encrypt через Caddy на VM. Валидный, никаких
`InsecureRequestWarning` от браузера не будет.

## 11.2 Env-vars для Vite

Фронт уже читает две переменные (`src/api/config.ts`):

```
VITE_API_BASE_URL=https://158-160-48-113.nip.io/api/v1
VITE_WS_URL=wss://158-160-48-113.nip.io/api/v1/ws
```

В GitHub → Settings → Secrets and variables → **Actions → Variables** (не Secrets — не sensitive):
- добавить `VITE_API_BASE_URL` и `VITE_WS_URL` с этими значениями.

В workflow — прокинуть через `env:` block в build-step. Пример:

```yaml
- run: pnpm build
  env:
    VITE_API_BASE_URL: ${{ vars.VITE_API_BASE_URL }}
    VITE_WS_URL: ${{ vars.VITE_WS_URL }}
```

## 11.3 CORS whitelist

Бекенд разрешает origin `https://alex-pletnev.github.io` (задано через
`APP_CORS_ORIGINS` при деплое). Если фронт будет опубликован по другому URL
(project-page vs user-page vs custom domain) — сообщи, перевыкачу бэк.

Что уже разрешено:
- HTTP methods: `GET, POST, PUT, DELETE, OPTIONS`
- Headers: `*` (любые)
- Credentials: `true`
- WS handshake origin: тот же список через `setAllowedOriginPatterns`

Preflight `OPTIONS /*` возвращает 200 без токена (стандартно для CORS).

## 11.4 Auth — JWT

Полный контракт — `03-auth.md`. Кратко для prod:

- **Регистрация**: `POST /auth/quick-register` body `{nickname, password, role}` →
  201 с `{accessToken, refreshToken, expiresIn}` (expiresIn = 900s = 15 min).
- **Логин**: `POST /auth/quick-login` (одинаковый response).
- **Access-токен** живёт 15 минут. Кладется в `Authorization: Bearer <token>` на
  все REST-запросы и в STOMP CONNECT headers.
- **Refresh**: `POST /auth/refresh` body `{refreshToken}` → новая пара. Refresh
  живёт 14 дней.
- **Logout**: `POST /auth/logout` c bearer'ом — invalidate'ит access-jti.

Хранить пару токенов в `localStorage` (ключи на выбор фронта, например
`ultimatum.access` / `ultimatum.refresh`). При 401 на REST/WS — попробовать
refresh, при 401 на refresh — редирект на login.

## 11.5 STOMP over WebSocket

Полный контракт — `05-websocket-api.md`. Prod-специфика:

- URL: `wss://158-160-48-113.nip.io/api/v1/ws` (SockJS **не** нужен — сервер
  выдаёт native WebSocket).
- CONNECT headers: `Authorization: Bearer <accessToken>`.
- Пример клиента (`@stomp/stompjs`):
  ```ts
  import { Client } from '@stomp/stompjs';
  const client = new Client({
    brokerURL: import.meta.env.VITE_WS_URL,
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    reconnectDelay: 5000,
  });
  client.activate();
  ```
- Handshake через Caddy → :8080 → Spring WebSocket handler. Caddy настроен на
  `websocket` upgrade автоматически, никаких дополнительных заголовков не нужно.

## 11.6 Startup latency (важно)

- **Обычный запрос**: ~50-200ms (Yandex ru-central1-a → пользователь).
- **Первый запрос после рестарта VM** (либо `docker restart ultimatum-game`):
  до **30 секунд** — Spring Boot стартует 27s, Flyway `validate` + Hikari
  подключение к Managed PG. В это окно фронт может увидеть `502 Bad Gateway`
  (Caddy → app не отвечает) или таймауты.
- **Никакой warmup-логики** на бэке нет — просто закладывай retry на первый
  запрос в session (например, при loading auth token или initial /session list).

## 11.7 Known-issues (не блокеры, но полезно знать)

- **Несуществующие пути возвращают 500, а не 404** (T-098 в backend-репо):
  например `GET /api/v1/does-not-exist` → 500 «Внутренняя ошибка сервера»
  вместо 404. Причина — `NoResourceFoundException` не обрабатывается в
  `GlobalExceptionsHandler`. Будет починено, но пока — не полагаться на статус
  как индикатор existence.
- **Springwolf при старте пишет ERROR в логи** про `SessionAdminWsController::abortCurrentRound`
  (T-099) — не блокер, WS-endpoint'ы работают, просто грязнит логи.
- **Real-endpoint names**: `/auth/quick-register` и `/auth/quick-login` (не
  `/auth/register` / `/auth/login`). Если пишешь curl вручную для теста —
  используй правильные, `POST /auth/register` вернёт 500 (см. предыдущий пункт).

## 11.8 Смoke-check (без VPN РФ должно работать)

```bash
API=https://158-160-48-113.nip.io/api/v1
curl -s "$API/actuator/health"
# → {"status":"UP"}

curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"nickname":"smoke-'$RANDOM'","password":"smoke123","role":"PLAYER"}' \
  "$API/auth/quick-register"
# → {"accessToken":"eyJ...","refreshToken":"eyJ...","expiresIn":900}
```

Если `curl` даёт `Connection refused` или таймаут — сеть не пропускает исходящий
HTTPS на этот IP (провайдер / DPI). VPN РФ обычно решает.

## 11.9 GH Pages deploy — что нужно фронту дополнительно

Для деплоя фронта на GH Pages помимо env-vars нужны:

1. **`vite.config.ts`** — `base: '/ultimatum-game-ui/'` (иначе assets 404 под
  подпапкой project-page).
2. **Router**: `<BrowserRouter basename={import.meta.env.BASE_URL}>` **или**
  HashRouter (проще — GH Pages не умеет SPA-fallback без `404.html`-хака).
3. **`.github/workflows/deploy.yml`** — build с env-vars из repo variables +
  `actions/deploy-pages@v4`.
4. **Settings → Pages → Source: GitHub Actions**.

## 11.10 Что делать если бэк вернул ошибку

- **502 Bad Gateway** от Caddy — контейнер лёг или ещё стартует. Retry через
  5-10 секунд. Если через 30s не поднимается — сообщи, посмотрю в prod-логах.
- **503 / connection timeout** — VM может быть в ребуте. Проверю через `yc`.
- **401 на REST/WS** — токен просрочен (или отозван logout'ом). Trigger refresh.
- **500 на POST/GET** — либо баг бэка (см. Known-issues), либо валидация не
  прошла. Смотри `07-error-handling.md`. Тело обычно содержит осмысленный
  `message`.
- **CORS-ошибка в браузерной консоли** — origin не в whitelist'е. Сообщи какой
  URL реальный — переконфигурирую бэк.

## См. также

- [03-auth.md](03-auth.md) — JWT flow детально.
- [04-rest-api.md](04-rest-api.md) — все REST endpoints.
- [05-websocket-api.md](05-websocket-api.md) — STOMP каналы.
- [07-error-handling.md](07-error-handling.md) — коды ошибок.
- [09-integration-flows.md](09-integration-flows.md) — сквозные сценарии.
- [specs/openapi.json](specs/openapi.json) / [specs/asyncapi.json](specs/asyncapi.json) — для codegen'а клиента.
