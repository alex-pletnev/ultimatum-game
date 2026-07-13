---
id: T-045
title: Сверить реализованные правила gameplay с канонической Ultimatum Game (Википедия) + многопользовательские адаптации
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/PlayerGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AdminGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/
related_docs:
  - docs/03-state-machines.md
  - docs/04-services.md
tags: [test, verification, gameplay]
---

## Контекст

Проект реализует «многопользовательскую» вариацию Ultimatum Game (модель FREE_FOR_ALL и TEAM_BATTLE), но canonical rules не задокументированы, и нет систематической сверки:

- Каноническая UG (Wikipedia): 2 игрока, фиксированная сумма, proposer предлагает split, responder accept/reject. Reject → оба получают 0. Accept → распределение по offer'у.
- Что делает наш проект: N игроков, N раундов, распределение ролей через shuffle-стратегии (см. `services/shuffle/*`), два режима — FREE_FOR_ALL (индивидуальные баллы) и TEAM_BATTLE (агрегация по командам).

Нужно (a) записать сравнение canonical vs наша реализация, (b) написать интеграционные тесты по каждому сценарию, включая edge case'ы (accept/reject/no-decision-yet/team boundaries/shuffle deterministicness).

## Acceptance criteria

- [ ] `docs/03-state-machines.md` дополнен разделом «Canonical Ultimatum Game (Wikipedia) → наша адаптация» с явной таблицей отличий (2-player vs N-player, single-round vs multi-round, individual vs team).
- [ ] Написаны end-to-end сценарные тесты (`@SpringBootTest` или через `FreeForAllTest` / `TeamBattleTest`) для:
  - accept/reject правила scoring'а (после T-003).
  - все shuffle-стратегии выдают ожидаемые pairings.
  - session finish при последнем decision последнего раунда.
  - TEAM_BATTLE — team totals корректны.
- [ ] Найденные расхождения с canonical rules — либо исправлены, либо задокументированы как осознанные адаптации.

## План

1. Прочитать Wikipedia Ultimatum Game (RU + EN) — записать canonical rules.
2. Сравнить с текущей реализацией (walk через `PlayerGameplayService`, `AdminGameplayService`, `SessionService`, shuffle-стратегии).
3. Заполнить сравнительную таблицу в docs.
4. Найти нехватку тестов — дописать.
5. Прогнать `./gradlew check` — все зелёные.
6. Расхождения → или fix, или отметить в `docs/11-known-gaps.md` как осознанные.

## Лог

- 2026-07-13: заведено по идее пользователя во время обсуждения T-003 («проверить что gameplay работает корректно, сверить с Wikipedia»). Priority medium. Логически идёт после T-003 (scoring должен быть на месте, чтобы тестировать правила подсчёта).
