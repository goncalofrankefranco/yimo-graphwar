function Test-TournamentReleaseFile([string]$RelativePath) {
    if ([string]::IsNullOrWhiteSpace($RelativePath)) { return $false }
    $normalized = $RelativePath.Replace('\', '/').TrimStart('/')
    $name = ($normalized -split '/')[-1]
    if ($normalized -match '(^|/)(data|backups)(/|$)') { return $false }
    if ($name -match '^\.env') { return $false }
    if ($name -match '(?i)\.(?:sqlite3?|db)(?:[-.](?:shm|wal|journal|backup|bak))?$') { return $false }
    return $true
}
