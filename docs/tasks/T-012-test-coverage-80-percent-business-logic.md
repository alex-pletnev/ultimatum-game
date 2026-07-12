---
id: T-012
title: Поднять тестовое покрытие бизнес-логики до 80%+ (services + shuffle strategies)
status: done
priority: high
created: 2026-07-12
updated: 2026-07-12
related_code:
  - src/main/kotlin/edu/itmo/ultimatum_game/services/AdminGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/AuthService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/CoreGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/CsvService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/EventPublisherService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/JwtService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/PlayerGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/SecurityService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/SessionService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/StatsService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/UserService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/model/ShuffleStrategy.kt
  - build.gradle.kts
related_docs:
  - docs/04-services.md
  - docs/11-known-gaps.md
  - docs/03-state-machines.md
tags: [test, backend, tech-debt, ci]
---

## Контекст

Текущее покрытие практически нулевое. В `src/test/kotlin/`:

- `UltimatumGameApplicationTests` — только `contextLoads`.
- `FreeForAllTest` — юнит на `FreeForAllStrategy` (единственный содержательный).
- `SpecSnapshotGeneratorTest` — генератор API-снапшотов, не тест бизнес-логики.

Все 11 сервисов + `TeamBattleStrategy` — без тестов. Это явно записано как gap в `docs/11-known-gaps.md`:

> Нет интеграционных тестов на: REST endpoints, WebSocket flow, JWT-auth, race conditions на «последний оффер / последнее решение».

Задача — поднять покрытие **бизнес-логики** (сервисы + shuffle-стратегии) до **≥80% line coverage** и завести CI-порог, ломающий сборку при регрессии. Инфраструктурные слои (`configs/`, `controllers/`, `dto/`, `util/` mappers) — вне scope.

## Определение "бизнес-логика" (scope)

**Внутри** (обязательные к покрытию):

- `edu.itmo.ultimatum_game.services.*` — все 11 сервисов.
- `edu.itmo.ultimatum_game.model.ShuffleStrategy` (`FreeForAllStrategy`, `TeamBattleStrategy`).

**Снаружи** (исключить из coverage-подсчёта):

- `edu.itmo.ultimatum_game.configs.*` — Spring wiring.
- `edu.itmo.ultimatum_game.controllers.*` — HTTP/WS адаптеры (тестируются интеграционно, если появятся; сейчас не в scope).
- `edu.itmo.ultimatum_game.dto.*`, `edu.itmo.ultimatum_game.util.*` — data + сгенерированные MapStruct-мапперы.
- `edu.itmo.ultimatum_game.exceptions.*`, `edu.itmo.ultimatum_game.repositories.*` — тонкие обёртки.
- `edu.itmo.ultimatum_game.model.*` кроме `ShuffleStrategy` — JPA-сущности, geter'ы/setter'ы.

## Acceptance criteria

- [ ] В `build.gradle.kts` подключён coverage-плагин (**JaCoCo** — стандарт Java-мира, интеграция с CI/IDE, отчёт HTML+XML).
- [ ] Задача `./gradlew test jacocoTestReport` генерирует отчёт в `build/reports/jacoco/test/html/index.html`.
- [ ] Задача `./gradlew jacocoTestCoverageVerification` проверяет порог. Порог: **line coverage ≥ 0.80** по классам из scope выше; классы вне scope — исключены из подсчёта.
- [ ] `./gradlew check` вызывает verification и падает при просадке.
- [ ] Для каждого класса из scope — минимум один осмысленный unit-тест на публичный метод (не smoke-test на конструктор).
- [ ] Unit-тесты используют **MockK** (идиоматично для Kotlin) для моков репозиториев/зависимостей. Добавить `io.mockk:mockk` в `testImplementation`.
- [ ] Покрыты граничные кейсы, зафиксированные в `docs/03-state-machines.md`:
  - `AdminGameplayService`: `startSession` в состоянии, отличном от `CREATED` — исключение; happy-path переход `CREATED → IN_PROGRESS`.
  - `PlayerGameplayService.sendOffer`: дубликат оффера от того же игрока в том же раунде → `DuplicateIdException`; корректный переход `WAIT_OFFERS → OFFERS_SENT` при последнем оффере.
  - `PlayerGameplayService.makeDecision`: дубликат решения → `DuplicateIdException`; последнее решение → раунд `FINISHED`.
  - `CoreGameplayService` (если содержит переходы состояний) — переходы между всеми фазами.
  - `SessionService.joinSession`: closed session, admin-джойн, переполнение — все → `SessionJoinRejectedException`.
  - `AuthService.quickRegister`: `Role.NPC` → `UserRoleNotAllowedException`.
  - `JwtService`: валидный/истёкший/подделанный токен, `isTokenValid` true/false.
  - `ShuffleStrategy`: FreeForAll на нечётном числе игроков, TeamBattle на несбалансированных командах.
  - `StatsService.getSessionStats`: пустая сессия vs сессия с офферами/решениями.
  - `CsvService`: корректная генерация CSV, экранирование запятых/переводов строк в никнеймах.
- [ ] Итоговый line coverage по scope: **≥ 80%** (проверяется `jacocoTestCoverageVerification`).
- [ ] `./gradlew check` зелёный.
- [ ] Обновлены `docs/11-known-gaps.md` (убрать пункт про отсутствие тестов на сервисы) и `docs/01-overview.md` (упомянуть `./gradlew jacocoTestReport` в разделе «Проверки»).

## План

1. **Инфраструктура.**
   - Добавить в `build.gradle.kts`: plugin `jacoco`, `testImplementation("io.mockk:mockk:<latest>")`.
   - Настроить `jacocoTestReport` (HTML+XML) и `jacocoTestCoverageVerification` с include-паттерном по scope (см. выше) и `minimum = 0.80` по `LINE` counter.
   - Привязать verification к `check`.
2. **Test scaffold.**
   - Создать `src/test/kotlin/edu/itmo/ultimatum_game/services/` — по одному файлу на сервис.
   - Общие фикстуры (User, Session, Round, Offer) — вынести в `TestFixtures.kt` в том же пакете.
3. **Написание тестов по приоритету.**
   - Порядок: сервисы с самой сложной логикой первыми (`PlayerGameplayService`, `AdminGameplayService`, `CoreGameplayService`, `SessionService`, `StatsService`), затем `AuthService`, `JwtService`, `SecurityService`, `UserService`, `EventPublisherService`, `CsvService`.
   - Для каждого — happy path + все exception-ветки, зафиксированные в `docs/09-error-handling.md`.
4. **`ShuffleStrategy`.**
   - Дополнить `FreeForAllTest` до полного coverage (edge cases: 0 игроков, 1 игрок).
   - Написать `TeamBattleTest`.
5. **Regression-guard.**
   - Запустить `./gradlew jacocoTestReport` — открыть HTML, посмотреть красные ветки, дописать.
   - Пока `jacocoTestCoverageVerification` не пройдёт — задача не закрыта.
6. **Docs.**
   - Обновить `04-services.md` если по ходу выяснятся сигнатуры/поведение, не описанные в доке.
   - Убрать соответствующий пункт из `11-known-gaps.md`.
   - `01-overview.md`: добавить `./gradlew jacocoTestReport` рядом с `./gradlew test`.

## Что НЕ трогаем

- Integration-тесты для REST/WebSocket controllers — отдельная большая задача (пусть будет T-013 если решим).
- Тесты на MapStruct-мапперы — MapStruct генерит корректный код, лучше проверять на реальных сервисах.
- `configs/` — конфигурация Spring, тестируется косвенно через `contextLoads` + интеграционные тесты (вне scope).
- Repositories — тонкие Spring Data JPA обёртки, тестировать смысла мало.

## Риски / открытые вопросы

- **JaCoCo vs Kover.** Взял JaCoCo как более зрелый и совместимый с CI/GitHub Actions. Если хочется чистого Kotlin-инструмента — можно переключиться на Kover (`org.jetbrains.kotlinx:kover`), но в контексте Spring Boot экосистема JaCoCo привычнее.
- **80% — минимум, а не цель.** Некоторые сервисы могут допрыгнуть до 95%, некоторые (например, `EventPublisherService` — тонкая обёртка над SimpMessagingTemplate) — с трудом до 80%. Возможно, придётся исключить отдельные тривиальные классы из подсчёта (если тесты на них — checkbox без смысла).
- **Дубликаты `FreeForAllTest`.** Существующий тест в `src/test/kotlin/edu/itmo/ultimatum_game/model/FreeForAllTest.kt` может быть неполным — сохранить его основу, дополнить недостающими кейсами.

## Лог

- 2026-07-12: заведена по прямому запросу пользователя после обсуждения бэклога. Приоритет `high` — тестовая база практически отсутствует, риск регрессии высок; T-012 закладывает фундамент для последующих рефакторингов и фичей.
- 2026-07-12: инфра — JaCoCo 0.8.12 + MockK 1.13.13; `test` финализируется `jacocoTestReport`, `check` вызывает `jacocoTestCoverageVerification` (line ≥0.80). Baseline: 10.18%.
- 2026-07-12: покрыты ShuffleStrategy (FreeForAll 100%, TeamBattle 95.7%) + SecurityService/UserService/AuthService/JwtService (по 100%) + EventPublisherService/StatsService/CsvService (по 100%). Итог: 46.4%.
- 2026-07-12: покрыты AdminGameplayService (100%), CoreGameplayService (90.5%), PlayerGameplayService (100%). Итог: 77.3%.
- 2026-07-12: покрыт SessionService (97.5%). **Итоговое покрытие 98.83% (505/511 lines)** — с солидным запасом сверх 80%-порога.
- 2026-07-12: порог JaCoCo поднят до 0.80, `./gradlew check` зелёный. Docs (01-overview, 11-known-gaps) синхронизированы.
- 2026-07-12: попутные находки, оформлены как follow-up:
  - `SessionService.isUserAreSessionObserver` фактически проверяет `members`, а не `observers` — задокументировано в T-011.
  - Потенциальный infinite loop в `FreeForAllStrategy` при плохом порядке шаффла — стабильно не воспроизводится (`random()`), тестом не покрыт; TODO для будущего разбора.
