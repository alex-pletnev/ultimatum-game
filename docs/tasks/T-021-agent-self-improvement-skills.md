---
id: T-021
title: Skills для само-улучшения агента — wheel-check, mid-retro, self-review + правила в CLAUDE.md
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/wheel-check.md
  - .claude/skills/mid-retro.md
  - .claude/skills/self-review.md
  - CLAUDE.md
related_docs:
  - docs/tasks/T-019-agent-command-duration-heuristics.md
tags: [meta, agent-behavior, skills]
---

## Контекст

В T-017 всплыли повторяющиеся проблемы моего поведения:
1. Пишу код прежде чем проверить, есть ли уже готовое решение (Micrometer Tracing, Prometheus registry — чуть не изобрёл велосипеды).
2. Не останавливаюсь по ходу, чтобы посмотреть на уже сделанное — вылезаю за скоуп задачи, потом объясняю в ретро.
3. После `task-done` смотрю только на «что сделано», а не на «что было не так с моим процессом» — упускаю возможности улучшить себя.

Хочется формализовать это в skills (explicit кнопки) + CLAUDE.md (мягкие правила-триггеры). Комбо 2+1.

Также сюда добавляется T-019: правила эвристики ожидания долгих команд — это часть само-улучшения.

## Acceptance criteria

- [ ] Новый skill `.claude/skills/wheel-check.md` — проверка на изобретение велосипеда перед крупным написанием кода / добавлением зависимости.
- [ ] Новый skill `.claude/skills/mid-retro.md` — mid-task пауза для само-осмотра.
- [ ] Новый skill `.claude/skills/self-review.md` — post-task-done ревью diff'а на скоуп, недочёты, улучшения меня самого.
- [ ] Все 3 skill'а имеют Auto-mode (условие срабатывания) и Explicit-mode (`/название`).
- [ ] `CLAUDE.md`: секция «Проактивные триггеры» дополнена строками про 3 skill'а.
- [ ] `CLAUDE.md`: новая секция «Долгие команды — эвристика ожидания» (закрывает T-019) с baselines для gradle-команд и порогом эскалации.
- [ ] T-019 закрывается тем же коммитом.

## План

1. Написать 3 skill-файла (формат — как `task-add.md`, с секциями Auto-mode/Explicit-mode/Ограничения).
2. Обновить CLAUDE.md — таблица «Проактивные триггеры» + новая секция про длительности.
3. Закрыть T-019 (status done, лог), обновить INDEX.
4. Закрыть T-021 (status done, лог).
5. Commit + push.

## Лог

- 2026-07-13: заведена по запросу пользователя после ретро T-017. Комбо: три новых skill'а (wheel-check, mid-retro, self-review) + правила-триггеры в CLAUDE.md.
- 2026-07-13: реализация — созданы `.claude/skills/wheel-check.md`, `.claude/skills/mid-retro.md`, `.claude/skills/self-review.md`. В `CLAUDE.md`: расширена таблица Slash-команд (3 новые строки), расширена таблица «Проактивные триггеры» (4 новые строки), добавлена секция «Долгие команды — эвристика ожидания» (закрывает T-019). T-019 закрыт этим же коммитом.
