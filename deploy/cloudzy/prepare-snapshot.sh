#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'Run snapshot preparation as root.' >&2
  exit 1
fi

systemctl stop yimo-global.service yimo-tournament.service yimo-public-rooms.service >/dev/null 2>&1 || true
# Keep the lobby and tournament units enabled so a restored snapshot starts
# them after first-boot reconfigures the new IP. Practice rooms stay opt-in.
systemctl disable yimo-public-rooms.service >/dev/null 2>&1 || true

# Runtime state and secrets are intentionally excluded from the golden image.
rm -f /var/lib/yimo/tournament.sqlite /var/lib/yimo/tournament.sqlite-shm /var/lib/yimo/tournament.sqlite-wal
rm -f /etc/yimo/tournament.env /root/yimo-admin-token.txt
rm -f /etc/yimo/yimo.properties

bootstrap_env=/etc/yimo/bootstrap.env
if [[ -f "$bootstrap_env" ]]; then
  if grep -q '^YIMO_PUBLIC_IP=' "$bootstrap_env"; then
    sed -i 's/^YIMO_PUBLIC_IP=.*/YIMO_PUBLIC_IP=/' "$bootstrap_env"
  else
    printf '\nYIMO_PUBLIC_IP=\n' >> "$bootstrap_env"
  fi
fi

systemctl enable yimo-first-boot.service
systemctl enable yimo-global.service yimo-tournament.service
sync
echo 'Snapshot preparation complete. The instance is stopped and safe to capture.'
echo 'On restore, yimo-first-boot.service detects the new IP and creates fresh secrets.'
