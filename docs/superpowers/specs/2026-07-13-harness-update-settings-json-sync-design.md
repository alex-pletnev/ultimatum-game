# Design — /harness-update Часть C: sync `.claude/settings.json` hooks

Дата: 2026-07-13
Связанные: T-039, T-030, T-035, T-036, T-037.

## Контекст

`/harness-update` в текущем виде (Часть A + Часть B) синхронизирует `.claude/skills/*` и `CLAUDE.md`. Настройки `.claude/settings.json` (hooks проекта) — не трогает. Из-за этого:

- Второй Stop-hook / PreCompact / любой новый recommended hook в harness'е не подхватится существующими проектами автоматически.
- User'ские кастомизации и recommended hooks сейчас неотличимы.
- Ручной перенос fragment'а из markdown-документации трудоёмок и подвержен ошибкам.

Задача — добавить Часть C, синхронизирующую hooks из `.claude/settings.json` с recommended-набором из harness-репо, устойчиво к: (1) user'ским custom-hook'ам, (2) локальным правкам команд recommended hook'ов, (3) сознательному disable'у, (4) deprecation'у со стороны harness'а.

Scope: **hooks-only**. Не трогаем `permissions`, `env`, `model`, `statusLine` и прочие ключи `settings.json` — они user-specific, sync им скорее вредит.

## Архитектура

Новый раздел `## Часть C — Sync .claude/settings.json (hooks)` в `.claude/skills/harness-update.md`. Триггер: наличие непустой директории `~/.claude/skills/setup-agent-harness/references/settings-hooks/`. Отсутствует/пуста → Часть C skip'ается без ошибки.

Механически повторяет Часть A (sha256-based tracking через `harness-sync-state.json`), но работает с JSON-мутациями массивов, а не с целыми файлами.

### Source of truth в harness-репо

`~/.claude/skills/setup-agent-harness/references/settings-hooks/<id>.json` — по одному файлу на recommended hook:

```json
{
  "id": "self-review-reminder",
  "event": "Stop",
  "hook": {
    "type": "command",
    "command": "cd \"$(git rev-parse --show-toplevel ...)\" ...",
    "timeout": 5,
    "statusMessage": "Checking for new commit"
  },
  "gitignore": [".claude/last-committed-head.txt"]
}
```

Поля:
- `id` — уникальный идентификатор hook'а (kebab-case). Используется как ключ в state.
- `event` — Claude Code hook event (`Stop`, `PreCompact`, `UserPromptSubmit`, ...).
- `hook` — объект, который будет вставлен в массив `settings.json.hooks[event][*].hooks[]`.
- `gitignore` — список строк, которые нужно append'ить в `.gitignore` проекта при активации (dedup через grep).

Существующий `references/settings-json-template.md` остаётся как человеко-читаемая документация «зачем каждый hook нужен». Часть C её **не парсит** — только JSON-файлы.

Миграция: содержимое единственного текущего Stop-hook'а из `settings-json-template.md` переносится в `references/settings-hooks/self-review-reminder.json`. Markdown-дока обновляется — упоминает, что source-of-truth теперь JSON, приводит ссылки.

### Tracking через `harness-sync-state.json`

Расширяем существующий файл дополнительным разделом `hooks`:

```json
{
  "harness_sha": "<git-sha>",
  "files": { "<skill.md>": "<sha256>" },
  "hooks": { "<id>": "<sha256-of-canonical-hook-json>" }
}
```

Хэш считается от **canonical JSON'а всего объекта `hook`** (ключи отсортированы, отступов нет, unicode escape'ы нормализованы). Это ловит любое изменение — `command`, `timeout`, `statusMessage`, любое новое поле. Не хэшируем `id`/`event`/`gitignore` — они метаданные, а не поведение самого hook'а.

### Идентификация hook'а внутри массива settings.json

`settings.json.hooks.<event>` — массив блоков `{ "hooks": [{...}, ...] }`. Recommended hook лежит где-то внутри одного из блоков.

Matching для recommended `id=X`:

1. Если в state есть `hooks.X = sha_state`:
   - Пройти по `settings.json.hooks[event][*].hooks[*]` и найти элемент с `sha256(canonical(elem)) == sha_state`.
   - Найден → это наш hook (обновить/удалить/оставить в зависимости от классификации).
   - Не найден → **user-disabled** (Case 1).
2. Если в state нет `hooks.X`, но в settings.json есть элемент с `sha256(canonical(elem)) == recommended-sha` → **adopt**: hook уже установлен вручную, записать в state, дальше классифицируется как identical.
3. Иначе → **new**.

### Классификация

Для каждого recommended hook (по id):

| Case | Условие | Действие |
|------|---------|----------|
| new | не в state, не в settings.json | добавить в settings.json |
| identical | sha в settings.json == recommended-sha | skip |
| upstream update | sha в settings.json == state-sha, но state-sha != recommended-sha | заменить |
| local customization | sha в settings.json != state-sha | ⚠ warn, спросить apply|skip |
| user-disabled | id в state, hook не найден в settings.json | skip, показать в summary |
| adopt | не в state, sha в settings.json == recommended-sha | записать в state, treat as identical |

Отдельно — **deprecated**: id в state, JSON'а в references больше нет. Действие: предложить удалить hook из settings.json + связанные `.gitignore` строки. Y/n per hook.

## Алгоритм apply

1. **Discover.** Прочитать `references/settings-hooks/*.json`, project `.claude/settings.json` (missing → `{}`), `harness-sync-state.json.hooks` (missing → `{}`).

2. **Classify** — по таблице выше.

3. **Summary + confirm.** Единый output:
   ```
   Часть C — .claude/settings.json:
   Добавлю:
     - <id> (event: Stop)
   Обновлю (upstream update):
     - <id> (event: Stop)
   ⚠ ЛОКАЛЬНАЯ кастомизация (будет ПОТЕРЯНА):
     - <id> (command изменён локально после последнего sync)
   Skip:
     - <id> (identical)
     - <id> (user-disabled)
   Deprecated (harness убрал рекомендацию):
     - <id> — удалить hook из settings.json? (y/n на каждый)
   .gitignore: добавлю строки: <line1>, <line2>
   Backup: .claude/settings.json.bak-YYYY-MM-DD-HHMMSS
   Продолжить? (y/n)
   ```
   Если есть LOCAL CUSTOMIZATION — отдельная плашка перед основным prompt'ом с советом «перенеси в custom-hook файл до продолжения».

4. **Wait for user.** Молчание/`n`/уточнение — не трогать файлы.

5. **Backup.** `cp .claude/settings.json .claude/settings.json.bak-...`. Если settings.json отсутствовал и Часть C создаёт его — backup'ить нечего, пропустить.

6. **Apply hooks** (in-memory JSON):
   - **new** — найти блок с нужным event или создать `{ "hooks": [] }`, append `hook`-объект.
   - **upstream update** — заменить in-place элемент, найденный по state-sha.
   - **deprecated (accepted)** — удалить элемент, найденный по state-sha; если родительский блок остался пустым (`hooks: []`) — удалить и блок; если массив event'а стал пуст — удалить ключ.
   - **local customization (accepted)** / **user-disabled** / **identical** / **adopt** — по правилам выше.

7. **Apply .gitignore.** Для каждого applied hook'а (new/adopted upstream) — append строки из `hook.gitignore`, если grep их не находит. Deduplicate между hook'ами (два hook'а требуют одну строку — append один раз).
   - Для deprecated (accepted): для удалённого hook'а — предложить удалить его `gitignore`-строки (y/n), только если ни один другой applied hook их не требует.

8. **Write.** `settings.json` — pretty-print JSON с 2-space indent (совместимо с текущим стилем). `harness-sync-state.json.hooks` — обновить sha для applied hooks, удалить для deprecated. `.gitignore` — атомарная запись через temp+rename.

9. **Commit + push.** По правилам git-автоматизации. Отдельный commit:
   ```
   chore(harness): sync .claude/settings.json hooks from references

   - added: <ids>
   - updated: <ids>
   - deprecated (removed): <ids>
   - harness sha: <sha>

   Refs: docs/tasks/T-039-*.md
   ```
   Files: `.claude/settings.json`, `.claude/harness-sync-state.json`, `.gitignore`, `.claude/settings.json.bak-*` (backup тоже в commit — для лёгкого revert).

10. **Отчёт:** «Часть C: added N, updated M, deprecated K, warned L».

## Взаимодействие с Частями A и B

Порядок в `harness-update`: A → B → C. Каждая — свой commit. Пользователь может остановиться на любом (y/n prompt per part). Consolidated no-op (все три сказали «нечего делать») — единый отчёт без commit'ов.

`harness-sync-state.json` теперь имеет три раздела: `harness_sha` (пишется всеми), `files` (Часть A), `hooks` (Часть C). Часть B состояние не хранит — она рендерит CLAUDE.md из `harness-config.json` в один клик, детект customization CLAUDE.md — вне scope'а v2.

## Edge cases

- **settings.json отсутствует в проекте.** Если есть хоть один applicable new hook — создать `{ "hooks": { "<event>": [{ "hooks": [<hook>] }] } }` (с `$schema`). Спросить у user'а подтверждение отдельно («В проекте нет settings.json, создать? y/n»).
- **settings.json содержит только user-custom hooks.** Часть C трогает только элементы, matched по state-sha или recommended-sha (для adopt). Всё остальное — не касается.
- **Битый JSON в settings.json.** Парсинг падает → Часть C аборт с сообщением «settings.json невалидный, почини вручную, потом снова /harness-update». State не трогаем.
- **Битый JSON в references/settings-hooks/<id>.json.** Skip этот файл, warn user'а, продолжить с остальными.
- **`command` содержит heredoc/mmulti-line/скрытые unicode.** Canonical JSON serialize + sha256 нормализуют — matching стабилен.
- **User переупорядочил массив hooks.** Matching по sha, не по индексу → устойчиво.
- **User переместил hook между event'ами (Stop → PreCompact).** С точки зрения matching — hook «пропал из Stop» = user-disabled для одного recommended, а новый в PreCompact — не наш (нет id-match). Не трогаем. Consumer effect: user должен знать что «переносить hook между event'ами» ломает tracking; документируем в «Ограничения».

## Что НЕ делает Часть C

- Не трогает `permissions`, `env`, `model`, `statusLine` и прочие ключи `settings.json`.
- Не трогает `.claude/settings.local.json`.
- Не меняет порядок элементов массивов hooks (стабильный append/in-place replace/удаление).
- Не восстанавливает user-disabled hook'и (без явного `--force`, который не реализуем в v1).
- Не пытается rollback'нуть после ошибки apply — user делает `git revert`.

## Ограничения

- **Перенос hook'а между event'ами** ломает tracking (см. edge case выше). Документируется.
- **Одинаковые command'ы у двух recommended hook'ов** — теоретически sha-collision. Практически исключено (id'ы разные → семантика разная → command'ы разные). Если возникнет — детект: state пишет одну и ту же sha под двумя ключами → warn при apply.
- **Дизайн hook'а в harness'е** должен ограничивать `hook`-объект хорошо-сериализуемым JSON'ом. Regex'ов/binary'ей не будет — только stringly command'ы.

## Тестирование

Ручная обкатка на этом же проекте (ultimatum-game):

1. **No-op на текущем состоянии.** Current `.claude/settings.json` совпадает с recommended для `self-review-reminder`. state должен adopt'нуть → следующий прогон говорит «identical, ничего не делаю».
2. **Local customization.** Изменить `command` в проектном settings.json (например, поменять текст reminder'а). Прогон должен пометить «⚠ LOCAL CUSTOMIZATION», предложить skip/overwrite.
3. **User-disabled.** Удалить hook из settings.json (state оставить). Прогон должен пометить «user-disabled, skip».
4. **New hook.** Добавить второй JSON в `references/settings-hooks/` (тестовый placeholder). Прогон должен предложить «Добавлю: <id>». Rollback после теста.
5. **Deprecated.** Удалить JSON из references, оставить hook в settings.json + state. Прогон должен предложить удалить.
6. **Битый settings.json.** Симулировать → аборт с внятной ошибкой.

## Deliverables

1. Правки `.claude/skills/harness-update.md` — новый раздел «Часть C».
2. Обновление `harness-sync-state.json` schema (документация в самом skill'е).
3. Миграция в harness-репо: `references/settings-hooks/self-review-reminder.json` + правки `references/settings-json-template.md` (ссылка на JSON).
4. Правки `~/.claude/skills/setup-agent-harness/references/skills/harness-update.md` — зеркальная версия для харнесса.
5. Обкатка сценариев 1-6 на этом проекте.
6. Commit + push в оба репо.

## Открытые вопросы

Нет — все развилки закрыты в brainstorming.
