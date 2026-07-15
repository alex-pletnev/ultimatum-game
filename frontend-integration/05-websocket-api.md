# 05. WebSocket API (STOMP)

Транспорт: **STOMP 1.2 поверх WebSocket**. Клиент: `@stomp/stompjs` (JS/TS) или любой STOMP-совместимый.

**Endpoint:** `ws://localhost:8080/api/v1/ws`

**Prefixes:**
- `/app/...` — клиент → сервер (SEND-команды).
- `/topic/...` — server → клиенты (broadcast).
- `/user/...` — server → конкретный пользователь (по principal).

Автогенерируемая полная спека — [specs/asyncapi.json](specs/asyncapi.json). Интерактивно: Springwolf UI на `http://localhost:8080/api/v1/springwolf/asyncapi-ui.html`.

---

## Подключение

STOMP `CONNECT`-фрейм должен нести JWT в заголовке:

```
CONNECT
Authorization: Bearer <accessToken>
```

Пример на `@stomp/stompjs`:

```typescript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/api/v1/ws',
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },
  reconnectDelay: 5000,
  onConnect: () => {
    console.log('STOMP connected');
    // Здесь подписываться на топики.
  },
  onStompError: (frame) => {
    // Например невалидный JWT — сервер прислал ERROR-фрейм.
    console.error('STOMP error', frame.headers['message']);
  },
});

client.activate();
```

**Ошибки CONNECT:**
- Нет JWT → сервер закрывает соединение с STOMP `ERROR`-фреймом.
- Невалидный JWT → аналогично.

**При истечении access-токена** — переустановить соединение с новым токеном (STOMP не обновляет заголовок автоматически). Стратегия: onDisconnect → refresh access → пересоздать client.

---

## Топики: server → клиент

Подписываются через `SUBSCRIBE /topic/...`.

| Destination | Payload | Когда публикуется |
|-------------|---------|-------------------|
| `/topic/session/{sessionId}/sessionStatus` | `SessionWithTeamsAndMembersResponse` | join / start / close / open / abortSession |
| `/topic/session/{sessionId}/roundStatus` | `RoundResponse` | Смена фазы раунда (WAIT_OFFERS → OFFERS_SENT → ALL_DECISIONS_RECEIVED, а также при abort/finish) |
| `/topic/session/{sessionId}/offerCreated` | `OfferCreatedResponse` | Каждый новый оффер (broadcast всем в сессии) |
| `/topic/session/{sessionId}/decisionMade` | `DecisionMadeResponse` | Каждое решение (broadcast) |
| `/topic/session/{sessionId}/offersShuffled` | `OffersShuffledResponse` | После shuffle: mapping `offerId → (proposerId, responderId)` для визуализации pairing (broadcast) |
| `/topic/session/{sessionId}/scoreUpdated` | `SessionScoreDto` | После закрытия раунда (`ALL_DECISIONS_RECEIVED`); per-player и per-team суммы + roundSum |
| `/topic/session/{sessionId}/player/{userId}/offer` | `AssignedOfferResponse` | Персональная доставка оффера respondent'у после shuffle (`{userId}` = ID respondent'а) |
| `/user/queue/errors` | `ApiErrorResponse` | Персональные ошибки STOMP-команд (см. ниже) |

**Роли на подписку:**

| Destination | Роли |
|-------------|------|
| `/topic/session/*/sessionStatus` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/roundStatus` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/offerCreated` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/decisionMade` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/offersShuffled` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/scoreUpdated` | ADMIN, PLAYER, OBSERVER |
| `/topic/session/*/player/*/offer` | ADMIN, PLAYER |
| `/user/queue/errors` | authenticated |

---

## SEND: клиент → сервер

### Admin — управление сессией и раундами

**Роль ADMIN.** Payload пустой (тело можно передать пустой JSON `{}` или пустую строку).

| Destination | Действие |
|-------------|----------|
| `/app/session/{sessionId}/start` | Перевести сессию в RUNNING, начать раунд 1 (phase=WAIT_OFFERS) |
| `/app/session/{sessionId}/close` | `openToConnect = false` (новые игроки не могут присоединиться) |
| `/app/session/{sessionId}/open` | `openToConnect = true` |
| `/app/session/{sessionId}/round.start` | Закрыть текущий раунд (phase=FINISHED) и перейти к следующему; если раундов больше нет — сессия FINISHED |
| `/app/session/{sessionId}/round.abort` | Прервать текущий раунд (phase=ABORTED). Не переходит к следующему автоматически — админ должен вызвать `round.start` |

### Player — игровые действия

**Роль PLAYER или ADMIN.**

**`/app/session/{sessionId}/offer.create`** — отправить оффер:
```json
{ "amount": 40 }    // 0 <= amount <= roundSum
```

**`/app/session/{sessionId}/make.decision`** — принять/отклонить оффер:
```json
{ "offerId": "550e8400-...", "decision": true }   // true=accept, false=reject
```

---

## Обработка ошибок из STOMP-команд

Раньше ошибки из `SEND`-команд молча логировались. Теперь: **любая ошибка отправляется в персональную очередь `/user/queue/errors`** как `ApiErrorResponse`.

Подписаться (обязательно если хочешь показывать пользователю причину отказа):

```typescript
client.subscribe('/user/queue/errors', (frame) => {
  const err = JSON.parse(frame.body) as ApiErrorResponse;
  showToast(`WS ошибка ${err.status}: ${err.message}`);
});
```

Матрица маппинга (та же семантика что и REST):

| Проблема | HTTP status |
|----------|-------------|
| Плохой JWT (подписан не тем, истёк, malformed) | 401 |
| Роль не подходит для destination | 403 (Spring Security AuthorizationDeniedException) |
| Не тот UUID / некорректные аргументы | 400 |
| Оффер уже отправлен / decision уже сделан / попали в фазу где действие не разрешено | 409 |
| Не найден offer / session / round | 404 |
| Прочее | 500 (без stack-trace в payload'е) |

Пример payload'а:
```json
{
  "timestamp": "2026-07-15T09:00:00.000+00:00",
  "status": 409,
  "error": "Conflict",
  "message": "Оффер нельзя отправить в фазе ABORTED, требуется WAIT_OFFERS",
  "path": "stomp"
}
```

---

## Типичный клиентский flow

**Игрок в сессии:**

```
1. CONNECT (Authorization: Bearer <access>)
2. SUBSCRIBE /user/queue/errors                             ← обязательно
3. SUBSCRIBE /topic/session/{id}/sessionStatus
4. SUBSCRIBE /topic/session/{id}/roundStatus
5. SUBSCRIBE /topic/session/{id}/offerCreated
6. SUBSCRIBE /topic/session/{id}/decisionMade
7. SUBSCRIBE /topic/session/{id}/offersShuffled
8. SUBSCRIBE /topic/session/{id}/scoreUpdated
9. SUBSCRIBE /topic/session/{id}/player/{myUserId}/offer    ← мой персональный оффер после shuffle

// В фазе WAIT_OFFERS:
10. SEND /app/session/{id}/offer.create  { "amount": 40 }
    → сервер публикует /topic/.../offerCreated (broadcast)
    → когда собраны все — shuffle, /topic/.../offersShuffled, /topic/.../roundStatus (phase=OFFERS_SENT)
    → каждому respondent'у в /topic/.../player/{userId}/offer прилетит AssignedOfferResponse

// В фазе OFFERS_SENT:
11. SEND /app/session/{id}/make.decision  { "offerId": "...", "decision": true }
    → /topic/.../decisionMade (broadcast)
    → когда собраны все — /topic/.../roundStatus (phase=ALL_DECISIONS_RECEIVED)
    → /topic/.../scoreUpdated с per-player и per-team суммами

// Ждём админа:
12. (админ шлёт SEND /app/session/{id}/round.start → следующий раунд, phase=WAIT_OFFERS)
    ИЛИ (админ шлёт SEND /app/session/{id}/round.abort → phase=ABORTED, потом round.start)
```

**Админ дополнительно:**

```
// Управление сессией
SEND /app/session/{id}/start
SEND /app/session/{id}/close      // временно закрыть для новых
SEND /app/session/{id}/open       // снова открыть
SEND /app/session/{id}/round.start
SEND /app/session/{id}/round.abort
```

**Наблюдатель (OBSERVER):**

Всё то же кроме SEND'ов — только SUBSCRIBE. Присоединяется через REST `POST /session/{id}/join/observer`.

---

## Гоча

- **Отсутствие anti-impersonation:** сервер не проверяет что `{userId}` в `/topic/session/*/player/{userId}/offer` — это тот же пользователь. Достаточно роли PLAYER или ADMIN. Это принято by design для pet-project (см. [03-auth.md](03-auth.md)). Для prod — задача открытая.
- **Порядок событий гарантирован per-destination**, но между разными destinations — нет. Например `offerCreated` для offer A может прийти после `decisionMade` для offer B в другом клиенте. Фронт должен считаться с этим (реагировать на state, не на порядок сообщений).
- **Заголовки STOMP не обновляются** — если access-токен истёк, надо переподключаться. `@stomp/stompjs` с `reconnectDelay` этому не поможет автоматически.
- **Server heartbeat** есть по умолчанию (`@stomp/stompjs` умеет).
