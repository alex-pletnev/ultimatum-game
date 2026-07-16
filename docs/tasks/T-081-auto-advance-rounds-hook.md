---
id: T-081
title: autoAdvanceRounds — hook в makeDecision + NpcService.playDecisions
status: pending
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/PlayerGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt
related_docs:
  - docs/superpowers/specs/2026-07-16-npc-mechanic-design.md
  - docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md
tags: [backend, feature, npc]
---

## Контекст

Task 6 из NPC-плана. После `ALL_DECISIONS_RECEIVED` + `publishScoreUpdated`, если `session.config.autoAdvanceRounds == true` и есть next round → вызывать `adminGameplayService.startNextRound(...)`. Два места вызова — в `PlayerGameplayService.makeDecision` (если раунд закрыл человек) и в `NpcService.playDecisions` (если раунд закрыл NPC).

Возможен circular-dep `PlayerGameplayService ↔ AdminGameplayService` — решается через `@Lazy` инъекцию.

Контракты не меняет.

## Acceptance criteria

- [ ] Unit-тест: `makeDecision` вызывает `startNextRound` при `autoAdvanceRounds=true` и `currentRound < numRounds`.
- [ ] Не вызывает при `autoAdvanceRounds=false`.
- [ ] Не вызывает при `currentRound == numRounds`.
- [ ] Не создаёт `Circular reference` в Spring context.

## План

См. Task 6 в `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.

## Лог

- 2026-07-16: заведено из NPC-plan.
