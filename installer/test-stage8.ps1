$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw "Stage 8 check failed: $message" }
}

function Assert-File([string]$path, [string]$message) {
    Assert-True (Test-Path -LiteralPath $path -PathType Leaf) $message
}

Assert-File (Join-Path $root 'NOTICE.md') 'NOTICE.md is required.'
Assert-File (Join-Path $root 'THIRD-PARTY-LICENSES.md') 'THIRD-PARTY-LICENSES.md is required.'
Assert-File (Join-Path $PSScriptRoot 'build-stage8-release.ps1') 'Stage 8 build script is required.'
Assert-File (Join-Path $PSScriptRoot 'test-stage8-install.ps1') 'Clean-install test is required.'
Assert-File (Join-Path $PSScriptRoot 'launch-yimo.cmd') 'YIMO launcher is required.'
Assert-File (Join-Path $PSScriptRoot 'YimoLauncher.cs') 'Clickable launcher source is required.'
Assert-File (Join-Path $PSScriptRoot 'YimoIconBuilder.cs') 'YIMO icon source is required.'
Assert-File (Join-Path $PSScriptRoot 'yimo-logo.png') 'Official YIMO logo asset is required.'
Assert-File (Join-Path $PSScriptRoot 'launch-practice-server.cmd') 'Practice-server launcher is required.'
Assert-File (Join-Path $PSScriptRoot 'launch-practice-client.cmd') 'Practice-client launcher is required.'

$notice = Get-Content -Raw (Join-Path $root 'NOTICE.md')
$licenses = Get-Content -Raw (Join-Path $root 'THIRD-PARTY-LICENSES.md')
$readme = Get-Content -Raw (Join-Path $PSScriptRoot 'README.md')
Assert-True ($notice -match 'YIMO Graphwar 2\.0\.0') 'Notice must identify the v2 release.'
Assert-True ($notice -match 'GPL-3\.0') 'Notice must identify GPL-3.0.'
Assert-True ($licenses -match 'OpenJDK') 'Third-party license audit must cover the bundled runtime.'
Assert-True ($readme -match 'YIMO-Graphwar-2\.0\.0-Setup\.exe') 'Installer README must document the v2 installer.'

$sourceFiles = @(
        (Join-Path $PSScriptRoot 'build-stage8-release.ps1'),
        (Join-Path $PSScriptRoot 'install.ps1'),
        (Join-Path $PSScriptRoot 'YimoLauncher.cs'),
        (Join-Path $PSScriptRoot 'YimoIconBuilder.cs'),
        (Join-Path $PSScriptRoot 'yimo-logo.png'),
        (Join-Path $PSScriptRoot 'launch-yimo.cmd'),
        (Join-Path $PSScriptRoot 'launch-practice-server.cmd'),
        (Join-Path $PSScriptRoot 'launch-practice-client.cmd')
)
foreach ($path in $sourceFiles) {
    $content = Get-Content -Raw $path
    Assert-True ($content -notmatch '(?i)(YIMO_ADMIN_TOKEN|ROOM_HMAC_SECRET|BEGIN (RSA|OPENSSH) PRIVATE KEY)') "Secret-like value found in $path."
}

$buildScript = Get-Content -Raw (Join-Path $PSScriptRoot 'build-stage8-release.ps1')
$installScript = Get-Content -Raw (Join-Path $PSScriptRoot 'install.ps1')
Assert-True ($buildScript -match 'YIMO-Graphwar\.exe') 'Build must package the clickable executable.'
Assert-True ($buildScript -match 'win32icon') 'Build must embed the YIMO icon in the executable.'
Assert-True ($installScript -match 'YIMO-Graphwar\.exe') 'Shortcut must target the clickable executable.'

Write-Output 'Stage 8 installer/source smoke checks passed.'
