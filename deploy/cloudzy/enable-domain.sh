#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'Run domain setup as root.' >&2
  exit 1
fi

ENV_FILE=/etc/yimo/bootstrap.env
if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1091
  . "$ENV_FILE"
fi
YIMO_PUBLIC_HOST="${YIMO_PUBLIC_HOST:-graphwar.yimo-official.org}"
if [[ "$YIMO_PUBLIC_HOST" != 'graphwar.yimo-official.org' ]]; then
  echo 'YIMO_PUBLIC_HOST must be graphwar.yimo-official.org.' >&2
  exit 1
fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends certbot
install -d -m 0755 /var/www/yimo-acme/.well-known/acme-challenge
nginx -t
systemctl reload nginx.service

certbot certonly \
  --webroot --webroot-path /var/www/yimo-acme \
  --domain "$YIMO_PUBLIC_HOST" \
  --non-interactive --agree-tos --register-unsafely-without-email \
  --keep-until-expiring

certificate_dir="/etc/letsencrypt/live/$YIMO_PUBLIC_HOST"
[[ -s "$certificate_dir/fullchain.pem" && -s "$certificate_dir/privkey.pem" ]] || {
  echo 'Let’s Encrypt did not produce the expected certificate.' >&2
  exit 1
}
install -d -m 0755 /etc/letsencrypt/renewal-hooks/deploy
printf '#!/bin/sh\nexec systemctl reload nginx.service\n' \
  > /etc/letsencrypt/renewal-hooks/deploy/yimo-nginx-reload
chmod 755 /etc/letsencrypt/renewal-hooks/deploy/yimo-nginx-reload
install -m 0644 /etc/yimo/nginx-yimo-domain.conf /etc/nginx/sites-available/yimo
nginx -t
ufw allow 443/tcp
systemctl reload nginx.service
systemctl enable --now certbot.timer

host="$YIMO_PUBLIC_HOST"
resolve="$host:443:127.0.0.1"
public_status="$(curl --silent --show-error --retry 10 --retry-connrefused --retry-delay 1 \
  --max-time 10 --output /dev/null \
  --write-out '%{http_code}' --resolve "$resolve" "https://$host/healthz")"
[[ "$public_status" == '200' ]] || {
  echo "Expected public HTTPS health HTTP 200, got $public_status." >&2
  exit 1
}

echo "Public HTTPS is active for $host. Organizer actions still require the organizer password."
