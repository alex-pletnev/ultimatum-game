#!/usr/bin/env bash
# T-090 Phase 4 — Deploy backend to Yandex.Cloud (Serverless Container + Managed PG + Lockbox).
#
# Идемпотентный: existing ресурсы переиспользуются, повторный запуск после
# сбоя продолжает с того места где остановился.
#
# Prereqs:
#   - yc CLI сконфигурирован (yc config list → folder-id=<ultimatum-game folder>)
#   - docker running (colima start)
#   - docker образ ultimatum-game:local собран (или скрипт соберёт)
#   - jq, openssl в PATH
#
# Usage:
#   bash scripts/deploy-yc.sh [<gh-user>]
#
# Default gh-user = alex-pletnev. Если фронт под другим — передай аргументом.

set -euo pipefail

# ---- Config ----------------------------------------------------------------
GH_USER="${1:-alex-pletnev}"
CORS_ORIGINS="https://${GH_USER}.github.io"

REGISTRY_NAME="ultimatum-game"
IMAGE_TAG="${IMAGE_TAG:-v1}"

PG_CLUSTER_NAME="ultimatum-pg"
PG_USER="utg"
PG_DB="ultimatum"
PG_PRESET="s3-c2-m8"          # 2 vCPU + 8GB RAM — минимум ~1500₽/мес
PG_DISK_GB=20

CONTAINER_NAME="ultimatum-game"
SA_NAME="utg-sa"
SECRET_NAME="utg-secrets"

FOLDER_ID=$(yc config get folder-id)
ZONE=$(yc config get compute-default-zone)
SUBNET_RANGE="10.130.0.0/24"

# docker-credential-yc:
#   - ручная установка (curl | bash) → $HOME/yandex-cloud/bin
#   - brew cask → /opt/homebrew/Caskroom/yandex-cloud-cli/*/yandex-cloud-cli/bin
export PATH="$HOME/yandex-cloud/bin:$PATH"
if ! command -v docker-credential-yc >/dev/null 2>&1; then
  YC_CASK_BIN=$(ls -d /opt/homebrew/Caskroom/yandex-cloud-cli/*/yandex-cloud-cli/bin 2>/dev/null | head -1)
  [[ -n "$YC_CASK_BIN" ]] && export PATH="$YC_CASK_BIN:$PATH"
fi

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!! %s\033[0m\n' "$*"; }

# ---- Pre-flight ------------------------------------------------------------
log "Pre-flight checks"
for cmd in yc docker jq openssl; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "missing: $cmd"; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "docker daemon not running (colima start?)"; exit 1; }
[[ -z "$FOLDER_ID" ]] && { echo "yc folder-id not set — run 'yc init'"; exit 1; }
echo "folder-id=$FOLDER_ID zone=$ZONE gh-user=$GH_USER"

# ---- Step 1: Container Registry --------------------------------------------
log "Step 1: Container Registry"
REGISTRY_ID=$(yc container registry list --format json | jq -r ".[] | select(.name==\"$REGISTRY_NAME\") | .id" | head -1)
if [[ -z "$REGISTRY_ID" ]]; then
  REGISTRY_ID=$(yc container registry create --name "$REGISTRY_NAME" --format json | jq -r .id)
  echo "Registry created: $REGISTRY_ID"
else
  echo "Registry exists: $REGISTRY_ID (reusing)"
fi
IMAGE="cr.yandex/$REGISTRY_ID/ultimatum-game:$IMAGE_TAG"

# ---- Step 2: Docker build (linux/amd64) + push -----------------------------
log "Step 2: Docker build (linux/amd64 — YC Serverless Container runs on amd64) + push"
# ВСЕГДА пересобираем под amd64 — image ultimatum-game:local собран под host-arch
# (arm64 на Apple Silicon → 'exec format error' в YC при попытке запуска).
# BuildKit не требуется — cross-build через QEMU/binfmt автоматически.
docker build --platform=linux/amd64 -t "$IMAGE" .
yc container registry configure-docker >/dev/null 2>&1 || true
docker push "$IMAGE"

# ---- Step 3: VPC network + subnet ------------------------------------------
log "Step 3: VPC network + subnet"
if ! yc vpc network get default >/dev/null 2>&1; then
  yc vpc network create --name default
fi
SUBNET_ID=$(yc vpc subnet list --format json | jq -r ".[] | select(.name==\"default-$ZONE\") | .id" | head -1)
if [[ -z "$SUBNET_ID" ]]; then
  yc vpc subnet create \
    --name "default-$ZONE" \
    --zone "$ZONE" \
    --network-name default \
    --range "$SUBNET_RANGE"
  SUBNET_ID=$(yc vpc subnet get "default-$ZONE" --format json | jq -r .id)
fi
NETWORK_ID=$(yc vpc network get default --format json | jq -r .id)
echo "network-id=$NETWORK_ID subnet-id=$SUBNET_ID"

# ---- Step 4: Managed PostgreSQL --------------------------------------------
log "Step 4: Managed PostgreSQL cluster (5-10 min если создаём заново)"
PG_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)
if yc managed-postgresql cluster get "$PG_CLUSTER_NAME" >/dev/null 2>&1; then
  warn "PG cluster $PG_CLUSTER_NAME уже существует — переиспользуем."
  warn "Пароль в этом запуске сгенерён новый, но НЕ применяется к existing cluster."
  warn "Если пароль потерян — обнови: yc managed-postgresql user update $PG_USER --cluster-name $PG_CLUSTER_NAME --password '<new>'"
  read -p "Нажми Enter чтобы сменить пароль на новый сгенерённый, или Ctrl-C чтобы отменить: "
  yc managed-postgresql user update "$PG_USER" \
    --cluster-name "$PG_CLUSTER_NAME" \
    --password "$PG_PASSWORD"
else
  yc managed-postgresql cluster create \
    --name "$PG_CLUSTER_NAME" \
    --environment production \
    --network-name default \
    --host "zone-id=$ZONE,subnet-id=$SUBNET_ID" \
    --resource-preset "$PG_PRESET" \
    --disk-size "$PG_DISK_GB" \
    --disk-type network-ssd \
    --postgresql-version 15 \
    --user "name=$PG_USER,password=$PG_PASSWORD" \
    --database "name=$PG_DB,owner=$PG_USER" \
    --deletion-protection
fi
PG_HOST=$(yc managed-postgresql host list --cluster-name "$PG_CLUSTER_NAME" --format json | jq -r '.[0].name')
DB_URL="jdbc:postgresql://${PG_HOST}:6432/${PG_DB}?sslmode=require"
echo "PG host: $PG_HOST"
echo "DB_URL:  $DB_URL"

# ---- Step 5: Service Account + IAM roles -----------------------------------
log "Step 5: Service Account + IAM roles"
SA_ID=$(yc iam service-account list --format json | jq -r ".[] | select(.name==\"$SA_NAME\") | .id" | head -1)
if [[ -z "$SA_ID" ]]; then
  SA_ID=$(yc iam service-account create --name "$SA_NAME" --format json | jq -r .id)
fi
for role in container-registry.images.puller lockbox.payloadViewer vpc.user; do
  yc resource-manager folder add-access-binding "$FOLDER_ID" \
    --role "$role" \
    --subject "serviceAccount:$SA_ID" 2>&1 | tail -1 || true
done
echo "sa-id=$SA_ID"

# ---- Step 6: Lockbox secret ------------------------------------------------
log "Step 6: Lockbox secret ($SECRET_NAME)"
JWT_KEY=$(openssl rand -base64 48)
PAYLOAD="[{\"key\":\"JWT_SIGNING_KEY\",\"text_value\":\"$JWT_KEY\"},{\"key\":\"DB_PASSWORD\",\"text_value\":\"$PG_PASSWORD\"}]"
if yc lockbox secret get "$SECRET_NAME" >/dev/null 2>&1; then
  yc lockbox secret add-version --name "$SECRET_NAME" --payload "$PAYLOAD" >/dev/null
else
  yc lockbox secret create --name "$SECRET_NAME" --payload "$PAYLOAD" >/dev/null
fi
SECRET_ID=$(yc lockbox secret get "$SECRET_NAME" --format json | jq -r .id)
SECRET_VER=$(yc lockbox secret list-versions --name "$SECRET_NAME" --format json | jq -r '.[0].id')
echo "secret-id=$SECRET_ID version=$SECRET_VER"

# ---- Step 7: Serverless Container ------------------------------------------
log "Step 7: Serverless Container + revision deploy"
if ! yc serverless container get "$CONTAINER_NAME" >/dev/null 2>&1; then
  yc serverless container create --name "$CONTAINER_NAME" >/dev/null
fi
CONTAINER_ID=$(yc serverless container get "$CONTAINER_NAME" --format json | jq -r .id)

yc serverless container revision deploy \
  --container-name "$CONTAINER_NAME" \
  --image "$IMAGE" \
  --service-account-id "$SA_ID" \
  --cores 1 --core-fraction 100 --memory 512MB \
  --concurrency 16 \
  --execution-timeout 60s \
  --network-id "$NETWORK_ID" \
  --environment "SPRING_PROFILES_ACTIVE=prod" \
  --environment "DB_URL=$DB_URL" \
  --environment "DB_USER=$PG_USER" \
  --environment "APP_CORS_ORIGINS=$CORS_ORIGINS" \
  --secret "name=$SECRET_NAME,version-id=$SECRET_VER,key=JWT_SIGNING_KEY,environment-variable=JWT_SIGNING_KEY" \
  --secret "name=$SECRET_NAME,version-id=$SECRET_VER,key=DB_PASSWORD,environment-variable=DB_PASSWORD"

# Публичный доступ (без IAM auth) — чтобы фронт с GH Pages мог ходить
yc serverless container allow-unauthenticated-invoke --name "$CONTAINER_NAME" >/dev/null 2>&1 || true

CONT_URL=$(yc serverless container get "$CONTAINER_NAME" --format json | jq -r .url | sed 's:/*$::')

# ---- Done ------------------------------------------------------------------
cat <<EOF

==========================================================================
✓ Deploy complete

Container URL:  $CONT_URL
API base:       $CONT_URL/api/v1
WS URL:         wss://${CONT_URL#https://}/api/v1/ws

Smoke:
  curl -s $CONT_URL/api/v1/actuator/health
  # ожидание: {"status":"UP"} (первый запрос может занять до 20s — cold start Spring Boot)

Frontend env-vars:
  VITE_API_BASE_URL=$CONT_URL/api/v1
  VITE_WS_URL=wss://${CONT_URL#https://}/api/v1/ws

Логи runtime:
  yc serverless container logs read $CONTAINER_NAME --since 5m

Ресурсы (для последующего удаления):
  registry     $REGISTRY_ID
  pg-cluster   $PG_CLUSTER_NAME  (с --deletion-protection)
  service-acc  $SA_ID
  secret       $SECRET_NAME
  container    $CONTAINER_NAME

==========================================================================
EOF
