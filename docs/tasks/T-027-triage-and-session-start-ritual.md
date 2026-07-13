---
id: T-027
title: Session-start ritual — чтение INDEX.md и синхронизация с in_progress тасками при входе в новую сессию
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - CLAUDE.md
related_docs:
  - docs/tasks/T-023-superpowers-integration-in-project.md
tags: [meta, agent-behavior]
---

## Контекст

При анализе флоу harness'а (гэп «I. Session-start ritual») выявлено: при входе в новую сессию агент не обязан прочитать `INDEX.md` и синхронизироваться с in_progress тасками. Каждая новая сессия — риск начать с нуля, потерять контекст, задать пользователю вопросы, ответы на которые уже в `## Лог` таска.

Триаж (см. T-026) частично закрывает эту дыру для новых задач — но не для **продолжения** уже открытых.

## Acceptance criteria

- [ ] В `CLAUDE.md` добавлено правило session-start: первое сообщение новой сессии — прочитать `docs/tasks/INDEX.md`, посмотреть in_progress-таски, кратко сказать пользователю «в работе T-X, T-Y (следующие шаги: Z)».
- [ ] Если задача из in_progress упомянута в сообщении пользователя — открыть таск-файл целиком, прочитать `## План` и `## Лог`, продолжить от последнего шага.
- [ ] Правило зеркалено в `harness/templates/claude-md.template.md`.
- [ ] Push в оба репо.

## План

1. Добавить в `CLAUDE.md` секцию «Session-start».
2. Зеркалить в harness template.
3. Commit + push.

## Лог

- 2026-07-13: заведена по итогам анализа флоу.
