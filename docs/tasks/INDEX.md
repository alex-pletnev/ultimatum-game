# Task Index

Автоматически обновляется командами `/task-add`, `/task-done`, `/task-sync`. При ручных правках следить за консистентностью с файлами задач.

Формат: сортировка по `id` (по возрастанию). Формат `docs/tasks/README.md`.

## Открытые задачи

| ID | Название | Статус | Приоритет | Обновлено | Файл |
|----|----------|--------|-----------|-----------|------|
| T-001 | Применять `index.sql` при старте приложения | pending | medium | 2026-07-12 | [T-001](T-001-apply-index-sql-on-startup.md) |
| T-009 | Стабилизировать AsyncAPI snapshot (SpringStompDefaultHeaders) | pending | low | 2026-07-12 | [T-009](T-009-asyncapi-snapshot-determinism.md) |
| T-016 | Согласовать версии Kotlin-плагинов (kapt vs jvm/spring/jpa) | pending | low | 2026-07-13 | [T-016](T-016-align-kotlin-plugin-versions.md) |
| T-018 | Тесты должны падать быстро при отсутствии инфраструктуры (Docker/Postgres) | pending | medium | 2026-07-13 | [T-018](T-018-tests-fail-fast-on-missing-infra.md) |
| T-041 | Часть C — warn при sha256-коллизии между recommended hook'ами | pending | low | 2026-07-13 | [T-041](T-041-sha-collision-warn-in-part-c.md) |
| T-042 | Skill'ы, использующие sha/hash, обязать приводить input→expected-output пример | pending | medium | 2026-07-13 | [T-042](T-042-inline-hash-examples-in-skills.md) |
| T-043 | High-priority баги, замеченные по ходу задачи, чинить в той же сессии до task-done | pending | medium | 2026-07-13 | [T-043](T-043-fix-high-priority-bugs-inline.md) |
| T-044 | Ввести систему миграций БД (Flyway или Liquibase) вместо Hibernate auto-DDL | pending | medium | 2026-07-13 | [T-044](T-044-adopt-db-migrations.md) |
| T-048 | JUnit5 parallel execution + split integrationTest source-set | pending | low | 2026-07-13 | [T-048](T-048-parallel-tests-and-split-integration.md) |
| T-050 | Validation exceptions в service-слое должны отдаваться клиенту как HTTP 400 (не 500) | pending | low | 2026-07-13 | [T-050](T-050-validation-exception-http-mapping.md) |
| T-054 | Реализовать AdminGameplayService.abortCurrentRound и pauseRound (сейчас TODO) | pending | medium | 2026-07-13 | [T-054](T-054-implement-abort-and-pause-round.md) |
| T-057 | Фильтры state/sessionType/openToConnect в GET /session | pending | low | 2026-07-13 | [T-057](T-057-session-list-filters.md) |
| T-058 | Отдельный DTO для персональной доставки оффера | pending | low | 2026-07-13 | [T-058](T-058-personal-offer-dto.md) |
| T-060 | Проактивный триггер — invoke systematic-debugging при повторной правке того же правила в сессии | pending | medium | 2026-07-13 | [T-060](T-060-invoke-systematic-debugging-on-second-patch.md) |
| T-061 | TokenRevocationService — TTL-cleanup отозванных jti (unbounded memory concern) | pending | low | 2026-07-14 | [T-061](T-061-token-revocation-ttl-cleanup.md) |
| T-062 | Уточнить в CLAUDE.md — pre-flight объявление отклонения от AC ≠ pre-commit уведомление | pending | medium | 2026-07-14 | [T-062](T-062-pre-commit-ac-deviation-notice.md) |
| T-063 | FreeForAllStrategy.shuffleOffers — бесконечный цикл при неудачном RNG (derangement bug) | pending | high | 2026-07-14 | [T-063](T-063-freeforall-derangement-flake.md) |

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
| T-030 | Hooks-based enforcement для критичных skill'ов | done | 2026-07-13 | [T-030](T-030-hooks-based-enforcement.md) |
| T-031 | Cadence-based checkpoint для mid-retro — тихие длинные задачи | done | 2026-07-13 | [T-031](T-031-mid-retro-cadence-checkpoint.md) |
| T-032 | External review (code-reviewer subagent) для high-stakes | done | 2026-07-13 | [T-032](T-032-external-review-for-high-stakes.md) |
| T-033 | Session-end handoff ritual — зеркало T-027 | done | 2026-07-13 | [T-033](T-033-session-end-handoff-ritual.md) |
| T-035 | /harness-update — sync .claude/skills/ существующего проекта (v1: только skill'ы) | done | 2026-07-13 | [T-035](T-035-harness-update-skill.md) |
| T-036 | /harness-update v2 — sync CLAUDE.md через harness-config.json | done | 2026-07-13 | [T-036](T-036-harness-update-claude-md.md) |
| T-034 | Periodic learning consolidation — сканирование закрытых задач на паттерны | done | 2026-07-13 | [T-034](T-034-periodic-learning-consolidation.md) |
| T-037 | harness-update — предупреждать о потере кастомизации harness-managed skill'ов | done | 2026-07-13 | [T-037](T-037-harness-update-warn-customization-loss.md) |
| T-038 | Привести проект под harness-конвенции — generic skills + SPECIFIC_RULES + отточить /harness-update | done | 2026-07-13 | [T-038](T-038-clean-project-under-harness-conventions.md) |
| T-039 | /harness-update — синхронизировать .claude/settings.json с harness template | done | 2026-07-13 | [T-039](T-039-harness-update-sync-settings-json.md) |
| T-040 | Починить Stop-hook — hookSpecificOutput не валиден для события Stop | done | 2026-07-13 | [T-040](T-040-fix-stop-hook-output-schema.md) |
| T-003 | Реализовать расчёт баллов игроков по итогам раундов | done | 2026-07-13 | [T-003](T-003-scoring-engine.md) |
| T-046 | Оптимизировать время `./gradlew check` — сейчас порог 5 мин | done | 2026-07-13 | [T-046](T-046-optimize-gradle-check-time.md) |
| T-047 | TDD discipline — сначала провальный тест, потом impl, а не одновременно | done | 2026-07-13 | [T-047](T-047-invoke-tdd-skill-for-features.md) |
| T-049 | Portability check — абсолютные пути и machine-specific настройки не коммитятся в общие файлы | done | 2026-07-13 | [T-049](T-049-portability-check-in-committed-files.md) |
| T-045 | Сверить реализованные правила gameplay с канонической Ultimatum Game + многопользовательские адаптации | done | 2026-07-13 | [T-045](T-045-verify-game-rules-vs-canonical.md) |
| T-051 | Broadcast '/topic/offerCreated' должен содержать responder после shuffle | done | 2026-07-13 | [T-051](T-051-broadcast-offer-with-responder.md) |
| T-052 | Endpoint для получения истории раундов сессии со всеми оффер'ами и решениями | done | 2026-07-13 | [T-052](T-052-rounds-history-endpoint.md) |
| T-053 | Helper-поля 'myRole' и 'phase' в RoundResponse — фронт должен знать «мой ход» | done | 2026-07-13 | [T-053](T-053-my-turn-hints-in-responses.md) |
| T-055 | POST /auth/logout — invalidate JWT + audit event | done | 2026-07-14 | [T-055](T-055-logout-endpoint.md) |
| T-056 | POST /auth/refresh — refresh JWT для long-lived sessions | done | 2026-07-14 | [T-056](T-056-jwt-refresh-endpoint.md) |
| T-059 | Gradle-команды всегда через run_in_background=true; `./gradlew --stop` до подозрительных запусков | done | 2026-07-13 | [T-059](T-059-gradle-always-background-and-stop.md) |

## Легенда статусов

`pending` → `in_progress` → `done` / `cancelled`. Промежуточно возможен `blocked`.
