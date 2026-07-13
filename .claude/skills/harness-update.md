---
name: harness-update
description: Use когда пользователь просит `/harness-update` — обновить skill-файлы в текущем проекте свежими копиями из harness-репо (`~/.claude/skills/setup-agent-harness/references/skills/`). НЕ трогает `docs/`, `docs/tasks/`, `.claude/settings.local.json`, custom skill'ы вне списка. Не обновляет CLAUDE.md (см. T-036).
---

# harness-update

Синхронизировать 8 skill-файлов в `.claude/skills/` текущего проекта с последней версией из harness-репо. Ничего больше.

**Область v1:** только `.claude/skills/*.md` из списка ниже.
**НЕ входит в v1** (см. T-036): обновление CLAUDE.md с сохранением project-specific секций. Пока — руками через `/setup-agent-harness (b)`.

## Аргументы

- **Explicit:** `/harness-update`.
- **Auto-mode:** нет. Skill вызывается только по явной команде — обновление skill-файлов может изменить поведение агента, требует осознанного шага пользователя.

## Шаги

1. **Determine source.** Harness repo — `~/.claude/skills/setup-agent-harness/`. Проверить что директория существует и содержит `references/skills/`. Если нет — сказать пользователю «harness не установлен», остановиться.

2. **List targets.** Пройтись по 8 файлам в `~/.claude/skills/setup-agent-harness/references/skills/`:
   - `task-add.md`
   - `task-done.md`
   - `task-sync.md`
   - `docs-sync.md`
   - `wheel-check.md`
   - `mid-retro.md`
   - `self-review.md`
   - `pre-flight.md`

3. **Diff summary.** Для каждого файла — сравнить с существующим в `.claude/skills/`:
   - Если файла нет в проекте — пометить как «новый (будет добавлен)».
   - Если есть, но идентичен — пометить как «идентичен (skip)».
   - Если есть, но отличается — показать `git diff --no-index --stat <src> <dst>` (или просто число различающихся строк).

   Показать пользователю итоговый summary:
   ```
   Обновлю:
     - task-done.md (изменено: 15 строк)
     - self-review.md (изменено: 6 строк)
   Добавлю:
     - pre-flight.md (новый)
   Skip (идентичны):
     - task-add.md, docs-sync.md, ...
   Не трогаю: docs/, docs/tasks/, CLAUDE.md, .claude/settings.local.json, custom skills.
   Продолжить? (y/n)
   ```

4. **Wait for user.** Ждать явного `y` (или синонимов). Молчание/`n`/уточнение — остановить, не трогать файлы.

5. **Apply.** Для каждого файла из списка (кроме «идентичен»):
   - Copy: `cp ~/.claude/skills/setup-agent-harness/references/skills/<file> .claude/skills/<file>`.
   - Force overwrite ок — это ожидаемое поведение.

6. **Custom skills.** Если в `.claude/skills/` есть файлы вне списка из шага 2 (custom skill'ы пользователя) — оставить их **как есть**. НЕ удалять, не трогать.

7. **Commit.** По правилам git-автоматизации:
   - `git add .claude/skills/*.md` (перечислить конкретные обновлённые файлы, не wildcard).
   - Message: `chore(harness): sync .claude/skills/ from setup-agent-harness references`.
   - Тело: список обновлённых файлов + версия harness (`git -C ~/.claude/skills/setup-agent-harness rev-parse --short HEAD`).
   - Push.

8. **Отчёт пользователю:** одна строка — «Обновил N файлов, версия harness: `<sha>`. Полный CLAUDE.md-sync — в T-036».

## Что НЕ делает v1

- Не обновляет CLAUDE.md (backup + preserve project-specific — см. T-036).
- Не трогает `docs/`, `docs/tasks/`, `.claude/settings.local.json`.
- Не добавляет новые skill'ы за пределами 8 harness-managed.
- Не удаляет старые файлы, даже если harness убрал соответствующий template (это опасно; удаление — только с явного запроса).

## Ограничения

- **Skill не самообновляется атомарно.** Если harness-update.md сам поменялся в harness'е — v1 не гарантирует что новая версия применится в этом же прогоне. Практически: skill v1 не self-mutating. Если пользователь запустил `/harness-update` — обновятся 8 skill'ов; сам `harness-update.md` обновится при **следующем** прогоне.
- **Диагностика конфликтов.** Если после update что-то сломалось — v1 не пытается rollback'ать. Пользователь делает `git revert` сам.
- **Не трогать через wildcards.** Всегда явно перечислять файлы. Wildcard `*.md` рискует зацепить custom skill'ы.
