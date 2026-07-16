---
id: T-073
title: Аудит Response-DTO — Kotlin non-null default'ы в primary constructor + MapStruct = NPE
status: pending
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
