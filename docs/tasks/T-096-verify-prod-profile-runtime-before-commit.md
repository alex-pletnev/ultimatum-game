---
id: T-096
title: Правило — изменения prod-профиля верифицируются runtime'ом, не `./gradlew check`
status: done
priority: medium
created: 2026-07-17
updated: 2026-07-17
related_code:
  - CLAUDE.md
  - .claude/skills/pre-flight.md
related_docs:
  - docs/tasks/T-090-prod-deploy-readiness.md
tags: [meta]
---

## Контекст

В T-090 Phase 1 закрыл config-externalization (application-prod.properties,
`@Value("\${app.cors.origins:...}")`), закоммитил после зелёного `./gradlew check`
— но проверка через `check` использует H2 test-profile, `spring-boot-docker-compose`
плагин не активен, Spring context не поднимается с `SPRING_PROFILES_ACTIVE=prod`.
То есть prod-профиль реально не verified — задача-in-session #3 «Local bootRun
smoke-test with prod profile» всё ещё pending, но commit уже ушёл.

Паттерн повторяющийся: доверяю `./gradlew check` как proxy для «изменения безопасны»,
хотя check покрывает только H2-контекст. Реальные ошибки prod-конфига (например
опечатка в property key, конфликт с docker-compose плагином, невалидный
`@Value`-default) увидит только либо `SPRING_PROFILES_ACTIVE=prod bootRun`, либо
контейнер, либо реальный deploy.

## Acceptance criteria

- [x] В CLAUDE.md проактивные триггеры добавить: «Diff трогает `application-prod.properties`
  или code-путь помеченный prod-only (`@Profile("prod")`, `@ConditionalOnProperty`) → до
  commit'а обязательно поднять runtime либо через `SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun`
  до READY, либо через `docker run` с prod-env. `./gradlew check` не считается покрытием».
- [x] `.claude/skills/pre-flight.md` — если задача трогает prod-профиль, добавить
  явный gate «как я это протестирую в prod-runtime» до кодинга.

## План

1. Обновить CLAUDE.md — секция «Проактивные триггеры»: новая строка про prod-профиль.
2. Обновить pre-flight.md — упомянуть prod-runtime verification как отдельный вопрос.
3. Sync в setup-agent-harness (если правило применимо не только к этому проекту —
  скорее нет, это Spring-специфично; ограничить SPECIFIC_RULES).

## Лог

- 2026-07-17: заведено self-review'ом T-090 Phase 1+2 (commit 4065f8a). Категория E
  (улучшения меня). Первое явное наблюдение — приоритет medium.
- 2026-07-17: закрыта. Проактивный триггер добавлен в CLAUDE.md проекта + harness template. Pre-flight обязательный 4-й пункт добавлен в `.claude/skills/pre-flight.md` (проект + harness). Правило generalized: не Spring-специфично, применимо к любому prod-profile / staging-config.
