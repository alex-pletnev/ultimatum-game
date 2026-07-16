---
id: T-088
title: paramsMatchStrategy дублируется в NpcService и SessionService
status: pending
priority: low
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/NpcParams.kt
related_docs:
  - docs/tasks/T-083-session-join-npc-and-bulk.md
tags: [tech-debt, npc]
---

## Контекст

Замечено в self-review 67ac41b (T-083).

Функция `paramsMatchStrategy(strategy: NpcStrategy, p: NpcParams): Boolean`
реализована один в один в:

- `NpcService` (T-082, `create` метод) 
- `SessionService` (T-083, `bulkCreateAndJoinNpcs` метод)

Плюс похожий mapping `NpcProfile → NpcProfileResponse` (`toResponse()`) — тоже
в двух местах.

## Acceptance criteria

- [ ] `NpcParams.matchesStrategy(strategy: NpcStrategy): Boolean` как extension
      в `model/NpcParams.kt` (или companion function на NpcStrategy).
- [ ] Оба вызова заменены.
- [ ] `NpcProfile.toResponse()` — единая extension в `dto/responses/`
      (либо через MapStruct-мaппер).

## План

Micro-refactor. Один PR: вынести extension → заменить две дубликата → прогнать check.

## Лог

- 2026-07-16: заведено из self-review 67ac41b.
