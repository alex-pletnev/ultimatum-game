# 08. Безопасность

## Модель

Stateless JWT-аутентификация. Пароли не используются. Двухслойная авторизация:

1. **HTTP filter chain** — валидация JWT на каждый REST-запрос.
2. **STOMP interceptors** — валидация JWT на `CONNECT`, авторизация членства в сессии на `SEND`/`SUBSCRIBE`.
3. **Method-level** — `@PreAuthorize` на контроллерах + `WebSocketSecurityConfig` для STOMP.

## HTTP Filter Chain

Файл: `configs/SecurityConfiguration.kt`.

```
Request
  → CORS filter
  → CSRF filter (session-based + XOR)
  → JwtAuthenticationFilter        ← ставится до UsernamePasswordAuthenticationFilter
  → Spring Security AuthorizationFilter (@PreAuthorize)
  → Controller
```

### CORS — `SecurityConfiguration.kt:59-69`

```kotlin
CorsConfiguration().apply {
    allowedOriginPatterns = listOf("http://localhost:[*]")
    allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
    allowedHeaders = listOf("*")
    allowCredentials = true
}
```

Регистрируется на `/**`. Отдельно для STOMP: `setAllowedOrigins("*")` в `WebSocketConfig.kt:51`.

### CSRF — `SecurityConfiguration.kt:30-36`

- Хранилище: `HttpSessionCsrfTokenRepository` (session-based).
- Обработчик: `XorCsrfTokenRequestAttributeHandler` (защита от BREACH).
- Токен получают через `GET /csrf` (`controllers/CsrfController.kt:11-17`).
- Отправляется в заголовке: `X-CSRF-TOKEN`.

### JwtAuthenticationFilter — `configs/JwtAuthenticationFilter.kt`

Алгоритм:
1. Читает `Authorization: Bearer <token>` (`:36`).
2. Если header отсутствует или без `Bearer ` — пропускает без аутентификации (`:37-41`).
3. `JwtService.extractUsername(token)` → UUID (`:43`).
4. `UserService.getUserDetailService()` → `User`.
5. `JwtService.isTokenValid(token, user)` (`:51`) — проверка exp + sub.
6. Собирает `UsernamePasswordAuthenticationToken` с `SimpleGrantedAuthority("ROLE_" + role.name)`, ставит в `SecurityContextHolder` (`:59-66`).
7. Ошибка валидации → `AccessDeniedException` (`:71`).

### Публичные vs защищённые endpoints — `SecurityConfiguration.kt:38-49`

Публичные (`permitAll`):
- `/actuator/**`
- `/auth/**`
- `/v3/api/**` (OpenAPI docs)
- `/ws/**` (WebSocket handshake — реальная авторизация в STOMP)
- `/csrf/**`
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
| TTL | 365 дней | `:85` |
| Subject | `user.id` (UUID как строка) | `:83` |
| Extra claims | `nickname`, `role`, `createdAt` | `:32-34` |

Валидация: `isTokenValid` (`:47-53`) — `!isTokenExpired && extractUsername == userDetails.username`.

### `application.properties`

```
token.signing.key=${JWT_SIGNING_KEY}
```

Секрет обязателен в env — без него приложение не поднимется.

## STOMP: двухслойная авторизация

Регистрация интерцепторов: `configs/WebSocketConfig.kt` (`configureClientInboundChannel`).

### 1. JwtStompChannelInterceptor — `configs/JwtStompChannelInterceptor.kt`

Работает **только на CONNECT** (`:49`).

- Достаёт `Authorization: Bearer <token>` из STOMP-заголовков (`:51-52`).
- `JwtService.extractUsername` → UUID.
- `JwtService.isTokenValid`.
- Ставит `UsernamePasswordAuthenticationToken` **и в `SecurityContextHolder`, и в `accessor.user`** (`:71-78`) — нужно и то, и то: контекст для `@PreAuthorize`, `accessor.user` для персональных топиков.

Ошибки:
- Нет JWT → `AuthenticationCredentialsNotFoundException`.
- Невалидный → `InvalidJwtException`.

### 2. PlaySessionStompChannelInterceptor — `configs/PlaySessionStompChannelInterceptor.kt`

Работает на `SEND` и `SUBSCRIBE` (`:32`).

Алгоритм:
1. `userId = accessor.user.name → UUID`.
2. Парсит `sessionId` из destination (`:56-71`):
   - SEND: `/app/session/{sessionId}/...`.
   - SUBSCRIBE: `/topic/session/{sessionId}/...`.
3. Если destination содержит `/player/{userId}/...` — парсит и проверяет совпадение с текущим пользователем (`:74-87`, `:109`).
4. Авторизация:
   - **SEND:** `isSessionAdmin(userId, sessionId) OR isSessionMember(userId, sessionId)` (`:92-93`).
   - **SUBSCRIBE:** `isSessionAdmin OR isSessionMember OR isSessionObserver` (`:104-106`).

Ошибка любой проверки → `SessionStompRejectedException`.

### 3. WebSocketSecurityConfig — `configs/WebSocketSecurityConfig.kt`

Аннотация: `@EnableWebSocketSecurity`. Строит `MessageMatcherDelegatingAuthorizationManager` с ролевыми правилами:

| Destination | Роли |
|-------------|------|
| `/app/session/*/offer.create`, `/app/session/*/make.decision` | PLAYER, ADMIN |
| `/app/session/*/start`, `/close`, `/open`, `/round.start` | ADMIN |
| `/topic/session/*/sessionStatus`, `/roundStatus`, `/offerCreated`, `/decisionMade` | PLAYER, OBSERVER, ADMIN |
| `/topic/session/*/player/*/offer` | PLAYER, ADMIN |
| `CONNECT`, `HEARTBEAT`, `UNSUBSCRIBE`, `DISCONNECT` | permitAll |

## Итоговая цепочка авторизации для STOMP-сообщения

```
1. HTTP handshake → SecurityConfiguration permits /ws/**
2. STOMP CONNECT → JwtStompChannelInterceptor (JWT valid?)
3. STOMP SEND/SUBSCRIBE:
   a. PlaySessionStompChannelInterceptor (юзер — участник сессии?)
   b. WebSocketSecurityConfig (у роли есть право на destination?)
   c. Персональные топики: userId в пути == текущий?
4. @MessageMapping метод контроллера
```

## Обработчик 403 — `exceptions/RestAccessDeniedHandler.kt`

Возвращает JSON `ApiErrorResponse` со статусом 403 вместо стандартной HTML-страницы Spring Security.

## Модель пользователя

- `User implements UserDetails` — `model/User.kt:10-59`.
- `getUsername()` → `id.toString()`.
- `getPassword()` → `""`.
- `getAuthorities()` → `[SimpleGrantedAuthority("ROLE_" + role.name)]`.

## Известные security-моменты

Все — задокументированы в `docs/11-known-gaps.md`:
- TTL JWT 365 дней, нет refresh-токенов.
- CORS `http://localhost:[*]` — только для dev.
- `WebSocketConfig.setAllowedOrigins("*")` — не подходит для prod.
- `logging.level.org.springframework.security=DEBUG` — чувствительно.
- Actuator полностью открыт (`management.endpoints.web.exposure.include=*`).
