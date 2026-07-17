#!/usr/bin/env bash
# T-090 Phase 4 (pivot) — Deploy to Compute VM instead of Serverless Container.
#
# Причина смены: YC Serverless Container не подходит для Spring Boot с 40s
# cold-start (Spring 12s + Hikari PG connect 29s + execution-timeout 60s).
# Тесты 2026-07-17 показали: приложение не успевает поднять Tomcat до YC
# request-timeout'а, YC перезапускает контейнер в цикле.
#
# Compute VM с Ubuntu + Docker + Caddy (HTTPS через nip.io) — always-on,
# нет cold-start'а, стандартный docker networking → PG connect как локально.
#
# Стоимость MVP:
#   - VM standard-v3 (2 vCPU 20%, 2GB): ~250-350₽/мес
#   - Managed PG (переиспользуем): ~1500₽/мес
#   - Static IP: ~2.5₽/мес (привязан к живой VM)
#   Итого: ~1750₽/мес (грант 5000₽ = ~3 месяца).
#
# Prereqs: Registry/PG/Lockbox/SA/VPC — уже созданы предыдущим deploy-yc.sh.
# Serverless Container можно удалить: yc serverless container delete ultimatum-game

set -uo pipefail

# ---- Config ----------------------------------------------------------------
VM_NAME="ultimatum-game"
IP_NAME="utg-vm-ip"

REGISTRY_ID="crp7b8ldv830cfseac0e"
IMAGE_TAG="${IMAGE_TAG:-v1}"
IMAGE="cr.yandex/$REGISTRY_ID/ultimatum-game:$IMAGE_TAG"

SA_NAME="utg-sa"
SECRET_NAME="utg-secrets"
PG_CLUSTER_NAME="ultimatum-pg"
PG_USER="utg"
PG_DB="ultimatum"

GH_USER="${1:-alex-pletnev}"
CORS_ORIGINS="https://${GH_USER}.github.io"

FOLDER_ID=$(yc config get folder-id)
ZONE=$(yc config get compute-default-zone)

log()  { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
warn() { printf '\033[1;33m!! %s\033[0m\n' "$*"; }
ok()   { printf '\033[1;32m✓ %s\033[0m\n' "$*"; }

# ---- Step 1: SA + roles ----------------------------------------------------
log "Step 1: Service Account (переиспользуем utg-sa)"
SA_ID=$(yc iam service-account list --format json | jq -r ".[] | select(.name==\"$SA_NAME\") | .id" | head -1)
[[ -z "$SA_ID" ]] && { echo "SA $SA_NAME не найден — запусти сначала scripts/deploy-yc.sh"; exit 1; }
# Compute VM должен уметь читать SA-token через metadata — роль compute.viewer + lockbox
for role in container-registry.images.puller lockbox.payloadViewer; do
  yc resource-manager folder add-access-binding "$FOLDER_ID" \
    --role "$role" --subject "serviceAccount:$SA_ID" 2>&1 | tail -1 || true
done
echo "sa-id=$SA_ID"

# ---- Step 2: PG cluster info ----------------------------------------------
log "Step 2: PG cluster info"
PG_HOST=$(yc managed-postgresql host list --cluster-name "$PG_CLUSTER_NAME" --format json | jq -r '.[0].name')
DB_URL="jdbc:postgresql://${PG_HOST}:6432/${PG_DB}?sslmode=require"
SECRET_ID=$(yc lockbox secret get "$SECRET_NAME" --format json | jq -r .id)
echo "PG:     $PG_HOST"
echo "secret: $SECRET_ID"

# ---- Step 3: Static public IP ---------------------------------------------
log "Step 3: Static public IP"
IP_ID=$(yc vpc address list --format json | jq -r ".[] | select(.name==\"$IP_NAME\") | .id" | head -1)
if [[ -z "$IP_ID" ]]; then
  IP_ID=$(yc vpc address create --name "$IP_NAME" --external-ipv4 zone="$ZONE" --format json | jq -r .id)
fi
PUBLIC_IP=$(yc vpc address get --id "$IP_ID" --format json | jq -r .external_ipv4_address.address)
echo "public IP: $PUBLIC_IP"
DOMAIN="${PUBLIC_IP//./-}.nip.io"
echo "domain:    $DOMAIN"

# ---- Step 4: Ubuntu 24.04 image ------------------------------------------
log "Step 4: Ubuntu 24.04 image"
UBUNTU_IMAGE=$(yc compute image get-latest-from-family ubuntu-2404-lts --folder-id standard-images --format json | jq -r .id)
echo "image: $UBUNTU_IMAGE"

# ---- Step 5: Subnet ID -----------------------------------------------------
SUBNET_ID=$(yc vpc subnet list --format json | jq -r ".[] | select(.zone_id==\"$ZONE\") | .id" | head -1)
echo "subnet: $SUBNET_ID"

# ---- Step 6: cloud-init user-data -----------------------------------------
log "Step 6: cloud-init user-data"
CLOUD_INIT=$(mktemp -t cloud-init-XXXXXX.yaml)
cat > "$CLOUD_INIT" <<CLOUDINIT
#cloud-config
package_update: true
packages:
  - docker.io
  - jq
  - curl
  - debian-keyring
  - debian-archive-keyring
  - apt-transport-https

write_files:
  - path: /opt/app/run.sh
    permissions: '0755'
    content: |
      #!/bin/bash
      set -e
      # SA metadata → IAM токен
      TOKEN=\$(curl -s -H "Metadata-Flavor: Google" http://169.254.169.254/computeMetadata/v1/instance/service-accounts/default/token | jq -r .access_token)

      # docker login через printf (не требует TTY)
      printf '%s' "\$TOKEN" | docker login --username iam --password-stdin cr.yandex

      docker pull $IMAGE

      # Secrets из Lockbox через REST
      PAYLOAD=\$(curl -s -H "Authorization: Bearer \$TOKEN" \\
        "https://payload.lockbox.api.cloud.yandex.net/lockbox/v1/secrets/$SECRET_ID/payload")
      JWT_KEY=\$(echo "\$PAYLOAD" | jq -r '.entries[] | select(.key=="JWT_SIGNING_KEY") | .textValue')
      DB_PWD=\$(echo "\$PAYLOAD" | jq -r '.entries[] | select(.key=="DB_PASSWORD") | .textValue')

      docker rm -f ultimatum-game 2>/dev/null || true

      docker run -d --name ultimatum-game --restart=always \\
        -p 8080:8080 \\
        -e SPRING_PROFILES_ACTIVE=prod \\
        -e "DB_URL=$DB_URL" \\
        -e "DB_USER=$PG_USER" \\
        -e "DB_PASSWORD=\$DB_PWD" \\
        -e "JWT_SIGNING_KEY=\$JWT_KEY" \\
        -e "APP_CORS_ORIGINS=$CORS_ORIGINS" \\
        $IMAGE

runcmd:
  - systemctl enable --now docker
  # Caddy repo + install (noninteractive, чтобы dpkg не спрашивал про Caddyfile)
  - curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  - curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
  - apt-get update
  - DEBIAN_FRONTEND=noninteractive apt-get install -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold caddy
  # Caddyfile — пишем ПОСЛЕ install (иначе dpkg спрашивает про конфликт)
  - printf '%s {\\n  reverse_proxy :8080\\n}\\n' '$DOMAIN' > /etc/caddy/Caddyfile
  - systemctl restart caddy
  # Приложение
  - /opt/app/run.sh
CLOUDINIT
echo "wrote $CLOUD_INIT ($(wc -l < "$CLOUD_INIT") lines)"

# ---- Step 7: Create VM -----------------------------------------------------
log "Step 7: Create VM"
if yc compute instance get "$VM_NAME" >/dev/null 2>&1; then
  warn "VM $VM_NAME уже существует. Удалить и пересоздать?"
  read -p "Enter — удалить+создать, Ctrl-C — отменить: "
  yc compute instance delete --name "$VM_NAME" --async 2>&1 | tail -3
  # Ждём удаления
  while yc compute instance get "$VM_NAME" >/dev/null 2>&1; do sleep 3; done
fi

yc compute instance create \
  --name "$VM_NAME" \
  --hostname "$VM_NAME" \
  --zone "$ZONE" \
  --platform standard-v3 \
  --cores 2 --core-fraction 20 --memory 2 \
  --create-boot-disk "image-id=$UBUNTU_IMAGE,size=20,type=network-hdd" \
  --network-interface "subnet-id=$SUBNET_ID,nat-ip-version=ipv4,nat-address=$PUBLIC_IP" \
  --service-account-id "$SA_ID" \
  --metadata-from-file "user-data=$CLOUD_INIT" \
  --metadata "serial-port-enable=1"

VM_ID=$(yc compute instance get "$VM_NAME" --format json | jq -r .id)

# ---- Done ------------------------------------------------------------------
cat <<EOF

==========================================================================
✓ VM создана. cloud-init выполняется (~2-3 мин на apt install docker+caddy).

VM ID:       $VM_ID
Public IP:   $PUBLIC_IP
HTTPS URL:   https://$DOMAIN/api/v1
Domain:      $DOMAIN (nip.io — auto DNS для этого IP)

Как проверить готовность:
  # cloud-init log через serial-console
  yc compute instance get-serial-port-output --name $VM_NAME | tail -50

  # health-check (первые 3-5 мин после старта — 502/timeout ок)
  curl -sk https://$DOMAIN/api/v1/actuator/health

  # SSH (если добавишь --ssh-key при создании):
  ssh ubuntu@$PUBLIC_IP

Ждать пока cloud-init поднимет: docker.io + caddy install → docker pull + run → caddy auto-cert LE.
Первый health может занять 3-5 минут после yc compute instance create.

Frontend env-vars:
  VITE_API_BASE_URL=https://$DOMAIN/api/v1
  VITE_WS_URL=wss://$DOMAIN/api/v1/ws

==========================================================================
EOF
