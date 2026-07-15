---
id: T-067
title: TDD-skip 2 задачи подряд в infrastructure/persistence — приоритезировать «а как я это протестирую» до кодинга
status: pending
priority: medium
created: 2026-07-15
updated: 2026-07-15
related_code:
  - .claude/skills/pre-flight.md
  - CLAUDE.md
related_docs:
  - docs/tasks/T-057-session-list-filters.md
  - docs/tasks/T-001-apply-index-sql-on-startup.md
tags: [meta, process, tdd]
---

## Контекст

В self-review T-001 (commit 87e1ce5) и T-057 (commit da2a279) — оба раза обошёл написание провальных тестов до реализации с одной и той же формулировкой: «unit-тест infrastructure/persistence-компонента малоинформативен без реальной БД». В обоих случаях это было формально закрыто как «отклонение от AC» / «follow-up при необходимости».

Проблема:
- Правило CLAUDE.md «RED→GREEN обязателен» для новой фичи → нарушено 2 раза подряд.
- Тесты infrastructure-компонентов **можно** писать с mockk (mock connection.metaData → PostgreSQL, verify statements executed) — я просто не рассмотрел вариант.
- Паттерн: инфраструктурный/config код автоматом считаю «непокрываемым» → отговорка от TDD.

**Следующая правка того же правила = сигнал искать root cause.** Это уже второй раз (self-review skill п.53 говорит про red flag).

## Acceptance criteria

- [ ] Обновить `.claude/skills/pre-flight.md`: при работе над infrastructure/config/persistence-компонентом обязательный вопрос «Как я это протестирую? Что можно смокать?» до Edit/Write в исходники.
- [ ] Или: обновить CLAUDE.md проактивный триггер `superpowers:test-driven-development` — уточнить «включая infrastructure-компоненты», не оставлять wiggle room «unit малоинформативен».
- [ ] Ретро-фикс: для T-001 и T-057 сделать proof-of-concept тесты (mockk-based), даже если посчитаю их «не идеальными» — доказать что они возможны.

## План

1. Прочитать pre-flight.md и определить лучшее место для check'а «как это протестируется».
2. Обновить skill или CLAUDE.md (что подойдёт лучше по scope).
3. Ретро-тесты по T-001/T-057 — отдельным commit'ом.

## Лог

- 2026-07-15: заведено из self-review T-001. Категория E, паттерн повторный (T-057 + T-001) — priority medium сразу.
