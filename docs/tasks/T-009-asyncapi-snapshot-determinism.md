---
id: T-009
title: Стабилизировать AsyncAPI snapshot — уходят 6 строк на springwolf's SpringStompDefaultHeaders
status: pending
priority: low
created: 2026-07-12
updated: 2026-07-16
related_code:
  - src/test/kotlin/edu/itmo/ultimatumgame/SpecSnapshotGeneratorTest.kt
  - src/main/resources/doc/asyncapi.json
related_docs:
  - docs/06-websocket-api.md
tags: [tech-debt, api]
---

## Контекст

После фикса детерминизма в T-008 `openapi.json` полностью стабилен, но `asyncapi.json` иногда меняется на ~6 строк между прогонами `./gradlew generateApiSnapshots`. Причина — springwolf добавляет схему `SpringStompDefaultHeaders` в `components.messages`, но не на каждом прогоне (зависит, видимо, от JVM class loading order у STOMP-обвязки).

Пример дельты:
```json
+  "SpringStompDefaultHeaders" : {
+    "examples" : [ { } ],
+    "properties" : { },
+    "title" : "SpringStompDefaultHeaders",
+    "type" : "object"
+  },
```

Это подрывает всю идею «снапшоты в git → drift виден в PR diff»: маленький шум на каждом прогоне создаёт cognitive load и приводит к тому, что реальные изменения теряются в косметике.

## Acceptance criteria

- [ ] `./gradlew generateApiSnapshots` дважды подряд даёт 0 строк diff в `asyncapi.json`.
- [ ] Продуктовые каналы/сообщения по-прежнему на месте (все 11 каналов, все payload схемы DTO).

## План

1. Воспроизвести нестабильность (запуск раз 5-10 подряд, посмотреть частоту появления).
2. Разобраться где именно рождается `SpringStompDefaultHeaders`:
   - Либо в самом springwolf-stomp — тогда filter/customizer.
   - Либо от нашего кода (например, `@Payload String::class` на admin-контроллерах?) — тогда изменить payloadType на что-то другое или удалить лишние заголовки.
3. Если springwolf'овская — либо `AsyncApiCustomizer` (по аналогии с `OpenApiCustomizer.filterInternalPaths`) вырезает `SpringStompDefaultHeaders`, либо конфиг `springwolf.*` отключает scan default headers.
4. Регенерировать снапшот, убедиться что diff нулевой.

## Лог

- 2026-07-12: заведена по итогам T-008 (self-retrospective). Обнаружено при проверке детерминизма openapi — попутно замерил asyncapi и увидел ±6 строк шума. Не относилось к T-008, растворилось бы без записи.
- 2026-07-16: попытка воспроизвести. 3 прогона `./gradlew generateApiSnapshots` подряд — 0 диффов между baseline / r1 / r2 / r3. Flake сейчас не воспроизводится (возможно, зафикшено косвенно через T-069/JVM/springwolf-версию, либо низкочастотный). Пауза: приоритет пользователя переключился на новую фичу (NPC-механизм). Следующий шаг когда вернёмся: (а) прогнать 10-20 раз для оценки частоты, (б) если по-прежнему 0 — closed как not-reproducible; если появится — реализовать `AsyncApiCustomizer`.
