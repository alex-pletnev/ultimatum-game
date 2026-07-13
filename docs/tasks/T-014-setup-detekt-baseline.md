---
id: T-014
title: Настроить detekt (плагин + formatting + baseline) под Kotlin-стандарты
status: done
priority: high
created: 2026-07-13
updated: 2026-07-13
related_code:
  - build.gradle.kts
  - config/detekt/
related_docs:
  - CLAUDE.md
tags: [infra, tooling, quality]
---

## Контекст

В проекте нет статического анализа Kotlin-кода — стилевые ошибки, complexity-регрессии, magic-numbers и пр. проходят мимо. Ставим `detekt` c дефолтной конфигурацией («общепринятые стандарты»), интегрируем в `check`, а существующий legacy-shum фиксируем через `baseline.xml`. Работа над самим кодом (выхлопать baseline) — отдельная задача **T-015**.

## Acceptance criteria

- [x] В `build.gradle.kts` подключён `io.gitlab.arturbosch.detekt` (стабильная версия, совместимая с Kotlin 1.9.x).
- [x] Подключён runtime `detekt-formatting` (обёртка над ktlint для стилевых правил).
- [x] Сгенерирован `config/detekt/detekt.yml` (`detektGenerateConfig`); `buildUponDefaultConfig = true`, никаких кастомных твиков.
- [x] Type resolution включён (используются `detektMain`/`detektTest`).
- [x] Сгенерированы `config/detekt/baseline-main.xml` + `baseline-test.xml` — все текущие findings в baseline (`detektBaselineMain` + `detektBaselineTest`).
- [x] Отчёты HTML + SARIF.
- [x] `tasks.check` зависит от detekt-тасков (по аналогии с JaCoCo).
- [x] `./gradlew check` зелёный.
- [x] Изменения в бизнес-коде отсутствуют (задача чисто про инфру).

## План

1. Добавить plugin + dep в `build.gradle.kts`.
2. Добавить блок `detekt { ... }` (baseline path, buildUponDefaultConfig, reports).
3. Заапгрейдить `tasks.check { dependsOn(...) }`.
4. `./gradlew detektGenerateConfig` → `config/detekt/detekt.yml`.
5. `./gradlew detektBaselineMain detektBaselineTest` → `config/detekt/baseline.xml`.
6. `./gradlew check` → зелёный.
7. Docs-sync: обновить CLAUDE.md (упомянуть `./gradlew check` теперь включает detekt).
8. Commit + push, closeовать T-014.

## Лог

- 2026-07-13: заведена по итогам brainstorming'а после T-013. Выбран вариант A (baseline), follow-up на «починить всё» — T-015.
- 2026-07-13: `detekt 1.23.7` + `detekt-formatting 1.23.7` подключены в `build.gradle.kts`. Baseline (`baseline-main.xml` — 579 findings, `baseline-test.xml` — 59 findings, всего 638) сгенерирован через `detektBaselineMain`/`detektBaselineTest`. Отчёты HTML + SARIF. `check` зависит от `detektMain` + `detektTest`; дефолтный `:detekt` (без type resolution) отключён (`enabled = false`), т.к. baseline генерируется только для sourceset-aware вариантов. Обнаружен version-mismatch Kotlin-плагинов (`kapt 2.1.20` vs `jvm 1.9.25`), из-за которого detekt изначально падал — накинут workaround через `resolutionStrategy` в detekt-конфигурациях; корневой fix оформлен как **T-016**. `./gradlew check` — `BUILD SUCCESSFUL` (test + jacoco 0.80 + detektMain + detektTest).
