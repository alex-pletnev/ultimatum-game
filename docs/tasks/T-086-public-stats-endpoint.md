---
id: T-086
title: Публичные (анонимные) GET /statistics/{id}/csv и /session/{id}/with-teams-and-members
status: done
priority: high
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/SecurityConfiguration.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/StatisticController.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/SessionController.kt
tags: [backend, security, api]
---

## Контекст

Фронт (`ultimatum-game-ui/BACKEND-FIX-public-stats-endpoint.md`): страница
`/session/{id}/stats` — публичная летопись партии, ссылку кидают в чат/форум.
Без Bearer два endpoint'а возвращали 403.

## Что сделано

1. `SecurityConfiguration` — `permitAll` для GET `/statistics/*/csv` и
   GET `/session/*/with-teams-and-members`.
2. Убрал `@PreAuthorize` с этих двух методов (иначе метод-security'ю вернёт 403
   ещё до фильтра, если контекст не аутентифицирован).

Оба endpoint'а — read-only, sensitive-инфы нет (ники + суммы). POST /session
и WS-команды остались с auth.

## Лог

- 2026-07-16: заведено по BACKEND-FIX от фронта.
- 2026-07-16: done. `./gradlew check` — зелёный. Проверка: `curl -w "HTTP=%{http_code}\n" http://localhost:8080/api/v1/session/{id}/with-teams-and-members` без Bearer → 200.
