# NPC-механизм — design spec (v1)

**Дата**: 2026-07-16
**Автор**: агентная сессия (обсуждение с пользователем)
**Статус**: draft (ожидает user review перед writing-plans)

## Цель

Позволить админу сессии Ultimatum Game заполнить её ботами (NPC), запустить симуляцию — в том числе полностью автоматическую, из одних NPC — и посмотреть по ней статистику. Ключевые продуктовые сценарии:

1. **Human + N ботов**: реальный игрок соревнуется с ботами разных стратегий; статистика сравнивается со всеми участниками.
2. **All-NPC симуляция**: сессия целиком из ботов, прогоняется без ручного участия человека, служит для калибровки стратегий и экспериментов «Fair vs Selfish vs Adaptive» на разных `roundSum`/`numRounds`.
3. **Persistent-эксперименты**: одна и та же NPC-«личность» участвует в N сессиях подряд, статистика по ней копится через все раунды всех сессий.

## Границы v1

**Входит** в v1:

- 5 стратегий: `FAIR`, `SELFISH`, `RANDOM`, `VENGEFUL` (память), `ADAPTIVE` (память).
- Persistent-модель: `NpcProfile` привязан к `User(role=NPC)`, reused между сессиями.
- Bulk-endpoint для создания N ботов сразу с одной стратегией.
- `SessionConfig.autoAdvanceRounds` — прогонка N раундов без ручного `round.start` между ними.
- Fallback при исключении стратегии → `FAIR` с default-params, чтобы раунд не залипал.

**Не входит** в v1 (follow-up при необходимости):

- Latency simulation (`NpcProfile.responseDelayMs`) — искусственная задержка ответа NPC.
- Team-loyal / Copycat стратегии.
- UI-редактор параметров стратегий (сейчас — JSON тело POST-запроса).
- ML/RL стратегии.
- Soft-delete NPC.

## Инварианты, которые нельзя ломать

- `round.offers.size == session.members.size` — единственный триггер перехода в `ALL_OFFERS_RECEIVED`. NPC должен физически быть в `session.members`, иначе фаза не сработает.
- Каждый игрок в раунде одновременно и proposer, и respondent (см. `frontend-integration/02-game-rules.md`). NPC должен уметь оба действия.
- Скоринг работает по `session.members` без ветвлений на role → в v1 менять `StatsService` не нужно.
- Роль `Role.NPC` уже зарезервирована в enum'е, `AuthService.quickRegister` её блокирует (`AuthService.kt:47`). Создание NPC пойдёт через отдельный ADMIN-only endpoint, минуя обычную регистрацию.

## Модель данных

### `NpcProfile` (новая entity)

```
npc_profile
├─ id          UUID PK
├─ user_id     UUID FK → users.id UNIQUE
├─ strategy    ENUM NpcStrategy (VARCHAR)
├─ params_json JSONB
├─ seed        BIGINT NULL          -- детерминизм для тестов и репродукции
└─ created_at  TIMESTAMP
```

- `@OneToOne` связь с `User`. NPC — это `User(role=NPC)` + `NpcProfile`.
- `params_json` полиморфен по `strategy` — на уровне Kotlin sealed-иерархия `NpcParams`.
- Отдельная таблица (не embed в `User`) — атрибуты специфичны для NPC, `User` не пухнет.

### `NpcStrategy` enum

```kotlin
enum class NpcStrategy { FAIR, SELFISH, RANDOM, VENGEFUL, ADAPTIVE }
```

### `NpcParams` sealed hierarchy

```kotlin
sealed interface NpcParams {
    data class Fair(val fairnessThreshold: Double = 0.30) : NpcParams
    data class Selfish(val minOffer: Int = 0) : NpcParams
    data class Random(val acceptProbability: Double = 0.5) : NpcParams
    data class Vengeful(
        val baselineFraction: Double = 0.5,
        val punishStep: Int = 1,           // абсолютные единицы амаунта
        val fairnessThreshold: Double = 0.30,
    ) : NpcParams
    data class Adaptive(
        val baselineFraction: Double = 0.5,
        val targetRejectRate: Double = 0.2,
        val slope: Double = 0.5,            // насколько подстраивать при отклонении rejectRate от target
    ) : NpcParams
}
```

Все `Double` в единичных долях `roundSum` (0.0..1.0) для устойчивости к `roundSum` любого масштаба.

### Изменение `SessionConfig`

Добавить одно поле:

```kotlin
@field:Column(nullable = false)
var autoAdvanceRounds: Boolean = false
```

`false` по умолчанию → полная обратная совместимость. Если `true` — после `ALL_DECISIONS_RECEIVED` сервер сам вызывает следующий `round.start` до тех пор, пока `roundNumber < numRounds`. Останавливается на `numRounds` или на `SessionState != IN_PROGRESS` или на `abortCurrentRound()`.

## Стратегии (интерфейс + 5 реализаций)

```kotlin
data class OfferCtx(
    val session: Session,
    val round: Round,
    val me: User,
    val myPastRounds: List<RoundOutcome>,   // мои offers + результаты
    val random: java.util.Random,           // см. «Детерминизм и seed»
)

data class DecisionCtx(
    val session: Session,
    val round: Round,
    val me: User,
    val incomingOffer: Offer,
    val myPastRounds: List<RoundOutcome>,
    val random: java.util.Random,
)

interface NpcStrategyPlayer {
    fun offer(ctx: OfferCtx): Int         // 0..ctx.session.config.roundSum
    fun decide(ctx: DecisionCtx): Boolean  // true = accept
}
```

**Поведение:**

- **Fair**: `offer = roundSum / 2` (integer div). `decide = amount >= fairnessThreshold * roundSum`.
- **Selfish**: `offer = minOffer`. `decide = amount > 0`.
- **Random**: `offer = random.nextInt(roundSum + 1)`. `decide = random.nextDouble() < acceptProbability`.
- **Vengeful**: смотрим последний прошедший раунд. Если мой оффер там был reject'нут → `offer = max(0, prev_offer - punishStep)`. Иначе → `offer = baselineFraction * roundSum`. `decide = amount >= fairnessThreshold * roundSum`. `fairnessThreshold` слегка растёт если предыдущий incoming был низким (`+= 0.05` при `incoming < baseline`, cap 0.5).
- **Adaptive**: `rejectRate = мои_режектнутые_офферы / мои_офферы_всего`. `offerFraction = clamp(baselineFraction + slope * (rejectRate - targetRejectRate), 0.0, 1.0)`. `offer = (offerFraction * roundSum).toInt()`. `decide` — как Fair.

**Stateless в памяти**: всё нужное восстанавливается из БД. `myPastRounds` собирается из `session.rounds` + `offer.proposer.id == me.id` фильтра.

### Детерминизм и seed

- Если `NpcProfile.seed == null` → каждый ход собирается `Random(SecureRandom.nextLong())` — недетерминированно.
- Если `NpcProfile.seed != null` → `Random(seed XOR round.id.mostSignificantBits XOR phaseTag)`, где `phaseTag` = 0 для offer'а, 1 для decision'а. Это обеспечивает:
  - воспроизводимость (тот же seed + тот же raund.id → тот же результат);
  - разнообразие между раундами (round.id разные → разные RNG-стриимы);
  - независимость offer/decide (не сваливаются в один и тот же choice в одном раунде).
- В тестах используем fixed `round.id` через MockClock либо тестовый Round-фабрикатор с известным UUID.

## Автопилот (когда/как срабатывает)

Синхронно, без scheduler / @Async / отдельных потоков. Всё в транзакции того же метода, который триггернул фазу.

### Триггер A: Round → WAIT_OFFERS

Место: `AdminGameplayService.startNextRound(...)` в конце, после `session.state = IN_PROGRESS; round.phase = WAIT_OFFERS`.

```
npcService.playOffers(round)
```

Внутри:

1. `session.members.filter { it.role == NPC }` → список NPC.
2. Для каждого: собираем `OfferCtx` (`myPastRounds` — pre-fetch из `RoundRepository.findBySessionOrderByRoundNumberAsc(session.id)` с fetch-join offers/decisions).
3. Прогоняем `strategy.offer(ctx)` → clamp в `0..roundSum` → сохраняем `Offer` → `eventPublisher.publishOfferCreated(...)`.
4. После добавления всех NPC-оффер, если `round.offers.size == session.members.size` → переход в `ALL_OFFERS_RECEIVED` + `gameplayService.initWaitDecisionsPhase(session)`.

### Триггер B: Round → OFFERS_SENT (после shuffle)

Место: `CoreGameplayService.initWaitDecisionsPhase(session)` в конце, после установки `round.phase = OFFERS_SENT`.

```
npcService.playDecisions(round)
```

Внутри:

1. NPC-члены сессии.
2. Для каждого NPC находим его назначенный `Offer` (`round.offers.find { it.responder?.id == npc.id }`).
3. Прогоняем `strategy.decide(ctx)` → сохраняем `Decision` → `eventPublisher.publishDecisionMade(...)`.
4. Если `round.decisions.size == session.members.size` → `ALL_DECISIONS_RECEIVED` + `publishRoundStatus` + `publishScoreUpdated`.

### Триггер C: `autoAdvanceRounds` → следующий раунд

Место: `PlayerGameplayService.makeDecision(...)` (и внутри `NpcService.playDecisions`, если раунд закрылся в NPC-транзакции) — в блоке `if (round.decisions.size == session.members.size)`, после `publishScoreUpdated`.

```
if (session.config.autoAdvanceRounds
    && session.state == IN_PROGRESS
    && round.roundNumber < session.config.numRounds) {
    adminGameplayService.startNextRound(session.id)
}
```

Тем самым для all-NPC + `autoAdvanceRounds=true` весь matched оказывается прогнан в одной входящей транзакции `session/start`. Циклически: startRound → playOffers → initWaitDecisions → playDecisions → publishScoreUpdated → startRound → …

### Fallback при исключении стратегии

```kotlin
try { strategy.offer(ctx) } catch (e: Exception) {
    logger.error("NPC strategy failed, fallback to FAIR", e)
    domainEventLogger.emit(NpcStrategyFailed(sessionId, roundId, userId = me.id!!, strategy = profile.strategy, phase = "offer"))
    FairStrategy(NpcParams.Fair()).offer(ctx)
}
```

Аналогично для `.decide()`. Раунд не залипает.

### Race с `abortCurrentRound`

`playOffers` / `playDecisions` в самом начале проверяют `round.phase`. Если не `WAIT_OFFERS` / не `OFFERS_SENT` → early-return (no-op). Логгируем WARN.

## API

Все endpoints — `ADMIN`-only.

### `POST /npc`

Body:
```json
{ "nickname": "Bob-Fair-40", "strategy": "FAIR", "params": { "fairnessThreshold": 0.4 }, "seed": 42 }
```
201 → `NpcProfileResponse` (`{id, userId, nickname, strategy, params, seed, createdAt}`).

Валидация:
- `nickname` — non-blank, unique в `users.nickname`.
- `strategy` — валидный enum.
- `params` — совместим с `strategy` (Fair-params для FAIR и т.п.), иначе 400.
- `seed` — optional; если null → runtime SecureRandom при каждом ходе.

### `GET /npc`

200 → `List<NpcProfileResponse>`.

### `GET /npc/{id}`

200 → `NpcProfileResponse` или 404.

### `DELETE /npc/{id}`

204 если удалено. 409 если NPC участник открытой (`state != FINISHED/ABORTED`) сессии. Удаляется `NpcProfile`; связанный `User` остаётся (FK от `Offer.proposer` / `Decision.responder` не рвётся, историю не теряем).

### `POST /session/{id}/join-npc`

Body: `{ "npcId": "<uuid>" }`. Precondition: `session.state == CREATED && openToConnect == true`. Добавляет `NpcProfile.user` в `session.members`. 200 → обновлённый `SessionResponse`.

### `POST /session/{id}/npcs` (convenience, bulk)

Body:
```json
{ "count": 5, "strategy": "FAIR", "params": { "fairnessThreshold": 0.3 }, "seedBase": 1000 }
```
Precondition: та же что для join-npc. В одной транзакции:
1. Создаёт `count` NPC-User + `NpcProfile`. Никнеймы: `NPC-{strategy}-{shortUuid(6)}` (гарантированно unique).
2. Если `seedBase` задан — seed для i-того NPC = `seedBase + i`; если нет — все с null seed.
3. Всех аттачит к сессии.

200 → `SessionResponse` с обновлёнными `members` + список созданных `NpcProfileResponse`.

Ограничение: `count in 1..100` — чтобы не забить сессию до `numPlayers` ограничения (валидируется отдельно: `members.size + count <= config.numPlayers`).

### Существующие endpoints — 0 изменений

- `POST /session/{id}/start` — работает без изменений; NPC уже в `session.members`.
- Гейминг endpoints (`POST /session/{id}/round/offer`, `.../decision`) — только для не-NPC (для NPC — server-side automation).
- `SessionResponse.members` уже содержит `role` — фронт видит `role == NPC` и рисует иконку/бейдж.

### Автогенерация спек

Изменения `controllers/**` + `dto/**` → перед commit'ом `./gradlew generateApiSnapshots` регенерирует `openapi.json` + `asyncapi.json`, они автоматом копируются в `frontend-integration/specs/` (T-069). Фронт получает актуальные схемы без ручных шагов.

## Скоринг

**Никаких изменений.** NPC — обычный `User` в `session.members`. `StatsService.getSessionStats` считает по всем members без ветвления на role. Единственный побочный эффект — в `SessionScoreDto.perPlayer` появится строчка на каждого NPC с его score.

## Тестирование (TDD, RED сначала)

### Unit: стратегии (RED first)

Для каждой стратегии — по несколько тестов:

- `FairStrategy.offer(roundSum=100) == 50`
- `FairStrategy.decide(amount=30, roundSum=100, threshold=0.30) == true`
- `FairStrategy.decide(amount=29, roundSum=100, threshold=0.30) == false`
- `SelfishStrategy.offer(minOffer=0) == 0`
- `SelfishStrategy.decide(amount=1) == true`
- `RandomStrategy` с fixed seed → детерминированные ассерты amount/decide.
- `VengefulStrategy` — три раунда: baseline offer 50, incoming reject, следующий offer 50 - punishStep.
- `AdaptiveStrategy` — трёхраундовая история с rejectRate=1.0 → offerFraction растёт от baseline.

### Unit: fallback

Stub-стратегия throw'ит → `NpcService` возвращает Fair-fallback + domain event `NpcStrategyFailed` в лог.

### Integration `@SpringBootTest`

1. **All-NPC FREE_FOR_ALL, 3 раунда, autoAdvance=true**: `POST /session (autoAdvance=true) → POST /npcs (count=4, FAIR, seed) → POST /session/start`. Ассерт: `session.state == FINISHED`, `rounds.size == 3`, все Offer/Decision записаны, `SessionScoreDto` содержит 4 членов и суммарный score детерминирован.
2. **Human + 3 NPC FREE_FOR_ALL, 1 раунд, autoAdvance=false**: человек шлёт offer → все NPC уже отправили в start-round → `WAIT_OFFERS` завершается человеком → shuffle → NPC decisions приходят в init-decisions → человек шлёт decision → `ALL_DECISIONS_RECEIVED`. Ассерт: phase-transitions в правильном порядке, публикация событий на WS.
3. **Human + 3 NPC TEAM_BATTLE, 2 команды, 1 раунд**: проверка что `respondent.team != proposer.team` соблюдён и в NPC-round'ах.
4. **Abort посреди NPC-tick**: сессия с NPC, `abortCurrentRound()` до end of `playOffers` → тесты за `noop` при последующем NPC-tick + фаза `ABORTED`.

### Contract

- `POST /npc` с невалидной комбинацией `strategy/params` → 400.
- `POST /session/{id}/join-npc` при `state != CREATED` → 409.
- `POST /session/{id}/npcs` с `count > numPlayers - members.size` → 400.
- `DELETE /npc/{id}` пока NPC в открытой сессии → 409.

## Edge cases

| Кейс | Поведение |
|---|---|
| Сессия из одних NPC + autoAdvance | `session/start` — единственный вход, весь matched прогоняется, финальный `ScoreUpdated` публикуется |
| `TEAM_BATTLE`, все NPC в одной команде | Shuffle это уже разрешает — не трогаем. Возможен degenerate раунд, где ни один NPC не получит offer от чужой команды (некому). В таком случае раунд не переходит из WAIT_OFFERS — это уже существующий баг архитектуры, не в скоупе NPC-задачи. Follow-up: T-XXX «Team-battle degenerate detection» (если проявится). |
| NPC-стратегия throw | Fallback FAIR + `NpcStrategyFailed` domain event. Раунд не залипает. |
| `abortCurrentRound` во время NPC-tick | Проверка `round.phase` в начале каждого NPC-хода → no-op при `ABORTED`. |
| Удаление NPC когда history существует | `NpcProfile` удалить можно, `User` остаётся (FK от Offer/Decision). Историю не теряем. |
| `nickname` collision при bulk | Внутренний retry на short-uuid хвост (max 5 попыток), либо fail с 500 (маловероятно при 6-hex суффиксе). |
| `seedBase + i` overflow | Валидация: `seedBase in Long.MIN..Long.MAX - count`. |

## Ожидаемое разбиение на задачи трекера (для writing-plans)

Приблизительно 8 задач (окончательный план — на этапе writing-plans):

1. `NpcProfile` entity + repository + SQL миграция + `SessionConfig.autoAdvanceRounds`.
2. `NpcStrategy` enum + `NpcParams` sealed + `NpcStrategyPlayer` interface + 3 базовых стратегии (Fair/Selfish/Random) + unit tests.
3. Две memory-стратегии (Vengeful, Adaptive) + unit tests.
4. `NpcService` + fallback + hooks в `AdminGameplayService.startNextRound` и `CoreGameplayService.initWaitDecisionsPhase` + unit tests.
5. `autoAdvanceRounds` — hook в `PlayerGameplayService.makeDecision` + `NpcService` + integration test.
6. `NpcController` (`POST/GET/DELETE /npc`) + tests + OpenAPI regen.
7. `SessionController.joinNpc` + `POST /session/{id}/npcs` (bulk) + tests + OpenAPI regen.
8. Integration `@SpringBootTest`-сценарии (4 штуки из раздела «Тестирование») + docs (`frontend-integration/*`, `docs/*`) + финальный regen snapshot'ов.

## Открытые вопросы (для writing-plans)

- **Транзакции**: цикл `autoAdvanceRounds` внутри одной входящей `@Transactional` через `startNextRound` — приемлемая длительность транзакции при `numRounds=10, numPlayers=120`? Прикинуть: 10 раундов × (offers + shuffle + decisions + score) — вероятно, ок в тестах, но при реальной нагрузке может быть worth разбить на per-round транзакции через `TransactionTemplate`. Решение — в writing-plans.
- **JSONB маппинг**: `@JdbcTypeCode(SqlTypes.JSON)` в Hibernate 6 + Jackson polymorphic (`@JsonTypeInfo`) для `NpcParams` sealed. Verify что работает на тестовом Postgres.
- **Domain events**: добавить `NpcJoined`, `NpcStrategyFailed` в `DomainEventLogger` (T-017 стандарт).

## Ссылки

- `src/main/kotlin/edu/itmo/ultimatumgame/model/Role.kt` — `NPC` уже зарезервирована.
- `src/main/kotlin/edu/itmo/ultimatumgame/services/AuthService.kt:47` — блокирует quick-register для NPC.
- `src/main/kotlin/edu/itmo/ultimatumgame/services/PlayerGameplayService.kt` — референсная логика transition'ов фаз, NPC-hook работает по тем же правилам.
- `src/main/kotlin/edu/itmo/ultimatumgame/services/AdminGameplayService.kt` — точка входа `startNextRound`.
- `src/main/kotlin/edu/itmo/ultimatumgame/services/CoreGameplayService.kt` — `initWaitDecisionsPhase`.
- `frontend-integration/02-game-rules.md` — правила, которых NPC не должен нарушать.
- `docs/tasks/T-069-auto-copy-specs-to-frontend-integration.md` — автосинк OpenAPI/AsyncAPI в frontend-integration.
