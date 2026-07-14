---
id: T-056
title: POST /auth/refresh — refresh JWT для long-lived sessions
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-14
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/AuthController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/JwtService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AuthService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/JwtAuthenticationResponse.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/requests/RefreshTokenRequest.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/exceptions/GlobalExceptionsHandler.kt
related_docs:
  - docs/08-security.md
tags: [feature, auth, api]
---

## Контекст

Текущий JWT имеет фиксированный TTL. При истечении фронт должен вынудить пользователя re-login (quick-login), что рвёт активную сессию UG (WS отключается, стейт теряется). Стандартный fix — refresh token: короткий access-token + длинный refresh-token, который меняется на новый access-token без ре-логина.

Обнаружено frontend-readiness audit'ом.

## Acceptance criteria

- [x] Access-token TTL сокращён до ~15 минут; refresh-token TTL ~14 дней.
- [x] Возвращаются оба в `JwtAuthenticationResponse { accessToken, refreshToken, expiresIn }`.
- [x] `POST /auth/refresh` (body: `{refreshToken}`) → новый access-token (rotation refresh отключён в MVP; refreshToken в ответе `null`).
- [x] Refresh-token хранится с `type: "REFRESH"` в claims, отличается от access-token (`ACCESS`).
- [x] Rate-limiting или единичный revoke при подозрительной активности — вне scope MVP.
- [x] Тесты: `JwtServiceTest` (5 новых — type-claims, TTL, isTokenValid rejects refresh), `AuthServiceTest.refresh — валидный refresh-токен возвращает новый accessToken`, `refresh — access-токен вместо refresh бросает InvalidJwtException`, `refresh — невалидный refresh (истёк или подделка) бросает InvalidJwtException`. `InvalidJwtException` → 401 через новый handler в `GlobalExceptionsHandler`.
- [x] Обновить `docs/08-security.md` (полностью перепиcан блок JWT + refresh секция + response DTO + known-gaps), regenerate `openapi.json`.

## План

1. Расширить `JwtService.generateAccessToken` + `generateRefreshToken` с разными TTL / claim'ом type.
2. Endpoint в `AuthController.refresh`.
3. Обновить `JwtAuthenticationResponse` — добавить `refreshToken`, `expiresIn`.
4. Совместимость: старые клиенты используют `accessToken` без refresh — можно сделать nullable, чтобы не ломать.
5. Тесты.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Priority medium — без refresh каждый пользователь ~час играет, потом сессию рвёт логаут; для лабораторных experiments может быть критично.
- 2026-07-14: закрыто. `JwtService` разделён на `generateAccessToken` (15 мин, `type=ACCESS`) + `generateRefreshToken` (14 дней, `type=REFRESH`), добавлены `extractType`, `extractTtlSeconds`, `accessTokenTtlSeconds`, `isRefreshTokenValid`. `isTokenValid` теперь требует `type=ACCESS` — refresh-токены в Bearer отклоняются автоматически (в filter'е и STOMP-interceptor'е). `AuthService.refresh(refreshToken)` — валидирует type + isRefreshTokenValid, возвращает `JwtAuthenticationResponse(accessToken, refreshToken=null, expiresIn)`. `AuthController.POST /auth/refresh` — publicly accessible endpoint. `InvalidJwtException` → 401 через новый handler в `GlobalExceptionsHandler`. `JwtAuthenticationResponse` renamed: `token` → `accessToken`; добавлены `refreshToken: String?` и `expiresIn: Long`. TDD: RED через `compileTestKotlin FAILED` (Unresolved references), затем GREEN через `./gradlew check` BUILD SUCCESSFUL 51s. Snapshots regenerated. **Инцидент:** первый check застрял на 6+ мин в `FreeForAllTest`/`FreeForAllStrategy.shuffleOffers` — probabilistic derangement bug (бесконечный `do-while`). Убил, `./gradlew --stop`, retry прошёл. Отдельный таск T-063 (high, bug/gameplay) на фикс.
