---
id: T-053
title: Helper-поля 'myRole' и 'phase' в RoundResponse/OfferCreatedResponse — фронт должен знать «мой ход»
status: pending
priority: high
created: 2026-07-13
updated: 2026-07-13
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/RoundResponse.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/util/RoundMapper.kt
related_docs:
  - docs/03-state-machines.md
  - docs/06-websocket-api.md
tags: [feature, api, ux, frontend-blocker]
---

## Контекст

Фронту нужно уметь мгновенно ответить: «моя роль сейчас — proposer или responder?», «фаза раунда — офферы или решения?», «могу ли я сейчас что-то делать?». Сейчас все эти вопросы требуют клиентской логики: сравнить `userId` с proposer/responder в offers, посмотреть `round.roundPhase`, вычислить состояние. Ошибки в такой логике фронта = плохой UX.

Backend знает всё это по определению (state-machine + user context). Логично отдавать hints.

Обнаружено frontend-readiness audit'ом.

## Acceptance criteria

- [ ] `RoundResponse` содержит computed поле `myRole: "PROPOSER" | "RESPONDER" | "BOTH" | "NONE"` — вычисляется относительно вызывающего user'а.
- [ ] `RoundResponse` содержит `myPendingActions: List<{type: "SEND_OFFER" | "MAKE_DECISION", offerId?: UUID}>` — что осталось сделать пользователю в текущей фазе.
- [ ] `phase` уже есть (`roundPhase`), убедиться что она заполняется корректно.
- [ ] Роль вычисляется на основе `SecurityContext` в `RoundMapper.toDto(round, userId)` или через отдельный `RoundEnrichmentService`.
- [ ] Тесты покрывают: пользователь-proposer одного оффера + responder другого → `BOTH`; observer → `NONE`.
- [ ] Обновить `docs/06-websocket-api.md` и regenerate `openapi.json`/`asyncapi.json`.

## План

1. Определить нужен ли новый DTO или расширение существующего (расширение проще).
2. Добавить `computeMyRole(round, userId): Role` в mapper.
3. Добавить `computePendingActions(round, userId): List<PendingAction>`.
4. Passthrough `userId` через параметр в mapper или через `SecurityContextHolder`.
5. Тесты, snapshots.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Blocker для UX — без hints фронт вынужден дублировать бизнес-логику state-machine, что легко расходится с backend'ом.
