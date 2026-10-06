#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo 'Run the Cloudflare IP updater as root.' >&2
  exit 1
fi

if ! v4="$(curl --fail --silent --show-error --location --retry 3 --retry-delay 2 https://www.cloudflare.com/ips-v4)" \
  || ! v6="$(curl --fail --silent --show-error --location --retry 3 --retry-delay 2 https://www.cloudflare.com/ips-v6)"; then
  if [[ -s /etc/nginx/conf.d/yimo-cloudflare-realip.conf ]]; then
    echo 'Cloudflare IP feeds are unavailable; keeping the last verified proxy ranges.' >&2
    exit 0
  fi
  echo 'Cloudflare IP feeds are unavailable and no verified proxy ranges exist.' >&2
  exit 1
fi
if [[ -z "$v4" || -z "$v6" ]]; then
  if [[ -s /etc/nginx/conf.d/yimo-cloudflare-realip.conf ]]; then
    echo 'Cloudflare IP feeds were empty; keeping the last verified proxy ranges.' >&2
    exit 0
  fi
  echo 'Cloudflare IP feeds must contain both IPv4 and IPv6 ranges.' >&2
  exit 1
fi
tmp="$(mktemp /etc/nginx/conf.d/yimo-cloudflare-realip.XXXXXX)"
trap 'rm -f "$tmp"' EXIT

count=0
{
  printf '%s\n' '# Generated from Cloudflare official IP range feeds.'
  for range in $v4 $v6; do
    if [[ ! "$range" =~ ^[0-9A-Fa-f:.]+/[0-9]+$ ]]; then
      echo 'Cloudflare returned an invalid IP range; keeping the previous trusted-proxy config.' >&2
      exit 1
    fi
    printf 'set_real_ip_from %s;\n' "$range"
    count=$((count + 1))
  done
  [[ "$count" -gt 0 ]] || { echo 'Cloudflare IP range feeds were empty.' >&2; exit 1; }
  printf '%s\n' 'real_ip_header CF-Connecting-IP;' 'real_ip_recursive on;'
} > "$tmp"

chmod 644 "$tmp"
mv -f "$tmp" /etc/nginx/conf.d/yimo-cloudflare-realip.conf
trap - EXIT
nginx -t
if systemctl is-active --quiet nginx.service; then systemctl reload nginx.service; fi
