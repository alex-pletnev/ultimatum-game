---
id: T-093
title: SessionResponse.membersCount + авто-закрытие полных сессий (openToConnect=false)
status: pending
priority: medium
created: 2026-07-16
updated: 2026-07-16
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

- [ ] `SessionResponse` содержит `membersCount: Int` = `session.members.size`.
  Работает единообразно для FREE_FOR_ALL и TEAM_BATTLE.
- [ ] `SessionMapper` (MapStruct) заполняет `membersCount` из `session.members.size`.
- [ ] `SessionService.joinSession` — после инкремента members: если
  `members.size >= config.numPlayers` → `session.openToConnect = false` + publish
  `sessionStatus`.
- [ ] `SessionService.addNpcMember` — тот же авто-close.
- [ ] `SessionService.bulkCreateAndJoinNpcs` — тот же авто-close (когда N NPC
  дозаполнили сессию).
- [ ] Юнит-тесты: 3 сценария (join полной сессии → openToConnect=false после;
  join-npc заполняет last-slot; bulk 3 из 3 → openToConnect=false).
- [ ] OpenAPI snapshot перегенерирован — `membersCount` виден в `SessionResponse`.
- [ ] `frontend-integration/*.md` (03-sessions.md или аналог) — упомянуть новое поле
  и авто-close поведение.

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
