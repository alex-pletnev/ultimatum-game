# Task Index

Автоматически обновляется командами `/task-add`, `/task-done`, `/task-sync`. При ручных правках следить за консистентностью с файлами задач.

Формат: сортировка по `id` (по возрастанию). Формат `docs/tasks/README.md`.

## Открытые задачи

| ID | Название | Статус | Приоритет | Обновлено | Файл |
|----|----------|--------|-----------|-----------|------|
| T-001 | Применять `index.sql` при старте приложения | pending | medium | 2026-07-12 | [T-001](T-001-apply-index-sql-on-startup.md) |
| T-002 | Fetch join для `DecisionRepository.findBySessionId` | pending | low | 2026-07-12 | [T-002](T-002-decision-fetch-join.md) |
| T-003 | Реализовать расчёт баллов игроков по итогам раундов | pending | high | 2026-07-12 | [T-003](T-003-scoring-engine.md) |
| T-009 | Стабилизировать AsyncAPI snapshot (SpringStompDefaultHeaders) | pending | low | 2026-07-12 | [T-009](T-009-asyncapi-snapshot-determinism.md) |

## Закрытые задачи

| ID | Название | Статус | Закрыто | Файл |
|----|----------|--------|---------|------|
| T-004 | Встроить автоматический commit + push в skills трекера | done | 2026-07-12 | [T-004](T-004-git-automation.md) |
| T-005 | Добавить `.gitignore` | done | 2026-07-12 | [T-005](T-005-add-gitignore.md) |
| T-006 | Автогенерация REST/WS спек, удаление ручных YAML | done | 2026-07-12 | [T-006](T-006-api-spec-autogeneration.md) |
| T-007 | Полировка генератора API-снапшотов | done | 2026-07-12 | [T-007](T-007-snapshot-generator-refinements.md) |
| T-008 | EventPublisherService: убрать nullable + error() | done | 2026-07-12 | [T-008](T-008-event-publisher-non-null-types.md) |
| T-010 | Упростить авторизацию до JWT-only (убрать CSRF + session STOMP checks) | done | 2026-07-12 | [T-010](T-010-simplify-auth-jwt-only.md) |
| T-011 | Удалить orphan-методы isUserAreSession* из SessionService | done | 2026-07-12 | [T-011](T-011-remove-orphan-session-membership-methods.md) |
| T-012 | Поднять тестовое покрытие бизнес-логики до 80%+ (services + shuffle strategies) | done | 2026-07-12 | [T-012](T-012-test-coverage-80-percent-business-logic.md) |
| T-013 | Восстановить gradle-wrapper (gradle/wrapper/*) | done | 2026-07-13 | [T-013](T-013-restore-gradle-wrapper.md) |

## Легенда статусов

`pending` → `in_progress` → `done` / `cancelled`. Промежуточно возможен `blocked`.
