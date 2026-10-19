[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReleaseDir,
    [string]$ExpectedGlobalHost = 'graphwar-server.yimo-official.org',
    [string]$ExpectedTournamentApiBaseUrl = 'https://graphwar-server.yimo-official.org'
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
    $activeInstaller = Get-Process -Name 'YIMO-Graphwar-3.2.0-Setup' -ErrorAction SilentlyContinue
    if ($null -ne $activeInstaller) { throw 'another YIMO installer test is already running.' }
    $env:LOCALAPPDATA = Join-Path $test 'local'
    $env:APPDATA = Join-Path $test 'roaming'
    $env:TEMP = Join-Path $test 'temp'
    $env:TMP = $env:TEMP
    $env:YIMO_INSTALL_NO_LAUNCH = '1'
    New-Item -ItemType Directory -Path $env:TEMP -Force | Out-Null

    $target = Join-Path $env:LOCALAPPDATA 'YIMO Graphwar'
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $target 'YIMO-Graphwar-2.2.0.jar') -Value 'old client fixture'

    $setup = Join-Path $release 'YIMO-Graphwar-3.2.0-Setup.exe'
    $manifestPath = Join-Path $release 'RELEASE-MANIFEST.txt'
    Assert-True (Test-Path -LiteralPath $manifestPath -PathType Leaf) 'release manifest is missing.'
    $manifest = Get-Content -Raw $manifestPath
    $fingerprintMatch = [regex]::Match($manifest, 'Package fingerprint: ([A-Fa-f0-9]{64})')
    Assert-True $fingerprintMatch.Success 'release manifest must record the embedded payload fingerprint.'
    $packageFingerprint = $fingerprintMatch.Groups[1].Value.ToUpperInvariant()
    $portableFiles = @(Get-ChildItem -LiteralPath $release -Filter '*Portable.zip' -File -ErrorAction SilentlyContinue)
    Assert-True ($portableFiles.Count -eq 0) 'release output must not keep a duplicate portable ZIP.'
    Assert-True (Test-Path -LiteralPath $setup -PathType Leaf) 'setup executable is missing.'
    $process = Start-Process -FilePath $setup -Wait -PassThru -WindowStyle Hidden
    Assert-True ($process.ExitCode -eq 0) "setup exited with code $($process.ExitCode)."

    foreach ($relative in @(
            'YIMO-Graphwar-3.2.0.jar',
            'YIMO-Graphwar.exe',
            'YIMO.ico',
            'yimo-logo.png',
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
    $tournamentRoot = Join-Path $target 'tournament'
    Assert-True (Test-Path -LiteralPath (Join-Path $tournamentRoot 'package.json') -PathType Leaf) 'tournament service source is missing.'
    $privateTournamentFiles = @(Get-ChildItem -LiteralPath $tournamentRoot -Recurse -File -Force | Where-Object {
        $relative = $_.FullName.Substring($tournamentRoot.Length).TrimStart('\')
        $relative -match '(^|[\\/])(data|backups)([\\/]|$)' -or
                $_.Name -match '^\.env(?:\.|$)' -or
                $_.Name -match '(?i)\.(?:sqlite(?:-(?:shm|wal))?|sqlite3?|db)$'
    })
    Assert-True ($privateTournamentFiles.Count -eq 0) 'local tournament secrets or database files must not be packaged.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $target 'YIMO-Graphwar-2.2.0.jar'))) 'upgrade must remove the obsolete versioned client JAR.'
    Assert-True ($properties -match ('global\.host=' + [regex]::Escape($ExpectedGlobalHost))) 'installed global endpoint is incorrect.'
    Assert-True ($properties -match ('tournament\.api\.baseUrl=' + [regex]::Escape($ExpectedTournamentApiBaseUrl))) 'installed tournament endpoint is incorrect.'
    Assert-True ($properties -match '(?m)^build\.id=YIMO-Graphwar-2\.2\.0\r?$') 'installed build ID must remain compatible with the existing servers.'
    Assert-True ($properties -match '(?m)^protocol\.version=2\r?$') 'installed protocol must remain compatible with the existing servers.'
    Assert-True ($properties -notmatch '(?i)(YIMO_ADMIN_TOKEN|ROOM_HMAC_SECRET|BEGIN (RSA|OPENSSH) PRIVATE KEY)') 'secret-like value was installed.'
    $shortcut = Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\YIMO Graphwar\YIMO Graphwar.lnk'
    Assert-True (Test-Path -LiteralPath $shortcut -PathType Leaf) 'Start menu shortcut is missing.'
    $shortcutInfo = (New-Object -ComObject WScript.Shell).CreateShortcut($shortcut)
    Assert-True ($shortcutInfo.IconLocation -match 'YIMO\.ico') 'shortcut must use the YIMO icon file.'

    $secondProcess = Start-Process -FilePath $setup -Wait -PassThru -WindowStyle Hidden
    Assert-True ($secondProcess.ExitCode -eq 0) "second setup run exited with code $($secondProcess.ExitCode)."
    $installedVersion = (Get-Content -Raw (Join-Path $target '.yimo-installed-version')).Trim()
    Assert-True ($installedVersion -match '^YIMO-Graphwar-3\.2\.0\|') 'installed product version marker is invalid.'
    Assert-True ($installedVersion -eq ('YIMO-Graphwar-3.2.0|' + $packageFingerprint.Substring(0, 16))) 'installed marker must identify the embedded package contents.'
    Assert-True ($manifest.Contains('Product version: 3.2.0')) 'manifest must identify the product version.'
    Assert-True ($manifest.Contains('Build ID: YIMO-Graphwar-2.2.0')) 'manifest must preserve the compatible wire build ID.'
    Assert-True ($manifest.Contains('Protocol version: 2')) 'manifest must preserve the compatible protocol.'

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
