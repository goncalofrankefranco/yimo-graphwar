[CmdletBinding()]
param(
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot',
    [string]$RuntimeSource = '',
    [string]$GlobalHost = '153.75.82.155',
    [string]$TournamentApiBaseUrl = 'http://153.75.82.155',
    [string]$OutputDir = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$installerRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $root 'build\yimo-stage8-release'
}

function Fail([string]$message) { throw "Stage 8 build failed: $message" }

if ([string]::IsNullOrWhiteSpace($RuntimeSource)) {
    $RuntimeSource = Join-Path $JavaHome 'jre'
}

$javac = Join-Path $JavaHome 'bin\javac.exe'
$runtimeJava = Join-Path $RuntimeSource 'bin\java.exe'
$runtimeJavaw = Join-Path $RuntimeSource 'bin\javaw.exe'
if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) { Fail "Java 8 javac was not found at $javac." }
if (-not (Test-Path -LiteralPath $runtimeJava -PathType Leaf)) { Fail "Java runtime was not found at $runtimeJava." }
if (-not (Test-Path -LiteralPath $runtimeJavaw -PathType Leaf)) { Fail "Java runtime was not found at $runtimeJavaw." }
if ($GlobalHost -notmatch '^[A-Za-z0-9.:-]+$') { Fail 'GlobalHost contains unsupported characters.' }

$apiUri = $null
if (-not [Uri]::TryCreate($TournamentApiBaseUrl, [UriKind]::Absolute, [ref]$apiUri) -or
        $apiUri.Scheme -notin @('http', 'https') -or [string]::IsNullOrWhiteSpace($apiUri.Host)) {
    Fail 'TournamentApiBaseUrl must be an HTTP(S) URL.'
}

$output = [IO.Path]::GetFullPath($OutputDir)
if (Test-Path -LiteralPath $output) { Fail "Output directory already exists: $output. Choose a new directory." }
New-Item -ItemType Directory -Path $output -Force | Out-Null
$revision = (& git -C $root rev-parse HEAD 2>$null) -join ''

$work = Join-Path ([IO.Path]::GetTempPath()) ('yimo-stage8-' + [Guid]::NewGuid().ToString('N'))
$stage7 = Join-Path $work 'stage7'
$payload = Join-Path $work 'payload'
$iexpressSource = Join-Path $work 'iexpress'
New-Item -ItemType Directory -Path $work, $payload, $iexpressSource -Force | Out-Null

try {
    $stage7Builder = Join-Path $root 'deploy\build-stage7-release.ps1'
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $stage7Builder -JavaHome $JavaHome -OutputDir $stage7
    if ($LASTEXITCODE -ne 0) { Fail 'Stage 7 Java build failed.' }

    Copy-Item -Path (Join-Path $stage7 '*') -Destination $payload -Recurse -Force
    Copy-Item -LiteralPath (Join-Path $root 'NOTICE.md') -Destination $payload -Force
    Copy-Item -LiteralPath (Join-Path $root 'THIRD-PARTY-LICENSES.md') -Destination $payload -Force
    Copy-Item -LiteralPath (Join-Path $installerRoot 'launch-yimo.cmd') -Destination $payload -Force
    Copy-Item -LiteralPath (Join-Path $installerRoot 'launch-practice-server.cmd') -Destination $payload -Force
    Copy-Item -LiteralPath (Join-Path $installerRoot 'launch-practice-client.cmd') -Destination $payload -Force

    $csc = Get-ChildItem -Path "$env:WINDIR\Microsoft.NET\Framework*\v4.0.30319\csc.exe" -File |
        Select-Object -First 1
    if ($null -eq $csc) { Fail 'The Windows .NET Framework C# compiler was not found.' }

    $yimoProperties = @"
# YIMO Graphwar 2.0.0 default endpoint
global.host=$GlobalHost
global.port=23762
room.port.start=30000
room.port.end=30049
tournament.api.baseUrl=$TournamentApiBaseUrl
build.id=YIMO-Graphwar-2.0.0
protocol.version=2
"@
    Set-Content -LiteralPath (Join-Path $payload 'yimo.properties') -Value $yimoProperties -Encoding ASCII

    $practiceProperties = @"
# Offline loopback practice configuration
global.host=127.0.0.1
global.port=23762
room.port.start=30000
room.port.end=30049
tournament.api.baseUrl=http://127.0.0.1:8080
build.id=YIMO-Graphwar-2.0.0
protocol.version=2
"@
    Set-Content -LiteralPath (Join-Path $payload 'practice.properties') -Value $practiceProperties -Encoding ASCII

    $installedReadme = @"
YIMO Graphwar 2.0.0

Double-click YIMO-Graphwar.exe to connect to the configured YIMO endpoint.
The Start menu shortcut launches the same executable.
Run launch-yimo.cmd only as a command-line fallback.
Run launch-practice-server.cmd followed by launch-practice-client.cmd for a local practice server.

Installed files: %~dp0
Source: https://github.com/goncalofrankefranco/yimo-graphwar
License: GPL-3.0-or-later; see COPYING and NOTICE.md.
"@
    Set-Content -LiteralPath (Join-Path $payload 'README-INSTALLED.txt') -Value $installedReadme -Encoding UTF8

    $runtime = Join-Path $payload 'runtime'
    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    Copy-Item -Path (Join-Path $RuntimeSource '*') -Destination $runtime -Recurse -Force

    $icon = Join-Path $work 'YIMO.ico'
    $iconBuilder = Join-Path $work 'YimoIconBuilder.exe'
    $officialLogo = Join-Path $installerRoot 'yimo-logo.png'
    if (-not (Test-Path -LiteralPath $officialLogo -PathType Leaf)) {
        Fail 'The official YIMO logo asset is missing.'
    }
    & $csc.FullName /nologo /target:exe /optimize+ /out:$iconBuilder /r:System.Drawing.dll `
        (Join-Path $installerRoot 'YimoIconBuilder.cs')
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $iconBuilder -PathType Leaf)) {
        Fail 'The YIMO icon builder could not be compiled.'
    }
    & $iconBuilder $officialLogo $icon
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $icon -PathType Leaf)) {
        Fail 'The YIMO icon could not be generated.'
    }

    $launcher = Join-Path $payload 'YIMO-Graphwar.exe'
    & $csc.FullName /nologo /target:winexe /optimize+ /out:$launcher /win32icon:$icon /r:System.Windows.Forms.dll `
        (Join-Path $installerRoot 'YimoLauncher.cs')
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
        Fail 'The clickable YIMO launcher could not be compiled.'
    }
    Copy-Item -LiteralPath $icon -Destination (Join-Path $payload 'YIMO.ico') -Force
    Copy-Item -LiteralPath $officialLogo -Destination (Join-Path $payload 'yimo-logo.png') -Force

    $portable = Join-Path $output 'YIMO-Graphwar-2.0.0-Portable.zip'
    Compress-Archive -Path (Join-Path $payload '*') -DestinationPath $portable -CompressionLevel Optimal

    $payloadZip = Join-Path $iexpressSource 'payload.zip'
    Copy-Item -LiteralPath $portable -Destination $payloadZip -Force
    Set-Content -LiteralPath (Join-Path $iexpressSource 'payload.version') -Value ("YIMO-Graphwar-2.0.0|$revision") -Encoding ASCII
    Copy-Item -LiteralPath (Join-Path $installerRoot 'install.cmd') -Destination $iexpressSource -Force
    Copy-Item -LiteralPath (Join-Path $installerRoot 'install.ps1') -Destination $iexpressSource -Force

    $installer = Join-Path $output 'YIMO-Graphwar-2.0.0-Setup.exe'
    $sed = Join-Path $work 'YIMO-Graphwar-2.0.0.sed'
    $sourceFilesRoot = $iexpressSource.TrimEnd('\') + '\'
    $sedContent = @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=0
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=1
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=I
InstallPrompt=%InstallPrompt%
DisplayLicense=%DisplayLicense%
FinishMessage=%FinishMessage%
TargetName=%TargetName%
FriendlyName=%FriendlyName%
AppLaunched=%AppLaunched%
PostInstallCmd=%PostInstallCmd%
AdminQuietInstCmd=%AdminQuietInstCmd%
UserQuietInstCmd=%UserQuietInstCmd%
SourceFiles=SourceFiles
[Strings]
InstallPrompt=
DisplayLicense=
FinishMessage=
TargetName=$installer
FriendlyName=YIMO Graphwar 2.0.0
AppLaunched=install.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
FILE0="install.cmd"
FILE1="install.ps1"
FILE2="payload.zip"
FILE3="payload.version"
[SourceFiles]
SourceFiles0=$sourceFilesRoot
[SourceFiles0]
%FILE0%=
%FILE1%=
%FILE2%=
%FILE3%=
"@
    Set-Content -LiteralPath $sed -Value $sedContent -Encoding ASCII

    $iexpress = Join-Path $env:WINDIR 'System32\iexpress.exe'
    if (-not (Test-Path -LiteralPath $iexpress -PathType Leaf)) { Fail 'Windows IExpress was not found.' }
    $process = Start-Process -FilePath $iexpress -ArgumentList @('/N', $sed) -Wait -PassThru -WindowStyle Hidden
    if ($process.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $installer -PathType Leaf)) {
        Fail "IExpress failed with exit code $($process.ExitCode)."
    }

    $manifest = @(
        'YIMO Graphwar 2.0.0 release',
        'Build ID: YIMO-Graphwar-2.0.0',
        'Protocol version: 2',
        'Java target: 8',
        'Installer: YIMO-Graphwar-2.0.0-Setup.exe',
        'Portable package: YIMO-Graphwar-2.0.0-Portable.zip',
        "Default global host: $GlobalHost",
        "Default tournament API: $TournamentApiBaseUrl",
        "Source revision: $revision",
        'Runtime legal files: copied from the selected Java runtime input when present'
    )
    Set-Content -LiteralPath (Join-Path $output 'RELEASE-MANIFEST.txt') -Value $manifest -Encoding UTF8

    $hashLines = foreach ($file in @(Get-ChildItem -LiteralPath $output -File | Sort-Object Name)) {
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        "$hash  $($file.Name)"
    }
    Set-Content -LiteralPath (Join-Path $output 'SHA256SUMS.txt') -Value $hashLines -Encoding ASCII

    Write-Output "Built Stage 8 release: $output"
    Write-Output "Installer: $installer"
    Write-Output "Portable: $portable"
}
finally {
    if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue }
}
