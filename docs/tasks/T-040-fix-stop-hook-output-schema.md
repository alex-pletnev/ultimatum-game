---
id: T-040
title: Починить Stop-hook — hookSpecificOutput не валиден для события Stop
status: pending
priority: high
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/settings.json
  - ~/.claude/skills/setup-agent-harness/references/settings-json-template.md
  - ~/.claude/skills/setup-agent-harness/references/settings-hooks/self-review-reminder.json
related_docs:
  - docs/tasks/T-030-hooks-based-enforcement.md
tags: [tech-debt, harness, hooks]
---

## Контекст

T-030 добавил Stop-hook, который после commit'а должен инжектить в контекст reminder «прогони /self-review». В реальности hook падает валидацией Claude Code:

```
Hook JSON output validation failed — (root): Invalid input
The hook's output was:
{
  "hookSpecificOutput": {
    "hookEventName": "Stop",
    "additionalContext": "..."
  }
}
```

Причина: поле `hookSpecificOutput.additionalContext` разрешено только для `UserPromptSubmit` и (optional) `PostToolUse`, а для `Stop` — не валидно. Значит **T-030 никогда не работал** — reminder не доходил до модели, self-review в Auto-mode держался только на CLAUDE.md-правиле.

Обнаружено при работе над T-039 (2026-07-13) — в UI показалась ошибка валидации.

## Acceptance criteria

- [ ] Определить валидный способ инжектить контекст из Stop-hook'а (Claude Code docs / реальные варианты — `decision: "block"` + `reason`, или `systemMessage`, или stdout).
- [ ] Обновить `command` в `.claude/settings.json` и `references/settings-hooks/self-review-reminder.json` (T-039 переместит source-of-truth сюда).
- [ ] Verify: после commit'а reminder действительно попадает в контекст модели в следующей итерации (не просто «нет ошибки в UI», а реально виден в conversation).
- [ ] Обновить `references/settings-json-template.md` — описать, какой output-shape правильный для Stop.
- [ ] `/harness-update` Часть C (после T-039) синхронизирует правку в проектах, где hook уже установлен.

## План

1. Разобраться с реальной семантикой Stop-hook output. Прочитать актуальную доку Claude Code (или `/help` / `claude --help` / release notes) — какие поля разрешены и куда идёт `additionalContext` для Stop.
2. Прототип: правка `command`, локальный тест — сделать пустой commit, посмотреть что попадает в контекст следующей итерации.
3. Если через hook инжектить контекст невозможно — рассмотреть альтернативу (Stop-hook просто пишет флаг-файл, а CLAUDE.md-правило «после Stop-hook читай `.claude/self-review-pending` при следующем UserPromptSubmit» — но это уже отдельная архитектура).
4. Обновить оба источника (settings.json проекта + settings-hooks/*.json в harness'е). После T-039 — прогнать `/harness-update` для проверки sync-механики.

## Лог

- 2026-07-13: обнаружено при работе над T-039 (сообщение об ошибке hook validation в UI). Заведено как high — правило self-review-after-commit сейчас не enforced'ся через hook, только через CLAUDE.md.
