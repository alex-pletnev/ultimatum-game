# 13. Deploy — prod runbook

Как задеплоить `ultimatum-game` бэк с нуля на бесплатный PaaS так, чтобы фронт с
GitHub Pages мог к нему ходить. Задача-контекст — T-090.

Кандидат хостинга по умолчанию — **Fly.io + Neon Postgres**. Причины: обе платформы
имеют вменяемый free-tier, поддерживают Docker deployment, регион можно выбрать
близко к пользователю. Альтернативы (Render, Railway, Koyeb) — те же шаги, отличия
только в CLI-командах и точных именах env-vars.

## 13.1 Что нужно на входе

- Docker установлен локально (для проверки образа перед push'ем; сам PaaS собирает
  из Dockerfile'а).
- Аккаунт Fly.io (`flyctl auth signup`).
- Аккаунт Neon (или другой managed Postgres 15+).
- Домен фронта (`https://<gh-user>.github.io` или собственный).

## 13.2 Env-vars контракт

Приложение читает из env (все с dev-defaults в `application.properties`):

| Var | Prod-значение | Dev-default | Where set |
|-----|---------------|-------------|-----------|
| `SPRING_PROFILES_ACTIVE` | `prod` | (dev) | Fly.io env |
| `JWT_SIGNING_KEY` | `openssl rand -base64 48` | — (required) | Fly.io **secret** |
| `DB_URL` | `jdbc:postgresql://<neon-host>/<db>?sslmode=require` | localhost | Fly.io **secret** |
| `DB_USER` | Neon user | `postgres` | Fly.io **secret** |
| `DB_PASSWORD` | Neon password | `postgres` | Fly.io **secret** |
| `APP_CORS_ORIGINS` | `https://<gh-user>.github.io` | `http://localhost:[*]` | Fly.io env |
| `PORT` | `8080` (Fly.io выставит сам) | 8080 | Fly.io platform |

CORS/WS читают один и тот же список — comma-separated, если origin'ов несколько.

## 13.3 Локальная проверка Docker-образа

Перед первым deploy'ем — собрать и погонять контейнер локально:

```bash
# 1. Собрать
docker build -t ultimatum-game:local .

# 2. Проверить, что стартует с prod-профилем и внешним datasource.
#    Postgres поднят из compose.yaml (localhost:5432).
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SIGNING_KEY="$(openssl rand -base64 48)" \
  -e DB_URL="jdbc:postgresql://host.docker.internal:5432/postgres" \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e APP_CORS_ORIGINS="http://localhost:3000" \
  ultimatum-game:local

# 3. В другом терминале:
curl -s http://localhost:8080/api/v1/actuator/health
# → {"status":"UP"}
```

Если старт не идёт — смотреть логи контейнера: скорее всего Flyway не смог
подключиться к БД (проверить `DB_URL`) или `JWT_SIGNING_KEY` короче 32 байт.

## 13.4 Deploy на Fly.io

### Setup DB (Neon)

1. `console.neon.tech` → создать проект → регион, максимально близкий к Fly.io региону.
2. Скопировать connection string (postgres-format): `postgresql://user:pwd@host/db?sslmode=require`.
3. Преобразовать в JDBC-формат: `jdbc:postgresql://host/db?sslmode=require&user=user&password=pwd`
   или задать user/pwd отдельно (как ниже).

### Setup app (Fly.io)

```bash
# Login
flyctl auth login

# Init (единожды, в корне репо)
flyctl launch --no-deploy --copy-config --name ultimatum-game --region <region>
# → создаётся fly.toml. Не деплоить пока — сначала secrets.

# Secrets (не в git!)
flyctl secrets set \
  JWT_SIGNING_KEY="$(openssl rand -base64 48)" \
  DB_URL="jdbc:postgresql://<neon-host>/<db>?sslmode=require" \
  DB_USER="<neon-user>" \
  DB_PASSWORD="<neon-pwd>"

# Env (публичные)
flyctl secrets set \
  SPRING_PROFILES_ACTIVE=prod \
  APP_CORS_ORIGINS="https://<gh-user>.github.io"
# (для Fly.io разница между "secret" и "env" минимальна — оба скрыты от git)

# Deploy
flyctl deploy
```

### Проверка

```bash
flyctl status
flyctl logs

curl -s https://ultimatum-game.fly.dev/api/v1/actuator/health
# → {"status":"UP"}

# Prometheus scrape (при желании подключить Grafana Cloud)
curl -s https://ultimatum-game.fly.dev/api/v1/actuator/prometheus | head
```

## 13.5 Smoke-test (end-to-end)

Проверка REST + JWT + STOMP через реальный URL:

```bash
API=https://ultimatum-game.fly.dev/api/v1

# 1. Register + login
curl -s -X POST "$API/auth/register" -H 'Content-Type: application/json' \
  -d '{"username":"smoke","password":"smoketest123"}'
JWT=$(curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"smoke","password":"smoketest123"}' | jq -r .token)
echo "$JWT"  # непустая строка

# 2. Authorized endpoint
curl -s -H "Authorization: Bearer $JWT" "$API/session?state=ACTIVE"
# → [] или список

# 3. WS handshake (нужен wscat)
wscat -c "wss://ultimatum-game.fly.dev/api/v1/ws" -H "Authorization: Bearer $JWT"
# → Connected (press CTRL+C to quit)
```

Если хоть один шаг сломался — искать причину в `flyctl logs` (JSON-логи, ищем по
`level=ERROR`).

## 13.6 Ротация secrets

- `JWT_SIGNING_KEY`: `flyctl secrets set JWT_SIGNING_KEY="$(openssl rand -base64 48)"`.
  Все выданные ранее JWT инвалидируются (подпись перестанет проверяться) — все
  клиенты потребуют re-login. Планировать в maintenance-окно.
- `DB_PASSWORD`: сначала обновить в Neon dashboard → `flyctl secrets set DB_PASSWORD=...`
  → Fly перезапустит app. Downtime — секунды.

## 13.7 Откат

```bash
# Список релизов
flyctl releases

# Откат на предыдущий
flyctl releases rollback <version>
```

Если проблема в миграции Flyway — откат образа не откатит схему. Для схемы —
писать compensating migration (`V<next>__revert_of_V<broken>.sql`), а не редактировать
уже применённые версии (см. `docs/10-configuration.md` § миграции).

## 13.8 Что дальше

- **Frontend cutover**: см. `docs/tasks/T-090-prod-deploy-readiness.md` § Phase 5 —
  спецификация для интегратора фронта (env-vars, GH Actions, JWT flow, smoke).
- **Observability в prod**: логи Fly.io → Grafana Cloud Loki (через logfmt drain).
  См. `docs/12-observability.md` — там же список dashboards, которые полезно
  завести на Grafana Cloud.
- **JWT-revocation TTL** (T-061): сейчас revocation list растёт unbounded в памяти;
  в prod с длительным uptime стоит закрыть до раскатки на реальных пользователей.

## См. также

- `docs/10-configuration.md` — env-vars, application.properties, миграции.
- `docs/12-observability.md` — логи, метрики, MDC.
- `docs/tasks/T-090-prod-deploy-readiness.md` — история задачи.
