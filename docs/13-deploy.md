# 13. Deploy — prod runbook (Yandex.Cloud)

Как задеплоить `ultimatum-game` бэк в Yandex.Cloud так, чтобы фронт с GitHub
Pages мог к нему ходить. Задача-контекст — T-090.

> **Redeploy новой версии на живую VM** — через GitHub Actions release workflow.
> Runbook: `docs/14-cicd.md`. Этот файл описывает **первичный** deploy (создание
> VM, PG, secrets, cloud-init) — то, что делается один раз или при пересоздании инфры.

**Почему Yandex.Cloud**: доступность из РФ без VPN, юрисдикция и юрлицо в РФ
(готово к 152-ФЗ если появятся персданные), managed Postgres, Serverless
Containers с pay-per-request. Free-tier'а нет, но при регистрации выдают
приветственный грант (~5000₽ на 60 дней) — на MVP этого хватает.

Альтернативы для доступности в РФ (не покрыты этим runbook'ом): **Selectel**,
**Timeweb Cloud** (обычная VM + PG руками — дешевле, ~250-500₽/мес).

## 13.1 Что нужно на входе

- Docker (для локального build'а и push'а в registry).
- Аккаунт в Yandex.Cloud + активированный приветственный грант.
- `yc` CLI (`curl -sSL https://storage.yandexcloud.net/yandexcloud-yc/install.sh | bash`).
- Домен фронта (`https://<gh-user>.github.io` или собственный).

## 13.2 Env-vars контракт

Приложение читает из env (все с dev-defaults в `application.properties`):

| Var | Prod-значение | Dev-default | Where set |
|-----|---------------|-------------|-----------|
| `SPRING_PROFILES_ACTIVE` | `prod` | (dev) | Container env |
| `JWT_SIGNING_KEY` | `openssl rand -base64 48` | — (required) | Lockbox secret |
| `DB_URL` | `jdbc:postgresql://<pg-host>:6432/<db>?sslmode=verify-full&sslrootcert=/etc/ssl/certs/YandexCA.crt` | localhost | Container env |
| `DB_USER` | user из Managed PG | `postgres` | Container env |
| `DB_PASSWORD` | password из Managed PG | `postgres` | Lockbox secret |
| `APP_CORS_ORIGINS` | `https://<gh-user>.github.io` | `http://localhost:[*]` | Container env |
| `PORT` | `8080` (YC выставит через `$PORT`) | 8080 | YC platform |

CORS/WS читают один и тот же список — comma-separated для нескольких origin'ов.

## 13.3 Часть A — что делает владелец аккаунта (ручные шаги в UI/CLI)

Эти шаги требуют паспортные данные / телефон РФ / согласие на договор.

### A.1 Регистрация + грант
1. `console.cloud.yandex.ru` → войти через Яндекс.ID.
2. Создать **платёжный аккаунт** — паспортные данные, привязка карты (для активации гранта; списаний в пределах гранта не будет).
3. Активировать приветственный грант (обычно предлагается автоматически).
4. Проверить: платёжный аккаунт в статусе **ACTIVE**, в разделе «Гранты» видна активная скидка.

### A.2 Облако и папка
1. Cloud создаётся автоматически при регистрации (например `default`).
2. Внутри Cloud — создать folder: `ultimatum-game` (в UI: «Сервисы» → «Resource Manager» → «Создать каталог»).
3. Записать `cloud-id` и `folder-id` (видны в URL и в свойствах folder'а).

### A.3 CLI
```bash
curl -sSL https://storage.yandexcloud.net/yandexcloud-yc/install.sh | bash
# перезапустить shell либо `source ~/.zshrc`
yc init
# на этапе setup — вставить OAuth-token из https://oauth.yandex.ru/...
# выбрать cloud-id и folder-id из шагов выше
# default zone: ru-central1-a
```

Проверка:
```bash
yc config list
# → token: ..., cloud-id: b1g..., folder-id: b1g...
```

### A.4 Отдать агенту

После A.1–A.3 — сообщить:
- `folder-id`;
- default zone (`ru-central1-a` рекомендую);
- готовность продолжить (агент сделает A.5 — E из своего терминала).

## 13.4 Часть B — что делает агент (yc CLI)

Актуальный скрипт для полного первичного deploy'а (Compute VM вариант) —
`scripts/deploy-yc-vm.sh`:

```bash
bash scripts/deploy-yc-vm.sh [<gh-user>]
# default gh-user = alex-pletnev
```

Создаёт SA + PG + Lockbox + static IP + Ubuntu VM с cloud-init'ом
(docker + caddy + первый `docker run`).

> **Устарело**: разделы B.1–B.7 ниже написаны под изначальный Serverless
> Container-вариант (заброшен после pivot'а — cold-start Spring Boot ~40s
> > YC execution-timeout). Оставлены как историческая справка на случай,
> если Serverless-вариант когда-нибудь снова окажется приемлемым (например,
> с GraalVM native-image). Мёртвые скрипты `scripts/deploy-yc.sh` и
> `scripts/smoke-yc.sh` удалены в T-101; в git history сохранены.

### B.1 Container Registry
```bash
yc container registry create --name ultimatum-game
# → registry-id: crp...
export CR_ID=crp...
# authenticate docker с yc
yc container registry configure-docker
```

### B.2 Push образа
```bash
docker build -t cr.yandex/$CR_ID/ultimatum-game:v1 .
docker push cr.yandex/$CR_ID/ultimatum-game:v1
```

### B.3 Managed PostgreSQL
```bash
# Минимальная конфигурация: 1 host, s3-c2-m8 (2 vCPU, 8GB RAM), 20GB SSD.
# На грантах ~1500-2000₽/мес.
yc managed-postgresql cluster create \
  --name ultimatum-pg \
  --environment production \
  --network-name default \
  --host zone-id=ru-central1-a,subnet-id=$(yc vpc subnet get default-ru-central1-a --format json | jq -r .id) \
  --resource-preset s3-c2-m8 \
  --disk-size 20 \
  --disk-type network-ssd \
  --postgresql-version 15 \
  --user name=utg,password=$(openssl rand -base64 24) \
  --database name=ultimatum,owner=utg \
  --deletion-protection

# Получить connection endpoint
yc managed-postgresql cluster get ultimatum-pg --format json | jq -r '.host'
# → <cluster-id>.mdb.yandexcloud.net (порт 6432 для connection pooler pgbouncer)
```

### B.4 Lockbox secrets
```bash
yc lockbox secret create --name utg-secrets \
  --payload "[{'key':'JWT_SIGNING_KEY','text_value':'$(openssl rand -base64 48)'},{'key':'DB_PASSWORD','text_value':'<pg-password-from-step-B.3>'}]"

# Service account для Container'а
yc iam service-account create --name utg-sa
export SA_ID=$(yc iam service-account get utg-sa --format json | jq -r .id)

# Права: pull из registry, чтение lockbox
yc resource-manager folder add-access-binding <folder-id> \
  --role container-registry.images.puller --subject serviceAccount:$SA_ID
yc resource-manager folder add-access-binding <folder-id> \
  --role lockbox.payloadViewer --subject serviceAccount:$SA_ID
```

### B.5 Serverless Container
```bash
yc serverless container create --name ultimatum-game
export CONT_ID=$(yc serverless container get ultimatum-game --format json | jq -r .id)

# Скачать сертификат CA для PostgreSQL (нужен для sslmode=verify-full)
# Обычно кладём в Dockerfile при build'е, но можно и в runtime через init-script.
# См. Dockerfile — сертификат уже встроен в eclipse-temurin (default CA-bundle).
# Для Yandex PG — нужен их CA:
#   https://storage.yandexcloud.net/cloud-certs/CA.pem
# В runbook рассматриваем два варианта:
#   (a) `sslmode=require` — без verify-full, проще; CA не нужен. РЕКОМЕНДУЕТСЯ для MVP.
#   (b) добавить CA в Dockerfile:
#         RUN wget -O /etc/ssl/certs/YandexCA.crt \
#             https://storage.yandexcloud.net/cloud-certs/CA.pem
#         RUN update-ca-certificates
#       и sslmode=verify-full.

yc serverless container revision deploy \
  --container-name ultimatum-game \
  --image cr.yandex/$CR_ID/ultimatum-game:v1 \
  --service-account-id $SA_ID \
  --cores 1 --core-fraction 100 --memory 512MB \
  --concurrency 16 \
  --execution-timeout 60s \
  --environment SPRING_PROFILES_ACTIVE=prod \
  --environment "DB_URL=jdbc:postgresql://<pg-host>:6432/ultimatum?sslmode=require" \
  --environment DB_USER=utg \
  --environment "APP_CORS_ORIGINS=https://<gh-user>.github.io" \
  --secret name=utg-secrets,version-id=<lockbox-version>,key=JWT_SIGNING_KEY,environment-variable=JWT_SIGNING_KEY \
  --secret name=utg-secrets,version-id=<lockbox-version>,key=DB_PASSWORD,environment-variable=DB_PASSWORD

# Публичный HTTPS endpoint
yc serverless container get ultimatum-game --format json | jq -r .url
# → https://bba...-containers.yandexcloud.net
```

**Внимание — cold start**. Spring Boot стартует ~12-15s (см. bootRun smoke).
Serverless Containers держат «холодный» инстанс `min_instances=0` по умолчанию —
первый после простоя запрос ждёт full startup. Для реального прод-показа
установить:

```bash
yc serverless container revision deploy \
  ... \
  --min-instances 1  # платно, но убирает cold start (~500-800₽/мес)
```

## 13.5 Smoke-test (end-to-end)

```bash
API=$(yc serverless container get ultimatum-game --format json | jq -r .url)/api/v1
echo "$API"

# 1. Register + login
NICK="smoke-$RANDOM"
curl -s -X POST "$API/auth/quick-register" -H 'Content-Type: application/json' \
  -d "{\"nickname\":\"$NICK\",\"password\":\"smoketest123\",\"role\":\"PLAYER\"}" | jq

# 2. Authorized endpoint (ADMIN нужен для /session — регистрируй ADMIN или используй /statistics)
curl -s "$API/actuator/health"
# → {"status":"UP"}

# 3. WS handshake — Serverless Containers поддерживают WebSocket с версии ноября 2023.
#    Порт тот же, wscat нужен для проверки.
wscat -c "wss://$(echo $API | sed 's|https://||' | sed 's|/api/v1||')/api/v1/ws"
```

Если что-то сломалось — логи через:
```bash
yc serverless container logs read ultimatum-game --since 5m
```

## 13.6 Ротация secrets

```bash
# Новый JWT_SIGNING_KEY — все выданные ранее токены становятся невалидными
yc lockbox secret add-version --name utg-secrets \
  --payload "[{'key':'JWT_SIGNING_KEY','text_value':'$(openssl rand -base64 48)'},{'key':'DB_PASSWORD','text_value':'<current-db-pwd>'}]"

# Обновить ссылку на новую version в container revision
NEW_VER=$(yc lockbox secret list-versions utg-secrets --format json | jq -r '.[0].id')
yc serverless container revision deploy ... --secret ...version-id=$NEW_VER...
```

## 13.7 Откат

```bash
# История revision'ов
yc serverless container revision list --container-name ultimatum-game

# Активировать предыдущую
yc serverless container revision activate --id <prev-revision-id>
```

Если проблема в миграции Flyway — откат revision'а не откатит схему. Для схемы —
писать compensating migration (`V<next>__revert_of_V<broken>.sql`), а не редактировать
уже применённые версии (см. `docs/10-configuration.md` § миграции).

## 13.8 Стоимость (ориентировочно, вне гранта)

| Ресурс | Конфигурация | Стоимость (~) |
|--------|--------------|----------------|
| Managed PG | 1 host s3-c2-m8, 20GB SSD | 1500-2000₽/мес |
| Serverless Container (idle) | min-instances=0 | 0₽ (только требования) |
| Serverless Container (min=1) | 512MB, always-on | 500-800₽/мес |
| Container Registry | до 500MB | ~50₽/мес |
| Lockbox | 2 secrets | копейки |
| **Итого MVP** | без cold-start | ~2000-2800₽/мес |

Приветственный грант ~5000₽ покрывает 2 месяца работы MVP.

## 13.9 Что дальше

- **Frontend cutover**: см. `docs/tasks/T-090-prod-deploy-readiness.md` § Phase 5 —
  спецификация для интегратора фронта.
- **Observability в prod**: логи Serverless Containers доступны через Cloud
  Logging. Prometheus scrape пока не подключён (Serverless Containers не имеют
  постоянного IP для scrape'а извне — нужен push-адаптер, отдельная задача).
- **JWT-revocation TTL** (T-061): revocation list растёт unbounded в памяти;
  при `min-instances=1` в prod с длительным uptime стоит закрыть.

## См. также

- `docs/10-configuration.md` — env-vars, application.properties, миграции.
- `docs/12-observability.md` — логи, метрики, MDC.
- `docs/tasks/T-090-prod-deploy-readiness.md` — история задачи.
