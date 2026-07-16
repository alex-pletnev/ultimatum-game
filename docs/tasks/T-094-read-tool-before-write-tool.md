---
id: T-094
title: Read-tool перед Write/Edit обязателен — Bash cat не считается (повтор паттерна)
status: pending
priority: low
created: 2026-07-16
updated: 2026-07-16
related_code:
  - CLAUDE.md
tags: [meta, agent-process]
---

## Контекст

Замечено в self-review T-092 (commit 0512460) и повторно в self-review T-044
(commit ad576a5) — Write в существующий `.claude/settings.json` и Edit в
`CLAUDE.md` провалились с ошибкой «File has not been read yet».

Причина: я читал файл через `Bash cat` (или через `Read`, но давно), а
Write/Edit-tool требуют Read-tool в **той же conversation** непосредственно
перед. Правило описано в Write-tool docs, но легко забывается по автоматизму.

Паттерн повторился 2 раза за 2 задачи подряд → пора закрепить в CLAUDE.md.

## Acceptance criteria

- [ ] `CLAUDE.md` → секция «Стилевые ограничения» или «Что не делать» —
  строка «Перед Write/Edit существующего файла — обязательный Read через
  Read-tool. Bash `cat`/`head` не удовлетворяет требованию».
- [ ] Проверить что не превращается в multi-fold правило.

## План

Micro-edit в CLAUDE.md. Один commit.

## Лог

- 2026-07-16: заведено self-review'ом T-044. Второй повтор паттерна за
  сессию → правило в CLAUDE.md для будущих сессий.
