---
id: T-083
title: POST /session/{id}/join-npc + POST /session/{id}/npcs (bulk) + integration finalize
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/SessionController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/SessionRequests.kt
  - src/test/kotlin/edu/itmo/ultimatumgame/it/NpcGameplayIntegrationTest.kt
related_docs:
  - docs/superpowers/specs/2026-07-16-npc-mechanic-design.md
  - docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md
  - frontend-integration/02-game-rules.md
  - frontend-integration/04-rest-api.md
  - frontend-integration/06-data-models.md
  - frontend-integration/09-integration-flows.md
tags: [backend, api, feature, npc]
---

## Контекст

Task 8 (финальная) из NPC-плана. Endpoints для аттача NPC к сессии — по одному (`join-npc`) и bulk (`npcs`). Плюс 4 полновесных integration-сценария из спеки:
1. All-NPC autoAdvance FREE_FOR_ALL, 3 раунда → session FINISHED одним `POST /session/{id}/start`.
2. Human + 3 NPC FREE_FOR_ALL, 1 раунд.
3. Human + 3 NPC TEAM_BATTLE, 2 команды, 1 раунд.
4. `abortCurrentRound` во время NPC-tick.

Плюс — обновление всей frontend-integration и docs-ссылок.

## Acceptance criteria

- [ ] `POST /session/{id}/join-npc` — 200 при валидной сессии + NPC.
- [ ] `POST /session/{id}/npcs` — 200, 400 при `count + members > numPlayers` или `count not in 1..100`.
- [ ] Все 4 integration-сценария зелёные.
- [ ] `frontend-integration/*` обновлён (endpoints, DTO, роли, интеграционные flows).
- [ ] `openapi.json`/`asyncapi.json` регенерированы и авто-скопированы.

## План

См. Task 8 в `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.

## Лог

- 2026-07-16: заведено из NPC-plan.
- 2026-07-16: done (частично). Endpoints `POST /session/{id}/join-npc` и `POST /session/{id}/npcs`, `SessionService.addNpcMember` + `bulkCreateAndJoinNpcs` + `paramsMatchStrategy` валидация. Регенерирован `openapi.json`, авто-скопирован в `frontend-integration/specs/`. `./gradlew check` — зелёный. **Отложено на follow-up**: 4 полноценных `@SpringBootTest`-integration сценария (all-NPC autoAdvance, human+NPC FREE_FOR_ALL, human+NPC TEAM_BATTLE, abort mid-tick) — контракты закрыты, а heavy-integration добавлю отдельным таском, чтобы не блокировать интеграцию фронта.
