---
id: T-048
title: JUnit5 parallel execution + split integrationTest source-set
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - build.gradle.kts
  - src/test/kotlin/
  - src/test/resources/
related_docs:
  - docs/tasks/T-046-optimize-gradle-check-time.md
tags: [tech-debt, build, tests]
---

## Контекст

T-046 применил safe subset оптимизаций `gradle check` (configuration cache + build cache). Осталась Priority-3 группа — потенциальный выигрыш ~3-5с (parallel tests) + ~2-3с (split integration), но с более высоким риском:

- **JUnit5 parallel:** большинство unit-тестов на mockk, независимы, safe. Единственный `@SpringBootTest` (UltimatumGameApplicationTests) — требует отдельного разбора: parallel context sharing или изоляции.
- **Split test/integrationTest:** отдельный source-set + task, unit'ы прогоняются паралельно integration'у. Требует переноса `@SpringBootTest`-класса в `src/integrationTest/`.

## Acceptance criteria

- [ ] Создан `src/test/resources/junit-platform.properties` с `junit.jupiter.execution.parallel.enabled=true` + `mode.default=concurrent`.
- [ ] `@SpringBootTest`-класс(ы) помечены `@Execution(SAME_THREAD)` **или** вынесены в `src/integrationTest/`.
- [ ] `./gradlew check` fresh уменьшается на ≥3с.
- [ ] Тесты остаются стабильными в 5 подряд прогонов (нет flakiness).

## План

1. Создать `junit-platform.properties`.
2. Прогнать `./gradlew clean check` — увидеть новые тайминги.
3. Если flaky — решить: (a) `@Execution(SAME_THREAD)` на flaky-классах, или (b) split integrationTest.
4. Обновить CLAUDE.md baseline'ы.

## Лог

- 2026-07-13: заведено из T-046 (Priority-3 subset). Priority low — выигрыш заметный только для fresh-runs, cached ~1с уже.
