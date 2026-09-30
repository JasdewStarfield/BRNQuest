#requires -Version 7.0
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory)][string]$Repository,
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$')][string]$Version,
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$Commit,
    [Parameter(Mandatory)][string]$JarPath,
    [Parameter(Mandatory)][string]$NotesPath,
    [ValidateSet('release', 'beta', 'alpha')][string]$ReleaseType = 'release',
    [switch]$Draft
)

$ErrorActionPreference = 'Stop'
$jar = Get-Item -LiteralPath $JarPath
if (-not (Get-Content -LiteralPath $NotesPath -Raw -Encoding UTF8)) {
    throw 'GitHub release notes must not be empty.'
}

# A rerun must still point at the source commit used to build this artifact.
$tagCommit = & git rev-parse --verify "refs/tags/${Version}^{commit}" 2>$null
if ($LASTEXITCODE -eq 0 -and $tagCommit -ne $Commit) {
    throw "Tag $Version belongs to another commit. Use a new version."
}
if (-not $PSCmdlet.ShouldProcess($Repository, "Publish GitHub release $Version from $Commit")) { return }

$existing = & gh release view $Version --repo $Repository --json assets,isDraft 2>$null
if ($LASTEXITCODE -eq 0) {
    $release = $existing | ConvertFrom-Json
    if (-not $tagCommit -or ($Draft -and -not $release.isDraft)) {
        throw 'Existing release cannot be resumed: missing local tag or attempted public-to-draft change.'
    }
    $asset = @($release.assets | Where-Object name -eq $jar.Name)
    if ($asset.Count -gt 0) {
        # Compare bytes before accepting an existing asset; never silently replace a published jar.
        $downloadDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $downloadDirectory | Out-Null
        try {
            & gh release download $Version --repo $Repository --pattern $jar.Name --dir $downloadDirectory
            if ($LASTEXITCODE -ne 0) { throw 'Failed to verify existing GitHub asset.' }
            $downloadedJar = Join-Path $downloadDirectory $jar.Name
            if ((Get-FileHash $downloadedJar).Hash -ne (Get-FileHash $jar.FullName).Hash) {
                throw 'Existing GitHub asset differs from this build. Use a new version.'
            }
        } finally {
            # Delete only the exact downloaded file and its now-empty temporary directory.
            $downloadedJar = Join-Path $downloadDirectory $jar.Name
            if (Test-Path -LiteralPath $downloadedJar) { Remove-Item -LiteralPath $downloadedJar }
            Remove-Item -LiteralPath $downloadDirectory
        }
    } else {
        & gh release upload $Version $jar.FullName --repo $Repository
        if ($LASTEXITCODE -ne 0) { throw 'Failed to upload GitHub asset.' }
    }
    & gh release edit $Version --repo $Repository --notes-file $NotesPath "--draft=$($Draft.IsPresent.ToString().ToLowerInvariant())" "--prerelease=$(($ReleaseType -ne 'release').ToString().ToLowerInvariant())"
    if ($LASTEXITCODE -ne 0) { throw 'Failed to update GitHub release.' }
    return
}

# Target the immutable workflow SHA, rather than a branch that can advance during the build.
$arguments = @('release', 'create', $Version, '--repo', $Repository, '--target', $Commit,
    '--title', "BRNQuest $Version", '--notes-file', $NotesPath)
if ($Draft) { $arguments += '--draft' }
if ($ReleaseType -ne 'release') { $arguments += '--prerelease' }
$arguments += $jar.FullName
& gh @arguments
if ($LASTEXITCODE -ne 0) { throw 'Failed to create GitHub release.' }
