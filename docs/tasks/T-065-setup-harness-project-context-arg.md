---
id: T-065
title: setup-agent-harness — аргумент со свободным описанием контекста проекта → SPECIFIC_RULES
status: done
priority: high
created: 2026-07-14
updated: 2026-07-14
related_code:
  - ~/.claude/skills/setup-agent-harness/SKILL.md
  - ~/.claude/skills/setup-agent-harness/references/playbook.md
  - ~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md
related_docs: []
tags: [meta, harness, dx]
---

## Контекст

`setup-agent-harness` сейчас принимается без аргументов. Все правила проекта детектятся эвристикой по стеку (build/test commands, baseline durations). Но специфика *культуры разработки* проекта не улавливается: жёсткий MVP vs enterprise-payment vs research-prototype требуют кардинально разной строгости (детализация задач, pre-flight, external review, audit trail).

Идея: разрешить писать после `/setup-agent-harness` свободный текст-комментарий, который harness интерпретирует и превращает в конкретные правила в `SPECIFIC_RULES` блоке CLAUDE.md.

Примеры:
- `/setup-agent-harness жёсткий MVP, скорость важнее детализации, минимум обвязки` → правила «не заводить mid-retro/self-review для <30 LOC изменений», «пропускать pre-flight для тривиального», «PR ≤200 строк, review 5 минут».
- `/setup-agent-harness платёжная система, любая правка — с тестами, review, audit trail` → правила «pre-flight для всех задач ≥5 LOC», «external code-reviewer subagent для каждого task-done», «audit log в docs/audit/ обязателен».

## Acceptance criteria

- [x] SKILL.md: description упомянает аргумент; секция «Аргумент» описывает формат (свободный текст, RU/EN).
- [x] playbook.md: новая фаза 2.5 «Interpret project context» — парсит текст, генерирует markdown-блок для `SPECIFIC_RULES`, показывает пользователю до фазы 7.
- [x] `.claude/harness-config.json` получает ключ `PROJECT_CONTEXT` (raw user input) — для re-render при `/harness-update`.
- [x] `SPECIFIC_RULES` в шаблоне рендерится либо сгенерированным блоком (если аргумент был), либо пустой строкой (backward-compat).
- [~] Проверка на demo: не выполнена интерактивно — требует нового вызова `/setup-agent-harness` на чистом проекте с двумя разными аргументами. Формально реализация полная (playbook описывает интерпретацию), но живого end-to-end прогона не было. Follow-up при первом реальном использовании.

## План

1. Обновить SKILL.md — description и секция «Аргумент».
2. Обновить playbook.md — фаза 2.5 + требования к rendering.
3. Обновить template — гарантировать что `{{SPECIFIC_RULES}}` рендерится корректно с пустым/непустым значением.
4. Демонстрационные примеры интерпретации в отдельной секции playbook'а.
5. Commit в harness-репо + push.

## Лог

- 2026-07-14: заведено пользователем во время T-055/T-056 как EXTRA HIGH PRIORITY. Идея сохранена «на лету», реализация — прямо сейчас без brainstorm'а.
- 2026-07-14: закрыто. Harness commit `ef799d6`. Изменения: (1) SKILL.md — description упомянает опциональный аргумент, добавлена секция «Аргумент» с таблицей 4-х примеров типовой транcляции; (2) playbook.md — новая фаза 2.5 «Interpret project context» с таблицей из 9 осей интерпретации (Speed/Reliability/Compliance/Exploration/Rollout-safe/Team/Solo/Legacy/Data-science), формат сгенерированного markdown-блока, протокол confirmation-пользователем; (3) playbook.md фаза 7.4 — harness-config.json получил ключ PROJECT_CONTEXT (raw) отдельно от SPECIFIC_RULES (сгенерированное). Merge-политика: при повторном `/setup-agent-harness` без аргумента старый PROJECT_CONTEXT сохраняется; с аргументом — замещается; (4) harness-update.md bootstrap извлекает PROJECT_CONTEXT из существующего блока «Проектные особенности (интерпретация PROJECT_CONTEXT)» если он присутствует. Backward compatibility: без аргумента `SPECIFIC_RULES=""`, идентично старому поведению.
