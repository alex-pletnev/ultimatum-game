---
id: T-104
title: setup-agent-harness — правило про bootstrap через CLI (gh + cloud-CLI), не через UI
status: pending
priority: low
created: 2026-07-17
updated: 2026-07-17
related_code:
  - ~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md
related_docs:
  - docs/14-cicd.md
  - docs/tasks/T-101-cicd-pipeline.md
tags: [meta, harness]
---

## Контекст

В T-101 bootstrap CI/CD-инфраструктуры прошёл почти полностью автоматически: `yc iam key create` → `gh secret set` → `ssh-copy-id` (одно разрешение пользователя на SSH). Ни одного клика в web-UI.

Стоит закрепить как правило harness'а: «Bootstrap-шаги (secrets, ключи, cloud-ресурсы) — через CLI, не через web-UI, если CLI покрывает. UI использовать только когда CLI объективно не может (например, MFA activation, billing setup, GH branch protection UI)».

## Acceptance criteria

- [ ] CLAUDE.md template harness'а — одна короткая строка в «Проактивные триггеры» или в стилевые ограничения.
- [ ] Опционально: краткий раздел «Bootstrap-паттерн» в `docs/NN-cicd.md` template.

## План

1. Формулировка правила (одна строка).
2. Патч template'а harness'а.

## Лог

- 2026-07-17: заведена в ходе Волны 3 meta-задач после T-101.
