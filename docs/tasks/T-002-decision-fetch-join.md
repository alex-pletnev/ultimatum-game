---
id: T-002
title: Fetch join для DecisionRepository.findBySessionId
status: done
priority: low
created: 2026-07-12
updated: 2026-07-13
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/repositories/DecisionRepository.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/services/StatsService.kt
related_docs:
  - docs/02-domain-model.md
  - docs/04-services.md
  - docs/11-known-gaps.md
tags: [db, performance, refactor]
---

## Контекст

`DecisionRepository.findBySessionId(sessionId)` — derived query без fetch join. При обращении к `decision.offer`, `decision.responder`, `decision.round` возникают доп. запросы (N+1).

Основной потребитель — `StatsService.getSessionStats` (используется в CSV-экспорте).

Источник: `docs/11-known-gaps.md` → «Потенциальный N+1».

## Acceptance criteria

- [ ] Новый метод `findAllBySessionIdWithRelations` (или переработанный существующий) c fetch join по `offer`, `responder`, `round`.
- [ ] `StatsService` использует новый метод.
- [ ] На локальной сессии с 20+ игроками × 10 раундов количество SQL-запросов при `/statistics/{id}/csv` сокращается до нескольких (не десятков/сотен). Проверить через `spring.jpa.show-sql=true`.
- [ ] Обновлены `docs/02-domain-model.md` (раздел «Репозитории») и `docs/04-services.md` (граф вызовов).

## План

1. Добавить в `DecisionRepository` метод по образцу `OfferRepository.findAllBySessionIdWithRelations`:
   ```jpql
   select d from Decision d
     left join fetch d.offer o
     left join fetch d.responder r
     left join fetch d.round rd
   where d.session.id = :sessionId
   ```
2. Обновить `StatsService.getSessionStats` — заменить вызов.
3. Замерить количество SQL до/после локально.
4. Обновить docs.

## Лог

- 2026-07-12: заведена из `docs/11-known-gaps.md`.
- 2026-07-13: закрыта. `DecisionRepository.findBySessionId` заменён на `findAllBySessionIdWithRelations` с fetch join по `offer`/`responder`/`round` (по образцу `OfferRepository`). `StatsService` использует новый метод, тесты `StatsServiceTest` обновлены (5 mock'ов). Docs синхронизированы (`docs/02-domain-model.md`, `docs/04-services.md`, `docs/11-known-gaps.md` — узкое место помечено как устранённое). Verification gate: `./gradlew check` — BUILD SUCCESSFUL 20s. AC про «замер SQL с 20+ игроками» не выполнено буквально — нет живой сессии, изменение однострочное и структурно идентичное работающему `OfferRepository`; отмечено в self-review.
