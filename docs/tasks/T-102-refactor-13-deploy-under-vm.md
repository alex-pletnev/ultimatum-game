---
id: T-102
title: Refactor docs/13-deploy.md — разделы B.1-B.7 под VM-архитектуру (исторический Serverless-текст)
status: pending
priority: low
created: 2026-07-17
updated: 2026-07-17
related_code:
  - docs/13-deploy.md
  - scripts/deploy-yc-vm.sh
related_docs:
  - docs/tasks/T-090-prod-deploy-readiness.md
  - docs/tasks/T-101-cicd-pipeline.md
tags: [tech-debt, docs]
---

## Контекст

`docs/13-deploy.md` разделы 13.4 B.1-B.7 написаны под изначальный вариант деплоя
через YC Serverless Container. После T-090 (pivot на Compute VM) актуальный
скрипт — `scripts/deploy-yc-vm.sh`, а разделы B.1-B.7 описывают реальность,
которой на проде уже нет (`yc serverless container revision deploy`,
`--min-instances`, execution-timeout и т.д.).

В T-101 добавлен ретро-warning, но полный refactor разделa — отдельная работа.

## Acceptance criteria

- [ ] Раздел 13.4 переписан под то, что реально делает `scripts/deploy-yc-vm.sh`:
  Compute VM + Ubuntu 24.04 + cloud-init + Caddy + docker run.
- [ ] Исторический Serverless-текст либо удалён, либо перенесён в отдельный
  раздел «Appendix — заброшенный Serverless-вариант, почему».
- [ ] Ссылки на `yc serverless container` заменены на `yc compute instance`.
- [ ] Актуальные env-vars, ссылки на `docs/14-cicd.md` для redeploy'а.

## План

1. Прочитать `scripts/deploy-yc-vm.sh` целиком, зафиксировать шаги.
2. Переписать 13.4 под VM-варианте (SA, PG, Lockbox, IP, VM, cloud-init).
3. Serverless-текст в Appendix (или удалить, если нет ценности).

## Лог

- 2026-07-17: заведена автоматически в ходе self-review T-101 (commit 3cdb2a3). Ретро-warning в 13-deploy добавлен в тот же commit; полный refactor вынесен сюда.
