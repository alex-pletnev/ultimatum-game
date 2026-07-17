---
id: T-099
title: Springwolf ERROR на старте — SessionAdminWsController::abortCurrentRound/openSession без @Payload
status: pending
priority: low
created: 2026-07-17
updated: 2026-07-17
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/SessionAdminWsController.kt
related_docs:
  - docs/06-websocket-api.md
tags: [bug, tech-debt]
---

## Контекст

На каждом старте приложения (bootRun и docker run с prod-профилем) Springwolf
логирует два ERROR-стектрейса:

```
ERROR io.github.springwolf.core.asyncapi.channels.DefaultChannelsService
  An error was encountered during channel scanning ...:
  Payload cannot be detected. Multi-parameter method must have one parameter
  annotated with @Payload, but none was found:
  SessionAdminWsController::abortCurrentRound

ERROR io.github.springwolf.core.asyncapi.operations.DefaultOperationsService
  ... SessionAdminWsController::openSession
```

Обнаружено на bootRun/docker smoke T-090 Phase 1+2. Приложение стартует
успешно, но грязнит prod-логи ERROR-уровнем на каждом деплое.

Причина — WS-методы с несколькими параметрами (например `@Header`, `@Payload`,
`Principal`) должны иметь **явный** `@Payload` для extractor'а Springwolf'а.
Метод `abortCurrentRound` — наследие T-054 (`RoundPhase.ABORTED`), `openSession`
— может быть без body или с несколькими header'ами.

## Acceptance criteria

- [ ] На `SessionAdminWsController::abortCurrentRound` — если у метода есть
  payload-параметр, добавить `@Payload`. Если payload'а нет (голый SEND-запуск)
  — рассмотреть удаление springwolf-scanning для метода или конфиг Springwolf.
- [ ] То же для `SessionAdminWsController::openSession`.
- [ ] Локально: bootRun чистый — без ERROR'ов от Springwolf.
- [ ] `./gradlew generateApiSnapshots` — asyncapi.json обновлён без разошлась.

## План

1. Открыть `SessionAdminWsController.kt`, посмотреть сигнатуры двух методов.
2. Расставить `@Payload` где применимо; либо если payload не нужен —
  добавить `@SpringwolfDisableScanning` (если поддерживается) или excluded-list
  в конфиге.
3. Прогнать bootRun локально — ERROR'ы ушли.
4. `generateApiSnapshots` → обновить снапшот.

## Лог

- 2026-07-17: заведено self-review'ом commit f8db6f8 (T-090 smoke). Категория B.
