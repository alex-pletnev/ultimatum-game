# Task Index

Автоматически обновляется командами `/task-add`, `/task-done`, `/task-sync`. При ручных правках следить за консистентностью с файлами задач.

Формат: сортировка по `id` (по возрастанию). Формат `docs/tasks/README.md`.

## Открытые задачи

| ID | Название | Статус | Приоритет | Обновлено | Файл |
|----|----------|--------|-----------|-----------|------|
| T-001 | Применять `index.sql` при старте приложения | pending | medium | 2026-07-12 | [T-001](T-001-apply-index-sql-on-startup.md) |
| T-003 | Реализовать расчёт баллов игроков по итогам раундов | pending | high | 2026-07-12 | [T-003](T-003-scoring-engine.md) |
| T-009 | Стабилизировать AsyncAPI snapshot (SpringStompDefaultHeaders) | pending | low | 2026-07-12 | [T-009](T-009-asyncapi-snapshot-determinism.md) |
| T-016 | Согласовать версии Kotlin-плагинов (kapt vs jvm/spring/jpa) | pending | low | 2026-07-13 | [T-016](T-016-align-kotlin-plugin-versions.md) |
| T-018 | Тесты должны падать быстро при отсутствии инфраструктуры (Docker/Postgres) | pending | medium | 2026-07-13 | [T-018](T-018-tests-fail-fast-on-missing-infra.md) |
| T-030 | Hooks-based enforcement для критичных skill'ов | pending | low | 2026-07-13 | [T-030](T-030-hooks-based-enforcement.md) |
| T-032 | External review (code-reviewer subagent) для high-stakes | pending | low | 2026-07-13 | [T-032](T-032-external-review-for-high-stakes.md) |
| T-034 | Periodic learning consolidation — сканирование закрытых задач на паттерны | pending | low | 2026-07-13 | [T-034](T-034-periodic-learning-consolidation.md) |
| T-036 | /harness-update v2 — sync CLAUDE.md через harness-config.json | pending | medium | 2026-07-13 | [T-036](T-036-harness-update-claude-md.md) |
| T-037 | harness-update — предупреждать о потере кастомизации harness-managed skill'ов | pending | low | 2026-07-13 | [T-037](T-037-harness-update-warn-customization-loss.md) |

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
| T-014 | Настроить detekt (плагин + formatting + baseline) под Kotlin-стандарты | done | 2026-07-13 | [T-014](T-014-setup-detekt-baseline.md) |
| T-015 | Выхлопать detekt baseline — починить все зафиксированные findings | done | 2026-07-13 | [T-015](T-015-detekt-clear-baseline.md) |
| T-017 | Стандарт логирования и observability — JSON, MDC, доменные события, Prometheus | done | 2026-07-13 | [T-017](T-017-observability-standard.md) |
| T-019 | Правило для агента — не считать многоминутное ожидание нормой, эскалировать при аномалии | done | 2026-07-13 | [T-019](T-019-agent-command-duration-heuristics.md) |
| T-021 | Skills для само-улучшения агента — wheel-check, mid-retro, self-review + правила в CLAUDE.md | done | 2026-07-13 | [T-021](T-021-agent-self-improvement-skills.md) |
| T-022 | Расширить setup-agent-harness — включить wheel-check/mid-retro/self-review в bootstrap | done | 2026-07-13 | [T-022](T-022-setup-harness-add-self-improvement-skills.md) |
| T-002 | Fetch join для `DecisionRepository.findBySessionId` | done | 2026-07-13 | [T-002](T-002-decision-fetch-join.md) |
| T-020 | Починить detekt-findings в тестах вместо @file:Suppress | done | 2026-07-13 | [T-020](T-020-fix-test-detekt-suppresses.md) |
| T-023 | Интеграция superpowers-skills в наши skills — обкатка в этом проекте | done | 2026-07-13 | [T-023](T-023-superpowers-integration-in-project.md) |
| T-024 | Перенести superpowers-integration в setup-agent-harness — после обкатки в T-023 | done | 2026-07-13 | [T-024](T-024-superpowers-integration-port-to-harness.md) |
| T-026 | Триаж по типам задач в task-add — bug ≠ brainstorming, а systematic-debugging | done | 2026-07-13 | [T-026](T-026-fix-task-add-triage-vs-brainstorming.md) |
| T-027 | Session-start ritual — чтение INDEX.md и синхронизация с in_progress тасками | done | 2026-07-13 | [T-027](T-027-triage-and-session-start-ritual.md) |
| T-025 | Правило — при отклонении от буквы AC уведомить пользователя до commit'а | done | 2026-07-13 | [T-025](T-025-notify-before-ac-deviation.md) |
| T-028 | Pre-flight check для non-trivial — assumptions/risks/reversibility | done | 2026-07-13 | [T-028](T-028-pre-flight-check-for-nontrivial.md) |
| T-029 | Расширить wheel-check — read before write (docs + impact) | done | 2026-07-13 | [T-029](T-029-wheel-check-docs-and-impact.md) |
| T-031 | Cadence-based checkpoint для mid-retro — тихие длинные задачи | done | 2026-07-13 | [T-031](T-031-mid-retro-cadence-checkpoint.md) |
| T-033 | Session-end handoff ritual — зеркало T-027 | done | 2026-07-13 | [T-033](T-033-session-end-handoff-ritual.md) |
| T-035 | /harness-update — sync .claude/skills/ существующего проекта (v1: только skill'ы) | done | 2026-07-13 | [T-035](T-035-harness-update-skill.md) |

## Легенда статусов

`pending` → `in_progress` → `done` / `cancelled`. Промежуточно возможен `blocked`.
