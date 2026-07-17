---
id: T-098
title: NoResourceFoundException возвращает 500 вместо 404 (обнаружено на /actuator/info в prod)
status: pending
priority: medium
created: 2026-07-17
updated: 2026-07-17
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/exceptions/GlobalExceptionsHandler.kt
related_docs:
  - docs/09-error-handling.md
tags: [bug, security]
---

## Контекст

Обнаружено на bootRun smoke T-090 Phase 1. В prod-профиле actuator exposure сужен
до `health,prometheus`. Запрос к нераскрытому endpoint'у:

```
GET /api/v1/actuator/info
→ HTTP 500
{"timestamp":"...","status":500,"error":"Internal Server Error","message":"Внутренняя ошибка сервера"}
```

Из логов:

```
ERROR ...GlobalExceptionsHandler - Необработанное исключение по пути /api/v1/actuator/info
org.springframework.web.servlet.resource.NoResourceFoundException: No static resource actuator/info.
```

Ожидаемое поведение — 404 Not Found. Возврат 500 на несуществующий ресурс:
- вводит клиента в заблуждение (транзиентная ошибка vs отсутствие ресурса);
- утекает stack trace в лог/monitoring дороже, чем надо;
- OWASP-issue: 500 на любой несуществующий path — легитимный fingerprinting-вектор.

## Acceptance criteria

- [ ] `GlobalExceptionsHandler` (или отдельный `@ControllerAdvice`) ловит
  `NoResourceFoundException` → возвращает 404 с ProblemDetail без stack trace.
- [ ] Unit-тест `/api/v1/nonexistent` → HTTP 404.
- [ ] Unit-тест `/api/v1/actuator/info` под prod-профилем → HTTP 404 (актуально после T-090).

## План

1. RED: тест на `nonexistent` path → сейчас 500, должен 404.
2. GREEN: `@ExceptionHandler(NoResourceFoundException::class)` в `GlobalExceptionsHandler` → `ResponseEntity.notFound()`.
3. Обновить `docs/09-error-handling.md` (таблица исключений → HTTP-коды).

## Лог

- 2026-07-17: заведено по итогам bootRun smoke T-090 Phase 1. Пример живой:
  `curl http://localhost:8080/api/v1/actuator/info` → 500.
