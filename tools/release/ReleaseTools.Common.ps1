Set-StrictMode -Version Latest

function Set-BrnquestUtf8File {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [AllowEmptyString()]
        [string]$Content
    )

    # Use UTF-8 without BOM consistently in Windows PowerShell 5 and PowerShell 7.
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8)
}

function Get-BrnquestProperty {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$Name
    )

    $propertiesPath = Join-Path $ProjectRoot 'gradle.properties'
    $matchingLine = Get-Content -LiteralPath $propertiesPath -Encoding UTF8 |
        Where-Object { $_ -match "^$([regex]::Escape($Name))=(.*)$" } |
        Select-Object -First 1

    if ($null -eq $matchingLine) {
        throw "Property '$Name' was not found in $propertiesPath."
    }

    return ($matchingLine -split '=', 2)[1].Trim()
}

function Get-BrnquestChangelogSection {
    param(
        [Parameter(Mandatory)]
        [string]$Path,

        [Parameter(Mandatory)]
        [string]$Heading
    )

    # ReadAllText preserves an empty file as a string, yielding a useful missing-section error.
    $content = [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path).Path)
    $escapedHeading = [regex]::Escape($Heading)
    $match = [regex]::Match(
        $content,
        "(?ms)^## \[$escapedHeading\][^\r\n]*\r?\n(?<body>.*?)(?=^## \[|\z)"
    )

    if (-not $match.Success) {
        throw "Section '## [$Heading]' was not found in $Path."
    }

    $body = $match.Groups['body'].Value.Trim()
    if (-not $body -or -not ($body -split '\r?\n' | Where-Object { $_.Trim() -and $_ -notmatch '^### ' })) {
        throw "Section '## [$Heading]' in $Path has no release notes."
    }
    return $body
}

function Assert-BrnquestReadmes {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$MinecraftVersion,

        [Parameter(Mandatory)]
        [string]$Loader,

        [Parameter(Mandatory)]
        [string]$JavaVersion
    )

    foreach ($fileName in @('README.md', 'README_zh.md')) {
        $path = Join-Path $ProjectRoot $fileName
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing bilingual README file: $path"
        }

        $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
        foreach ($requiredText in @($MinecraftVersion, $Loader, "Java", $JavaVersion)) {
            if (-not $content.Contains($requiredText)) {
                throw "$fileName in $ProjectRoot does not mention required release value '$requiredText'."
            }
        }
    }
}

function Assert-BrnquestChangelogVersion {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$Version
    )

    foreach ($fileName in @('CHANGELOG_zh.md', 'CHANGELOG.md')) {
        $path = Join-Path $ProjectRoot $fileName
        [void](Get-BrnquestChangelogSection -Path $path -Heading $Version)
    }
}

function Invoke-BrnquestBuild {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    Push-Location $ProjectRoot
    try {
        # $IsWindows is unavailable in Windows PowerShell 5; $env:OS works there and in pwsh.
        $wrapper = if ($env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
        & $wrapper build --no-configuration-cache --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed in $ProjectRoot with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Assert-BrnquestDiffCheck {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot
    )

    & git -C $ProjectRoot diff --check
    if ($LASTEXITCODE -ne 0) {
        throw "git diff --check failed in $ProjectRoot."
    }
}

function Get-BrnquestReleaseNotes {
    param(
        [Parameter(Mandatory)]
        [string]$ProjectRoot,

        [Parameter(Mandatory)]
        [string]$Version
    )

    $cn = Get-BrnquestChangelogSection -Path (Join-Path $ProjectRoot 'CHANGELOG_zh.md') -Heading $Version
    $en = Get-BrnquestChangelogSection -Path (Join-Path $ProjectRoot 'CHANGELOG.md') -Heading $Version
    $cnLabel = -join [char[]](20013, 25991)
    return "## $cnLabel`n`n$cn`n`n## English`n`n$en`n"
}
