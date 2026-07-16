---
id: T-069
title: Автоматическое копирование openapi/asyncapi.json в frontend-integration/specs после generateApiSnapshots
status: done
priority: low
created: 2026-07-15
updated: 2026-07-16
related_code:
  - build.gradle.kts
  - frontend-integration/specs/
related_docs:
  - frontend-integration/README.md
tags: [tech-debt, tooling, docs]
---

## Контекст

В commit 7de7996 создан каталог `frontend-integration/` для фронта. Внутри — `specs/openapi.json` и `specs/asyncapi.json`, статически скопированные из `src/main/resources/doc/`.

Проблема: любое изменение API-контракта требует **двух** шагов:
1. `./gradlew generateApiSnapshots` — обновляет `src/main/resources/doc/*.json` (уже есть проактивный триггер в CLAUDE.md).
2. `cp src/main/resources/doc/*.json frontend-integration/specs/` — забыть легко.

Забыл шаг 2 → фронт работает по устаревшей спеке → runtime-баги.

## Acceptance criteria

- [x] Gradle-task `generateApiSnapshots` (или новая обёртка) после генерации автоматически копирует оба файла в `frontend-integration/specs/`.
- [x] Ссылка на этот шаг в CLAUDE.md (проактивный триггер) — обновить: `./gradlew generateApiSnapshots` уже делает всё, отдельного `cp` не требуется.
- [x] Проверка: изменить контроллер → `./gradlew generateApiSnapshots` → убедиться что оба файла (в `src/main/resources/doc/` и в `frontend-integration/specs/`) обновились.

## План

1. В `build.gradle.kts` в task `generateApiSnapshots` добавить `doLast { copy { from(...) into(...) } }` или подключить отдельный `Copy`-task с `dependsOn(generateApiSnapshots)`.
2. Обновить CLAUDE.md «Дополнительный проактивный триггер» — упомянуть что копирование теперь автоматическое.
3. Обновить `frontend-integration/README.md` — убрать хинт «cp вручную».

## Лог

- 2026-07-15: заведено из self-review commit'а 7de7996 (создание frontend-integration/). Priority low — не блокер, но снижает риск stale-спек на фронте.
- 2026-07-16: реализовано. Первая попытка (`doLast { copy { … } }` внутри `generateApiSnapshots`) сломала configuration-cache — Test-таск сериализовал ссылку на Gradle-скрипт-объект. Разнесено на два таска: `generateApiSnapshots` + отдельный `copyApiSnapshotsToFrontendIntegration` (тип `Copy`, cache-safe) через `finalizedBy`. Прогон: `./gradlew generateApiSnapshots` → BUILD SUCCESSFUL, оба таска выполнились, md5 в обоих каталогах совпал, CC entry stored. Обновлены CLAUDE.md (проактивный триггер про включение обоих путей в commit) и `frontend-integration/README.md` (снята подсказка про ручной `cp`).
