param(
    [Parameter(Mandatory)][string]$ManifestPath,
    [Parameter(Mandatory)][string]$Notes,
    [string]$CatalogPath = '.pnlibrary/releases.json',
    [DateTimeOffset]$PublishedAt = [DateTimeOffset]::UtcNow
)
$ErrorActionPreference = 'Stop'
$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json -AsHashtable
if ($manifest.schema -ne 1 -or $manifest.version -notmatch '^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$') { throw 'Invalid release manifest' }
if ($manifest.repository -notmatch '^[\w.-]+/[\w.-]+$') { throw 'Invalid repository identity' }
if ($manifest.channel -notin @('stable', 'rc', 'beta', 'alpha', 'dev')) { throw 'Invalid release channel' }
if ($manifest.artifacts.Count -ne 4) { throw 'Expected all four verified platform artifacts' }
$directory = Split-Path -Parent (Resolve-Path -LiteralPath $ManifestPath)
foreach ($artifact in $manifest.artifacts) {
    if ([IO.Path]::GetFileName($artifact.file) -ne $artifact.file) { throw 'Artifact filename must have no directory' }
    $file = Join-Path $directory $artifact.file
    if ((Get-Item -LiteralPath $file).Length -ne $artifact.size -or
        (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $artifact.sha256) {
        throw "Artifact changed after manifest generation: $($artifact.file)"
    }
    $metadata = Get-Content -LiteralPath "$file.meta.json" -Raw | ConvertFrom-Json -AsHashtable
    if ($metadata.product -ne $manifest.product -or $metadata.version -ne $manifest.version -or
        $metadata.channel -ne $manifest.channel -or $metadata.java.minimum -ne $artifact.java.minimum -or
        $metadata.pnLibraryApi.minimum -ne $manifest.api.minimum -or $metadata.pnLibraryApi.maximum -ne $manifest.api.maximum) {
        throw "Artifact metadata disagrees with release manifest: $($artifact.file)"
    }
}
$catalog = if (Test-Path -LiteralPath $CatalogPath) {
    Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json -AsHashtable
} else { @{ schema = 1; product = $manifest.product; releases = @() } }
if ($catalog.schema -ne 1 -or $catalog.product -ne $manifest.product) { throw 'Catalog product or schema mismatch' }
if (@($catalog.releases | Where-Object version -eq $manifest.version).Count) { throw "Release $($manifest.version) already exists; published versions are immutable" }
$entry = [ordered]@{
    version = $manifest.version
    channel = $manifest.channel
    description = $Notes
    changelogUrl = "https://github.com/$($manifest.repository)/blob/main/.pnlibrary/changelogs/$($manifest.version).md"
    publishedAt = $PublishedAt.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    api = $manifest.api
    artifacts = $manifest.artifacts
}
$catalog.releases = @($entry) + @($catalog.releases)
$catalogFile = [IO.Path]::GetFullPath($CatalogPath)
$changelogDirectory = Join-Path ([IO.Path]::GetDirectoryName($catalogFile)) 'changelogs'
New-Item -ItemType Directory -Path $changelogDirectory -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $changelogDirectory "$($manifest.version).md"), "# $($manifest.version)`n`n$Notes`n")
[IO.File]::WriteAllText($catalogFile, ($catalog | ConvertTo-Json -Depth 30) + "`n")
Write-Output "Prepared catalog entry for $($manifest.version); no release has been published."
