#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'Run YIMO VPS setup as root.' >&2
  exit 1
fi
YIMO_REPO_URL="${YIMO_REPO_URL:-https://github.com/goncalofrankefranco/yimo-graphwar.git}"
YIMO_REPO_REF="${YIMO_REPO_REF:-7cce143}"
YIMO_PUBLIC_IP="${YIMO_PUBLIC_IP:-}"
YIMO_SSH_CIDR="${YIMO_SSH_CIDR:-auto}"
YIMO_RELEASE_URL="${YIMO_RELEASE_URL:-}"
YIMO_RELEASE_SHA256="${YIMO_RELEASE_SHA256:-}"
YIMO_ENABLE_PRACTICE_ROOMS="${YIMO_ENABLE_PRACTICE_ROOMS:-0}"
YIMO_SWAP_SIZE="${YIMO_SWAP_SIZE:-512M}"
export YIMO_PUBLIC_IP YIMO_SSH_CIDR YIMO_ENABLE_PRACTICE_ROOMS YIMO_SWAP_SIZE

log_file=/var/log/yimo-bootstrap.log
install -d -m 0750 /var/log
exec > >(tee -a "$log_file") 2>&1
trap 'echo "YIMO setup failed at line $LINENO. See $log_file." >&2' ERR

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl git unzip
source_dir=/opt/yimo-source
if [[ -d "$source_dir/.git" ]]; then
  git -C "$source_dir" remote set-url origin "$YIMO_REPO_URL"
  git -C "$source_dir" fetch --depth 1 origin "$YIMO_REPO_REF"
  git -C "$source_dir" checkout --detach FETCH_HEAD
else
  rm -rf "$source_dir"
  git clone --no-checkout "$YIMO_REPO_URL" "$source_dir"
  git -C "$source_dir" fetch --depth 1 origin "$YIMO_REPO_REF"
  git -C "$source_dir" checkout --detach FETCH_HEAD
fi

install -d -m 0700 /etc/yimo
cat > /etc/yimo/bootstrap.env <<EOF
YIMO_PUBLIC_IP=$YIMO_PUBLIC_IP
YIMO_SSH_CIDR=$YIMO_SSH_CIDR
YIMO_ENABLE_PRACTICE_ROOMS=$YIMO_ENABLE_PRACTICE_ROOMS
YIMO_SWAP_SIZE=$YIMO_SWAP_SIZE
EOF
chmod 600 /etc/yimo/bootstrap.env
bash "$source_dir/deploy/cloudzy/bootstrap-vps.sh"

release_stage="$(mktemp -d /tmp/yimo-release.XXXXXX)"
cleanup() { rm -rf "$release_stage"; }
trap cleanup EXIT
if [[ -n "$YIMO_RELEASE_URL" ]]; then
  [[ -n "$YIMO_RELEASE_SHA256" ]] || { echo 'YIMO_RELEASE_SHA256 is required with YIMO_RELEASE_URL.' >&2; exit 1; }
  archive="$(mktemp /tmp/yimo-release.XXXXXX.zip)"
  curl --fail --silent --show-error --location "$YIMO_RELEASE_URL" --output "$archive"
  echo "$YIMO_RELEASE_SHA256  $archive" | sha256sum --check --status
  unzip -q "$archive" -d "$release_stage"
  rm -f "$archive"
  rm -rf "$release_stage/runtime"
  rm -f "$release_stage/YIMO-Graphwar.exe" "$release_stage/launch-yimo.cmd"
  rm -f "$release_stage/launch-practice-server.cmd" "$release_stage/launch-practice-client.cmd"
else
  bash "$source_dir/deploy/cloudzy/build-linux-release.sh" --output-dir "$release_stage"
fi

bash "$source_dir/deploy/cloudzy/install-release.sh" --release-dir "$release_stage"
for unit in yimo-global.service yimo-tournament.service nginx.service; do
  systemctl is-active --quiet "$unit"
done
curl --fail --retry 10 --retry-delay 1 --silent http://127.0.0.1/healthz
ss -ltn | grep -Eq '0\.0\.0\.0:80|\*:80'
ss -ltn | grep -Eq '\*:23762|0\.0\.0\.0:23762'
echo 'YIMO VPS setup complete and health-checked.'
