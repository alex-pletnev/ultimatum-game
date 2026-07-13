---
id: T-052
title: Endpoint для получения истории раундов сессии со всеми оффер'ами и решениями
status: done
priority: high
created: 2026-07-13
updated: 2026-07-13
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/SessionController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/RoundResponse.kt
related_docs:
  - docs/06-websocket-api.md
  - docs/04-services.md
tags: [feature, api, rest, frontend-blocker]
---

## Контекст

Фронт может получить только `current-round` через `GET /session/{id}/current-round` и aggregated stats через `GET /statistics/{sessionId}/csv`. Полной **структурированной** истории раундов (по одному объекту на каждый round с его offers/decisions) — нет.

Фронту нужен:
- Отображение прогресса сессии (round-by-round).
- Возможность посмотреть завершённые раунды даже после начала следующего.

Обнаружено frontend-readiness audit'ом.

## Acceptance criteria

- [ ] `GET /session/{sessionId}/rounds` — список `List<RoundResponse>` (все раунды, отсортированы по `roundNumber`), включая offers + decisions.
- [ ] Или `GET /session/{sessionId}/round/{roundNumber}` — отдельный round по номеру.
- [ ] Роли доступа: ADMIN + PLAYER (участники сессии) + OBSERVER.
- [ ] Fetch join для offers/decisions/proposer/responder — избежать N+1 (по паттерну `DecisionRepository.findAllBySessionIdWithRelations`).
- [ ] Обновить `docs/05-rest-api.md` (или аналог).
- [ ] Regenerate `openapi.json`.

## План

1. Добавить метод в `SessionService.getRoundsBySessionId(UUID): List<Round>` с fetch join.
2. Endpoint в `SessionController` с role check.
3. Использовать `RoundMapper` (уже существует).
4. Тесты — `SessionControllerTest` (если существует) или `SessionServiceTest`.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Blocker — без этого фронт не может отрисовать прогресс сессии и историю.
- 2026-07-13: закрыто. `GET /session/{id}/rounds` возвращает `List<RoundResponse>` отсортированных по `roundNumber`. `SessionService.getRounds` под `@Transactional(readOnly = true)` — избегает LazyInit при обходе `session.rounds`. TDD RED→GREEN. Docs 05-rest-api обновлены, `openapi.json` регенерирован.
