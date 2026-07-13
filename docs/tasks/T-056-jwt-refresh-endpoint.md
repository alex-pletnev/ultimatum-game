---
id: T-056
title: POST /auth/refresh — refresh JWT для long-lived sessions
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/AuthController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/JwtService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/JwtAuthenticationResponse.kt
related_docs:
  - docs/08-security.md
tags: [feature, auth, api]
---

## Контекст

Текущий JWT имеет фиксированный TTL. При истечении фронт должен вынудить пользователя re-login (quick-login), что рвёт активную сессию UG (WS отключается, стейт теряется). Стандартный fix — refresh token: короткий access-token + длинный refresh-token, который меняется на новый access-token без ре-логина.

Обнаружено frontend-readiness audit'ом.

## Acceptance criteria

- [ ] Access-token TTL сокращён до ~15 минут; refresh-token TTL ~14 дней.
- [ ] Возвращаются оба в `JwtAuthenticationResponse { accessToken, refreshToken, expiresIn }`.
- [ ] `POST /auth/refresh` (body: `{refreshToken}`) → новый access-token (rotation refresh опционально).
- [ ] Refresh-token хранится с `type: "REFRESH"` в claims, отличается от access-token.
- [ ] Rate-limiting или единичный revoke при подозрительной активности — из scope MVP убрать, отдельным таском если понадобится.
- [ ] Тесты: refresh валидным токеном → 200 + новый access; refresh истёкшим → 401; access вместо refresh → 401.
- [ ] Обновить `docs/08-security.md`, regenerate `openapi.json`.

## План

1. Расширить `JwtService.generateAccessToken` + `generateRefreshToken` с разными TTL / claim'ом type.
2. Endpoint в `AuthController.refresh`.
3. Обновить `JwtAuthenticationResponse` — добавить `refreshToken`, `expiresIn`.
4. Совместимость: старые клиенты используют `accessToken` без refresh — можно сделать nullable, чтобы не ломать.
5. Тесты.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Priority medium — без refresh каждый пользователь ~час играет, потом сессию рвёт логаут; для лабораторных experiments может быть критично.
