---
id: T-055
title: POST /auth/logout — invalidate JWT + audit event
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/AuthController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AuthService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/JwtService.kt
related_docs:
  - docs/05-rest-api.md
  - docs/08-security.md
tags: [feature, auth, api]
---

## Контекст

JWT stateless — клиент теоретически может «сам разлогиниться», удалив токен. Но для чистоты API + audit logging + возможности принудительной invalidate (например, при смене пароля) нужен серверный endpoint.

Обнаружено frontend-readiness audit'ом (изначально classified как MEDIUM blocker, пропущен при заведении T-051..T-054).

## Acceptance criteria

- [ ] `POST /auth/logout` — 204 No Content если Authorization: Bearer валиден, 401 иначе.
- [ ] Добавить jti (token id) в JWT и хранить revoked-list (in-memory `Set<UUID>` для MVP, Redis/DB для prod). Проверка при `JwtService.validate`.
- [ ] Domain event `UserLoggedOut(userId, at)` через `DomainEventLogger`.
- [ ] Тесты: logout + попытка после логаута → 401.
- [ ] Обновить `docs/08-security.md`, regenerate `openapi.json`.

## План

1. Расширить JWT claims — `jti: UUID`.
2. Реализовать `TokenRevocationService` (in-memory + interface для будущего Redis).
3. Подключить проверку в `JwtService.validateToken` / фильтре.
4. Endpoint в `AuthController`.
5. Тесты, snapshots.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Priority medium — фронт может обойтись клиентским logout'ом, но UX / audit требуют серверного.
