---
id: T-070
title: STOMP CONNECT падает с CloseStatus 1002 (MissingCsrfTokenException) — disable WebSocket CSRF
status: done
priority: high
created: 2026-07-15
updated: 2026-07-15
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/WebSocketSecurityConfig.kt
related_docs:
  - docs/06-websocket-api.md
  - docs/08-security.md
  - frontend-integration/05-websocket-api.md
  - frontend-integration/07-error-handling.md
tags: [bug, security-config, websocket, stomp, high]
---

## Контекст

Обнаружено фронтендной командой (`alex-pletnev/ultimatum-game-ui`, задача T-012). Спека:
https://github.com/alex-pletnev/ultimatum-game-ui/blob/main/BACKEND-FIX-stomp-csrf.md

`@stomp/stompjs`-клиент с корректным JWT в `connectHeaders` получает
`CloseStatus[code=1002]` немедленно после WS-handshake — до того как
`JwtStompChannelInterceptor` увидит фрейм.

Root cause: Spring Security 6 при `@EnableWebSocketSecurity` регистрирует отдельный
`CsrfChannelInterceptor` в `clientInboundChannel`, который блокирует не-same-origin
STOMP-фреймы без CSRF-токена. `.csrf { it.disable() }` из HTTP-фильтр-чейна на STOMP-канал
не распространяется — это отдельная цепочка интерцепторов.

Frontend на dev-хосте `:5173`, backend на `:8080` — same-origin не проходит.
Стандартная практика — `sameOriginDisabled() = true` через
`AbstractSecurityWebSocketMessageBrokerConfigurer`.

## Acceptance criteria

- [x] `AbstractSecurityWebSocketMessageBrokerConfigurer.sameOriginDisabled() = true` в
  `WebSocketSecurityConfig.kt` — регистрация как `@Bean`.
- [x] Bonus: добавить missing matcher `/app/session/*/round.abort` → ADMIN (пропущено при
  реализации T-054 — endpoint добавлен, но matcher забыт → админ получал 403).
- [x] Bonus: добавить missing matcher `/user/queue/errors` → SUBSCRIBE authenticated
  (пропущено при реализации T-050 — WebSocketExceptionAdvice отправляет payload через
  `@SendToUser`, но клиентский SUBSCRIBE падал под `anyMessage().denyAll()`).
- [x] `./gradlew check` зелёный.
- [x] `docs/06-websocket-api.md`, `docs/08-security.md`, `frontend-integration/05` и
  `frontend-integration/07` актуализированы если что-то упоминало CSRF/matcher'ы.
- [ ] Frontend-side smoke: e2e `pnpm test:e2e` проходит после fix'а (проверяется на
  стороне фронта).

## План

1. Прочитать `WebSocketSecurityConfig.kt` (существующий).
2. Добавить `@Bean disableWebSocketCsrf` с override `sameOriginDisabled() = true`.
3. Заодно — matcher для `round.abort` и `/user/queue/errors`.
4. `./gradlew check`.
5. Docs sync.
6. Commit + push. Пингануть фронт.

## Лог

- 2026-07-15: заведено фронтом (репо ultimatum-game-ui, спека BACKEND-FIX-stomp-csrf.md).
  High-priority — блокирует всю игровую механику через WS (start/round.*/offer/decision).
- 2026-07-15: реализация 1 — по спеке фронта `@Bean disableWebSocketCsrf(): AbstractSecurityWebSocketMessageBrokerConfigurer`.
  ❌ SpringBootContext не поднимается: `NoSuchBeanDefinitionException: ChannelSecurityInterceptor`.
  Root cause: legacy `AbstractSecurityWebSocketMessageBrokerConfigurer` конфликтует с
  `@EnableWebSocketSecurity` (Spring Security 6 использует `AuthorizationManager`-based
  chain, legacy configurer ожидает старый `ChannelSecurityInterceptor`). Спека фронта
  устарела для 6.x.
- 2026-07-15: реализация 2 (правильная) — подмена бина `csrfChannelInterceptor` на
  no-op `ChannelInterceptor` через `@Bean(name = ["csrfChannelInterceptor"])`.
  `WebSocketMessageBrokerSecurityConfiguration` резолвит его через `getBeanOrNull`
  и берёт наш вместо default'ного. ✅ `./gradlew check` зелёный (240 тестов).
  Bonus'ы: matcher `/app/session/*/round.abort` (регрессия T-054) и
  `/user/queue/errors` (регрессия T-050 — WS-ошибки фактически не доходили до фронта).
  Оба должны были быть пойманы self-review своих задач, но проскочили — паттерн для
  T-067 (проверка полного контракта на security-config зоне).
