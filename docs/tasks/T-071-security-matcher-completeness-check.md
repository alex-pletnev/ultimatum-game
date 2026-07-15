---
id: T-071
title: Правило — при добавлении @MessageMapping / @SendToUser проверять WebSocketSecurityConfig matcher
status: pending
priority: medium
created: 2026-07-15
updated: 2026-07-15
related_code:
  - .claude/skills/pre-flight.md
  - CLAUDE.md
  - src/main/kotlin/edu/itmo/ultimatumgame/configs/WebSocketSecurityConfig.kt
related_docs:
  - docs/tasks/T-050-validation-exception-http-mapping.md
  - docs/tasks/T-054-implement-abort-and-pause-round.md
  - docs/tasks/T-070-disable-websocket-csrf-for-stomp.md
  - docs/tasks/T-067-tdd-skip-in-infrastructure-tasks.md
tags: [meta, process, security-config, stomp]
---

## Контекст

В T-070 (fix STOMP CONNECT для фронта) обнаружились **две регрессии из недавних задач**:

1. **T-054** добавил WS endpoint `/app/session/{id}/round.abort` в `SessionAdminWsController`,
   но не добавил matcher `.simpDestMatchers("/app/session/*/round.abort").hasRole("ADMIN")`
   в `WebSocketSecurityConfig`. Результат: попадал под `anyMessage().denyAll()` → админ
   получал 403.

2. **T-050** добавил `WebSocketExceptionAdvice.@SendToUser("/queue/errors")`, но не добавил
   matcher `.simpSubscribeDestMatchers("/user/queue/errors").hasAnyRole(...)`. Результат:
   клиентский SUBSCRIBE падал под `anyMessage().denyAll()` → фронт не получал WS-ошибки.

Оба self-review не поймали это — фокусировался на бизнес-логике, не на secondary-эффектах
в парном security-config'е. Паттерн: **изменил STOMP-контроллер / добавил `@SendToUser` /
поменял destination — забываю проверить `WebSocketSecurityConfig`**.

## Acceptance criteria

- [ ] Обновить `.claude/skills/pre-flight.md` (или CLAUDE.md проактивные триггеры):
  при работе с файлами `controllers/ws/*` или `@SendToUser` в service — обязательный
  чек «есть ли парный matcher в WebSocketSecurityConfig для нового destination?»
  до commit'а.
- [ ] Или (более надёжно): unit-тест `WebSocketSecurityConfigAuditTest`, который через
  рефлексию собирает все `@MessageMapping`-destinations из `controllers/ws/*` и все
  `@SendToUser`-destinations из `exceptions/` / `services/*`, и проверяет что каждый
  destination покрыт matcher'ом. Fail → миссинг matcher.
- [ ] Первый вариант обучает агента; второй — enforce'ит автоматически. По возможности —
  второй.

## План

1. Определить какой подход надёжнее (process rule vs test-audit).
2. Если test-audit — черновой прогон через рефлексию: собрать все `@MessageMapping.value()`
   в `controllers/ws/`, все `@SendToUser.value()` в `services/` и `exceptions/`, сверить
   против введённых matcher'ов в `WebSocketSecurityConfig`.
3. Если process-rule — уточнить `pre-flight.md` формулировкой и добавить в проактивные
   триггеры CLAUDE.md.

## Лог

- 2026-07-15: заведено из self-review T-070 (commit a052b73). Паттерн (T-050 и T-054 —
  два miss'а подряд), приоритет medium. Соседствует с T-067 — общая тема «infrastructure
  и security-config под TDD/audit».
