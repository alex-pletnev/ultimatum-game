---
id: T-005
title: Добавить .gitignore для типовых артефактов и локальных конфигов
status: done
priority: medium
created: 2026-07-12
updated: 2026-07-12
related_code:
  - .gitignore
related_docs: []
tags: [tech-debt]
---

## Контекст

В корне проекта нет `.gitignore`. При работе с трекером обнаружено: `.claude/settings.local.json` (локальные разрешения пользователя, содержит абсолютные пути машины) попал бы в staging при `git add -A`. Также нет игнора для стандартных Gradle-артефактов (`build/`, `.gradle/`, `bin/`, `*.class`), IDE (`.idea/`, `*.iml`, `.vscode/`) и OS-мусора (`.DS_Store`).

Пока проблема не выстрелила (я стейджу только по именам), но рано или поздно кто-то сделает `git add -A` и утечёт лишнее.

## Acceptance criteria

- [ ] В корне репозитория есть `.gitignore` со стандартным набором для Kotlin/Gradle/Spring Boot проекта.
- [ ] Игнорируются: `.claude/settings.local.json`, `build/`, `.gradle/`, `bin/`, `out/`, `*.class`, `.idea/`, `*.iml`, `.vscode/`, `.DS_Store`, `*.log`, `.env*`.
- [ ] `git status` в чистом рабочем дереве не показывает лишнего.
- [ ] Существующие tracked-файлы (если такие попадают под новые правила) — либо остаются tracked (через `!`), либо явно удалены из индекса.

## План

1. Проверить `git ls-files` — не отслеживается ли уже что-то, что попадёт под новые правила.
2. Написать `.gitignore` по шаблону gradle/kotlin/idea/macos.
3. Проверить `git status` — чистое дерево.
4. Commit + push.

## Лог

- 2026-07-12: заведена автоматически в ходе работы над T-004 (Auto-mode). Обнаружено при первом commit: `.claude/settings.local.json` — untracked, легко попал бы в staging.
- 2026-07-12: добавлен `.gitignore` — стандартный набор (Gradle, Kotlin/JVM, IDEA/VSCode, macOS/Windows, .env, `.claude/settings.local.json`). Tracked-файлов под новые правила нет — миграции индекса не требуется. `git status` — чисто.
