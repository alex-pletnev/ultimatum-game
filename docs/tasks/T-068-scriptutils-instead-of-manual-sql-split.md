---
id: T-068
title: IndexSqlInitializer — переписать на ScriptUtils.executeSqlScript вместо split(';')
status: cancelled
priority: low
created: 2026-07-15
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/IndexSqlInitializer.kt
related_docs:
  - docs/tasks/T-001-apply-index-sql-on-startup.md
tags: [tech-debt, refactor, spring]
---

## Контекст

`IndexSqlInitializer.applyIndexSql()` разбивает SQL по `;` руками:
```kotlin
sql.split(";").map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("--") }
```

Работает для текущего `index.sql` (простые DDL), но контракт хрупкий:
- `;` внутри строкового литерала (`WHERE x = 'a;b'`) сломает split.
- Многострочные комментарии `/* ... ; ... */` тоже.
- Комментарии `-- inline` внутри statement не отсеиваются (только те что в начале).

Spring предоставляет `org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(Connection, Resource)` — стандартный helper, который парсит SQL корректно и умеет transaction'ы.

Заведено в self-review T-001 — категория «wheel-check miss».

## Acceptance criteria

- [ ] `IndexSqlInitializer.applyIndexSql` использует `ScriptUtils.executeSqlScript` (или `ResourceDatabasePopulator`).
- [ ] Unit-тест: mockk на `jdbcTemplate.dataSource.connection.metaData` возвращает `PostgreSQL` → verify что ScriptUtils вызван / statements applied. Аналогично для H2 → verify skip.
- [ ] `./gradlew check` зелёный.

## План

1. Разобраться с ResourceDatabasePopulator vs ScriptUtils — что удобнее для однократного apply.
2. Переписать applier.
3. Добавить unit-тест.

## Лог

- 2026-07-15: заведено из self-review T-001 (commit 87e1ce5). Категория B+E (wheel-check miss). Priority low — текущий код работает для текущего SQL, но контракт хрупкий.
- 2026-07-16: **cancelled**. В рамках T-044 (Flyway) удалены `IndexSqlInitializer.kt`, `IndexSqlInitializerTest.kt` и `src/main/resources/index.sql` — миграции берут на себя всё что делал initializer. `ScriptUtils`-рефактор теряет смысл.
