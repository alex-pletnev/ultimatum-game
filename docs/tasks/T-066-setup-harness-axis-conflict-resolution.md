---
id: T-066
title: setup-agent-harness фаза 2.5 — разрешение конфликтов между осями интерпретации PROJECT_CONTEXT
status: pending
priority: medium
created: 2026-07-14
updated: 2026-07-14
related_code:
  - ~/.claude/skills/setup-agent-harness/references/playbook.md
related_docs:
  - docs/tasks/T-065-setup-harness-project-context-arg.md
tags: [meta, harness, dx]
---

## Контекст

В T-065 (harness commit `ef799d6`) введена фаза 2.5 с 9 осями интерпретации свободного текста PROJECT_CONTEXT. Оси помечены как «не взаимно исключающие» — при совпадении сигнальных слов из нескольких осей их правила складываются. Но некоторые пары **противоречат** друг другу и требуют разрешения:

| Пара осей | Конфликт |
|-----------|----------|
| Solo-mode vs Team-mode | Solo убирает external review, Team добавляет — что применить если user написал «solo проект для команды»? |
| Speed-first vs Compliance-first | Speed пропускает pre-flight <30 LOC, Compliance требует pre-flight для любого касания payment-code — накладываются |
| Exploration-first vs Reliability-first | Exploration допускает breaking changes, Reliability требует rollback plan — противоречит |
| Legacy-aware vs Speed-first | Legacy требует обоснования каждого structural change, Speed — минимум обвязки |

Сейчас playbook просто «складывает» правила при multi-axis matching — пользователь получит противоречивый SPECIFIC_RULES блок.

## Acceptance criteria

- [ ] В playbook фазе 2.5 — таблица «Приоритеты конфликтов» с 4-6 разрешёнными парами.
- [ ] Правило: **Compliance-first > Reliability-first > Team-mode > Speed-first > Solo-mode > Exploration-first** (safety wins).
- [ ] При обнаружении конфликта — pipeline на фазе 2.5.4 упоминает пользователю: «Совмещены оси X + Y, приоритет Y».
- [ ] «Wait 3-10s» протокол переформулировать — сейчас нереализуем в tool'инге (агент не имеет таймера). Заменить на «выводит блок, ждёт explicit `ok`; если пользователь молчит два turn'а — считать согласованным».

## План

1. Добавить таблицу приоритетов в playbook.md → фаза 2.5.2 (после таблицы осей).
2. Добавить логику: если триггернуты обе конфликтующие оси — брать правила high-priority оси, low-priority правила либо игнорировать, либо (по маркеру `⚠ конфликт`) вставлять как секцию «Альтернатива, отклонена».
3. Переформулировать confirmation-протокол в 2.5.4.
4. Обновить примеры интерпретации.

## Лог

- 2026-07-14: заведено из self-review T-065 (harness ef799d6), категория D. Priority medium — конфликты будут проявляться сразу на первых реальных вызовах (типовой input «solo, но payment-система, для команды из 3 человек»); текущая implementation даёт противоречивый output без предупреждения. Не блокер, но качество interpretation'а под угрозой на первом же use.
