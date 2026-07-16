---
id: T-085
title: IndexSqlInitializer — per-line comment filter (chunk pre-comment теряет валидный statement)
status: done
priority: high
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/IndexSqlInitializer.kt
related_docs:
  - docs/tasks/T-084-alter-table-add-auto-advance-column.md
tags: [backend, bug, db]
---

## Контекст

Фронт (`ultimatum-game-ui/BACKEND-FIX-index-sql-comment-filter.md`) заметил: после
T-084 `ALTER TABLE` в `index.sql` НЕ применяется. Причина в фильтре:

```kotlin
sql.split(";")
    .map { it.trim() }
    .filter { it.isNotBlank() && !it.startsWith("--") }
```

Split по `;` даёт chunk'и, где ведущие `--` комменты «прилипают» к следующему
statement'у. `startsWith("--")` отсекает **весь chunk**, включая `ALTER TABLE`.

Фикс: фильтровать комментные строки построчно **внутри** chunk'а, потом собрать
statement и проверить `isNotBlank`.

## Acceptance criteria

- [ ] `IndexSqlInitializer` применяет все 4 statement'а из текущего `index.sql`.
- [ ] Юнит-тест: sql с ведущими `--` перед statement'ом → statement всё равно
      выполняется.
- [ ] `./gradlew check` — зелёный.

## План

Правка одной строки в `IndexSqlInitializer` + маленький юнит-тест.

## Лог

- 2026-07-16: заведено по BACKEND-FIX от фронта.
- 2026-07-16: done. Извлёк `splitSqlStatements()` — фильтр `--`-комментов идёт построчно ВНУТРИ chunk'а, а не chunk целиком. Юнит-тесты покрывают три сценария (комменты перед statement, пустой chunk, inline-коммент). `./gradlew check` — зелёный. Пользователю: рестартнуть backend, ALTER TABLE должен применится (лог должен показать «2 statements», POST /session → 201).
