# Интеграция superpowers-skills в наш flow — дизайн

**Дата:** 2026-07-13
**Статус:** approved (brainstorming)
**Связанные задачи:** T-023 (реализация в проекте), T-024 (перенос в harness — pending)

## Цель

Взять `superpowers` (brainstorming, writing-plans, systematic-debugging, verification-before-completion, receiving-code-review, test-driven-development, executing-plans) — популярный набор процессных skill'ов — и вплести в наш существующий flow из 7 skill'ов (`task-add`, `task-done`, `task-sync`, `docs-sync`, `wheel-check`, `mid-retro`, `self-review`) без ломки уже сложившейся привычки.

## Ограничения (принятые в brainstorming'e)

1. **Наши skills — оркестраторы, superpowers — инструменты внутри.** Никаких параллельных наборов и мучительного выбора «каким пользоваться сейчас».
2. **Не переписываем superpowers.** Только зовём.
3. **Не создаём новых skills.** Только правки существующих + CLAUDE.md.
4. **Обкатка в этом проекте, перенос в harness — отдельная задача** (T-024). Сначала живой feedback, потом распространение.

## Точки wire (все 7)

| # | Триггер | Наш skill / условие | Superpowers-skill внутри | Артефакт |
|---|---------|----------------------|--------------------------|----------|
| 1 | `/task-add` для non-trivial задачи (не однострочный фикс) | `task-add` шаг перед созданием файла | `superpowers:brainstorming` | Spec-файл в `docs/superpowers/specs/YYYY-MM-DD-<topic>-design.md`, ссылка в `related_docs` таска |
| 2 | Многошаговая задача, spec есть | между `task-add` и началом кодинга | `superpowers:writing-plans` | Plan-файл в `docs/superpowers/plans/YYYY-MM-DD-<topic>-plan.md` |
| 3 | Перед закрытием любой задачи | `task-done` шаг перед flip'ом status | `superpowers:verification-before-completion` | Отчёт verification в `## Лог` таска |
| 4 | Баг / упавший тест / неожиданное поведение | Проактивный триггер в CLAUDE.md | `superpowers:systematic-debugging` | (нет артефакта — процессная дисциплина) |
| 5 | Внутри финального ретро — перед 5 категориями | `self-review` шаг 2 | `superpowers:receiving-code-review` | Внутренний диалог по diff'у, входит в отчёт self-review |
| 6 | Новая фича с acceptance criteria | Проактивный триггер в CLAUDE.md | `superpowers:test-driven-development` | Тесты пишутся до реализации |
| 7 | Многошаговый plan уже есть, начинаем исполнение | Проактивный триггер в CLAUDE.md | `superpowers:executing-plans` | Прогон плана с checkpoint'ами |

Разграничение обязанностей: наши skills — учёт (файл-таск, INDEX, git), superpowers — процессная дисциплина (design → plan → verify → review).

## Что меняется в файлах

### `.claude/skills/task-add.md`

Новая секция **«Non-trivial задачи: brainstorming первым»** (перед шагом 1):

- Если задача сложнее «поменять строку / переименовать поле / добавить flag» — сначала `superpowers:brainstorming`. Результат → spec-файл в `docs/superpowers/specs/`.
- Ссылка на spec попадает в `related_docs` создаваемого таска.
- Для тривиальных задач — этот шаг skip'ается.

### `.claude/skills/task-done.md`

Новая секция **«Verification gate»** (перед flip'ом status → done):

- Обязательный проход `superpowers:verification-before-completion`.
- Если verification не проходит — status остаётся `in_progress`, в `## Лог` — что именно не сошлось.
- Только после verification-passed — git-commit + status flip.

### `.claude/skills/self-review.md`

Правка шага 2 «Прогнать чек-лист по 5 категориям»:

- Перед прогоном 5 категорий — invoke `superpowers:receiving-code-review` с diff'ом, треактовать себя как autor + reviewer одновременно (внутренний диалог).
- Возражения от «внутреннего reviewer'а» — материал для категорий A-E.

### `CLAUDE.md`

Таблица «Проактивные триггеры» — 3 новые строки:

| Условие | Skill | Что делать |
|---------|-------|-----------|
| Баг / упавший тест / неожиданное поведение | `superpowers:systematic-debugging` | Не пытаться сразу фиксить — сначала systematic-debugging: гипотеза → эксперимент → вывод |
| Новая фича с ясным acceptance criteria | `superpowers:test-driven-development` | До реализации — написать провальные тесты по каждому AC, потом делать реализацию до зелёного |
| Многошаговый plan уже есть (`docs/superpowers/plans/*.md`) | `superpowers:executing-plans` | Прогон плана с checkpoint'ами, не «зачитал и побежал» |

Плюс — небольшая секция **«Superpowers integration»** с картой (7 точек) для быстрой навигации в будущих сессиях.

### Что НЕ меняем

- `.claude/skills/task-sync.md`, `docs-sync.md`, `wheel-check.md`, `mid-retro.md` — без изменений. Их триггеры и обязанности не пересекаются с superpowers.
- Само содержание superpowers-skill'ов — не трогаем.

## Roadmap

### Фаза 1 (эта задача, T-023)

- Внести изменения только в этом проекте.
- Прогнать через 2-3 реальных задачи, где wire-points сработают.
- Замечать что реально помогает / что мешает.

### Фаза 2 (follow-up T-024)

- После обкатки — перенести изменения в `~/.claude/skills/setup-agent-harness/` (репо `alex-pletnev/claude-setup-agent-harness`).
- Обновить `references/skills/task-add.md`, `task-done.md`, `self-review.md`, `templates/claude-md.template.md`.
- Новые проекты через `/setup-agent-harness` получат integration «из коробки».
- Оценить: возможно к этому моменту вылезут ещё wire-points или, наоборот, часть окажется лишней — доработать.

## Границы (что НЕ входит)

- Не создаём новые skills.
- Не переписываем superpowers.
- Не форкаем superpowers-репозиторий.
- Не добавляем hooks в `settings.json` (пока — если после обкатки окажется что triggers не срабатывают, вернёмся к hooks в отдельной задаче).
- Не автоматизируем cross-skill state (например, «пометить spec как approved» — процесс живёт в диалоге, не в файлах).

## Что было отвергнуто в brainstorming'e

- **Полная замена наших skills на superpowers** — переучивание, потеря учётной части (файл-таски + INDEX + git).
- **Параллельные наборы (наши vs superpowers по типу задачи)** — плодят выбор «каким пользоваться».
- **Мини-версия (D)** — обкатать только 2-3 самых нужных — потеряли бы связность процесса.
- **Немедленный wire в harness** — не проверено на живой работе.
