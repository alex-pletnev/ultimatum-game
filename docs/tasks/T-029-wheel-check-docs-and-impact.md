---
id: T-029
title: Расширить wheel-check — read before write (docs pre-check) + impact analysis
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/wheel-check.md
related_docs:
  - docs/tasks/T-021-agent-self-improvement-skills.md
tags: [meta, agent-behavior, skills]
---

## Контекст

Из анализа флоу — гэпы **E (Docs pre-check) и F (Impact analysis)** — сливаются в «прочитай прежде чем писать». Сейчас `wheel-check` отвечает на «есть ли уже готовое», но не отвечает на:

- Что говорят `docs/*.md` про модуль, который я собираюсь трогать? (инварианты, договорённости)
- Кто зависит от того, что я собираюсь менять? (callers, downstream)

Логично расширить `wheel-check` двумя дополнительными шагами.

## Acceptance criteria

- [ ] `.claude/skills/wheel-check.md` дополнен: шаг «Docs pre-check» — прочитать релевантные `docs/*.md` для затронутой области, отметить инварианты; шаг «Impact analysis» — grep по каллерам/зависимостям изменяемого модуля.
- [ ] То же в harness.
- [ ] Push.

## Лог

- 2026-07-13: заведена по итогам анализа флоу.
