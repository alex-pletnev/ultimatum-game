---
id: T-089
title: TDD-skip 3 задачи подряд (T-082, T-083, T-086) — пришло время уточнить триггер
status: pending
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - /Users/aleksandrpletnev/sandbox/ultimatum-game/CLAUDE.md
  - /Users/aleksandrpletnev/sandbox/ultimatum-game/.claude/skills/self-review.md
related_docs:
  - docs/tasks/T-067-tdd-skip-in-infrastructure-tasks.md
tags: [meta]
---

## Контекст

Замечено в self-review 67ac41b (T-083). Уже T-067 (аналогичное наблюдение).

В T-082 (CRUD endpoints), T-083 (join-npc/bulk), T-086 (security fix) я НЕ
писал RED-тесты до реализации. По правилу TDD в CLAUDE.md (Проактивные
триггеры → «Новая фича с ясным acceptance criteria») это нарушение.

Root cause по горячим следам:
1. Я оправдываю skip тем, что integration'ы плана «heavy» и будут покрывать
   endpoints — но AC требует именно юнит-теста на happy path и валидации.
2. Auto-mode и urgency (frontend bug reports) подталкивают к «minimum viable»
   без тестов.
3. Правило в CLAUDE.md уже есть, но недостаточно жёсткое: нет автотриггера
   pre-flight'а «увидел ли я RED до commit'а».

## Acceptance criteria

- [ ] Внести в CLAUDE.md явное правило: **перед commit'ом feature-задачи** —
      pre-commit чек «есть ли RED-run в `## Лог` таск-файла или в session-tracker».
      Нет — блокирующий вопрос перед `task-done`.
- [ ] В `self-review.md` шаг 2 (обязательный чек для feature-задач) уточнить,
      что 3 подряд skip'а RED → automated priority `high` для meta-таска.

## План

Правки в CLAUDE.md + self-review.md. Одним commit'ом.

## Лог

- 2026-07-16: заведено из self-review 67ac41b. Это повторение паттерна из T-067 →
      приоритет `medium`, не `low`.
