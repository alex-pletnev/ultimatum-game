---
id: T-046
title: Оптимизировать время `./gradlew check` — сейчас порог 5 мин, ощущение что можно быстрее
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - build.gradle.kts
  - gradle.properties
  - src/test/kotlin/edu/itmo/ultimatumgame/services/
related_docs:
  - CLAUDE.md
tags: [tech-debt, build, ci]
---

## Контекст

Пользователь заметил, что baseline из CLAUDE.md для `./gradlew check` — до 3 мин (после свежих зависимостей) с порогом тревоги 5 мин. Ощущение — избыточно для Kotlin/Spring Boot проекта такого размера. Часто hot-run упирается в десятки секунд + `@SpringBootTest` перезапуски контекста.

Возможные рычаги для ресёрча:
- **Kapt → KSP.** Kapt (Java stub gen) медленный на Kotlin. KSP заметно быстрее для тех же аннотаций.
- **Gradle Configuration Cache.** Gradle уже подсказывает «Consider enabling configuration cache» — реально даёт −20-40% на повторных прогонах.
- **Test parallelization.** JUnit5 умеет `junit.jupiter.execution.parallel.enabled=true` + parallel mode. Для unit-тестов даст ускорение на многоядерке.
- **Spring context caching.** Разные `@SpringBootTest` конфигурации → пересоздание context'а. Проверить: одинаков ли конфиг у integration-тестов? Если разный — унифицировать, чтобы контекст переиспользовался.
- **Split `test` и `integrationTest`.** Отдельный source-set для тяжёлых `@SpringBootTest`; unit-тесты в основном таске быстрые.
- **Gradle daemon / build cache.** `--build-cache` и persistent daemon.
- **Detekt.** Иногда занимает секунды сам по себе. Baseline или incremental.
- **Jacoco coverage verification.** Дорогое; можно вынести из dev-loop'а.
- **JVM args для Gradle.** `-Xmx` / `-XX:+UseParallelGC` — влияют на compile/kapt.

## Acceptance criteria

- [ ] Замерить baseline: время каждой стадии `./gradlew check --profile` (compile, kapt, test, detekt, jacoco).
- [ ] Определить top-3 самых медленных стадии.
- [ ] Применить безопасные оптимизации (configuration cache, build cache, daemon flags).
- [ ] Если применимо — перевести kapt → KSP.
- [ ] Если применимо — включить JUnit5 parallel для unit-тестов.
- [ ] Обновить `docs/CLAUDE.md` секцию «Долгие команды» — новые baseline'ы и пороги.

## План

1. Прогнать `./gradlew check --profile` — прочесть HTML-отчёт.
2. Тэйк-away: где реально время. Далее — по списку рычагов выше, применить по одному, замерить delta.
3. Не ломать поведение — цель просто быстрее.
4. Финал: обновить CLAUDE.md baseline'ы.

## Лог

- 2026-07-13: заведено по замечанию пользователя во время T-003. Priority medium — не блокирует работу, но dev-loop-quality-of-life падает с ростом теста.
