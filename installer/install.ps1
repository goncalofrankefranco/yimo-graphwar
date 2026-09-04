param(
    [switch]$NoLaunch
)

$ErrorActionPreference = 'Stop'

$target = Join-Path $env:LOCALAPPDATA 'YIMO Graphwar'
$archive = Join-Path $PSScriptRoot 'payload.zip'
$startMenu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\YIMO Graphwar'

if (-not (Test-Path -LiteralPath $archive -PathType Leaf)) {
    throw "Installer payload is missing: $archive"
}

New-Item -ItemType Directory -Force -Path $target | Out-Null
Expand-Archive -LiteralPath $archive -DestinationPath $target -Force

$shell = New-Object -ComObject WScript.Shell
New-Item -ItemType Directory -Force -Path $startMenu | Out-Null
$shortcut = $shell.CreateShortcut((Join-Path $startMenu 'YIMO Graphwar.lnk'))
$shortcut.TargetPath = Join-Path $target 'YIMO-Graphwar.exe'
$shortcut.WorkingDirectory = $target
$shortcut.IconLocation = Join-Path $target 'YIMO-Graphwar.exe'
$shortcut.Save()

Write-Host "YIMO Graphwar installed to $target"
if (-not $NoLaunch) {
    Start-Process -FilePath (Join-Path $target 'YIMO-Graphwar.exe') -WorkingDirectory $target
}
