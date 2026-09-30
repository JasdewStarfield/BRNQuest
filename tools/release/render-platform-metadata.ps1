[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ProjectRoot,

    [Parameter(Mandatory)]
    [string]$Version,

    [Parameter(Mandatory)]
    [ValidateSet('neoforge_1_21_1')]
    [string]$Target,

    [Parameter(Mandatory)]
    [ValidateSet('release', 'beta', 'alpha')]
    [string]$ReleaseType,

    [Parameter(Mandatory)]
    [string]$OutputPath,

    [string]$TemplatePath = (Join-Path $PSScriptRoot 'platform-metadata.json')
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ReleaseTools.Common.ps1')

function Expand-BrnquestMetadataText {
    param(
        [Parameter(Mandatory)]
        [string]$Text,

        [Parameter(Mandatory)]
        [hashtable]$Values
    )

    $expanded = $Text
    foreach ($entry in $Values.GetEnumerator()) {
        $expanded = $expanded.Replace("{$($entry.Key)}", [string]$entry.Value)
    }
    return $expanded
}

$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$template = Get-Content -LiteralPath $TemplatePath -Raw -Encoding utf8 | ConvertFrom-Json
$targetTemplate = $template.targets.$Target
if ($null -eq $targetTemplate) {
    throw "Target '$Target' was not found in $TemplatePath."
}

$values = @{
    version           = $Version
    minecraft_version = $targetTemplate.minecraft_version
    loader            = $targetTemplate.curseforge_loader
}
$releaseName = Expand-BrnquestMetadataText -Text $template.release.name -Values $values
$versionNumber = Expand-BrnquestMetadataText -Text $template.release.version_number -Values $values
$jarName = Expand-BrnquestMetadataText -Text $targetTemplate.jar_name -Values $values
$releaseNotes = Get-BrnquestReleaseNotes -ProjectRoot $ProjectRoot -Version $Version
$repositoryUrl = $template.project.repository_url.TrimEnd('/')
$changelogBranch = $template.project.changelog_branch
$cnChangelogLabel = -join [char[]](20013, 25991)
$releaseNotes += @"

---

Full changelogs: [English]($repositoryUrl/blob/$changelogBranch/CHANGELOG.md) | [$cnChangelogLabel]($repositoryUrl/blob/$changelogBranch/CHANGELOG_zh.md)
"@

$modrinthDependencies = @($template.modrinth.dependencies | ForEach-Object {
    [ordered]@{
        version_id      = $null
        project_id      = $_.project_id
        file_name       = $null
        dependency_type = $_.dependency_type
    }
})
$curseForgeRelations = @($template.curseforge.relations.projects | ForEach-Object {
    [ordered]@{
        slug      = $_.slug
        # CurseForge's upload endpoint requires numeric project IDs inside relation metadata.
        projectID = [int]$_.projectID
        type      = $_.type
    }
})
$gameVersionNames = @($template.curseforge.game_version_names | ForEach-Object {
    Expand-BrnquestMetadataText -Text $_ -Values $values
})

# The rendered file intentionally contains no credentials and can be retained as a CI artifact.
$metadata = [ordered]@{
    schema_version = $template.schema_version
    target = $Target
    jar_name = $jarName
    modrinth = [ordered]@{
        name           = $releaseName
        version_number = $versionNumber
        changelog      = $releaseNotes
        dependencies   = $modrinthDependencies
        game_versions  = @($targetTemplate.minecraft_version)
        version_type   = $ReleaseType
        loaders        = @($targetTemplate.modrinth_loader)
        featured       = [bool]$template.modrinth.featured
        status         = $template.modrinth.status
        file_parts     = @('file')
        primary_file   = 'file'
        environment    = $template.modrinth.environment
    }
    curseforge = [ordered]@{
        changelog                 = $releaseNotes
        changelogType             = $template.curseforge.changelog_type
        displayName               = Expand-BrnquestMetadataText -Text $template.curseforge.display_name -Values $values
        gameVersionNames          = $gameVersionNames
        releaseType               = $ReleaseType
        isMarkedForManualRelease  = [bool]$template.curseforge.is_marked_for_manual_release
        relations                 = [ordered]@{ projects = $curseForgeRelations }
    }
}

$outputDirectory = Split-Path -Parent $OutputPath
if ($outputDirectory) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
Set-BrnquestUtf8File -Path $OutputPath -Content ($metadata | ConvertTo-Json -Depth 12)
Write-Host "[BRNQuest Release] Rendered $Target metadata to $OutputPath."
