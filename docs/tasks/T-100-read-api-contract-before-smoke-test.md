---
id: T-100
title: Правило — trust memory over reality (API-контракты + CLI-синтаксис) — читать контракт, не помнить
status: pending
priority: high
created: 2026-07-17
updated: 2026-07-17
related_code:
  - CLAUDE.md
related_docs:
  - docs/tasks/T-090-prod-deploy-readiness.md
tags: [meta]
---

## Контекст

Повторяющийся fault-mode «trust memory over reality» — применительно к внешним
контрактам (не своему коду).

**Эпизод 1** (T-090 docker-smoke, commit f8db6f8): тестировал `POST /auth/register`
и `POST /auth/login` — таких endpoint'ов у нас нет, реальные —
`/auth/quick-register`, `/auth/quick-login`. Не открыл `AuthController.kt` или
`docs/05-rest-api.md` до первого curl'а. Потратил время на ложную диагностику
(проверял jar-classes, gradle-log, component-scan) прежде чем осознал что
endpoint'а просто нет.

**Эпизод 2** (T-090 YC deploy, commit a93c3f6): написал 200-строчный `scripts/deploy-yc.sh`
с `yc` командами по памяти документации, не прогнав ни одну. Синтаксис
`--host zone-id=X,subnet-id=Y`, поведение `--network-id` для serverless
container, точный вид `allow-unauthenticated-invoke` — всё по памяти.

Общая природа: **доверяю памяти о шаблонных API/CLI-паттернах вместо чтения
контракта источника**. Работает быстро, ломается на первом же нестандартном
проекте / инструменте.

## Acceptance criteria

- [ ] В CLAUDE.md проактивные триггеры добавить две строки:
  1. «Smoke-тест **внешнего API проекта** (curl / Postman / wscat) → до первого
     запроса прочитать контроллер (Grep `@\*Mapping` на нужный путь) или
     `docs/05-rest-api.md`».
  2. «Первый вызов **внешнего CLI/SDK** (yc, flyctl, gh, aws, terraform, ...)
     в текущей сессии → до написания скрипта или chain'а команд прочитать
     минимум одну команду через `<cli> <cmd> --help` или официальный docs-page.
     Не полагаться на память о синтаксисе».
- [ ] Синхронизировать оба правила в `setup-agent-harness` — применимо к любому
  проекту с внешним API и/или CLI-based deploy.

## План

1. CLAUDE.md → секция «Проактивные триггеры»: новая строка про smoke-test и
  чтение контракта.
2. Оценить: пойдёт ли в setup-agent-harness как generic-rule (да, применимо к
  любому проекту с внешним API).
3. Обновить `.claude/skills/pre-flight.md` — вопрос «как я это протестирую»
  дополнить «какие endpoint'ы?» с обязательным чтением до кодинга.

## Лог

- 2026-07-17: заведено self-review'ом commit f8db6f8 (T-090 smoke). Категория E.
  Первое явное наблюдение этого fault-mode для API-контрактов → medium.
- 2026-07-17: **повышено до high**. Второе срабатывание за две сессии подряд —
  self-review commit'а a93c3f6 (T-090 YC deploy-script). Написал 200-строчный
  bash-скрипт с `yc` командами по памяти документации, не прогнав ни одну:
  синтаксис `--host zone-id=X,subnet-id=Y`, поведение `--network-id` для
  serverless container, точный вид `allow-unauthenticated-invoke`. Пользователь
  запускает — и я не знаю, где именно упадёт. Расширил scope: правило
  распространяется не только на REST/WS API целевого проекта, но и на **любой
  внешний CLI/API, вызываемый впервые в этой сессии** (yc, flyctl, gh, aws, ...).
  Per self-review skill: повторение E-паттерна во втором ревью подряд ⇒ high.
