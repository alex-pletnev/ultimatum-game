---
id: T-013
title: Восстановить gradle-wrapper (gradle/wrapper/*)
status: done
priority: high
created: 2026-07-12
updated: 2026-07-13
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

- [x] `gradle/wrapper/gradle-wrapper.jar` и `gradle/wrapper/gradle-wrapper.properties` присутствуют в рабочем дереве и закоммичены.
- [x] `./gradlew --version` и `./gradlew test` отрабатывают без ошибок (при выставленном `JAVA_HOME`).
- [x] Версия wrapper'а зафиксирована (соответствует Gradle из `build.gradle.kts` / текущему `gradle 9.6.1` или ниже — на усмотрение).
- [x] В `CLAUDE.md` (секция «Запуск и проверки») задокументировано требование `JAVA_HOME` — чтобы будущие сессии не тратили время на выяснение, почему `./gradlew` падает.

## План

1. `gradle wrapper --gradle-version <target>` — сгенерировать wrapper.
2. Убедиться, что `.gitignore` пропускает `gradle-wrapper.jar` (уже есть whitelist).
3. Закоммитить `gradle/wrapper/` и обновлённый `gradlew*`.

## Лог

- 2026-07-12: заведена автоматически по итогам T-011 — при попытке запустить `./gradlew test` обнаружено отсутствие wrapper-jar.
- 2026-07-13: сгенерирован wrapper через `gradle wrapper --gradle-version 9.6.1` (`gradle/wrapper/gradle-wrapper.jar`, `.properties`). В свежесгенерированном `properties` дефолтные `networkTimeout=10000ms, retries=0` спровоцировали таймаут при скачивании дистрибутива — правил на `networkTimeout=60000, retries=3`. Дополнительно исправлен порядок правил в `.gitignore`: `!gradle/wrapper/gradle-wrapper.jar` был перебит более поздним `*.jar` — whitelist перенесён в конец JVM-блока. После правок `./gradlew test` → `BUILD SUCCESSFUL`. Секция «Запуск и проверки» в `CLAUDE.md` дополнена требованием `JAVA_HOME` (`brew --prefix openjdk@21`). Приоритет поднят до `high` по явному запросу пользователя.
