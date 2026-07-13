---
id: T-042
title: Skill'ы, использующие sha/hash, обязать приводить input→expected-output пример
status: pending
priority: medium
created: 2026-07-13
updated: 2026-07-13
related_code:
  - .claude/skills/harness-update.md
  - ~/.claude/skills/setup-agent-harness/references/skills/harness-update.md
related_docs:
  - CLAUDE.md
tags: [meta, harness, skills]
---

## Контекст

`/harness-update` Часть C (v3) полагается на sha256 canonical JSON'а `hook`-объекта. Определение canonical form описано текстом («ключи отсортированы, no indent, `ensure_ascii=false`, separators `(',', ':')`»). При росте набора recommended hooks и/или при генерации hash'а из разных языков (Python vs jq vs Node) есть риск silent divergence: canonical form интерпретируется по-разному → все hash'и разъезжаются → tracking сломан у всех.

Наблюдение — из self-review T-039, категория E. То же самое актуально для любого будущего skill'а harness'а, где используется hash для tracking'а.

## Acceptance criteria

- [ ] В CLAUDE.md (или в мета-skill'е `writing-skills`-подобном) — правило: **skill, использующий sha/hash в качестве contract'а, обязан привести inline пример «известный input → ожидаемый hash»**. Пример должен быть тривиально проверяемым (одна строка кода в двух runtime'ах — Python + jq).
- [ ] Ретроактивно применить к harness-update.md (обе копии): добавить fixture-блок с известным JSON и его sha256, чтобы future-agent мог проверить свой canonicalization.
- [ ] Правило распространить как рекомендацию через `/harness-update` (для новых версий skill'ов).

## План

1. Написать правило в CLAUDE.md → «Проактивные триггеры» или отдельная секция про hash-tracking skill'ы.
2. Добавить fixture в harness-update.md обе копии.
3. Прогнать fixture двумя способами (python3 + jq) — убедиться что hash совпадает.
4. Убедиться, что T-041 (когда возьмут в работу) использует этот же fixture.

## Лог

- 2026-07-13: заведено из self-review T-039. Категория E. Priority medium — пока один такой skill, но паттерн быстро распространится (T-041 добавит collision detection на тот же hash).
