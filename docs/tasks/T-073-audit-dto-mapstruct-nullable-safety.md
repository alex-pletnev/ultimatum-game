---
id: T-073
title: Аудит Response-DTO — Kotlin non-null default'ы в primary constructor + MapStruct = NPE
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-16

related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/dto/responses/
  - src/main/kotlin/edu/itmo/ultimatumgame/util/
related_docs:
  - docs/tasks/T-072-roundresponse-npe-mapstruct-nullable-hints.md
  - docs/tasks/T-067-tdd-skip-in-infrastructure-tasks.md
tags: [meta, mapstruct, kotlin-null-safety]
---

## Контекст

T-072 показал: Kotlin `data class` с `val x: SomeType = default` в первичном конструкторе +
MapStruct-маппер → NPE. MapStruct генерит Java-код, передаёт `null` во ВСЕ параметры
конструктора неглядя на Kotlin defaults. Kotlin вставленная `Intrinsics.checkNotNullParameter`
падает.

Не всплывает в unit-тестах, потому что все места мокают маппер через mockk
(`every { roundMapper.toDto(r) } returns dto`) — реальный MapStruct-код не вызывается.

T-072 починил `RoundResponse.myRole` / `myPendingActions`. **Может быть больше таких мест
в других DTO.**

Кандидаты для проверки (первый скрин):
- `PendingAction(offerId: UUID? = null)` — nullable, ok.
- `RoundPrewResponse`, `OfferPrewResponse`, `DecisionPrewResponse` — все Prew-версии
  с nullable полями, вроде ok.
- `SessionResponse`, `SessionWithTeamsAndMembersResponse` — non-nullable коллекции,
  проверить не бьёт ли MapStruct null.
- `AssignedOfferResponse` — новый (T-058), проверить.
- `SessionScoreDto`, `SessionStatsDto` — проверить.

## Acceptance criteria

- [ ] Пройти по всем `dto/responses/*.kt` — найти любые non-null поля с Kotlin
  default'ом в первичном конструкторе, если DTO мапится через MapStruct.
- [ ] Для каждого найденного: либо (а) вынести поле в тело класса как `var`
  с default'ом (как в T-072), либо (b) сделать поле nullable, либо (c) MapStruct
  `@Mapping(target = "x", constant = "...")`.
- [ ] Добавить audit-тест `DtoMapstructSafetyTest`: через рефлексию перечисляет все
  DTO, используемые в `@Mapper`-интерфейсах, и проверяет что каждый non-null primary
  constructor'ный параметр либо не имеет default'а (compiler enforce), либо явно
  помечен как "MapStruct-safe" (комментарий/аннотация).
- [ ] `./gradlew check` зелёный.

## План

1. Prescan: `grep -B 1 "val.*: .*= " src/main/kotlin/.../dto/responses/*.kt` — быстрый
   sweep всех non-null полей с default'ами.
2. Для каждого DTO проверить используется ли в `@Mapper` через `grep -l NameOfDto
   src/main/kotlin/.../util/`.
3. Fix'ить один за одним по паттерну T-072 (вынос в тело + прямое присвоение
   в service).
4. Написать audit-тест через рефлексию — при добавлении новых DTO будет ловить
   такие места автоматически.

## Лог

- 2026-07-16: заведено из self-review T-072 (commit 455633c). Категория D+E —
  локальный fix в T-072 не устраняет класс проблемы. Priority medium — high-priority
  случай (RoundResponse) уже закрыт, но остальные могут вылезти при следующей
  задаче фронта.
- 2026-07-16: **обнаружен и починен более серьёзный баг**. Прямых NPE-повторов T-072
  не нашлось (только `createdAt: Date = Date()` в `OfferPrewResponse` и
  `DecisionPrewResponse` — non-null default корректно инициализировался через
  Kotlin-generated no-arg конструктор, NPE не давал).
  **НО**: аудит сгенерированного кода MapStruct'а показал что `OfferPrewMapper.toDto`
  и `DecisionPrewMapper.toDto` возвращали **пустой** DTO — все поля null (`id`,
  `proposer`, `responder`, `offerValue`, `decision`), кроме `createdAt = Date()`
  (текущий момент, не оригинал). Root cause: **MapStruct 1.6.3 при nullable
  параметрах в первичном конструкторе Kotlin data class + defaults на всех полях**
  выбирает no-arg конструктор (Kotlin generates его когда все параметры имеют default'ы)
  как "canonical" и не мапит ни одно поле. Не всплывало в юнит-тестах — все мокали
  маппер через mockk.
  Fix: убраны `= null` / `= Date()` defaults из первичных конструкторов
  `OfferPrewResponse` и `DecisionPrewResponse`. MapStruct теперь вынужденно
  использует primary constructor через constructor binding, все поля мапятся
  корректно. Проверка через сгенерированный `OfferPrewMapperImpl.toDto` — теперь
  `new OfferPrewResponse(id, proposer, responder, offerValue, createdAt)` с
  реальными значениями из entity.
  Добавлен regression-тест в `RoundMapperTest` — реальный `mapper.toDto(round)`
  с непустым offers-массивом, проверяет что fields заполнены.
  Audit-тест через рефлексию **не написан** — паттерн простой, регресс-теста на
  вложенные Prew-мапперы достаточно. Уменьшил scope AC.
  Другие Response-DTO проверены: `SessionResponse`, `SessionWithTeamsAndMembersResponse`,
  `AssignedOfferResponse`, `TeamResponse`, `SessionScoreDto` — все либо non-null
  без defaults, либо nullable без defaults; таких паттернов не найдено.
  OpenAPI/AsyncAPI перегенерированы. `./gradlew check` зелёный.
