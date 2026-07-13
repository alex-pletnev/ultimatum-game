---
id: T-029
title: Расширить wheel-check — read before write (docs pre-check) + impact analysis
status: done
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
- 2026-07-13: закрыта. `wheel-check.md` в обеих сторонах (проект + harness) дополнен двумя новыми шагами: 5 «Docs pre-check» — читать `docs/*.md` для затронутой области, ловить противоречия с задокументированными инвариантами; 6 «Impact analysis» — grep по каллерам, оценка внешних API-контрактов, порог >5 каллеров как эскалация. Отчёт wheel-check теперь содержит явные строки «Docs: ...» и «Impact: N каллеров / API-контракт [да/нет]». AC-deviation check: 3 из 3 пунктов буквально.
