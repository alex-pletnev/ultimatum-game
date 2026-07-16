---
id: T-092
title: Stop-hook «Checking for new commit» — не триггерить self-review на task-add / только-docs коммиты
status: done
priority: medium
created: 2026-07-16
updated: 2026-07-16
related_code:
  - .claude/settings.json
related_docs:
  - .claude/skills/self-review.md
tags: [meta, hooks]
---

## Контекст

Замечено в сессии T-087/T-091. Stop-hook «Checking for new commit» блокирует
ответ пользователю на **любой** новый commit, требуя `/self-review`. Но правило
skill'а (`.claude/skills/self-review.md` → «Что НЕ является поводом») явно
исключает:

- Задачи tривиальные (typo/rename).
- Правки только `docs/**` или `docs/tasks/*`.
- Статус `cancelled`.

Значит hook триггерит false-positive'ы на:

- `chore(tasks): add T-XXX` (task-add — только `docs/tasks/*`).
- `docs(...)` — правки документации.
- Правки только `docs/tasks/INDEX.md`.

Каждый такой false-positive заставляет агента писать «self-review skipped by
rule» вместо ответа. Плюс порождает петлю: task-add → hook → response → next
task-add → hook → ...

## Acceptance criteria

- [x] Hook вынесен из inline-JSON в `.claude/hooks/self-review-guard.py` (тестируемо).
- [x] Skip: `chore(tasks): ...`, только `docs/**`, `docs(...)` без изменений в исходниках.
- [x] Триггер: `src/**`, `build.gradle*`, `.claude/**`, `CLAUDE.md`, `frontend-integration/**` (кроме `specs/` — авто-регенерация).
- [x] Проверено на 5 sample-inputs: task-add → skip, docs-only → skip, src-fix → block, fe-doc → block, specs-only → block (edge, реально не встречается).
- [x] `.claude/skills/self-review.md` → секция «Auto-mode» описывает enforcement и правила skip'а.

## План

Micro-edit hook JSON. Поменять python3-скрипт: после определения `current`/`msg` —
получить `git diff-tree --no-commit-id --name-only -r HEAD` и check:

```python
paths = git_paths.splitlines()
only_docs = all(p.startswith('docs/') for p in paths)
is_task_add = msg.startswith('chore(tasks): add T-')
is_docs_only = msg.startswith('docs(') and not any(
    p.startswith(('src/', 'build.gradle', '.claude/')) for p in paths
)
if only_docs or is_task_add or is_docs_only:
    sys.exit(0)  # no block
```

Проверить: settings.json — self-modification zone, требует явного разрешения
пользователя.

## Лог

- 2026-07-16: заведено self-review'ом T-087 → замечание из чата после T-091.
- 2026-07-16: guard вынесен в `.claude/hooks/self-review-guard.py`, settings.json теперь вызывает его вместо inline-Python. 5 sample-inputs проверены руками. `self-review.md` обновлён — секция «Auto-mode» упоминает enforcement через guard. Закрыто.
