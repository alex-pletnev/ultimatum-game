---
id: T-061
title: TokenRevocationService — TTL-cleanup отозванных jti (unbounded memory concern)
status: pending
priority: low
created: 2026-07-14
updated: 2026-07-14
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/TokenRevocationService.kt
related_docs:
  - docs/08-security.md
tags: [tech-debt, security]
---

## Контекст

`TokenRevocationService` (введён в T-055) хранит все отозванные `jti` в `ConcurrentHashMap.newKeySet<UUID>()` без верхней границы. При долгоживущем инстансе каждый `POST /auth/logout` добавляет UUID (~50 байт с overhead). 100k logout'ов ≈ 5 МБ. Для лабы приемлемо, но правильнее сбрасывать `jti` из set после того как соответствующий токен уже сам истёк по `exp` — держать в памяти revoked-запись бессмысленно.

## Acceptance criteria

- [ ] `TokenRevocationService.revoke(jti, expiresAt: Instant)` — хранить `Map<UUID, Instant>` с exp.
- [ ] Периодический sweeper (например, `@Scheduled(fixedDelay = 5.minutes)`) удаляет истёкшие записи.
- [ ] Тест: revoke → быстрый sweep-пропуск (не удаляет) → sweep после `expiresAt` → запись удалена.

## План

1. Расширить сигнатуру `revoke` — принимать `expiresAt`.
2. Обновить `AuthService.logout` — доставать `exp` из токена через `JwtService.extractExpiration`.
3. Добавить `@Scheduled` sweeper (+ `@EnableScheduling` в конфиге, если ещё нет).
4. Тест.

## Лог

- 2026-07-14: заведено из self-review T-055 (commit cf73ed3), категория D. Priority low — не блокер, деградация памяти медленная и на лабе не проявится.
