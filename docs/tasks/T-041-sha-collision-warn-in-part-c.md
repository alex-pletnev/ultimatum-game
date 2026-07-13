---
id: T-041
title: Часть C — warn при sha256-коллизии между recommended hook'ами
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/harness-update.md
  - ~/.claude/skills/setup-agent-harness/references/skills/harness-update.md
related_docs:
  - docs/superpowers/specs/2026-07-13-harness-update-settings-json-sync-design.md
tags: [tech-debt, harness]
---

## Контекст

Spec Части C `/harness-update` упоминает: «Одинаковые command'ы у двух recommended hooks — теоретически sha-collision. Практически исключено. Если возникнет — при apply двум ключам state пишется одна sha → warn». Но в самом алгоритме шаги Classify/Apply не описывают, где и как этот warn выдаётся.

Пока в `references/settings-hooks/` один файл (`self-review-reminder.json`) — риск нулевой. Актуальность растёт с каждым новым recommended hook'ом.

## Acceptance criteria

- [ ] Добавить в шаг Classify (Часть C) обнаружение collision: при чтении references считать recommended_sha для каждого; если два `id`'а дают одинаковый sha — выдать `⚠ SHA COLLISION: <id1>, <id2> имеют одинаковый canonical hook`.
- [ ] Явно определить fallback-поведение: skip обоих или apply один с предупреждением. Рекомендация: skip обоих с явным сообщением и попросить пользователя разрулить.
- [ ] Правки применить и в `.claude/skills/harness-update.md` (проект), и в `references/skills/harness-update.md` (harness-репо).

## План

1. Уточнить fallback (skip vs apply один) — записать в spec addendum или прямо в skill.
2. Добавить сравнение sha при Discover.
3. Обновить summary-шаблон в skill'е — добавить блок «⚠ SHA COLLISION».
4. Ручная проверка: создать два JSON'а с одинаковым `hook`-объектом, прогнать `/harness-update`, убедиться в warn.

## Лог

- 2026-07-13: заведено из self-review T-039. Категория D. Priority low — пока набор из одного hook'а, collision невозможен.
