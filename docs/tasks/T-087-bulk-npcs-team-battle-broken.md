---
id: T-087
title: bulkCreateAndJoinNpcs не распределяет NPC по командам в TEAM_BATTLE
status: done
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

- [x] `BulkNpcsRequest` — `teamId: UUID? = null`. TEAM_BATTLE + `teamId != null` → все N в указанную команду; TEAM_BATTLE + `null` → round-robin по least-full; FREE_FOR_ALL + `teamId != null` → 400.
- [x] Юнит-тесты — 6 сценариев в `SessionServiceTest.kt` (FREE_FOR_ALL, round-robin пустых, least-full приоритет, targeted teamId, unknown teamId → 404, FREE_FOR_ALL + teamId → 400).
- [ ] `paramsMatchStrategy` не дублируется — вынесено в T-088 (micro-refactor вне скоупа этой bug-fix задачи).

## План

Задизайнить API bulk-запроса для TEAM_BATTLE (нужен brainstorming — round-robin
vs явные teamId'ы). После — обновить `BulkNpcsRequest` и `bulkCreateAndJoinNpcs`
с покрытием юнит-тестом.

## Лог

- 2026-07-16: заведено из self-review 67ac41b.
- 2026-07-16: старт. Дизайн: `BulkNpcsRequest` расширяется `teamId: UUID? = null`. TEAM_BATTLE + `teamId != null` — все в одну команду; TEAM_BATTLE + `null` — round-robin по least-full. FREE_FOR_ALL + `teamId != null` — 400. AC3 (paramsMatchStrategy дедуп) остаётся в T-088 — не тяну в этот скоуп.
- 2026-07-16: TDD RED→GREEN — 6 юнит-тестов (SessionServiceTest.kt). Impl: `planTeamAssignments` считает раскладку до создания сущностей (round-robin учитывает уже назначенные в этом же bulk). Detekt findings (name-shadowing внутреннего `it`) — исправлен через destructuring. `./gradlew check` зелёный. OpenAPI snapshots перегенерены — `teamId` виден в схеме BulkNpcsRequest. `frontend-integration/10-npc.md` обновлён: убран warning про known-limit, добавлена таблица поведения по `sessionType × teamId`, обновлён список ошибок. Закрыто.
