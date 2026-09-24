$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$setup = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\setup-yimo-vps.sh')
$bootstrap = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\bootstrap-vps.sh')
$firstBoot = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\first-boot.sh')
$recovery = Get-Content -Raw (Join-Path $root 'deploy\cloudzy\cloud-init-recovery.yaml')

if ($setup -match '153\.75\.82\.155|172\.86\.74\.172|YIMO_RELEASE_SHA256="[0-9A-Fa-f]{64}"') {
    throw 'Startup setup script contains a stale endpoint or stale release hash.'
}
foreach ($required in @('YIMO_PUBLIC_IP', 'YIMO_SSH_CIDR', 'YIMO_RELEASE_URL', 'YIMO_RELEASE_SHA256', 'build-linux-release.sh', 'healthz')) {
    if ($setup -notmatch [regex]::Escape($required)) { throw "setup script is missing $required" }
}
foreach ($required in @('YIMO_PUBLIC_IP', 'YIMO_SSH_CIDR', 'ufw', 'systemctl', 'nginx')) {
    if ($bootstrap -notmatch [regex]::Escape($required)) { throw "bootstrap script is missing $required" }
}
if ($firstBoot -notmatch 'YIMO_ADMIN_PASSWORD|YIMO_ADMIN_TOKEN') {
    throw 'first-boot must create the single organizer credential.'
}
if ($recovery -notmatch 'runcmd:' -or $recovery -notmatch 'YIMO_PUBLIC_IP') {
    throw 'Cloudzy recovery template is missing startup configuration.'
}
Write-Output 'Startup script smoke check: PASS'
