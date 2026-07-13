---
id: T-036
title: /harness-update v2 — sync CLAUDE.md с сохранением project-specific через harness-config.json
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/harness-update.md
  - ~/.claude/skills/setup-agent-harness/references/skills/harness-update.md
  - ~/.claude/skills/setup-agent-harness/references/playbook.md
related_docs:
  - docs/tasks/T-035-harness-update-skill.md
tags: [meta, harness, skills]
---

## Контекст

T-035 v1 закрыл минимальную часть `/harness-update` — sync 8+1 skill-файлов. CLAUDE.md обновление осталось за кадром, потому что требует нетривиального механизма сохранения project-specific placeholder-values (`{{STACK}}`, `{{TEST_COMMAND}}`, `{{PRIMARY_BRANCH}}`, `{{LANGUAGE}}`, `{{DURATION_BASELINES}}`, `{{SPECIFIC_RULES}}` и т.д.).

Выбранный подход (в brainstorming T-035): **вариант A** — хранить placeholder-values в `.claude/harness-config.json`, который создаётся при initial `/setup-agent-harness` и используется как источник правды при re-render CLAUDE.md через `/harness-update`.

## Acceptance criteria

- [ ] При initial `/setup-agent-harness` — записывается `.claude/harness-config.json` со всеми placeholder-values, использованными для рендера CLAUDE.md.
- [ ] `/harness-update` расширяется:
  - Если `.claude/harness-config.json` существует — предложить пользователю также обновить CLAUDE.md (v2): backup → re-render template с сохранёнными placeholder'ами → показать diff → apply.
  - Если конфиг отсутствует (проект настроен старым setup-агентом) — предложить сгенерировать его один раз с текущими значениями (сам агент их спрашивает у пользователя).
- [ ] `.claude/harness-config.json` формально описан: JSON-объект с ключами по названиям placeholder'ов из `claude-md.template.md`.
- [ ] `.gitignore` — файл `harness-config.json` **не** игнорируется (нужен в git для team-consistency).
- [ ] `setup-agent-harness` playbook.md — Фаза 7 дополнена шагом «сохранить placeholder-values в harness-config.json».
- [ ] `harness-update.md` — секция v2 добавлена с описанием шагов обновления CLAUDE.md.

## План

1. Определить полный список placeholder'ов и их источников (некоторые вычисляются автоматически: stack, primary_branch, test_command; некоторые от user'а: language, specific_rules).
2. Формат `harness-config.json`.
3. Обновить `setup-agent-harness` playbook Фаза 7 — сохранение конфига.
4. Расширить `harness-update.md` — v2 flow для CLAUDE.md.
5. Обкатать на этом проекте (после T-036 — этот проект будет полигоном).
6. Push в оба репо.

## Лог

- 2026-07-13: заведена как follow-up T-035. v1 явно descope'ил CLAUDE.md; v2 (эта задача) закрывает недостаток через `.claude/harness-config.json` (вариант A выбран пользователем).
