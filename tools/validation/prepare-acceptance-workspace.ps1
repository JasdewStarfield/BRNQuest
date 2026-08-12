[CmdletBinding()]
param(
    [switch]$Replace
)

$ErrorActionPreference = 'Stop'
$versionRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixtureRoot = Join-Path $versionRoot 'src\test\resources\fixtures\native\acceptance_workspace'
$targetRoot = Join-Path $versionRoot 'run\config\brnquest\workspace'
$manifest = Join-Path $fixtureRoot 'SHA256SUMS'

# Never silently replace an author's active workspace. Explicit replacement keeps a timestamped backup.
if ((Test-Path -LiteralPath $targetRoot) -and (Get-ChildItem -LiteralPath $targetRoot -Force | Select-Object -First 1)) {
    if (!$Replace) {
        throw "Workspace is not empty: $targetRoot. Re-run with -Replace to create a backup before installing the fixture."
    }
    $backupRoot = "$targetRoot.backup-$((Get-Date).ToString('yyyyMMdd-HHmmss'))"
    Move-Item -LiteralPath $targetRoot -Destination $backupRoot
    Write-Host "Backed up the existing workspace to $backupRoot"
}

$expected = @{}
Get-Content -LiteralPath $manifest -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([0-9A-Fa-f]{64})\s+\*?(.+)$') {
        $expected[$matches[2].Replace('/', '\')] = $matches[1].ToUpperInvariant()
    }
}
if ($expected.Count -eq 0) { throw "Fixture manifest is empty: $manifest" }

foreach ($entry in $expected.GetEnumerator()) {
    $source = Join-Path $fixtureRoot $entry.Key
    if (!(Test-Path -LiteralPath $source) -or (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $entry.Value) {
        throw "Source fixture hash mismatch: $($entry.Key)"
    }
    $target = Join-Path $targetRoot $entry.Key
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $entry.Value) {
        throw "Copied fixture hash mismatch: $($entry.Key)"
    }
}

Write-Host "Prepared $($expected.Count) verified acceptance workspace files in $targetRoot"
