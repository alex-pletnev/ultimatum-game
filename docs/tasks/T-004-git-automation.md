---
id: T-004
title: Встроить автоматический commit + push в skills трекера
status: done
priority: high
created: 2026-07-12
updated: 2026-07-12
related_code: []
related_docs:
  - CLAUDE.md
  - docs/tasks/README.md
  - .claude/skills/task-add.md
  - .claude/skills/task-done.md
  - .claude/skills/docs-sync.md
tags: [meta]
---

## Контекст

Пользователь хочет максимально автоматизированный harness: агент сам коммитит и пушит завершённую работу без запроса подтверждения. Push прямо в `main` (текущий стиль работы, одиночная разработка).

## Acceptance criteria

- [x] Задан формат commit-сообщения (Conventional Commits + `Refs: docs/tasks/T-XXX.md`).
- [x] `task-done` автоматически: тесты (если задача трогает `src/`) → commit → push. При падении тестов — блок commit.
- [x] `docs-sync` при применении правок: commit `docs: sync with src` → push.
- [x] `task-add` коммитит создание карточки, push не делает (уедет со следующим).
- [x] В `CLAUDE.md` — новый раздел «Git-автоматизация» с правилами и safety-guards.
- [x] В `docs/tasks/README.md` — упомянут авто-flow.
- [x] Safety: секреты не коммитятся (простой grep-фильтр по diff); `--no-verify` не используется; `git add -A/.` не используется — только именованные пути; force-push запрещён.

## План

1. Дописать секции в `.claude/skills/task-done.md`, `.claude/skills/docs-sync.md`, `.claude/skills/task-add.md`.
2. Добавить раздел в `CLAUDE.md`.
3. Обновить `docs/tasks/README.md`.
4. Обновить `INDEX.md` (T-004 → done).
5. Проверить всё через `git status`, закоммитить и запушить как первое применение нового flow.

## Лог

- 2026-07-12: заведена по запросу пользователя. Мандат: без подтверждений, прямо в `main`.
- 2026-07-12: реализовано. Обновлены CLAUDE.md, task-add/task-done/docs-sync skills, docs/tasks/README.md. Закрыта.
