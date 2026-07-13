---
id: T-016
title: Согласовать версии Kotlin-плагинов (kapt vs jvm/spring/jpa)
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - build.gradle.kts
tags: [tech-debt, build]
---

## Контекст

В `build.gradle.kts` рассогласованы версии Kotlin-плагинов:

```
kotlin("jvm") version "1.9.25"
kotlin("plugin.spring") version "1.9.25"
kotlin("plugin.jpa") version "1.9.25"
kotlin("kapt") version "2.1.20"   ← не совпадает
```

`kapt` притягивает в classpath Kotlin 2.1.20, а остальное — 1.9.25. Обнаружено в T-014: detekt 1.23.7 (built with Kotlin 2.0.10) упал с ошибкой `«was compiled with Kotlin 2.0.10 but is currently running with 2.1.20»`. Пришлось накидывать workaround `resolutionStrategy.eachDependency { useVersion("2.0.10") }` в detekt-конфигурациях (см. build.gradle.kts, блок про detekt).

Скорее всего опечатка / случайный bump kapt при генерации проекта. Логично привести к единой версии `1.9.25` (либо же поднять все до 2.x — но это уже не мелочь).

## Acceptance criteria

- [ ] Все `kotlin("...")` в `plugins { }` имеют одинаковую версию (по умолчанию — `1.9.25`, если нет причин апгрейдиться).
- [ ] `./gradlew check` зелёный.
- [ ] Если workaround в detekt-блоке из T-014 становится не нужен — удалить его.

## План

1. Заменить `kotlin("kapt") version "2.1.20"` → `kotlin("kapt") version "1.9.25"`.
2. `./gradlew check`.
3. Если проходит без workaround'а — вычистить блок `configurations.matching { it.name.startsWith("detekt") }` из `build.gradle.kts`.

## Лог

- 2026-07-13: заведена по итогам T-014, где обнаружен version-mismatch.
