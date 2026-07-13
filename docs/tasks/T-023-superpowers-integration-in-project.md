---
id: T-023
title: Интеграция superpowers-skills в наши skills — обкатка в этом проекте
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/task-add.md
  - .claude/skills/task-done.md
  - .claude/skills/self-review.md
  - CLAUDE.md
related_docs:
  - docs/superpowers/specs/2026-07-13-superpowers-integration-design.md
tags: [meta, agent-behavior, skills, superpowers]
---

## Контекст

`superpowers` — популярный набор процессных skills (brainstorming, writing-plans, systematic-debugging, verification-before-completion, receiving-code-review, TDD, executing-plans и др.). У нас в проекте свой набор из 7 skills (task-add, task-done, task-sync, docs-sync, wheel-check, mid-retro, self-review) — покрывает учёт/git/трекер, но процессная дисциплина (design → plan → verify → review) слабая.

Решили: наши skills = оркестраторы, superpowers = инструменты внутри. Наши учитывают и коммитят, superpowers дают правильный процесс. Обкатка — сначала в этом проекте, перенос в harness — отдельно (T-024).

Полный дизайн — `docs/superpowers/specs/2026-07-13-superpowers-integration-design.md`.

## Acceptance criteria

- [ ] `.claude/skills/task-add.md` — новая секция «Non-trivial задачи: brainstorming первым» перед шагом 1; для тривиальных задач шаг skip'ается.
- [ ] `.claude/skills/task-done.md` — новая секция «Verification gate» с обязательным `superpowers:verification-before-completion` перед flip'ом status.
- [ ] `.claude/skills/self-review.md` — шаг 2 обновлён: перед прогоном 5 категорий invoke `superpowers:receiving-code-review`, использовать возражения как материал.
- [ ] `CLAUDE.md` — таблица «Проактивные триггеры» + 3 строки (systematic-debugging, TDD, executing-plans); новая секция «Superpowers integration» с картой 7 wire-points.
- [ ] Все остальные наши skills (task-sync, docs-sync, wheel-check, mid-retro) — без изменений.
- [ ] Спек-документ + T-023 + T-024 в трекере.

## План

1. Правки 3 skill-файлов (task-add, task-done, self-review).
2. Правки CLAUDE.md — 3 новые строки в таблице триггеров + секция «Superpowers integration».
3. Обновить INDEX, закрыть T-023.
4. Завести T-024 (pending) на перенос в harness.
5. Commit + push.

## Лог

- 2026-07-13: заведена по итогам brainstorming'а. Выбрана комбинация A (наши skills — оркестраторы) + C (полный wire всех 7 точек) + C (обкатка здесь → перенос в harness).
- 2026-07-13: реализация — `task-add.md` секция «Non-trivial задачи: brainstorming первым», `task-done.md` секция «Verification gate», `self-review.md` шаг 2 с `receiving-code-review` как внутренним диалогом. `CLAUDE.md` — 3 строки триггеров (systematic-debugging, TDD, executing-plans) + секция «Superpowers integration» с картой 7 wire-points. T-024 заведена как follow-up для переноса в harness.
