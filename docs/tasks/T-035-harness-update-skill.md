---
id: T-035
title: /harness-update — обновить skills+CLAUDE.md существующего проекта из свежих шаблонов harness'а
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - ~/.claude/skills/setup-agent-harness/SKILL.md
  - ~/.claude/skills/setup-agent-harness/references/playbook.md
related_docs:
  - docs/tasks/T-022-setup-harness-add-self-improvement-skills.md
tags: [meta, harness, skills]
---

## Контекст

Сейчас, чтобы применить накопленные изменения в harness'е к уже настроенному проекту, нужно запустить `/setup-agent-harness` и выбрать вариант (a) «только skills» или (b) «только CLAUDE.md» или (d) «полный переустановщик». Это работает, но:

- Пользователь может не помнить, что именно поменялось — риск выбрать не тот вариант.
- Опасно затронуть docs/tasks/*, project-specific настройки в CLAUDE.md.
- Логика «идемпотентного доката» и «повторного вызова» размазаны по playbook'у.

Хочется явную под-команду `/harness-update`, которая делает узкий, безопасный, предсказуемый шаг: **обновляет только те файлы, которые точно являются "чистыми" копиями из harness-шаблонов**, и НЕ трогает project-specific контент.

## Acceptance criteria

- [ ] В `~/.claude/skills/setup-agent-harness/` появляется под-команда/секция `/harness-update` (или отдельный skill `.claude/skills/harness-update.md` в целевом проекте).
- [ ] Команда обновляет:
  - Все 8 skill-файлов в `.claude/skills/` — принудительно из свежих templates.
  - CLAUDE.md — с backup'ом старого (аналогично полному Setup-у), с сохранением project-specific секций (стек, специфичные правила).
- [ ] Команда НЕ трогает:
  - `docs/**` (кроме, возможно, добавления новых стандартных индексов).
  - `docs/tasks/**`.
  - `.claude/settings.local.json`.
  - Custom skill'ы, которые пользователь добавил сам.
- [ ] Показывает пользователю diff-summary перед применением: «Обновляю: 8 skills, CLAUDE.md. Сохраняю: docs/, tasks/, custom skills. Продолжить?».
- [ ] Push харнесс-репо `alex-pletnev/claude-setup-agent-harness`.

## План

1. Определить где живёт команда — расширение `setup-agent-harness` или отдельный skill в целевом проекте (устанавливается тем же `setup-agent-harness`, но живёт локально).
2. Реализовать логику различения "чистых template-файлов" vs "project-modified".
3. Обкатать на этом проекте (после накопленных изменений — самая полная площадка для теста).
4. Push.

## Лог

- 2026-07-13: заведена по итогам обсуждения T-033 — пользователь заметил, что при live-разработке я вручную дублирую правки в обе стороны, и предложил формализовать процесс синхронизации.
- 2026-07-13: закрыта v1. **AC-deviation (согласовано с пользователем до commit'а):** CLAUDE.md-sync descope'нут в отдельную задачу T-036 (вариант A — `.claude/harness-config.json`). Реализовано в v1: новый skill `.claude/skills/harness-update.md` в проекте + `~/.claude/skills/setup-agent-harness/references/skills/harness-update.md` в harness. Skill описывает Explicit-only процедуру: `определить source → list 8 target'ов → diff-summary с пометками (обновлю/добавлю/skip) → wait for user y/n → apply force-copy → сохранить custom skill'ы → commit + push`. SKILL.md, playbook.md, claude-md.template.md обновлены с 8 → 9 skills. Slash-команда `/harness-update` добавлена в таблицу CLAUDE.md обеих сторон.
