---
id: T-063
title: FreeForAllStrategy.shuffleOffers — бесконечный цикл при неудачном RNG (derangement bug)
status: pending
priority: high
created: 2026-07-14
updated: 2026-07-14
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/model/ShuffleStrategy.kt
  - src/test/kotlin/edu/itmo/ultimatumgame/model/FreeForAllTest.kt
related_docs:
  - docs/07-domain-model.md
tags: [bug, gameplay, tech-debt]
---

## Контекст

`FreeForAllStrategy.shuffleOffers` (`ShuffleStrategy.kt:14-28`):

```kotlin
val responders = copyOf(session.members).toMutableSet()
round.offers.forEach {
    var responder: User
    do {
        responder = responders.random()
    } while (responder.id == it.proposer?.id)
    responders.remove(responder)
    it.responder = responder
}
```

Классический баг вероятностного derangement'а: когда на последней итерации в `responders` осталась ровно одна кандидатура — сам proposer текущего оффера, `do-while` крутит бесконечно, потому что `responders.random()` всегда возвращает того же пользователя.

Обнаружено при прогоне `./gradlew check` в T-056 — Test worker застрял на 6+ минутах (CPU 102%) в `FreeForAllTest.kt:81 responder — участник session_members, не какой-то посторонний`. Thread dump подтвердил цикл в `ShuffleStrategy.kt:23`. Test был probabilistic-flaky, но раньше везло; после kill'а и retry прошёл.

## Acceptance criteria

- [ ] `FreeForAllStrategy.shuffleOffers` гарантированно терминирует за конечное число шагов при `n >= 2` (derangement всегда существует для `n >= 2`).
- [ ] Тест `FreeForAllTest.kt:81` детерминированно проходит на 1000 подряд запусках (или ×100 через `@RepeatedTest`).
- [ ] Кейс `n = 1` (session с одним игроком) — не должен приводить к shuffleOffers (проверить что вызывающая логика такое не допускает); если допускает — `check`/exception.

## План

1. Заменить алгоритм на bounded-retry с fisher-yates shuffle (~1/e wasted attempts, но max 100 попыток) ИЛИ на конструктивный derangement (например, «shift by 1 после случайной перестановки»).
2. Добавить `@RepeatedTest(100)` на `responder — участник session_members` для регресс-защиты.
3. Проверить, что `TeamBattleStrategy` не имеет аналогичного бага (там filter'ы, but check логику).

## Лог

- 2026-07-14: заведено во время T-056. Обнаружено thread dump'ом застрявшего test worker'а. Priority high — блокирует dev-experience, любой прогон `./gradlew check` может нарваться на flake и повесить CI/локальную сессию на 10+ минут.
