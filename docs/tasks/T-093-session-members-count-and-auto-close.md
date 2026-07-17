---
id: T-093
title: SessionResponse.membersCount + авто-закрытие полных сессий (openToConnect=false)
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-17
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/SessionResponse.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/util/SessionMapper.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt
related_docs:
  - frontend-integration/10-npc.md
tags: [backend, api, feature, ux]
---

## Контекст

Запрос из фронт-репо (`alex-pletnev/ultimatum-game-ui:BACKEND-FIX-session-list-member-count.md`).

Симптом: `GET /session?openToConnect=true&state=CREATED` возвращает полные
сессии, у которых на фронте пусто по `teams` (для FREE_FOR_ALL `numTeams=0`).
Клиент видит «0/2 мест», жмёт «Заявиться», получает 400. Причины:

1. `SessionResponse` не содержит `membersCount` — фронт не может вычислить
   занятость мест единообразно для FFA и TEAM_BATTLE.
2. Сервис не выставляет `openToConnect=false` при достижении `numPlayers` —
   поэтому фильтр `openToConnect=true` возвращает уже полные сессии.

Временный фикс на фронте — N+1 запросы `GET /session/{id}/with-teams-and-members`
per-card. Приемлемо на page-size=8, не масштабируется.

## Acceptance criteria

- [x] `SessionResponse.membersCount: Int = 0` (default для Kotlin data class, MapStruct перезапишет).
- [x] `SessionMapper.toDto` с `@Mapping(target = "membersCount", expression = "java(session.getMembers().size())")`.
- [x] `SessionService.closeIfFull(session, config)` — private helper; вызван в `joinSession`, `addNpcMember`, `bulkCreateAndJoinNpcs` после инкремента `session.members` и до `sessionRepository.save`. `sessionStatus` публикуется существующим `eventPublisherService.publishSessionStatus` — уже включает новое состояние `openToConnect=false`.
- [x] Юнит-тесты (4): join full → close; join меньше numPlayers → остаётся open (регрессия); addNpcMember full → close; bulk 1+2 = 3/3 → close.
- [x] OpenAPI snapshot перегенерирован — `membersCount` в `SessionResponse` schema.
- [x] Docs sync: `frontend-integration/06-data-models.md` (описание `membersCount` + auto-close), `frontend-integration/04-rest-api.md` (примечание про auto-close на join endpoint'ах).

## План

1. Обновить `SessionResponse` DTO — добавить `membersCount: Int = 0`.
2. Обновить `SessionMapper` — `@Mapping(target = "membersCount", expression = "java(session.getMembers().size())")`.
3. Ввести private helper `closeIfFull(session, config)` в `SessionService`.
4. Вызвать после инкремента в: `joinSession`, `addNpcMember`, `bulkCreateAndJoinNpcs`.
5. Юнит-тесты в `SessionServiceTest.kt`.
6. `generateApiSnapshots`.
7. Docs sync.

## Лог

- 2026-07-16: заведено по запросу из фронт-репо (BACKEND-FIX-session-list-member-count.md).
  Не критический баг, но заметный UX.
- 2026-07-17: TDD RED→GREEN — 4 unit-теста в SessionServiceTest.kt (3 сценария auto-close + 1 regression на неполную сессию). Impl: `closeIfFull` (мутирует `openToConnect`), MapStruct expression на `membersCount`. Snapshot перегенерирован, copyApiSnapshotsToFrontendIntegration отработал. Docs синхронизированы. Detekt LargeClass на разросшемся `SessionServiceTest` — `@file:Suppress("LargeClass")` + follow-up T-095 на split файла. `./gradlew check` зелёный. Закрыто.
