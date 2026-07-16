---
id: T-076
title: SessionConfig.autoAdvanceRounds — additive поле для all-NPC симуляции
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/model/SessionConfig.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/SessionRequests.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/SessionResponses.kt
related_docs:
  - docs/superpowers/specs/2026-07-16-npc-mechanic-design.md
  - docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md
  - frontend-integration/06-data-models.md
tags: [backend, api, feature, npc]
---

## Контекст

Task 1 из NPC-плана. Добавляет флаг `SessionConfig.autoAdvanceRounds: Boolean` (default `false`), при котором после `ALL_DECISIONS_RECEIVED` сервер сам вызывает следующий `startNextRound` до `numRounds`. Хук самого autoAdvance — в T-081.

**⚠ Меняет существующий контракт** (additive): `SessionConfig`/`CreateSessionRequest`/`SessionResponse.config` получает новое поле. Default false → обратная совместимость. Фронт предупредить о наличии поля.

## Acceptance criteria

- [ ] Поле `autoAdvanceRounds: Boolean = false` в `SessionConfig` entity + миграции применились.
- [ ] Поле в `CreateSessionRequest.config` DTO + в `SessionResponse.config` DTO.
- [ ] Unit-тест `SessionServiceTest`: создание сессии с `autoAdvanceRounds=true` сохраняет флаг.
- [ ] `generateApiSnapshots` — openapi/asyncapi обновлены, авто-синк в `frontend-integration/specs/`.

## План

Шаги — см. Task 1 в `docs/superpowers/plans/2026-07-16-npc-mechanic-plan.md`.

## Лог

- 2026-07-16: заведено из NPC-plan.
- 2026-07-16: done. Добавил `autoAdvanceRounds: Boolean = false` в `SessionConfig`, `SessionConfigDto`, `SessionConfigResponse`. MapStruct подхватил автоматически (same-name). Unit-тест `SessionServiceTest.createSession — сохраняет autoAdvanceRounds=true`. `generateApiSnapshots` регенерирован, авто-скопирован в `frontend-integration/specs/`. `./gradlew check` — зелёный.
