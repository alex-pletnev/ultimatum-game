---
id: T-069
title: Автоматическое копирование openapi/asyncapi.json в frontend-integration/specs после generateApiSnapshots
status: pending
priority: low
created: 2026-07-15
updated: 2026-07-15
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

- [ ] Gradle-task `generateApiSnapshots` (или новая обёртка) после генерации автоматически копирует оба файла в `frontend-integration/specs/`.
- [ ] Ссылка на этот шаг в CLAUDE.md (проактивный триггер) — обновить: `./gradlew generateApiSnapshots` уже делает всё, отдельного `cp` не требуется.
- [ ] Проверка: изменить контроллер → `./gradlew generateApiSnapshots` → убедиться что оба файла (в `src/main/resources/doc/` и в `frontend-integration/specs/`) обновились.

## План

1. В `build.gradle.kts` в task `generateApiSnapshots` добавить `doLast { copy { from(...) into(...) } }` или подключить отдельный `Copy`-task с `dependsOn(generateApiSnapshots)`.
2. Обновить CLAUDE.md «Дополнительный проактивный триггер» — упомянуть что копирование теперь автоматическое.
3. Обновить `frontend-integration/README.md` — убрать хинт «cp вручную».

## Лог

- 2026-07-15: заведено из self-review commit'а 7de7996 (создание frontend-integration/). Priority low — не блокер, но снижает риск stale-спек на фронте.
