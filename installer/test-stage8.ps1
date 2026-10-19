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
Assert-True ($notice -match 'YIMO Graphwar 3\.2\.0') 'Notice must identify the v3.2 product release.'
Assert-True ($notice -match 'GPL-3\.0') 'Notice must identify GPL-3.0.'
Assert-True ($licenses -match 'OpenJDK') 'Third-party license audit must cover the bundled runtime.'
Assert-True ($readme -match 'YIMO-Graphwar-3\.2\.0-Setup\.exe') 'Installer README must document the v3.2 installer.'

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
$stage7BuildScript = Get-Content -Raw (Join-Path $root 'deploy\build-stage7-release.ps1')
$installScript = Get-Content -Raw (Join-Path $PSScriptRoot 'install.ps1')
$launcherSource = Get-Content -Raw (Join-Path $PSScriptRoot 'YimoLauncher.cs')
Assert-True ($buildScript -match 'YIMO-Graphwar-3\.2\.0-Setup\.exe') 'Build must name the v3.2.0 installer.'
Assert-True ($buildScript -match 'build\.id=YIMO-Graphwar-2\.2\.0' -and $buildScript -match 'protocol\.version=2') 'Installer must retain the v2.2 wire build and protocol.'
Assert-True ($stage7BuildScript -match 'YIMO-Graphwar-3\.2\.0\.jar') 'Stage 7 must package the v3.2.0 client JAR.'
Assert-True ($stage7BuildScript -match 'YIMO-Graphwar-2\.2\.0' -and $stage7BuildScript -match 'Protocol version: 2') 'Stage 7 must preserve 2.2.0 server compatibility.'
Assert-True ($stage7BuildScript -match 'javacVersion' -and $stage7BuildScript -match '--release') 'Modern JDK builds must target the Java 8 API.'
Assert-True ($launcherSource -match 'YIMO-Graphwar-3\.2\.0\.jar') 'Clickable launcher must start the v3.2.0 client JAR.'
Assert-True ($buildScript -match 'SourceRevision' -and $stage7BuildScript -match 'SourceRevision') 'Clean source exports must preserve the reviewed commit in release manifests.'
Assert-True ($buildScript -match 'Join-Path \$work .*Portable\.zip') 'Portable staging must stay in the temporary build directory.'
Assert-True ($buildScript -match 'YIMO-Graphwar\.exe') 'Build must package the clickable executable.'
Assert-True ($buildScript -match 'win32icon') 'Build must embed the YIMO icon in the executable.'
Assert-True ($buildScript -match 'status --porcelain' -and $buildScript -match 'Refusing to build from a dirty source tree') 'Stage 8 must reject a dirty Git checkout even with an explicit source revision.'
Assert-True ($stage7BuildScript -match 'status --porcelain' -and $stage7BuildScript -match 'Refusing to build from a dirty source tree') 'Stage 7 must reject a dirty Git checkout even with an explicit source revision.'
Assert-True ($buildScript -match 'SourceRevision is required when building outside a Git worktree') 'Clean source exports must identify the reviewed revision.'
Assert-True ($buildScript -match 'packageFingerprint' -and $buildScript -match 'Package fingerprint:') 'Installed build identity must be derived from the actual portable package.'
Assert-True ($installScript -match 'YIMO-Graphwar\.exe') 'Shortcut must target the clickable executable.'

Write-Output 'Stage 8 installer/source smoke checks passed.'
