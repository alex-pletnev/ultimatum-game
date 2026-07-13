---
name: harness-update
description: Use когда пользователь просит `/harness-update` — обновить skill-файлы **и** CLAUDE.md в текущем проекте свежими копиями из harness-репо (`~/.claude/skills/setup-agent-harness/references/`). НЕ трогает `docs/`, `docs/tasks/`, `.claude/settings.local.json`, custom skill'ы вне списка.
---

# harness-update

Синхронизировать harness-managed артефакты в проекте с последними версиями из harness-репо. Два раздела:

- **Часть A** (v1): 9 skill-файлов в `.claude/skills/`.
- **Часть B** (v2): CLAUDE.md с сохранением project-specific placeholder-values через `.claude/harness-config.json`.

Если `.claude/harness-config.json` **отсутствует** — Часть B пропускается, предлагается bootstrap (см. ниже).

## Аргументы

- **Explicit:** `/harness-update`.
- **Auto-mode:** нет. Skill вызывается только по явной команде — обновление skill-файлов и CLAUDE.md может изменить поведение агента, требует осознанного шага пользователя.

## Часть A — Sync .claude/skills/

1. **Determine source.** Harness repo — `~/.claude/skills/setup-agent-harness/`. Проверить что директория существует и содержит `references/skills/`. Если нет — сказать пользователю «harness не установлен», остановиться.

2. **List targets.** Пройтись по 9 файлам в `~/.claude/skills/setup-agent-harness/references/skills/`:
   - `task-add.md`
   - `task-done.md`
   - `task-sync.md`
   - `docs-sync.md`
   - `wheel-check.md`
   - `mid-retro.md`
   - `self-review.md`
   - `pre-flight.md`
   - `consolidate.md`

3. **Diff summary + customization check.** Для каждого файла — сравнить с существующим в `.claude/skills/`:
   - Если файла нет в проекте — пометить как «новый (будет добавлен)».
   - Если есть, но идентичен — пометить как «идентичен (skip)».
   - Если есть, но отличается — **определить причину**:
     - Читаю `.claude/harness-sync-state.json` (создаётся после первого apply): для каждого skill'а лежит `{ "file": "sha256 на момент последнего sync"}`.
     - Если current-sha == recorded-sha в state → это чистый upstream update (harness обновил файл, локальной кастомизации нет). Пометить как «upstream update: N строк».
     - Если current-sha != recorded-sha → **user кастомизировал** файл локально после последнего sync'а. Пометить как «⚠ LOCAL CUSTOMIZATION: N строк изменено локально, будет перезаписано upstream'ом».
     - Если `.claude/harness-sync-state.json` вообще отсутствует (первый запуск harness-update в этом проекте) → любое различие трактуется как upstream update без предупреждения (т.к. не знаем предыдущее состояние).

   Показать пользователю итоговый summary:
   ```
   Обновлю (upstream update):
     - task-done.md (изменено: 15 строк)
     - self-review.md (изменено: 6 строк)
   ⚠ Обнаружена ЛОКАЛЬНАЯ кастомизация (будет ПОТЕРЯНА):
     - task-add.md (12 строк отличаются от последнего sync'а)
   Добавлю:
     - pre-flight.md (новый)
   Skip (идентичны):
     - docs-sync.md, task-sync.md, ...
   Не трогаю: docs/, docs/tasks/, CLAUDE.md, .claude/settings.local.json, custom skills.
   Продолжить? (y/n)
   ```

   **Если есть хотя бы один файл с LOCAL CUSTOMIZATION**, отдельно напомнить пользователю: «Твои локальные правки в этих файлах пропадут. Если хочешь сохранить их — перенеси в custom skill (файл вне harness-managed списка) до commit'а. Продолжить всё равно? (y/n)».

4. **Wait for user.** Ждать явного `y` (или синонимов). Молчание/`n`/уточнение — остановить, не трогать файлы.

5. **Apply.** Для каждого файла из списка (кроме «идентичен»):
   - Copy: `cp ~/.claude/skills/setup-agent-harness/references/skills/<file> .claude/skills/<file>`.
   - Force overwrite ок — это ожидаемое поведение.
   - **Обновить `.claude/harness-sync-state.json`**: для каждого applied файла — записать sha256 текущего содержимого. Формат:
     ```json
     {
       "harness_sha": "<git-sha harness-репо на момент sync'а>",
       "files": {
         "task-add.md": "<sha256>",
         "task-done.md": "<sha256>",
         ...
       }
     }
     ```
   - Файл `.claude/harness-sync-state.json` **коммитится в git** (не в .gitignore) — team-consistency detect'а кастомизации.

6. **Custom skills.** Если в `.claude/skills/` есть файлы вне списка из шага 2 (custom skill'ы пользователя) — оставить их **как есть**. НЕ удалять, не трогать.

7. **Commit.** По правилам git-автоматизации:
   - `git add .claude/skills/*.md` (перечислить конкретные обновлённые файлы, не wildcard).
   - Message: `chore(harness): sync .claude/skills/ from setup-agent-harness references`.
   - Тело: список обновлённых файлов + версия harness (`git -C ~/.claude/skills/setup-agent-harness rev-parse --short HEAD`).
   - Push.

8. **Отчёт пользователю:** одна строка — «Часть A: обновил N файлов, версия harness: `<sha>`».

## Часть B — Sync CLAUDE.md (v2)

**Триггер выполнения Части B:** если файл `.claude/harness-config.json` существует в проекте.
**Если отсутствует:** предложить пользователю bootstrap (см. подраздел ниже), затем — либо продолжить Часть B, либо skip.

### Шаги Части B

1. **Read config.** Прочитать `.claude/harness-config.json` — JSON-объект с placeholder-values.

2. **Read template.** Прочитать `~/.claude/skills/setup-agent-harness/references/templates/claude-md.template.md`.

3. **Detect missing keys.** Найти все `{{PLACEHOLDER}}` в template. Сравнить с ключами в config'е:
   - Если keys есть в template, но нет в config → **stop**, спросить пользователя: «Template имеет новый placeholder `{{X}}`, значение не найдено в harness-config.json. Введи значение».
   - Обновить config после ввода.

4. **Render.** Substitute placeholders → получить новый CLAUDE.md текст.

5. **Backup.** `cp CLAUDE.md CLAUDE.md.bak-YYYY-MM-DD-HHMMSS`.

6. **Diff summary.** Сравнить backup и новый рендер. Показать пользователю (git diff-like):
   ```
   CLAUDE.md изменится: N+ строк добавлено, M- удалено.
   Backup: CLAUDE.md.bak-YYYY-MM-DD-HHMMSS
   Если у тебя были project-specific правки в CLAUDE.md вне `{{SPECIFIC_RULES}}` области — они будут потеряны. Перенеси их в SPECIFIC_RULES секцию harness-config.json.
   Продолжить? (y/n)
   ```

7. **Wait for user.** `y` → apply; `n` → удалить backup, оставить CLAUDE.md как был.

8. **Apply.** Записать новый рендер в `CLAUDE.md`.

9. **Отчёт:** «Часть B: CLAUDE.md обновлён. Backup: `CLAUDE.md.bak-...`».

### Bootstrap (для существующих проектов без harness-config.json)

Если проект был настроен старой версией `setup-agent-harness` (до появления `harness-config.json`) — предложить создать config один раз.

**Шаги bootstrap:**

1. Спросить пользователя: «Проект не имеет `.claude/harness-config.json`. Bootstrap? (y/n) — v2 CLAUDE.md-sync без него невозможен».
2. Если `y` → извлечь placeholder-values из current CLAUDE.md по эвристике:
   - `PROJECT_NAME` — из первой строки секции `## Общие принципы` (шаблон «Проект: **{{PROJECT_NAME}}** — {{STACK}}»).
   - `STACK` — оттуда же.
   - `PRIMARY_BRANCH` — из секции `## Git-автоматизация` («Работает в ветке `{{PRIMARY_BRANCH}}`»).
   - `BUILD_COMMAND` / `TEST_COMMAND` — из `## Запуск и проверки`.
   - `LANGUAGE` — эвристика по тексту CLAUDE.md (наличие кириллицы → RU).
   - `DURATION_BASELINES` — вся таблица из секции «Долгие команды — эвристика ожидания».
   - `SPECIFIC_RULES` — оставить пустым, спросить пользователя.
3. Показать extract'нутые значения пользователю: «Вот что нашёл. Правильно? (y/n или укажи ошибки)».
4. Записать `.claude/harness-config.json` (не в .gitignore).
5. Продолжить Часть B.

## Общее (обе части)

- **Commit.** По правилам git-автоматизации. Отдельный commit на Часть A, отдельный на Часть B (легче ревертить одну без другой). Или один атомарный — по решению пользователя.
- **Push.** После commit'а.

## Что НЕ делает

- Не трогает `docs/`, `docs/tasks/`, `.claude/settings.local.json`.
- Не добавляет новые skill'ы за пределами 8 harness-managed.
- Не удаляет старые файлы, даже если harness убрал соответствующий template (это опасно; удаление — только с явного запроса).
- **Не пытается сохранить custom-правки CLAUDE.md вне placeholder-областей.** Если у пользователя были кастомные секции — они пропадут при re-render. Правильное место для персистентной кастомизации — `SPECIFIC_RULES` в `harness-config.json`.

## Ограничения

- **Skill не самообновляется атомарно.** Если harness-update.md сам поменялся в harness'е — v1 не гарантирует что новая версия применится в этом же прогоне. Практически: skill v1 не self-mutating. Если пользователь запустил `/harness-update` — обновятся 8 skill'ов; сам `harness-update.md` обновится при **следующем** прогоне.
- **Диагностика конфликтов.** Если после update что-то сломалось — v1 не пытается rollback'ать. Пользователь делает `git revert` сам.
- **Не трогать через wildcards.** Всегда явно перечислять файлы. Wildcard `*.md` рискует зацепить custom skill'ы.
