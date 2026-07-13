---
id: T-034
title: Periodic learning consolidation — сканирование закрытых задач на кросс-паттерны
status: done
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/self-review.md
related_docs:
  - docs/tasks/T-021-agent-self-improvement-skills.md
  - docs/tasks/T-025-notify-before-ac-deviation.md
tags: [meta, agent-behavior, skills]
---

## Контекст

Из анализа флоу — гэп **K**. `self-review` категория E собирает наблюдения по **одной** задаче. Но никто не смотрит на 10 подряд закрытых задач и не ищет паттерны через них. Пример реального случая: T-025 родился из наблюдения, что одна и та же нота вылезла в двух self-review подряд (T-020 → T-002) — я поймал это в разговоре с пользователем, не автоматически. Автоматизация этого — periodic-consolidation.

Хочется периодический (раз в N закрытых задач, раз в неделю или явный `/learn`) skill:

- Прочитать N последних закрытых тасков (например, 10).
- Выделить категорию E-наблюдения из каждого `## Лог`.
- Найти повторяющиеся паттерны (одно и то же слово-о-концепт в 2+ тасках).
- Свести в короткий отчёт с предложением: завести T-XXX (meta) на исправление паттерна, если он не закрыт.

## Acceptance criteria

- [ ] Новый skill `.claude/skills/consolidate.md` **или** секция «Periodic sweep» в `self-review.md` (что уместнее — решить в реализации).
- [ ] Триггер: каждые N=10 закрытых задач ИЛИ явный `/consolidate`.
- [ ] Формат отчёта: 3-5 строк, список повторяющихся паттернов + предложение action item.
- [ ] Зеркалено в harness.

## План

1. Обсудить: отдельный skill vs секция self-review.
2. Реализация с явным вызовом (не hooks).
3. Прогнать на текущих закрытых T-001..T-026, посмотреть на реальный отчёт.
4. Push.

## Лог

- 2026-07-13: заведена по итогам анализа флоу (гэп K).
- 2026-07-13: закрыта. Новый standalone skill `.claude/skills/consolidate.md` (10-й в наборе), а не секция в self-review — жизненный цикл разный (self-review per-task, consolidate periodic). Auto-mode: caждые 10 закрытых с прошлой consolidation → user notify + confirm. Explicit `/consolidate [N]`. State в `.claude/consolidation-state.json` (last_run_date, last_run_after_task, count). Формат отчёта: список паттернов (тема встретилась в 2+ задачах) + предложение action item'ов. Task-add в Auto-mode с приоритетом `medium` (у паттернов приоритет выше single-task). Обновлены SKILL.md (10 skills), playbook (Фаза 6 копирует 10, добавлен consolidate), claude-md.template.md (+1 slash-команда, +1 триггер), harness-update.md (List targets 8 → 9). AC-check: 4/4 буквально.
