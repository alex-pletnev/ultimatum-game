# 03. State machines: SessionState и RoundPhase

## SessionState

Enum: `model/SessionState.kt:5-20`.

```
       ┌──────────┐
       │ CREATED  │
       └────┬─────┘
            │ AdminGameplayService.startSession()
            ▼
       ┌──────────┐         AdminGameplayService.abortSession()
       │ RUNNING  │─────────────────────────────┐
       └────┬─────┘                             │
            │ startNextRound() когда            │
            │ следующего раунда нет             ▼
            ▼                             ┌──────────┐
       ┌──────────┐                       │ ABORTED  │
       │ FINISHED │                       └──────────┘
       └──────────┘
```

| Из → В | Триггер | Файл |
|--------|---------|------|
| `CREATED → RUNNING` | `AdminGameplayService.startSession(sessionId)` | `services/AdminGameplayService.kt:22-38` |
| `RUNNING → FINISHED` | `AdminGameplayService.startNextRound(sessionId)` когда `roundNumber+1 > numRounds` | `services/AdminGameplayService.kt:84-106` |
| `CREATED/RUNNING → ABORTED` | `AdminGameplayService.abortSession(sessionId)` | `services/AdminGameplayService.kt:69-81` |

Побочные эффекты при переходе:
- `startSession()`: `session.currentRound = round #1`, `currentRound.roundPhase = WAIT_OFFERS`, `openToConnect = false`, publish `sessionStatus`.
- `abortSession()`: `openToConnect = false`, publish `roundStatus`.
- `startNextRound()` (последний): `currentRound.roundPhase = FINISHED`, publish `roundStatus`.

Флаг `openToConnect` — независимая ортогональная штука:

| Из → В | Триггер |
|--------|---------|
| `openToConnect = false` | `AdminGameplayService.closeSession()` / автоматически в `startSession()` |
| `openToConnect = true` | `AdminGameplayService.openSession()` |

Управляет тем, разрешено ли новым игрокам джоиниться (`SessionService.joinSession` кидает `SessionJoinRejectedException`, если `openToConnect=false`).

## RoundPhase

Enum: `model/RoundPhase.kt:5-22`.

```
CREATED
   │ (session start / startNextRound)
   ▼
WAIT_OFFERS
   │ (собран последний Offer в PlayerGameplayService.sendOffer)
   ▼
ALL_OFFERS_RECEIVED
   │ (CoreGameplayService.initWaitDecisionsPhase → shuffleOffers)
   ▼
OFFERS_SENT
   │ (собрано последнее Decision в PlayerGameplayService.makeDecision)
   ▼
ALL_DECISIONS_RECEIVED
   │ (AdminGameplayService.startNextRound)
   ▼
FINISHED
```

| Из → В | Триггер | Файл |
|--------|---------|------|
| `CREATED → WAIT_OFFERS` | `AdminGameplayService.startSession()` (для 1-го раунда) или `startNextRound()` (для последующих) | `services/AdminGameplayService.kt:29,97` |
| `WAIT_OFFERS → ALL_OFFERS_RECEIVED` | `PlayerGameplayService.sendOffer()` когда `round.offers.size == session.members.size` | `services/PlayerGameplayService.kt:70` |
| `ALL_OFFERS_RECEIVED → OFFERS_SENT` | `CoreGameplayService.initWaitDecisionsPhase()` после `shuffleOffers()` | `services/CoreGameplayService.kt:30` |
| `OFFERS_SENT → ALL_DECISIONS_RECEIVED` | `PlayerGameplayService.makeDecision()` когда `round.decisions.size == session.members.size` | `services/PlayerGameplayService.kt:131` |
| `ALL_DECISIONS_RECEIVED → FINISHED` | `AdminGameplayService.startNextRound()` (закрытие текущего раунда) | `services/AdminGameplayService.kt:90` |

## Полный цикл сессии (последовательность вызовов)

```
1. POST /session              → SessionState=CREATED, N rounds в phase=CREATED
2. POST /session/{id}/join    → members.add(user)  ×N
3. WS  /app/../start          → SessionState=RUNNING, currentRound=r1, r1.phase=WAIT_OFFERS
4. WS  /app/../offer.create   → Offer сохранён (×N)
   на последнем:                r1.phase=ALL_OFFERS_RECEIVED → shuffle → r1.phase=OFFERS_SENT
                                → publishOfferToPlayer для каждого responder
5. WS  /app/../make.decision  → Decision сохранён (×N)
   на последнем:                r1.phase=ALL_DECISIONS_RECEIVED
6. WS  /app/../round.start    → r1.phase=FINISHED
                                если есть r2: currentRound=r2, r2.phase=WAIT_OFFERS → GOTO 4
                                иначе: SessionState=FINISHED
```

## Инварианты по фазам

| Фаза | Что должно быть истинно |
|------|-------------------------|
| `WAIT_OFFERS` | `round.offers.size < session.members.size`; каждый `Offer.responder = null` |
| `ALL_OFFERS_RECEIVED` | `round.offers.size == session.members.size`; `responder` ещё `null` |
| `OFFERS_SENT` | все `Offer.responder != null`; `round.decisions.size < session.members.size` |
| `ALL_DECISIONS_RECEIVED` | `round.decisions.size == session.members.size`; каждое `Decision.offer.responder == Decision.responder` |
| `FINISHED` | раунд immutable для новых `Offer`/`Decision` |

## Что публикуется в WebSocket на переходах

Через `EventPublisherService` (`services/EventPublisherService.kt`):

| Переход | Что публикуется | Куда |
|---------|-----------------|------|
| join / start / close / open / abort | `publishSessionStatus(session)` | `/topic/session/{id}/sessionStatus` |
| каждый `sendOffer` | `publishOfferCreated(offer)` | `/topic/session/{id}/offerCreated` |
| `WAIT_OFFERS → OFFERS_SENT` | `publishOfferToPlayer(offer)` каждому responder + `publishRoundStatus(round)` | `/topic/session/{id}/player/{userId}/offer` + `/topic/session/{id}/roundStatus` |
| каждый `makeDecision` | `publishDecisionMade(decision)` | `/topic/session/{id}/decisionMade` |
| последнее decision | `publishRoundStatus(round)` + `publishScoreUpdated(sessionScoreDto)` | `/topic/session/{id}/roundStatus` + `/topic/session/{id}/scoreUpdated` |
| `startNextRound` | `publishRoundStatus(round)` | `/topic/session/{id}/roundStatus` |

## Ограничения / TODO

- `AdminGameplayService.abortCurrentRound()` — заглушка (`services/AdminGameplayService.kt`).
- `AdminGameplayService.pauseRound()` — TODO.
- `timeoutMoveSec` из `SessionConfig` не используется — нет автоматических переходов по таймауту.
