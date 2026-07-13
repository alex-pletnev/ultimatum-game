---
id: T-049
title: Portability check — абсолютные пути и machine-specific настройки не коммитятся в общие файлы
status: pending
priority: low
created: 2026-07-13
updated: 2026-07-13
related_code:
  - CLAUDE.md
  - .claude/skills/self-review.md
related_docs:
  - docs/tasks/T-046-optimize-gradle-check-time.md
tags: [meta, harness, skills]
---

## Контекст

В T-046 закоммитил в проектный `gradle.properties` строку `org.gradle.java.home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home` — абсолютный macOS+brew+Apple Silicon путь. Работало на моей машине, но сломало бы Linux CI, Intel Mac и любого другого разработчика. Правильное место для user-machine-специфичных настроек — `~/.gradle/gradle.properties` (user-scope, не commit'нутое) или переменная окружения.

Обнаружено self-review'ом commit'а `d3e94c0`. Категория E (улучшения меня): классическая ошибка «работает у меня — коммитим».

## Acceptance criteria

- [ ] В CLAUDE.md (или в `self-review.md`) явно указать: любой абсолютный path (`/opt/`, `/Users/`, `C:\`) или machine-specific setting (JDK путь, `docker.host`, локальный IP) в commit'нутом файле — red flag. Проверить перед commit'ом либо через `git diff --cached` grep, либо через self-review категорию B.
- [ ] Обновить `.claude/skills/self-review.md` категория B — добавить чек «absolute path grep в diff'е».
- [ ] Порт в setup-agent-harness references.

## План

1. Дописать в `self-review.md` категорию B — pattern-check для абсолютных путей.
2. Дописать в CLAUDE.md «Что не делать» правило про portability.
3. Портировать в harness-репо.
4. Обкатать на следующей задаче с изменениями в build-конфигах.

## Лог

- 2026-07-13: заведено из self-review T-046 (commit d3e94c0). Категория E. Priority low — паттерн повторяющийся, но не критичный (баг был бы отловлен CI/другим разработчиком до релиза).
