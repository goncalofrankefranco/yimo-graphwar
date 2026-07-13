$ErrorActionPreference = 'Stop'

$target = Join-Path $env:LOCALAPPDATA 'Graphwar'
$archive = Join-Path $PSScriptRoot 'payload.zip'
$startMenu = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\Graphwar'

New-Item -ItemType Directory -Force -Path $target | Out-Null
Expand-Archive -LiteralPath $archive -DestinationPath $target -Force

$shell = New-Object -ComObject WScript.Shell
New-Item -ItemType Directory -Force -Path $startMenu | Out-Null
$shortcut = $shell.CreateShortcut((Join-Path $startMenu 'Graphwar.lnk'))
$shortcut.TargetPath = Join-Path $target 'launch-graphwar.cmd'
$shortcut.WorkingDirectory = $target
$shortcut.IconLocation = Join-Path $target 'runtime\bin\javaw.exe'
$shortcut.Save()

Write-Host "Graphwar installed to $target"
Start-Process -FilePath (Join-Path $target 'launch-graphwar.cmd') -WorkingDirectory $target
