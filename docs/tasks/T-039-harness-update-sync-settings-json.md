---
id: T-039
title: /harness-update — синхронизировать .claude/settings.json с harness template
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/harness-update.md
  - ~/.claude/skills/setup-agent-harness/references/skills/harness-update.md
  - ~/.claude/skills/setup-agent-harness/references/settings-json-template.md
related_docs:
  - docs/tasks/T-030-hooks-based-enforcement.md
tags: [meta, harness, skills]
---

## Контекст

T-030 добавил Stop-hook (reminder для self-review после commit'а) в `.claude/settings.json` этого проекта. Также добавлен opt-in шаг в `/setup-agent-harness` playbook (Фаза 6.5). Но `/harness-update` пока не занимается `settings.json` — если в harness template'е появится новый hook (например, Часть B к T-030 или новые Stop-хуки), существующие проекты не подхватят их автоматически.

## Acceptance criteria

- [ ] `harness-update.md` расширяется Частью C — sync `.claude/settings.json` (аналогично Части A/B).
- [ ] Логика: сравнить hooks в проектном settings.json с рекомендованными из `references/settings-json-template.md`. Если проект имеет **свои hooks вне рекомендованных** — предупредить, не перезаписывать. Если проект имеет **устаревшую версию рекомендованного** — предложить обновить.
- [ ] Custom hooks (user'а собственные) — не трогать.
- [ ] Backup перед перезаписью.
- [ ] Обновлено sync-state.json (добавить `settings_json_sha`).

## План

1. Дождаться следующего hook'а от harness'а (второй Stop-hook, или PreCompact) — сейчас template один, sync тривиален.
2. Реализовать Часть C.
3. Обкатать на этом проекте.
4. Push в оба репо.

## Лог

- 2026-07-13: заведена как follow-up T-030. Priority low — пока в template один hook, ручной sync тривиален. Актуальность вырастет при добавлении второго hook'а.
