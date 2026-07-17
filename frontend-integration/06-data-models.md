# 06. Data models (DTO)

Полная спека — [specs/openapi.json](specs/openapi.json). Здесь — human-friendly сводка с примерами JSON.

## Соглашения

- Все `id` — **UUID** в форме строки: `"550e8400-e29b-41d4-a716-446655440000"`.
- Даты — **ISO 8601 с миллисекундами и таймзоной**: `"2026-07-15T09:00:00.000+00:00"`.
- Nullable поля — явно `null` в JSON (не отсутствуют).
- Enum'ы — строки в верхнем регистре.
- Целые — стандартный JSON number (int).

## Enum'ы

### `Role`
```
"ADMIN" | "PLAYER" | "OBSERVER" | "NPC"
```
`NPC` возвращается сервером в некоторых старых записях, но регистрация с этой ролью запрещена.

### `SessionState`
```
"CREATED" | "RUNNING" | "FINISHED" | "ABORTED"
```

### `SessionType`
```
"FREE_FOR_ALL" | "TEAM_BATTLE"
```

### `RoundPhase`
```
"CREATED" | "WAIT_OFFERS" | "ALL_OFFERS_RECEIVED" | "OFFERS_SENT" | "ALL_DECISIONS_RECEIVED" | "FINISHED" | "ABORTED"
```

### `MyRole` (в `RoundResponse.myRole`)
```
"PROPOSER" | "RESPONDER" | "BOTH" | "NONE"
```

### `PendingActionType` (в `RoundResponse.myPendingActions[].type`)
```
"SEND_OFFER" | "MAKE_DECISION"
```

---

## Request DTO

### `CreateUserRequest`
```json
{
  "nickname": "alice",     // 3..42, required
  "role": "PLAYER"          // default PLAYER; NPC запрещена
}
```

### `AuthenticateUserRequestDto`
```json
{ "id": "550e8400-..." }
```

### `RefreshTokenRequest`
```json
{ "refreshToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

### `CreateSessionRequest`
```json
{
  "displayName": "My session",   // 3..100
  "state": "CREATED",             // default
  "openToConnect": true,          // default true
  "config": {                     // required
    "sessionType": "FREE_FOR_ALL",
    "numRounds": 3,               // 1..10
    "numTeams": 0,                // 0 (FFA) или 2..5 (TEAM_BATTLE)
    "numPlayers": 4,              // 2..120
    "roundSum": 100,              // 10..100000
    "timeoutMoveSec": 60          // 10..300
  }
}
```

### `CreateOfferCmd` (STOMP payload)
```json
{ "amount": 40 }    // 0..roundSum
```

### `MakeDecisionCmd` (STOMP payload)
```json
{ "offerId": "550e8400-...", "decision": true }
```

---

## Response DTO

### `JwtAuthenticationResponse`
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",     // null при POST /auth/refresh (rotation off)
  "expiresIn": 900              // TTL access в секундах
}
```

### `UserResponse`
```json
{
  "id": "550e8400-...",
  "nickname": "alice",
  "role": "PLAYER",
  "createdAt": "2026-07-15T09:00:00.000+00:00"
}
```

### `UserIdResponse`
```json
{ "id": "550e8400-..." }
```

### `SessionConfigResponse`
```json
{
  "sessionType": "FREE_FOR_ALL",
  "numRounds": 3,
  "numTeams": 0,
  "numPlayers": 4,
  "roundSum": 100,
  "timeoutMoveSec": 60
}
```

### `SessionResponse`
Полная информация о сессии с pagination-safe вложенными объектами (без глубоких раскрытий).
```json
{
  "id": "550e8400-...",
  "displayName": "My session",
  "state": "RUNNING",
  "createdAt": "2026-07-15T09:00:00.000+00:00",
  "admin": { /* UserResponse */ },
  "openToConnect": false,
  "rounds": [ /* RoundPrewResponse[] */ ],
  "config": { /* SessionConfigResponse */ },
  "teams": [ /* TeamPrewResponse[] */ ],
  "currentRound": { /* RoundPrewResponse | null */ },
  "membersCount": 4
}
```

**`membersCount`** — количество вошедших участников (`session.members.size`).
Работает единообразно для FFA (`teams: []`) и TEAM_BATTLE. Используй его как
источник истины «сколько мест занято», не считай `teams[].members` вручную.

**Auto-close полных сессий:** сервер выставляет `openToConnect=false`
автоматически, как только `membersCount >= config.numPlayers` (после `join` /
`join-npc` / bulk-`npcs`). Значит фильтр `GET /session?openToConnect=true` уже
не вернёт полные — фронту не надо перепроверять «есть ли места».

### `SessionWithTeamsAndMembersResponse`
Как `SessionResponse`, но с раскрытыми teams (внутри — members), плюс верхнеуровневые `members` и `observers`.
```json
{
  "id": "550e8400-...",
  "displayName": "My session",
  "state": "RUNNING",
  "createdAt": "2026-07-15T09:00:00.000+00:00",
  "admin": { /* UserResponse */ },
  "openToConnect": false,
  "currentRound": { /* RoundPrewResponse | null */ },
  "config": { /* SessionConfigResponse */ },
  "teams": [ { "id": "...", "name": "Red", "members": [ /* UserResponse[] */ ] } ],
  "members": [ /* UserResponse[] */ ],
  "observers": [ /* UserResponse[] */ ]
}
```

### `SessionPrewResponse`
Облегчённая сессия для вложенности (без rounds/teams/members).
```json
{
  "id": "...", "displayName": "...", "state": "RUNNING",
  "createdAt": "...", "admin": { /* UserResponse */ },
  "openToConnect": false, "config": { /* SessionConfigResponse */ }
}
```

### `RoundResponse`
Полный раунд + per-user hints.
```json
{
  "id": "550e8400-...",
  "roundNumber": 1,                       // 1-based
  "roundPhase": "OFFERS_SENT",
  "offers": [ /* OfferPrewResponse[] */ ],
  "decisions": [ /* DecisionPrewResponse[] */ ],
  "session": { /* SessionPrewResponse */ },

  // Только для GET-endpoints (в WS-broadcast'ах эти поля default'ные):
  "myRole": "PROPOSER",                   // моя роль в этом раунде
  "myPendingActions": [                    // что мне ещё нужно сделать
    { "type": "SEND_OFFER" },
    { "type": "MAKE_DECISION", "offerId": "550e8400-..." }
  ]
}
```

### `RoundPrewResponse`
```json
{ "id": "...", "roundNumber": 1, "roundPhase": "WAIT_OFFERS" }
```

### `OfferCreatedResponse`
Broadcast payload при новом оффере.
```json
{
  "id": "550e8400-...",
  "round": { /* RoundPrewResponse */ },
  "proposer": { /* UserResponse */ },
  "responder": null,                      // null до shuffle, потом заполнено
  "offerValue": 40,
  "createdAt": "..."
}
```

### `OfferPrewResponse`
Как `OfferCreatedResponse`, но все поля nullable — для вложенности.
```json
{
  "id": "...", "proposer": null, "responder": null, "offerValue": 40, "createdAt": "..."
}
```

### `AssignedOfferResponse`
**Персональный** payload из `/topic/session/{id}/player/{userId}/offer` — «этот оффер адресован тебе, реши accept/reject».
```json
{
  "offerId": "550e8400-...",
  "round": { /* RoundPrewResponse */ },
  "proposer": { /* UserResponse */ },
  "amount": 40,
  "offeredAt": "..."
}
```

### `DecisionMadeResponse`
Broadcast payload при новом решении.
```json
{
  "id": "550e8400-...",
  "round": { /* RoundPrewResponse */ },
  "responder": { /* UserResponse */ },
  "offer": { /* OfferCreatedResponse */ },
  "decision": true,                       // true=accept, false=reject
  "createdAt": "..."
}
```

### `DecisionPrewResponse`
Как `DecisionMadeResponse`, но все поля nullable.

### `OffersShuffledResponse`
Публикуется после shuffle — mapping для UI визуализации pairing.
```json
{
  "roundId": "550e8400-...",
  "roundNumber": 1,
  "pairs": [
    { "offerId": "...", "proposerId": "...", "responderId": "..." },
    /* ... для каждого оффера в раунде */
  ]
}
```

### `TeamResponse`
```json
{ "id": "...", "name": "Red", "members": [ /* UserResponse[] */ ] }
```

### `TeamPrewResponse`
```json
{ "id": "...", "name": "Red" }
```

### `SessionScoreDto`
Публикуется в `/topic/session/{id}/scoreUpdated` после каждого закрытия раунда. Кумулятивный счёт.
```json
{
  "roundSum": 100,
  "roundsCompleted": 2,
  "players": [
    {
      "userId": "...",
      "nickname": "alice",
      "score": 145                        // сумма по всем офферам во всех сыгранных раундах
    }
  ],
  "teams": [                              // только для TEAM_BATTLE, пустой массив для FFA
    { "teamId": "...", "name": "Red", "score": 290 }
  ]
}
```

### `SessionStatsDto`
Возвращается внутренне; в API отдаётся преимущественно через CSV (`GET /statistics/{id}/csv`).
```json
{
  "sessionId": "...",
  "displayName": "...",
  "state": "FINISHED",
  "createdAt": "...",
  "totalRounds": 3,
  "decisionsCount": 12,
  "offers": [
    {
      "offerId": "...",
      "amount": 40,
      "proposer": { "id": "...", "nickname": "alice" },
      "responder": { "id": "...", "nickname": "bob" },
      "proposerTeam": null,
      "responderTeam": null,
      "accepted": true,
      "roundNumber": 1,
      "timestamp": "..."
    }
  ],
  "score": { /* SessionScoreDto */ }
}
```

### `ApiErrorResponse`
Единый формат ошибок и для REST, и для STOMP (`/user/queue/errors`).
```json
{
  "timestamp": "2026-07-15T09:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "displayName: must not be blank",
  "path": "/api/v1/session"     // "stomp" для WS-ошибок
}
```

---

## Правила именования

- `*Response` — полные DTO для чтения (endpoints).
- `*PrewResponse` — «превью» для вложенности (все поля nullable, чтобы не циклиться и не тянуть лишнее).
- `*Request` — тело REST-запроса.
- `*Cmd` — payload STOMP-команды.
- `*Dto` — внутренние DTO (`SessionConfigDto`, `SessionStatsDto`, `SessionScoreDto`).
- Опечатка `Prew` (должно быть `Preview`) — присутствует исторически, менять не будем в MVP.

## Ремап полей

- `CreateOfferCmd.amount` → `Offer.offerValue` (в БД поле называется `offerValue`, в API запросе — `amount`).
- Остальные поля мапятся 1-в-1 по имени.
