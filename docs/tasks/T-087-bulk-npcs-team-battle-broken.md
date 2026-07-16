---
id: T-087
title: bulkCreateAndJoinNpcs не распределяет NPC по командам в TEAM_BATTLE
status: pending
priority: high
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/CreateNpcRequest.kt
related_docs:
  - docs/tasks/T-083-session-join-npc-and-bulk.md
tags: [backend, bug, npc]
---

## Контекст

Замечено в self-review 67ac41b (T-083).

`SessionService.bulkCreateAndJoinNpcs` для `TEAM_BATTLE` кладёт NPC-user'ов
только в `session.members`, но никогда не в `team.members`. `addNpcMember`
для TEAM_BATTLE обязательно требует `teamId` и добавляет в team.members
через `team.members += profile.user` — bulk молча пропускает это. В итоге
bulk-attach в TEAM_BATTLE сессию сломан.

## Acceptance criteria

- [ ] `BulkNpcsRequest` — либо поле `teamAssignments: Map<UUID, Int>` (per-team counts),
      либо `teamId` (все N в одну команду), либо round-robin по существующим командам.
- [ ] Юнит-тест — bulk с TEAM_BATTLE распределяет NPC по командам согласно логике.
- [ ] `paramsMatchStrategy` не дублируется (см. T-088).

## План

Задизайнить API bulk-запроса для TEAM_BATTLE (нужен brainstorming — round-robin
vs явные teamId'ы). После — обновить `BulkNpcsRequest` и `bulkCreateAndJoinNpcs`
с покрытием юнит-тестом.

## Лог

- 2026-07-16: заведено из self-review 67ac41b.
