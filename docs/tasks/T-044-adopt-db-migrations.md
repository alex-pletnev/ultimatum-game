---
id: T-044
title: Ввести систему миграций БД (Flyway или Liquibase) вместо Hibernate auto-DDL
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
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

- [ ] Выбран инструмент (Flyway или Liquibase — Flyway проще под текущий стек Spring Boot 3.4/Postgres).
- [ ] Добавлена зависимость + автоконфигурация Spring Boot.
- [ ] Создана baseline-миграция от текущей схемы (`V1__baseline.sql`), плюс index.sql перенесён в миграцию.
- [ ] `spring.jpa.hibernate.ddl-auto` → `validate` (проверяет что схема из migrations совпадает с entity'ями).
- [ ] Обновлён `docker-compose.yml` / dev-instructions в CLAUDE.md.
- [ ] Тесты `./gradlew check` проходят.
- [ ] Обновлены `docs/07-persistence.md` (или аналог), `docs/11-known-gaps.md`.

## План

1. Обсудить Flyway vs Liquibase — под текущий стек Flyway проще.
2. Добавить зависимость, включить.
3. Дамп текущей schema через `pg_dump --schema-only` → адаптировать под V1.
4. Переключить `ddl-auto` на `validate`.
5. Прогнать `./gradlew check` в чистом docker'е — schema должна создаться миграциями.
6. Задокументировать процесс добавления новых миграций.

## Лог

- 2026-07-13: заведено по идее пользователя во время обсуждения T-003. Priority medium — не блокирует текущую работу, но без миграций каждая новая schema-change задача копит риск.
