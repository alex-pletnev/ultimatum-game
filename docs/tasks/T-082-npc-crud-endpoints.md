---
id: T-082
title: REST — POST/GET/DELETE /npc + валидация strategy/params
status: pending
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/NpcController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/NpcRequests.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/NpcResponses.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt
related_docs:
  - docs/superpowers/specs/2026-07-16-npc-mechanic-design.md
  - docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md
  - frontend-integration/04-rest-api.md
  - frontend-integration/06-data-models.md
tags: [backend, api, feature, npc]
---

## Контекст

Task 7 из NPC-плана. CRUD-эндпоинты для управления NPC-личностями. Только новые endpoint'ы и DTO — существующие контракты не меняет.

## Acceptance criteria

- [ ] `POST /npc` — создаёт NPC (ADMIN only), 201.
- [ ] `POST /npc` — 400 при несовместимости strategy/params.
- [ ] `POST /npc` — 409 при дублирующемся nickname.
- [ ] `GET /npc`, `GET /npc/{id}` — 200.
- [ ] `DELETE /npc/{id}` — 204 если не в открытой сессии, 409 иначе.
- [ ] Все не-ADMIN — 403.
- [ ] `openapi.json` содержит все endpoints, авто-копирован в `frontend-integration/specs/`.

## План

См. Task 7 в `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.

## Лог

- 2026-07-16: заведено из NPC-plan.
