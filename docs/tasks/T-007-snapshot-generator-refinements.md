---
id: T-007
title: Полировка генератора API-снапшотов (custom task, exclude из test, чистка openapi)
status: done
priority: low
created: 2026-07-12
updated: 2026-07-12
related_code:
  - build.gradle.kts
  - src/test/kotlin/edu/itmo/ultimatum_game/SpecSnapshotGeneratorTest.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/configs/OpenApiConfig.kt
related_docs:
  - docs/05-rest-api.md
  - CLAUDE.md
tags: [tech-debt, meta, api]
---

## Контекст

По ходу T-006 остались 3 шероховатости, которые я оставил ради скорости закрытия:

1. **Нет краткой команды.** Custom gradle-таска `generateApiSnapshots` пыталась зарегистрироваться через `tasks.register("...", Test::class.java)`, но Gradle 9 Kotlin DSL иногда не подхватывал регистрацию (task not found). Час диагностики не дал результата — оставил единственный вход `./gradlew test --tests "*.SpecSnapshotGeneratorTest"`. Пользователю неудобно запоминать длинную форму.
2. **Генератор снапшотов бежит на каждом `./gradlew test`.** `SpecSnapshotGeneratorTest` не имеет assertions, всегда «зелёный», но грузит Spring-контекст ~5с. Добавляет замедление на весь `test`-суит без пользы (кроме того, что перезатирает снапшоты — иногда полезно, иногда нет).
3. **`/springwolf/*` эндпоинты попали в `openapi.json`.** Это внутренние пути UI/JSON springwolf (`/springwolf/docs`, `/springwolf/ui-config`, ...), они не часть публичного API. 4 лишних path'а замусоривают спеку.

## Acceptance criteria

- [ ] Есть короткая команда для регенерации: либо custom gradle-таска (`./gradlew generateApiSnapshots`), либо shell-скрипт (`./scripts/regen-specs.sh`). Одна команда — оба файла.
- [ ] Обычный `./gradlew test` не запускает `SpecSnapshotGeneratorTest` (либо через exclude в `withType<Test>` c условием, либо через отдельный source set, либо через `@Tag`+filter).
- [ ] В `openapi.json` нет `/springwolf/*` путей (отфильтровать в `OpenApiCustomizer` — `openApi.paths.remove(...)` для путей с префиксом `/springwolf`).
- [ ] `docs/05-rest-api.md` и `CLAUDE.md` — при появлении короткой команды обновить упоминание.

## План

1. Разобраться с gradle-регистрацией — попробовать `buildSrc/` script plugin или `gradle/tasks.gradle.kts` (обычные скрипт-плагины меньше страдают от KTS caching).
2. Отделить `SpecSnapshotGeneratorTest` от обычного теста: либо `@Tag("snapshot")` + JUnit filter в основном `test` (`excludeTags`), либо отдельный source set `specGen`.
3. В `OpenApiConfig.addDefaultErrorResponses` (или отдельным `OpenApiCustomizer`) — удалить paths начинающиеся с `/springwolf`.
4. Регенерировать снапшоты, убедиться что `/springwolf/*` пропали и все продуктовые endpoint'ы на месте (13).
5. Обновить документацию.

## Лог

- 2026-07-12: заведена автоматически по итогам T-006 (self-retrospective). Все три пункта — компромиссы, принятые ради закрытия основной задачи; растворятся без записи.
- 2026-07-12: сделано. Все три пункта:
  - `@Tag("snapshot")` на `SpecSnapshotGeneratorTest` + `test { useJUnitPlatform { excludeTags("snapshot") } }` — обычный `test` больше не грузит Spring-контекст ради снапшотов (7s вместо 13s+).
  - `generateApiSnapshots` — теперь регистрируется стабильно. Разница с прошлой попыткой: `tasks.named<Test>("test") { ... }` вместо `tasks.withType<Test> { ... }` — видимо withType конфликтовал с последующей регистрацией нового Test. Плюс `useJUnitPlatform { includeTags("snapshot") }` для чёткой изоляции.
  - Новый `OpenApiCustomizer.filterInternalPaths` вырезает `/springwolf/*` из openapi. Итог: 12 продуктовых путей (13 endpoints), 0 springwolf.
