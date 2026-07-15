# 03. Аутентификация

## Модель

Stateless JWT по **RFC 6750 (Bearer Token)**. Пароли не используются вообще — регистрация занимает 1 запрос.

Два типа токенов:

| Тип | TTL | Claim `type` | Extra claims | Назначение |
|-----|-----|--------------|--------------|-----------|
| **Access** | 15 минут | `ACCESS` | `nickname`, `role`, `createdAt` | `Authorization: Bearer <...>` для REST/STOMP |
| **Refresh** | 14 дней | `REFRESH` | — | Только для `POST /auth/refresh` |

Алгоритм: HS256 (HMAC SHA-256).
Subject: `user.id` (UUID как строка).

## Регистрация

```
POST /api/v1/auth/quick-register
Content-Type: application/json

{
  "nickname": "alice",     // 3..42 символов, обязательно
  "role": "PLAYER"          // "PLAYER" | "ADMIN" | "OBSERVER", default "PLAYER"
                            // "NPC" запрещена (403)
}
```

**200 OK:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 900        // TTL access-токена в секундах (OAuth 2.0 совместимо)
}
```

**Ошибки:**
- `400` — не прошла валидация (`nickname` пустой, короткий, слишком длинный).
- `403` — `role: NPC`.

Сервер возвращает `user.id` только внутри JWT (subject). Извлечь можно декодированием JWT либо через `GET /user/id` (см. ниже).

## Вход (для существующих пользователей)

```
POST /api/v1/auth/quick-login
Content-Type: application/json

{ "id": "550e8400-e29b-41d4-a716-446655440000" }
```

**200 OK:** тот же `{ accessToken, refreshToken, expiresIn }`.

**Ошибки:**
- `400` — не UUID.
- `404` — пользователь не найден.

## Профиль текущего пользователя

```
GET /api/v1/user
Authorization: Bearer <accessToken>
```

**200 OK:**
```json
{
  "id": "550e8400-...",
  "nickname": "alice",
  "role": "PLAYER",
  "createdAt": "2026-07-15T09:00:00.000+00:00"
}
```

Компактнее:
```
GET /api/v1/user/id
Authorization: Bearer <accessToken>
```
Возвращает `{ "id": "550e8400-..." }`.

## Refresh — обновление access-токена

Access-токен живёт 15 минут. Когда истёк:

```
POST /api/v1/auth/refresh
Content-Type: application/json

{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

**200 OK:**
```json
{
  "accessToken": "eyJ...",   // новый access-токен
  "refreshToken": null,       // rotation отключён — используй прежний refresh
  "expiresIn": 900
}
```

**Ошибки:**
- `401` — refresh просрочен / подделан / отозван / это на самом деле access-токен (`type != REFRESH`).

**Важно:** refresh-токен НЕ обновляется. Тот же самый refresh продолжает работать до `exp` (14 дней) даже после многократных вызовов `/auth/refresh`. Rotation можно включить в будущем — API-контракт (`refreshToken: null`) уже под это готов, чтобы фронт не начал полагаться на статический токен.

## Logout — отзыв токена

```
POST /api/v1/auth/logout
Authorization: Bearer <accessToken>
```

**204 No Content** при успехе.

Отзывает `jti` текущего access-токена — токен становится невалидным немедленно даже до `exp`. Refresh-токен при этом НЕ отзывается автоматически — если хочешь полностью выкинуть пользователя, лучше:
1. `POST /auth/logout` с access-токеном.
2. Удалить оба токена с клиента.

## Ролевая матрица

| Endpoint | Требуемая роль |
|----------|----------------|
| `POST /auth/*` | permitAll |
| `GET /user`, `GET /user/id` | authenticated |
| `POST /session` | ADMIN |
| `GET /session/**`, `GET /statistics/**` | ADMIN, PLAYER, OBSERVER |
| `POST /session/{id}/join` | ADMIN, PLAYER |
| `POST /session/{id}/join/observer` | ADMIN, PLAYER, OBSERVER |
| STOMP `SEND /app/session/*/start`, `/close`, `/open`, `/round.start`, `/round.abort` | ADMIN |
| STOMP `SEND /app/session/*/offer.create`, `/make.decision` | PLAYER, ADMIN |
| STOMP `SUBSCRIBE /topic/session/*` | ADMIN, PLAYER, OBSERVER |

## Формат ошибок

Все ошибки — единый JSON:

```json
{
  "timestamp": "2026-07-15T09:00:00.000+00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "JWT токен истёк",
  "path": "/api/v1/user"
}
```

Auth-специфичные коды:
- **401** — токен истёк / подделан / некорректная подпись / некорректная структура / access вместо refresh для `/auth/refresh`.
- **403** — токен валиден, но у пользователя нет нужной роли для этого endpoint.

## Клиентские рекомендации

1. **Хранение**: `accessToken` — в памяти (var/state); `refreshToken` — если браузер, лучше `httpOnly cookie` (нужна поддержка бекенда) или в `localStorage` для dev. Не в `sessionStorage`.
2. **Interceptor**: на 401 из `/api/v1/...` (кроме `/auth/*`) — попытаться refresh, повторить оригинальный запрос. На повторное 401 — вылогинить пользователя.
3. **Preemptive refresh**: за минуту до `exp` (у нас `expiresIn = 900s`, то есть refresh каждые ~14 минут). Не обязательно, но избавит от лишних 401'ов.
4. **WebSocket-переподключение** — если STOMP-соединение упало, переподключиться с текущим access-токеном. Если 401 при CONNECT — refresh + retry.
5. Никакого CSRF-заголовка не требуется.

## Пример полного цикла (curl)

```bash
BASE=http://localhost:8080/api/v1

# 1. Register
REG=$(curl -s -X POST $BASE/auth/quick-register \
  -H 'Content-Type: application/json' \
  -d '{"nickname":"alice","role":"PLAYER"}')
ACCESS=$(echo $REG | jq -r .accessToken)
REFRESH=$(echo $REG | jq -r .refreshToken)

# 2. Get profile
curl -s $BASE/user -H "Authorization: Bearer $ACCESS"

# 3. Refresh access
REF=$(curl -s -X POST $BASE/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}")
ACCESS_NEW=$(echo $REF | jq -r .accessToken)

# 4. Logout
curl -sw '%{http_code}' -X POST $BASE/auth/logout \
  -H "Authorization: Bearer $ACCESS_NEW"
# → 204
```
