[CmdletBinding()]
param(
    [switch]$Replace
)

$ErrorActionPreference = 'Stop'
$versionRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixtureRoot = Join-Path $versionRoot 'src\test\resources\fixtures\ftb_v13\rich_text'
$targetRoot = Join-Path $versionRoot 'run\config\brnquest\imports\p4c-rich-text'

# Replace only this disposable import source; author drafts and other import sources stay untouched.
if ((Test-Path -LiteralPath $targetRoot) -and
        (Get-ChildItem -LiteralPath $targetRoot -Force | Select-Object -First 1)) {
    if (!$Replace) {
        throw "P4c import fixture already exists: $targetRoot. Re-run with -Replace to back it up first."
    }
    $backupRoot = "$targetRoot.backup-$((Get-Date).ToString('yyyyMMdd-HHmmss'))"
    Move-Item -LiteralPath $targetRoot -Destination $backupRoot
    Write-Host "Backed up the existing P4c import fixture to $backupRoot"
}

$copiedCount = 0
Get-ChildItem -LiteralPath $fixtureRoot -Recurse -Filter '*.snbt' -File | ForEach-Object {
    # Substring-based paths keep this helper compatible with Windows PowerShell 5.1.
    $relativePath = $_.FullName.Substring($fixtureRoot.Length).TrimStart([char[]]@('\', '/'))
    $target = Join-Path $targetRoot $relativePath
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $target
    if ((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash -ne
            (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash) {
        throw "Copied P4c fixture hash mismatch: $relativePath"
    }
    $copiedCount++
}

Write-Host "Prepared $copiedCount verified P4c rich-text fixture files in $targetRoot"
