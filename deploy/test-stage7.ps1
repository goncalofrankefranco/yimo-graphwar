$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Assert-True([bool] $condition, [string] $message) {
    if (-not $condition) { throw $message }
}

$required = @(
    'deploy/README.md',
    'deploy/build-stage7-release.ps1',
    'deploy/cloudzy/bootstrap-vps.sh',
    'deploy/cloudzy/first-boot.sh',
    'deploy/cloudzy/yimo-first-boot.service',
    'deploy/cloudzy/yimo-global.service',
    'deploy/cloudzy/yimo-public-rooms.service',
    'deploy/cloudzy/yimo-tournament.service',
    'deploy/cloudzy/nginx-yimo.conf',
    'deploy/cloudzy/install-release.sh',
    'deploy/cloudzy/setup-yimo-vps.sh',
    'deploy/cloudzy/prepare-snapshot.sh',
    'deploy/cloudzy/cloud-init.yaml'
)

foreach ($relative in $required) {
    Assert-True (Test-Path (Join-Path $root $relative)) "Missing Stage 7 file: $relative"
}

$cloudInit = Get-Content (Join-Path $root 'deploy/cloudzy/cloud-init.yaml') -Raw
$bootstrap = Get-Content (Join-Path $root 'deploy/cloudzy/bootstrap-vps.sh') -Raw
$firstBoot = Get-Content (Join-Path $root 'deploy/cloudzy/first-boot.sh') -Raw
$snapshot = Get-Content (Join-Path $root 'deploy/cloudzy/prepare-snapshot.sh') -Raw
$globalService = Get-Content (Join-Path $root 'deploy/cloudzy/yimo-global.service') -Raw
$tournamentService = Get-Content (Join-Path $root 'deploy/cloudzy/yimo-tournament.service') -Raw
$setup = Get-Content (Join-Path $root 'deploy/cloudzy/setup-yimo-vps.sh') -Raw

Assert-True ($cloudInit -match 'YIMO_REPO_URL') 'Cloud-init must expose the repository URL.'
Assert-True ($bootstrap -match 'YIMO_JAVA8_URL' -and $bootstrap -match 'setup_24\.x') 'Bootstrap must install Java 8 and Node 24.'
Assert-True ($firstBoot -match 'YIMO_PUBLIC_IP' -and $firstBoot -match 'YIMO_ROOM_HMAC_SECRET') 'First boot must configure the IP and generate runtime secrets.'
Assert-True ($snapshot -match 'tournament\.sqlite' -and $snapshot -match 'tournament\.env') 'Snapshot preparation must remove runtime data and secrets.'
Assert-True ($bootstrap -match '23762' -and $bootstrap -match '30000:30049') 'Firewall must expose only the documented YIMO ports.'
Assert-True ($bootstrap -notmatch 'YIMO_ADMIN_TOKEN=.*[A-Za-z0-9]{20,}' -and $bootstrap -notmatch 'YIMO_ROOM_HMAC_SECRET=.*[A-Za-z0-9]{20,}') 'Bootstrap must not contain real secrets.'
Assert-True ($globalService -match '-Xmx128m' -and $globalService -match 'UseSerialGC') 'Global Java service needs a bounded low-memory profile.'
Assert-True ($tournamentService -match 'max-old-space-size=160') 'Tournament service needs a bounded Node memory profile.'
Assert-True ($bootstrap -match 'YIMO_SWAP_SIZE') 'Bootstrap must provision the documented emergency swap file.'
Assert-True ($setup -match 'YIMO_REPO_REF' -and $setup -match 'YIMO_RELEASE_URL') 'Recovery setup must pin source and release inputs.'
Assert-True ($setup -match 'YIMO_RELEASE_SHA256' -and $setup -match 'sha256sum') 'Recovery setup must support release verification.'
Assert-True ($setup -notmatch '(?i)(YIMO_ADMIN_TOKEN|YIMO_ROOM_HMAC_SECRET)=.{20,}') 'Recovery setup must not contain runtime secrets.'

Write-Output 'stage7-deployment-config-check: PASS'
