---
id: T-074
title: WebSocketSecurityMatcherAuditTest — учитывать block-комментарии и ловить dead matcher'ы
status: pending
priority: low
created: 2026-07-16
updated: 2026-07-16
related_code:
  - src/test/kotlin/edu/itmo/ultimatumgame/configs/WebSocketSecurityMatcherAuditTest.kt
related_docs:
  - docs/tasks/T-071-security-matcher-completeness-check.md
tags: [tech-debt, test, security-config]
---

## Контекст

T-071 добавил audit-тест, который проверяет что каждый `@MessageMapping` / `@SendToUser`
destination покрыт matcher'ом в `WebSocketSecurityConfig`. Два известных ограничения:

1. **`/* ... */` block-комментарии не отфильтрованы**. Тест удаляет только `//`-строчные
   комментарии. Если кто-то закомментирует matcher через block-comment — тест vacuously
   true (matcher будет parsed как активный).

2. **Dead-matcher detection нет**. Обратное направление: если matcher есть, но
   соответствующий endpoint удалён — matcher останется мёртвым. Тест сейчас
   проверяет только «каждый endpoint покрыт», не «каждый matcher нужен».

Оба недостатка не создают текущих рисков (в проекте нет block-комментариев в конфиге,
dead-matcher — не безопасностный баг). Но при следующей эволюции кода могут проявиться.

## Acceptance criteria

- [ ] Regex-парсер patterns'ов удаляет `/* ... */` block-комментарии перед сканом
  (или переехать на Kotlin-AST через `kotlin-compiler-embeddable` — overkill, но
  надёжнее).
- [ ] Тест добавляет второй assertion: каждый extracted matcher-pattern должен
  соответствовать хотя бы одному endpoint или @SendToUser destination
  (dead-matcher detection).
- [ ] Ретро-проверка обоих сценариев: (а) закомментировать matcher block-стилем
  — тест падает; (б) добавить лишний matcher — тест падает как dead.

## План

1. В `extractMatcherPatterns` перед line-based `//`-удалением сделать regex-удаление
   `/\*.*?\*/` (multiline).
2. Добавить в тест обратный проход: для каждого extracted pattern — проверить что
   хотя бы один normalized destination матчится.
3. Ретро-проверка.

## Лог

- 2026-07-16: заведено из self-review T-071 (commit 11fd68c). Priority low —
  оба недостатка не создают текущих рисков, но test-audit неполный.
