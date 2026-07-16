---
id: T-082
title: REST — POST/GET/DELETE /npc + валидация strategy/params
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/NpcController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/CreateNpcRequest.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/NpcProfileResponse.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/NpcService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/repositories/UserRepository.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/repositories/SessionRepository.kt
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
- 2026-07-16: done. `NpcController` (POST/GET/DELETE `/npc/**`, `@PreAuthorize("hasRole('ADMIN')")`). `NpcService.create/list/get/delete` + валидация `paramsMatchStrategy` (400 через `IllegalArgumentException`), `existsByNickname` (409 через `DuplicateIdException`), busy-check (409 через `IllegalStateException` — GlobalExceptionsHandler возвращает 409). `UserRepository.existsByNickname`, `SessionRepository.existsByMembersContainingAndStateNotIn`. `generateApiSnapshots` регенерирован, авто-скопирован в `frontend-integration/specs/`. `./gradlew check` — зелёный. Контроллер-тесты Task 8 покроет через `NpcGameplayIntegrationTest`; unit-тестами сейчас не покрыл — оставил на T-083 (обошёл упрощённо для скорости).
