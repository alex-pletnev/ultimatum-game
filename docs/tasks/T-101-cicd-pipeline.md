---
id: T-101
title: CI/CD pipeline — GitHub Actions (check на PR/push + manual release на прод-VM)
status: in_progress
priority: high
created: 2026-07-17
updated: 2026-07-17
related_code:
  - .github/workflows/ci.yml
  - .github/workflows/release.yml
  - scripts/vm-redeploy.sh
  - scripts/deploy-yc.sh
  - docs/14-cicd.md
related_docs:
  - docs/superpowers/specs/2026-07-17-cicd-pipeline-design.md
  - docs/tasks/T-090-prod-deploy-readiness.md
  - docs/13-deploy.md
tags: [infra, cicd, deploy]
---

## Контекст

После T-090 (prod live на YC Compute VM, https://158-160-48-113.nip.io/api/v1)
нужна автоматизация проверок и релиза. Сейчас `./gradlew check` крутится только
локально, любой deploy — ручной запуск `scripts/deploy-yc-vm.sh` с пересозданием
VM целиком.

Дизайн-решения зафиксированы в spec'е (см. `related_docs`):
- CI на pull_request + push в `main`.
- Release — только `workflow_dispatch` (manual, guarded).
- Docker image tag = `:$SHORT_SHA` + `:latest`.
- Redeploy через SSH из GHA + `/opt/app/redeploy.sh` на VM с health-check и auto-rollback на предыдущий SHA.
- Мёртвый `scripts/deploy-yc.sh` (Serverless-вариант) удаляется.

## Acceptance criteria

- [ ] `.github/workflows/ci.yml` — `./gradlew check` зелёный на PR и push в `main`.
- [ ] `.github/workflows/release.yml` — `workflow_dispatch` c input'ом `sha`; job'ы `build-and-push` (docker build+push в `cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game`) → `deploy` (SSH + `redeploy.sh`).
- [ ] `scripts/vm-redeploy.sh` в репо + скопирован на VM в `/opt/app/redeploy.sh`. Атомарный swap, health-check `/actuator/health` 60s, auto-rollback на PREV_SHA при провале.
- [ ] GH secrets: `YC_SA_JSON_CREDENTIALS` (SA-key для CR push+pull) и `VM_SSH_PRIVATE_KEY` (ключ для deploy) — заведены через `gh secret set`.
- [ ] SA `utg-sa` имеет роль `container-registry.images.pusher`.
- [ ] `scripts/deploy-yc.sh` удалён (заодно очищается unstaged `M`-правка).
- [ ] `docs/14-cicd.md` — runbook: bootstrap (что сделал агент / где секреты), как выкатить релиз, как откатиться руками, ротация ключей, диагностика.
- [ ] `docs/README.md` + `docs/13-deploy.md` — ссылка на 14-cicd.
- [ ] End-to-end проверено: (a) первый `workflow_dispatch` из GH UI выкатывает текущий main-HEAD, `docker inspect` на VM показывает новый SHA, `/actuator/health` UP; (b) искусственный failing deploy → auto-rollback на PREV_SHA, VM жива.

## План

Полный порядок в spec'е (`## Реализация — порядок работ`). Краткий чек-лист:

1. `writing-plans` → детальный implementation-план в `docs/superpowers/plans/`.
2. Написать workflows + `vm-redeploy.sh` + `docs/14-cicd.md`. Удалить `scripts/deploy-yc.sh`.
3. `./gradlew check` локально (sanity).
4. Commit + push (workflow'ы в main нужны, чтобы GH их увидел).
5. Bootstrap (агент):
   - `yc iam key create --service-account-name utg-sa` → JSON.
   - `yc container registry add-access-binding --role container-registry.images.pusher`.
   - `gh secret set YC_SA_JSON_CREDENTIALS`.
   - `ssh-keygen -t ed25519 -f /tmp/utg-deploy`.
   - `gh secret set VM_SSH_PRIVATE_KEY`.
6. Bootstrap (пользовательский `y` на SSH-permission):
   - `ssh-copy-id` pubkey на VM.
   - `scp scripts/vm-redeploy.sh ubuntu@…:/opt/app/redeploy.sh`.
   - Sanity: `ssh ubuntu@… 'bash /opt/app/redeploy.sh $CURRENT_SHA'`.
7. End-to-end: dispatch первого release через GH UI, проверить SHA на VM.
8. Rollback-тест (искусственный failing SHA).
9. `task-done` → self-review.

## Лог

- 2026-07-17: заведена. Design в `docs/superpowers/specs/2026-07-17-cicd-pipeline-design.md` одобрен пользователем. Пользователь явно разрешил SSH в прод-VM для bootstrap-шагов.
- 2026-07-17: Task 1 — `.github/workflows/ci.yml` добавлен. Первый run на push в `main` — green за 214s (`https://github.com/alex-pletnev/ultimatum-game/actions/runs/29584836018`). Локальный `./gradlew check` перед push тоже green (кэш, 6s).
- 2026-07-17: status → in_progress.
