---
id: T-013
title: Восстановить gradle-wrapper (gradle/wrapper/*)
status: pending
priority: low
created: 2026-07-12
updated: 2026-07-12
related_code:
  - gradlew
  - gradle/wrapper/
related_docs:
  - CLAUDE.md
tags: [tech-debt, infra]
---

## Контекст

Каталог `gradle/wrapper/` физически отсутствует в рабочем дереве и не хранится в git (`git ls-files gradle/` пустой). При этом `.gitignore` содержит `!gradle/wrapper/gradle-wrapper.jar` — whitelist, т.е. по замыслу jar должен быть в репозитории. Из-за отсутствия `gradle-wrapper.jar` команда `./gradlew` падает с `Could not find or load main class org.gradle.wrapper.GradleWrapperMain`. В `CLAUDE.md` и таск-файлах все инструкции опираются на `./gradlew` (запуск, тесты, `generateApiSnapshots`) — сейчас они не выполнимы без системного `gradle`.

Обнаружено при выполнении T-011: тесты пришлось запускать через `gradle test` вместо `./gradlew test` c ручным `JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`.

## Acceptance criteria

- [ ] `gradle/wrapper/gradle-wrapper.jar` и `gradle/wrapper/gradle-wrapper.properties` присутствуют в рабочем дереве и закоммичены.
- [ ] `./gradlew --version` отрабатывает без ошибок.
- [ ] Версия wrapper'а зафиксирована (соответствует Gradle из `build.gradle.kts` / текущему `gradle 9.6.1` или ниже — на усмотрение).

## План

1. `gradle wrapper --gradle-version <target>` — сгенерировать wrapper.
2. Убедиться, что `.gitignore` пропускает `gradle-wrapper.jar` (уже есть whitelist).
3. Закоммитить `gradle/wrapper/` и обновлённый `gradlew*`.

## Лог

- 2026-07-12: заведена автоматически по итогам T-011 — при попытке запустить `./gradlew test` обнаружено отсутствие wrapper-jar.
