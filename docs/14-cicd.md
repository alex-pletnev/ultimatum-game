# 14. CI/CD

GitHub Actions pipeline'ы для проекта — автоматический `./gradlew check`
на каждый PR/push и manual-release на прод-VM в YC.

Контекст: T-101.

## 14.1 Обзор

| Workflow | Файл | Триггер | Что делает |
|----------|------|---------|-----------|
| CI | `.github/workflows/ci.yml` | `pull_request: [main]` + `push: [main]` | `./gradlew check` (test + jacoco 0.80 + detekt). Без секретов. |
| Release | `.github/workflows/release.yml` | `workflow_dispatch` (manual через UI или `gh workflow run`) | Docker build+push в `cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game:{$SHORT_SHA,latest}` → SSH на VM → `/opt/app/redeploy.sh <sha>` с health-check и auto-rollback. |

Скрипт redeploy'а на VM живёт в repo (`scripts/vm-redeploy.sh`) и копируется на VM в `/opt/app/redeploy.sh` при bootstrap'е.

## 14.2 Что где хранится

| Артефакт | Где | Как обновить |
|----------|-----|-------------|
| CI workflow | `.github/workflows/ci.yml` | обычный commit + push |
| Release workflow | `.github/workflows/release.yml` | обычный commit + push |
| Redeploy-скрипт (repo) | `scripts/vm-redeploy.sh` | обычный commit + push |
| Redeploy-скрипт (VM) | `ubuntu@158.160.48.113:/opt/app/redeploy.sh` | `scp scripts/vm-redeploy.sh …:/tmp/ && ssh 'sudo mv /tmp/…'` (см. 14.3 Step 4) |
| GH secret `YC_SA_JSON_CREDENTIALS` | GitHub → Settings → Secrets → Actions | `gh secret set YC_SA_JSON_CREDENTIALS < sa-key.json` |
| GH secret `VM_SSH_PRIVATE_KEY` | там же | `gh secret set VM_SSH_PRIVATE_KEY < ssh-private-key` |
| Prod-secrets (JWT_KEY, DB_PASSWORD) | YC Lockbox `utg-secrets` (`e6qaub97dnasecoh2k8t`) | `yc lockbox secret add-version` — см. `docs/13-deploy.md` |
| SA role `container-registry.images.pusher` | YC IAM binding на `utg-sa` | `yc container registry add-access-binding …` |
| Deploy SSH pubkey | `ubuntu@158.160.48.113:~/.ssh/authorized_keys` | ручное дописывание строки с комментарием `gh-actions-deploy-T-101` |

## 14.3 Bootstrap (историческая справка + инструкция «с нуля»)

Выполнено 2026-07-17 в рамках T-101. Здесь — точные команды на случай пересоздания VM или ротации.

### Step 1: SA-key

```bash
SA_ID=$(yc iam service-account list --format json | jq -r '.[] | select(.name=="utg-sa") | .id')
yc iam key create --service-account-id "$SA_ID" \
  --description "GitHub Actions release workflow — T-101" \
  --output /tmp/sa-key.json
```

### Step 2: Role pusher

```bash
yc container registry add-access-binding --id crp7b8ldv830cfseac0e \
  --role container-registry.images.pusher \
  --subject "serviceAccount:$SA_ID"
```

`utg-sa` уже имеет `container-registry.images.puller` + `lockbox.payloadViewer` из T-090 (для cloud-init'а VM).

### Step 3: GH secret `YC_SA_JSON_CREDENTIALS`

```bash
gh secret set YC_SA_JSON_CREDENTIALS \
  --repo alex-pletnev/ultimatum-game \
  < /tmp/sa-key.json
```

Format — cпец. `json_key` (см. `docker/login-action@v3` documentation): содержимое SA-key JSON в поле `password`.

### Step 4: Deploy SSH-keypair

```bash
ssh-keygen -t ed25519 -f /tmp/utg-deploy -N '' -C 'gh-actions-deploy-T-101'
gh secret set VM_SSH_PRIVATE_KEY \
  --repo alex-pletnev/ultimatum-game \
  < /tmp/utg-deploy
```

Pubkey — на VM:

```bash
cat /tmp/utg-deploy.pub | ssh ubuntu@158.160.48.113 \
  'cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys'
```

### Step 5: Redeploy-скрипт на VM

```bash
scp -i /tmp/utg-deploy -o IdentitiesOnly=yes \
  scripts/vm-redeploy.sh \
  ubuntu@158.160.48.113:/tmp/redeploy.sh
ssh -i /tmp/utg-deploy -o IdentitiesOnly=yes ubuntu@158.160.48.113 \
  'sudo mv /tmp/redeploy.sh /opt/app/redeploy.sh && sudo chmod +x /opt/app/redeploy.sh'
```

### Step 6: Cleanup

```bash
shred -u /tmp/sa-key.json /tmp/utg-deploy /tmp/utg-deploy.pub 2>/dev/null \
  || rm -f /tmp/sa-key.json /tmp/utg-deploy /tmp/utg-deploy.pub
```

## 14.4 Как выкатить релиз

**UI (обычный вариант):** GitHub → Actions → Release → «Run workflow» → `sha` пусто (= HEAD main) или конкретный SHA → «Run workflow».

**CLI:**

```bash
gh workflow run release.yml --repo alex-pletnev/ultimatum-game
# или конкретный SHA:
gh workflow run release.yml -f sha=<full-sha> --repo alex-pletnev/ultimatum-game
```

**Watch:**

```bash
gh run watch $(gh run list --workflow=release.yml --limit 1 --json databaseId -q '.[0].databaseId') --repo alex-pletnev/ultimatum-game
```

Ожидаемое время: build+push ~5-8 мин (cache miss первый раз, потом ~2-3 мин), deploy 1-2 мин. Downtime ~5-10s.

## 14.5 Rollback

**Автоматический** — `redeploy.sh` сам откатит на предыдущий SHA если `/actuator/health` не поднимется за 60s. Workflow вернётся red, VM останется на PREV_SHA.

**Ручной** — если auto-rollback не сработал (не было PREV_SHA, или rollback тоже упал):

```bash
# Найти последний known-good SHA (из истории gh run list --workflow=release.yml)
gh run list --workflow=release.yml --status success --limit 5 --repo alex-pletnev/ultimatum-game
# Триггернуть с этим SHA
gh workflow run release.yml -f sha=<good-sha> --repo alex-pletnev/ultimatum-game
```

Крайний случай (VM в broken state): SSH напрямую, `docker run` known-good tag:

```bash
ssh ubuntu@158.160.48.113 \
  'sudo bash /opt/app/redeploy.sh <short-sha>'
```

## 14.6 Ротация ключей

### SA-key

```bash
# 1. Создать новый
SA_ID=$(yc iam service-account list --format json | jq -r '.[] | select(.name=="utg-sa") | .id')
yc iam key create --service-account-id "$SA_ID" --output /tmp/sa-key-new.json
# 2. Обновить GH secret
gh secret set YC_SA_JSON_CREDENTIALS --repo alex-pletnev/ultimatum-game < /tmp/sa-key-new.json
# 3. Проверить релизом (dispatch)
gh workflow run release.yml --repo alex-pletnev/ultimatum-game
# 4. Удалить старый key (найти по created_at)
yc iam key list --service-account-id "$SA_ID"
yc iam key delete --id <OLD_KEY_ID>
# 5. Cleanup
shred -u /tmp/sa-key-new.json
```

### SSH-key

```bash
# 1. Создать новый
ssh-keygen -t ed25519 -f /tmp/utg-deploy-new -N '' -C 'gh-actions-deploy-T-101-rotated'
# 2. Положить pubkey на VM (не удаляя старый)
cat /tmp/utg-deploy-new.pub | ssh ubuntu@158.160.48.113 \
  'cat >> ~/.ssh/authorized_keys'
# 3. Обновить GH secret
gh secret set VM_SSH_PRIVATE_KEY --repo alex-pletnev/ultimatum-game < /tmp/utg-deploy-new
# 4. Проверить релизом
gh workflow run release.yml --repo alex-pletnev/ultimatum-game
# 5. Удалить старую строку из authorized_keys (искать по comment'у)
ssh ubuntu@158.160.48.113 \
  "sed -i '/gh-actions-deploy-T-101 /d' ~/.ssh/authorized_keys"
# 6. Cleanup
shred -u /tmp/utg-deploy-new /tmp/utg-deploy-new.pub
```

## 14.7 Диагностика

| Симптом | Куда смотреть |
|---------|--------------|
| CI red | `gh run view <id> --log-failed --repo alex-pletnev/ultimatum-game` |
| Release red на `build-and-push` | тот же `gh run view` — секция build step (docker-build logs) |
| Release red на `deploy` | тот же `gh run view` — секция SSH step. Полный лог redeploy — на VM в `/var/log/utg-redeploy.log`. |
| Health-check timeout после deploy | лог `redeploy.sh` покажет `deploy FAILED (health-check timeout)`. Ниже — вывод `docker logs --tail 100 ultimatum-game`. Auto-rollback уже произошёл (если был PREV_SHA). |
| Rollback тоже упал | `/var/log/utg-redeploy.log` → секция «WARN: rollback container не поднялся». Ручной SSH + `docker run` known-good. |

Быстрая проверка живого прода:

```bash
curl -fsk https://158-160-48-113.nip.io/api/v1/actuator/health | jq
# должен вернуть {"status":"UP",...}
```

## 14.8 Что не автоматизировано

- **Branch protection rules** (запрет merge при red CI, обязательный review) — репозиторий-level настройка в GH UI, не workflow.
- **Semver-теги / GitHub Releases / changelog** — используем `:$SHORT_SHA` (см. spec, вариант A).
- **Notifications** — Slack/tg/email не подключены.
- **Blue-green / zero-downtime deploy** — текущий swap `docker rm && docker run` даёт ~5-10s downtime.
- **Pre-release проверка «CI green на выбранном SHA»** — trust the trigger (dispatcher должен убедиться сам).
- **Multi-arch build** — только `linux/amd64`.

Если что-то из этого укусит — заводим отдельный таск.
