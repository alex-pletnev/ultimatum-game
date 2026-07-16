---
id: T-084
title: index.sql — ALTER TABLE session ADD COLUMN auto_advance_rounds (Hibernate update пропускает NOT NULL без DEFAULT)
status: done
priority: high
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/resources/index.sql
related_docs:
  - docs/tasks/T-076-session-config-auto-advance.md
tags: [backend, bug, db, npc]
---

## Контекст

Фронт (`ultimatum-game-ui/BACKEND-FIX-session-column-missing.md`) отчитался: после T-076
`POST /api/v1/session` падает 500 — `column "auto_advance_rounds" of relation "session" does not exist`.

Причина: Hibernate `ddl-auto=update` не добавляет NOT NULL колонку без DEFAULT
в непустую таблицу. В prod-like окружении (dev с уже созданной схемой) новое
поле не подтянулось. Пока нет полноценных миграций (T-044), пользуемся
существующим `IndexSqlInitializer` (T-001), который применяет `index.sql`
на `ApplicationReadyEvent` идемпотентно.

## Acceptance criteria

- [ ] `index.sql` содержит `ALTER TABLE session ADD COLUMN IF NOT EXISTS auto_advance_rounds BOOLEAN NOT NULL DEFAULT FALSE;`.
- [ ] После рестарта backend'а `POST /api/v1/session` возвращает 201.
- [ ] `./gradlew check` — зелёный.

## План

- Дописать одну строку в `index.sql`.
- Перезапустить backend.
- Фронтовый smoke: `curl -sX POST http://localhost:8080/api/v1/session ...`.

## Лог

- 2026-07-16: заведено по BACKEND-FIX от фронта.
- 2026-07-16: done. Добавил `ALTER TABLE session ADD COLUMN IF NOT EXISTS auto_advance_rounds BOOLEAN NOT NULL DEFAULT FALSE;` в `index.sql`. Идемпотентно применится через `IndexSqlInitializer` на следующем `bootRun`. `./gradlew check` — зелёный. Пользователю: рестартнуть backend, проверить `POST /session` → 201.
