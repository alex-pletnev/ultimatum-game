---
id: T-024
title: Перенести superpowers-integration в setup-agent-harness — после обкатки в T-023
status: done
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - ~/.claude/skills/setup-agent-harness/references/skills/task-add.md
  - ~/.claude/skills/setup-agent-harness/references/skills/task-done.md
  - ~/.claude/skills/setup-agent-harness/references/skills/self-review.md
  - ~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md
related_docs:
  - docs/superpowers/specs/2026-07-13-superpowers-integration-design.md
  - docs/tasks/T-023-superpowers-integration-in-project.md
tags: [meta, agent-behavior, skills, harness, superpowers]
---

## Контекст

T-023 добавил superpowers-integration в наши 7 skills — но только в этом проекте. Harness (`~/.claude/skills/setup-agent-harness/`, репо `alex-pletnev/claude-setup-agent-harness`) — user-scoped, ставит наш flow во всех новых проектах. Пока integration в harness не перенесён — новые проекты стартуют без него.

Триггер закрытия этой задачи: после 2-3 реальных задач в проекте, где wire-points superpowers'ов реально сработали. Замеченные проблемы / улучшения — учесть при переносе.

## Acceptance criteria

- [ ] `~/.claude/skills/setup-agent-harness/references/skills/task-add.md` — обновлён (секция «Non-trivial задачи: brainstorming первым»).
- [ ] `~/.claude/skills/setup-agent-harness/references/skills/task-done.md` — обновлён (секция «Verification gate»).
- [ ] `~/.claude/skills/setup-agent-harness/references/skills/self-review.md` — обновлён (шаг 2 с receiving-code-review).
- [ ] `~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md` — таблица триггеров + 3 строки, секция «Superpowers integration».
- [ ] `SKILL.md` — описание обновлено (упомянут superpowers-integration).
- [ ] Push в `alex-pletnev/claude-setup-agent-harness`.
- [ ] Учтены наблюдения из работы в проекте после T-023.

## План

1. Дождаться 2-3 реальных задач в этом проекте (T-023 включает live-обкатку).
2. Собрать выводы: что работало, что не сработало, где integration переусложняет.
3. Скопировать финальные версии skill-файлов в harness с генерализацией.
4. Commit + push в harness-репо.
5. Закрыть T-024.

## Лог

- 2026-07-13: заведена как follow-up T-023 (обкатка → перенос).
- 2026-07-13: закрыта. В `~/.claude/skills/setup-agent-harness/` обновлены 5 файлов: `references/skills/task-add.md` (non-trivial → brainstorming), `task-done.md` (verification gate), `self-review.md` (шаг 2 с receiving-code-review + правило категории E из T-025), `references/templates/claude-md.template.md` (+3 триггера + секция «Superpowers integration»), `SKILL.md` (упомянут superpowers-integration в description). Commit `be83197` в `alex-pletnev/claude-setup-agent-harness main`. Учтены наблюдения обкатки: verification-gate реально ловил недоделки (T-020, T-002), правило про категорию E добавлено превентивно (T-025).
