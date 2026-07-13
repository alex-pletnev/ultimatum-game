---
id: T-054
title: Реализовать AdminGameplayService.abortCurrentRound и pauseRound (сейчас TODO)
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
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

- [ ] `abortCurrentRound(sessionId)`:
  - если `session.currentRound != null` → `roundPhase = ABORTED` (новое значение enum'а, если нужно) или `FINISHED`; `startNextRound` продолжает работать.
  - публикация `publishRoundStatus`.
  - если сессия не running или round уже FINISHED → 409 через `IllegalStateException` / кастомный exception.
- [ ] Решить `pauseRound`: реализовать или явно вернуть 501/убрать endpoint.
- [ ] Тесты для позитивных и негативных путей.
- [ ] Обновить `docs/03-state-machines.md` (state-machine round'а).

## План

1. Обсудить, вводить ли `RoundPhase.ABORTED` (стоит).
2. Реализовать `abortCurrentRound`, добавить hook в `PlayerGameplayService.sendOffer`/`makeDecision` — не принимать при ABORTED/PAUSED.
3. Пропустить `pauseRound` в v1 (`throw NotImplementedError` → `NotImplementedException` → 501) — заведём отдельно если нужно.
4. Тесты + snapshot regen.

## Лог

- 2026-07-13: заведено из frontend-readiness audit'а. Priority medium — фронт не сломается без функции (кнопка не появится), но админ-flow неполный.
