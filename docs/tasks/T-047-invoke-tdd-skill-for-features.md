---
id: T-047
title: TDD discipline — для feature-задач сначала провальный тест, потом impl, а не одновременно
status: done
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - CLAUDE.md
  - .claude/skills/task-add.md
related_docs:
  - docs/tasks/T-003-scoring-engine.md
tags: [meta, harness, skills]
---

## Контекст

CLAUDE.md таблица «Проактивные триггеры» гласит: «Новая фича с ясным acceptance criteria → `superpowers:test-driven-development` (до реализации — написать провальные тесты по каждому AC, потом реализация до зелёного)».

Обнаружено self-review'ом T-003 (2026-07-13): я писал новые тесты (`StatsServiceTest.*`) и реализацию (`StatsService.buildSessionScore`, `CsvService`, `EventPublisherService`) практически одновременно — тесты компилировались вместе с impl'ом, ни разу не видел «RED» состояние отдельно. Формально AC покрыт, но дисциплина test-driven не соблюдена — если бы в impl'е был баг, оба (тест и код) могли согласованно быть кривыми и я бы этого не заметил.

## Acceptance criteria

- [ ] В CLAUDE.md (или в `task-add.md`) явно указать: для задач с тегом `feature` или `feat` при invoke'е — сначала прогон `superpowers:test-driven-development` skill'а, минимум один цикл RED→GREEN на каждом AC.
- [ ] Обновить `.claude/skills/self-review.md` — категория E — добавить чек «увидел ли ты хотя бы одно RED состояние теста в этой задаче? Если нет — почему».
- [ ] Порт в setup-agent-harness references.

## План

1. Обновить CLAUDE.md — Проактивные триггеры + workflow для feature-задач.
2. Обновить `.claude/skills/self-review.md`.
3. Портировать в harness-репо.
4. Обкатать на первой следующей feature-задаче.

## Лог

- 2026-07-13: заведено из self-review T-003. Категория E (улучшения меня). Priority low — паттерн не разрушительный (тесты всё-таки покрывают AC), но дисциплина TDD прояснит слепые пятна в будущих реализациях.
- 2026-07-13: закрыто. CLAUDE.md проекта + harness template — TDD-триггер усилен формулировкой «RED→GREEN обязателен, увидеть FAIL до реализации». `.claude/skills/self-review.md` + harness reference — добавлен обязательный чек в категории E: «увидел ли RED?». Portи в harness-репо (commit 60dca1c). Sha в `harness-sync-state.json` обновлена.
