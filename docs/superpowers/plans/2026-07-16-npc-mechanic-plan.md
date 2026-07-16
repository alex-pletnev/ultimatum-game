# NPC-механизм — Implementation Plan (v1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ввести в игру Ultimatum Game персистентных ботов (NPC) с 5 поведенческими стратегиями, автоматической прогонкой раундов, bulk-endpoint'ом заливки и full-automation режимом `autoAdvanceRounds`.

**Architecture:** NPC = обычный `User(role=NPC)` + отдельная entity `NpcProfile` (@OneToOne). Стратегии реализуют интерфейс `NpcStrategyPlayer` (offer + decide), парам-набор per-strategy — sealed `NpcParams` в JSONB. `NpcService` синхронно триггерится в двух местах существующего gameplay'а (`AdminGameplayService.startNextRound` и `CoreGameplayService.initWaitDecisionsPhase`) — никаких @Async / scheduler'ов. `autoAdvanceRounds` — новый флаг `SessionConfig`, при котором после `ALL_DECISIONS_RECEIVED` сервер сам вызывает следующий `startNextRound` до `numRounds`.

**Tech Stack:** Kotlin 1.9 + Spring Boot 3.4 (JDK 21), Hibernate 6 + JSONB через `@JdbcTypeCode(SqlTypes.JSON)`, Jackson polymorphic (`@JsonTypeInfo`) для sealed `NpcParams`, JUnit5 + MockMvc + Testcontainers-Postgres (через локальный docker-compose).

## Global Constraints

- Каждый значимый файл (services, controllers) под покрытием JaCoCo 0.80 (см. `build.gradle.kts:coverageIncludes`) — для новых services **обязательно** покрытие ≥ 0.80.
- Detekt строгий, baseline не используется (T-015). Новые findings — либо править код, либо `@Suppress` с обоснованием.
- Тесты через `./gradlew check` (docker-compose up -d postgres перед этим). Все gradle-команды — через `run_in_background=true` в файл + notification (T-059).
- Commit-format Conventional Commits + `Refs: docs/tasks/T-XXX-*.md` (см. CLAUDE.md).
- Изменения в `controllers/**`, `dto/**`, `services/EventPublisherService.kt` → перед commit'ом `./gradlew generateApiSnapshots` (авто-копирование в `frontend-integration/specs/` через T-069) — включить оба каталога в тот же commit.
- TDD: RED first (написать провальный тест → прогнать → увидеть FAIL) до реализации (см. `superpowers:test-driven-development`).
- Никаких абсолютных путей и machine-specific настроек в commit (T-049).
- Пре-flight check обязателен для non-trivial задач (Task 1 — миграция схемы = high-stakes).

---

## File Structure

**Новые файлы:**

- `src/main/kotlin/edu/itmo/ultimatumgame/model/NpcProfile.kt` — JPA entity.
- `src/main/kotlin/edu/itmo/ultimatumgame/model/NpcStrategy.kt` — enum.
- `src/main/kotlin/edu/itmo/ultimatumgame/model/NpcParams.kt` — sealed hierarchy.
- `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/NpcStrategyPlayer.kt` — интерфейс + `OfferCtx`/`DecisionCtx`/`RoundOutcome`.
- `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/FairStrategy.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/SelfishStrategy.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/RandomStrategy.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/VengefulStrategy.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/AdaptiveStrategy.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/repositories/NpcProfileRepository.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/controllers/NpcController.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/NpcRequests.kt`
- `src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/NpcResponses.kt`
- `src/test/kotlin/edu/itmo/ultimatumgame/model/npc/FairStrategyTest.kt` (по одному на стратегию)
- `src/test/kotlin/edu/itmo/ultimatumgame/services/NpcServiceTest.kt`
- `src/test/kotlin/edu/itmo/ultimatumgame/controllers/NpcControllerTest.kt`
- `src/test/kotlin/edu/itmo/ultimatumgame/it/NpcGameplayIntegrationTest.kt`

**Существующие файлы, которые правим:**

- `src/main/kotlin/edu/itmo/ultimatumgame/model/SessionConfig.kt` — добавить `autoAdvanceRounds`.
- `src/main/kotlin/edu/itmo/ultimatumgame/services/AdminGameplayService.kt` — hook `npcService.playOffers(round)`.
- `src/main/kotlin/edu/itmo/ultimatumgame/services/CoreGameplayService.kt` — hook `npcService.playDecisions(round)`.
- `src/main/kotlin/edu/itmo/ultimatumgame/services/PlayerGameplayService.kt` — hook autoAdvance после `ALL_DECISIONS_RECEIVED`.
- `src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt` — метод `addNpcMember(...)` + валидация precondition'ов.
- `src/main/kotlin/edu/itmo/ultimatumgame/controllers/SessionController.kt` — `POST /session/{id}/join-npc`, `POST /session/{id}/npcs`.
- `src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/SessionRequests.kt` — `autoAdvanceRounds` в `CreateSessionRequest.config`.
- `src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/SessionResponses.kt` — `autoAdvanceRounds` в session-config responses.
- `src/main/resources/index.sql` — индекс на `npc_profile(user_id)` (unique).
- `src/main/kotlin/edu/itmo/ultimatumgame/util/DomainEventLogger.kt` — новые события `NpcJoined`, `NpcStrategyFailed`.

**Изменения контрактов, о которых надо предупредить фронт (флажок в описании задачи):**

- Task 1 — добавление `SessionConfig.autoAdvanceRounds` (additive, default false).

Остальные API-изменения — чисто новые endpoints/DTO.

---

## Task 1: `SessionConfig.autoAdvanceRounds` + миграция схемы

> **⚠ Меняет существующий контракт.** Additive: новое поле в `SessionConfig`, default `false`. Фронту сказать: старые payload'ы без поля продолжают работать; при желании включить полную автоматическую симуляцию — передавать `autoAdvanceRounds: true`.

**Files:**
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/model/SessionConfig.kt`
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/SessionRequests.kt`
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/SessionResponses.kt`
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/services/SessionServiceTest.kt` (существующий, расширить)

**Interfaces:**
- Consumes: —
- Produces: `SessionConfig.autoAdvanceRounds: Boolean` (default false).

- [ ] **Step 1: Написать провальный тест** — расширить `SessionServiceTest`:

  ```kotlin
  @Test
  fun `createSession — сохраняет autoAdvanceRounds=true`() {
      val req = CreateSessionRequest(
          displayName = "auto",
          config = SessionConfigDto(sessionType = FREE_FOR_ALL, numRounds = 3, numTeams = 0,
              numPlayers = 4, roundSum = 100, timeoutMoveSec = 30, autoAdvanceRounds = true)
      )
      val session = service.createSession(admin.id!!, req)
      assertEquals(true, session.config!!.autoAdvanceRounds)
  }
  ```

- [ ] **Step 2: Прогнать тест — увидеть FAIL** (компиляция упадёт: unknown parameter `autoAdvanceRounds`)

  `./gradlew test --tests "*SessionServiceTest.createSession*" > /tmp/gradle-t1.log 2>&1` (background + notification).

- [ ] **Step 3: Добавить поле в `SessionConfig` entity**

  ```kotlin
  @field:Column(nullable = false)
  var autoAdvanceRounds: Boolean = false
  ```

  (Data class → добавить параметр с дефолтом в primary constructor.)

- [ ] **Step 4: Добавить поле в `CreateSessionRequest.SessionConfigDto` и `SessionResponses`**

  В `SessionRequests.kt` и `SessionResponses.kt`: `val autoAdvanceRounds: Boolean = false`. Не забыть маппер (если есть в `util/mappers/` — MapStruct или ручной).

- [ ] **Step 5: Hibernate DDL — колонка добавится auto (или через `spring.jpa.hibernate.ddl-auto=update`)**

  Убедиться что application.properties поддерживает `update` или `create-drop` для тестов. Ничего не менять в prod-конфиге (миграции в отдельном таске T-044).

- [ ] **Step 6: Прогнать тест — увидеть PASS**

  `./gradlew test --tests "*SessionServiceTest.createSession*" > /tmp/gradle-t1.log 2>&1`.

- [ ] **Step 7: Regenerate API snapshots**

  `./gradlew generateApiSnapshots > /tmp/gradle-t1-specs.log 2>&1`. Убедиться что `openapi.json` содержит `autoAdvanceRounds` в `SessionConfigDto`.

- [ ] **Step 8: Full check**

  `./gradlew check > /tmp/gradle-t1-check.log 2>&1`. Пройти detekt + jacoco.

- [ ] **Step 9: Commit**

  ```bash
  git add src/main/kotlin/.../SessionConfig.kt \
          src/main/kotlin/.../dto/requests/SessionRequests.kt \
          src/main/kotlin/.../dto/responses/SessionResponses.kt \
          src/main/resources/doc/openapi.json src/main/resources/doc/asyncapi.json \
          frontend-integration/specs/*.json \
          src/test/kotlin/.../SessionServiceTest.kt
  git commit -m "feat(T-076): добавить SessionConfig.autoAdvanceRounds

  Additive-поле для полной автоматической прогонки all-NPC сессии.
  Default false — обратная совместимость сохранена.

  Refs: docs/tasks/T-076-session-config-auto-advance.md"
  git push origin main
  ```

---

## Task 2: `NpcProfile` entity + repository

**Files:**
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/NpcProfile.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/NpcStrategy.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/NpcParams.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/repositories/NpcProfileRepository.kt`
- Modify: `src/main/resources/index.sql` — индекс на `npc_profile(user_id)`
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/repositories/NpcProfileRepositoryTest.kt`

**Interfaces:**
- Consumes: `User` (existing entity), `Role.NPC` (уже в enum'е).
- Produces:
  - `enum class NpcStrategy { FAIR, SELFISH, RANDOM, VENGEFUL, ADAPTIVE }`
  - `sealed interface NpcParams { data class Fair(val fairnessThreshold: Double = 0.30) : NpcParams; data class Selfish(val minOffer: Int = 0) : NpcParams; data class Random(val acceptProbability: Double = 0.5) : NpcParams; data class Vengeful(val baselineFraction: Double = 0.5, val punishStep: Int = 1, val fairnessThreshold: Double = 0.30) : NpcParams; data class Adaptive(val baselineFraction: Double = 0.5, val targetRejectRate: Double = 0.2, val slope: Double = 0.5) : NpcParams }`
  - `class NpcProfile(id: UUID?, user: User, strategy: NpcStrategy, params: NpcParams, seed: Long?, createdAt: Date)`
  - `interface NpcProfileRepository : JpaRepository<NpcProfile, UUID> { fun findByUserId(id: UUID): NpcProfile?; fun existsByUserNickname(nickname: String): Boolean }`

- [ ] **Step 1: Написать провальный repository-тест**

  ```kotlin
  @DataJpaTest
  class NpcProfileRepositoryTest @Autowired constructor(
      private val userRepo: UserRepository,
      private val npcRepo: NpcProfileRepository,
  ) {
      @Test
      fun `save + findByUserId`() {
          val user = userRepo.save(User(nickname = "NPC-Fair-abc123", role = Role.NPC))
          val profile = npcRepo.save(NpcProfile(user = user, strategy = NpcStrategy.FAIR,
              params = NpcParams.Fair(0.4), seed = 42))
          val loaded = npcRepo.findByUserId(user.id!!)!!
          assertEquals(NpcStrategy.FAIR, loaded.strategy)
          assertEquals(NpcParams.Fair(0.4), loaded.params)
          assertEquals(42L, loaded.seed)
      }
  }
  ```

- [ ] **Step 2: Прогнать — FAIL** (не скомпилируется, классы не существуют).

- [ ] **Step 3: Создать `NpcStrategy.kt` и `NpcParams.kt`**

  ```kotlin
  // NpcStrategy.kt
  package edu.itmo.ultimatumgame.model
  enum class NpcStrategy { FAIR, SELFISH, RANDOM, VENGEFUL, ADAPTIVE }
  ```

  ```kotlin
  // NpcParams.kt
  package edu.itmo.ultimatumgame.model
  import com.fasterxml.jackson.annotation.JsonSubTypes
  import com.fasterxml.jackson.annotation.JsonTypeInfo

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(
      JsonSubTypes.Type(value = NpcParams.Fair::class, name = "FAIR"),
      JsonSubTypes.Type(value = NpcParams.Selfish::class, name = "SELFISH"),
      JsonSubTypes.Type(value = NpcParams.Random::class, name = "RANDOM"),
      JsonSubTypes.Type(value = NpcParams.Vengeful::class, name = "VENGEFUL"),
      JsonSubTypes.Type(value = NpcParams.Adaptive::class, name = "ADAPTIVE"),
  )
  sealed interface NpcParams {
      data class Fair(val fairnessThreshold: Double = 0.30) : NpcParams
      data class Selfish(val minOffer: Int = 0) : NpcParams
      data class Random(val acceptProbability: Double = 0.5) : NpcParams
      data class Vengeful(val baselineFraction: Double = 0.5, val punishStep: Int = 1,
                          val fairnessThreshold: Double = 0.30) : NpcParams
      data class Adaptive(val baselineFraction: Double = 0.5, val targetRejectRate: Double = 0.2,
                          val slope: Double = 0.5) : NpcParams
  }
  ```

- [ ] **Step 4: Создать `NpcProfile.kt` entity**

  ```kotlin
  package edu.itmo.ultimatumgame.model
  import com.fasterxml.jackson.databind.ObjectMapper
  import jakarta.persistence.*
  import org.hibernate.annotations.JdbcTypeCode
  import org.hibernate.type.SqlTypes
  import java.util.Date
  import java.util.UUID

  @Entity
  @Table(name = "npc_profile")
  class NpcProfile(
      @Id @GeneratedValue(strategy = GenerationType.UUID)
      var id: UUID? = null,

      @OneToOne(optional = false, fetch = FetchType.LAZY)
      @JoinColumn(name = "user_id", unique = true, nullable = false)
      var user: User,

      @Enumerated(EnumType.STRING) @Column(nullable = false)
      var strategy: NpcStrategy,

      @JdbcTypeCode(SqlTypes.JSON) @Column(name = "params_json", nullable = false, columnDefinition = "jsonb")
      var params: NpcParams,

      @Column
      var seed: Long? = null,

      @Column(nullable = false, updatable = false)
      var createdAt: Date = Date(),
  )
  ```

- [ ] **Step 5: Создать `NpcProfileRepository.kt`**

  ```kotlin
  package edu.itmo.ultimatumgame.repositories
  import edu.itmo.ultimatumgame.model.NpcProfile
  import org.springframework.data.jpa.repository.JpaRepository
  import java.util.UUID
  interface NpcProfileRepository : JpaRepository<NpcProfile, UUID> {
      fun findByUserId(id: UUID): NpcProfile?
      fun existsByUserNickname(nickname: String): Boolean
  }
  ```

- [ ] **Step 6: Добавить unique-индекс в `index.sql`**

  ```sql
  CREATE UNIQUE INDEX IF NOT EXISTS ix_npc_profile_user_id ON npc_profile(user_id);
  ```

- [ ] **Step 7: Прогнать тест — PASS**

  `./gradlew test --tests "*NpcProfileRepositoryTest*" > /tmp/gradle-t2.log 2>&1`.

- [ ] **Step 8: Full check + commit**

  `./gradlew check > /tmp/gradle-t2-check.log 2>&1`. Проходит detekt + jacoco (repository-класс исключён из coverage-includes, но связанные модели — тоже, т.к. в `coverageIncludes` только `services/**` + `model/Shuffle*` + `Free/TeamBattle` — см. `build.gradle.kts:116-121`).

  ```bash
  git add src/main/kotlin/.../model/NpcProfile.kt \
          src/main/kotlin/.../model/NpcStrategy.kt \
          src/main/kotlin/.../model/NpcParams.kt \
          src/main/kotlin/.../repositories/NpcProfileRepository.kt \
          src/main/resources/index.sql \
          src/test/kotlin/.../repositories/NpcProfileRepositoryTest.kt
  git commit -m "feat(T-077): NpcProfile entity + repository + JSONB params

  Refs: docs/tasks/T-077-npc-profile-entity.md"
  git push origin main
  ```

---

## Task 3: `NpcStrategyPlayer` interface + Fair / Selfish / Random стратегии (unit-tested)

**Files:**
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/NpcStrategyPlayer.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/FairStrategy.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/SelfishStrategy.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/RandomStrategy.kt`
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/model/npc/FairStrategyTest.kt`
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/model/npc/SelfishStrategyTest.kt`
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/model/npc/RandomStrategyTest.kt`

**Interfaces:**
- Consumes: `NpcParams` из Task 2.
- Produces:
  - `data class RoundOutcome(val roundNumber: Int, val myOfferAmount: Int?, val myOfferAccepted: Boolean?, val incomingOfferAmount: Int?, val incomingAccepted: Boolean?)`
  - `data class OfferCtx(val session: Session, val round: Round, val me: User, val myPastRounds: List<RoundOutcome>, val random: java.util.Random)`
  - `data class DecisionCtx(val session: Session, val round: Round, val me: User, val incomingOffer: Offer, val myPastRounds: List<RoundOutcome>, val random: java.util.Random)`
  - `interface NpcStrategyPlayer { fun offer(ctx: OfferCtx): Int; fun decide(ctx: DecisionCtx): Boolean }`
  - `class FairStrategy(val p: NpcParams.Fair) : NpcStrategyPlayer`
  - `class SelfishStrategy(val p: NpcParams.Selfish) : NpcStrategyPlayer`
  - `class RandomStrategy(val p: NpcParams.Random) : NpcStrategyPlayer`

- [ ] **Step 1: Написать три провальных теста (по одному на стратегию)**

  ```kotlin
  // FairStrategyTest.kt
  class FairStrategyTest {
      @Test fun `offer = roundSum div 2`() {
          val ctx = TestFactories.offerCtx(roundSum = 100)
          assertEquals(50, FairStrategy(NpcParams.Fair()).offer(ctx))
      }
      @Test fun `decide accept at threshold`() {
          val ctx = TestFactories.decisionCtx(roundSum = 100, incomingAmount = 30)
          assertTrue(FairStrategy(NpcParams.Fair(fairnessThreshold = 0.30)).decide(ctx))
      }
      @Test fun `decide reject below threshold`() {
          val ctx = TestFactories.decisionCtx(roundSum = 100, incomingAmount = 29)
          assertFalse(FairStrategy(NpcParams.Fair(fairnessThreshold = 0.30)).decide(ctx))
      }
  }
  ```

  Аналогично SelfishStrategyTest (offer=minOffer, decide accept > 0) и RandomStrategyTest (fixed seed → детерминированные значения).

- [ ] **Step 2: Создать `TestFactories`** (helper для сборки OfferCtx/DecisionCtx с минимальным Session/Round/User): `src/test/kotlin/edu/itmo/ultimatumgame/model/npc/TestFactories.kt`.

- [ ] **Step 3: Прогнать — FAIL** (классы не существуют).

- [ ] **Step 4: Создать `NpcStrategyPlayer.kt`**

  Определить `RoundOutcome`, `OfferCtx`, `DecisionCtx`, `interface NpcStrategyPlayer`.

- [ ] **Step 5: Реализовать `FairStrategy`**

  ```kotlin
  class FairStrategy(private val p: NpcParams.Fair) : NpcStrategyPlayer {
      override fun offer(ctx: OfferCtx): Int = ctx.session.config!!.roundSum / 2
      override fun decide(ctx: DecisionCtx): Boolean {
          val roundSum = ctx.session.config!!.roundSum
          return ctx.incomingOffer.offerValue >= (p.fairnessThreshold * roundSum).toInt()
      }
  }
  ```

- [ ] **Step 6: `SelfishStrategy`**

  ```kotlin
  class SelfishStrategy(private val p: NpcParams.Selfish) : NpcStrategyPlayer {
      override fun offer(ctx: OfferCtx): Int = p.minOffer.coerceIn(0, ctx.session.config!!.roundSum)
      override fun decide(ctx: DecisionCtx): Boolean = ctx.incomingOffer.offerValue > 0
  }
  ```

- [ ] **Step 7: `RandomStrategy`**

  ```kotlin
  class RandomStrategy(private val p: NpcParams.Random) : NpcStrategyPlayer {
      override fun offer(ctx: OfferCtx): Int = ctx.random.nextInt(ctx.session.config!!.roundSum + 1)
      override fun decide(ctx: DecisionCtx): Boolean = ctx.random.nextDouble() < p.acceptProbability
  }
  ```

- [ ] **Step 8: Прогнать все три теста — PASS**.

- [ ] **Step 9: Full check + commit**

  ```bash
  git commit -m "feat(T-078): NpcStrategyPlayer + Fair/Selfish/Random стратегии

  Refs: docs/tasks/T-078-basic-npc-strategies.md"
  ```

---

## Task 4: Memory-стратегии Vengeful + Adaptive (unit-tested)

**Files:**
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/VengefulStrategy.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/model/npc/AdaptiveStrategy.kt`
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/model/npc/VengefulStrategyTest.kt`
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/model/npc/AdaptiveStrategyTest.kt`

**Interfaces:**
- Consumes: интерфейс `NpcStrategyPlayer` + `RoundOutcome` из Task 3, `NpcParams.Vengeful`/`NpcParams.Adaptive` из Task 2.
- Produces: `class VengefulStrategy(...)`, `class AdaptiveStrategy(...)`.

- [ ] **Step 1: Провальные тесты**

  ```kotlin
  class VengefulStrategyTest {
      @Test fun `baseline offer if no history`() {
          val ctx = TestFactories.offerCtx(roundSum = 100, myPastRounds = emptyList())
          assertEquals(50, VengefulStrategy(NpcParams.Vengeful()).offer(ctx))
      }
      @Test fun `punish step after reject in prev round`() {
          val past = listOf(RoundOutcome(roundNumber = 1, myOfferAmount = 50,
              myOfferAccepted = false, incomingOfferAmount = null, incomingAccepted = null))
          val ctx = TestFactories.offerCtx(roundSum = 100, myPastRounds = past)
          assertEquals(49, VengefulStrategy(NpcParams.Vengeful(punishStep = 1)).offer(ctx))
      }
  }

  class AdaptiveStrategyTest {
      @Test fun `no history — baseline offer`() {
          val ctx = TestFactories.offerCtx(roundSum = 100, myPastRounds = emptyList())
          assertEquals(50, AdaptiveStrategy(NpcParams.Adaptive()).offer(ctx))
      }
      @Test fun `high rejectRate raises offerFraction`() {
          val past = (1..3).map { RoundOutcome(it, myOfferAmount = 20, myOfferAccepted = false,
              incomingOfferAmount = null, incomingAccepted = null) }
          val ctx = TestFactories.offerCtx(roundSum = 100, myPastRounds = past)
          val offer = AdaptiveStrategy(NpcParams.Adaptive(baselineFraction = 0.5,
              targetRejectRate = 0.2, slope = 0.5)).offer(ctx)
          // rejectRate=1.0, delta=0.8, offerFraction=0.5+0.5*0.8=0.9 → 90
          assertEquals(90, offer)
      }
  }
  ```

- [ ] **Step 2: Прогнать — FAIL**.

- [ ] **Step 3: Реализовать `VengefulStrategy`**

  ```kotlin
  class VengefulStrategy(private val p: NpcParams.Vengeful) : NpcStrategyPlayer {
      override fun offer(ctx: OfferCtx): Int {
          val roundSum = ctx.session.config!!.roundSum
          val baseline = (p.baselineFraction * roundSum).toInt()
          val last = ctx.myPastRounds.lastOrNull() ?: return baseline
          return if (last.myOfferAccepted == false) {
              maxOf(0, (last.myOfferAmount ?: baseline) - p.punishStep)
          } else baseline
      }
      override fun decide(ctx: DecisionCtx): Boolean {
          val roundSum = ctx.session.config!!.roundSum
          val lastIncoming = ctx.myPastRounds.lastOrNull()?.incomingOfferAmount
          val baselineIncoming = (p.baselineFraction * roundSum).toInt()
          val effectiveThreshold = if (lastIncoming != null && lastIncoming < baselineIncoming) {
              (p.fairnessThreshold + 0.05).coerceAtMost(0.5)
          } else p.fairnessThreshold
          return ctx.incomingOffer.offerValue >= (effectiveThreshold * roundSum).toInt()
      }
  }
  ```

- [ ] **Step 4: Реализовать `AdaptiveStrategy`**

  ```kotlin
  class AdaptiveStrategy(private val p: NpcParams.Adaptive) : NpcStrategyPlayer {
      override fun offer(ctx: OfferCtx): Int {
          val roundSum = ctx.session.config!!.roundSum
          if (ctx.myPastRounds.isEmpty()) return (p.baselineFraction * roundSum).toInt()
          val myOffers = ctx.myPastRounds.count { it.myOfferAmount != null }
          if (myOffers == 0) return (p.baselineFraction * roundSum).toInt()
          val rejected = ctx.myPastRounds.count { it.myOfferAccepted == false }
          val rejectRate = rejected.toDouble() / myOffers
          val fraction = (p.baselineFraction + p.slope * (rejectRate - p.targetRejectRate))
              .coerceIn(0.0, 1.0)
          return (fraction * roundSum).toInt()
      }
      override fun decide(ctx: DecisionCtx): Boolean {
          // как Fair: accept iff >= baselineFraction * roundSum
          val roundSum = ctx.session.config!!.roundSum
          return ctx.incomingOffer.offerValue >= (p.baselineFraction * roundSum).toInt() / 2  // threshold = half of baseline
      }
  }
  ```

  *(Уточнение — decide для Adaptive намеренно мягче: он оптимизирует свою proposer-стратегию, а на responding-стороне принимает всё выше «половины baseline» — иначе слишком агрессивно режектит.)*

- [ ] **Step 5: Прогнать — PASS**.

- [ ] **Step 6: Full check + commit**

  ```bash
  git commit -m "feat(T-079): Vengeful + Adaptive memory-стратегии

  Refs: docs/tasks/T-079-memory-npc-strategies.md"
  ```

---

## Task 5: `NpcService` — оркестратор стратегий + hooks в gameplay + fallback

**Files:**
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt`
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/services/AdminGameplayService.kt` (hook в `startNextRound`)
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/services/CoreGameplayService.kt` (hook в `initWaitDecisionsPhase`)
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/util/DomainEventLogger.kt` (события `NpcStrategyFailed`)
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/services/NpcServiceTest.kt`

**Interfaces:**
- Consumes: `NpcStrategyPlayer`, все стратегии, `NpcProfileRepository`, `OfferRepository`, `DecisionRepository`, `RoundRepository`, `EventPublisherService`.
- Produces:
  - `class NpcService { fun playOffers(round: Round); fun playDecisions(round: Round); private fun strategyOf(profile: NpcProfile): NpcStrategyPlayer; private fun buildOfferCtx(...); private fun buildDecisionCtx(...); }`
  - Domain event: `data class NpcStrategyFailed(val sessionId: UUID, val roundId: UUID, val userId: UUID, val strategy: NpcStrategy, val phase: String) : DomainEvent`

- [ ] **Step 1: Провальные unit-тесты `NpcServiceTest`**

  Четыре сценария:

  ```kotlin
  @Test fun `playOffers — сохраняет offer для каждого NPC-члена и не трогает людей`() { ... }
  @Test fun `playOffers — no-op при phase != WAIT_OFFERS`() { ... }
  @Test fun `playOffers — fallback FAIR если стратегия throw`() { ... }
  @Test fun `playDecisions — сохраняет decision для каждого NPC-члена по назначенному offer'у`() { ... }
  ```

  Использовать `@MockBean` для репозиториев + фейковый `NpcStrategyPlayer` throw'ающий.

- [ ] **Step 2: FAIL**.

- [ ] **Step 3: Реализация `NpcService`** (эскиз):

  ```kotlin
  @Service
  class NpcService(
      private val npcProfileRepo: NpcProfileRepository,
      private val offerRepo: OfferRepository,
      private val decisionRepo: DecisionRepository,
      private val roundRepo: RoundRepository,
      private val eventPublisher: EventPublisherService,
      private val domainEventLogger: DomainEventLogger,
  ) {
      private val logger = logger()

      @Transactional
      fun playOffers(round: Round) {
          if (round.roundPhase != RoundPhase.WAIT_OFFERS) {
              logger.warn("playOffers no-op: phase=${round.roundPhase}")
              return
          }
          val session = round.session
          val npcMembers = session.members.filter { it.role == Role.NPC }
          for (npc in npcMembers) {
              if (round.offers.any { it.proposer?.id == npc.id }) continue  // уже есть (retry safety)
              val profile = npcProfileRepo.findByUserId(npc.id!!) ?: continue
              val ctx = buildOfferCtx(session, round, npc, profile)
              val amount = try { strategyOf(profile).offer(ctx) }
                  catch (e: Exception) {
                      logger.error("NPC strategy failed for user=${npc.id}, fallback FAIR", e)
                      domainEventLogger.emit(NpcStrategyFailed(session.id!!, round.id!!, npc.id!!, profile.strategy, "offer"))
                      FairStrategy(NpcParams.Fair()).offer(ctx)
                  }
              val clamped = amount.coerceIn(0, session.config!!.roundSum)
              val offer = offerRepo.save(Offer(session = session, round = round, proposer = npc, offerValue = clamped))
              round.offers += offer
              eventPublisher.publishOfferCreated(session.id!!, offer)
          }
          roundRepo.save(round)
      }

      @Transactional
      fun playDecisions(round: Round) {
          if (round.roundPhase != RoundPhase.OFFERS_SENT) return
          val session = round.session
          val npcMembers = session.members.filter { it.role == Role.NPC }
          for (npc in npcMembers) {
              if (round.decisions.any { it.responder?.id == npc.id }) continue
              val incoming = round.offers.find { it.responder?.id == npc.id } ?: continue
              val profile = npcProfileRepo.findByUserId(npc.id!!) ?: continue
              val ctx = buildDecisionCtx(session, round, npc, incoming, profile)
              val accepted = try { strategyOf(profile).decide(ctx) }
                  catch (e: Exception) {
                      domainEventLogger.emit(NpcStrategyFailed(session.id!!, round.id!!, npc.id!!, profile.strategy, "decide"))
                      FairStrategy(NpcParams.Fair()).decide(ctx)
                  }
              val decision = decisionRepo.save(Decision(session = session, round = round,
                  responder = npc, offer = incoming, decision = accepted))
              round.decisions += decision
              eventPublisher.publishDecisionMade(session.id!!, decision)
          }
          roundRepo.save(round)
      }

      private fun strategyOf(profile: NpcProfile): NpcStrategyPlayer = when (profile.strategy) {
          NpcStrategy.FAIR -> FairStrategy(profile.params as NpcParams.Fair)
          NpcStrategy.SELFISH -> SelfishStrategy(profile.params as NpcParams.Selfish)
          NpcStrategy.RANDOM -> RandomStrategy(profile.params as NpcParams.Random)
          NpcStrategy.VENGEFUL -> VengefulStrategy(profile.params as NpcParams.Vengeful)
          NpcStrategy.ADAPTIVE -> AdaptiveStrategy(profile.params as NpcParams.Adaptive)
      }

      private fun buildOfferCtx(session: Session, round: Round, me: User, profile: NpcProfile): OfferCtx {
          val past = pastRoundsFor(session, me)
          val random = randomFor(profile, round, phaseTag = 0L)
          return OfferCtx(session, round, me, past, random)
      }

      private fun buildDecisionCtx(session: Session, round: Round, me: User, incoming: Offer, profile: NpcProfile): DecisionCtx {
          val past = pastRoundsFor(session, me)
          val random = randomFor(profile, round, phaseTag = 1L)
          return DecisionCtx(session, round, me, incoming, past, random)
      }

      private fun pastRoundsFor(session: Session, me: User): List<RoundOutcome> =
          session.rounds.filter { it.roundNumber < (session.currentRound?.roundNumber ?: Int.MAX_VALUE) }
              .sortedBy { it.roundNumber }
              .map { r ->
                  val myOffer = r.offers.find { it.proposer?.id == me.id }
                  val myOfferDecision = myOffer?.let { off -> r.decisions.find { it.offer?.id == off.id } }
                  val incoming = r.offers.find { it.responder?.id == me.id }
                  val incomingDecision = incoming?.let { off -> r.decisions.find { it.offer?.id == off.id } }
                  RoundOutcome(
                      roundNumber = r.roundNumber,
                      myOfferAmount = myOffer?.offerValue,
                      myOfferAccepted = myOfferDecision?.decision,
                      incomingOfferAmount = incoming?.offerValue,
                      incomingAccepted = incomingDecision?.decision,
                  )
              }

      private fun randomFor(profile: NpcProfile, round: Round, phaseTag: Long): java.util.Random {
          val seed = profile.seed ?: return java.util.Random()
          val roundMsb = round.id?.mostSignificantBits ?: 0L
          return java.util.Random(seed xor roundMsb xor phaseTag)
      }
  }
  ```

- [ ] **Step 4: Добавить `NpcStrategyFailed` в `DomainEventLogger.kt`** (аналогично существующим).

- [ ] **Step 5: Hook в `AdminGameplayService.startNextRound`**

  В самом конце метода (после сохранения round + публикации RoundStatus) — вызвать `npcService.playOffers(round)`. После — если `round.offers.size == session.members.size` → `round.phase = ALL_OFFERS_RECEIVED; coreGameplayService.initWaitDecisionsPhase(session)`.

- [ ] **Step 6: Hook в `CoreGameplayService.initWaitDecisionsPhase`**

  В конце (после установки `OFFERS_SENT` + `publishRoundStatus`) — `npcService.playDecisions(round)`. После — если `round.decisions.size == session.members.size` → `round.phase = ALL_DECISIONS_RECEIVED; publishRoundStatus; publishScoreUpdated`.

- [ ] **Step 7: Прогнать unit-тесты `NpcServiceTest` — PASS**.

- [ ] **Step 8: Прогнать regression** — `./gradlew check` (существующие integration'ы не должны сломаться).

- [ ] **Step 9: Commit**

  ```bash
  git commit -m "feat(T-080): NpcService + hooks в start/init-decisions + fallback FAIR

  Refs: docs/tasks/T-080-npc-service-and-hooks.md"
  ```

---

## Task 6: `autoAdvanceRounds` — hook после ALL_DECISIONS_RECEIVED

**Files:**
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/services/PlayerGameplayService.kt` (в `makeDecision` после `publishScoreUpdated`)
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt` (в `playDecisions` в конце — то же условие)
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/it/NpcAutoAdvanceIntegrationTest.kt`

**Interfaces:**
- Consumes: `AdminGameplayService.startNextRound(sessionId: UUID)`.
- Produces: no new public interface (только приватная логика).

- [ ] **Step 1: Провальный integration test**

  ```kotlin
  @SpringBootTest
  @AutoConfigureMockMvc
  class NpcAutoAdvanceIntegrationTest {
      @Test
      fun `all-NPC session с autoAdvance проходит все раунды одним start-запросом`() {
          // 1. Создать сессию с autoAdvanceRounds=true, numRounds=3, numPlayers=4, FREE_FOR_ALL
          // 2. POST /session/{id}/npcs count=4 strategy=FAIR seedBase=1
          // 3. POST /session/{id}/start
          // 4. Ассерт: session.state == FINISHED, rounds.size == 3, все offers+decisions на месте
      }
  }
  ```

- [ ] **Step 2: FAIL** — endpoint'ы /npcs ещё нет. (Здесь либо ждём Task 8, либо стабим NPC через прямые репозитории. Правильнее — этот integration прогнать после Task 8; сейчас можно сделать unit-эмуляцию через прямые вызовы `sessionService.addNpcMember`.)

  → **Corrected step**: писать unit-тест `PlayerGameplayServiceTest.autoAdvance triggers next startNextRound` через `@MockBean AdminGameplayService`.

  ```kotlin
  @Test
  fun `makeDecision — вызывает startNextRound если autoAdvanceRounds и есть next round`() {
      // Session c autoAdvanceRounds=true, currentRound=1, numRounds=3
      // Последний decision → check startNextRound(sessionId) вызван 1 раз
  }
  ```

- [ ] **Step 3: Реализовать autoAdvance-хук в `PlayerGameplayService.makeDecision`**

  В блоке `if (round.decisions.size == session.members.size)` после `domainEventLogger.emit(RoundClosed(...))`:

  ```kotlin
  if (session.config?.autoAdvanceRounds == true
      && session.state == SessionState.IN_PROGRESS
      && round.roundNumber < session.config!!.numRounds) {
      logger.info("autoAdvanceRounds включён — старт следующего раунда сессии {}", session.id)
      adminGameplayService.startNextRound(session.id!!)
  }
  ```

  **Важно**: `adminGameplayService` инжектится в конструктор `PlayerGameplayService`. Проверить, нет ли circular-dep — если есть, использовать `ObjectProvider<AdminGameplayService>` или `@Lazy`.

- [ ] **Step 4: То же самое в `NpcService.playDecisions`** (когда раунд закрыт NPC-действием):

  Тот же блок. Тоже через `@Lazy AdminGameplayService`.

- [ ] **Step 5: Unit-тест — PASS**.

- [ ] **Step 6: Full check + commit**

  ```bash
  git commit -m "feat(T-081): autoAdvanceRounds — hook в makeDecision + NpcService

  Refs: docs/tasks/T-081-auto-advance-rounds-hook.md"
  ```

---

## Task 7: REST — `POST/GET/DELETE /npc`

**Files:**
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/controllers/NpcController.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/NpcRequests.kt`
- Create: `src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/NpcResponses.kt`
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt` — методы `create`, `list`, `get`, `delete`
- Test: `src/test/kotlin/edu/itmo/ultimatumgame/controllers/NpcControllerTest.kt`

**Interfaces:**
- Consumes: `NpcService.create(req: CreateNpcRequest): NpcProfile`, `.list(): List<NpcProfile>`, `.get(id: UUID): NpcProfile`, `.delete(id: UUID)`.
- Produces:
  - `data class CreateNpcRequest(val nickname: String, val strategy: NpcStrategy, val params: NpcParams, val seed: Long? = null)`
  - `data class NpcProfileResponse(val id: UUID, val userId: UUID, val nickname: String, val strategy: NpcStrategy, val params: NpcParams, val seed: Long?, val createdAt: Instant)`

- [ ] **Step 1: Провальный `@WebMvcTest` controller-тест**

  ```kotlin
  @Test fun `POST npc создаёт NPC — 201 с id`() {
      mockMvc.perform(post("/npc")
          .contentType(APPLICATION_JSON)
          .content("""{"nickname":"Bob-Fair","strategy":"FAIR","params":{"type":"FAIR","fairnessThreshold":0.4}}""")
          .with(user(admin)))
          .andExpect(status().isCreated)
          .andExpect(jsonPath("$.strategy").value("FAIR"))
  }

  @Test fun `POST npc — 400 если strategy=FAIR но params=Selfish`() { ... }
  @Test fun `DELETE npc — 409 если NPC в открытой сессии`() { ... }
  @Test fun `GET npc список — только ADMIN`() { ... }
  ```

- [ ] **Step 2: FAIL** — endpoint'а нет.

- [ ] **Step 3: Реализовать DTO + controller**

  ```kotlin
  // NpcController.kt
  @RestController
  @RequestMapping("/npc")
  @PreAuthorize("hasRole('ADMIN')")
  class NpcController(private val npcService: NpcService) {
      @PostMapping
      fun create(@Valid @RequestBody req: CreateNpcRequest): ResponseEntity<NpcProfileResponse> =
          ResponseEntity.status(HttpStatus.CREATED).body(npcService.create(req).toResponse())
      @GetMapping fun list(): List<NpcProfileResponse> = npcService.list().map { it.toResponse() }
      @GetMapping("/{id}") fun get(@PathVariable id: UUID): NpcProfileResponse = npcService.get(id).toResponse()
      @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
      fun delete(@PathVariable id: UUID) = npcService.delete(id)
  }
  ```

- [ ] **Step 4: Реализовать `NpcService.create/list/get/delete`**

  ```kotlin
  fun create(req: CreateNpcRequest): NpcProfile {
      require(paramsMatchStrategy(req.strategy, req.params)) { "params не соответствуют strategy" }
      if (userRepo.existsByNickname(req.nickname)) throw DuplicateIdException("nickname уже занят")
      val user = userRepo.save(User(nickname = req.nickname, role = Role.NPC))
      return npcProfileRepo.save(NpcProfile(user = user, strategy = req.strategy,
          params = req.params, seed = req.seed))
  }
  fun list() = npcProfileRepo.findAll()
  fun get(id: UUID) = npcProfileRepo.findById(id).orElseThrow { IdNotFoundException("NPC $id не найден") }
  fun delete(id: UUID) {
      val profile = get(id)
      val inOpenSession = sessionRepo.existsByMembersContainingAndStateNotIn(profile.user,
          listOf(SessionState.FINISHED, SessionState.ABORTED))
      if (inOpenSession) throw IllegalStateException("NPC участвует в открытой сессии")
      npcProfileRepo.delete(profile)
  }
  private fun paramsMatchStrategy(strategy: NpcStrategy, p: NpcParams): Boolean = when (strategy) {
      NpcStrategy.FAIR -> p is NpcParams.Fair
      NpcStrategy.SELFISH -> p is NpcParams.Selfish
      NpcStrategy.RANDOM -> p is NpcParams.Random
      NpcStrategy.VENGEFUL -> p is NpcParams.Vengeful
      NpcStrategy.ADAPTIVE -> p is NpcParams.Adaptive
  }
  ```

  Требуется `sessionRepo.existsByMembersContainingAndStateNotIn(...)` — метод в `SessionRepository`, тоже добавить.

- [ ] **Step 5: Прогнать controller-тесты — PASS**.

- [ ] **Step 6: Regenerate API snapshots**

  `./gradlew generateApiSnapshots` → `openapi.json` содержит `/npc` endpoints. Копирование в `frontend-integration/specs/` — авто (T-069).

- [ ] **Step 7: Full check + commit**

  ```bash
  git commit -m "feat(T-082): REST — POST/GET/DELETE /npc + валидация strategy/params

  Refs: docs/tasks/T-082-npc-crud-endpoints.md"
  ```

---

## Task 8: `POST /session/{id}/join-npc` + `POST /session/{id}/npcs` (bulk) + finalize integration

**Files:**
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/controllers/SessionController.kt` (или отдельный контроллер, если структура позволит)
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt` — методы `addNpcMember(sessionId, npcId)`, `bulkCreateAndJoinNpcs(sessionId, count, strategy, params, seedBase)`
- Modify: `src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/SessionRequests.kt` — `JoinNpcRequest`, `BulkNpcsRequest`
- Create: `src/test/kotlin/edu/itmo/ultimatumgame/it/NpcGameplayIntegrationTest.kt` — 4 сценария из спеки (all-NPC autoAdvance, human+NPC FREE_FOR_ALL, human+NPC TEAM_BATTLE, abort mid-tick)
- Modify: `frontend-integration/*.md` (04, 05, 06 — обновить endpoints/DTO), `docs/*.md` (где упоминается роли/фазы)
- Regenerate: `openapi.json`, `asyncapi.json` — авто через T-069

**Interfaces:**
- Consumes: `NpcService.create` из Task 7, `AdminGameplayService.startNextRound`, `PlayerGameplayService`, `SessionService`.
- Produces:
  - `POST /session/{id}/join-npc {npcId: UUID}` → 200 SessionResponse.
  - `POST /session/{id}/npcs {count, strategy, params, seedBase?}` → 200 `BulkNpcsResponse(session: SessionResponse, npcs: List<NpcProfileResponse>)`.

- [ ] **Step 1: Провальные controller-тесты + integration'ы**

  ```kotlin
  @Test fun `POST join-npc — добавляет NPC в session members`() { ... }
  @Test fun `POST npcs — bulk создаёт N ботов и аттачит`() { ... }
  @Test fun `POST npcs — 400 если count + members больше numPlayers`() { ... }
  ```

  И `NpcGameplayIntegrationTest`:
  ```kotlin
  @Test fun `all-NPC autoAdvance FREE_FOR_ALL 3 раунда — finished в одном /start`() { ... }
  @Test fun `human + 3 NPC FREE_FOR_ALL — phase transitions корректны`() { ... }
  @Test fun `human + 3 NPC TEAM_BATTLE — respondent из чужой команды`() { ... }
  @Test fun `abortCurrentRound во время NPC-tick — фаза ABORTED, no-op`() { ... }
  ```

- [ ] **Step 2: FAIL**.

- [ ] **Step 3: Реализовать `SessionService.addNpcMember` + `bulkCreateAndJoinNpcs`**

  ```kotlin
  @Transactional
  fun addNpcMember(sessionId: UUID, npcId: UUID): Session {
      val session = getSessionEntity(sessionId)
      require(session.state == SessionState.CREATED && session.openToConnect) {
          "join-npc доступен только для сессий в состоянии CREATED и открытых к подключению"
      }
      val profile = npcProfileRepo.findById(npcId).orElseThrow { IdNotFoundException(...) }
      require(session.members.size < session.config!!.numPlayers) {
          "лимит numPlayers=${session.config!!.numPlayers} достигнут"
      }
      session.members += profile.user
      return sessionRepo.save(session)
  }

  @Transactional
  fun bulkCreateAndJoinNpcs(sessionId: UUID, req: BulkNpcsRequest): BulkNpcsResponse {
      val session = getSessionEntity(sessionId)
      require(session.state == SessionState.CREATED && session.openToConnect)
      require(session.members.size + req.count <= session.config!!.numPlayers)
      require(req.count in 1..100)
      val createdProfiles = (0 until req.count).map { i ->
          val nick = "NPC-${req.strategy}-${UUID.randomUUID().toString().take(6)}"
          val user = userRepo.save(User(nickname = nick, role = Role.NPC))
          val seed = req.seedBase?.plus(i.toLong())
          npcProfileRepo.save(NpcProfile(user = user, strategy = req.strategy,
              params = req.params, seed = seed))
      }
      createdProfiles.forEach { session.members += it.user }
      sessionRepo.save(session)
      return BulkNpcsResponse(session.toResponse(), createdProfiles.map { it.toResponse() })
  }
  ```

- [ ] **Step 4: Endpoints в `SessionController`**

  ```kotlin
  @PostMapping("/session/{id}/join-npc")
  @PreAuthorize("hasRole('ADMIN')")
  fun joinNpc(@PathVariable id: UUID, @Valid @RequestBody req: JoinNpcRequest): SessionResponse =
      sessionService.addNpcMember(id, req.npcId).toResponse()

  @PostMapping("/session/{id}/npcs")
  @PreAuthorize("hasRole('ADMIN')")
  fun bulkNpcs(@PathVariable id: UUID, @Valid @RequestBody req: BulkNpcsRequest): BulkNpcsResponse =
      sessionService.bulkCreateAndJoinNpcs(id, req)
  ```

- [ ] **Step 5: Прогнать controller + integration тесты — PASS**.

- [ ] **Step 6: Обновить frontend-integration docs**

  - `frontend-integration/04-rest-api.md` — новые endpoints `/npc`, `/session/{id}/join-npc`, `/session/{id}/npcs`.
  - `frontend-integration/06-data-models.md` — DTO `CreateNpcRequest`, `NpcProfileResponse`, `BulkNpcsRequest`, `JoinNpcRequest`, обновление `SessionConfigDto.autoAdvanceRounds`.
  - `frontend-integration/02-game-rules.md` — снять пометку «NPC не реализованы», описать pipeline (human + NPC + autoAdvance).
  - `frontend-integration/09-integration-flows.md` — добавить сценарий «all-NPC симуляция за один клик».

- [ ] **Step 7: Regenerate + full check**

  `./gradlew generateApiSnapshots && ./gradlew check`.

- [ ] **Step 8: Commit**

  ```bash
  git commit -m "feat(T-083): join-npc + bulk /npcs + integration-сценарии + docs

  Финальная задача v1: все контракты закрыты, all-NPC autoAdvance работает
  end-to-end.

  Refs: docs/tasks/T-083-session-join-npc-and-bulk.md"
  ```

---

## Post-plan actions

После всех 8 задач:

1. Обновить `frontend-integration/00-README.md` / `docs/tasks/INDEX.md`.
2. Consolidation-check: с последнего /consolidate закрыто ≥ 10 задач? Если да — предложить пользователю `/consolidate`.
3. Финальный smoke: локально `bootRun` → создать сессию через swagger → all-NPC autoAdvance → посмотреть stats.

---

## Self-Review

**1. Spec coverage:**
- ✅ 5 стратегий (Fair/Selfish/Random/Vengeful/Adaptive) — Tasks 3, 4.
- ✅ `NpcProfile` entity + JSONB — Task 2.
- ✅ Автопилот (WAIT_OFFERS, OFFERS_SENT triggers) — Task 5.
- ✅ Fallback FAIR — Task 5, Step 3.
- ✅ `autoAdvanceRounds` — Tasks 1 (поле) + 6 (hook).
- ✅ REST `/npc/*` — Task 7.
- ✅ REST `/session/{id}/join-npc` + `/session/{id}/npcs` — Task 8.
- ✅ Integration тесты (4 сценария из спеки) — Task 8, Step 1.
- ✅ Скоринг без изменений — покрывается regression в `./gradlew check` в каждой Task'е.
- ✅ Детерминизм seed через `Random(seed XOR round.id.msb XOR phaseTag)` — Task 5, `randomFor`.

**2. Placeholder scan:** проверил — нет «TBD», «TODO», ссылок на «Similar to Task N».

**3. Type consistency:** методы `startNextRound(sessionId: UUID)`, `initWaitDecisionsPhase(session: Session)`, `playOffers(round: Round)`, `playDecisions(round: Round)` — используются одинаково в Task 5, 6, 8.

**4. Ambiguity check:**
- Adaptive `.decide()`: описан «как Fair, но с половиной baseline» — уточнение в тексте Task 4, Step 4. Явно.
- `Vengeful` при отсутствии `myOfferAmount` в last round → используется `baseline`, не 0. Явно.
- Circular-dep `PlayerGameplayService ↔ AdminGameplayService`: помечено в Task 6 как риск, решение `@Lazy`. Явно.

Открытая ставка: длинная транзакция при `autoAdvanceRounds + numRounds=10 + numPlayers=120` — до 60 sec на транзакцию. В спеке в «Открытых вопросах» упомянут, здесь не деконструируем — если по нагрузочным тестам увидим таймауты, следующая задача разобьёт на per-round транзакции через `TransactionTemplate`. Оставляем как известный follow-up.

---

Plan complete and saved to `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.
