[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ProjectRoot,

    [Parameter(Mandatory)]
    [string]$Version,

    [Parameter(Mandatory)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ReleaseTools.Common.ps1')

$notes = Get-BrnquestReleaseNotes -ProjectRoot (Resolve-Path -LiteralPath $ProjectRoot).Path -Version $Version
Set-BrnquestUtf8File -Path $OutputPath -Content $notes
Write-Host "[BRNQuest Release] Wrote bilingual release notes to $OutputPath."
