---
id: T-032
title: External review (code-reviewer subagent) для high-stakes изменений
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/task-done.md
  - CLAUDE.md
related_docs:
  - docs/tasks/T-023-superpowers-integration-in-project.md
tags: [meta, agent-behavior, skills, safety]
---

## Контекст

Из анализа флоу — гэп **H**. `self-review` — это самообман по определению (я ревьюю себя). Для high-stakes изменений (security, DB миграция, breaking API change, деструктивные операции, prod-конфиг) — стоит вовлекать внешний источник до commit'а:

- Либо dispatched subagent через `Agent(subagent_type=code-reviewer)` — независимый прогон diff'а.
- Либо явный «попроси user одобрить» — короткая пауза с diff'ом.
- Либо `superpowers:requesting-code-review` — специализированный skill для запроса review.

Нужно определить условия high-stakes и подключить один из этих механизмов.

## Acceptance criteria

- [ ] В CLAUDE.md добавлен триггер `high-stakes`: изменения в security/, migrations/, deletion, prod-конфигах, cross-cutting breaking API changes.
- [ ] Для high-stakes задач `task-done`'s Verification gate дополнен требованием external review — субагент **или** явный user-check.
- [ ] Выбор (subagent vs user vs superpowers) — либо оформлен как правило по типу high-stakes, либо оставлен на agent'а с пояснением критериев.
- [ ] То же зеркалено в harness.

## План

1. Определить критерии «high-stakes» (список зон).
2. Выбрать механизм (subagent / user-check / superpowers skill).
3. Обновить `task-done.md` + CLAUDE.md.
4. Push в оба репо.

## Лог

- 2026-07-13: заведена по итогам анализа флоу (гэп H).
