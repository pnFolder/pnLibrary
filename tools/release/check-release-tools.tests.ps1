param([string]$ManifestPath = 'distribution/build/libs/release-manifest.json')
$ErrorActionPreference = 'Stop'
$manifestFile = (Resolve-Path -LiteralPath $ManifestPath).Path
$manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json -AsHashtable
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("pnlibrary-release-test-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    $catalog = Join-Path $testRoot 'releases.json'
    & (Join-Path $PSScriptRoot 'update-catalog.ps1') -ManifestPath $manifestFile -CatalogPath $catalog -Notes 'Fixture release' -PublishedAt '2026-10-22T12:00:00Z'
    $created = Get-Content -LiteralPath $catalog -Raw | ConvertFrom-Json -AsHashtable
    $entry = $created.releases[0]
    if ($created.product -ne $manifest.product -or $entry.version -ne $manifest.version -or $entry.channel -ne $manifest.channel -or
        $entry.api.minimum -ne $manifest.api.minimum -or ([DateTimeOffset]$entry.publishedAt).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ') -ne '2026-10-22T12:00:00Z' -or $entry.artifacts.Count -ne 4) {
        throw 'Catalog did not preserve packaged release metadata'
    }
    foreach ($artifact in $entry.artifacts) {
        $source = $manifest.artifacts | Where-Object file -eq $artifact.file
        if ($artifact.sha256 -ne $source.sha256 -or $artifact.size -ne $source.size -or $artifact.java.minimum -ne $source.java.minimum) {
            throw 'Catalog artifact metadata drifted'
        }
    }
    if (-not (Test-Path -LiteralPath (Join-Path $testRoot "changelogs/$($manifest.version).md"))) { throw 'Changelog was not created' }
    $before = Get-FileHash -LiteralPath $catalog
    $rejected = $false
    try { & (Join-Path $PSScriptRoot 'update-catalog.ps1') -ManifestPath $manifestFile -CatalogPath $catalog -Notes 'Duplicate' } catch {
        if ($_.Exception.Message -notmatch 'already exists') { throw }
        $rejected = $true
    }
    if (-not $rejected -or (Get-FileHash -LiteralPath $catalog).Hash -ne $before.Hash) { throw 'Published release was overwritten' }
    Write-Output 'Release catalog tests passed.'
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    if ([IO.Path]::GetDirectoryName($resolved).TrimEnd('\', '/') -ne [IO.Path]::GetTempPath().TrimEnd('\', '/') -or
        -not [IO.Path]::GetFileName($resolved).StartsWith('pnlibrary-release-test-')) { throw "Unexpected test directory: $resolved" }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
