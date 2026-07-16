#!/usr/bin/env python3
"""
Stop-hook guard: решает, надо ли требовать /self-review после нового commit'а.

Правила skip'а — синхронизированы с .claude/skills/self-review.md, раздел
«Что НЕ является поводом»:

1. Task-add / task-sync коммиты (правки только `docs/tasks/*`).
2. `docs(...)`-коммиты — правки только `docs/**`.
3. Commit-message начинается с `chore(tasks):` — task-add / task-sync через
   наши skill'ы.

Триггер (block) — если хоть один path из diff'а трогает исходники (`src/**`),
build-конфиг (`build.gradle*`), harness (`.claude/**`, `CLAUDE.md`) или
frontend-integration (`frontend-integration/**` без `specs/` — specs
регенерируются автоматически).

Вход:
- argv[1] — short sha (для сообщения).
- argv[2] — commit message subject.
- env PATHS — вывод `git diff-tree --no-commit-id --name-only -r <sha>`,
  переносы строк как разделители.

Выход:
- exit 0, без stdout — skip (Stop не блокируется).
- exit 0 с JSON `{"decision":"block","reason":"..."}` — Stop блокируется,
  агент должен запустить /self-review.
"""

import json
import os
import sys


def main() -> int:
    if len(sys.argv) < 3:
        return 0
    short = sys.argv[1]
    msg = sys.argv[2]
    paths = [p for p in os.environ.get("PATHS", "").splitlines() if p]

    is_task_chore = msg.startswith("chore(tasks):")

    only_docs = bool(paths) and all(p.startswith("docs/") for p in paths)

    touches_impl = any(
        p.startswith((
            "src/",
            "build.gradle",
            ".claude/",
            "CLAUDE.md",
        ))
        or (p.startswith("frontend-integration/") and not p.startswith("frontend-integration/specs/"))
        for p in paths
    )
    is_docs_scope_only = msg.startswith("docs(") and not touches_impl

    if is_task_chore or only_docs or is_docs_scope_only:
        return 0

    reason = (
        f"В этой итерации был commit {short}: {msg}. "
        "По правилу harness (self-review в Auto-mode) — прогони /self-review до ответа пользователю."
    )
    print(json.dumps({"decision": "block", "reason": reason}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
