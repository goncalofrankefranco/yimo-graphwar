#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'Run this bootstrap as root.' >&2
  exit 1
fi

ENV_FILE=/etc/yimo/bootstrap.env
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1091
  . "$ENV_FILE"
fi

YIMO_SSH_CIDR="${YIMO_SSH_CIDR:-}"
YIMO_JAVA8_URL="${YIMO_JAVA8_URL:-https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jre/hotspot/normal/eclipse}"
YIMO_JAVA8_SHA256="${YIMO_JAVA8_SHA256:-}"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl git nginx openssl tar ufw xz-utils

# NodeSource is used only to install the pinned Node 24 major line required by the
# tournament service. The service itself uses Node's built-in modules only.
node_major=''
if command -v node >/dev/null 2>&1; then
  node_major="$(node -p "process.versions.node.split('.')[0]")"
fi
if [[ "$node_major" != '24' ]]; then
  node_setup=/tmp/yimo-node-setup_24.x
  curl --fail --silent --show-error --location https://deb.nodesource.com/setup_24.x --output "$node_setup"
  bash "$node_setup"
  apt-get install -y --no-install-recommends nodejs
  rm -f "$node_setup"
fi

YIMO_ROOT=/opt/yimo-graphwar
JAVA_ROOT=/opt/yimo/java8
if [[ ! -x "$JAVA_ROOT/bin/java" ]]; then
  java_archive=/tmp/yimo-java8.tar.gz
  java_stage=/opt/yimo/java8.new
  curl --fail --silent --show-error --location "$YIMO_JAVA8_URL" --output "$java_archive"
  if [[ -n "$YIMO_JAVA8_SHA256" ]]; then
    echo "$YIMO_JAVA8_SHA256  $java_archive" | sha256sum --check --status -
  fi
  rm -rf "$java_stage"
  install -d "$java_stage"
  tar -xzf "$java_archive" --strip-components=1 -C "$java_stage"
  rm -rf "$JAVA_ROOT"
  mv "$java_stage" "$JAVA_ROOT"
  rm -f "$java_archive"
fi

if ! getent passwd yimo >/dev/null 2>&1; then
  useradd --system --home-dir "$YIMO_ROOT" --shell /usr/sbin/nologin yimo
fi
install -d -o yimo -g yimo "$YIMO_ROOT" "$YIMO_ROOT/releases" /var/lib/yimo /var/log/yimo /etc/yimo

if [[ ! -f "$ENV_FILE" ]]; then
  cat > "$ENV_FILE" <<'EOF'
# Local instance settings. Replace the SSH CIDR before exposing this host.
YIMO_PUBLIC_IP=
YIMO_SSH_CIDR=0.0.0.0/0
YIMO_ENABLE_PRACTICE_ROOMS=0
EOF
fi
chmod 600 "$ENV_FILE"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
install -m 0750 "$SCRIPT_DIR/first-boot.sh" /usr/local/sbin/yimo-first-boot.sh
install -m 0750 "$SCRIPT_DIR/prepare-snapshot.sh" /usr/local/sbin/yimo-prepare-snapshot.sh
install -m 0644 "$SCRIPT_DIR/yimo-first-boot.service" /etc/systemd/system/yimo-first-boot.service
install -m 0644 "$SCRIPT_DIR/yimo-global.service" /etc/systemd/system/yimo-global.service
install -m 0644 "$SCRIPT_DIR/yimo-public-rooms.service" /etc/systemd/system/yimo-public-rooms.service
install -m 0644 "$SCRIPT_DIR/yimo-tournament.service" /etc/systemd/system/yimo-tournament.service
install -m 0644 "$SCRIPT_DIR/nginx-yimo.conf" /etc/nginx/sites-available/yimo
rm -f /etc/nginx/sites-enabled/default
ln -sfn /etc/nginx/sites-available/yimo /etc/nginx/sites-enabled/yimo

nginx -t
systemctl daemon-reload
systemctl enable nginx.service yimo-first-boot.service
systemctl restart nginx.service

# Stage 7 is IP-only, so HTTP is intentionally temporary. Add HTTPS before
# using real participant codes or publishing the tournament endpoint.
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow 80/tcp
ufw allow 23762/tcp
ufw allow 30000:30049/tcp
if [[ -n "$YIMO_SSH_CIDR" && "$YIMO_SSH_CIDR" != '0.0.0.0/0' ]]; then
  ufw allow from "$YIMO_SSH_CIDR" to any port 22 proto tcp
else
  echo 'WARNING: SSH is open to the world until YIMO_SSH_CIDR is restricted.' >&2
  ufw allow 22/tcp
fi
ufw --force enable

/usr/local/sbin/yimo-first-boot.sh
echo 'YIMO bootstrap complete. Install a release with install-release.sh.'
