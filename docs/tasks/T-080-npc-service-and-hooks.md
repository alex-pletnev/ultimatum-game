---
id: T-080
title: NpcService — оркестратор стратегий + hooks в start/init-decisions + fallback
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AdminGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/CoreGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/util/DomainEventLogger.kt
related_docs:
  - docs/superpowers/specs/2026-07-16-npc-mechanic-design.md
  - docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md
tags: [backend, feature, npc]
---

## Контекст

Task 5 из NPC-плана. Центральный класс механизма — `NpcService.playOffers(round)` и `.playDecisions(round)`. Синхронно вызывается из существующего gameplay-кода: `AdminGameplayService.startNextRound` и `CoreGameplayService.initWaitDecisionsPhase`. Fallback FAIR при exception в стратегии + domain event `NpcStrategyFailed`. Seed-детерминизм через `Random(seed XOR round.id.msb XOR phaseTag)`.

Контракты внешнего API не меняет — только hooks в существующих services.

## Acceptance criteria

- [ ] `playOffers` — сохраняет Offer для каждого NPC, no-op при `phase != WAIT_OFFERS`.
- [ ] `playDecisions` — сохраняет Decision по назначенному Offer, no-op при `phase != OFFERS_SENT`.
- [ ] Fallback FAIR при exception + emit `NpcStrategyFailed`.
- [ ] Regression: существующие integration'ы `./gradlew check` — зелёные.

## План

См. Task 5 в `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.

## Лог

- 2026-07-16: заведено из NPC-plan.
- 2026-07-16: done. `NpcService.playOffers/playDecisions` — синхронные хуки. Триггеры: `AdminGameplayService.startSession` (первый WAIT_OFFERS), `AdminGameplayService.startNextRound` (все последующие), `CoreGameplayService.initWaitDecisionsPhase` (после shuffle). Все офферы NPC собраны → auto `initWaitDecisionsPhase` → playDecisions. Все decisions собраны → ALL_DECISIONS_RECEIVED → publishScoreUpdated + `RoundClosed`. Circular `NpcService ↔ CoreGameplayService` — через `@Lazy`. Fallback `FairStrategy` при exception + `NpcStrategyFailed`. Seed: `Random(seed XOR round.id.msb XOR phaseTag)`. `NpcServiceTest` — 5 сценариев. `./gradlew check` — зелёный.
