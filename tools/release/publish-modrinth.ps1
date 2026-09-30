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

    [string]$ApiBase = 'https://api.modrinth.com/v2'
)

$ErrorActionPreference = 'Stop'
if ($ProjectId -like 'REPLACE_*') { throw 'Replace the Modrinth project ID placeholder before uploading.' }
Add-Type -AssemblyName System.Net.Http
$jar = Get-Item -LiteralPath $JarPath
$metadata = Get-Content -LiteralPath $MetadataPath -Raw -Encoding utf8 | ConvertFrom-Json
if ($jar.Name -ne $metadata.jar_name) {
    throw "Jar '$($jar.Name)' does not match rendered metadata jar '$($metadata.jar_name)'."
}

$payload = [ordered]@{}
foreach ($property in $metadata.modrinth.PSObject.Properties) {
    $payload[$property.Name] = $property.Value
}
$payload.project_id = $ProjectId
$platformVersion = $payload.version_number
$data = $payload | ConvertTo-Json -Depth 10 -Compress

if (-not $PSCmdlet.ShouldProcess("Modrinth project $ProjectId", "Publish $($jar.Name) as $platformVersion")) {
    return
}

$headers = @{
    Authorization = $Token
    'User-Agent'  = 'JasdewStarfield/BRNQuest-release-automation (github.com/JasdewStarfield/BRNQuest)'
}

# Accept a rerun only when the same platform version contains exactly this jar.
$existingVersions = Invoke-RestMethod -Method Get -Uri "$ApiBase/project/$ProjectId/version" -Headers $headers
$matchingVersions = @($existingVersions | Where-Object version_number -eq $platformVersion)
if ($matchingVersions.Count -gt 0) {
    $sha512 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA512).Hash.ToLowerInvariant()
    $matchingFiles = @($matchingVersions | ForEach-Object { $_.files } | Where-Object {
        $_.filename -eq $jar.Name -and $_.hashes.sha512 -eq $sha512
    })
    if ($matchingVersions.Count -ne 1 -or $matchingFiles.Count -ne 1) {
        throw "Modrinth version $platformVersion already exists with different files. Use a new version."
    }
    Write-Host "[BRNQuest Release] Modrinth version $platformVersion already exists; skipping." -ForegroundColor Yellow
    return
}

$client = [System.Net.Http.HttpClient]::new()
$form = [System.Net.Http.MultipartFormDataContent]::new()
try {
    $null = $client.DefaultRequestHeaders.TryAddWithoutValidation('Authorization', $Token)
    $null = $client.DefaultRequestHeaders.TryAddWithoutValidation('User-Agent', $headers['User-Agent'])

    # Modrinth parses the data part as JSON; an untyped form string can be rejected before JSON parsing begins.
    $dataContent = [System.Net.Http.StringContent]::new($data, [System.Text.Encoding]::UTF8, 'application/json')
    $fileStream = [System.IO.File]::OpenRead($jar.FullName)
    $fileContent = [System.Net.Http.StreamContent]::new($fileStream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('application/java-archive')
    $form.Add($dataContent, 'data')
    $form.Add($fileContent, 'file', $jar.Name)

    $httpResponse = $client.PostAsync("$ApiBase/version", $form).GetAwaiter().GetResult()
    $responseBody = $httpResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $httpResponse.IsSuccessStatusCode) {
        throw "Modrinth upload failed with HTTP $([int]$httpResponse.StatusCode): $responseBody"
    }
    $response = $responseBody | ConvertFrom-Json
} finally {
    $form.Dispose()
    $client.Dispose()
}
Write-Host "[BRNQuest Release] Modrinth published version id $($response.id)." -ForegroundColor Green
