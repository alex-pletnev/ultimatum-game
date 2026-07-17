---
id: T-100
title: Правило — перед smoke-тестом внешнего API прочитать контракт, а не полагаться на память
status: pending
priority: medium
created: 2026-07-17
updated: 2026-07-17
related_code:
  - CLAUDE.md
related_docs:
  - docs/tasks/T-090-prod-deploy-readiness.md
tags: [meta]
---

## Контекст

Во время T-090 docker-smoke я тестировал `POST /auth/register` и `POST /auth/login`
и получал 500-ки, тратил время на диагностику (проверял jar-classes, gradle-log,
component-scan) — думал, что controller не мапится в prod-профиле. По факту
`/auth/register` и `/auth/login` **никогда не существовали** в этом проекте —
реальные endpoint'ы: `/auth/quick-register`, `/auth/quick-login`. Достаточно
было открыть `AuthController.kt` (или `docs/05-rest-api.md`) перед первым curl'ом.

Причина: доверился памяти о «стандартных» Spring-auth endpoint'ах вместо
чтения контракта. Классический fault-mode «trust memory over reality» — те же
костыли, что в T-094 (Read-tool перед Write/Edit) но применительно к API-контракту
внешнего сервиса.

## Acceptance criteria

- [ ] В CLAUDE.md проактивные триггеры добавить:
  «Smoke-тест реального API (curl / Postman / wscat) → до первого запроса
  прочитать контроллер (`Grep '@\*Mapping' на нужный путь) или `docs/05-rest-api.md`.
  Не полагаться на память или дефолты фреймворка».
- [ ] Синхронизировать в `setup-agent-harness` SPECIFIC_RULES (для проектов с
  REST/WS API).

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
