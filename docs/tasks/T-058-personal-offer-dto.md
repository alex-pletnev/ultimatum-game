---
id: T-058
title: Отдельный DTO для персональной доставки оффера (/topic/…/player/{userId}/offer)
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
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

- [ ] Новый DTO `AssignedOfferResponse { offerId, roundNumber, proposer: UserInfo, amount, offeredAt }` — семантика «этот оффер адресован тебе, решай».
- [ ] `publishOfferToPlayer` использует новый DTO.
- [ ] Backwards compat: broadcast `/topic/offerCreated` продолжает `OfferCreatedResponse`.
- [ ] Обновить `docs/06-websocket-api.md`, regenerate `asyncapi.json`.
- [ ] Тесты — `EventPublisherServiceTest.publishOfferToPlayer` проверяет payload-type.

## План

1. Создать DTO + маппер.
2. Заменить в `EventPublisherService.publishOfferToPlayer`.
3. Тесты + snapshots.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Priority low — фронт может работать со старым DTO, но явный контракт «assigned to me» проще для UI-кода.
