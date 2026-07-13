---
id: T-028
title: Pre-flight check для non-trivial — assumptions / risks / reversibility
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - CLAUDE.md
  - .claude/skills/task-add.md
related_docs:
  - docs/tasks/T-023-superpowers-integration-in-project.md
tags: [meta, agent-behavior, skills]
---

## Контекст

Из анализа флоу — гэпы **B (Assumption capture), C (Risk scan), D (Reversibility check)** — сейчас никакого explicit'ного шага между brainstorming'ом (или systematic-debugging'ом) и началом кодинга нет. brainstorming ловит assumptions только для creative work'а. Risk и reversibility нигде не проговариваются, кроме дефолта в системном промпте.

Идея: новый мини-skill `/pre-flight` (или расширение существующей секции в `task-add.md`), который для **non-trivial** задачи, **после** brainstorm/debugging и **до** первой правки кода, требует 3-пунктный чек:

1. **Assumptions** — что я принимаю на веру? Что не выяснил и с чем работаю по умолчанию?
2. **Risks** — что может сломаться от моего изменения? Особенно для DB миграций, security, deletion.
3. **Reversibility** — если сломается — как откатим? Ninja-revert, git revert, миграция вспять, восстановление из backup'а?

Если ответы получены — можно кодить. Если хоть по одному пункту неясно — эскалировать пользователю до commit'а.

## Acceptance criteria

- [ ] Новый skill `.claude/skills/pre-flight.md` **или** новая секция в `task-add.md` (что уместнее — решить в реализации).
- [ ] Триггер в CLAUDE.md: после brainstorming/systematic-debugging для non-trivial задачи и до первого Edit/Write в `src/**` — обязательный проход.
- [ ] Правило зеркалено в harness.

## План

1. Обсудить с пользователем: отдельный skill или расширение? (по итогу анализа флоу — 5-min решение).
2. Реализация.
3. Push в оба репо.

## Лог

- 2026-07-13: заведена по итогам анализа флоу (Часть 3, гэпы B/C/D объединены в один).
