---
id: T-044
title: Ввести систему миграций БД (Flyway или Liquibase) вместо Hibernate auto-DDL
status: done
priority: high
created: 2026-07-13
updated: 2026-07-16
related_code:
  - src/main/resources/application.properties
  - src/main/resources/index.sql
  - build.gradle.kts
related_docs:
  - docs/11-known-gaps.md
tags: [tech-debt, db, infra]
---

## Контекст

Сейчас проект работает через `spring.jpa.hibernate.ddl-auto=update` — Hibernate сам меняет схему при старте. Это ок для локальной разработки, но:

- Нет истории миграций → нельзя понять что и когда менялось.
- Нет rollback'а → откат схемы вручную.
- `update`-режим не удаляет колонки/таблицы → накапливается «мёртвая» схема.
- Прод-подобные окружения обычно требуют explicit-миграций (compliance, ревью DDL перед выкаткой).
- T-003 (scoring) — первый кандидат на честное schema-change (если пойдём вариантом B); пока делаем A (on-the-fly), но добавлять миграции — правильный шаг до следующей миграционной задачи.

## Acceptance criteria

- [x] Выбран Flyway (стандарт для Spring Boot 3.4 + Postgres).
- [x] `implementation("org.flywaydb:flyway-core")` + `runtimeOnly("org.flywaydb:flyway-database-postgresql")` в `build.gradle.kts` (обязательно, иначе NoSuchMethodError на Postgres 15+).
- [x] `V1__baseline.sql` — сгенерирован `pg_dump --schema-only` из БД, поднятой `@SpringBootTest` с `ddl-auto=update` (после T-076 + T-001). Sanitize'нутый (SET/pg_catalog/\restrict убраны).
- [x] `IndexSqlInitializer.kt`, `IndexSqlInitializerTest.kt`, `resources/index.sql` — удалены (перенесено в V1). T-068 отменён.
- [x] `spring.jpa.hibernate.ddl-auto=validate` в main. Тесты — H2 + `spring.flyway.enabled=false` + `ddl-auto=create-drop` (без изменений в контракте тестов).
- [x] `spring.flyway.baseline-on-migrate=true` + `baseline-version=0` — на пустой БД V1 применяется, на существующей БД без `flyway_schema_history` не падает (baseline записывается).
- [x] `./gradlew check` — зелёный (286 тестов, H2, Flyway=false).
- [x] `./gradlew bootRun` на чистом Postgres — Flyway создаёт `flyway_schema_history`, применяет V1, приложение стартует (`Successfully applied 1 migration to schema "public", now at version v1`).
- [x] Обновлены `docs/10-configuration.md` (Flyway workflow + как добавлять миграции), `docs/11-known-gaps.md` (устранён пункт `ddl-auto=update`), `CLAUDE.md` (новый триггер на изменение JPA-entity, правило «не редактировать applied migrations»).
- [x] `docs/tasks/T-001-*.md` — запись про поглощение в T-044.

## План

1. Обсудить Flyway vs Liquibase — под текущий стек Flyway проще.
2. Добавить зависимость, включить.
3. Дамп текущей schema через `pg_dump --schema-only` → адаптировать под V1.
4. Переключить `ddl-auto` на `validate`.
5. Прогнать `./gradlew check` в чистом docker'е — schema должна создаться миграциями.
6. Задокументировать процесс добавления новых миграций.

## Лог

- 2026-07-13: заведено по идее пользователя во время обсуждения T-003. Priority medium — не блокирует текущую работу, но без миграций каждая новая schema-change задача копит риск.
- 2026-07-16: priority → high. Стал hard-blocker'ом для T-090 (prod-deploy readiness) — без миграций накатывать на удалённую БД небезопасно.
- 2026-07-16: pre-flight пройден (3 вопроса assumptions/risks/reversibility). Deps добавлены. Baseline снят через `pg_dump` (initial attempt через JPA `schema-generation.scripts` не сработал — Hibernate `ddl-auto=update` перебивает). Тесты на H2 сохранены — `spring.flyway.enabled=false` для test-профиля (Postgres-specific SQL в baseline на H2 не парсится). Одноразовый `docker-compose down -v` для перехода — согласовано с пользователем. Проверено на реальном Postgres 18.4: Flyway создал schema_history, применил V1, приложение стартанулось за 5.7s. `./gradlew check` зелёный. `IndexSqlInitializer` + `index.sql` удалены, T-068 → cancelled. Закрыто.
