---
id: T-011
title: Удалить orphan-методы isUserAreSession* из SessionService
status: pending
priority: low
created: 2026-07-12
updated: 2026-07-12
related_code:
  - src/main/kotlin/edu/itmo/ultimatum_game/services/SessionService.kt
related_docs:
  - docs/04-services.md
tags: [tech-debt, refactor]
---

## Контекст

После T-010 удалён `PlaySessionStompChannelInterceptor`, который был единственным потребителем методов `SessionService.isUserAreSessionAdmin`, `isUserAreSessionMember`, `isUserAreSessionObserver`. Сейчас (`src/main/kotlin/edu/itmo/ultimatum_game/services/SessionService.kt:122-133`) эти три метода мертвы: grep по `src/` не находит других вызовов.

## Acceptance criteria

- [ ] Удалить `isUserAreSessionAdmin`, `isUserAreSessionMember`, `isUserAreSessionObserver` из `SessionService`.
- [ ] Если удаление тянет удаление helper'ов в репозиториях — их тоже вычистить.
- [ ] `./gradlew test` зелёный.
- [ ] Если методы упомянуты в `docs/04-services.md` — синхронизировать.

## План

1. `grep -rn 'isUserAreSession'` → убедиться, что вызовов нет.
2. Удалить три метода.
3. Проверить `SessionRepository` / `SessionService` на осиротевшие вспомогательные вызовы.
4. Тесты, docs-sync.

## Лог

- 2026-07-12: заведена автоматически по итогам ретроспективы T-010. Методы стали orphan после удаления `PlaySessionStompChannelInterceptor`.
