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

T-045 добавил `require(offerValue in 0..roundSum)` в `PlayerGameplayService.sendOffer`. При нарушении инварианта бросается `IllegalArgumentException`.

**Пост-проверка (сразу же):** `exceptions/GlobalExceptionsHandler.kt:106-117` уже мапит `IllegalArgumentException` → 400 для REST. Значит для REST-endpoint'ов проблема уже решена.

**Оставшийся scope:**
1. **STOMP endpoint'ы.** `@RestControllerAdvice` не покрывает `@MessageMapping`. Ошибки из STOMP'а могут теряться / отдаваться не тем каналом. Нужно завести `@MessageExceptionHandler` или отдельный interceptor.
2. **`Exception.class` handler (строки 64-76) утекает stack trace в message** — security concern (message + "; st: -> " + stackTraceToString()).
3. **Тестовое покрытие** — нет теста, что клиент реально получает 400 на `amount > roundSum`.

## Acceptance criteria

- [x] Проверить наличие `@RestControllerAdvice` / `GlobalExceptionHandler` в проекте — есть (`exceptions/GlobalExceptionsHandler.kt`).
- [ ] Убрать утечку stack trace в message из общего `@ExceptionHandler(Exception::class)` — вернуть generic message, stack trace писать только в log.
- [ ] Добавить `@MessageExceptionHandler` для STOMP-каналов — публиковать `ApiErrorResponse` в персональный error-topic пользователя.
- [ ] Интеграционный тест — POST offer через REST с `amount > roundSum` возвращает 400 + `ApiErrorResponse`.
- [ ] STOMP-тест — offer через `/app/session/*/offer.create` с `amount > roundSum` возвращает error-frame + payload с 400.
- [ ] Обновить `docs/06-websocket-api.md` — раздел «Error handling»: список кодов + destinations для ошибок.

## План

1. `grep -r 'RestControllerAdvice\|ExceptionHandler' src/main` — найти существующий handler.
2. По результату — либо расширить, либо создать новый.
3. Написать провальный тест RED (400 expected → получаем 500) → импл → GREEN.

## Лог

- 2026-07-13: заведено из self-review T-045 (commit 2df5b37). Категория D. Priority low — нарушения границ валидируются, клиент получает ошибку; но 500 вместо 400 путает автоматизированные клиенты.
- 2026-07-13: post-check — `GlobalExceptionsHandler` уже мапит `IllegalArgumentException` → 400 для REST. Реальный scope сузился: (1) fix stack-trace leak в fallback handler, (2) добавить STOMP `@MessageExceptionHandler`, (3) integration-тесты.
