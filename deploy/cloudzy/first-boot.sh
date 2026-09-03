#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'Run first-boot as root.' >&2
  exit 1
fi

YIMO_DIR=/opt/yimo-graphwar/current
CONFIG_DIR=/etc/yimo
BOOTSTRAP_ENV="$CONFIG_DIR/bootstrap.env"
if [[ -f "$BOOTSTRAP_ENV" ]]; then
  # shellcheck disable=SC1091
  . "$BOOTSTRAP_ENV"
fi

public_ip="${YIMO_PUBLIC_IP:-}"
if [[ -z "$public_ip" ]]; then
  public_ip="$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for (i = 1; i <= NF; i++) if ($i == "src") { print $(i + 1); exit }}')"
fi
if [[ ! "$public_ip" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
  echo 'Could not determine a public IPv4 address. Set YIMO_PUBLIC_IP in /etc/yimo/bootstrap.env.' >&2
  exit 1
fi

install -d /etc/yimo /var/lib/yimo /var/log/yimo

properties_tmp="$(mktemp /etc/yimo/yimo.properties.XXXXXX)"
cat > "$properties_tmp" <<EOF
global.host=$public_ip
global.port=23762
room.port.start=30000
room.port.end=30049
tournament.api.baseUrl=http://$public_ip
build.id=YIMO-Graphwar-2.0.0
protocol.version=2
EOF
chmod 644 "$properties_tmp"
mv -f "$properties_tmp" "$CONFIG_DIR/yimo.properties"

tournament_env="$CONFIG_DIR/tournament.env"
if [[ ! -f "$tournament_env" ]]; then
  admin_token="$(openssl rand -hex 32)"
  room_secret="$(openssl rand -hex 32)"
  env_tmp="$(mktemp /etc/yimo/tournament.env.XXXXXX)"
  cat > "$env_tmp" <<EOF
YIMO_ADMIN_TOKEN=$admin_token
YIMO_ROOM_HMAC_SECRET=$room_secret
YIMO_TOURNAMENT_DB=/var/lib/yimo/tournament.sqlite
YIMO_BUILD_ID=YIMO-Graphwar-2.0.0
YIMO_PROTOCOL_VERSION=2
HOST=127.0.0.1
PORT=8080
EOF
  chmod 600 "$env_tmp"
  mv -f "$env_tmp" "$tournament_env"
  printf '%s\n' "$admin_token" > /root/yimo-admin-token.txt
  chmod 600 /root/yimo-admin-token.txt
elif [[ ! -f /root/yimo-admin-token.txt ]]; then
  admin_token="$(sed -n 's/^YIMO_ADMIN_TOKEN=//p' "$tournament_env" | head -n 1)"
  if [[ -z "$admin_token" ]]; then
    echo 'Existing tournament.env has no YIMO_ADMIN_TOKEN.' >&2
    exit 1
  fi
  printf '%s\n' "$admin_token" > /root/yimo-admin-token.txt
  chmod 600 /root/yimo-admin-token.txt
fi

chown -R yimo:yimo /var/lib/yimo /var/log/yimo
systemctl daemon-reload
# This unit only writes instance configuration. Enabled services declare
# After=yimo-first-boot.service and start after this one-shot completes.
echo "YIMO first-boot configuration complete for $public_ip."
