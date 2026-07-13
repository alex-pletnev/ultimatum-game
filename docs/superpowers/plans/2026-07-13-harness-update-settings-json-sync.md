# harness-update Часть C — settings.json hooks sync — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Расширить `/harness-update` Частью C — синхронизацией `.claude/settings.json` hooks проекта с recommended-набором из harness-репо, устойчиво к user'ским кастомизациям.

**Architecture:** Source-of-truth — `~/.claude/skills/setup-agent-harness/references/settings-hooks/<id>.json` (по файлу на hook). Tracking через `harness-sync-state.json.hooks` (sha256 canonical JSON'а `hook`-объекта). Классификация: new / identical / upstream update / local customization / user-disabled / adopt / deprecated. Sync — JSON-мутации массивов `settings.json.hooks[event]`.

**Tech Stack:** Markdown skill'ы (человеко-читаемые инструкции, исполняет Claude Code). Артефакты — JSON (settings.json, state, hook-configs), bash-команды в commit-hook'ах. Runtime — Claude Code / оболочка macOS.

## Global Constraints

- Scope Части C — только ключ `hooks` в `settings.json`. Не трогаем `permissions`, `env`, `model`, `statusLine`, `apiKeyHelper`.
- Не трогаем `.claude/settings.local.json`.
- sha256 считается от **canonical JSON** объекта `hook` (ключи отсортированы, no indent, `ensure_ascii=false`).
- Все правки harness'а идут в оба репо: `~/.claude/skills/setup-agent-harness/` (git worktree) + `.claude/skills/` этого проекта. Пуш в оба.
- Backup перед мутацией `settings.json` — обязательно (`.claude/settings.json.bak-YYYY-MM-DD-HHMMSS`).
- Commits — по правилам git-автоматизации (Conventional Commits + `Refs: docs/tasks/T-039-*.md`).
- Спецификация: `docs/superpowers/specs/2026-07-13-harness-update-settings-json-sync-design.md`.

---

## File Structure

**Harness-репо** (`~/.claude/skills/setup-agent-harness/`):
- **Create:** `references/settings-hooks/self-review-reminder.json` — миграция существующего Stop-hook'а из `settings-json-template.md`.
- **Modify:** `references/settings-json-template.md` — станет документацией-обзором; source-of-truth переезжает в JSON.
- **Modify:** `references/skills/harness-update.md` — добавить раздел «Часть C».

**Проект** (`/Users/aleksandrpletnev/sandbox/ultimatum-game/`):
- **Modify:** `.claude/skills/harness-update.md` — зеркало Части C.
- **Modify:** `.claude/harness-sync-state.json` — появится ключ `hooks` (пустой начально, наполнится при первом прогоне).
- **Modify:** `docs/tasks/T-039-*.md` — `## Лог` + status=done в конце.
- **Modify:** `docs/tasks/INDEX.md` — обновить строку T-039.

Тестирование — ручное, per-scenario. Каждый scenario = отдельная task с явными шагами и rollback'ом.

---

### Task 1: Extract self-review-reminder в references/settings-hooks/*.json (harness-репо)

**Files:**
- Create: `~/.claude/skills/setup-agent-harness/references/settings-hooks/self-review-reminder.json`
- Modify: `~/.claude/skills/setup-agent-harness/references/settings-json-template.md`

**Interfaces:**
- Produces: JSON-схема `{ id, event, hook: { type, command, timeout, statusMessage }, gitignore: [] }`. Часть C (Task 2) читает эту директорию как источник recommended hooks.

- [ ] **Step 1: Создать JSON с текущим Stop-hook'ом.**

Файл `~/.claude/skills/setup-agent-harness/references/settings-hooks/self-review-reminder.json`:

```json
{
  "id": "self-review-reminder",
  "event": "Stop",
  "hook": {
    "type": "command",
    "command": "cd \"$(git rev-parse --show-toplevel 2>/dev/null || pwd)\" || exit 0; current=$(git rev-parse HEAD 2>/dev/null || echo \"\"); state_file=\".claude/last-committed-head.txt\"; prev=$(cat \"$state_file\" 2>/dev/null || echo \"\"); if [ -n \"$current\" ] && [ \"$current\" != \"$prev\" ]; then echo \"$current\" > \"$state_file\"; short=$(git rev-parse --short \"$current\"); msg=$(git log -1 --format=%s \"$current\"); printf '{\"hookSpecificOutput\": {\"hookEventName\": \"Stop\", \"additionalContext\": \"В этой итерации был commit %s: %s. По правилу harness (self-review в Auto-mode) — прогони /self-review до ответа пользователю.\"}}\\n' \"$short\" \"$msg\"; fi",
    "timeout": 5,
    "statusMessage": "Checking for new commit"
  },
  "gitignore": [".claude/last-committed-head.txt"]
}
```

Примечание: JSON-схема самого hook'а сохраняется как есть; известно, что hookSpecificOutput здесь для события Stop не пройдёт валидацию Claude Code — это отдельный баг **T-040** (заводим после T-039). В рамках этой задачи мы только мигрируем hook в новый source-of-truth, не чиним его семантику.

- [ ] **Step 2: Валидировать JSON.**

Run: `python3 -c "import json; json.load(open('$HOME/.claude/skills/setup-agent-harness/references/settings-hooks/self-review-reminder.json'))"`
Expected: без вывода (успех).

- [ ] **Step 3: Обновить settings-json-template.md — переключить source-of-truth.**

В самом верху документа добавить блок:

```markdown
> **Source of truth:** JSON-файлы в `references/settings-hooks/*.json`. Часть C `/harness-update` читает только их. Этот markdown — человеко-читаемая документация «зачем каждый hook нужен».
```

Секцию «## Hook 1 — Stop-hook: reminder про self-review после commit'а» оставить как есть, но в конце добавить ссылку:

```markdown
**Source:** `references/settings-hooks/self-review-reminder.json`
```

- [ ] **Step 4: Commit в harness-репо.**

```bash
cd ~/.claude/skills/setup-agent-harness
git add references/settings-hooks/self-review-reminder.json references/settings-json-template.md
git commit -m "$(cat <<'EOF'
feat(settings-hooks): extract self-review-reminder as JSON source-of-truth

Machine-readable per-hook JSON — prep for /harness-update Часть C.
EOF
)"
git push origin main
```

Expected: `1 file changed, ... insertions(+)` + push успех.

---

### Task 2: Написать раздел «Часть C» в `.claude/skills/harness-update.md` (проект)

**Files:**
- Modify: `/Users/aleksandrpletnev/sandbox/ultimatum-game/.claude/skills/harness-update.md`

**Interfaces:**
- Consumes: `references/settings-hooks/*.json` из Task 1.
- Produces: инструкции для агента про алгоритм sync'а `settings.json`. Task 3 копирует то же в harness-репо.

- [ ] **Step 1: Найти якорь для вставки.**

Между секциями «## Часть B — Sync CLAUDE.md (v2)» (заканчивается) и «## Общее (обе части)» (начинается) — вставить новый раздел «## Часть C — Sync .claude/settings.json (hooks) (v3)».

- [ ] **Step 2: Написать содержимое Части C.**

Добавить перед «## Общее (обе части)»:

````markdown
## Часть C — Sync .claude/settings.json (hooks) (v3)

**Триггер выполнения:** наличие непустой директории `~/.claude/skills/setup-agent-harness/references/settings-hooks/`. Отсутствует/пуста → Часть C skip'ается без ошибки.

**Scope:** только ключ `hooks` в `.claude/settings.json`. Не трогаем `permissions`, `env`, `model`, `statusLine`, `apiKeyHelper`, `.claude/settings.local.json`.

### Схема source-of-truth

Один JSON-файл на hook: `references/settings-hooks/<id>.json`:

```json
{
  "id": "self-review-reminder",
  "event": "Stop",
  "hook": { "type": "command", "command": "...", "timeout": 5, "statusMessage": "..." },
  "gitignore": [".claude/last-committed-head.txt"]
}
```

- `id` — уникальный, kebab-case. Ключ в `harness-sync-state.json.hooks`.
- `event` — Claude Code hook event (`Stop`, `PreCompact`, `UserPromptSubmit`, ...).
- `hook` — объект, вставляемый в массив `settings.json.hooks[event][*].hooks[]`.
- `gitignore` — строки для append'а в `.gitignore` проекта.

### Canonical hash

sha256 считается от **canonical JSON'а объекта `hook`** — ключи отсортированы, no indent, `ensure_ascii=false`. Пример на Python (используем при ручной проверке):

```bash
python3 -c "import json,hashlib,sys; h=json.load(open(sys.argv[1]))['hook']; print(hashlib.sha256(json.dumps(h,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()).hexdigest())" <path-to-hook.json>
```

### Шаги Части C

1. **Discover.**
   - Прочитать все `references/settings-hooks/*.json`. Для каждого — вычислить `recommended_sha` = sha256(canonical(hook)).
   - Прочитать `.claude/settings.json` (парсить JSON). Если отсутствует — считать `{}`.
   - Прочитать `.claude/harness-sync-state.json.hooks` (если ключа нет — считать `{}`).
   - Битый JSON в `settings.json` → **abort** с сообщением: «settings.json невалидный, почини вручную, потом снова `/harness-update`». State не трогаем.
   - Битый JSON в `references/settings-hooks/<id>.json` → skip этот файл, warn user'а, продолжить с остальными.

2. **Classify.** Для каждого recommended hook по `id`:
   - **new** — `id` не в state, не найдено в settings.json (ни по state-sha, ни по recommended-sha).
   - **adopt** — `id` не в state, но в settings.json есть элемент с sha == recommended-sha. Действие: записать в state, treat as identical.
   - **identical** — `id` в state, в settings.json найден элемент с sha == recommended-sha (и state-sha == recommended-sha).
   - **upstream update** — `id` в state, найден элемент с sha == state-sha, но state-sha != recommended-sha.
   - **local customization** — `id` в state, элемент по state-sha не найден, но в state-sha != recommended-sha. Формально: state есть, но текущий hook в settings.json не совпадает ни с state-sha, ни с recommended-sha. Практически: user отредактировал command.
   - **user-disabled** — `id` в state, но в settings.json вообще нет элементов с sha == state-sha и нет с sha == recommended-sha. То есть hook был удалён user'ом.

   Также классифицировать **deprecated** — `id` в state, но JSON'а в references больше нет.

3. **Summary + confirm.** Единый output:

   ```
   Часть C — .claude/settings.json:
   Добавлю:
     - <id> (event: Stop)
   Обновлю (upstream update):
     - <id> (event: Stop)
   ⚠ ЛОКАЛЬНАЯ кастомизация (будет ПОТЕРЯНА при apply):
     - <id> (command изменён локально после последнего sync)
   Skip:
     - <id> (identical)
     - <id> (user-disabled — не восстанавливаю)
     - <id> (adopt — записываю в state)
   Deprecated (harness убрал рекомендацию):
     - <id> — удалить hook из settings.json? (y/n)
   .gitignore: добавлю строки: <line1>, <line2>
   Backup: .claude/settings.json.bak-YYYY-MM-DD-HHMMSS
   Продолжить? (y/n)
   ```

   Если есть local customization — отдельная плашка перед основным prompt'ом с советом «перенеси свои правки в custom hook (id вне harness-managed списка), иначе они пропадут».

4. **Wait for user.** Молчание/`n`/уточнение — не трогать файлы.

5. **Backup.** `cp .claude/settings.json .claude/settings.json.bak-YYYY-MM-DD-HHMMSS`. Если settings.json отсутствовал и Часть C его создаёт — backup'ить нечего.

6. **Apply hooks** (in-memory JSON):
   - **new** — найти блок `settings.json.hooks[event]` (создать `{ "hooks": [] }`, если нет), append `hook`-объект в его `hooks[]`.
   - **upstream update** — заменить in-place элемент, найденный по state-sha.
   - **deprecated (accepted)** — удалить элемент по state-sha; если родительский блок опустел (`hooks: []`) — удалить блок; если массив event'а опустел — удалить ключ event'а.
   - **local customization (accepted)** — то же что upstream update, но перед этим спросить отдельный y/n именно на этот hook.
   - **user-disabled / identical / adopt** — не мутировать settings.json.

7. **Apply .gitignore.**
   - Собрать все строки из `gitignore` полей hook'ов, которые в результате apply'я present в state (new/upstream update/adopt/existing identical).
   - Для каждой — `grep -Fxq "<line>" .gitignore` → если не найдена, append.
   - Для deprecated (accepted): если строка требовалась только удалённым hook'ом (никто другой не требует) — предложить удалить (y/n per line).

8. **Write.** Атомарно (temp + rename):
   - `settings.json` — pretty-print JSON с 2-space indent, финальный newline.
   - `.claude/harness-sync-state.json` — обновить `hooks` секцию: applied → new sha, adopted → recommended sha, deprecated → удалить ключ.
   - `.gitignore` — обновлённая версия.

9. **Commit.** Отдельный commit:

   ```
   chore(harness): sync .claude/settings.json hooks from references

   - added: <ids>
   - updated: <ids>
   - deprecated (removed): <ids>
   - harness sha: <sha>

   Refs: docs/tasks/T-039-*.md
   ```

   Files: `.claude/settings.json`, `.claude/harness-sync-state.json`, `.gitignore`, `.claude/settings.json.bak-*`.

10. **Push.**

11. **Отчёт:** «Часть C: added N, updated M, deprecated K, warned L».

### Edge cases

- **settings.json отсутствует.** Если есть хоть один new hook и user подтвердил — создать `{ "$schema": "https://json.schemastore.org/claude-code-settings.json", "hooks": { "<event>": [{ "hooks": [<hook>] }] } }`.
- **Битый JSON settings.json** — abort (см. Discover).
- **Битый JSON hook-file** — skip + warn, продолжить.
- **User переместил hook между event'ами** — matching идёт по (event, sha). Считаем user-disabled в старом event'е; в новом event'е чужого hook'а с id-match нет → не трогаем. Документируется как ограничение.
- **Одинаковые command'ы у двух recommended hooks** — теоретически sha-collision. Практически исключено. Если возникнет — при apply двум ключам state пишется одна sha → warn.

### Ограничения Части C

- Не трогает не-hooks ключи settings.json.
- Не восстанавливает user-disabled hook'и (без `--force`, который не реализуем в v1).
- Не rollback'ает после ошибки — user делает `git revert`.
- Не меняет порядок массивов hooks — stabilnое append/in-place replace.
````

- [ ] **Step 3: Verify markdown валиден (нет syntax-битой fenced code).**

Run: `awk '/^```/{n++} END{print (n%2==0)?"ok":"unbalanced fences: " n}' /Users/aleksandrpletnev/sandbox/ultimatum-game/.claude/skills/harness-update.md`
Expected: `ok`.

- [ ] **Step 4: Не коммитим отдельно.** Изменения проектного skill'а войдут в общий commit после Task 3 + верификации (Task 10).

---

### Task 3: Зеркальная правка в harness-репо

**Files:**
- Modify: `~/.claude/skills/setup-agent-harness/references/skills/harness-update.md`

**Interfaces:**
- Consumes: содержимое из Task 2 (буква-в-букву тот же раздел).
- Produces: harness-репо готова к тому, чтобы `/setup-agent-harness` в новых проектах разворачивал skill с Частью C сразу.

- [ ] **Step 1: Скопировать раздел «Часть C» из Task 2 в harness-репо.**

Вставить между «## Часть B» и «## Общее (обе части)» — идентичный текст. Content-drift сверить финально в Task 10.

- [ ] **Step 2: Verify md-syntax.**

Run: `awk '/^```/{n++} END{print (n%2==0)?"ok":"unbalanced"}' ~/.claude/skills/setup-agent-harness/references/skills/harness-update.md`
Expected: `ok`.

- [ ] **Step 3: Commit в harness-репо.**

```bash
cd ~/.claude/skills/setup-agent-harness
git add references/skills/harness-update.md
git commit -m "$(cat <<'EOF'
feat(harness-update): add Часть C — sync .claude/settings.json hooks

sha256-tracked hooks in references/settings-hooks/*.json,
adopt/user-disabled/deprecated cases, hooks-only scope.
EOF
)"
git push origin main
```

---

### Task 4: Verification — Scenario 1 (adopt existing, no-op на повторе)

**Files:**
- Read/modify (в процессе): `.claude/settings.json`, `.claude/harness-sync-state.json`

**Interfaces:**
- Consumes: свежеустановленная Часть C из Task 2.
- Produces: заполненная секция `hooks` в `harness-sync-state.json` (`self-review-reminder`: `<sha>`).

- [ ] **Step 1: Baseline — снять состояние.**

Run: `cat .claude/harness-sync-state.json 2>/dev/null | python3 -m json.tool || echo "no state file"`
Ожидаемо: файл существует (был создан в T-035/T-037), в нём есть `files`, но нет ключа `hooks`.

- [ ] **Step 2: Прогнать `/harness-update`.**

Пользователь запускает `/harness-update` в этой сессии. Ожидаемая классификация в summary:

```
Часть C — .claude/settings.json:
Skip:
  - self-review-reminder (adopt — записываю в state)
```

Ответить `y`.

- [ ] **Step 3: Проверить state.**

Run: `python3 -c "import json; s=json.load(open('.claude/harness-sync-state.json')); print(s.get('hooks',{}))"`
Expected: `{'self-review-reminder': '<64-hex-chars>'}`.

- [ ] **Step 4: Повторный прогон — no-op.**

Пользователь запускает `/harness-update` снова. Ожидаемо в summary Части C:

```
Skip:
  - self-review-reminder (identical)
```

Ни `settings.json`, ни `state.hooks` не меняются.

- [ ] **Step 5: Commit (adopt state).**

Первый прогон произвёл commit силами skill'а (state обновился). Проверить:

Run: `git log -1 --format=%s`
Expected: содержит `chore(harness): sync .claude/settings.json hooks from references`.

---

### Task 5: Verification — Scenario 2 (local customization warn)

- [ ] **Step 1: Симулировать local customization.**

Отредактировать `.claude/settings.json` вручную: поменять `statusMessage` с `"Checking for new commit"` на `"CUSTOM: my local override"`.

Run: `python3 -m json.tool .claude/settings.json > /dev/null && echo ok`
Expected: `ok` (валидный JSON).

- [ ] **Step 2: Прогнать `/harness-update`.**

Ожидаемо в summary:

```
⚠ ЛОКАЛЬНАЯ кастомизация (будет ПОТЕРЯНА при apply):
  - self-review-reminder (command изменён локально после последнего sync)
```

Отдельная плашка с советом. Prompt: продолжить (y/n)?

Ответить `n`.

- [ ] **Step 3: Проверить, что файл не менялся.**

Run: `grep -c 'CUSTOM: my local override' .claude/settings.json`
Expected: `1` (local customization осталась).

- [ ] **Step 4: Rollback вручную.**

Отредактировать `.claude/settings.json` — вернуть `"Checking for new commit"`.

Run: `grep -c 'CUSTOM: my local override' .claude/settings.json`
Expected: `0`.

Не коммитить — это тестовая манипуляция.

---

### Task 6: Verification — Scenario 3 (user-disabled skip)

- [ ] **Step 1: Симулировать disable.**

Отредактировать `.claude/settings.json` — удалить hook самого `self-review-reminder`'а (весь элемент из массива `hooks[Stop][0].hooks[]`, при пустоте — блок Stop тоже удалить).

Промежуточное состояние: `{ "$schema": "...", "hooks": {} }` (или без ключа `hooks`).

Оставить `.claude/harness-sync-state.json.hooks.self-review-reminder` как был.

- [ ] **Step 2: Прогнать `/harness-update`.**

Ожидаемо в summary:

```
Skip:
  - self-review-reminder (user-disabled — не восстанавливаю)
```

Никакого y/n на восстановление. `settings.json` не меняется.

- [ ] **Step 3: Rollback.**

`git checkout -- .claude/settings.json` — вернуть исходное состояние.

Run: `python3 -c "import json; d=json.load(open('.claude/settings.json')); print(len(d['hooks']['Stop'][0]['hooks']))"`
Expected: `1`.

---

### Task 7: Verification — Scenario 4 (new hook added)

- [ ] **Step 1: Добавить тестовый JSON в references.**

Создать `~/.claude/skills/setup-agent-harness/references/settings-hooks/test-placeholder.json`:

```json
{
  "id": "test-placeholder",
  "event": "PreCompact",
  "hook": {
    "type": "command",
    "command": "echo test-placeholder-hook",
    "timeout": 3,
    "statusMessage": "Test placeholder"
  },
  "gitignore": []
}
```

- [ ] **Step 2: Прогнать `/harness-update`.**

Ожидаемо:

```
Добавлю:
  - test-placeholder (event: PreCompact)
```

Ответить `y`.

- [ ] **Step 3: Проверить settings.json.**

Run: `python3 -c "import json; d=json.load(open('.claude/settings.json')); print([h.get('statusMessage') for b in d['hooks'].get('PreCompact',[]) for h in b.get('hooks',[])])"`
Expected: `['Test placeholder']`.

- [ ] **Step 4: Проверить state.**

Run: `python3 -c "import json; s=json.load(open('.claude/harness-sync-state.json')); print(list(s.get('hooks',{}).keys()))"`
Expected: содержит `'test-placeholder'`.

- [ ] **Step 5: Rollback.**

- Удалить `~/.claude/skills/setup-agent-harness/references/settings-hooks/test-placeholder.json`.
- `git checkout -- .claude/settings.json .claude/harness-sync-state.json` — вернуть проект в состояние после Task 4.

Проверить: `python3 -c "import json; s=json.load(open('.claude/harness-sync-state.json')); print(list(s.get('hooks',{}).keys()))"`
Expected: `['self-review-reminder']`.

---

### Task 8: Verification — Scenario 5 (deprecated remove offer)

- [ ] **Step 1: Симулировать deprecation.**

Временно переименовать: `mv ~/.claude/skills/setup-agent-harness/references/settings-hooks/self-review-reminder.json /tmp/`.

- [ ] **Step 2: Прогнать `/harness-update`.**

Ожидаемо:

```
Deprecated (harness убрал рекомендацию):
  - self-review-reminder — удалить hook из settings.json? (y/n)
```

Отвечаем `n` (сначала).

Ожидаемо: настройки не меняются, state не меняется.

- [ ] **Step 3: Прогнать снова, ответить `y`.**

Ожидаемо:
- `hooks` в settings.json Stop-массив пустеет → удаляется блок → удаляется ключ Stop.
- `state.hooks.self-review-reminder` удаляется.
- `.gitignore`: строка `.claude/last-committed-head.txt` — предложить удалить (нет других hook'ов, которые её требуют) → отвечаем `y`.

Проверить: `python3 -c "import json; d=json.load(open('.claude/settings.json')); print(d.get('hooks',{}))"`
Expected: `{}`.

- [ ] **Step 4: Rollback.**

- `mv /tmp/self-review-reminder.json ~/.claude/skills/setup-agent-harness/references/settings-hooks/`.
- `git checkout -- .claude/settings.json .claude/harness-sync-state.json .gitignore` — восстановить.
- Убедиться: `python3 -c "import json; d=json.load(open('.claude/settings.json')); print(len(d['hooks']['Stop'][0]['hooks']))"` → `1`.

---

### Task 9: Verification — Scenario 6 (broken JSON abort)

- [ ] **Step 1: Сломать settings.json.**

Дописать в конец файла один лишний `}`.

Run: `python3 -m json.tool .claude/settings.json > /dev/null; echo "exit=$?"`
Expected: `exit=1` (parse error).

- [ ] **Step 2: Прогнать `/harness-update`.**

Ожидаемо Часть C говорит: «settings.json невалидный, почини вручную, потом снова `/harness-update`». Ни `settings.json`, ни `state` не меняются.

- [ ] **Step 3: Rollback.**

`git checkout -- .claude/settings.json`

Run: `python3 -m json.tool .claude/settings.json > /dev/null && echo ok`
Expected: `ok`.

---

### Task 10: Финализация — sync docs, task-done, commits

**Files:**
- Modify: `docs/tasks/T-039-harness-update-sync-settings-json.md` (лог + status)
- Modify: `docs/tasks/INDEX.md` (закрытие)
- Verify: содержимое Части C в `.claude/skills/harness-update.md` идентично `~/.claude/skills/setup-agent-harness/references/skills/harness-update.md`.

- [ ] **Step 1: Diff-sanity harness ↔ project.**

Run:
```bash
diff -u <(sed -n '/^## Часть C /,/^## Общее /p' /Users/aleksandrpletnev/sandbox/ultimatum-game/.claude/skills/harness-update.md) \
        <(sed -n '/^## Часть C /,/^## Общее /p' ~/.claude/skills/setup-agent-harness/references/skills/harness-update.md)
```
Expected: пусто (файлы синхронизированы).

Если различия — привести к единому виду и повторить.

- [ ] **Step 2: Обновить T-039.**

В `docs/tasks/T-039-harness-update-sync-settings-json.md` — добавить в `## Лог`:

```
- 2026-07-13: Часть C реализована. Source-of-truth — references/settings-hooks/*.json, tracking через harness-sync-state.json.hooks (sha256 canonical). Обкатано 6 сценариев (adopt/no-op/local-customization/user-disabled/new/deprecated/broken-json). Push в оба репо.
```

Обновить frontmatter: `status: done`, `updated: 2026-07-13`.

- [ ] **Step 3: Обновить INDEX.md.**

Переместить строку T-039 из «Открытые задачи» в «Закрытые задачи» с датой `2026-07-13`.

- [ ] **Step 4: Commit проектных изменений.**

```bash
cd /Users/aleksandrpletnev/sandbox/ultimatum-game
git add .claude/skills/harness-update.md docs/tasks/T-039-harness-update-sync-settings-json.md docs/tasks/INDEX.md
git commit -m "$(cat <<'EOF'
feat(T-039): /harness-update Часть C — sync .claude/settings.json hooks

- sha256-tracked recommended hooks in references/settings-hooks/*.json
- classify new / identical / upstream update / local customization /
  user-disabled / adopt / deprecated
- hooks-only scope; не трогаем permissions/env/model/statusLine
- verified 6 scenarios on ultimatum-game

Refs: docs/tasks/T-039-harness-update-sync-settings-json.md
EOF
)"
git push origin main
```

- [ ] **Step 5: Убедиться, что self-review отработает.**

Согласно правилу «после task-done» — прогнать `/self-review` (harness Auto-mode). Заметки к следующей задаче (если есть) — завести отдельными тасками.

---

## Self-Review (запись самой планировщицы)

**Spec coverage:**
- Классификация 7 case'ов → Task 2 (описание) + Tasks 4-8 (по одному сценарию на case, кроме identical, который проверяется в Task 4 Step 4).
- Deprecated + gitignore cleanup → Task 8.
- Broken JSON abort → Task 9.
- Миграция template → JSON → Task 1.
- Синхронизация с harness-репо → Task 3.
- Backup → шаг 5 алгоритма в Task 2.
- Adopt — Task 4 (Step 3).

**Placeholder scan:** нет TBD/TODO; все commit-сообщения даны буквально; hook-JSON, шаги verification, expected outputs — конкретны.

**Type consistency:** `id`/`event`/`hook`/`gitignore` — одинаковы во всех задачах. `harness-sync-state.json` секция `hooks` — единообразна. `sha256 canonical(hook)` — везде тот же алгоритм (sort_keys, ensure_ascii=false, separators (',', ':')).

Готово.
