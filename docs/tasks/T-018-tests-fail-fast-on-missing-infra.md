---
id: T-018
title: Тесты должны падать быстро при отсутствии инфраструктуры (Docker/Postgres)
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - build.gradle.kts
  - src/test/kotlin/edu/itmo/ultimatumgame/UltimatumGameApplicationTests.kt
related_docs:
  - docs/10-configuration.md
tags: [tech-debt, tests, dx]
---

## Контекст

При запуске `./gradlew check` без запущенного Docker Desktop, `UltimatumGameApplicationTests` (`@SpringBootTest`) уходит в бесконечное ожидание PostgreSQL через `spring-boot-docker-compose`. Обнаружено в T-017 — гоняли `check`, тест 20+ минут висел на 100% CPU без единой строчки в stdout. Диагностика проблемы заняла ощутимое время: нет timeout'а, нет fail-fast, silently spinning.

Проблема повторится: у любого нового человека / после ребута системы Docker может быть не запущен, и `./gradlew check` будет молча висеть, а не выдавать понятную ошибку «Postgres unavailable, skipping @SpringBootTest».

## Acceptance criteria

- [ ] Без Docker `./gradlew check` завершается за ≤ 60 секунд с внятным сообщением (например: `SKIPPED: Docker/Postgres unavailable`), а не висит.
- [ ] Unit-тесты (без `@SpringBootTest`) остаются быстрыми и всегда прогоняются.
- [ ] Локально с запущенным Docker всё работает как раньше.

## Возможные подходы

1. **Разделение test-source-set'ов.** `test` — быстрые unit-тесты (services/*, model/*, TestFixtures). `integrationTest` — `@SpringBootTest` c Testcontainers. `check` зависит от `test`, `integrationTest` — опциональный. Требует небольшой правки `build.gradle.kts`.
2. **Testcontainers вместо `spring-boot-docker-compose`.** Testcontainers сам поднимает Postgres в изолированном контейнере, есть механизм fail-fast при недоступности docker daemon (`DockerClientFactory.instance().client()` кидает исключение с понятным сообщением).
3. **`@EnabledIf` / `@DisabledIfSystemProperty` на `UltimatumGameApplicationTests`** — skip если docker/postgres недоступен. Самое дешёвое, но требует хендрайтера health-check'а.
4. **JUnit `@Timeout`** на класс — жёсткий timeout 30 секунд, если контекст не поднялся — fail с понятной ошибкой. Комбинировать с #3.

По ощущениям — #1 + #2 наиболее правильно, но #3 или #4 — быстрый фикс на «сейчас».

## План

Определить при работе над задачей — начать с быстрого фикса (#3/#4), при необходимости мигрировать на #1+#2 отдельным шагом.

## Лог

- 2026-07-13: заведена в ходе T-017. `./gradlew check` завис на 20+ минут при выключенном Docker, потеряли время на диагностику.
