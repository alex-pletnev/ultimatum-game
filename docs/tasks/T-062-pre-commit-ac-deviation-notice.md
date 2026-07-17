---
id: T-062
title: Уточнить в CLAUDE.md — pre-flight объявление отклонения от AC ≠ pre-commit уведомление
status: done
priority: medium
created: 2026-07-14
updated: 2026-07-17
related_code:
  - CLAUDE.md
related_docs:
  - docs/tasks/T-025-notify-before-ac-deviation.md
tags: [meta, harness]
---

## Контекст

В T-055 я объявил в pre-flight «AC #1 требует 401 при невалидном Bearer — не буду реализовывать, приму глобальную семантику проекта (403)». User ответил «го, но не забудь всё проверить». Я это интерпретировал как молчаливое одобрение и запушил без второго явного «commit'ю с отклонением от AC #1».

По букве T-025: «уведомить пользователя до commit'а при отклонении от буквы AC». Формально одно уведомление в pre-flight было — но user не ответил именно «да, отклоняйся». В self-review я это заметил постфактум.

Правило нуждается в уточнении:
- Если в pre-flight есть явное объявление отклонения → user явно санкционирует ("да, отклоняйся") → достаточно.
- Если pre-flight объявляет отклонение, а user отвечает общим «го» — pre-commit нужно продублировать одной строкой: «commit'ю с отклонением от AC #N — [что именно не сделано]».

## Acceptance criteria

- [x] В `CLAUDE.md` (или `.claude/skills/pre-flight.md`) — уточнение: pre-flight-объявление отклонения от AC требует **явного** «да, отклоняйся» от user'а, иначе продублировать одной строкой pre-commit.
- [x] Портировать в harness template.

## План

1. Обновить `CLAUDE.md` правило (либо secция «Проактивные триггеры», либо блок про T-025).
2. Обновить `.claude/skills/pre-flight.md` — добавить чек-item «получил ли явный approve на озвученное отклонение? если нет — pre-commit repeat».
3. Портировать в harness.

## Лог

- 2026-07-14: заведено из self-review T-055 (commit cf73ed3), категория E. Priority medium — не критично, но паттерн «pre-flight сказал → считаю согласованным» будет повторяться без правила.
- 2026-07-17: закрыта. Проактивный триггер добавлен в CLAUDE.md проекта + harness. Pre-flight обязательный 4-й пункт (AC-deviation approve required) добавлен в `.claude/skills/pre-flight.md` (проект + harness).
