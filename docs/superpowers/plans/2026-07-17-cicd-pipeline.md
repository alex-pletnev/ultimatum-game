# CI/CD pipeline — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ввести CI на PR/push (`./gradlew check`) и manual release-pipeline (`workflow_dispatch` → docker build+push в `cr.yandex` → SSH-redeploy на прод-VM с auto-rollback).

**Architecture:** Два GitHub Actions workflow: `ci.yml` (auto на pull_request/push) и `release.yml` (manual dispatch). Release-workflow build'ит Docker image, пушит в Yandex Container Registry с тегами `:$SHORT_SHA` + `:latest`, затем через SSH дёргает `/opt/app/redeploy.sh` на живой VM. Скрипт делает атомарный swap контейнера, health-check по HTTPS в течение 60s, и при провале откатывается на предыдущий SHA.

**Tech Stack:** GitHub Actions, Kotlin/Gradle 8.14.3, Java 21, Docker + BuildKit, Yandex.Cloud Container Registry + Lockbox + Compute VM (Ubuntu 24.04), Caddy.

## Global Constraints

- **Runner:** `ubuntu-latest` для всех job'ов.
- **JDK:** `temurin`, версия `21`.
- **Docker platform:** `linux/amd64` (YC VM x86).
- **Container registry:** `cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game`.
- **Prod VM:** `ubuntu@158.160.48.113`, HTTPS через `https://158-160-48-113.nip.io/api/v1`.
- **GH secrets (заводятся в задаче 6):** `YC_SA_JSON_CREDENTIALS`, `VM_SSH_PRIVATE_KEY`.
- **Триггер CI:** `pull_request: [main]` + `push: [main]` (согласовано в brainstorming'е).
- **Триггер release:** только `workflow_dispatch` — auto-deploy на push НЕ делаем.
- **Docker tag policy:** `:$SHORT_SHA` (7 hex-chars) + `:latest`.
- **Concurrency release-workflow:** `group: release-prod, cancel-in-progress: false`.
- **Health-check policy:** 30 попыток × 2s = до 60s суммарно; `/actuator/health` возвращает JSON с `.status=="UP"`.
- **Rollback policy:** при провале health-check — `docker run` предыдущего SHA (сохранён из `docker inspect` до swap'а).
- **Git-политика проекта (CLAUDE.md):** `git add` только именованные пути; никогда `-A`/`.`; после каждого нашего commit — push в `origin main` (без force). Каждый commit — Conventional Commits + `Refs: docs/tasks/T-101-cicd-pipeline.md`.
- **Секреты после bootstrap:** `/tmp/sa-key.json`, `/tmp/utg-deploy*` — удалить (`shred -u` или `rm`).

---

## File Structure

**Создаются:**
- `.github/workflows/ci.yml` — CI workflow.
- `.github/workflows/release.yml` — release workflow.
- `scripts/vm-redeploy.sh` — скрипт redeploy'а, хранится в repo, копия живёт на VM в `/opt/app/redeploy.sh`.
- `docs/14-cicd.md` — runbook.

**Правятся:**
- `docs/README.md` — добавить пункт «14. CI/CD».
- `docs/13-deploy.md` — cross-ref на `14-cicd.md`.
- `docs/tasks/T-090-prod-deploy-readiness.md` — cross-ref в `## Лог` (что CD теперь есть).
- `docs/tasks/T-101-cicd-pipeline.md` — обновление `## Лог` по мере прохождения задач.
- `docs/tasks/INDEX.md` — при закрытии T-101.

**Удаляются:**
- `scripts/deploy-yc.sh` (мёртвый Serverless-вариант; заодно исчезает `M`-правка в git-status).

**Внешние побочные эффекты** (Yandex.Cloud, GitHub secrets, VM filesystem):
- `yc iam key create` в SA `utg-sa`.
- `yc container registry add-access-binding --role container-registry.images.pusher`.
- `gh secret set YC_SA_JSON_CREDENTIALS`, `gh secret set VM_SSH_PRIVATE_KEY`.
- Pubkey в `~ubuntu/.ssh/authorized_keys` на VM.
- `scp` `vm-redeploy.sh` в `/opt/app/redeploy.sh` на VM.

---

## Task 1: CI workflow (`ci.yml`) — базовый check на PR/push

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `./gradlew check` (существует, порог jacoco 0.80 + detekt strict).
- Produces: pass/fail-статус в GH Actions UI на PR и push в `main`. Никаких артефактов.

- [ ] **Step 1: Создать `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  check:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Run gradle check
        run: ./gradlew check --no-daemon --stacktrace
```

- [ ] **Step 2: Локальная sanity-проверка YAML**

Прогнать локально `./gradlew check` (чтобы убедиться что оно зелёное — иначе первый CI-run упадёт независимо от workflow):

```bash
JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home ./gradlew check > /tmp/gradle-check.log 2>&1
```

Через `run_in_background=true` + wait-notification (CLAUDE.md).
Expected: BUILD SUCCESSFUL.
При FAIL — исправить причину до продолжения (workflow чинить бессмысленно если сама проверка красная).

- [ ] **Step 3: Commit + push (workflow нужен в main, чтобы GH его увидел)**

```bash
git add .github/workflows/ci.yml
git commit -m "$(cat <<'EOF'
feat(T-101): CI workflow — ./gradlew check на PR и push в main

Refs: docs/tasks/T-101-cicd-pipeline.md
EOF
)"
git push origin main
```

- [ ] **Step 4: Проверить первый run на GH**

```bash
gh run list --workflow=ci.yml --limit 1
gh run watch $(gh run list --workflow=ci.yml --limit 1 --json databaseId -q '.[0].databaseId')
```

Expected: workflow появился в списке, статус `completed success`.
Если fail — открыть `gh run view <id> --log-failed`, исправить, снова push, повторить.

- [ ] **Step 5: Запись в `## Лог` T-101**

Дописать в `docs/tasks/T-101-cicd-pipeline.md`:

```
- 2026-07-17: Task 1 — CI workflow (.github/workflows/ci.yml) добавлен, первый run зелёный.
```

Commit + push (докс-only коммит, `docs(T-101): update log`).

---

## Task 2: `scripts/vm-redeploy.sh` — атомарный swap с health-check и rollback

**Files:**
- Create: `scripts/vm-redeploy.sh`

**Interfaces:**
- Consumes: аргумент `$1` = `NEW_SHA` (7-hex short SHA); окружение VM (SA metadata service, docker daemon, Lockbox доступен).
- Produces: exit code 0 при успешном deploy + health, exit code 1 при провале (после rollback или без него). Пишет полный лог в `/var/log/utg-redeploy.log` + stdout.

- [ ] **Step 1: Достать актуальные значения `SECRET_ID` и `PG_HOST`**

```bash
SECRET_ID=$(yc lockbox secret get utg-secrets --format json | jq -r .id)
PG_HOST=$(yc managed-postgresql host list --cluster-name ultimatum-pg --format json | jq -r '.[0].name')
echo "SECRET_ID=$SECRET_ID"
echo "PG_HOST=$PG_HOST"
```

Запомнить оба значения — они пойдут в скрипт как константы.

- [ ] **Step 2: Создать `scripts/vm-redeploy.sh`**

Подставить `SECRET_ID` и `PG_HOST` из Step 1 в места `<REPLACE:...>`:

```bash
#!/usr/bin/env bash
# T-101 — VM-side redeploy script. Живёт в repo, копируется на VM в /opt/app/redeploy.sh.
#
# Атомарный swap контейнера ultimatum-game на указанный SHA-тег из cr.yandex.
# При провале health-check в течение 60s — auto-rollback на предыдущий SHA.

set -uo pipefail

exec > >(tee -a /var/log/utg-redeploy.log) 2>&1
echo "===== $(date -u +%FT%TZ) redeploy start: $* ====="

NEW_SHA="${1:-}"
[[ -z "$NEW_SHA" ]] && { echo "ERROR: usage: $0 <short-sha>"; exit 2; }

# ---- Константы (значения известны из bootstrap; не секреты) ----
REG_ID="crp7b8ldv830cfseac0e"
IMAGE="cr.yandex/$REG_ID/ultimatum-game"
SECRET_ID="<REPLACE:SECRET_ID>"
PG_HOST="<REPLACE:PG_HOST>"
DB_URL="jdbc:postgresql://${PG_HOST}:6432/ultimatum?sslmode=require"
PG_USER="utg"
CORS_ORIGINS="https://alex-pletnev.github.io"
HEALTH_URL="https://158-160-48-113.nip.io/api/v1/actuator/health"

# ---- Предыдущий SHA (для rollback) ----
PREV_SHA=$(docker inspect ultimatum-game --format '{{.Config.Image}}' 2>/dev/null \
  | awk -F: '{print $NF}' \
  || true)
echo "PREV_SHA=${PREV_SHA:-<none>}"
echo "NEW_SHA=$NEW_SHA"

# ---- SA-token из metadata ----
TOKEN=""
for i in 1 2 3 4 5; do
  TOKEN=$(curl -sf -H "Metadata-Flavor: Google" \
    http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/token \
    2>/dev/null | jq -r .access_token 2>/dev/null || true)
  [[ -n "$TOKEN" && "$TOKEN" != "null" ]] && break
  echo "metadata retry $i"; sleep 3
done
[[ -n "$TOKEN" ]] || { echo "ERROR: no SA token from metadata"; exit 1; }

# ---- Секреты из Lockbox ----
PAYLOAD=$(curl -sf -H "Authorization: Bearer $TOKEN" \
  "https://payload.lockbox.api.cloud.yandex.net/lockbox/v1/secrets/$SECRET_ID/payload")
JWT_KEY=$(echo "$PAYLOAD" | jq -r '.entries[]|select(.key=="JWT_SIGNING_KEY")|.textValue')
DB_PWD=$(echo "$PAYLOAD" | jq -r '.entries[]|select(.key=="DB_PASSWORD")|.textValue')
[[ -n "$JWT_KEY" && -n "$DB_PWD" ]] || { echo "ERROR: secrets missing from Lockbox"; exit 1; }

# ---- Docker login + pull ----
docker login -u iam -p "$TOKEN" cr.yandex
docker pull "$IMAGE:$NEW_SHA"

# ---- Функция запуска контейнера ----
run_container() {
  local tag="$1"
  docker rm -f ultimatum-game 2>/dev/null || true
  docker run -d --name ultimatum-game --restart=always \
    -p 8080:8080 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e "DB_URL=$DB_URL" \
    -e "DB_USER=$PG_USER" \
    -e "DB_PASSWORD=$DB_PWD" \
    -e "JWT_SIGNING_KEY=$JWT_KEY" \
    -e "APP_CORS_ORIGINS=$CORS_ORIGINS" \
    "$IMAGE:$tag"
}

# ---- Deploy NEW ----
run_container "$NEW_SHA"

# ---- Health-check ----
HEALTHY=0
for i in $(seq 1 30); do
  RESP=$(curl -fsk --max-time 3 "$HEALTH_URL" 2>/dev/null || echo "")
  if echo "$RESP" | jq -e '.status=="UP"' >/dev/null 2>&1; then
    echo "HEALTHY on attempt $i (${RESP})"
    HEALTHY=1
    break
  fi
  sleep 2
done

if [[ "$HEALTHY" -eq 1 ]]; then
  echo "===== deploy OK: $NEW_SHA ====="
  exit 0
fi

# ---- Rollback ----
echo "===== deploy FAILED (health-check timeout after 60s) ====="
docker logs --tail 100 ultimatum-game || true

if [[ -n "$PREV_SHA" && "$PREV_SHA" != "$NEW_SHA" ]]; then
  echo "rolling back to $PREV_SHA"
  run_container "$PREV_SHA"
  # Верификация что откат жив
  for i in $(seq 1 15); do
    RESP=$(curl -fsk --max-time 3 "$HEALTH_URL" 2>/dev/null || echo "")
    echo "$RESP" | jq -e '.status=="UP"' >/dev/null 2>&1 && { echo "rollback healthy"; exit 1; }
    sleep 2
  done
  echo "WARN: rollback container не поднялся за 30s — VM в broken state"
  exit 1
else
  echo "WARN: no PREV_SHA — cannot rollback, VM broken"
  exit 1
fi
```

- [ ] **Step 3: Локальный shellcheck**

Запустить `shellcheck scripts/vm-redeploy.sh` (если shellcheck установлен: `brew install shellcheck`). Прочитать warnings — пофиксить критические (SC2086 quoting, SC2181). Опционально, не blocker.

- [ ] **Step 4: `chmod +x` и sanity-парсинг**

```bash
chmod +x scripts/vm-redeploy.sh
bash -n scripts/vm-redeploy.sh   # только syntax check, без выполнения
```

Expected: exit 0, никакого output'а.

- [ ] **Step 5: Commit + push**

```bash
git add scripts/vm-redeploy.sh
git commit -m "$(cat <<'EOF'
feat(T-101): scripts/vm-redeploy.sh — атомарный swap с health-check и rollback

Скрипт живёт в repo для version control, копируется на VM в
/opt/app/redeploy.sh при bootstrap'е (см. docs/14-cicd.md).

При провале /actuator/health в течение 60s — auto-rollback на
предыдущий SHA (docker inspect до swap'а).

Refs: docs/tasks/T-101-cicd-pipeline.md
EOF
)"
git push origin main
```

---

## Task 3: Release workflow (`release.yml`) — build+push+SSH-deploy

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: `Dockerfile` (существует, multi-stage), `scripts/vm-redeploy.sh` копия на VM (положим в Task 6b), GH secrets `YC_SA_JSON_CREDENTIALS` + `VM_SSH_PRIVATE_KEY` (заведём в Task 6a).
- Produces: docker image в `cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game:$SHORT_SHA` + `:latest`; обновлённый контейнер на VM.

- [ ] **Step 1: Создать `.github/workflows/release.yml`**

```yaml
name: Release

on:
  workflow_dispatch:
    inputs:
      sha:
        description: 'Git SHA to deploy (default = HEAD of main)'
        required: false
        default: ''

concurrency:
  group: release-prod
  cancel-in-progress: false

env:
  REG_ID: crp7b8ldv830cfseac0e
  IMAGE_NAME: cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    outputs:
      short_sha: ${{ steps.sha.outputs.short }}
      full_sha:  ${{ steps.sha.outputs.full }}
    steps:
      - name: Resolve target SHA
        id: sha
        run: |
          if [[ -n "${{ inputs.sha }}" ]]; then
            FULL="${{ inputs.sha }}"
          else
            FULL="${{ github.sha }}"
          fi
          echo "full=$FULL" >> "$GITHUB_OUTPUT"
          echo "short=${FULL:0:7}" >> "$GITHUB_OUTPUT"

      - name: Checkout @${{ steps.sha.outputs.full }}
        uses: actions/checkout@v4
        with:
          ref: ${{ steps.sha.outputs.full }}

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to Yandex Container Registry
        uses: docker/login-action@v3
        with:
          registry: cr.yandex
          username: json_key
          password: ${{ secrets.YC_SA_JSON_CREDENTIALS }}

      - name: Build & push image
        uses: docker/build-push-action@v6
        with:
          context: .
          platforms: linux/amd64
          push: true
          tags: |
            ${{ env.IMAGE_NAME }}:${{ steps.sha.outputs.short }}
            ${{ env.IMAGE_NAME }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - name: SSH → VM → /opt/app/redeploy.sh
        uses: appleboy/ssh-action@v1
        with:
          host: 158.160.48.113
          username: ubuntu
          key: ${{ secrets.VM_SSH_PRIVATE_KEY }}
          command_timeout: 3m
          script_stop: true
          script: |
            bash /opt/app/redeploy.sh ${{ needs.build-and-push.outputs.short_sha }}
```

- [ ] **Step 2: YAML-lint / syntax**

Локально проверить, что GH его распарсит. Простейший способ — `gh workflow list` после push (см. Step 3).

- [ ] **Step 3: Commit + push (workflow должен быть в main до dispatch'а)**

```bash
git add .github/workflows/release.yml
git commit -m "$(cat <<'EOF'
feat(T-101): release workflow — workflow_dispatch → build+push → SSH redeploy

Manual trigger из GH Actions UI. Build & push Docker image в
cr.yandex с тегами :$SHORT_SHA и :latest, затем SSH на прод-VM
и запуск /opt/app/redeploy.sh с указанным SHA.

Concurrency group release-prod — второй dispatch встаёт в очередь.

Refs: docs/tasks/T-101-cicd-pipeline.md
EOF
)"
git push origin main
```

- [ ] **Step 4: Проверить что GH увидел workflow**

```bash
gh workflow list | grep -i release
gh workflow view release.yml
```

Expected: workflow появился, `state: active`, есть input `sha`. Ещё **не запускать** — секретов пока нет (Task 6).

---

## Task 4: Runbook `docs/14-cicd.md` + cross-refs

**Files:**
- Create: `docs/14-cicd.md`
- Modify: `docs/README.md` — добавить пункт «14. CI/CD».
- Modify: `docs/13-deploy.md` — cross-ref на 14-cicd в разделе про redeploy.

**Interfaces:**
- Consumes: описание bootstrap-действий из этого плана (Task 6).
- Produces: полный runbook для человека — bootstrap, релиз, откат, ротация, диагностика.

- [ ] **Step 1: Прочитать текущий `docs/README.md` и `docs/13-deploy.md`**

Понадобится точное место, куда встроить пункт «14. CI/CD» в оглавлении README и cross-ref в 13-deploy.

- [ ] **Step 2: Создать `docs/14-cicd.md`**

Разделы (все обязательны, минимум):

1. `## Обзор` — что CI, что release, где живут файлы, что делают триггеры.
2. `## Что где хранится` — таблица: workflow'ы (`.github/workflows/*.yml`), скрипт (`scripts/vm-redeploy.sh` в repo + `/opt/app/redeploy.sh` на VM), секреты (`YC_SA_JSON_CREDENTIALS`, `VM_SSH_PRIVATE_KEY` в GH Actions), где положить новые.
3. `## Bootstrap` — что было сделано агентом (перечислить конкретные команды, значения `SA_ID`, `SECRET_ID`, `IP`, версии actions). После bootstrap'а этот раздел — историческая справка + инструкция «как повторить с нуля».
4. `## Как выкатить релиз` — GH UI: `Actions → Release → Run workflow → sha пусто (= main HEAD) или конкретный → Run`. Плюс альтернатива через CLI: `gh workflow run release.yml -f sha=<sha>`.
5. `## Rollback` — auto (workflow сам откатит при health-check fail) + manual (`gh workflow run release.yml -f sha=<prev-sha>`).
6. `## Ротация ключей` — SA-key: `yc iam key create` → `gh secret set YC_SA_JSON_CREDENTIALS` → `yc iam key delete <old>`. SSH-key: `ssh-keygen` → положить pubkey на VM (SSH access!) → `gh secret set VM_SSH_PRIVATE_KEY` → удалить старый pubkey из `~ubuntu/.ssh/authorized_keys`.
7. `## Диагностика` — где смотреть логи: CI red → `gh run view <id> --log-failed`; Release build red → docker-build step logs; Release deploy red → `gh run view` покажет stdout `redeploy.sh`, полный лог на VM в `/var/log/utg-redeploy.log`; health-check red → рядом же в логах видно, что откат уже случился.
8. `## Что не автоматизировано` — branch protection rules (репозиторий-level, через UI), semver-теги, notifications, blue-green.

- [ ] **Step 3: Обновить `docs/README.md`**

Добавить строку в оглавление в правильном месте (после `13-deploy`).

Пример правки (exact формулировка зависит от текущего файла — прочитать перед правкой):

```markdown
- [14-cicd.md](14-cicd.md) — CI/CD pipeline: GitHub Actions workflow'ы, bootstrap, release, rollback, ротация ключей.
```

- [ ] **Step 4: Обновить `docs/13-deploy.md`**

В разделе «Redeploy» / «Update» добавить cross-ref:

```markdown
> **Автоматизированный redeploy** — см. `docs/14-cicd.md`.
> Ручной запуск скрипта на VM (fallback) — оставлен здесь.
```

- [ ] **Step 5: Commit + push**

```bash
git add docs/14-cicd.md docs/README.md docs/13-deploy.md
git commit -m "$(cat <<'EOF'
docs(T-101): docs/14-cicd.md — runbook для CI/CD pipeline

Bootstrap, release, rollback, ротация ключей, диагностика.
Cross-ref из docs/README.md и docs/13-deploy.md.

Refs: docs/tasks/T-101-cicd-pipeline.md
EOF
)"
git push origin main
```

---

## Task 5: Удалить мёртвый `scripts/deploy-yc.sh`

**Files:**
- Delete: `scripts/deploy-yc.sh`

**Interfaces:**
- Consumes: подтверждение из brainstorming'а, что Serverless-вариант заброшен после pivot'а на VM.
- Produces: чистый `git status`, отсутствие мёртвого кода.

- [ ] **Step 1: Убедиться, что нет ссылок на скрипт из активных мест**

```bash
grep -rn "deploy-yc.sh" --include="*.md" --include="*.sh" --include="*.yml" .
```

Expected: только строки внутри `docs/tasks/T-090-*.md` в разделе `## Лог` (историческая справка — оставляем), и, возможно, `docs/13-deploy.md`. Если 13-deploy ссылается — заменить на 14-cicd/`deploy-yc-vm.sh`.

- [ ] **Step 2: Удалить файл (с несохранённой правкой в нём)**

```bash
git rm scripts/deploy-yc.sh
git status --short
```

Expected: `D scripts/deploy-yc.sh` в staged.

- [ ] **Step 3: Commit + push**

```bash
git commit -m "$(cat <<'EOF'
chore(T-101): remove scripts/deploy-yc.sh — dead Serverless-variant

Заброшен в T-090 после pivot'а на Compute VM (Serverless cold-start
Spring Boot ~40s > YC execution-timeout). Живой скрипт — deploy-yc-vm.sh.

Refs: docs/tasks/T-101-cicd-pipeline.md
EOF
)"
git push origin main
```

---

## Task 6: Bootstrap — YC + GH secrets + VM setup

Смешанный: часть автоматизируется агентом (Task 6a), часть требует явного разрешения на SSH в прод-VM (Task 6b). Пользователь уже дал разрешение на SSH в этой сессии.

### Task 6a: `yc` + `gh` (агент делает сам)

**Files:** (нет, только внешние side-effects)

**Interfaces:**
- Consumes: локальный `yc` CLI (folder-id уже настроен), `gh` CLI (auth есть).
- Produces:
  - Новый SA-key в SA `utg-sa`.
  - Роль `container-registry.images.pusher` у `utg-sa`.
  - GH secret `YC_SA_JSON_CREDENTIALS`.
  - GH secret `VM_SSH_PRIVATE_KEY`.
  - Локальные файлы `/tmp/sa-key.json` и `/tmp/utg-deploy` + `/tmp/utg-deploy.pub` (удаляются в Task 6b Step 4).

- [ ] **Step 1: Найти `SA_ID` и `REGISTRY_ID`**

```bash
SA_ID=$(yc iam service-account list --format json | jq -r '.[] | select(.name=="utg-sa") | .id')
REGISTRY_ID="crp7b8ldv830cfseac0e"
echo "SA_ID=$SA_ID"
```

Expected: непустой `SA_ID`.

- [ ] **Step 2: Создать SA-key**

```bash
yc iam key create --service-account-id "$SA_ID" \
  --description "GitHub Actions release workflow — T-101" \
  --output /tmp/sa-key.json
ls -la /tmp/sa-key.json
```

Expected: файл создан, содержит JSON с `id`, `service_account_id`, `private_key`.

- [ ] **Step 3: Дать SA роль pusher**

```bash
yc container registry add-access-binding --id "$REGISTRY_ID" \
  --role container-registry.images.pusher \
  --subject "serviceAccount:$SA_ID"
```

Expected: `done` в output'е. При «already exists» — тоже ок, идемпотентно.

- [ ] **Step 4: Залить SA-key в GH secret**

```bash
gh secret set YC_SA_JSON_CREDENTIALS \
  --repo alex-pletnev/ultimatum-game \
  < /tmp/sa-key.json
gh secret list --repo alex-pletnev/ultimatum-game | grep YC_SA_JSON_CREDENTIALS
```

Expected: `YC_SA_JSON_CREDENTIALS  <дата>`.

- [ ] **Step 5: Сгенерировать SSH-keypair для deploy'а**

```bash
ssh-keygen -t ed25519 -f /tmp/utg-deploy -N '' -C 'gh-actions-deploy-T-101'
ls -la /tmp/utg-deploy /tmp/utg-deploy.pub
```

Expected: оба файла созданы.

- [ ] **Step 6: Залить private key в GH secret**

```bash
gh secret set VM_SSH_PRIVATE_KEY \
  --repo alex-pletnev/ultimatum-game \
  < /tmp/utg-deploy
gh secret list --repo alex-pletnev/ultimatum-game | grep VM_SSH_PRIVATE_KEY
```

Expected: `VM_SSH_PRIVATE_KEY <дата>`.

### Task 6b: VM setup (требует SSH — пользователь разрешил)

- [ ] **Step 1: Проверить SSH-доступ к VM с текущим ключом**

```bash
ssh -o StrictHostKeyChecking=accept-new ubuntu@158.160.48.113 'echo OK && whoami && ls /opt/app/'
```

Expected: `OK`, `ubuntu`, `run.sh` (положен cloud-init'ом).
Если permission denied — вернуться и проверить, какой ключ был добавлен через cloud-init (в скрипте `deploy-yc-vm.sh`: `SSH_PUB=$(cat "$HOME/.ssh/id_ed25519.pub" 2>/dev/null || cat "$HOME/.ssh/id_rsa.pub" 2>/dev/null)`).

- [ ] **Step 2: Положить pubkey deploy-ключа в `authorized_keys`**

```bash
cat /tmp/utg-deploy.pub | ssh ubuntu@158.160.48.113 \
  'cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && wc -l < ~/.ssh/authorized_keys'
```

Expected: число строк ≥ 2 (был твой + добавлен deploy). Проверка независимости — Step 3.

- [ ] **Step 3: Проверить deploy-ключ работает**

```bash
ssh -i /tmp/utg-deploy -o IdentitiesOnly=yes \
  -o StrictHostKeyChecking=accept-new \
  ubuntu@158.160.48.113 'echo DEPLOY_KEY_OK'
```

Expected: `DEPLOY_KEY_OK`.

- [ ] **Step 4: Скопировать `vm-redeploy.sh` в `/opt/app/redeploy.sh`**

```bash
scp -i /tmp/utg-deploy -o IdentitiesOnly=yes \
  scripts/vm-redeploy.sh \
  ubuntu@158.160.48.113:/tmp/redeploy.sh
ssh -i /tmp/utg-deploy -o IdentitiesOnly=yes \
  ubuntu@158.160.48.113 \
  'sudo mv /tmp/redeploy.sh /opt/app/redeploy.sh && sudo chmod +x /opt/app/redeploy.sh && sudo chown root:root /opt/app/redeploy.sh && ls -la /opt/app/'
```

Expected: файл на месте, `-rwxr-xr-x root root`.

- [ ] **Step 5: Sanity-run — прогнать redeploy на текущий уже задеплоенный SHA**

Достать текущий SHA контейнера:

```bash
CURRENT_SHA=$(ssh -i /tmp/utg-deploy -o IdentitiesOnly=yes ubuntu@158.160.48.113 \
  "docker inspect ultimatum-game --format '{{.Config.Image}}'" \
  | awk -F: '{print $NF}')
echo "CURRENT_SHA=$CURRENT_SHA"
```

Если контейнер ещё не тегирован через SHA (это первый запуск после T-090 где image = `v1`), можно передать `v1`:

```bash
ssh -i /tmp/utg-deploy -o IdentitiesOnly=yes ubuntu@158.160.48.113 \
  'sudo bash /opt/app/redeploy.sh v1'
```

Expected: скрипт `docker pull cr.yandex/.../ultimatum-game:v1`, поднимает контейнер, health-check → HEALTHY, exit 0. Downtime ~5-10s.
Если fail — читать `/var/log/utg-redeploy.log` на VM.

- [ ] **Step 6: Cleanup секретных файлов на локале**

```bash
shred -u /tmp/sa-key.json /tmp/utg-deploy 2>/dev/null || rm -f /tmp/sa-key.json /tmp/utg-deploy
rm -f /tmp/utg-deploy.pub
ls -la /tmp/sa-key.json /tmp/utg-deploy* 2>&1 | head
```

Expected: `No such file or directory` для всех.

- [ ] **Step 7: Запись в `## Лог` T-101**

Дописать в `docs/tasks/T-101-cicd-pipeline.md`:

```
- 2026-07-17: Task 6 — bootstrap завершён. SA-key + role pusher, GH secrets YC_SA_JSON_CREDENTIALS + VM_SSH_PRIVATE_KEY, /opt/app/redeploy.sh на VM. Sanity redeploy текущего v1-image прошёл (health UP за <N>s).
```

Commit + push.

---

## Task 7: End-to-end test — реальный release-dispatch

**Files:** (нет)

**Interfaces:**
- Consumes: всё готовое из Task 1-6.
- Produces: подтверждение, что pipeline работает end-to-end.

- [ ] **Step 1: Триггернуть release с текущим main-HEAD**

```bash
CURRENT_SHA=$(git rev-parse HEAD)
SHORT=${CURRENT_SHA:0:7}
echo "Dispatching release for $SHORT"
gh workflow run release.yml -f sha="$CURRENT_SHA" --repo alex-pletnev/ultimatum-game
```

- [ ] **Step 2: Дождаться завершения**

```bash
sleep 5
RUN_ID=$(gh run list --workflow=release.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --repo alex-pletnev/ultimatum-game
```

Expected: `completed success`.

- [ ] **Step 3: Проверить, что на VM новый SHA**

```bash
ssh -i ~/.ssh/id_ed25519 ubuntu@158.160.48.113 \
  "docker inspect ultimatum-game --format '{{.Config.Image}}'"
```

Expected: `cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game:<SHORT>` (тот же 7-hex что $SHORT).

- [ ] **Step 4: Проверить health**

```bash
curl -fsk https://158-160-48-113.nip.io/api/v1/actuator/health | jq
```

Expected: `{"status":"UP",...}`.

- [ ] **Step 5: Sanity — quick-register**

```bash
curl -sk -X POST https://158-160-48-113.nip.io/api/v1/auth/quick-register \
  -H "Content-Type: application/json" -d '{}' | jq -r '.accessToken' | head -c 40 && echo "..."
```

Expected: JWT (первые 40 символов начинаются с `eyJ`).

- [ ] **Step 6: Запись в `## Лог`**

Дописать в `docs/tasks/T-101-cicd-pipeline.md`:

```
- 2026-07-17: Task 7 — end-to-end release прошёл. Dispatched SHA <SHORT>, workflow green за <N>s, VM показывает новый SHA, health UP, quick-register → JWT.
```

Commit + push.

---

## Task 8: Rollback test — искусственный failing deploy

**Files:**
- Create + delete (не commit): временный commit с ломаной prod-конфигурацией на feature-ветке.

**Interfaces:**
- Consumes: работающий release-pipeline из Task 7.
- Produces: подтверждение, что auto-rollback работает.

- [ ] **Step 1: Создать ветку с ломаной prod-конфигурацией**

```bash
git checkout -b test/rollback-T-101
```

Правка `src/main/resources/application-prod.properties`: добавить строку, из-за которой Spring не поднимется, например неверный `spring.datasource.driver-class-name=org.foo.BadDriver`.

```bash
git add src/main/resources/application-prod.properties
git commit -m "test(T-101): deliberate broken prod-config for rollback test"
git push -u origin test/rollback-T-101
```

- [ ] **Step 2: Запомнить working SHA до dispatch'а сломанного**

```bash
WORKING_SHA=$(ssh -i ~/.ssh/id_ed25519 ubuntu@158.160.48.113 \
  "docker inspect ultimatum-game --format '{{.Config.Image}}'" | awk -F: '{print $NF}')
BROKEN_SHA=$(git rev-parse HEAD)
echo "WORKING_SHA=$WORKING_SHA"
echo "BROKEN_SHA=${BROKEN_SHA:0:7}"
```

- [ ] **Step 3: Диспатчнуть release с BROKEN_SHA**

```bash
gh workflow run release.yml -f sha="$BROKEN_SHA" --repo alex-pletnev/ultimatum-game
sleep 5
RUN_ID=$(gh run list --workflow=release.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$RUN_ID" --repo alex-pletnev/ultimatum-game || true   # ожидаем FAIL
```

Expected: workflow `completed failure` на job `deploy` (build должен пройти).

- [ ] **Step 4: Проверить, что на VM всё ещё `WORKING_SHA`**

```bash
CURRENT=$(ssh -i ~/.ssh/id_ed25519 ubuntu@158.160.48.113 \
  "docker inspect ultimatum-game --format '{{.Config.Image}}'" | awk -F: '{print $NF}')
echo "AFTER=$CURRENT (should equal $WORKING_SHA)"
curl -fsk https://158-160-48-113.nip.io/api/v1/actuator/health | jq -e '.status=="UP"' && echo "HEALTH OK"
```

Expected: `AFTER == WORKING_SHA`, `HEALTH OK`.

- [ ] **Step 5: Cleanup ветки**

```bash
git checkout main
git branch -D test/rollback-T-101
git push origin --delete test/rollback-T-101
```

- [ ] **Step 6: Запись в `## Лог`**

Дописать в `docs/tasks/T-101-cicd-pipeline.md`:

```
- 2026-07-17: Task 8 — rollback test прошёл. Dispatched broken SHA <SHORT>, health-check упал за 60s, VM автоматически откатилась на WORKING_SHA <SHORT>, health снова UP.
```

Commit + push.

---

## Task 9: Закрыть T-101 через `/task-done`

**Files:**
- Modify: `docs/tasks/T-101-cicd-pipeline.md` (status: done, финальная запись в `## Лог`).
- Modify: `docs/tasks/INDEX.md` (переместить T-101 из «Открытые» в «Закрытые»).
- Modify: `docs/tasks/T-090-prod-deploy-readiness.md` — добавить в `## Лог` cross-ref, что CD теперь есть (Phase 4 → auto).

**Interfaces:**
- Consumes: все acceptance criteria из T-101 отмечены `[x]`.
- Produces: закрытая задача.

- [ ] **Step 1: Прогнать полный `./gradlew check` локально**

```bash
JAVA_HOME=$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home ./gradlew check > /tmp/gradle-check-final.log 2>&1
```

Через `run_in_background=true`. Expected: BUILD SUCCESSFUL.
При fail — блокер `/task-done`, чинить.

- [ ] **Step 2: Отметить все AC в T-101 как `[x]`**

Прочитать `docs/tasks/T-101-cicd-pipeline.md`, все AC должны быть выполнены — заменить `- [ ]` на `- [x]`.

- [ ] **Step 3: Обновить frontmatter T-101**

`status: pending` → `status: done`; `updated: 2026-07-17`.

- [ ] **Step 4: Добавить финальную запись в `## Лог` T-101**

```
- 2026-07-17: task done. CI + release + rollback все работают, secrets заведены, runbook в docs/14-cicd.md. Follow-up'ы (если найдутся) — через self-review.
```

- [ ] **Step 5: Обновить `docs/tasks/INDEX.md`**

Переместить строку T-101 из «Открытые задачи» в «Закрытые задачи» с колонкой `Закрыто: 2026-07-17`.

- [ ] **Step 6: Cross-ref в T-090**

Добавить в `docs/tasks/T-090-prod-deploy-readiness.md` в `## Лог`:

```
- 2026-07-17: T-101 закрыт — CI/CD автоматизирован. Phase 4 (deploy на VM) теперь через `gh workflow run release.yml`, не через ручной `deploy-yc-vm.sh`. `deploy-yc-vm.sh` остаётся как fallback для пересоздания VM с нуля.
```

- [ ] **Step 7: Verification-before-completion**

Прогнать чек перед `task-done`:

```bash
gh workflow list                # оба workflow активны
gh secret list --repo alex-pletnev/ultimatum-game | grep -E 'YC_SA|VM_SSH'  # оба secret'а есть
curl -fsk https://158-160-48-113.nip.io/api/v1/actuator/health | jq -e '.status=="UP"'  # прод жив
```

Expected: всё зелёное. При любом red — не закрывать задачу, вернуться к соответствующему Task.

- [ ] **Step 8: Commit + push (task-done с прогонкой check'а)**

```bash
git add docs/tasks/T-101-cicd-pipeline.md docs/tasks/INDEX.md docs/tasks/T-090-prod-deploy-readiness.md
git commit -m "$(cat <<'EOF'
docs(T-101): task done — CI/CD pipeline готов

CI на PR/push в main зелёный. Release workflow (workflow_dispatch)
end-to-end проверен: build+push в cr.yandex, SSH-redeploy на VM,
health-check, auto-rollback при провале. Runbook в docs/14-cicd.md.

Refs: docs/tasks/T-101-cicd-pipeline.md
EOF
)"
git push origin main
```

- [ ] **Step 9: Self-review**

После task-done — вызвать `/self-review` (proactive-trigger из CLAUDE.md) на diff всей задачи. Особый акцент на пункт E (улучшения меня самого) — если найдены паттерны, которые повторяются между T-090 и T-101 (например: cloud-init ↔ redeploy.sh дублирование env-vars), — завести follow-up таск с тегом `tech-debt` или `meta`.

---

## Self-Review — spec coverage

Пройдусь по AC из `docs/tasks/T-101-cicd-pipeline.md`:

| AC | Покрывается task'ом |
|---|---|
| `ci.yml` — check зелёный на PR/push | Task 1 |
| `release.yml` — workflow_dispatch + build+push+deploy | Task 3 |
| `vm-redeploy.sh` — swap + health-check 60s + rollback | Task 2 + Task 6b (deploy на VM) |
| GH secrets заведены | Task 6a |
| SA role pusher | Task 6a Step 3 |
| `scripts/deploy-yc.sh` удалён | Task 5 |
| `docs/14-cicd.md` runbook + cross-refs | Task 4 |
| End-to-end release проверен | Task 7 |
| Rollback test | Task 8 |

Всё покрыто.

## Self-Review — type consistency

- `NEW_SHA` — 7-hex short SHA. Используется в `redeploy.sh` (Task 2), в `release.yml` output (Task 3), в bootstrap sanity (Task 6b), в end-to-end (Task 7), в rollback test (Task 8) — везде одинаково.
- `IMAGE`/`IMAGE_NAME` — `cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game`. Одно значение в constants во всех местах.
- `PREV_SHA` — 7-hex short SHA либо пустая строка. Локальная переменная в `redeploy.sh`, не exposed.
- `run_container()` — internal function `redeploy.sh`, принимает tag ($1). Не exposed.
- Секреты в GH — `YC_SA_JSON_CREDENTIALS` (JSON blob), `VM_SSH_PRIVATE_KEY` (openssh private key). Именование одинаковое в spec'е (Task 3), в bootstrap'е (Task 6a), в runbook'е (Task 4), в rollback ротации (Task 4 Step 2).

## Self-Review — placeholder scan

Нет `TBD` / `TODO` / «add appropriate error handling» / «similar to Task N». Все команды bash / yaml — точные.

Есть один динамический placeholder: `<REPLACE:SECRET_ID>` и `<REPLACE:PG_HOST>` в шаблоне `vm-redeploy.sh` (Task 2 Step 2). Это осознанно: Task 2 Step 1 явно даёт команды достать эти значения, Step 2 подставляет. Не «TBD», а «take result of Step 1 and put here».
