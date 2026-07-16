---
id: T-079
title: Vengeful + Adaptive memory-стратегии NPC
status: pending
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/model/npc/VengefulStrategy.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/npc/AdaptiveStrategy.kt
related_docs:
  - docs/superpowers/specs/2026-07-16-npc-mechanic-design.md
  - docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md
tags: [backend, feature, npc]
---

## Контекст

Task 4 из NPC-плана. Две стратегии с историей. `Vengeful` снижает свой offer после reject'а прошлого раунда. `Adaptive` считает свой rejectRate и подстраивает offerFraction. Обе stateless в памяти — history восстанавливается из БД через `RoundOutcome`.

## Acceptance criteria

- [ ] `VengefulStrategy` — baseline при отсутствии истории; punish_step после reject.
- [ ] `AdaptiveStrategy` — offerFraction растёт при высоком rejectRate.
- [ ] TDD: RED-тесты сначала.

## План

См. Task 4 в `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.

## Лог

- 2026-07-16: заведено из NPC-plan.
