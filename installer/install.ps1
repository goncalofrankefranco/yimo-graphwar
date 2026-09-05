param(
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'

$target = Join-Path $env:LOCALAPPDATA 'YIMO Graphwar'
$archive = Join-Path $PSScriptRoot 'payload.zip'
$versionFile = Join-Path $PSScriptRoot 'payload.version'
$startMenu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\YIMO Graphwar'

if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
    throw "Installer payload is missing: $archive"
}

$version = if (Test-Path -LiteralPath $versionFile -PathType Leaf) {
    (Get-Content -Raw -LiteralPath $versionFile).Trim()
} else {
    ''
}
$installedVersionFile = Join-Path $target '.yimo-installed-version'
$alreadyInstalled = $version.Length -gt 0 -and
    (Test-Path -LiteralPath $installedVersionFile -PathType Leaf) -and
    ((Get-Content -Raw -LiteralPath $installedVersionFile).Trim() -eq $version) -and
    (Test-Path -LiteralPath (Join-Path $target 'YIMO-Graphwar.exe') -PathType Leaf)

New-Item -ItemType Directory -Force -Path $target | Out-Null
if (-not $alreadyInstalled) {
    Expand-Archive -LiteralPath $archive -DestinationPath $target -Force
    if ($version.Length -gt 0) {
        Set-Content -LiteralPath $installedVersionFile -Value $version -Encoding ASCII
    }
} else {
    Write-Host "YIMO Graphwar $version is already installed; reusing the existing files."
}

$shell = New-Object -ComObject WScript.Shell
New-Item -ItemType Directory -Force -Path $startMenu | Out-Null
$shortcutPath = Join-Path $startMenu 'YIMO Graphwar.lnk'
if (Test-Path -LiteralPath $shortcutPath) {
    Remove-Item -LiteralPath $shortcutPath -Force
}
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = Join-Path $target 'YIMO-Graphwar.exe'
$shortcut.WorkingDirectory = $target
$shortcut.IconLocation = (Join-Path $target 'YIMO.ico') + ',0'
$shortcut.Save()

Write-Host "YIMO Graphwar installed to $target"
if (-not $NoLaunch) {
    Start-Process -FilePath (Join-Path $target 'YIMO-Graphwar.exe') -WorkingDirectory $target
}
