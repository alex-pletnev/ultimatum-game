---
id: T-054
title: Реализовать AdminGameplayService.abortCurrentRound и pauseRound (сейчас TODO)
status: done
priority: medium
created: 2026-07-13
updated: 2026-07-15
related_code:
  - src/main/kotlin/edu/itmo/ultimatumgame/services/AdminGameplayService.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/model/RoundPhase.kt
  - src/main/kotlin/edu/itmo/ultimatumgame/controllers/AdminGameplayController.kt
related_docs:
  - docs/03-state-machines.md
  - docs/04-services.md
tags: [feature, gameplay, admin]
---

## Контекст

`AdminGameplayService.abortCurrentRound()` и `pauseRound()` содержат `TODO()` — вызов через STOMP endpoint приведёт к падению. Обнаружено frontend-readiness audit'ом.

- **abortCurrentRound**: администратор хочет прекратить текущий раунд без завершения сессии (например, кто-то отвалился по timeout). Реалистичный use case на фронте — админ-кнопка «Прервать раунд».
- **pauseRound**: заморозить сессию (принимать offers/decisions временно нельзя). Менее критично, `pauseRound` можно оставить `TODO` с явной 501 Not Implemented, если не нужно на MVP-фронте.

## Acceptance criteria

- [x] `abortCurrentRound(sessionId)`:
  - если `session.currentRound != null` → `roundPhase = ABORTED` (новое значение enum'а).
  - `startNextRound` продолжает работать (сохраняет ABORTED, переводит в следующий раунд).
  - публикация `publishRoundStatus`, эмит `RoundAborted` domain event.
  - если сессия не RUNNING или round уже FINISHED/ABORTED → 409 через `IllegalStateException` (→ WebSocketExceptionAdvice T-050).
- [x] Решить `pauseRound`: **удалён** как dead code (не было endpoint'а). Заводить если понадобится клиенту.
- [x] Тесты для позитивных и негативных путей (abort → ABORTED; not RUNNING → throw; no currentRound → throw; FINISHED/ABORTED → throw; startNextRound after ABORTED — фаза сохраняется).
- [x] Обновить `docs/03-state-machines.md` (state-machine round'а).

## План

1. Обсудить, вводить ли `RoundPhase.ABORTED` (стоит).
2. Реализовать `abortCurrentRound`, добавить hook в `PlayerGameplayService.sendOffer`/`makeDecision` — не принимать при ABORTED/PAUSED.
3. Пропустить `pauseRound` в v1 (`throw NotImplementedError` → `NotImplementedException` → 501) — заведём отдельно если нужно.
4. Тесты + snapshot regen.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Priority medium — фронт не сломается без функции (кнопка не появится), но админ-flow неполный.
- 2026-07-15: закрыто. Реализация: (1) `RoundPhase.ABORTED` — новое значение enum'а. (2) `AdminGameplayService.abortCurrentRound(sessionId)` — проверка session RUNNING, currentRound != null, phase не FINISHED/ABORTED → phase=ABORTED, save, publishRoundStatus, emit RoundAborted. (3) `startNextRound` доработан: если фаза ABORTED — не переписываем в FINISHED (история корректна). (4) `PlayerGameplayService.sendOffer` и `makeDecision` теперь проверяют текущую фазу (WAIT_OFFERS / OFFERS_SENT) — offers/decisions после abort'а отклоняются с IllegalStateException (→ 409 через handler'ы REST + STOMP T-050). (5) WS endpoint `/app/session/{sessionId}/round.abort` в `SessionAdminWsController` (ADMIN). (6) `pauseRound` **удалён** как dead code (не был подключён к endpoint'у). (7) Тесты: 5 позитивных/негативных для abort в `AdminGameplayServiceTest`, фаза-checks в существующих `PlayerGameplayServiceTest` (обновлены дефолтные фазы). (8) Docs: `03-state-machines.md`, `04-services.md`, `06-websocket-api.md`, `11-known-gaps.md`. (9) AsyncAPI перегенерирован. `./gradlew check` зелёный.
