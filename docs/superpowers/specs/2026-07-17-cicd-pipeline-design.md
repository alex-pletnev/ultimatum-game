# CI/CD pipeline — design

**Дата:** 2026-07-17
**Автор:** brainstorming-сессия (пользователь + агент)
**Статус:** draft, ожидает user-review до перевода в plan
**Контекст задачи:** после T-090 (prod live на YC Compute VM) нужна автоматизация проверок и релиза.

## Цель

1. **CI** — на каждый PR в `main` и на каждый push в `main` прогонять `./gradlew check` (test + jacoco 0.80 + detekt). Не даёт merge'ить/пушить зелёные проверки.
2. **CD** — по manual trigger'у из GitHub UI (workflow_dispatch) выкатить указанный git-SHA на прод-VM: build docker image → push в `cr.yandex` → SSH на VM → атомарный swap контейнера с health-check и auto-rollback при провале.

## Не в scope

- Branch protection rules (репозиторий-level настройка, не workflow).
- Semver-теги / GitHub Releases / changelog automation.
- Multi-arch build (только `linux/amd64`, YC VM x86).
- Notifications (Slack/tg/email).
- Pre-release проверка «CI green на выбранном SHA» перед `workflow_dispatch` (первая версия — trust the trigger, добавим если укусит).
- Blue-green / zero-downtime deploy (текущий redeploy = ~5s downtime на `docker rm && docker run`).
- Ротация SA-key и SSH-key на автомате (runbook в docs, ручная процедура).

## Архитектура

**Файлы, которые появятся:**

```
.github/workflows/ci.yml           # PR + push в main → ./gradlew check
.github/workflows/release.yml      # workflow_dispatch → build+push+deploy+rollback
scripts/vm-redeploy.sh             # копия того, что живёт на VM в /opt/app/redeploy.sh
docs/14-cicd.md                    # runbook: секреты, ротация, ручной rollback
```

**Файлы, которые удаляются:**

```
scripts/deploy-yc.sh               # мёртвый Serverless-вариант (заброшен после pivot'а на VM)
```

**Файлы, которые правятся:**

```
docs/README.md                     # индекс + пункт про 14-cicd
docs/13-deploy.md                  # ссылка на 14-cicd в разделе «Redeploy»
```

## Component 1 — `.github/workflows/ci.yml`

Один job — `check`.

- **Триггеры:** `pull_request: branches: [main]` + `push: branches: [main]`.
- **Runner:** `ubuntu-latest`.
- **Steps:**
  1. `actions/checkout@v4`
  2. `actions/setup-java@v4` с `distribution: temurin`, `java-version: 21`
  3. `gradle/actions/setup-gradle@v4` (кэш wrapper + Gradle deps + configuration-cache)
  4. `./gradlew check --no-daemon`
- **Секреты:** нет.
- **Postgres/Docker:** не нужны. Тесты идут на H2 (см. `src/test/resources/application.properties`), `spring-boot-docker-compose` только `developmentOnly` → под `test` не активируется.
- **Fail-fast:** единственный job — падение = red workflow.

## Component 2 — `.github/workflows/release.yml`

Двухjob'овый workflow, ручной запуск.

- **Триггер:** `workflow_dispatch` с input:
  - `sha` — string, default: `''`. Пустое = использовать `github.sha` (HEAD `main`).
- **Concurrency:**
  ```yaml
  concurrency:
    group: release-prod
    cancel-in-progress: false
  ```
  Второй параллельный запуск встаёт в очередь, не отменяет первый.

### Job A — `build-and-push`

- Runner: `ubuntu-latest`.
- Steps:
  1. Compute `SHORT_SHA = ${{ inputs.sha || github.sha }}` (первые 7 символов).
  2. `actions/checkout@v4` с `ref: ${{ steps.sha.outputs.full }}`.
  3. `docker/setup-buildx-action@v3`.
  4. `docker/login-action@v3`:
     ```yaml
     registry: cr.yandex
     username: json_key
     password: ${{ secrets.YC_SA_JSON_CREDENTIALS }}
     ```
  5. `docker/build-push-action@v6`:
     ```yaml
     context: .
     platforms: linux/amd64
     push: true
     tags: |
       cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game:${{ steps.sha.outputs.short }}
       cr.yandex/crp7b8ldv830cfseac0e/ultimatum-game:latest
     cache-from: type=gha
     cache-to: type=gha,mode=max
     ```
  6. `outputs: short_sha: ${{ steps.sha.outputs.short }}`.

### Job B — `deploy`

- `needs: build-and-push`.
- Runner: `ubuntu-latest`.
- Steps:
  1. `appleboy/ssh-action@v1`:
     ```yaml
     host: 158.160.48.113
     username: ubuntu
     key: ${{ secrets.VM_SSH_PRIVATE_KEY }}
     command_timeout: 3m
     script: |
       bash /opt/app/redeploy.sh ${{ needs.build-and-push.outputs.short_sha }}
     ```
  2. Exit-code SSH-action = exit-code скрипта = финальный статус workflow.

## Component 3 — `scripts/vm-redeploy.sh`

Живёт в репозитории (для version control), копируется на VM в `/opt/app/redeploy.sh` при bootstrap'е.

**Что делает:**

1. Принимает аргумент `NEW_SHA`. Проверяет что не пустой.
2. Читает текущий SHA:
   ```bash
   PREV_SHA=$(docker inspect ultimatum-game --format '{{.Config.Image}}' 2>/dev/null \
     | awk -F: '{print $NF}' \
     || true)
   ```
   Если контейнера нет — `PREV_SHA=""`.
3. Достаёт секреты из Lockbox (как cloud-init делает сейчас):
   ```bash
   TOKEN=$(curl -sf -H "Metadata-Flavor: Google" \
     http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/token \
     | jq -r .access_token)
   PAYLOAD=$(curl -s -H "Authorization: Bearer $TOKEN" \
     "https://payload.lockbox.api.cloud.yandex.net/lockbox/v1/secrets/$SECRET_ID/payload")
   JWT_KEY=$(echo "$PAYLOAD" | jq -r '.entries[]|select(.key=="JWT_SIGNING_KEY")|.textValue')
   DB_PWD=$(echo "$PAYLOAD" | jq -r '.entries[]|select(.key=="DB_PASSWORD")|.textValue')
   ```
   Константы в топе скрипта (не секреты; publicly-known ID):
   - `REG_ID=crp7b8ldv830cfseac0e`
   - `SECRET_ID` — достаётся один раз при bootstrap через `yc lockbox secret get utg-secrets --format json | jq -r .id` и hardcode'ится в скрипт агентом.
   - `DB_URL=jdbc:postgresql://<pg-host>:6432/ultimatum?sslmode=require` — hostname достаётся из `yc managed-postgresql host list --cluster-name ultimatum-pg`.
   - `PG_USER=utg`, `CORS_ORIGINS=https://alex-pletnev.github.io` — известны из `scripts/deploy-yc-vm.sh`.
4. Docker login (SA-token из metadata):
   ```bash
   docker login -u iam -p "$TOKEN" cr.yandex
   ```
5. `docker pull cr.yandex/$REG_ID/ultimatum-game:$NEW_SHA`.
6. `docker rm -f ultimatum-game 2>/dev/null || true`.
7. `docker run -d --name ultimatum-game --restart=always -p 8080:8080` со всеми env-vars как в cloud-init, image = `:$NEW_SHA`.
8. **Health-check loop:** 30 попыток × 2s = до 60s суммарно.
   ```bash
   for i in $(seq 1 30); do
     RESP=$(curl -fsk https://158-160-48-113.nip.io/api/v1/actuator/health 2>/dev/null || echo "")
     if echo "$RESP" | jq -e '.status=="UP"' >/dev/null 2>&1; then
       echo "HEALTHY on attempt $i"
       exit 0
     fi
     sleep 2
   done
   echo "UNHEALTHY after 60s"
   ```
9. **Rollback path** (если health не поднялся):
   ```bash
   if [ -n "$PREV_SHA" ] && [ "$PREV_SHA" != "$NEW_SHA" ]; then
     echo "rolling back to $PREV_SHA"
     docker rm -f ultimatum-game
     docker run -d --name ultimatum-game --restart=always -p 8080:8080 \
       <те же env> \
       cr.yandex/$REG_ID/ultimatum-game:$PREV_SHA
     exit 1  # release всё равно red
   else
     echo "no previous SHA — cannot rollback, VM left in broken state"
     exit 1
   fi
   ```
10. Логирует всё в `/var/log/utg-redeploy.log`, вывод SSH-action в GHA даёт полный tail.

**Идемпотентность:** если `NEW_SHA == PREV_SHA` — скрипт всё равно перезапустит контейнер (не оптимизируем). Пользы от короткого цикла (skip if same) немного, а сложность растёт.

## Component 4 — `docs/14-cicd.md`

Runbook для человека. Разделы:

1. **Обзор** — что CI, что release, где что живёт.
2. **Bootstrap** (один раз). Большую часть автоматизирует агент через `yc` и `gh`
   CLI. Пользователь только разрешает SSH-операции в прод-VM и следит.

   **Агент делает сам:**
   - `yc iam key create --service-account-name utg-sa -o /tmp/sa-key.json` → SA-key.
   - `yc container registry add-access-binding --id crp7b8ldv830cfseac0e --role container-registry.images.pusher --subject serviceAccount:$SA_ID` → роль push'ить.
   - `gh secret set YC_SA_JSON_CREDENTIALS < /tmp/sa-key.json --repo alex-pletnev/ultimatum-game` → секрет в GH.
   - `ssh-keygen -t ed25519 -f /tmp/utg-deploy -N ''` → SSH-keypair.
   - `gh secret set VM_SSH_PRIVATE_KEY < /tmp/utg-deploy --repo alex-pletnev/ultimatum-game` → private в GH.

   **Требует явного разрешения пользователя на SSH в прод-VM (ubuntu@158.160.48.113):**
   - `ssh-copy-id` pubkey → `~ubuntu/.ssh/authorized_keys` на VM.
   - `scp scripts/vm-redeploy.sh ubuntu@…:/opt/app/redeploy.sh` + `chmod +x`.
   - Sanity-check: `ssh ubuntu@… 'bash /opt/app/redeploy.sh $CURRENT_SHA'` → должен redeploy'ить текущее и вернуть healthy.

   Причина разделения: harness блокирует `ssh` в прод-VM без явного user-authorization
   (сообщение «Production Reads via remote shell without explicit user authorization»).
   Пользователь одним `y` разрешает пачку — дальше агент отработает end-to-end.

   После bootstrap: `/tmp/sa-key.json` и `/tmp/utg-deploy*` — удалить (`shred -u`
   или `rm`), они больше не нужны и не должны лежать на диске.
3. **Как выкатить релиз:** GH Actions UI → Release → Run workflow → `sha` пусто (= main HEAD) или конкретный SHA → Run.
4. **Как откатиться вручную:** Run workflow с явным `sha` = коммит, который работал.
5. **Ротация ключей:**
   - SA-key: `yc iam key create` → обновить GH secret → `yc iam key delete <old>`.
   - SSH-key: сгенерить новый, положить pubkey на VM, обновить GH secret, удалить старый pubkey из VM `authorized_keys`.
6. **Диагностика:**
   - CI red → GH Actions logs, обычно stack trace теста.
   - Release red на build → docker-build logs в step Actions.
   - Release red на deploy → SSH tail `/var/log/utg-redeploy.log` на VM или сразу видно в GHA output.
   - Health-check red после deploy → auto-rollback уже произошёл, `docker ps` покажет старый SHA. Смотреть `docker logs ultimatum-game` на новом (упавшем) SHA — но контейнер уже удалён. Хранить последние логи rollback'а — future work (не в этом skope).

## Data flow

```
Разработчик → git push → GitHub
                          ├→ [ci.yml] check   → PR green/red
                          └→ (main push тоже check)

Разработчик → GH Actions UI → workflow_dispatch(sha)
                                  ↓
                          [release.yml]
                                  ↓
                      ┌─ build-and-push ─┐
                      │  ./gradlew build │
                      │  docker build    │
                      │  docker push     │
                      └────────┬─────────┘
                               ↓
                       ┌── deploy ──┐
                       │  ssh VM    │
                       │  /opt/app/redeploy.sh $SHA
                       │      ├ pull new         │
                       │      ├ rm+run new       │
                       │      ├ health-check 60s │
                       │      └ rollback if red  │
                       └────────────┘
```

## Error handling / failure modes

| Что упало | Итог | Что делать человеку |
|---|---|---|
| Test/detekt в CI | CI red | Читать logs, чинить |
| Docker build | release red, VM не тронута | Читать logs, чинить локально |
| Docker push | release red, VM не тронута | Проверить SA-роли, network |
| SSH connect | release red, VM не тронута | Проверить `VM_SSH_PRIVATE_KEY`, IP, sshd |
| `docker pull` на VM | release red, старая версия крутится | Проверить SA-роли на CR, тег |
| `docker run` на VM | release red, VM без контейнера | Ручной SSH, `docker logs`, `docker run` |
| Health-check timeout | auto-rollback → PREV_SHA поднят, release red | Проверить `/actuator/health` логи, диагностировать почему упало |
| Rollback (нет PREV_SHA) | release red, VM broken | Ручной SSH, `docker run` предыдущего известного SHA |
| Rollback (rollback тоже упал) | release red, VM broken | Ручной SSH, `docker logs`, дальше по ситуации |

## Тестирование

- **CI**: сразу проверяется — создаётся тестовый PR (либо push в feature-ветку), проверяется что workflow триггерится и `./gradlew check` завершается.
- **Release**: проверяется реальным `workflow_dispatch` с текущим main-SHA. Ожидание:
  1. Build прошёл, image в CR.
  2. SSH зашёл, redeploy запустился.
  3. `/actuator/health` → UP.
  4. `docker inspect ultimatum-game` показывает новый SHA.
- **Rollback**: искусственно триггерим failing deploy (например, коммит с намеренно сломанным `application-prod.properties`) → dispatch → ожидание: auto-rollback на PREV_SHA, release red, VM здорова на старом SHA.

## Открытые вопросы

- (Нет открытых на момент утверждения дизайна пользователем.)

## Реализация — порядок работ

1. Написать `scripts/vm-redeploy.sh` + `.github/workflows/ci.yml` + `.github/workflows/release.yml` + `docs/14-cicd.md`.
2. Удалить `scripts/deploy-yc.sh` и unstaged правку в нём.
3. `./gradlew check` локально — sanity.
4. Commit + push (все файлы в один commit по CLAUDE.md-правилам).
5. Ждать пока пользователь пройдёт bootstrap (SA-key, роль pusher, SSH-key, копирование redeploy.sh на VM).
6. Первый `workflow_dispatch` для release — проверить end-to-end.
7. Обновить `T-090` (Phase 5 — Frontend cutover остаётся; но CD-инфра теперь есть). Возможно завести новый таск `T-101 CI/CD pipeline` — решить на этапе `/task-add`.
