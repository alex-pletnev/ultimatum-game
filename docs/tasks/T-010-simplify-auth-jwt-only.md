---
id: T-010
title: Упростить авторизацию до JWT-only (убрать CSRF и session-membership STOMP checks)
status: pending
priority: high
created: 2026-07-12
updated: 2026-07-12
related_code:
  - src/main/kotlin/edu/itmo/ultimatum_game/configs/SecurityConfiguration.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/configs/WebSocketConfig.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/configs/PlaySessionStompChannelInterceptor.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/controllers/CsrfController.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/dto/responses/CsrfTokenResponse.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/exceptions/SessionStompRejectedException.kt
related_docs:
  - docs/08-security.md
  - docs/05-rest-api.md
  - docs/06-websocket-api.md
  - docs/11-known-gaps.md
tags: [security, refactor, api]
---

## Контекст

Проект — пет; аутентификация в текущем виде избыточна для нужд разработки. Frontend упирается в дополнительные проверки: CSRF-токен (`X-CSRF-TOKEN` + `HttpSessionCsrfTokenRepository`), session-membership на STOMP (`PlaySessionStompChannelInterceptor` требует, чтобы юзер был `admin || member || observer` этой сессии), персональная проверка `userId` в топике `/player/{userId}/offer`. Из-за этого запросы падают с 403/rejected до того, как достигают контроллера, и разработчик фронта не понимает почему.

Пользователь явно принял риск impersonation: другой юзер может подделать запрос от чужого имени — это ok для пет-проекта. Достаточно **различать** пользователей (кто-то залогинен через JWT), но не защищаться от их взаимной подмены.

Стандарт, который остаётся после чистки — **RFC 6750 (OAuth 2.0 Bearer Token Usage)**: клиент шлёт `Authorization: Bearer <JWT>`, сервер валидирует токен и достаёт `sub` → identity. Это индустриальный минимум для API без anti-impersonation. Дополнительно ничего реализовывать не нужно — оно уже есть, надо только вырезать лишние слои сверху.

## Acceptance criteria

- [ ] CSRF полностью отключён на HTTP (`http.csrf { it.disable() }`).
- [ ] Endpoint `GET /csrf` удалён; `CsrfController.kt` и `CsrfTokenResponse.kt` удалены.
- [ ] `PlaySessionStompChannelInterceptor` удалён; убран из `WebSocketConfig.configureClientInboundChannel`.
- [ ] `SessionStompRejectedException` удалён (если больше не используется).
- [ ] Персональная проверка `userId == currentUser.id` в топике `/topic/session/*/player/{userId}/offer` убрана.
- [ ] Оставлены и работают: `JwtAuthenticationFilter` (HTTP), `JwtStompChannelInterceptor` (STOMP CONNECT), `@PreAuthorize` на контроллерах, `WebSocketSecurityConfig` с ролевыми матчерами.
- [ ] Frontend может делать любой mutating REST-запрос без `X-CSRF-TOKEN` header'а, имея только `Authorization: Bearer <jwt>`.
- [ ] Frontend может подписаться на `/topic/session/{sessionId}/*` для любой сессии — авторизация только по роли (PLAYER/ADMIN/OBSERVER валидны через JWT).
- [ ] Обновлены: `docs/08-security.md` (упрощён), `docs/05-rest-api.md` (убран `/csrf`, убрано упоминание X-CSRF-TOKEN), `docs/06-websocket-api.md` (убран раздел про PlaySessionStompChannelInterceptor и персональные проверки), `docs/11-known-gaps.md` (явно записан принятый риск impersonation).
- [ ] `./gradlew test` зелёный.
- [ ] `./gradlew generateApiSnapshots` — снапшоты обновлены (удалены пути `/csrf` и связанные каналы `/player/{userId}/offer` не должны требовать userId matching).
- [ ] Стандарт зафиксирован в `docs/08-security.md`: «Bearer token per RFC 6750; anti-impersonation отсутствует by design».

## План

1. **HTTP layer** (`SecurityConfiguration.kt`):
   - `.csrf { it.disable() }` вместо repository+handler.
   - Убрать импорты `HttpSessionCsrfTokenRepository`, `XorCsrfTokenRequestAttributeHandler`.
   - Убрать `.requestMatchers("/csrf/**").permitAll()`.
2. **Удалить endpoint:**
   - `controllers/CsrfController.kt`
   - `dto/responses/CsrfTokenResponse.kt`
3. **STOMP layer** (`WebSocketConfig.kt`):
   - Убрать `PlaySessionStompChannelInterceptor` из зависимостей класса и из `configureClientInboundChannel` (оставить только `JwtStompChannelInterceptor`).
4. **Удалить interceptor:**
   - `configs/PlaySessionStompChannelInterceptor.kt`
   - `exceptions/SessionStompRejectedException.kt` (если больше не референсится — проверить grep).
5. **Роли по destinations:** `WebSocketSecurityConfig` оставить как есть — ролевые матчеры не мешают frontend'у (PLAYER/ADMIN/OBSERVER всё равно есть в JWT).
6. **Тесты:** `./gradlew test` + починить если что-то падает (маловероятно — тесты бизнес-логики, не security).
7. **Снапшоты:** `./gradlew generateApiSnapshots` → в openapi.json пропадёт `/csrf` (12 путей → 11).
8. **Docs sync:**
   - `docs/08-security.md` — переписать: убрать разделы про CSRF, PlaySessionStomp, персональные проверки. Оставить: JWT filter, JwtStompChannelInterceptor, ролевую матрицу.
   - `docs/05-rest-api.md` — убрать `/csrf` из сводной таблицы + раздел; убрать упоминание CSRF из заголовка.
   - `docs/06-websocket-api.md` — убрать раздел «Двойная авторизация STOMP», оставить только JWT на CONNECT + роли.
   - `docs/11-known-gaps.md` — добавить строку «Impersonation принята как допустимый риск (пет-проект, T-010). Другой юзер может подделать запрос от чужого имени, зная свой JWT».

## Что НЕ трогаем

- JWT TTL (365 дней), алгоритм подписи, refresh-токены — вне скоупа.
- CORS правила (`allowedOriginPatterns = "http://localhost:[*]"`, WS `setAllowedOrigins("*")`) — оставляем как есть.
- Actuator (`management.endpoints.web.exposure.include=*`) — вне скоупа.
- `@PreAuthorize` на контроллерах — оставляем: разделение ADMIN vs PLAYER endpoints это фича, не anti-impersonation.

## Лог

- 2026-07-12: заведена по прямому запросу пользователя после обсуждения. Frontend блокируется CSRF и session-membership проверками; impersonation принят как допустимый риск.
