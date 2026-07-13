---
id: T-026
title: Триаж по типам задач в task-add — bug ≠ brainstorming, а systematic-debugging
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/task-add.md
related_docs:
  - docs/tasks/T-023-superpowers-integration-in-project.md
tags: [meta, agent-behavior, skills]
---

## Контекст

При анализе флоу harness'а (см. историю разговора «Проанализируем флоу...») обнаружен конфликт: `task-add.md` для не-тривиальных задач требует `superpowers:brainstorming`. Но brainstorming — процесс для creative work (новые фичи, архитектура). Для bug fix / regression правильный процесс — `superpowers:systematic-debugging`. Правило «non-trivial → brainstorming» применённое к багу выключает нужный debugging-процесс и включает лишний design-процесс.

Нужно вместо бинарного «trivial / non-trivial» явно ввести **триаж по типам** — feature, bug, refactor, investigation, hotfix, trivial — с описанием подключаемого процесса на каждый.

## Acceptance criteria

- [x] В `.claude/skills/task-add.md` секция «Non-trivial задачи: brainstorming первым» заменена на «Триаж перед созданием таска» с таблицей 6 типов и подключаемого процесса.
- [x] То же зеркалено в `~/.claude/skills/setup-agent-harness/references/skills/task-add.md`.
- [x] Push в harness-репо `alex-pletnev/claude-setup-agent-harness`.

## План

1. Заменить секцию в `task-add.md` в проекте и в harness'е.
2. Commit + push в оба репо.
3. Закрыть.

## Лог

- 2026-07-13: заведена по итогам анализа флоу. Ключевая дыра — task-add не различал creative work и debugging.
- 2026-07-13: закрыта. Триаж-таблица в обоих `task-add.md` (проект + harness). 6 типов: trivial, feature, bug, refactor, investigation, emergency hotfix. Bug теперь корректно ведёт на systematic-debugging.
