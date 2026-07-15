---
id: T-058
title: Отдельный DTO для персональной доставки оффера (/topic/…/player/{userId}/offer)
status: done
priority: low
created: 2026-07-13
updated: 2026-07-15
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/EventPublisherService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/OfferCreatedResponse.kt
related_docs:
  - docs/06-websocket-api.md
tags: [feature, api, ws]
---

## Контекст

`publishOfferToPlayer` использует `OfferCreatedResponse` — тот же payload, что и broadcast `/topic/offerCreated`. Клиенту непонятно: этот payload — «мне лично адресован» или «broadcast'ное уведомление о чужом оффере»? Приходится сравнивать responderId с своим userId (что вообще-то тавтологично для персонального topic'а).

Обнаружено frontend-readiness audit'ом.

## Acceptance criteria

- [x] Новый DTO `AssignedOfferResponse { offerId, round: RoundPrewResponse, proposer, amount, offeredAt }` — семантика «этот оффер адресован тебе, решай».
- [x] `publishOfferToPlayer` использует новый DTO (маппер `OfferMapper.toAssignedDto`).
- [x] Backwards compat: broadcast `/topic/offerCreated` продолжает `OfferCreatedResponse`.
- [x] Обновить `docs/06-websocket-api.md`, regenerate `asyncapi.json`.
- [x] Тесты — `EventPublisherServiceTest.publishOfferToPlayer` проверяет payload-type (mock на `toAssignedDto`).

## План

1. Создать DTO + маппер.
2. Заменить в `EventPublisherService.publishOfferToPlayer`.
3. Тесты + snapshots.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Priority low — фронт может работать со старым DTO, но явный контракт «assigned to me» проще для UI-кода.
- 2026-07-15: закрыто. Создан DTO `AssignedOfferResponse` (offerId/round/proposer/amount/offeredAt). Добавлен `OfferMapper.toAssignedDto(offer)`. `EventPublisherService.publishOfferToPlayer` использует новый DTO + fix naming параметра `proposerId → responderId` (semantic bug — persist сам факт что `{userId}` в destination это respondent). Broadcast `/topic/offerCreated` не тронут (backwards-compat). `EventPublisherServiceTest` обновлён. AsyncAPI перегенерирован — `AssignedOfferResponse` попал в snapshot. `./gradlew check` зелёный.
