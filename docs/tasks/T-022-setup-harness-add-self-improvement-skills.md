---
id: T-022
title: Расширить setup-agent-harness — включить wheel-check/mid-retro/self-review в bootstrap
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - ~/.claude/skills/setup-agent-harness/SKILL.md
  - ~/.claude/skills/setup-agent-harness/references/playbook.md
  - ~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md
related_docs:
  - docs/tasks/T-021-agent-self-improvement-skills.md
tags: [meta, agent-behavior, skills, harness]
---

## Контекст

В T-021 добавили в этот проект 3 skill'а само-улучшения агента (wheel-check, mid-retro, self-review) + правила-триггеры в CLAUDE.md. Пока это только у этого проекта — но `setup-agent-harness` (user-scoped skill в `~/.claude/skills/`) поднимает harness в **любом** новом проекте и должен установить эти 3 skill'а тоже, иначе новые проекты будут стартовать с урезанным набором.

## Acceptance criteria

- [ ] `~/.claude/skills/setup-agent-harness/references/skills/` содержит 3 новых файла: `wheel-check.md`, `mid-retro.md`, `self-review.md` — генеризованные, без Kotlin/Spring/gradle-specifics.
- [ ] `~/.claude/skills/setup-agent-harness/SKILL.md`: описание обновлено (7 slash-skills вместо 4), таблица key references расширена.
- [ ] `references/playbook.md` фаза 6: копирует 7 файлов вместо 4.
- [ ] `references/templates/claude-md.template.md`:
  - таблица «Slash-команды» — 3 новые строки;
  - таблица «Проактивные триггеры» — 4 новые строки (wheel-check, mid-retro, self-review, длительные команды);
  - новая секция «Долгие команды — эвристика ожидания» с placeholder'ом `{{DURATION_BASELINES}}` для стек-специфичных времён.
- [ ] `references/stack-detectors.md` (или в самом playbook'е) — reasonable defaults для `{{DURATION_BASELINES}}` по стекам (JVM/Gradle, Node, Python, Rust, Go).
- [ ] «Идемпотентный повторный вызов» в playbook'е обновлён — при повторном setup'е на уже настроенном проекте надо доложить 3 недостающих skill'а.

## План

1. Скопировать 3 skill-файла из `.claude/skills/` в `~/.claude/skills/setup-agent-harness/references/skills/`, вычистить проектные specifics.
2. Обновить SKILL.md, playbook.md, claude-md.template.md.
3. Добавить baseline'ы длительностей в stack-detectors.md.
4. Обновить handled-случай в идемпотентном повторе.

## Лог

- 2026-07-13: заведена сразу после T-021 — user предложил распространить новые skills через harness.
- 2026-07-13: реализация — в `~/.claude/skills/setup-agent-harness/`:
  - `references/skills/wheel-check.md`, `mid-retro.md`, `self-review.md` — генерические версии без Kotlin/Spring/gradle specifics.
  - `SKILL.md` — описание «7 slash-skills», таблица key references расширена, `Обязательные проверки` обновлены.
  - `references/playbook.md` — Фаза 6 копирует 7 файлов, идемпотентный докат недостающих skills, новый плейсхолдер `{{DURATION_BASELINES}}` в Фазе 7.
  - `references/templates/claude-md.template.md` — таблица Slash-команд (+3 строки), Проактивные триггеры (+4 строки: 3 skills + эскалация долгих команд), новая секция «Долгие команды — эвристика ожидания» с `{{DURATION_BASELINES}}`.
  - `references/stack-detectors.md` — новая секция «Durations — baseline'ы» с готовыми таблицами для 10 стеков (Node, Python, Rust, Go, JVM Gradle, JVM Maven, .NET, Ruby, PHP, Flutter) + fallback для неопределимого.
