---
id: T-038
title: Привести проект под harness-конвенции — generic skills + SPECIFIC_RULES + отточить /harness-update
status: done
priority: high
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/
  - .claude/harness-config.json
  - CLAUDE.md
  - ~/.claude/skills/setup-agent-harness/references/
related_docs:
  - docs/tasks/T-035-harness-update-skill.md
  - docs/tasks/T-036-harness-update-claude-md.md
tags: [meta, harness, cleanup, high-priority]
---

## Контекст

Тест `/harness-update` v2 в T-036 выявил, что этот проект содержит два вида «грязи», которые нарушают чистое разделение generic-harness vs project-specific:

1. **8 skill'ов проекта** содержат стек-специфичные термы (`./gradlew`, `src/main/kotlin/**`, `detekt`) вместо generic-формулировок (`команда тестов из CLAUDE.md`, `исходники`, `линтер`).
2. **CLAUDE.md проекта** содержит расширенные секции («Во время работы» с TaskList, «После изменений в src/» с подпунктами и т.д.) прямо в теле, а не в `SPECIFIC_RULES` placeholder.

Дизайн задумка: **harness-managed файлы = 100% generic** (одинаковые для всех проектов). Project-specific — **только** в `harness-config.json` (placeholder values) + `SPECIFIC_RULES` (произвольные секции).

После рефактора `/harness-update` должен работать чистым copy'ом skill'ов и чистой substitution'ой в CLAUDE.md.

## Acceptance criteria

- [ ] Все 8 skill'ов в `.claude/skills/*.md` — **идентичны** соответствующим файлам в `~/.claude/skills/setup-agent-harness/references/skills/`. Никаких стек-специфичных термов.
- [ ] `.claude/harness-config.json` расширен: `SPECIFIC_RULES` содержит все project-specific секции, вычлененные из current CLAUDE.md.
- [ ] `CLAUDE.md` — result of rendering `claude-md.template.md` + `harness-config.json`. Ничего лишнего.
- [ ] Фикс: HTML-комментарий с placeholder-metadata в `claude-md.template.md` не попадает в рендер (либо strip regex'ом, либо move в playbook.md).
- [ ] `/harness-update` Часть A на этом проекте — «все skill'ы идентичны, skip».
- [ ] `/harness-update` Часть B — показывает пустой diff (проектная CLAUDE.md ≡ render).
- [ ] Push в оба репо.

## План

1. **Rehash skill'ов** — force-copy 8 файлов из harness references в проект. Верифицировать, что после этого все identical.
2. **Refactor CLAUDE.md** — вычленить project-specific секции, перенести в `SPECIFIC_RULES` как multi-line значение. Всё остальное — должно совпасть с template render'ом.
3. **Fix template** — убрать HTML-комментарий с placeholder-metadata из `claude-md.template.md` (переместить в `playbook.md` секцию Фаза 7.2 как reference).
4. **Обновить harness-config.json** — с новым расширенным `SPECIFIC_RULES`.
5. **Render + replace CLAUDE.md** — bombкup existing, записать rendered.
6. **Test /harness-update end-to-end** — Часть A должна показать «all identical», Часть B — «no diff». Если diff — итерировать.
7. **Push** в оба репо.

## Лог

- 2026-07-13: заведена по итогам live-теста `/harness-update` (T-036). Проект содержит manually-diverged skill'ы и CLAUDE.md с extra sections не в SPECIFIC_RULES. Priority: high — блокирует нормальную работу harness-update, и это упражнение раскроет ещё и дыры в дизайне skill'ов harness'а.
- 2026-07-13: закрыта. Force-copied 8 skill'ов из harness references в проект → все идентичны. Расширил template (Формат задач, развёрнутая «После изменений в исходниках», upfront intro к «Проактивным триггерам»). Убрал HTML-комментарий metadata из template (заменил коротким pointer'ом). Собрал multi-line `SPECIFIC_RULES` в harness-config.json с окружением (JAVA_HOME, colima, Postgres), gradle-специфичными проверками (jacoco, detekt), API-snapshot триггером, доп. «Что не делать». Applied rendered как новый CLAUDE.md. End-to-end тест: Часть A — 8/8 identical, Часть B — bit-exact. Backup `CLAUDE.md.bak-*` игнорируется в .gitignore. AC-deviation: 7/7 буквально. T-036 закрывается вместе (v2 полностью реализован).
