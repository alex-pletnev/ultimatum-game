---
id: T-072
title: GET /session/{id}/current-round отдаёт 500 NPE — MapStruct передаёт null в non-null myRole
status: done
priority: high
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/RoundResponse.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/util/RoundMapper.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt
related_docs:
  - frontend-integration/06-data-models.md
tags: [bug, mapstruct, kotlin-null-safety, high]
---

## Контекст

Обнаружено фронтом (`alex-pletnev/ultimatum-game-ui`, T-012). Спека:
https://github.com/alex-pletnev/ultimatum-game-ui/blob/main/BACKEND-FIX-current-round-npe.md

Симптом: `GET /session/{id}/current-round` → 500. Стек:
```
NullPointerException: Parameter specified as non-null is null:
method RoundResponse.<init>, parameter myRole
```

Root cause: `RoundResponse` — Kotlin `data class` с `myRole: MyRole = MyRole.NONE` и
`myPendingActions: List<PendingAction> = emptyList()` в первичном конструкторе. Kotlin
default'ы работают только когда параметр не передан. MapStruct генерит Java-код
(в Java нет defaults), передаёт `null` для этих 2 параметров, вставляемый компилятором
Kotlin `Intrinsics.checkNotNullParameter(myRole, "myRole")` падает NPE.

Проверил: `build/generated/source/kapt/main/edu/itmo/ultimatumgame/util/RoundMapperImpl.java`:
```java
MyRole myRole = null;
List<PendingAction> myPendingActions = null;
RoundResponse roundResponse = new RoundResponse(id, ..., session, myRole, myPendingActions);
```

Почему не всплыло в unit-тестах: везде `roundMapper.toDto(...)` мокается через mockk
(`every { roundMapper.toDto(r) } returns dto`), реальный маппер не вызывается.

## Acceptance criteria

- [ ] `GET /session/{id}/current-round` возвращает 200 для authenticated ADMIN'а
  (не в `members`) — с `myRole: "NONE"` и `myPendingActions: []`.
- [ ] `GET /session/{id}/rounds` то же самое.
- [ ] Unit-тест на реальный `roundMapper.toDto(round)` (не mockk) — вызывает MapStruct,
  проверяет что `myRole == NONE` и `myPendingActions == []` для round'а без hints.
  Это RED-фаза: до fix'а тест падает с NPE.
- [ ] `./gradlew check` зелёный.
- [ ] Контракт `myRole: MyRole` не-nullable в клиентском JSON сохраняется
  (frontend-integration/06 обещал что всегда non-null).

## План

1. **Fix**: вынести `myRole` и `myPendingActions` из primary constructor `RoundResponse`
   в тело класса как `var` с default'ами. MapStruct больше не увидит их в конструкторе
   → не передаст null.
2. Переписать `SessionService.enrichWithHints`: `dto.copy(myRole = role, ...)` →
   прямое присвоение `dto.myRole = role; dto.myPendingActions = actions`.
3. Добавить unit-тест `RoundMapperTest` — вызывает `RoundMapperImpl` напрямую с реальным
   Round-объектом. Проверяет: `dto.myRole == NONE`, `dto.myPendingActions.isEmpty()`.
4. Убедиться что Jackson serialization продолжает включать эти поля (важно для контракта).
5. `./gradlew check`.

## Лог

- 2026-07-16: заведено спекой фронта. Root cause диагностирован (MapStruct + Kotlin
  data class defaults). High-priority — блокирует все игровые экраны фронта.
- 2026-07-16: закрыто. TDD-цикл выполнен полностью:
  1. **RED**: написан `RoundMapperTest.toDto — myRole default NONE, myPendingActions default
     emptyList`. Вызывает реальный `RoundMapperImpl` (не mockk) с рефлективной инъекцией
     nested-мапперов. Прогон до fix'а — `NPE at RoundMapperTest.kt:67` (строка
     `mapper.toDto(r)`), баг воспроизведён на unit-уровне.
  2. **GREEN**: `myRole` и `myPendingActions` вынесены из primary constructor в тело
     `RoundResponse` как `var` с default'ами. MapStruct теперь не видит их в конструкторе,
     Kotlin применяет default'ы. `SessionService.enrichWithHints` переписан с `dto.copy(...)`
     на прямое присвоение (`dto.myRole = role; dto.myPendingActions = actions`).
  3. **Bonus fix**: `RoundMapper.toEntity` удалён (был dead code — MapStruct падал на нём
     от несовпадения `SessionPrewResponse.config: SessionConfigResponse` vs
     `Session.config: SessionConfig`; kapt-регенерация после моих правок вскрыла эту
     проблему).
  4. `./gradlew check` — 240+1 (новый тест) зелёные. AsyncAPI/OpenAPI перегенерированы
     (schema не поменялась — оба поля остались в JSON-serialization).
