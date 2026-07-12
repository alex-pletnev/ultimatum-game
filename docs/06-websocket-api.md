# 06. WebSocket API (STOMP)

## Конфигурация

Файл: `configs/WebSocketConfig.kt`.

| Параметр | Значение | Строка |
|----------|----------|--------|
| STOMP endpoint | `/ws` (полный: `/api/v1/ws`) | `:31` |
| Broker | In-memory Simple Broker | `:26` |
| Broker destination prefix | `/topic` | `:26` |
| Application destination prefix | `/app` | `:27` |
| Handshake CORS | `setAllowedOrigins("*")` | `:51` |

Аннотация класса: `@EnableWebSocketMessageBroker`.

## Подключение

1. Клиент делает HTTP upgrade: `GET /api/v1/ws`.
2. Отправляет STOMP `CONNECT` фрейм с заголовком `Authorization: Bearer <JWT>`.
3. `JwtStompChannelInterceptor` валидирует токен (см. `docs/08-security.md`).
4. Дальнейшие `SEND` / `SUBSCRIBE` проверяются `PlaySessionStompChannelInterceptor` на членство в сессии + `WebSocketSecurityConfig` на роли.

## Endpoints клиент → сервер (`SEND` → `/app/...`)

### Admin

Файл: `controllers/ws/SessionAdminWsController.kt`.

| Destination | Роль | Payload | Действие | Строка |
|-------------|------|---------|----------|--------|
| `/app/session/{sessionId}/start` | ADMIN | — | `AdminGameplayService.startSession` | `:21-30` |
| `/app/session/{sessionId}/close` | ADMIN | — | `AdminGameplayService.closeSession` | `:32-42` |
| `/app/session/{sessionId}/open` | ADMIN | — | `AdminGameplayService.openSession` | `:44-54` |
| `/app/session/{sessionId}/round.start` | ADMIN | — | `AdminGameplayService.startNextRound` | `:57-66` |

### Player

Файл: `controllers/ws/OfferWsController.kt`.

| Destination | Роль | Payload | Действие | Строка |
|-------------|------|---------|----------|--------|
| `/app/session/{sessionId}/offer.create` | PLAYER, ADMIN | `CreateOfferCmd` `{ amount: Int ≥0 }` | `PlayerGameplayService.sendOffer` | `:25-35` |
| `/app/session/{sessionId}/make.decision` | PLAYER, ADMIN | `MakeDecisionCmd` `{ offerId: String, decision: Boolean }` | `PlayerGameplayService.makeDecision` | `:38-48` |

Ошибки в обработчиках `SEND` не возвращаются напрямую клиенту как STOMP-фреймы; исключения из бизнес-логики (`DuplicateIdException`, `IdNotFoundException`, `IllegalStateException`) логируются и обрабатываются на уровне обработчика ошибок Spring.

## Топики сервер → клиент (`SUBSCRIBE` на `/topic/...`)

Публикуются через `EventPublisherService`.

| Destination | Payload | Когда публикуется |
|-------------|---------|-------------------|
| `/topic/session/{sessionId}/sessionStatus` | `SessionWithTeamsAndMembersResponse` | join / start / close / open / abort |
| `/topic/session/{sessionId}/roundStatus` | `RoundResponse` | смена фазы раунда |
| `/topic/session/{sessionId}/offerCreated` | `OfferCreatedResponse` / `OfferPrewResponse` | каждый новый оффер |
| `/topic/session/{sessionId}/decisionMade` | `DecisionMadeResponse` / `DecisionPrewResponse` | каждое решение |
| `/topic/session/{sessionId}/player/{userId}/offer` | `OfferCreatedResponse` / `OfferPrewResponse` | shuffle: персональная доставка оффера respondent'у (`{userId}` = ID респондента) |

Роли на подписку (см. `configs/WebSocketSecurityConfig.kt`):

| Destination | Роли |
|-------------|------|
| `/topic/session/*/sessionStatus` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/roundStatus` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/offerCreated` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/decisionMade` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/player/*/offer` | ADMIN, PLAYER — плюс `userId` в пути **должен совпадать** с текущим пользователем (проверка в `PlaySessionStompChannelInterceptor`) |

## Двойная авторизация STOMP

1. **`JwtStompChannelInterceptor`** (`configs/JwtStompChannelInterceptor.kt`) — на `CONNECT`.
   - Валидирует JWT, устанавливает `Authentication` в `SecurityContextHolder` и в `accessor.user`.
   - Ошибки: `AuthenticationCredentialsNotFoundException`, `InvalidJwtException`.

2. **`PlaySessionStompChannelInterceptor`** (`configs/PlaySessionStompChannelInterceptor.kt`) — на `SEND` / `SUBSCRIBE`.
   - Извлекает `sessionId` из destination.
   - Для `SEND`: требуется `isSessionAdmin(user, session) || isMember(user, session)`.
   - Для `SUBSCRIBE`: требуется `isSessionAdmin || isMember || isObserver`.
   - Дополнительно для `/topic/.../player/{userId}/offer`: `userId == currentUser.id`.
   - Ошибка: `SessionStompRejectedException`.

3. **`WebSocketSecurityConfig`** (`configs/WebSocketSecurityConfig.kt`) — ролевая матрица destinations через `MessageMatcherDelegatingAuthorizationManager`.

## Типичный клиентский флоу

```
CONNECT (Authorization: Bearer <jwt>)
  ↓
SUBSCRIBE /topic/session/{id}/sessionStatus
SUBSCRIBE /topic/session/{id}/roundStatus
SUBSCRIBE /topic/session/{id}/offerCreated
SUBSCRIBE /topic/session/{id}/decisionMade
SUBSCRIBE /topic/session/{id}/player/{myUserId}/offer
  ↓
[Admin] SEND /app/session/{id}/start
[Player] SEND /app/session/{id}/offer.create  { amount: 40 }
[Player] SEND /app/session/{id}/make.decision  { offerId, decision: true }
[Admin] SEND /app/session/{id}/round.start
```

## AsyncAPI

Спека: `src/main/resources/doc/asyncapi.json` (AsyncAPI 3.0, автогенерация из кода через springwolf-stomp).
Локальный UI: `http://localhost:8080/api/v1/springwolf/asyncapi-ui.html`.
Регенерация снапшота: `./gradlew generateApiSnapshots`.
