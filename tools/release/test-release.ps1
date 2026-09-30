[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ProjectRoot,

    [Parameter(Mandatory)]
    [ValidatePattern('^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$')]
    [string]$Version,

    [Parameter(Mandatory)]
    [string]$MinecraftVersion,

    [Parameter(Mandatory)]
    [ValidateSet('NeoForge')]
    [string]$Loader,

    [Parameter(Mandatory)]
    [ValidateSet('21')]
    [string]$JavaVersion,

    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'ReleaseTools.Common.ps1')

$ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
$actualVersion = Get-BrnquestProperty -ProjectRoot $ProjectRoot -Name 'mod_version'
if ($actualVersion -ne $Version) {
    throw "Expected mod_version=$Version in $ProjectRoot, found $actualVersion."
}

$actualMinecraftVersion = Get-BrnquestProperty -ProjectRoot $ProjectRoot -Name 'minecraft_version'
if ($actualMinecraftVersion -ne $MinecraftVersion) {
    throw "Expected Minecraft $MinecraftVersion in $ProjectRoot, found $actualMinecraftVersion."
}

Assert-BrnquestChangelogVersion -ProjectRoot $ProjectRoot -Version $Version
Assert-BrnquestReadmes -ProjectRoot $ProjectRoot -MinecraftVersion $MinecraftVersion -Loader $Loader -JavaVersion $JavaVersion
Assert-BrnquestDiffCheck -ProjectRoot $ProjectRoot

if (-not $SkipBuild) {
    Invoke-BrnquestBuild -ProjectRoot $ProjectRoot
}

$expectedJar = Join-Path $ProjectRoot "build/libs/brnquest-mc$MinecraftVersion-$Version.jar"
if (-not $SkipBuild -and -not (Test-Path -LiteralPath $expectedJar -PathType Leaf)) {
    throw "Expected release artifact was not produced: $expectedJar"
}

Write-Host "[BRNQuest Release] Verified $Loader $MinecraftVersion / BRNQuest $Version." -ForegroundColor Green
if ($env:GITHUB_OUTPUT -and -not $SkipBuild) {
    "jar_path=$expectedJar" | Out-File -LiteralPath $env:GITHUB_OUTPUT -Encoding utf8 -Append
}
