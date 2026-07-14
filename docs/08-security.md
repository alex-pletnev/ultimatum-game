# 08. Безопасность

## Модель

Stateless JWT-аутентификация по **RFC 6750 (Bearer Token)**. Пароли не используются. Anti-impersonation отсутствует **by design** (пет-проект): любой залогиненный пользователь может слать запросы от имени другого — это принятый риск (см. `docs/11-known-gaps.md`, задача T-010).

Слои:

1. **HTTP filter chain** — валидация JWT на каждый REST-запрос.
2. **STOMP CONNECT** — валидация JWT при установке соединения.
3. **Method-level** — `@PreAuthorize` на контроллерах + `WebSocketSecurityConfig` для STOMP (ролевые матчеры).

CSRF отключён; session-membership STOMP-проверок нет; персональной проверки `userId` в путях топиков нет.

## HTTP Filter Chain

Файл: `configs/SecurityConfiguration.kt`.

```
Request
  → CORS filter
  → (CSRF disabled)
  → JwtAuthenticationFilter        ← ставится до UsernamePasswordAuthenticationFilter
  → Spring Security AuthorizationFilter (@PreAuthorize)
  → Controller
```

### CORS — `SecurityConfiguration.kt`

```kotlin
CorsConfiguration().apply {
    allowedOriginPatterns = listOf("http://localhost:[*]")
    allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
    allowedHeaders = listOf("*")
    allowCredentials = true
}
```

Регистрируется на `/**`. Отдельно для STOMP: `setAllowedOrigins("*")` в `WebSocketConfig.kt`.

### CSRF — отключён

```kotlin
.csrf { it.disable() }
```

Endpoint `/csrf` не существует, заголовок `X-CSRF-TOKEN` не требуется, mutating-запросы принимаются с одним `Authorization: Bearer <jwt>`.

### JwtAuthenticationFilter — `configs/JwtAuthenticationFilter.kt`

Алгоритм:
1. Читает `Authorization: Bearer <token>`.
2. Если header отсутствует или без `Bearer ` — пропускает без аутентификации.
3. `JwtService.extractUsername(token)` → UUID.
4. `UserService.getUserDetailService()` → `User`.
5. `JwtService.isTokenValid(token, user)` — проверка exp + sub.
6. Собирает `UsernamePasswordAuthenticationToken` с `SimpleGrantedAuthority("ROLE_" + role.name)`, ставит в `SecurityContextHolder`.
7. Ошибка валидации → `AccessDeniedException`.

### Публичные vs защищённые endpoints — `SecurityConfiguration.kt`

Публичные (`permitAll`):
- `/actuator/**`
- `/auth/**`
- `/v3/api-docs/**`, `/swagger-ui/**`, `/springwolf/**` (OpenAPI/AsyncAPI docs)
- `/ws/**` (WebSocket handshake — реальная авторизация в STOMP CONNECT)
- `OPTIONS /**` (CORS preflight)

Защищённые: всё остальное требует валидного JWT. Ролевой контроль — через `@PreAuthorize` на контроллерах:

| Аннотация | Где |
|-----------|-----|
| `hasRole('ADMIN')` | `POST /session` и все WS `SessionAdminWsController` |
| `hasAnyRole('ADMIN','PLAYER','OBSERVER')` | GET `/session/**`, `/statistics/**` |
| `hasAnyRole('ADMIN','PLAYER')` | `POST /session/{id}/join`, WS `offer.create`, `make.decision` |
| без ограничений (только `authenticated`) | `GET /user`, `GET /user/id` |

## JWT

Файл: `services/JwtService.kt`.

| Параметр | Значение | Строка |
|----------|----------|--------|
| Алгоритм | HS256 (HMAC SHA-256) | `:86` |
| Ключ | base64-decoded `${token.signing.key}` (env `JWT_SIGNING_KEY`) | `:21-22`, `:91-92` |
| TTL | 365 дней | `:29` |
| Subject | `user.id` (UUID как строка) | `:97-102` |
| Extra claims | `nickname`, `role`, `createdAt` | `:36-39` |
| `jti` | `UUID.randomUUID()` — идентификатор токена для revocation-check | `:100` |

Валидация: `isTokenValid` (`:60-68`) — `!isTokenExpired && extractUsername == userDetails.username && !tokenRevocationService.isRevoked(jti)`.

### Revocation — `services/TokenRevocationService.kt`

In-memory `Set<UUID>` отозванных `jti`. Заполняется через `POST /auth/logout` (см. `services/AuthService.logout`).

Ограничения MVP:
- Хранится в памяти — рестарт приложения обнуляет revoked-list. Отозванный токен снова валиден до `exp`.
- Для прода — заменить бэкенд на Redis/DB (сохранив интерфейс `revoke(jti)` / `isRevoked(jti)`).

### POST `/auth/logout`

- Header: `Authorization: Bearer <jwt>`.
- 204 при валидном токене; отзывает `jti` и эмитит `UserLoggedOut(userId)`.
- Токен без `jti` (issued до релиза этой фичи) — событие эмитится, но revocation-запись не заводится.
- Auth-семантика ошибок унаследована от глобальных handler'ов проекта (403 через `RestAccessDeniedHandler`, 401 только для `ExpiredJwtException`); отдельного 401-entry-point'а не заведено.

### `application.properties`

```
token.signing.key=${JWT_SIGNING_KEY}
```

Секрет обязателен в env — без него приложение не поднимется.

## STOMP: авторизация

Регистрация интерцепторов: `configs/WebSocketConfig.kt` (`configureClientInboundChannel`) — один интерцептор.

### 1. JwtStompChannelInterceptor — `configs/JwtStompChannelInterceptor.kt`

Работает **только на CONNECT**.

- Достаёт `Authorization: Bearer <token>` из STOMP-заголовков.
- `JwtService.extractUsername` → UUID.
- `JwtService.isTokenValid`.
- Ставит `UsernamePasswordAuthenticationToken` **и в `SecurityContextHolder`, и в `accessor.user`** — контекст для `@PreAuthorize`, `accessor.user` доступен по всей сессии STOMP.

Ошибки:
- Нет JWT → `AuthenticationCredentialsNotFoundException`.
- Невалидный → `InvalidJwtException`.

### 2. WebSocketSecurityConfig — `configs/WebSocketSecurityConfig.kt`

Аннотация: `@EnableWebSocketSecurity`. Строит `MessageMatcherDelegatingAuthorizationManager` с ролевыми правилами:

| Destination | Роли |
|-------------|------|
| `/app/session/*/offer.create`, `/app/session/*/make.decision` | PLAYER, ADMIN |
| `/app/session/*/start`, `/close`, `/open`, `/round.start` | ADMIN |
| `/topic/session/*/sessionStatus`, `/roundStatus`, `/offerCreated`, `/decisionMade` | PLAYER, OBSERVER, ADMIN |
| `/topic/session/*/player/*/offer` | PLAYER, ADMIN |
| `CONNECT`, `HEARTBEAT`, `UNSUBSCRIBE`, `DISCONNECT` | permitAll |

Никакой проверки, что `userId` в пути `/player/{userId}/offer` совпадает с текущим пользователем, **нет**. Достаточно роли.

## Итоговая цепочка авторизации для STOMP-сообщения

```
1. HTTP handshake → SecurityConfiguration permits /ws/**
2. STOMP CONNECT → JwtStompChannelInterceptor (JWT valid?)
3. STOMP SEND/SUBSCRIBE:
   a. WebSocketSecurityConfig (у роли есть право на destination?)
4. @MessageMapping метод контроллера
```

## Обработчик 403 — `exceptions/RestAccessDeniedHandler.kt`

Возвращает JSON `ApiErrorResponse` со статусом 403 вместо стандартной HTML-страницы Spring Security.

## Модель пользователя

- `User implements UserDetails` — `model/User.kt:10-59`.
- `getUsername()` → `id.toString()`.
- `getPassword()` → `""`.
- `getAuthorities()` → `[SimpleGrantedAuthority("ROLE_" + role.name)]`.

## Стандарт

**RFC 6750 (OAuth 2.0 Bearer Token Usage)** — клиент шлёт `Authorization: Bearer <JWT>`, сервер валидирует токен и достаёт `sub` → identity. Никаких дополнительных anti-impersonation слоёв.

## Известные security-моменты

Все — задокументированы в `docs/11-known-gaps.md`:
- Impersonation принята как допустимый риск (пет-проект, T-010).
- TTL JWT 365 дней, нет refresh-токенов.
- CORS `http://localhost:[*]` — только для dev.
- `WebSocketConfig.setAllowedOrigins("*")` — не подходит для prod.
- `logging.level.org.springframework.security=DEBUG` — чувствительно.
- Actuator полностью открыт (`management.endpoints.web.exposure.include=*`).
