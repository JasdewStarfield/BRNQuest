#requires -Version 7.0
[CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'High')]
param(
    [Parameter(Mandatory)]
    [string]$ProjectId,

    [Parameter(Mandatory)]
    [string]$Token,

    [Parameter(Mandatory)]
    [string]$JarPath,

    [Parameter(Mandatory)]
    [string]$MetadataPath,

    [string]$ApiBase = 'https://minecraft.curseforge.com/api'
)

$ErrorActionPreference = 'Stop'
if ($ProjectId -notmatch '^\d+$') { throw 'Set a numeric CurseForge project ID before uploading.' }
$jar = Get-Item -LiteralPath $JarPath
$rendered = Get-Content -LiteralPath $MetadataPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($jar.Name -ne $rendered.jar_name) {
    throw "Jar '$($jar.Name)' does not match rendered metadata jar '$($rendered.jar_name)'."
}
$metadata = $rendered.curseforge | ConvertTo-Json -Depth 10 -Compress

if (-not $PSCmdlet.ShouldProcess("CurseForge project $ProjectId", "Upload $($jar.Name) as $($rendered.curseforge.displayName)")) {
    return
}

$request = @{
    Method  = 'Post'
    Uri     = "$ApiBase/projects/$ProjectId/upload-file"
    Headers = @{ 'X-Api-Token' = $Token }
    Form    = @{ metadata = $metadata; file = $jar }
}
try {
    $response = Invoke-RestMethod @request
} catch {
    # Include the HTTP status so failed jobs can distinguish rejection from a transient outage.
    if ($_.Exception.Response) {
        throw "CurseForge upload failed with HTTP $([int]$_.Exception.Response.StatusCode): $($_.ErrorDetails.Message)"
    }
    throw
}
Write-Host "[BRNQuest Release] CurseForge accepted file id $($response.id)." -ForegroundColor Green
