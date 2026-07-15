---
id: T-064
title: AuthService.refresh — malformed/подделанный refresh-токен даёт 500 вместо 401
status: done
priority: high
created: 2026-07-14
updated: 2026-07-15
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AuthService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/JwtService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/exceptions/GlobalExceptionsHandler.kt
related_docs:
  - docs/tasks/T-056-jwt-refresh-endpoint.md
  - docs/08-security.md
tags: [bug, auth, security]
---

## Контекст

В `AuthService.refresh(refreshToken)` (введён в T-056):

```kotlin
if (jwtService.extractType(refreshToken) != JwtService.TYPE_REFRESH) {
    throw InvalidJwtException("Ожидался refresh-токен")
}
val userId = UUID.fromString(jwtService.extractUsername(refreshToken))
```

- `extractType` использует `runCatching` — на любой парсер-исключение вернёт `null`. Malformed токен пройдёт через первую проверку (`null != "REFRESH"` → throw `InvalidJwtException` → 401). **Этот путь работает корректно.**
- Но если у токена валидная структура + корректный `type=REFRESH` claim, но **подписан другим ключом** — первая проверка пройдёт (тело парсится и без валидации подписи через `runCatching`, вернёт `type`), потом `jwtService.extractUsername(refreshToken)` кинет `io.jsonwebtoken.security.SignatureException` — нет `@ExceptionHandler` для неё → 500.

Аналогично `MalformedJwtException` вне ветки `runCatching` (после первой проверки) → 500.

По AC #6 T-056 refresh должен возвращать 401 в этих случаях.

Self-review T-056 (commit 5387ef7) — мой mockk-тест `refresh — невалидный refresh (истёк или подделка)` эмулировал через `isRefreshTokenValid=false`, но не через реальный SignatureException.

## Acceptance criteria

- [ ] Handler'ы для `io.jsonwebtoken.SignatureException` и `io.jsonwebtoken.MalformedJwtException` в `GlobalExceptionsHandler` → 401.
- [ ] Интеграционный тест (или расширение unit-теста): реальный подделанный refresh-токен (подписан другим ключом) → `AuthService.refresh` → `InvalidJwtException` (или прямой 401 через handler).
- [ ] Аналогично для `AuthController.POST /auth/logout` — тот же класс токенов, тот же класс handler'ов.

## План

1. Добавить `@ExceptionHandler(SignatureException::class)` и `@ExceptionHandler(MalformedJwtException::class)` → 401.
2. Тест: сгенерировать refresh-токен другим `signingKey`, вызвать `AuthService.refresh`, ассертить `InvalidJwtException`.
3. Проверить logout-endpoint — cascading behavior.

## Лог

- 2026-07-14: заведено из self-review T-056 (commit 5387ef7), категория B. Priority high — API отдаёт 500 в security-critical пути; клиенту невозможно отличить «свой refresh истёк» от «баг сервера». Прямая security-hygiene.
- 2026-07-15: закрыто. Добавлены `@ExceptionHandler(SignatureException)` и `@ExceptionHandler(MalformedJwtException)` в `GlobalExceptionsHandler` → 401. Добавлен unit-тест в `AuthServiceTest`: реально подделанный (чужим ключом) refresh-токен → `InvalidJwtException` (текущий путь через `runCatching` в `extractType`) ИЛИ `SignatureException` (защита handler'ом). `./gradlew check` зелёный. Замечание: описанный в контексте путь через прямой `extractUsername` в `refresh` не срабатывает — `extractType` с `runCatching` перехватывает `SignatureException` первым и превращает в `InvalidJwtException`. Handler'ы всё равно важны как defence-in-depth для других путей (напр. `JwtAuthenticationFilter.extractUsername` — вне runCatching).
