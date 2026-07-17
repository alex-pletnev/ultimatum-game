---
id: T-103
title: setup-agent-harness — CI/CD template как opt при init (по обкатке T-101)
status: pending
priority: medium
created: 2026-07-17
updated: 2026-07-17
related_code:
  - ~/.claude/skills/setup-agent-harness/references/playbook.md
  - ~/.claude/skills/setup-agent-harness/references/templates/
related_docs:
  - docs/14-cicd.md
  - docs/tasks/T-101-cicd-pipeline.md
tags: [meta, harness]
---

## Контекст

T-101 в этом проекте выкристаллизовал полный CI/CD-шаблон:
- `.github/workflows/ci.yml` — check на PR/push.
- `.github/workflows/release.yml` — manual dispatch → build+push → SSH-redeploy c auto-rollback.
- `scripts/vm-redeploy.sh` — атомарный swap с health-check.
- `docs/14-cicd.md` runbook.

Harness сейчас не спрашивает про CI/CD. Стоит добавить opt при `/setup-agent-harness init`:
«Нужен CI/CD (GitHub Actions + опционально release-workflow)?» — если да, генерировать шаблоны + правило в CLAUDE.md «релиз только через workflow, не руками».

## Acceptance criteria

- [ ] Playbook harness'а — новый шаг «CI/CD template» между Skills install и CLAUDE.md render.
- [ ] Templates: generic `ci.yml` (setup-<lang> + test-command placeholder), опциональный `release.yml` skeleton с placeholder'ами.
- [ ] Правило в CLAUDE.md template: «релиз через workflow, не через SSH руками».
- [ ] Runbook template `docs/NN-cicd.md`.

## План

1. Собрать шаблоны из ultimatum-game (T-101), заменить hardcoded IP/registry на placeholder'ы.
2. Расширить playbook + `SKILL.md` описание opt.
3. Обкатать на новом сервисе.

## Лог

- 2026-07-17: заведена в ходе Волны 3 meta-задач после T-101 (по запросу пользователя добавить best-practices в setup-agent-harness).
