#!/usr/bin/env bash
set -Eeuo pipefail
umask 027

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'Run release installation as root.' >&2
  exit 1
fi

if [[ -f /etc/yimo/bootstrap.env ]]; then
  # shellcheck disable=SC1091
  . /etc/yimo/bootstrap.env
fi

release_dir=''
while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-dir)
      [[ $# -ge 2 ]] || { echo '--release-dir needs a path.' >&2; exit 2; }
      release_dir="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$release_dir" || ! -d "$release_dir" ]]; then
  echo 'Pass --release-dir with the uploaded Stage 7 release directory.' >&2
  exit 2
fi
for required in YIMO-Graphwar-2.0.0.jar globalServer.jar roomServer.jar rsc tournament/src/main.ts; do
  if [[ ! -e "$release_dir/$required" ]]; then
    echo "Release is missing $required." >&2
    exit 1
  fi
done

install_root=/opt/yimo-graphwar
new_dir="$install_root/current.new"
install -d -o yimo -g yimo "$install_root"
rm -rf "$new_dir"
install -d -o yimo -g yimo "$new_dir"
cp -a "$release_dir/." "$new_dir/"
chown -R yimo:yimo "$new_dir"

if [[ -d "$install_root/current" ]]; then
  backup="$install_root/current.previous.$(date +%Y%m%d%H%M%S)"
  mv "$install_root/current" "$backup"
fi
mv "$new_dir" "$install_root/current"

/usr/local/sbin/yimo-first-boot.sh
systemctl daemon-reload
systemctl enable yimo-global.service yimo-tournament.service
systemctl restart yimo-global.service yimo-tournament.service
if [[ "${YIMO_ENABLE_PRACTICE_ROOMS:-0}" == '1' ]]; then
  systemctl enable yimo-public-rooms.service
  systemctl restart yimo-public-rooms.service
fi
nginx -t
systemctl reload nginx.service
echo 'YIMO release installed and services restarted.'
