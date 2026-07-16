---
id: T-078
title: NpcStrategyPlayer interface + Fair / Selfish / Random стратегии
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/model/npc/NpcStrategyPlayer.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/npc/FairStrategy.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/npc/SelfishStrategy.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/npc/RandomStrategy.kt
related_docs:
  - docs/superpowers/specs/2026-07-16-npc-mechanic-design.md
  - docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md
tags: [backend, feature, npc]
---

## Контекст

Task 3 из NPC-плана. Интерфейс `NpcStrategyPlayer` + `OfferCtx`/`DecisionCtx`/`RoundOutcome` + три baseline-стратегии без памяти. Только новые файлы.

## Acceptance criteria

- [ ] `FairStrategy.offer(roundSum=100) == 50`, `.decide()` соблюдает `fairnessThreshold`.
- [ ] `SelfishStrategy.offer == minOffer`, `.decide(amount > 0) == true`.
- [ ] `RandomStrategy` с fixed seed → детерминированные amount/decide.
- [ ] Все три RED-теста были провальными до реализации (TDD).

## План

См. Task 3 в `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.

## Лог

- 2026-07-16: заведено из NPC-plan.
- 2026-07-16: done. Созданы `NpcStrategyPlayer` (интерфейс + `OfferCtx`/`DecisionCtx`/`RoundOutcome`), `FairStrategy`, `SelfishStrategy`, `RandomStrategy`. Extension `roundSum()` для checkNotNull на `session.config` (detekt UnsafeCallOnNullableType). Юнит-тесты по каждой стратегии + helper `NpcTestFactories`. `./gradlew check` — зелёный.
