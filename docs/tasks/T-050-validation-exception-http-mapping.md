---
id: T-050
title: Validation exceptions в service-слое должны отдаваться клиенту как HTTP 400 (не 500)
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/PlayerGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/exceptions/
related_docs:
  - docs/tasks/T-045-verify-game-rules-vs-canonical.md
tags: [tech-debt, api, error-handling]
---

## Контекст

T-045 добавил `require(offerValue in 0..roundSum)` в `PlayerGameplayService.sendOffer`. При нарушении инварианта бросается `IllegalArgumentException`. Spring по умолчанию мапит его в HTTP 500 (Internal Server Error), хотя логически это HTTP 400 (Bad Request) — клиент прислал invalid data. Не проверил, есть ли уже `GlobalExceptionHandler` или `@ExceptionHandler` для этого проекта.

Аналогично: другие `error(...)` / `require(...)` в service-слое могут иметь ту же проблему.

## Acceptance criteria

- [ ] Проверить наличие `@RestControllerAdvice` / `GlobalExceptionHandler` в проекте.
- [ ] Если нет — завести с mapping'ом:
  - `IllegalArgumentException` → 400 с payload `ApiErrorResponse`.
  - `IdNotFoundException` → 404.
  - `DuplicateIdException` → 409.
  - остальное → 500 без утечки stack trace.
- [ ] Интеграционный тест через `MockMvc` (или существующий stack) — POST offer с `amount > roundSum` возвращает 400.
- [ ] Обновить `docs/06-websocket-api.md` (если endpoint через STOMP) или соответствующий REST-doc с описанием ошибок.

## План

1. `grep -r 'RestControllerAdvice\|ExceptionHandler' src/main` — найти существующий handler.
2. По результату — либо расширить, либо создать новый.
3. Написать провальный тест RED (400 expected → получаем 500) → импл → GREEN.

## Лог

- 2026-07-13: заведено из self-review T-045 (commit 2df5b37). Категория D. Priority low — нарушения границ валидируются, клиент получает ошибку; но 500 вместо 400 путает автоматизированные клиенты.
