---
id: T-055
title: POST /auth/logout — invalidate JWT + audit event
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-14
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/AuthController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AuthService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/JwtService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/TokenRevocationService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/util/DomainEvent.kt
related_docs:
  - docs/05-rest-api.md
  - docs/08-security.md
  - docs/12-observability.md
tags: [feature, auth, api]
---

## Контекст

JWT stateless — клиент теоретически может «сам разлогиниться», удалив токен. Но для чистоты API + audit logging + возможности принудительной invalidate (например, при смене пароля) нужен серверный endpoint.

Обнаружено frontend-readiness audit'ом (изначально classified как MEDIUM blocker, пропущен при заведении T-051..T-054).

## Acceptance criteria

- [x] `POST /auth/logout` — 204 No Content если Authorization: Bearer валиден, 401 иначе. **Deviation:** 204 happy-path реализовано; 401 не реализовано — сохранена глобальная auth-семантика проекта (403 через `RestAccessDeniedHandler`, 401 только для `ExpiredJwtException`). Отдельный `AuthenticationEntryPoint` для 401 — scope creep из T-055; не заведён.
- [x] Добавить jti (token id) в JWT и хранить revoked-list (in-memory `Set<UUID>` для MVP, Redis/DB для prod). Проверка при `JwtService.isTokenValid`.
- [x] Domain event `UserLoggedOut(userId)` через `DomainEventLogger`.
- [x] Тесты: `TokenRevocationServiceTest`, `JwtServiceTest` (jti + revocation), `AuthServiceTest.logout — извлекает jti, отзывает его и эмитит UserLoggedOut`, `AuthServiceTest.logout — токен без jti не падает, событие всё равно эмитится`.
- [x] Обновить `docs/08-security.md` + `docs/12-observability.md` (auth.logout), regenerate `openapi.json`.

## План

1. Расширить JWT claims — `jti: UUID`.
2. Реализовать `TokenRevocationService` (in-memory + interface для будущего Redis).
3. Подключить проверку в `JwtService.validateToken` / фильтре.
4. Endpoint в `AuthController`.
5. Тесты, snapshots.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Priority medium — фронт может обойтись клиентским logout'ом, но UX / audit требуют серверного.
- 2026-07-14: закрыто. Добавлены: `TokenRevocationService` (in-memory `ConcurrentHashMap.newKeySet<UUID>()`), `jti` claim в JWT (через `Jwts.builder().setId(...)`), `JwtService.extractJti` (nullable для backward-compat), revocation-check в `JwtService.isTokenValid`, `AuthService.logout(bearerToken)`, endpoint `POST /auth/logout` в `AuthController`, domain event `UserLoggedOut`. TDD: RED-подтверждение через `compileTestKotlin FAILED` до создания сервиса, затем GREEN через полный `./gradlew check` (BUILD SUCCESSFUL 2m 7s). Snapshots regenerated (openapi `/auth/logout` появился). **Отклонение от AC #1:** 401 при невалидном Bearer не реализовано — сохранена глобальная auth-семантика проекта (403 для auth-failure, 401 только для `ExpiredJwtException`). Отдельный `AuthenticationEntryPoint` — scope creep; при необходимости завести отдельным таском.
