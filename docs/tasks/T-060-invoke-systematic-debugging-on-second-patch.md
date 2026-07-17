---
id: T-060
title: Проактивный триггер — invoke systematic-debugging при повторной правке того же правила в сессии
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-17
related_code:
  - CLAUDE.md
  - .claude/skills/self-review.md
related_docs:
  - docs/tasks/T-059-gradle-always-background-and-stop.md
tags: [meta, harness]
---

## Контекст

T-059 задокументировал «reactive patchwork» как lagging indicator в self-review категории E. Но self-review случается уже после `task-done` — поздно, чтобы предотвратить очередную итерацию патча в той же сессии.

Из T-053 → T-059: три reactive итерации fix'а gradle-lock'ов прошли внутри одной сессии, `superpowers:systematic-debugging` не вызывался ни разу — хотя по симптомам («правлю то же правило второй раз») это был classic case для него.

Нужен leading indicator: **как только правлю то же самое правило / файл / секцию 2+ раза в сессии → немедленно invoke `superpowers:systematic-debugging`**, а не ждать третьей итерации и последующего self-review.

## Acceptance criteria

- [x] В `CLAUDE.md` таблица «Проактивные триггеры» — новая строка: «Правлю то же самое правило / секцию CLAUDE.md / skill 2+ раза внутри одной сессии → `superpowers:systematic-debugging` в Auto-mode → искать root cause, а не патчить симптом».
- [x] Портировать в harness template.
- [x] Кросс-ссылка из self-review категории E: «если этот пункт триггерится в self-review, значит проактивный триггер не сработал → escalation для CLAUDE.md rule».

## План

1. Добавить строку в таблицу проактивных триггеров в `CLAUDE.md` и harness template.
2. В `self-review.md` (проект + harness) — крест-ссылка от «Reactive patchwork» к проактивному триггеру.

## Лог

- 2026-07-13: заведено из self-review T-059 (commit f5caa73), категория E. Priority medium — leading indicator для root-cause-first подхода; low blast radius, но high leverage для будущих сессий с сопротивляющейся проблемой.
- 2026-07-17: закрыта. Проактивный триггер добавлен в CLAUDE.md проекта + harness template. Cross-ref из self-review.md категория E (проект + harness): «reactive patchwork = сработал этот триггер поздно».
