$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$setup = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\setup-yimo-vps.sh')
$bootstrap = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\bootstrap-vps.sh')
$firstBoot = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\first-boot.sh')
$snapshot = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\prepare-snapshot.sh')
$firstBootUnit = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\yimo-first-boot.service')
$recovery = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\cloud-init-recovery.yaml')
$bootstrapTemplate = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\cloud-init.yaml')
$domainSetupPath = Join-Path $root 'deploy\cloudzy\enable-domain.sh'
$nginxDomainPath = Join-Path $root 'deploy\cloudzy\nginx-yimo-domain.conf'
if (-not (Test-Path $domainSetupPath) -or -not (Test-Path $nginxDomainPath)) {
    throw 'The domain setup and TLS configuration are missing.'
}
$nginxPending = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\nginx-yimo.conf')
$domainSetup = Get-Content -Raw $domainSetupPath
$nginxDomain = Get-Content -Raw $nginxDomainPath

if ($setup -match '153\.75\.82\.155|172\.86\.74\.172|YIMO_RELEASE_SHA256="[0-9A-Fa-f]{64}"') {
    throw 'Startup setup script contains a stale endpoint or stale release hash.'
}
foreach ($required in @('YIMO_PUBLIC_IP', 'YIMO_SSH_CIDR', 'YIMO_RELEASE_URL', 'YIMO_RELEASE_SHA256', 'build-linux-release.sh', 'healthz')) {
    if ($setup -notmatch [regex]::Escape($required)) { throw "setup script is missing $required" }
}
foreach ($required in @('YIMO_PUBLIC_HOST', '127.0.0.1:8080/healthz')) {
    if ($setup -notmatch [regex]::Escape($required)) { throw "setup script is missing $required" }
}
if ($setup -notmatch 'curl --fail --retry 10 --retry-connrefused.*127\.0\.0\.1:8080/healthz') {
    throw 'setup health check must retry while the local tournament service is starting.'
}
if ($setup -notmatch 'YIMO_REPO_REF="\$\{YIMO_REPO_REF:-[0-9a-f]{40}\}"') {
    throw 'setup script must pin its default source checkout to a full commit hash.'
}
if ($recovery -notmatch 'YIMO_REPO_REF=[0-9a-f]{40}' -or $bootstrapTemplate -notmatch 'YIMO_REPO_REF=[0-9a-f]{40}') {
    throw 'Cloudzy templates must pin source checkout to a full commit hash.'
}
foreach ($required in @('YIMO_PUBLIC_IP', 'YIMO_SSH_CIDR', 'ufw', 'systemctl', 'nginx')) {
    if ($bootstrap -notmatch [regex]::Escape($required)) { throw "bootstrap script is missing $required" }
}
foreach ($required in @('YIMO_PUBLIC_HOST', 'ufw allow 443/tcp', 'fullchain.pem', '/etc/yimo/nginx-yimo-domain.conf')) {
    if ($bootstrap -notmatch [regex]::Escape($required)) { throw "bootstrap script is missing $required" }
}
if ($bootstrap -match 'apache2-utils|yimo\.htpasswd|yimo-web-password') {
    throw 'Bootstrap must not install or configure a public website password.'
}
if ($bootstrap -notmatch "YIMO_SSH_CIDR.*auto" -or $bootstrap -notmatch "SSH is open to the world") {
    throw 'bootstrap must handle auto SSH mode without guessing the organizer IP.'
}
if ($bootstrap -match 'detected_ssh_ip|api\.ipify\.org') {
    throw 'bootstrap must not mistake the VPS public IP for the organizer IP.'
}
if ($firstBoot -notmatch 'YIMO_ADMIN_PASSWORD|YIMO_ADMIN_TOKEN') {
    throw 'first-boot must create the single organizer credential.'
}
if ($firstBoot -notmatch 'YIMO_PUBLIC_HOST' -or $firstBoot -notmatch 'https://\$public_host' -or
        $firstBoot -notmatch 'global\.host=graphwar-server\.yimo-official\.org') {
    throw 'first-boot must configure the fixed YIMO game and HTTPS tournament domains.'
}
if ($firstBoot -match 'openssl rand -hex 24|htpasswd -Bbc') {
    throw 'first-boot must not generate or install a public website password.'
}
if ($snapshot -notmatch 'yimo-web-password.txt' -or $snapshot -notmatch 'yimo.htpasswd') {
    throw 'snapshot preparation must remove legacy website password files.'
}
if ($firstBootUnit -notmatch 'Before=.*yimo-global.service') {
    throw 'first-boot must configure the server before YIMO services start after a restore.'
}
foreach ($required in @('return 444', 'well-known/acme-challenge')) {
    if ($nginxPending -notmatch [regex]::Escape($required)) { throw "pending Nginx config is missing $required" }
}
foreach ($required in @('listen 443 ssl', 'proxy_pass http://127.0.0.1:8080', 'return 301 https://')) {
    if ($nginxDomain -notmatch [regex]::Escape($required)) { throw "Nginx config is missing $required" }
}
if ($nginxDomain -match 'auth_basic|yimo\.htpasswd') {
    throw 'The public HTTPS site must not require a BasicAuth password.'
}
foreach ($required in @('certbot certonly', 'nginx -t', 'systemctl reload nginx', 'renewal-hooks/deploy', '200')) {
    if ($domainSetup -notmatch [regex]::Escape($required)) { throw "domain setup script is missing $required" }
}
if ($domainSetup -match '401|yimo-web-password|yimo\.htpasswd|curl_config') {
    throw 'Domain setup must verify the public site without a website password.'
}
if ($recovery -notmatch 'runcmd:' -or $recovery -notmatch 'YIMO_PUBLIC_IP') {
    throw 'Cloudzy recovery template is missing startup configuration.'
}
Write-Output 'Startup script smoke check: PASS'
