---
id: T-095
title: SessionServiceTest — split на функциональные группы (LargeClass detekt suppress)
status: pending
priority: low
created: 2026-07-17
updated: 2026-07-17
related_code:
  - src/test/kotlin/edu/itmo/ultimatumgame/services/SessionServiceTest.kt
related_docs:
  - docs/tasks/T-093-session-members-count-and-auto-close.md
tags: [tech-debt, tests, refactor]
---

## Контекст

После T-087 (6 bulk-npc тестов) и T-093 (4 auto-close теста) файл вырос до
~600+ строк, detekt LargeClass ругается. Временно подавлено `@file:Suppress("LargeClass")`.

## Acceptance criteria

- [ ] Файл разбит на 3-4 отдельных класса по функциональным группам:
  - `SessionServiceCrudTest` (createSession, getters, setters, getAllSessions).
  - `SessionServiceJoinTest` (joinSession, joinSessionAsObserver, addNpcMember, auto-close).
  - `SessionServiceBulkNpcsTest` (bulkCreateAndJoinNpcs + auto-close bulk).
  - `SessionServiceGetRoundsTest` (getRounds + enrichWithHints).
- [ ] Общая инициализация mockk'ов вынесена в `AbstractSessionServiceTest` или @BeforeEach.
- [ ] `@file:Suppress("LargeClass")` удалён.
- [ ] `./gradlew check` зелёный.

## План

Micro-refactor. Каждый класс — свои mockk-заглушки (либо общий базовый).

## Лог

- 2026-07-17: заведено self-review'ом T-093 (auto-close тестов добавили массы файлу).
