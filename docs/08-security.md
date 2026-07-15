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

### CSRF — отключён (в двух местах, T-070)

**HTTP-чейн** — `SecurityConfiguration`:
```kotlin
.csrf { it.disable() }
```

**STOMP-канал** — `WebSocketSecurityConfig`. `@EnableWebSocketSecurity` в Spring Security 6 регистрирует свой `CsrfChannelInterceptor` в `clientInboundChannel` (отдельная цепочка, не покрывается HTTP `.csrf().disable()`). Мы подменяем его на no-op через:

```kotlin
@Bean(name = ["csrfChannelInterceptor"])
fun noOpCsrfChannelInterceptor(): ChannelInterceptor = object : ChannelInterceptor {}
```

`WebSocketMessageBrokerSecurityConfiguration` резолвит бин по имени через `getBeanOrNull()`. Без этого overrida `@stomp/stompjs` (из не-same-origin dev-фронта) не может подключиться — CONNECT падает с `MissingCsrfTokenException` (CloseStatus 1002). См. T-070.

Endpoint `/csrf` не существует, заголовок `X-CSRF-TOKEN` не требуется, mutating-запросы (HTTP и STOMP) принимаются с одним `Authorization: Bearer <jwt>`.

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

Два типа токенов, различаются claim `type`:

| Тип | TTL | Claim `type` | Extra claims | Назначение |
|-----|-----|--------------|--------------|-----------|
| Access | 15 минут | `ACCESS` | `nickname`, `role`, `createdAt` | Bearer для REST/STOMP |
| Refresh | 14 дней | `REFRESH` | нет | Только для `POST /auth/refresh` |

Общее:
- Алгоритм HS256 (HMAC SHA-256).
- Ключ: base64-decoded `${token.signing.key}` (env `JWT_SIGNING_KEY`).
- Subject: `user.id` (UUID как строка).
- `jti` — `UUID.randomUUID()` в каждом токене; используется для revocation-check.

Валидация:
- `isTokenValid(token, user)` — access-токен: `!expired && sub==user.id && !revoked(jti) && type==ACCESS`. Refresh-токены в Bearer отклоняются.
- `isRefreshTokenValid(token, user)` — refresh-токен: `!expired && sub==user.id && !revoked(jti) && type==REFRESH`. Используется только `AuthService.refresh`.

### Revocation — `services/TokenRevocationService.kt`

In-memory `Set<UUID>` отозванных `jti`. Заполняется через `POST /auth/logout` (см. `services/AuthService.logout`).

Ограничения MVP:
- Хранится в памяти — рестарт приложения обнуляет revoked-list. Отозванный токен снова валиден до `exp`.
- Для прода — заменить бэкенд на Redis/DB (сохранив интерфейс `revoke(jti)` / `isRevoked(jti)`).

### POST `/auth/logout`

- Header: `Authorization: Bearer <access-jwt>`.
- 204 при валидном токене; отзывает `jti` и эмитит `UserLoggedOut(userId)`.
- Токен без `jti` (issued до релиза этой фичи) — событие эмитится, но revocation-запись не заводится.
- Auth-семантика ошибок унаследована от глобальных handler'ов проекта (403 через `RestAccessDeniedHandler`, 401 только для `ExpiredJwtException` / `InvalidJwtException`); отдельного 401-entry-point'а не заведено.

### POST `/auth/refresh`

- Body: `{ "refreshToken": "..." }`.
- 200 + `JwtAuthenticationResponse(accessToken, refreshToken=null, expiresIn)` при валидном refresh-токене.
- 401 `InvalidJwtException` если: подан access-токен вместо refresh (`type != REFRESH`); refresh невалиден (истёк / отозван / подделан).
- Refresh-token rotation отключён в MVP — прежний refresh-токен продолжает работать до `exp`.

### Response DTO `JwtAuthenticationResponse`

```kotlin
data class JwtAuthenticationResponse(
    val accessToken: String,
    val refreshToken: String?,   // null при /auth/refresh (rotation off)
    val expiresIn: Long,          // TTL access-токена в секундах (OAuth 2.0 совместимо)
)
```

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
| `/app/session/*/start`, `/close`, `/open`, `/round.start`, `/round.abort` | ADMIN |
| `/topic/session/*/sessionStatus`, `/roundStatus`, `/offerCreated`, `/decisionMade`, `/scoreUpdated`, `/offersShuffled` | PLAYER, OBSERVER, ADMIN |
| `/topic/session/*/player/*/offer` | PLAYER, ADMIN |
| `/user/queue/errors` (SUBSCRIBE — персональная очередь ошибок из T-050) | PLAYER, OBSERVER, ADMIN |
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
- Refresh-token rotation отключён (T-056 AC), длинный TTL refresh (14 дней) = стянутый refresh = 14 дней доступа. Приемлемо для лабы, для прода — включить rotation.
- Revocation-list in-memory (T-055), unbounded, теряется при рестарте (T-061 — TTL cleanup).
- CORS `http://localhost:[*]` — только для dev.
- `WebSocketConfig.setAllowedOrigins("*")` — не подходит для prod.
- `logging.level.org.springframework.security=DEBUG` — чувствительно.
- Actuator полностью открыт (`management.endpoints.web.exposure.include=*`).
