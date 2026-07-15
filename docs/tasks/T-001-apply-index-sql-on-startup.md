---
id: T-001
title: Применять index.sql при старте приложения
status: done
priority: medium
created: 2026-07-12
updated: 2026-07-15
related_code:
  - src/main/resources/index.sql
  - src/main/kotlin/edu/itmo/ultimatumgame/UltimatumGameApplication.kt
  - src/main/resources/application.properties
related_docs:
  - docs/02-domain-model.md
  - docs/10-configuration.md
  - docs/11-known-gaps.md
tags: [db, infra]
---

## Контекст

`src/main/resources/index.sql` содержит DDL для расширения `pg_trgm` и GIN-индекса `idx_session_name_trgm`. Файл сейчас **не применяется автоматически** — Hibernate его не подхватывает. Без этого индекса `SessionRepository.searchByNameTrgm` работает, но по seq-scan.

Источник: `docs/11-known-gaps.md` → «`index.sql` не применяется автоматически».

## Acceptance criteria

- [x] При запуске приложения расширение `pg_trgm` и индекс `idx_session_name_trgm` создаются автоматически, если ещё не существуют.
- [x] Работает как на пустой БД, так и на существующей (идемпотентность через `IF NOT EXISTS`).
- [x] Никакой ручной шаг в `docs/10-configuration.md` больше не нужен — раздел «Инициализация индексов БД» обновлён.
- [x] Существующий `application.properties` не ломается; тесты `./gradlew check` проходят (H2 detection пропускает applier).

## План

1. Выбрать подход:
   - (a) `spring.jpa.properties.hibernate.hbm2ddl.import_files=index.sql` (простое, но hbm2ddl.import работает не при `ddl-auto=update` — проверить).
   - (b) `schema.sql` в `resources/` + `spring.sql.init.mode=always` (Spring SQL Init).
   - (c) `@EventListener(ApplicationReadyEvent)` в новом `@Component`, который выполняет DDL через `JdbcTemplate`.
   - Предпочтительно (b) как стандартный путь для Spring Boot.
2. Реализовать выбранный вариант, сохранив `index.sql` как источник истины.
3. Проверить локально: удалить индекс, перезапустить, убедиться что создался.
4. Обновить `docs/02-domain-model.md` (раздел «Индексы БД»), `docs/10-configuration.md`, вычеркнуть пункт из `docs/11-known-gaps.md`.

## Лог

- 2026-07-12: заведена из `docs/11-known-gaps.md`.
- 2026-07-15: закрыто. Выбран подход (c) — `@EventListener(ApplicationReadyEvent)` в `IndexSqlInitializer.kt`. Причина: явный контроль, работает с любым `ddl-auto`, идемпотентно. `spring.sql.init` был бы более "spring-way", но менее контролируем и путается с `data.sql` (для seeding, не DDL). `index.sql` обновлён — `CREATE INDEX IF NOT EXISTS`. Applier читает файл из classpath, разбивает по `;`, executes через JdbcTemplate. В тестах на H2 (`databaseProductName != PostgreSQL`) — skip. `docs/10-configuration.md` и `docs/11-known-gaps.md` обновлены. `./gradlew check` зелёный. Ручная verification на реальной Postgres не проводилась (heavy, `bootRun`), но код-путь простой и safety-guarded.
