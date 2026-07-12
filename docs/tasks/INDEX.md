# Task Index

Автоматически обновляется командами `/task-add`, `/task-done`, `/task-sync`. При ручных правках следить за консистентностью с файлами задач.

Формат: сортировка по `id` (по возрастанию). Формат `docs/tasks/README.md`.

## Открытые задачи

| ID | Название | Статус | Приоритет | Обновлено | Файл |
|----|----------|--------|-----------|-----------|------|
| T-001 | Применять `index.sql` при старте приложения | pending | medium | 2026-07-12 | [T-001](T-001-apply-index-sql-on-startup.md) |
| T-002 | Fetch join для `DecisionRepository.findBySessionId` | pending | low | 2026-07-12 | [T-002](T-002-decision-fetch-join.md) |
| T-003 | Реализовать расчёт баллов игроков по итогам раундов | pending | high | 2026-07-12 | [T-003](T-003-scoring-engine.md) |

## Закрытые задачи

| ID | Название | Статус | Закрыто | Файл |
|----|----------|--------|---------|------|
| T-004 | Встроить автоматический commit + push в skills трекера | done | 2026-07-12 | [T-004](T-004-git-automation.md) |
| T-005 | Добавить `.gitignore` | done | 2026-07-12 | [T-005](T-005-add-gitignore.md) |
| T-006 | Автогенерация REST/WS спек, удаление ручных YAML | done | 2026-07-12 | [T-006](T-006-api-spec-autogeneration.md) |
| T-007 | Полировка генератора API-снапшотов | done | 2026-07-12 | [T-007](T-007-snapshot-generator-refinements.md) |
| T-008 | EventPublisherService: убрать nullable + error() | done | 2026-07-12 | [T-008](T-008-event-publisher-non-null-types.md) |

## Легенда статусов

`pending` → `in_progress` → `done` / `cancelled`. Промежуточно возможен `blocked`.
