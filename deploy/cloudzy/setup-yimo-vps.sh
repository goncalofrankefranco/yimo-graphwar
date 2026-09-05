#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'Run YIMO VPS setup as root.' >&2
  exit 1
fi

: "${YIMO_SSH_CIDR:?Set YIMO_SSH_CIDR to the organizer IP in CIDR form, such as 203.0.113.42/32.}"

YIMO_REPO_URL="${YIMO_REPO_URL:-https://github.com/goncalofrankefranco/yimo-graphwar.git}"
YIMO_REPO_REF="${YIMO_REPO_REF:-v2.0.0}"
YIMO_RELEASE_URL="${YIMO_RELEASE_URL:-https://github.com/goncalofrankefranco/yimo-graphwar/releases/download/v2.0.0/YIMO-Graphwar-2.0.0-Portable.zip}"
YIMO_RELEASE_SHA256="${YIMO_RELEASE_SHA256:-087638A79D946C419903749689A8BC9D4DB75C1DB1733A530C57365E4CC93064}"
YIMO_PUBLIC_IP="${YIMO_PUBLIC_IP:-}"
YIMO_ENABLE_PRACTICE_ROOMS="${YIMO_ENABLE_PRACTICE_ROOMS:-0}"
YIMO_SWAP_SIZE="${YIMO_SWAP_SIZE:-512M}"

if [[ "$YIMO_REPO_REF" == 'REPLACE_WITH_APPROVED_COMMIT' ]]; then
  echo 'YIMO_REPO_REF must name an approved tag or commit.' >&2
  exit 1
fi

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
bootstrap_env=/etc/yimo/bootstrap.env
if [[ ! -f "$bootstrap_env" ]]; then
  cat > "$bootstrap_env" <<EOF
# Non-secret settings for this YIMO instance.
YIMO_PUBLIC_IP=$YIMO_PUBLIC_IP
YIMO_SSH_CIDR=$YIMO_SSH_CIDR
YIMO_ENABLE_PRACTICE_ROOMS=$YIMO_ENABLE_PRACTICE_ROOMS
YIMO_SWAP_SIZE=$YIMO_SWAP_SIZE
EOF
  chmod 600 "$bootstrap_env"
else
  echo "Keeping existing $bootstrap_env; edit it before rerunning if the IP or SSH range changed."
fi

bash "$source_dir/deploy/cloudzy/bootstrap-vps.sh"

release_archive="$(mktemp /tmp/yimo-release.XXXXXX.zip)"
release_stage="$(mktemp -d /tmp/yimo-release.XXXXXX)"
cleanup() {
  rm -f "$release_archive"
  rm -rf "$release_stage"
}
trap cleanup EXIT

curl --fail --silent --show-error --location "$YIMO_RELEASE_URL" --output "$release_archive"
if [[ -n "$YIMO_RELEASE_SHA256" ]]; then
  echo "$YIMO_RELEASE_SHA256  $release_archive" | sha256sum --check --status -
fi
unzip -q "$release_archive" -d "$release_stage"

# The portable package contains the Windows client runtime too. The Linux
# server needs only the Java server JARs, resources, and tournament service.
rm -rf "$release_stage/runtime"
rm -f "$release_stage/YIMO-Graphwar.exe" "$release_stage/launch-yimo.cmd"
rm -f "$release_stage/launch-practice-server.cmd" "$release_stage/launch-practice-client.cmd"

bash "$source_dir/deploy/cloudzy/install-release.sh" --release-dir "$release_stage"
systemctl is-active --quiet yimo-global.service
systemctl is-active --quiet yimo-tournament.service
echo 'YIMO VPS setup complete. The lobby and tournament services are active.'
