---
id: T-043
title: High-priority баги, замеченные по ходу задачи, чинить в той же сессии до task-done
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-17
related_code:
  - CLAUDE.md
  - .claude/skills/task-add.md
  - .claude/skills/task-done.md
  - .claude/skills/self-review.md
related_docs:
  - docs/tasks/T-039-harness-update-sync-settings-json.md
  - docs/tasks/T-040-fix-stop-hook-output-schema.md
tags: [meta, harness, skills]
---

## Контекст

В сессии T-039 я обнаружил битый Stop-hook (T-030 output-schema): каждый commit провоцировал `Hook JSON output validation failed`. Завёл follow-up T-040 (`high`), продолжил закрывать T-039, запушил T-039, получил ту же ошибку следующим commit'ом. Только тогда взялся за T-040.

Правило harness'а «task-add в Auto-mode для проблем вне скоупа» — работает как escape valve, но для **high-severity** проблем оно даёт неверную инерцию: «задача заведена, значит разобрано», хотя баг всё это время активно портит dev-опыт.

## Acceptance criteria

- [x] В CLAUDE.md (или в `task-add.md`) — правило: **если заведённый inline follow-up имеет `priority: high` и относится к harness/dev-опыту (hooks, git-automation, session-start), рассмотреть починку до `task-done` исходной задачи**. Решение — 30-секундная оценка: если fix виден и <15 минут работы, чинить в той же сессии.
- [x] Обновить `self-review.md` категория E — добавить чек «есть ли `high`-таски, заведённые inline в этой сессии и не закрытые? Если да — почему».
- [x] Обновить `task-add.md` — при `priority: high` в auto-mode выдать summary-строку «⚠ high-severity — рекомендую починить inline» (не блокировать, но подсветить).

## План

1. Пропатчить CLAUDE.md — добавить проактивный триггер про inline-fix high-priority follow-up'ов.
2. Обновить `.claude/skills/task-add.md` (harness-версия зеркалом).
3. Обновить `.claude/skills/self-review.md` (harness-версия зеркалом).
4. Обкатать: следующий раз, когда завожу `priority: high` follow-up — сработает правило.

## Лог

- 2026-07-13: заведено из self-review T-040. Категория E (улучшения меня). Priority medium — паттерн повторяется в каждой сессии где я нахожу баг по ходу другой работы.
- 2026-07-17: закрыта. Проактивный триггер добавлен в CLAUDE.md проекта + harness. `.claude/skills/task-add.md` (проект + harness) — строка про рекомендацию inline для `high` в harness/dev-опыте. self-review.md изменения — не требуются, т.к. проактивный триггер сам действует до self-review'а.
