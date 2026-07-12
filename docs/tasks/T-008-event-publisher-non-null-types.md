---
id: T-008
title: Убрать nullable-параметры и runtime error(...) в EventPublisherService
status: pending
priority: low
created: 2026-07-12
updated: 2026-07-12
related_code:
  - src/main/kotlin/edu/itmo/ultimatum_game/services/EventPublisherService.kt
related_docs:
  - docs/04-services.md
tags: [tech-debt, refactor]
---

## Контекст

Все 5 методов `EventPublisherService` (`publishOfferCreated`, `publishOfferToPlayer`, `publishDecisionMade`, `publishRoundStatus`, `publishSessionStatus`) принимают nullable параметры (`sessionId: UUID?`, `offer: Offer?` и т.п.) и в теле метода делают ручную проверку через `error("... не может быть null на этом этапе")`.

По факту (проверил callsites — `AdminGameplayService`, `PlayerGameplayService`, `RoundLifecycleService`) — ни в одном месте эти аргументы не приходят null, потому что вызывающий код только что достал сущности из БД / только что их создал. Nullable в сигнатуре — историческое, не отражает контракт.

Правильно: сделать параметры non-null. Kotlin компилятор поймает потенциальный `null` на этапе компиляции у любого будущего callsite. Runtime-проверки `error(...)` уйдут.

## Acceptance criteria

- [ ] Все 5 методов `EventPublisherService` — non-null параметры (`UUID`, `Offer`, `Decision`, `Round`, `Session`).
- [ ] Ни одного `error(...)` в теле методов.
- [ ] Все callsites компилируются без warnings/предупреждений о nullable-типах.
- [ ] `./gradlew test` зелёный.
- [ ] `./gradlew test --tests "*.SpecSnapshotGeneratorTest"` — openapi/asyncapi снапшоты не меняются (изменение чисто внутреннее, контракт стабилен).

## План

1. Изменить сигнатуры 5 методов `EventPublisherService.kt` — убрать `?` из типов параметров.
2. Удалить `if (... != null)` и `error(...)` из тел; оставить только payload happy-path.
3. Пройти по callsites — компилятор укажет места, где вызывают с nullable значением. В таких местах либо `?.let { publisher.publishX(...) }`, либо `requireNotNull` в самом callsite (в зависимости от контекста).
4. Прогнать тесты + регенерацию снапшотов.

## Лог

- 2026-07-12: заведена автоматически в ходе T-006 (self-retrospective). Заметил при чтении `EventPublisherService` для добавления `@AsyncPublisher`. Не относилось к T-006, но растворится без записи.
