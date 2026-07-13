---
id: T-051
title: Broadcast '/topic/offerCreated' должен содержать responder после shuffle
status: done
priority: high
created: 2026-07-13
updated: 2026-07-13
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/EventPublisherService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/CoreGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/PlayerGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/OfferCreatedResponse.kt
related_docs:
  - docs/06-websocket-api.md
tags: [feature, api, ws, frontend-blocker]
---

## Контекст

Фронт при подписке на `/topic/session/{sessionId}/offerCreated` получает `OfferCreatedResponse` **без поля `responder`** — только `proposer`. Shuffle-логика (`FreeForAllStrategy`/`TeamBattleStrategy`) присваивает responder'а на этапе `initWaitDecisionsPhase`, но broadcast случается раньше и с proposer'ом only. В результате наблюдатели/участники не знают "кому адресован оффер" до того как respondent получит персональную нотификацию.

Обнаружено frontend-readiness audit'ом (subagent report, 2026-07-13).

## Acceptance criteria

- [ ] После `shuffleOffers()` (переход `ALL_OFFERS_RECEIVED → OFFERS_SENT`) публикуется дополнительный broadcast — либо повторно `publishOfferCreated` с заполненным `responder`, либо новое событие `/topic/session/{sessionId}/offersShuffled` с payload `List<{offerId, responderId}>`.
- [ ] `OfferCreatedResponse` содержит `responder: UserInfo?` — nullable для до-shuffle оффера.
- [ ] Тест: `PlayerGameplayServiceTest` / integration — проверяет что после последнего оффера broadcast имеет заполненный responder.
- [ ] Обновить `docs/06-websocket-api.md` и `references/settings-hooks/*` при необходимости.
- [ ] Regenerate `asyncapi.json`.

## План

1. Выбрать подход (повторный broadcast vs новое событие). Рекомендация — новое событие `/topic/session/{sessionId}/offersShuffled` с mapping-payload — не ломает существующее.
2. Добавить `publishOffersShuffled(sessionId, offers)` в `EventPublisherService`.
3. Вызвать из `CoreGameplayService.initWaitDecisionsPhase` после `shuffleOffers`.
4. Разрешить subscribe в `WebSocketSecurityConfig`.
5. Тесты + snapshot regen.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Blocker'ом для UX — без этого фронт не может визуализировать pairing до персональной нотификации.
- 2026-07-13: закрыто. Выбран вариант «новое событие» (не ломает существующий /topic/offerCreated). DTO — `OffersShuffledResponse { roundNumber, assignments: List<{offerId, proposerId, responderId}> }`. `EventPublisherService.publishOffersShuffled` + `CoreGameplayService.broadcastShuffleAssignments` — публикуется в `/topic/session/{id}/offersShuffled` после `dispatchOffers`. `WebSocketSecurityConfig` разрешает подписку ADMIN/PLAYER/OBSERVER. TDD: RED (compile error — метода нет) → GREEN. Docs 03/06 + `asyncapi.json` синхронизированы.
