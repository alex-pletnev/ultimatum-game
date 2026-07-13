---
id: T-059
title: Gradle-команды всегда через run_in_background=true; `./gradlew --stop` до подозрительных запусков
status: done
priority: high
created: 2026-07-13
updated: 2026-07-13
related_code:
  - CLAUDE.md
  - ~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md
related_docs:
  - docs/tasks/T-053-my-turn-hints-in-responses.md
tags: [meta, harness, dx]
---

## Контекст

В сессии T-053 я 3 раза подряд писал retro-правила и каждый раз они оказывались неполными:

1. Первый инцидент: pipe `| tail -N` буферизует stdout → Bash-tool в фон → gradle daemon жив → lock не отпущен.
2. Первое правило: «не пайпить». Второй инцидент: `until sleep`-loop тоже без stdout → тот же путь → тот же lock-clash.
3. Второе правило: «не sleep-loop». Третий инцидент: unit-тесты 8s + integration 12s = 20s ≠ full check 5+ мин — оказалось, что **накопленные lock'и из предыдущих попыток** держали новый запуск, а не bug в коде.
4. Третье правило: «один активный gradle-запуск + `ps grep` до запуска».

Каждый следующий fix был reactive. Root cause — не в конкретной технике ожидания, а в том, что **Bash-tool не гарантирует foreground-выполнение для long-running команд с редким stdout**. Любая gradle-команда потенциально уходит в фон.

## Acceptance criteria

- [x] В CLAUDE.md: **любая** gradle-команда (`./gradlew check|test|build|clean|generateApiSnapshots`) вызывается через `run_in_background=true` c явным `> /tmp/*.log 2>&1`. Ждать — **только** через task-notification (не через polling / sleep-loops).
- [x] До подозрительных запусков (после kill'а, после долгого висения, после смены зависимостей) — прогонять `./gradlew --stop` для явного отпускания daemon lock'ов.
- [x] Убрать из CLAUDE.md все reactive-правила предыдущих итераций (три версии за одну сессию) — заменить одним общим блоком.
- [x] Портировать в harness template.
- [x] Пометить как повторяющийся паттерн E-категории в self-review — если наблюдение возникло дважды, приоритет правила поднимается.

## План

1. Переписать секцию «Правила запуска long-running Gradle команд» в CLAUDE.md — общее правило + `--stop` рекомендация.
2. Портировать в harness template как универсальный принцип.
3. Обкатать: в следующей задаче использовать только `run_in_background=true` для gradle.

## Лог

- 2026-07-13: заведено из self-review T-053 (commit 4ead791). Категория E — reactive fixing вместо системного диагноза. Priority high — паттерн блокирует dev-experience, каждая gradle-команда потенциально теряет 5+ минут.
- 2026-07-13: закрыто. (1) В `CLAUDE.md` секция «Правила запуска long-running Gradle команд» переписана — 5 общих пунктов вместо 5 reactive: run_in_background=true + redirect в /tmp, wait только по task-notification, `./gradlew --stop` до подозрительных запусков, `ps grep` check, one active run at a time. (2) В `~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md` — универсальное правило переписано под тот же паттерн (gradle/docker/cargo/npm/sbt/mvn). (3) В `.claude/skills/self-review.md` (и в harness `references/skills/self-review.md`) — категория E расширена пунктом «Reactive patchwork внутри одной сессии» с автоматическим `high`-приоритетом и требованием зафиксировать цепочку итераций.
