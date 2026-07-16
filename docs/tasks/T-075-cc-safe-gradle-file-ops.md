---
id: T-075
title: CLAUDE.md — configuration-cache-safe паттерн для file-ops в gradle-тасках
status: pending
priority: low
created: 2026-07-16
updated: 2026-07-16
related_code:
  - CLAUDE.md
  - build.gradle.kts
tags: [meta]
---

## Контекст

При реализации T-069 первая версия использовала `doLast { copy { … } }` внутри существующего `generateApiSnapshots` (тип `Test`). Прогон свалился на configuration-cache: «Task ':generateApiSnapshots' of type 'org.gradle.api.tasks.testing.Test': cannot serialize Gradle script object references as these are not supported with the configuration cache». Fix — разнести на два таска (Test + отдельный `Copy` через `finalizedBy`).

Это **известный** CC anti-pattern — но я его не вспомнил заранее, хотя в build.gradle.kts CC уже включён (первый же прогон печатал `configuration cache cannot be reused because file 'build.gradle.kts' has changed`). Стоило погрепать build.gradle.kts на предмет паттернов file-ops до придумывания своего.

## Acceptance criteria

- [ ] В CLAUDE.md (секция «Дополнительный проактивный триггер» или «Что не делать») добавить одну строку: перед добавлением file-ops в gradle-таск проверить, что паттерн CC-safe: для копирования/перемещения/удаления файлов — отдельный `Copy`/`Delete`-task через `dependsOn`/`finalizedBy`, а не inline `copy { }` / `delete { }` в `doLast`/`doFirst` существующего таска.
- [ ] Один пример (что ломает / что работает) — 4-6 строк, не больше.

## План

1. Патч секции в CLAUDE.md с одной строкой + минимальным примером.
2. Проверить что build.gradle.kts повторно проходит `./gradlew --dry-run`.

## Лог

- 2026-07-16: заведено из self-review T-069 (commit 9581dd4). E-категория, meta. Приоритет low — не блокер, но нужный fingerprint для follow-up'ов на build.gradle.kts.
