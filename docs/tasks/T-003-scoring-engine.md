---
id: T-003
title: Реализовать расчёт баллов игроков по итогам раундов
status: pending
priority: high
created: 2026-07-12
updated: 2026-07-12
related_code:
  - src/main/kotlin/edu/itmo/ultimatum_game/services/PlayerGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/AdminGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/services/StatsService.kt
  - src/main/kotlin/edu/itmo/ultimatum_game/model/
related_docs:
  - docs/03-state-machines.md
  - docs/04-services.md
  - docs/11-known-gaps.md
tags: [feature, gameplay, api, db]
---

## Контекст

Сейчас `Offer` и `Decision` только фиксируются, но **баланс игроков не считается**. Клиент вынужден считать очки сам. Для полноценного эксперимента нужна серверная сторона правды:

- При accept: proposer получает `roundSum - offer.amount`, responder получает `offer.amount`.
- При reject: обе стороны получают 0.
- В TEAM_BATTLE — суммируется по игрокам и по командам.

Источник: `docs/11-known-gaps.md` → «Нет расчёта баллов/выплат».

## Acceptance criteria

- [ ] Определена доменная модель «баланса»: либо новый `Score`/`RoundResult` entity, либо агрегация на лету в `StatsService` (обсудить перед реализацией).
- [ ] По завершении раунда (`ALL_DECISIONS_RECEIVED` или переход в `FINISHED`) баллы вычисляются и, при выборе персистентного варианта, сохраняются.
- [ ] `StatsService.getSessionStats` возвращает итоговый счёт каждого игрока и (для TEAM_BATTLE) команды.
- [ ] CSV-экспорт (`CsvService`) содержит финальные итоги.
- [ ] Публикуется соответствующее WS-событие (например, `scoreUpdated` или расширение `roundStatus`).
- [ ] Обновлены `docs/03-state-machines.md`, `docs/04-services.md`, `docs/06-websocket-api.md`.

## План

1. **Спроектировать модель** (обсуждение с пользователем):
   - Вариант A: on-the-fly агрегация — просто и без миграции, но повторные вычисления.
   - Вариант B: `RoundResult` entity — быстрее чтение, но нужны миграция и синхронизация.
2. Реализовать расчёт по выбранной модели.
3. Интегрировать в `PlayerGameplayService.makeDecision` (при последнем решении) или в `AdminGameplayService.startNextRound` (при закрытии раунда) — обсудить.
4. Расширить DTO статистики (`StatsDtos.kt`) и CSV.
5. Добавить WS-событие в `EventPublisherService`.
6. Написать юнит-тест на правила подсчёта (accept / reject / TEAM_BATTLE).
7. Обновить docs.

## Лог

- 2026-07-12: заведена из `docs/11-known-gaps.md`. Требуется предварительное обсуждение выбора модели (A vs B).
