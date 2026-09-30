#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$ModrinthProjectId = 'k2cwPRGW',
    [string]$CurseForgeProjectId = '1714617',
    [string]$OutputPath = './build/reports/platform-access.json',
    [string]$ModrinthApiBase = 'https://api.modrinth.com/v2',
    [string]$CurseForgeApiBase = 'https://api.curseforge.com/v1',
    [string]$CurseForgeAuthorApiBase = 'https://minecraft.curseforge.com/api'
)

$ErrorActionPreference = 'Stop'

function Get-PlatformProbe {
    param([string]$Uri, [hashtable]$Headers)
    try {
        # Read-only probes return status codes, never raw error bodies or credential-bearing headers.
        $response = Invoke-RestMethod -Method Get -Uri $Uri -Headers $Headers -TimeoutSec 20 `
            -SkipHttpErrorCheck -StatusCodeVariable httpStatus
        return @{ status = [int]$httpStatus; data = $response }
    } catch {
        return @{ status = 0; data = $null }
    }
}

$report = [ordered]@{
    checked_at = [DateTime]::UtcNow.ToString('o')
    upload_attempted = $false
    modrinth = [ordered]@{
        project_id = $ModrinthProjectId
        token_configured = [bool]$env:MODRINTH_TOKEN
        project_http_status = $null
        project_matches = $false
        project_status = $null
        versions_http_status = $null
        versions = @()
    }
    curseforge = [ordered]@{
        project_id = $CurseForgeProjectId
        token_configured = [bool]$env:CURSEFORGE_TOKEN
        api_key_configured = [bool]$env:CURSEFORGE_API_KEY
        author_http_status = $null
        upload_permission_verified = $false
        project_http_status = $null
        project_matches = $false
        files_http_status = $null
        files = @()
    }
}
$failures = [System.Collections.Generic.List[string]]::new()

if (-not $env:MODRINTH_TOKEN -or $ModrinthProjectId -notmatch '^[a-zA-Z0-9_-]+$') {
    $failures.Add('Set MODRINTH_TOKEN and a valid Modrinth project ID.')
} else {
    $headers = @{ Authorization = $env:MODRINTH_TOKEN; 'User-Agent' = 'JasdewStarfield/BRNQuest-platform-preflight' }
    $project = Get-PlatformProbe "$ModrinthApiBase/project/$ModrinthProjectId" $headers
    $report.modrinth.project_http_status = $project.status
    if ($project.status -eq 200 -and ($project.data.id -eq $ModrinthProjectId -or $project.data.slug -eq $ModrinthProjectId)) {
        $report.modrinth.project_matches = $true
        $report.modrinth.project_status = $project.data.status
        $versions = Get-PlatformProbe "$ModrinthApiBase/project/$ModrinthProjectId/version" $headers
        $report.modrinth.versions_http_status = $versions.status
        if ($versions.status -eq 200) {
            $report.modrinth.versions = @($versions.data | ForEach-Object {
                [ordered]@{ version = $_.version_number; status = $_.status; files = @($_.files.filename) }
            })
        } else {
            $failures.Add("Modrinth version-list read failed (HTTP $($versions.status)).")
        }
    } else {
        $failures.Add("Modrinth project read failed (HTTP $($project.status)); check token access to the pending project and its ID.")
    }
}

if (-not $env:CURSEFORGE_TOKEN -or $CurseForgeProjectId -notmatch '^\d+$') {
    $failures.Add('Set CURSEFORGE_TOKEN and a numeric CurseForge project ID.')
} else {
    # Legacy author read routes may be unavailable; their failure cannot establish upload permission.
    $author = Get-PlatformProbe "$CurseForgeAuthorApiBase/game/versions" @{ 'X-Api-Token' = $env:CURSEFORGE_TOKEN }
    $report.curseforge.author_http_status = $author.status
    if ($author.status -in @(401, 403)) {
        $failures.Add("CurseForge author token rejected by the read probe (HTTP $($author.status)).")
    }
}

if ($env:CURSEFORGE_API_KEY -and $CurseForgeProjectId -match '^\d+$') {
    # Public/history reads use a separate key; an upload token cannot substitute for this header.
    $headers = @{ 'x-api-key' = $env:CURSEFORGE_API_KEY; Accept = 'application/json' }
    $project = Get-PlatformProbe "$CurseForgeApiBase/mods/$CurseForgeProjectId" $headers
    $report.curseforge.project_http_status = $project.status
    if ($project.status -eq 200 -and [string]$project.data.data.id -eq $CurseForgeProjectId) {
        $report.curseforge.project_matches = $true
        $files = Get-PlatformProbe "$CurseForgeApiBase/mods/$CurseForgeProjectId/files?pageSize=20" $headers
        $report.curseforge.files_http_status = $files.status
        if ($files.status -eq 200) {
            $report.curseforge.files = @($files.data.data | ForEach-Object {
                [ordered]@{ id = $_.id; name = $_.fileName; status = $_.fileStatus; game_versions = @($_.gameVersions) }
            })
        } else {
            $failures.Add("CurseForge file-list read failed (HTTP $($files.status)).")
        }
    } else {
        $failures.Add("CurseForge public project read failed (HTTP $($project.status)); check CURSEFORGE_API_KEY and project ID.")
    }
}

# Persist the sanitized report even on a failed probe, so CI can retain actionable status evidence.
$directory = Split-Path -Parent $OutputPath
if ($directory) { New-Item -ItemType Directory -Path $directory -Force | Out-Null }
[System.IO.File]::WriteAllText($OutputPath, ($report | ConvertTo-Json -Depth 12), [System.Text.UTF8Encoding]::new($false))
Write-Host "Modrinth project read: HTTP $($report.modrinth.project_http_status); project status: $($report.modrinth.project_status)"
Write-Host "CurseForge project read: HTTP $($report.curseforge.project_http_status); author read probe: HTTP $($report.curseforge.author_http_status)"
Write-Host 'No upload attempted. CurseForge upload permission requires a future real upload to verify.'
if ($failures.Count) { throw ($failures -join "`n") }
