---
id: T-106
title: PR + worktrees + parallel-agents workflow — правила, wire в harness, условный триггер
status: pending
priority: medium
created: 2026-07-17
updated: 2026-07-17
related_code:
  - ~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md
  - ~/.claude/skills/setup-agent-harness/SKILL.md
related_docs:
  - docs/tasks/T-101-cicd-pipeline.md
tags: [meta, harness]
---

## Контекст

Пользователь хочет попробовать git worktrees + parallel agents в будущих проектах. Механика:

- **Условный триггер:** если в flight только одна задача — direct-push в `main` fast-path (как сейчас в этом проекте). Если во время выполнения прилетает **вторая** задача — переключение на worktrees + parallel subagent'ы, каждая ветка → PR → merge.
- **Не платить overhead одиночной задаче** — worktrees включаются только при реальной параллельности.

Требует изменений:
1. CLAUDE.md — правило про условный триггер + wire для `superpowers:using-git-worktrees` / `superpowers:dispatching-parallel-agents`.
2. setup-agent-harness opt: «PR-based или direct-push? (default: гибрид — direct-push для solo-задач, PR при parallel)».
3. Skill/раздел про декомпозицию: как определить «параллелится ли задача с текущими»?
4. Обновление `finishing-a-development-branch` wire — при parallel-режиме `task-done` = PR + auto-merge.
5. Branch protection setup — как часть CI/CD bootstrap (T-103).

## Acceptance criteria

- [ ] CLAUDE.md проекта + harness template — правило про условный триггер (одна задача — main direct, две+ — worktrees).
- [ ] Проактивный триггер: «в session-flight ≥2 in_progress-задач одновременно → invoke `superpowers:using-git-worktrees` для новой + `superpowers:dispatching-parallel-agents` для оркестрации».
- [ ] setup-agent-harness — opt при init с 3 вариантами: `direct-push-only`, `pr-only`, `hybrid` (default).
- [ ] Runbook: как правильно декомпозировать «пачку» задач для parallel execution (файлы не пересекаются, тесты изолируются).
- [ ] Обкатано на первой bulk-задаче в новом сервисе (не в vacuum).

## План

1. Спека hybrid-workflow (brainstorming на этапе реализации).
2. Правила в CLAUDE.md + harness.
3. Обкатать на новом сервисе.

## Лог

- 2026-07-17: заведена по запросу пользователя после ознакомления с концепцией git worktrees. Условный триггер (worktrees только при 2+ параллельных задачах) — ключевой user requirement.
