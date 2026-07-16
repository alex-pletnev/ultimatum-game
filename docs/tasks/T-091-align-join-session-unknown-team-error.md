---
id: T-091
title: joinSession и addNpcMember — unknown teamId → IdNotFoundException (сейчас error() = 500)
status: pending
priority: low
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt
related_docs:
  - docs/tasks/T-087-bulk-npcs-team-battle-broken.md
tags: [tech-debt, api, bug]
---

## Контекст

Замечено в self-review ab8c524 (T-087). В T-087 `bulkCreateAndJoinNpcs`
на неизвестный `teamId` кидает `IdNotFoundException` → HTTP 404 (правильно
для «клиент передал несуществующий UUID»).

`SessionService.joinSession` (line ~300) и `addNpcMember` (line ~335) для
того же кейса используют `error("В сессии $sessionId не найдена команда $tId")`
→ `IllegalStateException` → HTTP 500 (клиент видит серверную ошибку вместо
валидационной 404).

## Acceptance criteria

- [ ] `joinSession` при неизвестном `teamId` кидает `IdNotFoundException`.
- [ ] `addNpcMember` при неизвестном `teamId` кидает `IdNotFoundException`.
- [ ] Тесты обновлены (сейчас в `SessionServiceTest.kt` — `assertThrows<IllegalStateException>`).
- [ ] `joinSession` при `teamId == null` в TEAM_BATTLE — оставить `error()` (это баг клиента, но 500 приемлем как «не должно случиться при живом фронте»), либо тоже переделать в валидационное исключение — на усмотрение исполнителя.

## План

Micro-refactor. Заменить `error(...)` → `throw IdNotFoundException(...)` в двух
местах, обновить два теста, прогнать `check`.

## Лог

- 2026-07-16: заведено self-review'ом T-087. Мой bulk отдаёт 404, старые
  join-методы — 500 на том же кейсе. Inconsistency, но не блокер.
