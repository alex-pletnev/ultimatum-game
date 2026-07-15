# 08. State machines

Фронт обычно строит UI поверх state — эта дока показывает, откуда какие переходы фазы приходят и что они значат.

## SessionState

```
       ┌──────────┐
       │ CREATED  │  ← создана админом через POST /session
       └────┬─────┘
            │  ADMIN шлёт SEND /app/session/{id}/start
            ▼
       ┌──────────┐         (админ шлёт abortSession — не реализовано в WS,
       │ RUNNING  │──────────  только сервисный вызов)
       └────┬─────┘                          │
            │  ADMIN шлёт /round.start,      │
            │  когда следующего раунда нет   ▼
            ▼                          ┌──────────┐
       ┌──────────┐                    │ ABORTED  │
       │ FINISHED │                    └──────────┘
       └──────────┘
```

| Переход | Триггер |
|---------|---------|
| `CREATED → RUNNING` | ADMIN: `SEND /app/session/{id}/start` |
| `RUNNING → FINISHED` | ADMIN: `SEND /app/session/{id}/round.start` когда `roundNumber+1 > numRounds` |
| `CREATED/RUNNING → ABORTED` | Внутренний вызов `abortSession()` — сейчас нет WS-endpoint'а. Может появиться позже. |

**openToConnect** — независимый флаг, ортогонально:

| Переход | Триггер |
|---------|---------|
| `openToConnect = false` | ADMIN: `SEND /app/session/{id}/close`; также автоматически при `start` |
| `openToConnect = true` | ADMIN: `SEND /app/session/{id}/open` |

Управляет: можно ли новым игрокам присоединиться через `POST /session/{id}/join`. Если `false` → сервер вернёт 409.

**Публикация:** каждый переход → `/topic/session/{id}/sessionStatus` с `SessionWithTeamsAndMembersResponse`.

## RoundPhase

```
CREATED
   │  (session start / round.start)
   ▼
WAIT_OFFERS ─────────────────┐
   │  автомат: все офферы     │
   ▼  собраны                 │
ALL_OFFERS_RECEIVED           │
   │  автомат: сервер         │  ADMIN: SEND /app/session/{id}/round.abort
   ▼  применил shuffle         │
OFFERS_SENT ──────────────────┤
   │  автомат: все решения    │
   ▼  собраны                  │
ALL_DECISIONS_RECEIVED         ▼
   │                       ┌──────────┐
   │  ADMIN: /round.start  │ ABORTED  │  ← сохраняет фазу в истории
   ▼                       └────┬─────┘
   │                            │  ADMIN: /round.start
   │◄───────────────────────────┘
   ▼
FINISHED
```

| Переход | Кто триггерит | Публикация |
|---------|---------------|------------|
| `CREATED → WAIT_OFFERS` | ADMIN: start (для r1) или round.start (для r2+) | `/topic/.../roundStatus` |
| `WAIT_OFFERS → ALL_OFFERS_RECEIVED` | Автоматически: когда `offers.size == members.size` | нет отдельного (сразу переходит дальше) |
| `ALL_OFFERS_RECEIVED → OFFERS_SENT` | Автоматически: сервер применил shuffle | `/topic/.../offersShuffled`, `/topic/.../roundStatus`; персональные `/topic/.../player/{userId}/offer` |
| `OFFERS_SENT → ALL_DECISIONS_RECEIVED` | Автоматически: когда `decisions.size == members.size` | `/topic/.../roundStatus`, `/topic/.../scoreUpdated` |
| `ALL_DECISIONS_RECEIVED → FINISHED` | ADMIN: round.start | `/topic/.../roundStatus` |
| `WAIT_OFFERS|OFFERS_SENT|... → ABORTED` | ADMIN: round.abort | `/topic/.../roundStatus` |

**Важно:** `round.start` после ABORTED переходит к следующему раунду **сохраняя фазу ABORTED** для истории. Новый (следующий) раунд получает phase `WAIT_OFFERS`.

## Что игроку разрешено в каждой фазе

| Фаза | Действие | Роль |
|------|----------|------|
| `CREATED` | Ничего | — |
| `WAIT_OFFERS` | `SEND offer.create` (один раз на игрока) | PLAYER, ADMIN |
| `ALL_OFFERS_RECEIVED` | Ничего (короткая переходная фаза) | — |
| `OFFERS_SENT` | `SEND make.decision` (один раз на респондента; `offerId` — свой персональный) | PLAYER, ADMIN |
| `ALL_DECISIONS_RECEIVED` | Ничего | — |
| `FINISHED` | Ничего | — |
| `ABORTED` | Ничего (offer/decision вернут 409) | — |

## Как определить «мой ход»

Через `RoundResponse.myRole` и `RoundResponse.myPendingActions` — приходит в:
- `GET /session/{id}/current-round` — снапшот текущего раунда.
- `GET /session/{id}/rounds` — история всех раундов.

**Заметь:** в WS-broadcast'ах (`/topic/session/{id}/roundStatus`) эти поля default'ные (`myRole: NONE`, `myPendingActions: []`), потому что broadcast не привязан к конкретному пользователю. Для актуальных hints — сходить в REST.

Практика:
1. На каждый входящий `roundStatus` → сделать `GET /session/{id}/current-round` для актуализации hints.
2. Или собрать hints локально по `offers` / `decisions` в payload (то же что делает сервер).

Логика сервера:
```
myRole:
  isProposer  = round.offers.any { it.proposer.id == myUserId }
  isResponder = round.offers.any { it.responder?.id == myUserId }
  → PROPOSER | RESPONDER | BOTH | NONE

myPendingActions:
  if phase == WAIT_OFFERS && ¬ я уже отправил offer:
    [ SEND_OFFER ]
  if phase == OFFERS_SENT:
    для каждого offer где responder == me и нет моего decision:
      MAKE_DECISION(offerId)
  else: []
```

## Полный цикл сессии (шпаргалка)

```
1. POST /session                → state=CREATED, N рounds в phase=CREATED
2. POST /session/{id}/join      × M игроков
3. WS  /app/../start            → state=RUNNING, current=r1, r1.phase=WAIT_OFFERS
                                → publish sessionStatus, roundStatus

Цикл по раундам (r1 ... rN):

4. WS  /app/../offer.create     × M игроков
                                → каждый: publish offerCreated
                                на последнем: shuffle → publish offersShuffled + roundStatus (OFFERS_SENT)
                                                     + персональные /player/{userId}/offer

5. WS  /app/../make.decision    × M игроков (каждый по своему офферу)
                                → каждый: publish decisionMade
                                на последнем: publish roundStatus (ALL_DECISIONS_RECEIVED) + scoreUpdated

6. WS  /app/../round.start      → r_i.phase=FINISHED
                                → если есть r_{i+1}: current=r_{i+1}, phase=WAIT_OFFERS → GOTO 4
                                → иначе: state=FINISHED, current остаётся r_N в phase=FINISHED
                                → publish roundStatus (+ sessionStatus если FINISHED)
```

Опционально в цикле, если раунд «завис»:
```
WS /app/../round.abort         → phase=ABORTED, publish roundStatus
   потом WS /app/../round.start → переход в следующий раунд (или state=FINISHED если это был последний)
```
