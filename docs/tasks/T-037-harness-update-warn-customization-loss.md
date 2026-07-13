---
id: T-037
title: harness-update — предупреждать о потере кастомизации harness-managed skill'ов
status: done
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/harness-update.md
  - ~/.claude/skills/setup-agent-harness/references/skills/harness-update.md
related_docs:
  - docs/tasks/T-035-harness-update-skill.md
tags: [tech-debt, harness, safety]
---

## Контекст

Из self-review T-035: skill `harness-update` v1 force-overwrite'ит 8 harness-managed skill-файлов без предупреждения о потере кастомизации. Если пользователь между двумя `/harness-update` кастомизировал `task-add.md` (например, изменил формулировку триажа под свои процессы) — эти изменения молча пропадут.

## Acceptance criteria

- [ ] Перед apply (шаг 5 в `harness-update.md`) — сравнить `.claude/skills/<name>.md` с `~/.claude/skills/setup-agent-harness/references/skills/<name>.md` по контенту.
- [ ] Если различается **и** файл на стороне harness'а отличается от **той версии, которая была при последнем sync'е** (эту версию нужно как-то фиксировать — вероятно в `.claude/harness-sync-state.json` с sha checksum'ов) — это может быть local customization; предупредить пользователя явно: «`.claude/skills/task-add.md` расходится с harness-версией. Если это ваша кастомизация — она пропадёт. Продолжить?»
- [ ] Если файл различается **но harness-версия совпадает с предыдущим sync'ем** — значит это чистый upstream update, можно применять без специального предупреждения.

## План

1. Определить формат `.claude/harness-sync-state.json` (sha от последнего примерённого скилла).
2. Расширить `harness-update.md` — шаг сравнения sha, ветвление логики предупреждения.
3. Обкатать.

## Лог

- 2026-07-13: заведена авто-режимом из self-review T-035 (категория B).
- 2026-07-13: закрыта. `harness-update.md` (проект + harness) расширен: шаг 3 «Diff summary + customization check» использует `.claude/harness-sync-state.json` для различения upstream update vs local customization. Local customization помечается ⚠ в diff-summary + отдельный дополнительный prompt user'у. Шаг 5 (apply) обновляет sync-state.json после каждого copy. Playbook Фаза 6 дополнена: initial setup пишет harness-sync-state.json. Сгенерирован `harness-sync-state.json` для этого проекта (post-T-038: все 8 skill'ов = harness references, harness_sha=e324459). AC-check: 3/3 буквально.
