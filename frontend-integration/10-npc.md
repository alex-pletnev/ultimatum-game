# 10. NPC — боты со стратегиями

Механизм неигровых персонажей (NPC): персистентные боты, которые играют по одной из 5 стратегий. NPC — обычный `User` с `role: "NPC"` + отдельная сущность `NpcProfile` со стратегией и её параметрами. Бэкенд синхронно ходит за NPC в те же фазы, в которые ходят люди (`WAIT_OFFERS` → NPC делают offer'ы; `OFFERS_SENT` → NPC делают decisions). Никаких дополнительных WS-каналов и клиентских тикеров не нужно — фронт получает те же самые события `offerCreated` / `decisionMade` / `roundStatus` / `scoreUpdated`, что и в чисто-человеческой сессии.

Дополнительно есть флаг `SessionConfig.autoAdvanceRounds`: если все игроки — NPC (или человеческие раунды закрываются мгновенно), сервер автоматически прогоняет следующий раунд после `ALL_DECISIONS_RECEIVED`, вплоть до `numRounds`.

**Bird's eye view:**

```
Admin → POST /npc              → создать NPC-профили (persistent)
Admin → POST /session          → создать сессию (autoAdvanceRounds: true для all-NPC)
Admin → POST /session/{id}/npcs → bulk-создать + сразу приаттачить N ботов
                                   ИЛИ
        POST /session/{id}/join-npc → приаттачить существующего NPC
Admin → POST /session/{id}/start → игра пошла; никаких других вызовов
                                    для «пусть NPC ходят» не нужно
Frontend ← WS /topic/session/{id}/roundStatus / offerCreated / decisionMade /
           scoreUpdated / sessionStatus ← всё как обычно
```

---

## Enum'ы и sealed types

### `NpcStrategy`

```
"FAIR" | "SELFISH" | "RANDOM" | "VENGEFUL" | "ADAPTIVE"
```

- **FAIR** — proposer предлагает половину `roundSum`; responder принимает, если `offer >= fairnessThreshold * roundSum`.
- **SELFISH** — proposer предлагает `minOffer`; responder принимает всё, где `offer > 0`.
- **RANDOM** — proposer предлагает случайное число в `[0, roundSum]`; responder принимает с вероятностью `acceptProbability`.
- **VENGEFUL** — proposer стартует с `baselineFraction * roundSum`, но если в прошлом раунде его offer отклонили — снижает следующий на `punishStep`. Responder поднимает порог `fairnessThreshold` на 0.05 (клэмп до 0.5), если предыдущий полученный offer был ниже baseline.
- **ADAPTIVE** — proposer подстраивает `offerFraction` под собственный rejectRate: `baselineFraction + slope * (rejectRate − targetRejectRate)`, clamp в `[0, 1]`. Responder принимает мягко — всё, что `>= (baselineFraction * roundSum) / 2` (иначе слишком агрессивно режектит).

### `NpcParams`

Sealed-тип с дискриминатором `type` (Jackson polymorphic). **Обязательно передавать `type` в JSON**, иначе бэкенд не восстановит конкретный подкласс.

```jsonc
// FAIR
{ "type": "FAIR", "fairnessThreshold": 0.30 }               // default 0.30

// SELFISH
{ "type": "SELFISH", "minOffer": 0 }                        // default 0

// RANDOM
{ "type": "RANDOM", "acceptProbability": 0.5 }              // default 0.5

// VENGEFUL
{ "type": "VENGEFUL", "baselineFraction": 0.5, "punishStep": 1, "fairnessThreshold": 0.30 }

// ADAPTIVE
{ "type": "ADAPTIVE", "baselineFraction": 0.5, "targetRejectRate": 0.2, "slope": 0.5 }
```

Все поля имеют default'ы — можно опустить и передать только `type`. Диапазоны — не enforce'ятся сервером явно; в фронт-UI разумно ограничить `fairnessThreshold, acceptProbability ∈ [0, 1]`, `minOffer ∈ [0, roundSum]`.

---

## Определённость (seed)

Каждый `NpcProfile` может иметь опциональный `seed: Long`. Влияет только на `RANDOM` и `ADAPTIVE` (там есть стохастика). Для одной и той же сессии + раунда + seed'а результат детерминирован — удобно для воспроизведения багов / A/B-сравнений стратегий.

Внутри: `Random(seed XOR round.id.mostSignificantBits XOR phaseTag)`, где `phaseTag = 0` для offer, `1` для decide.

---

## Модели DTO

### `NpcProfileResponse`

```jsonc
{
  "id": "9f8a-…",                    // UUID NpcProfile
  "userId": "4c1b-…",                // UUID связанного User (роль NPC)
  "nickname": "NPC-FAIR-a3f21b",     // отображаемое имя
  "strategy": "FAIR",
  "params": { "type": "FAIR", "fairnessThreshold": 0.30 },
  "seed": 42,                        // nullable
  "createdAt": "2026-07-16T10:00:00.000Z"  // ISO-8601, Instant
}
```

### `CreateNpcRequest`

```jsonc
{
  "nickname": "Bob-Fair",
  "strategy": "FAIR",
  "params": { "type": "FAIR", "fairnessThreshold": 0.4 },
  "seed": 42                         // optional
}
```

`strategy` и `params.type` **должны совпадать** — иначе 400.

### `BulkNpcsRequest`

```jsonc
{
  "count": 4,                        // 1..100
  "strategy": "FAIR",
  "params": { "type": "FAIR", "fairnessThreshold": 0.3 },
  "seedBase": 100,                   // optional; NPC получат seed = seedBase + index
  "teamId": null                     // optional; смысл зависит от sessionType — см. ниже
}
```

`count + текущее число членов сессии <= config.numPlayers` — иначе 400.

Nickname'ы генерятся автоматически: `NPC-<STRATEGY>-<random6>`.

**Раскладка по командам (`teamId`):**

| sessionType | `teamId` | Поведение |
|---|---|---|
| FREE_FOR_ALL | `null` | NPC добавляются в `session.members`, teams не задействованы |
| FREE_FOR_ALL | указан | **400** — teamId недопустим для FREE_FOR_ALL |
| TEAM_BATTLE | указан | все N NPC добавляются в указанную команду |
| TEAM_BATTLE | `null` | round-robin по существующим командам с приоритетом наименее заполненной (3 NPC на 2 пустые команды → 2/1, а не 3/0) |

### `BulkNpcsResponse`

```jsonc
{
  "session": { … SessionWithTeamsAndMembersResponse … },
  "npcs":    [ { … NpcProfileResponse … }, … ]
}
```

### `JoinNpcRequest`

```jsonc
{
  "npcId": "9f8a-…",
  "teamId": null                     // required для TEAM_BATTLE
}
```

### `SessionConfigDto` — новое поле

```jsonc
{
  "sessionType": "FREE_FOR_ALL",
  "numRounds": 3,
  "numTeams": 0,
  "numPlayers": 4,
  "roundSum": 100,
  "timeoutMoveSec": 60,
  "autoAdvanceRounds": true          // ← новое, default false
}
```

Если `autoAdvanceRounds: true`, сервер сразу после `ALL_DECISIONS_RECEIVED` вызывает `startNextRound(sessionId)` внутренне, пока не дойдёт до `numRounds`. Фронт увидит цепочку `roundStatus`-событий (ALL_DECISIONS_RECEIVED → RoundClosed → следующий раунд WAIT_OFFERS → ALL_OFFERS_RECEIVED → OFFERS_SENT → ALL_DECISIONS_RECEIVED → …), без необходимости слать `/start-next-round` вручную.

---

## REST endpoints

### Сводка

| Метод | Путь | Роль | Кратко |
|-------|------|------|--------|
| POST | `/npc` | ADMIN | Создать NPC-профиль |
| GET | `/npc` | ADMIN | Список всех NPC-профилей |
| GET | `/npc/{id}` | ADMIN | Один NPC-профиль |
| DELETE | `/npc/{id}` | ADMIN | Удалить NPC (только если не в активной сессии) |
| POST | `/session/{id}/join-npc` | ADMIN | Приаттачить существующего NPC к сессии |
| POST | `/session/{id}/npcs` | ADMIN | Bulk: создать N NPC + сразу приаттачить |

---

### `POST /npc` — создать NPC-профиль

**Роль:** ADMIN.

**Body:** `CreateNpcRequest` (см. выше).

**201 Created:** `NpcProfileResponse`.

**Пример:**

```bash
curl -sX POST http://localhost:8080/api/v1/npc \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H 'Content-Type: application/json' \
  -d '{
    "nickname": "Bob-Vengeful",
    "strategy": "VENGEFUL",
    "params": { "type": "VENGEFUL", "baselineFraction": 0.5, "punishStep": 2 },
    "seed": 42
  }'
```

**Ошибки:**
- `400` — `strategy` и `params.type` не совпадают.
- `409` — `nickname` уже занят.
- `403` — не ADMIN.

**Побочные эффекты:** создаётся `User(role=NPC)` + `NpcProfile`. NPC-user'а нельзя использовать для регистрации/логина.

---

### `GET /npc` — список NPC

**Роль:** ADMIN.

**200 OK:** `NpcProfileResponse[]`. Пагинации нет (в первой версии).

---

### `GET /npc/{id}` — один NPC

**Роль:** ADMIN.

**200 OK:** `NpcProfileResponse`.

**Ошибки:** `404` — не найден.

---

### `DELETE /npc/{id}` — удалить NPC

**Роль:** ADMIN.

**204 No Content**: удалён профиль + связанный `User`.

**Ошибки:**
- `404` — не найден.
- `409` — NPC участник сессии, которая ещё `CREATED` или `RUNNING`. Сначала дождаться `FINISHED` / `ABORTED` или убрать NPC.

---

### `POST /session/{sessionId}/join-npc` — приаттачить NPC к сессии

**Роль:** ADMIN.

**Body:** `JoinNpcRequest`.

**200 OK:** `SessionWithTeamsAndMembersResponse`.

Использовать когда NPC-профили уже созданы через `POST /npc` и вы хотите переиспользовать одного и того же бота в разных сессиях.

**Ошибки:**
- `400` — сессия не в `CREATED` или `openToConnect: false`.
- `400` — достигнут `numPlayers`.
- `400` — TEAM_BATTLE без `teamId` или несуществующий `teamId`.
- `404` — NPC/сессия не найдены.

**Побочные эффекты:** публикует `sessionStatus` в `/topic/session/{sessionId}/sessionStatus`. В логе — domain event `npc.joined`.

---

### `POST /session/{sessionId}/npcs` — bulk-создать и сразу приаттачить

**Роль:** ADMIN.

**Body:** `BulkNpcsRequest`.

**200 OK:** `BulkNpcsResponse` (session + список созданных NpcProfileResponse).

Удобно для «полностью-NPC» сессии: одна ручка вместо N раз POST /npc + N раз POST /join-npc.

**Пример — 4-игровая FREE_FOR_ALL c 4 Fair-ботами:**

```bash
# 1. Создать сессию с autoAdvanceRounds=true, numPlayers=4
curl -sX POST http://localhost:8080/api/v1/session \
  -H "Authorization: Bearer <ADMIN>" \
  -H 'Content-Type: application/json' \
  -d '{
    "displayName":"all-npc-fair",
    "config": {
      "sessionType":"FREE_FOR_ALL","numRounds":3,"numTeams":0,
      "numPlayers":4,"roundSum":100,"timeoutMoveSec":30,
      "autoAdvanceRounds": true
    }
  }'
# → SessionWithTeamsAndMembersResponse, забрать sessionId

# 2. Bulk-заливка 4 FAIR-ботов в неё
curl -sX POST http://localhost:8080/api/v1/session/{sessionId}/npcs \
  -H "Authorization: Bearer <ADMIN>" \
  -H 'Content-Type: application/json' \
  -d '{
    "count": 4,
    "strategy": "FAIR",
    "params": { "type": "FAIR" },
    "seedBase": 1
  }'

# 3. Запустить сессию — дальше сервер сам её прогонит до FINISHED
curl -sX POST http://localhost:8080/api/v1/session/{sessionId}/start \
  -H "Authorization: Bearer <ADMIN>"
```

Между шагом 3 и `state = FINISHED` фронт получит серию `roundStatus` событий; никаких других REST-вызовов не нужно.

**Ошибки:**
- `400` — `count not in 1..100`.
- `400` — `count + members > numPlayers`.
- `400` — `strategy` и `params.type` не совпадают.
- `400` — сессия не в `CREATED` или `openToConnect: false`.
- `400` — `teamId` указан для сессии FREE_FOR_ALL.
- `404` — `teamId` указан, но команда с таким UUID в сессии не найдена.
- `403` — не ADMIN.

---

## WebSocket — что фронт видит во время NPC-хода

**Ничего специфичного для NPC.** Тот же набор STOMP-событий, что и в чисто-человеческой сессии — просто некоторые из них будут отправляться сервером мгновенно, друг за другом, без человеческих задержек:

- `/topic/session/{id}/roundStatus` — фазы `WAIT_OFFERS → ALL_OFFERS_RECEIVED → OFFERS_SENT → ALL_DECISIONS_RECEIVED`.
- `/topic/session/{id}/offerCreated` — на каждый offer NPC.
- `/topic/session/{id}/player/{userId}/offer` — персональная доставка для human-responder'а, если он играет с NPC-proposer'ом.
- `/topic/session/{id}/offersShuffled` — pairing offer→responder после shuffle.
- `/topic/session/{id}/decisionMade` — на каждый decision NPC.
- `/topic/session/{id}/scoreUpdated` — после `ALL_DECISIONS_RECEIVED`.
- `/topic/session/{id}/sessionStatus` — на join-npc и на переход в `FINISHED`.

**Практика для UI:** если фронт получил serie событий за <100ms (all-NPC autoAdvance) — просто анимировать переходы фаз последовательно, а не запрашивать текущее состояние по REST.

---

## Domain events (server-side logs)

Технически фронту не нужны, но упоминаю для ясности:

- `npc.joined` — при `join-npc` / `bulk npcs`.
- `npc.strategy.failed` — если стратегия бросила exception, сервер сфейлбэчился на FAIR. Не критично, но commit signal что можно уточнить params NPC.

---

## Интеграционные сценарии

### Сценарий 1: All-NPC симуляция за один клик

```typescript
// 1. Создать сессию с autoAdvanceRounds=true
const session = await api<SessionWithTeamsAndMembersResponse>('/session', {
  method: 'POST',
  body: JSON.stringify({
    displayName: `simulation-${Date.now()}`,
    config: {
      sessionType: 'FREE_FOR_ALL',
      numRounds: 10,
      numTeams: 0,
      numPlayers: 4,
      roundSum: 100,
      timeoutMoveSec: 30,
      autoAdvanceRounds: true,
    },
  }),
});

// 2. Приаттачить 4 разных бота bulk'ом
await api<BulkNpcsResponse>(`/session/${session.id}/npcs`, {
  method: 'POST',
  body: JSON.stringify({
    count: 4,
    strategy: 'ADAPTIVE',
    params: { type: 'ADAPTIVE', baselineFraction: 0.5, targetRejectRate: 0.2, slope: 0.5 },
    seedBase: 1,
  }),
});

// 3. Подписаться на WS до старта (иначе первые roundStatus'ы улетят мимо)
stomp.subscribe(`/topic/session/${session.id}/roundStatus`, msg => {
  const round: RoundResponse = JSON.parse(msg.body);
  updateUi(round);
});
stomp.subscribe(`/topic/session/${session.id}/scoreUpdated`, msg => {
  const score: SessionScoreDto = JSON.parse(msg.body);
  updateScoreboard(score);
});
stomp.subscribe(`/topic/session/${session.id}/sessionStatus`, msg => {
  const s: SessionWithTeamsAndMembersResponse = JSON.parse(msg.body);
  if (s.state === 'FINISHED') showFinishScreen(s);
});

// 4. Запустить — дальше сервер сам прогонит все 10 раундов
await api(`/session/${session.id}/start`, { method: 'POST' });
```

### Сценарий 2: Human + NPC гибрид (обычный live-режим)

Никаких изменений в клиенте по сравнению с обычным «все люди» флоу — только несколько мест сессии заполнены NPC вместо человеков.

Порядок:
1. Создать сессию с `autoAdvanceRounds: false` (default) — админ сам решает когда стартовать следующий раунд.
2. Через `POST /session/{id}/join` — люди-игроки подключаются.
3. Через `POST /session/{id}/join-npc` или `POST /session/{id}/npcs` — админ добивает NPC до `numPlayers`.
4. `POST /session/{id}/start` — игра идёт. NPC ходят автоматически в свои фазы; люди — через WS `/send-offer` и `/make-decision`.
5. После каждого `ALL_DECISIONS_RECEIVED` — админ вручную `POST /session/{id}/start-next-round`.

### Сценарий 3: Пересборка сессии на тех же NPC

Если хочется играть несколько сессий с одним и тем же составом NPC (например, A/B две стратегии) — создавать NPC через `POST /npc` один раз, потом каждую новую сессию заполнять через `POST /session/{id}/join-npc`. NPC не удаляется между сессиями.

---

## Failure modes

| Симптом | Причина | Что делать |
|---------|---------|-----------|
| `POST /npc` → 400 «params не соответствуют strategy» | `strategy` и `params.type` не совпадают | Синхронизировать в форме создания |
| `POST /session/{id}/npcs` → 400 «count + members больше numPlayers» | Перебор мест | Уменьшить `count` или увеличить `numPlayers` |
| `DELETE /npc/{id}` → 409 | NPC в активной сессии | Дождаться `FINISHED` / `ABORTED` или прервать сессию |
| Сессия с `autoAdvanceRounds: true` не переходит между раундами | В сессии есть человек, который не сделал decision в `OFFERS_SENT` фазе | Люди тормозят автопрогонку — это ожидаемо. Автопрогонка работает только когда все decisions уже пришли |
| Все NPC вернули один и тот же случайный offer | У всех одинаковый `seed` | Использовать `seedBase` в bulk-запросе, не отдельные одинаковые seed'ы |

---

## Реализационный контракт (что фронт должен предоставить)

Минимум для полной работы NPC:

1. **UI для CRUD NPC** — форма с выбором стратегии → динамическое поле параметров под неё (см. secion NpcParams). Опциональный `seed`. Список созданных NPC + кнопка delete.
2. **UI в форме создания сессии** — чекбокс `autoAdvanceRounds`.
3. **UI на экране лобби сессии** — кнопка «Добавить NPC» (открывает подменю: 1 существующий / bulk-новые). Для 1 существующего — селект из уже созданных NPC + для TEAM_BATTLE выбор команды. Для bulk — `count` + `strategy` + params + `seedBase`.
4. **Экран запущенной сессии** — ничего специфичного для NPC, но убедиться, что WS-события обрабатываются достаточно быстро для all-NPC-симуляции (не блокировать UI на каждом offer'е, разумно батчить/дебаунсить перерисовку).

Готовые схемы для type-generation'а — в [specs/openapi.json](specs/openapi.json).
