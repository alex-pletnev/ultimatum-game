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
WAIT_OFFERS ─────────────┐
   │                     │
   │                     │ AdminGameplayService.abortCurrentRound()
   ▼                     │
ALL_OFFERS_RECEIVED      │
   │                     │
   ▼                     │
OFFERS_SENT ─────────────┤
   │                     │
   ▼                     │
ALL_DECISIONS_RECEIVED   │
   │                     ▼
   │                 ┌──────────┐
   │                 │ ABORTED  │  ← startNextRound переходит к следующему
   │                 └──────────┘     без переписывания фазы
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
| `WAIT_OFFERS/ALL_OFFERS_RECEIVED/OFFERS_SENT/ALL_DECISIONS_RECEIVED → ABORTED` | `AdminGameplayService.abortCurrentRound(sessionId)` (T-054) — только для сессии в состоянии RUNNING; после abort'а startNextRound переводит в следующий раунд сохраняя фазу ABORTED для истории | `services/AdminGameplayService.kt` |

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
| `ABORTED` | раунд immutable для новых `Offer`/`Decision` (PlayerGameplayService.sendOffer / makeDecision отклоняют с IllegalStateException → 409) |

## Что публикуется в WebSocket на переходах

Через `EventPublisherService` (`services/EventPublisherService.kt`):

| Переход | Что публикуется | Куда |
|---------|-----------------|------|
| join / start / close / open / abort | `publishSessionStatus(session)` | `/topic/session/{id}/sessionStatus` |
| каждый `sendOffer` | `publishOfferCreated(offer)` | `/topic/session/{id}/offerCreated` |
| `WAIT_OFFERS → OFFERS_SENT` | `publishOfferToPlayer(offer)` каждому responder + `publishOffersShuffled(mapping)` broadcast'ом + `publishRoundStatus(round)` | `/topic/session/{id}/player/{userId}/offer` + `/topic/session/{id}/offersShuffled` + `/topic/session/{id}/roundStatus` |
| каждый `makeDecision` | `publishDecisionMade(decision)` | `/topic/session/{id}/decisionMade` |
| последнее decision | `publishRoundStatus(round)` + `publishScoreUpdated(sessionScoreDto)` | `/topic/session/{id}/roundStatus` + `/topic/session/{id}/scoreUpdated` |
| `startNextRound` | `publishRoundStatus(round)` | `/topic/session/{id}/roundStatus` |

## Canonical Ultimatum Game vs наша адаптация

Каноническая UG (Wikipedia): 2 игрока, single-shot, роли proposer/responder, фиксированная сумма, ultimatum accept/reject. Наш проект — многопользовательская вариация. Отличия зафиксированы явно.

| Параметр | Canonical UG | Наша реализация | Комментарий |
|----------|--------------|-----------------|-------------|
| Игроков в сессии | 2 | N (задаётся `SessionConfig.numPlayers`) | Адаптация под лабораторные эксперименты |
| Роли | proposer / responder (фиксированы) | Каждый игрок оффер отправляет и решение принимает; pairing через shuffle-стратегии (`FreeForAllStrategy`, `TeamBattleStrategy`) | В одном раунде игрок может быть proposer'ом одного оффера и responder'ом другого |
| Раундов | 1 (single-shot) | N (`SessionConfig.numRounds`) | Iterated-версия под experimental protocol |
| Сумма | Фиксированная | `SessionConfig.roundSum`, immutable per session | ✓ Соответствует канону |
| Правило accept | proposer keeps `sum − offer`, responder gets `offer` | То же: `(roundSum − offer.amount, offer.amount)` — см. `StatsService.computeScores` | ✓ Соответствует канону (T-003) |
| Правило reject | Оба получают 0 | То же: `(0, 0)` | ✓ Соответствует канону |
| Диапазон offer'а | `0 ≤ offer ≤ sum` (неявно, split денег) | `0 ≤ offerValue ≤ roundSum` — проверяется в `PlayerGameplayService.sendOffer` (T-045); нижняя граница — `@PositiveOrZero` на DTO, верхняя — `require(...)` | ✓ Соответствует канону; **до T-045 верхняя граница не валидировалась** |
| Multi-player варианты | «Competitive UG» (n proposer'ов), «Pirate game» | Не реализовано; наш N-player — независимые пары proposer↔responder через shuffle | Не претендуем на «competitive» вариант |
| Команды (TEAM_BATTLE) | Не canonical | Каждый игрок делает индивидуальный оффер; scoring агрегируется по командам (`SessionScoreDto.teams`) | Наша extension |

### Осознанные адаптации

- **Multi-round**: для сбора статистики стратегий поведения — стандартная научная практика iterated UG.
- **N-player shuffle**: экономия участников, каждый раунд — новая пара; статистика по индивидууму сохраняется.
- **TEAM_BATTLE**: групповые эффекты не покрыты каноническим UG, но популярный extension в экспериментальной экономике.

### Тесты, подтверждающие соответствие

- `StatsServiceTest.accept — proposer получает roundSum минус offer, responder получает offer` — правило accept.
- `StatsServiceTest.reject — обе стороны получают 0` — правило reject.
- `StatsServiceTest.no decision yet — score = 0, не начисляется` — оффер без решения не создаёт payoff.
- `PlayerGameplayServiceTest.sendOffer — IllegalArgumentException если offerValue больше roundSum` — верхняя граница.
- `PlayerGameplayServiceTest.sendOffer — offerValue равный roundSum допустим` — граница включена.
- `FreeForAllTest`, `TeamBattleStrategyTest` — invariants shuffle-стратегий (`responder != proposer`, `responder ∈ members`, для TEAM_BATTLE — из другой команды).

## Ограничения / TODO

- `AdminGameplayService.pauseRound()` — не реализован (нет endpoint'а, метод удалён из сервиса — T-054). Заводить если реально понадобится клиенту.
- `timeoutMoveSec` из `SessionConfig` не используется — нет автоматических переходов по таймауту.
