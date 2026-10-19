[CmdletBinding()]
param(
    [string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot',
    [string]$OutputDir = (Join-Path (Split-Path -Parent $PSScriptRoot) 'build\yimo-stage7-release'),
    [string]$SourceRevision = ''
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
$gitMetadata = Join-Path $root '.git'
if (Test-Path -LiteralPath $gitMetadata) {
    $workingTree = (& git -C $root status --porcelain 2>$null) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify the source tree status.' }
    if (-not [string]::IsNullOrWhiteSpace($workingTree)) {
        throw 'Refusing to build from a dirty source tree, even when SourceRevision is supplied. Build from a clean Git archive.'
    }
} elseif ([string]::IsNullOrWhiteSpace($SourceRevision)) {
    throw 'SourceRevision is required when building outside a Git worktree.'
}

if (-not (Test-Path -LiteralPath $javac)) {
    throw "Java 8 javac was not found at $javac. Pass -JavaHome with the Java 8 installation path."
}
if (-not (Test-Path -LiteralPath $jar)) {
    throw "Java 8 jar was not found at $jar. Pass -JavaHome with the Java 8 installation path."
}

$output = [IO.Path]::GetFullPath($OutputDir)
if (Test-Path -LiteralPath $output) {
    throw "Output directory already exists: $output. Choose a new directory or remove it manually."
}

$classes = Join-Path $output 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null

$sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src') -Recurse -File -Filter '*.java')
if ($sources.Count -eq 0) {
    throw 'No Java production sources were found.'
}

$javacVersion = (& $javac -version 2>&1) -join ''
$javacArgs = @('-encoding', 'UTF-8')
if ($javacVersion.Contains('1.8')) {
    $javacArgs += @('-source', '8', '-target', '8')
} else {
    $javacArgs += @('--release', '8')
}
$javacArgs += @('-d', $classes)
$javacArgs += @($sources | ForEach-Object { $_.FullName })
& $javac @javacArgs
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE."
}

$resourceDir = Join-Path $root 'rsc'
$jarSpecs = @(
    @{ Name = 'YIMO-Graphwar-3.2.0.jar'; Main = 'Graphwar.Graphwar' },
    @{ Name = 'globalServer.jar'; Main = 'GlobalServer.GlobalServer' },
    @{ Name = 'roomServer.jar'; Main = 'RoomServer.RoomServer' }
)

foreach ($spec in $jarSpecs) {
    $jarPath = Join-Path $output $spec.Name
    & $jar 'cfe' $jarPath $spec.Main '-C' $classes '.' '-C' $root 'rsc'
    if ($LASTEXITCODE -ne 0) {
        throw "jar failed while creating $($spec.Name)."
    }
}

# The compiled classes are already inside the three JARs; keep the uploaded
# release small and avoid shipping a second mutable copy of production code.
Remove-Item -LiteralPath $classes -Recurse -Force

Copy-Item -LiteralPath $resourceDir -Destination (Join-Path $output 'rsc') -Recurse
$tournamentRoot = Join-Path $root 'tournament'
$tournamentOutput = Join-Path $output 'tournament'
$tournamentFiles = @(Get-ChildItem -LiteralPath $tournamentRoot -Recurse -File -Force | Where-Object {
    $relativePath = $_.FullName.Substring($tournamentRoot.Length).TrimStart('\')
    $localDirectory = $relativePath -match '(^|[\\/])(data|backups)([\\/]|$)'
    $environmentFile = $_.Name -match '^\.env(?:\.|$)'
    $databaseFile = $_.Name -match '(?i)\.(?:sqlite(?:-(?:shm|wal))?|sqlite3?|db)$'
    -not ($localDirectory -or $environmentFile -or $databaseFile)
})
foreach ($file in $tournamentFiles) {
    $relativePath = $file.FullName.Substring($tournamentRoot.Length).TrimStart('\')
    $destination = Join-Path $tournamentOutput $relativePath
    $destinationDirectory = Split-Path -Parent $destination
    if (-not (Test-Path -LiteralPath $destinationDirectory)) {
        New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null
    }
    Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
}
Copy-Item -LiteralPath (Join-Path $root 'yimo.properties.example') -Destination $output
Copy-Item -LiteralPath (Join-Path $root 'COPYING') -Destination $output
Copy-Item -LiteralPath (Join-Path $root 'LICENSE') -Destination $output
Copy-Item -LiteralPath (Join-Path $root 'README.md') -Destination $output

$sourceRevision = $SourceRevision
if ([string]::IsNullOrWhiteSpace($sourceRevision)) {
    $sourceRevision = (& git -C $root rev-parse HEAD 2>$null) -join ''
    if ([string]::IsNullOrWhiteSpace($sourceRevision)) { $sourceRevision = 'unknown' }
}

$manifest = @(
    'YIMO Graphwar 3.2.0 client / 2.2.0-compatible Stage 7 release',
    'Product version: 3.2.0',
    'Build ID: YIMO-Graphwar-2.2.0',
    'Protocol version: 2',
    'Java target: 8',
    'Tournament runtime: Node.js 24.x',
    "Source revision: $sourceRevision"
)
Set-Content -LiteralPath (Join-Path $output 'RELEASE-MANIFEST.txt') -Value $manifest -Encoding UTF8

$files = @(Get-ChildItem -LiteralPath $output -Recurse -File | Sort-Object FullName)
$hashLines = foreach ($file in $files) {
    $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    $relative = $file.FullName.Substring($output.Length).TrimStart('\').Replace('\', '/')
    "$hash  $relative"
}
Set-Content -LiteralPath (Join-Path $output 'SHA256SUMS.txt') -Value $hashLines -Encoding ASCII

$zipPath = "$output.zip"
if (Test-Path -LiteralPath $zipPath) {
    throw "Archive already exists: $zipPath. Choose a new output directory."
}
Compress-Archive -Path (Join-Path $output '*') -DestinationPath $zipPath -CompressionLevel Optimal

Write-Output "Built release: $output"
Write-Output "Built archive: $zipPath"
