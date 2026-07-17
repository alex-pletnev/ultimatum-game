---
id: T-105
title: Правило — при архитектурном pivot'е runbook обновляется в том же commit'е, не оставляется устаревший
status: pending
priority: medium
created: 2026-07-17
updated: 2026-07-17
related_code:
  - CLAUDE.md
  - ~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md
related_docs:
  - docs/tasks/T-090-prod-deploy-readiness.md
  - docs/tasks/T-102-refactor-13-deploy-under-vm.md
tags: [meta, harness]
---

## Контекст

В T-090 было **два** архитектурных pivot'а:
1. Fly.io + Neon → Yandex.Cloud (в spec/design обновлено).
2. YC Serverless Container → Compute VM (в spec/design обновлено).

Однако `docs/13-deploy.md` разделы B.1-B.7 остались написанными под изначальный Serverless-вариант. T-102 (`tech-debt low`) — уже заведён, чтобы это исправить, но симптом важнее самой задачи.

Правило: **если задача включает архитектурный pivot (смена хостера / стека / фундаментального подхода) — обновление связанного runbook'а обязательно в том же commit-е, что и pivot**. Не откладывать «на потом», не оставлять WARN'ы.

## Acceptance criteria

- [ ] Строка в CLAUDE.md проекта + harness template — в «Проактивные триггеры» или «Что не делать»: «При архитектурном pivot'е (смена хостера/стека/deployment target'а) — runbook в том же commit'е. Не оставлять `docs/*-deploy*.md` описывающими заброшенный вариант».
- [ ] Cross-ref из docs-sync правил.

## План

1. Формулировка правила.
2. Патч CLAUDE.md проекта + harness template.

## Лог

- 2026-07-17: заведена в ходе Волны 3 meta-задач после T-101. Симптом наблюдался в T-090 (двух pivot'а подряд без синхронного обновления runbook'а).
