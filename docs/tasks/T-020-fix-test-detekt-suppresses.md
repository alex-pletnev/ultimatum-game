---
id: T-020
title: Починить detekt-findings в тестах вместо @file:Suppress
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - src/test/kotlin/edu/itmo/ultimatumgame/TestFixtures.kt
  - src/test/kotlin/edu/itmo/ultimatumgame/UltimatumGameApplicationTests.kt
  - src/test/kotlin/edu/itmo/ultimatumgame/SpecSnapshotGeneratorTest.kt
  - src/test/kotlin/edu/itmo/ultimatumgame/services/StatsServiceTest.kt
  - src/test/kotlin/edu/itmo/ultimatumgame/services/PlayerGameplayServiceTest.kt
tags: [tech-debt, tests, detekt]
---

## Контекст

В T-017 при запуске `./gradlew check` вылезли pre-existing detekt-findings в тестах, которые никогда не чинились (T-014/T-015 очистили baseline только для main). Чтобы не расширять скоуп T-017, все они были подавлены через `@file:Suppress(...)`:

| Файл | Правила |
|------|---------|
| `TestFixtures.kt` | `LongParameterList` (4 функции) |
| `UltimatumGameApplicationTests.kt` | `EmptyFunctionBlock` |
| `SpecSnapshotGeneratorTest.kt` | `VarCouldBeVal` |
| `StatsServiceTest.kt` | `NoSemicolons` (6 мест) |
| `PlayerGameplayServiceTest.kt` | `MaxLineLength`/`MaximumLineLength` (длинное имя теста) |

Это косметика, но подавление в `@file:Suppress` — обход, а не фикс.

## Acceptance criteria

- [ ] Убрать `@file:Suppress` из 5 файлов выше.
- [ ] Починить каждый finding по существу:
  - TestFixtures: builder-паттерн вместо long parameter list, или разбиение на несколько узких фикстур.
  - UltimatumGameApplicationTests.contextLoads: тело можно оставить пустым с комментарием, либо перевести на `assertDoesNotThrow`.
  - SpecSnapshotGeneratorTest: `var mockMvc/objectMapper` → `val` (нужно проверить что framework injecting всё-таки работает через `val`).
  - StatsServiceTest: убрать 6 лишних `;`.
  - PlayerGameplayServiceTest: разбить длинное имя теста через `\n` в строке, либо укоротить.
- [ ] `./gradlew check` зелёный.

## Лог

- 2026-07-13: заведена по итогам T-017. Тех-долг чистого type: pre-existing findings, вылезшие только при полноценном прогоне `./gradlew check`.
