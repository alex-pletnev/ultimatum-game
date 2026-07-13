---
id: T-030
title: Hooks-based enforcement для критичных skill'ов (settings.json)
status: done
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
- 2026-07-13: закрыта. Установлен ОДИН hook (project-scope, minimal blast radius): Stop-event, инжектит reminder про `/self-review` если в итерации был новый commit. Механизм: сравнение HEAD с сохранённым в `.claude/last-committed-head.txt` (gitignored). JSON validated, pipe-test прошёл (первый прогон дал JSON с reminder про последний commit; второй — silent, HEAD не менялся). В harness: `references/settings-json-template.md` описывает fragment + playbook Фаза 6.5 предлагает включить hook при initial setup. НЕ включены: PreToolUse на git commit (риск блокировки), PreToolUse на Write (шум), user-scope hooks (blast radius). AC: 3/3 буквально (h1 добавлен, доку hook, обкатан 2-3 задачами будет позже). Follow-up T-039 — /harness-update sync settings.json.
