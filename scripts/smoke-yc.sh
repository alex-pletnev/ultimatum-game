#!/usr/bin/env bash
# T-090 — Smoke-тест деплоя в Yandex.Cloud.
# Запускать после scripts/deploy-yc.sh (или когда есть подозрение что сервис лёг).
#
# Что делает:
#   1. Читает URL из yc.
#   2. Curl /actuator/health с retry (учитывает cold start).
#   3. POST /auth/quick-register — проверяет что бизнес-логика + БД доступны.
#   4. Показывает последние логи контейнера через yc logging.
#   5. Показывает revision status.
#
# Usage: bash scripts/smoke-yc.sh
# Prereq: yc сконфигурирован, VPN РФ включён (иначе endpoint может быть недоступен).

set -uo pipefail

CONTAINER_NAME="ultimatum-game"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }
bad() { printf '\033[1;31m✗ %s\033[0m\n' "$*"; }

# --- Step 1: получить URL ---
log "Step 1: получить container URL"
CONT_URL=$(yc serverless container get "$CONTAINER_NAME" --format json | jq -r .url | sed 's:/*$::')
CONT_ID=$(yc serverless container get "$CONTAINER_NAME" --format json | jq -r .id)
echo "URL: $CONT_URL"
echo "ID:  $CONT_ID"
API="$CONT_URL/api/v1"

# --- Step 2: revision status ---
log "Step 2: revision status"
yc serverless container revision list --container-name "$CONTAINER_NAME" --format json \
  | jq -r '.[] | "\(.id) status=\(.status) created=\(.created_at) image=\(.image.image_url)"' \
  | head -3

# --- Step 3: health с retry (cold start ~15-20s) ---
log "Step 3: /actuator/health с retry (cold start ~15-20s)"
HEALTH_OK=0
for i in 1 2 3 4 5; do
  START=$(date +%s)
  HTTP_CODE=$(curl -s -o /tmp/smoke-health.txt -w "%{http_code}" --max-time 30 "$API/actuator/health" 2>&1 || echo "curl-fail")
  END=$(date +%s)
  BODY=$(cat /tmp/smoke-health.txt 2>/dev/null)
  echo "attempt $i: HTTP=$HTTP_CODE ($((END-START))s), body: $BODY"
  if [[ "$HTTP_CODE" == "200" ]]; then
    HEALTH_OK=1
    break
  fi
  sleep 3
done
[[ $HEALTH_OK -eq 1 ]] && ok "health UP" || bad "health не отвечает 200 после 5 попыток"

# --- Step 4: register (проверяет БД + JWT) ---
log "Step 4: POST /auth/quick-register"
NICK="smoke-$(date +%s)"
REG_RESP=$(curl -s -X POST -H 'Content-Type: application/json' \
  -d "{\"nickname\":\"$NICK\",\"password\":\"smoketest123\",\"role\":\"PLAYER\"}" \
  --max-time 30 "$API/auth/quick-register")
echo "$REG_RESP"
if echo "$REG_RESP" | grep -q "accessToken"; then
  ok "auth flow работает, JWT получен"
else
  bad "register не вернул accessToken"
fi

# --- Step 5: логи контейнера через yc (последние 5 минут) ---
log "Step 5: container logs (последние 5 минут, ERROR/WARN + startup)"
yc logging read --group-name default --resource-ids "$CONT_ID" --since 5m --format json 2>/dev/null \
  | jq -r '.[] | select(.level=="ERROR" or .level=="WARN" or (.message | test("Started UltimatumGameApplicationKt|Tomcat started|APPLICATION FAILED"))) | "\(.timestamp) [\(.level)] \(.message)"' \
  | tail -30
