[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('version', 'build', 'github', 'central', 'release')]
    [string]$Action,
    [string]$Version,
    [string]$Message
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location -LiteralPath $ProjectRoot

function Invoke-Checked {
    param([string]$Program, [string[]]$Arguments)
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $Program $($Arguments -join ' ')"
    }
}

function Get-ProjectVersion {
    $match = Select-String -LiteralPath 'gradle.properties' -Pattern '^version=(.+)$' | Select-Object -First 1
    if (-not $match) { throw 'version is missing from gradle.properties' }
    return $match.Matches[0].Groups[1].Value.Trim()
}

function Set-ProjectVersion {
    param([string]$NewVersion)
    if ([string]::IsNullOrWhiteSpace($NewVersion)) {
        throw 'Specify a version: set-version.bat 2.0.0-beta.7'
    }
    if ($NewVersion -notmatch '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$') {
        throw "Invalid semantic version: $NewVersion"
    }

    $OldVersion = Get-ProjectVersion
    if ($OldVersion -eq $NewVersion) {
        Write-Host "Version is already $NewVersion"
        return
    }

    $properties = Get-Content -LiteralPath 'gradle.properties' -Raw
    $properties = $properties -replace '(?m)^version=.*$', "version=$NewVersion"
    [IO.File]::WriteAllText((Join-Path $ProjectRoot 'gradle.properties'), $properties, [Text.UTF8Encoding]::new($false))

    $documentation = @('README.md', 'INTEGRATION.md', 'HELP-README.md')
    $documentation += Get-ChildItem -LiteralPath 'docs' -Filter '*.md' -File -Recurse | ForEach-Object FullName
    foreach ($file in $documentation) {
        if (-not (Test-Path -LiteralPath $file)) { continue }
        $path = (Resolve-Path -LiteralPath $file).Path
        $text = Get-Content -LiteralPath $path -Raw
        $updated = $text.Replace($OldVersion, $NewVersion)
        if ($updated -ne $text) {
            [IO.File]::WriteAllText($path, $updated, [Text.UTF8Encoding]::new($false))
        }
    }

    $changelog = Get-Content -LiteralPath 'CHANGELOG.md' -Raw
    if ($changelog -notmatch "(?m)^## $([regex]::Escape($NewVersion))$") {
        $heading = "# Changelog`r`n`r`n## $NewVersion`r`n`r`n- Describe the release changes.`r`n"
        $changelog = $changelog -replace '^# Changelog\r?\n', $heading
        [IO.File]::WriteAllText((Join-Path $ProjectRoot 'CHANGELOG.md'), $changelog, [Text.UTF8Encoding]::new($false))
    }
    Write-Host "Version updated: $OldVersion -> $NewVersion"
}

function Build-Library {
    Invoke-Checked '.\gradlew.bat' @('clean', 'test', ':pnlibrary-distribution:build', '--console=plain')
    Write-Host "Artifacts: $ProjectRoot\pnlibrary-distribution\build\libs"
}

function Publish-GitHub {
    param([string]$CommitMessage)
    if ([string]::IsNullOrWhiteSpace($CommitMessage)) {
        throw 'Specify a commit message: publish-github.bat "release: 2.0.0-beta.7"'
    }
    Invoke-Checked 'git' @('rev-parse', '--is-inside-work-tree')
    $changes = & git status --short
    if ($LASTEXITCODE -ne 0) { throw 'Unable to read Git status' }
    if (-not $changes) { throw 'There are no changes to commit' }

    Write-Host 'The following changes will be committed:'
    $changes | ForEach-Object { Write-Host $_ }
    $answer = Read-Host 'Commit ALL listed files and push them? [y/N]'
    if ($answer -notin @('y', 'Y', 'yes', 'YES')) { throw 'Cancelled' }

    Invoke-Checked 'git' @('add', '-A')
    Invoke-Checked 'git' @('commit', '-m', $CommitMessage)
    Invoke-Checked 'git' @('push', 'origin', 'HEAD')
}

function Publish-Central {
    Assert-CentralConfigured
    foreach ($name in @('CENTRAL_USERNAME', 'CENTRAL_PASSWORD', 'SIGNING_KEY', 'SIGNING_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
            throw "Required environment variable is missing: $name"
        }
    }
    Invoke-Checked '.\gradlew.bat' @('publishAndReleaseToMavenCentral', '--no-configuration-cache', '--console=plain')
}

function Assert-CentralConfigured {
    $tasks = & .\gradlew.bat tasks --all --console=plain 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect Gradle publishing tasks' }
    if ($tasks -notmatch 'publishAndReleaseToMavenCentral') {
        throw @'
Maven Central is not configured in this project yet.
Required before publishing: a verified Central Portal namespace, POM metadata,
artifact signing, and CENTRAL_USERNAME/CENTRAL_PASSWORD plus a GPG key.
No files were uploaded.
'@
    }
}

switch ($Action) {
    'version' { Set-ProjectVersion $Version }
    'build' { Build-Library }
    'github' { Build-Library; Publish-GitHub $Message }
    'central' { Build-Library; Publish-Central }
    'release' {
        Assert-CentralConfigured
        Set-ProjectVersion $Version
        Build-Library
        Publish-GitHub $(if ($Message) { $Message } else { "release: $Version" })
        Publish-Central
    }
}
