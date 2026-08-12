[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$versionRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixtureRoot = Join-Path $versionRoot 'src\test\resources\fixtures\ftb_v13\eow'
$targetRoot = Join-Path $versionRoot 'run\brnquest-import\eow'
$manifest = Join-Path $fixtureRoot 'SHA256SUMS'

New-Item -ItemType Directory -Force -Path $targetRoot | Out-Null
Get-ChildItem -LiteralPath $fixtureRoot -Recurse -Filter '*.snbt' -File | ForEach-Object {
    $relative = $_.FullName.Substring($fixtureRoot.Length).TrimStart('\')
    $target = Join-Path $targetRoot $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $target -Force
}

$expected = @{}
Get-Content -LiteralPath $manifest -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([0-9A-Fa-f]{64})\s+\*?(.+)$') { $expected[$matches[2].Replace('/', '\')] = $matches[1].ToUpperInvariant() }
}
foreach ($entry in $expected.GetEnumerator()) {
    $copied = Join-Path $targetRoot $entry.Key
    if (!(Test-Path -LiteralPath $copied) -or (Get-FileHash -LiteralPath $copied -Algorithm SHA256).Hash -ne $entry.Value) {
        throw "Fixture hash mismatch: $($entry.Key)"
    }
}

Write-Host "Prepared $($expected.Count) verified EOW fixture files in $targetRoot"
