[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseDir
)

$ErrorActionPreference = 'Stop'
$release = (Resolve-Path -LiteralPath $ReleaseDir).Path
$test = Join-Path $env:TEMP ('yimo-clean-install-' + [Guid]::NewGuid().ToString('N'))
$oldLocal = $env:LOCALAPPDATA
$oldAppData = $env:APPDATA
$oldTemp = $env:TEMP
$oldTmp = $env:TMP
$oldNoLaunch = $env:YIMO_INSTALL_NO_LAUNCH

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) { throw "Clean-install check failed: $message" }
}

New-Item -ItemType Directory -Path $test -Force | Out-Null
try {
    # IExpress uses a fixed IXP000.TMP name on some Windows builds. Remove
    # only that stale extractor cache so an earlier package cannot contaminate
    # this isolated install check.
    $legacyIexpressTemp = Join-Path $oldTemp 'IXP000.TMP'
    $activeInstaller = Get-Process -Name 'YIMO-Graphwar-2.0.0-Setup' -ErrorAction SilentlyContinue
    if ($null -ne $activeInstaller) { throw 'another YIMO installer test is already running.' }
    if (Test-Path -LiteralPath $legacyIexpressTemp) {
        Remove-Item -LiteralPath $legacyIexpressTemp -Recurse -Force
    }
    $env:LOCALAPPDATA = Join-Path $test 'local'
    $env:APPDATA = Join-Path $test 'roaming'
    $env:TEMP = Join-Path $test 'temp'
    $env:TMP = $env:TEMP
    $env:YIMO_INSTALL_NO_LAUNCH = '1'
    New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null

    $setup = Join-Path $release 'YIMO-Graphwar-2.0.0-Setup.exe'
    Assert-True (Test-Path -LiteralPath $setup -PathType Leaf) 'setup executable is missing.'
    $process = Start-Process -FilePath $setup -Wait -PassThru -WindowStyle Hidden
    Assert-True ($process.ExitCode -eq 0) "setup exited with code $($process.ExitCode)."

    $target = Join-Path $env:LOCALAPPDATA 'YIMO Graphwar'
    foreach ($relative in @(
            'YIMO-Graphwar-2.0.0.jar',
            'YIMO-Graphwar.exe',
            'YIMO.ico',
            '.yimo-installed-version',
            'globalServer.jar',
            'roomServer.jar',
            'yimo.properties',
            'practice.properties',
            'launch-yimo.cmd',
            'runtime\bin\java.exe',
            'runtime\bin\javaw.exe')) {
        Assert-True (Test-Path -LiteralPath (Join-Path $target $relative) -PathType Leaf) "missing installed file $relative."
    }

    $properties = Get-Content -Raw (Join-Path $target 'yimo.properties')
    Assert-True ($properties -match 'global\.host=153\.75\.82\.155') 'installed endpoint is incorrect.'
    Assert-True ($properties -notmatch '(?i)(YIMO_ADMIN_TOKEN|ROOM_HMAC_SECRET|BEGIN (RSA|OPENSSH) PRIVATE KEY)') 'secret-like value was installed.'
    $shortcut = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\YIMO Graphwar\YIMO Graphwar.lnk'
    Assert-True (Test-Path -LiteralPath $shortcut -PathType Leaf) 'Start menu shortcut is missing.'

    $secondProcess = Start-Process -FilePath $setup -Wait -PassThru -WindowStyle Hidden
    Assert-True ($secondProcess.ExitCode -eq 0) "second setup run exited with code $($secondProcess.ExitCode)."
    $installedVersion = (Get-Content -Raw (Join-Path $target '.yimo-installed-version')).Trim()
    Assert-True ($installedVersion -match '^YIMO-Graphwar-2\.0\.0\|') 'installed version marker is invalid.'

    $javaProcess = Start-Process -FilePath (Join-Path $target 'runtime\bin\java.exe') -ArgumentList @('-version') -Wait -PassThru -WindowStyle Hidden
    Assert-True ($javaProcess.ExitCode -eq 0) 'bundled Java did not start.'
    $signature = [IO.File]::ReadAllBytes($setup)
    Assert-True ($signature[0] -eq 0x4d -and $signature[1] -eq 0x5a) 'setup is not a Windows PE executable.'

    Write-Output 'Stage 8 clean-install check passed.'
}
finally {
    if ($null -eq $oldNoLaunch) {
        Remove-Item Env:YIMO_INSTALL_NO_LAUNCH -ErrorAction SilentlyContinue
    } else {
        $env:YIMO_INSTALL_NO_LAUNCH = $oldNoLaunch
    }
    $env:LOCALAPPDATA = $oldLocal
    $env:APPDATA = $oldAppData
    $env:TEMP = $oldTemp
    $env:TMP = $oldTmp
    if (Test-Path -LiteralPath $test) {
        Remove-Item -LiteralPath $test -Recurse -Force -ErrorAction SilentlyContinue
    }
}
