#!/usr/bin/env bash
# T-101 — VM-side redeploy script. Живёт в repo, копируется на VM в /opt/app/redeploy.sh.
#
# Атомарный swap контейнера ultimatum-game на указанный SHA-тег из cr.yandex.
# При провале health-check в течение 60s — auto-rollback на предыдущий SHA.
#
# Запускается GitHub Actions release workflow'ом через SSH:
#   bash /opt/app/redeploy.sh <short-sha>

set -uo pipefail

exec > >(tee -a /var/log/utg-redeploy.log) 2>&1
echo "===== $(date -u +%FT%TZ) redeploy start: $* ====="

NEW_SHA="${1:-}"
[[ -z "$NEW_SHA" ]] && { echo "ERROR: usage: $0 <short-sha-or-tag>"; exit 2; }

# ---- Константы (значения известны из bootstrap; не секреты) ----
REG_ID="crp7b8ldv830cfseac0e"
IMAGE="cr.yandex/$REG_ID/ultimatum-game"
SECRET_ID="e6qaub97dnasecoh2k8t"
PG_HOST="rc1a-m2ijrdhocr72r55r.mdb.yandexcloud.net"
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
# Password через stdin, чтобы TOKEN не светился в ps.
echo "$TOKEN" | docker login -u iam --password-stdin cr.yandex
# Явный guard: без set -e провал pull'а не остановит скрипт, а мы не хотим
# делать docker rm -f под мёртвый tag.
docker pull "$IMAGE:$NEW_SHA" || { echo "ERROR: docker pull $IMAGE:$NEW_SHA failed"; exit 1; }

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
