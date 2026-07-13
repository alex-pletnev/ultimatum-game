---
id: T-030
title: Hooks-based enforcement для критичных skill'ов (settings.json)
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/settings.local.json
related_docs:
  - docs/tasks/T-021-agent-self-improvement-skills.md
tags: [meta, agent-behavior, harness]
---

## Контекст

Из анализа флоу — гэп **L (Enforcement layer)**. Все триггеры сейчас soft — я могу «забыть» вызвать `wheel-check`, `verification-before-completion`, `self-review`. По факту работы в проекте — уже видно, что иногда пропускаю.

Hook'и в `settings.json` дают hard-enforcement: harness'а/Claude Code выполняет заданный skill перед конкретным event'ом (Stop, PreToolUse, PostToolUse). Явно отложено в T-021 «пока не понадобится» — момент, вероятно, подошёл.

Кандидаты на hard-enforcement:

1. **On Stop event** (перед ответом user'у в конце итерации) — если в этой итерации был commit — прогнать `self-review`.
2. **On PreToolUse(Write) на новый файл в `src/**`** — прогнать `wheel-check`.
3. **On PreToolUse(Bash `git commit`)** — прогнать `verification-before-completion`.

Риск: hooks трудно отлаживать, легко зациклить. Начинать с одного (self-review on Stop), если работает — добавлять.

## Acceptance criteria

- [ ] Один hook (на выбор — предложить пользователю какой) настроен в `.claude/settings.local.json` этого проекта.
- [ ] Задокументирован в CLAUDE.md.
- [ ] Прогнан на 2-3 задачах, чтобы убедиться что не залипает.
- [ ] Если работает — перенос в harness.

## План

1. Использовать skill `update-config` для правильной настройки hooks.
2. Обкатать один hook.
3. Оценить, стоит ли делать остальные.

## Лог

- 2026-07-13: заведена по итогам анализа флоу. Приоритет low, так как soft-триггеры пока справляются, но паттерн пропуска skills уже виден.
