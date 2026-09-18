param(
    [string]$BaseRef,
    [string]$BaseApiPath,
    [string]$CurrentApiPath = 'modules/api/api/api.api',
    [string]$BaseVersionPath,
    [string]$CurrentVersionPath = 'modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/version/PnLibraryApi.kt'
)

$ErrorActionPreference = 'Stop'

function Read-GitFile {
    param([string]$Ref, [string]$Path)
    $value = & git show "${Ref}:$Path" 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Cannot read $Path from git ref $Ref" }
    return ($value -join "`n")
}

function Read-GitFileWithFallback {
    param([string]$Ref, [string]$CurrentPath, [string]$LegacyPath)
    $value = & git show "${Ref}:$CurrentPath" 2>$null
    if ($LASTEXITCODE -eq 0) { return ($value -join "`n") }
    return Read-GitFile $Ref $LegacyPath
}

function Read-InputText {
    param([string]$Path, [string]$Ref)
    if ($Ref) { return Read-GitFile $Ref $Path }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "File not found: $Path" }
    return [System.IO.File]::ReadAllText((Resolve-Path -LiteralPath $Path))
}

function Read-ApiGeneration {
    param([string]$Source)
    $match = [regex]::Match($Source, 'const\s+val\s+VERSION\s*:\s*Int\s*=\s*(\d+)')
    if (-not $match.Success) { throw 'PnLibraryApi.VERSION was not found' }
    return [int]$match.Groups[1].Value
}

function Get-ApiDeclarations {
    param([string]$Dump)
    $result = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    $owner = ''
    foreach ($line in ($Dump -split "`r?`n")) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('//') -or $trimmed -eq '}') { continue }
        if (-not [char]::IsWhiteSpace($line[0])) {
            $owner = $trimmed
            [void]$result.Add("TYPE::$trimmed")
        } elseif ($owner) {
            [void]$result.Add("$owner::$trimmed")
        }
    }
    return $result
}

$baseApi = if ($BaseRef) {
    Read-GitFileWithFallback $BaseRef 'modules/api/api/api.api' 'pnlibrary-api/api/pnlibrary-api.api'
} else {
    Read-InputText $BaseApiPath $null
}
$baseVersionSource = if ($BaseRef) {
    Read-GitFileWithFallback $BaseRef `
        'modules/api/src/main/kotlin/ru/privatenull/pnlibrary/api/version/PnLibraryApi.kt' `
        'pnlibrary-api/src/main/kotlin/ru/privatenull/pnlibrary/api/version/PnLibraryApi.kt'
} else {
    Read-InputText $BaseVersionPath $null
}
$currentApi = Read-InputText $CurrentApiPath $null
$currentVersionSource = Read-InputText $CurrentVersionPath $null

$baseVersion = Read-ApiGeneration $baseVersionSource
$currentVersion = Read-ApiGeneration $currentVersionSource
if ($currentVersion -lt $baseVersion) {
    Write-Error "API generation decreased from $baseVersion to $currentVersion."
    exit 1
}

$baseDeclarations = Get-ApiDeclarations $baseApi
$currentDeclarations = Get-ApiDeclarations $currentApi
$removed = @($baseDeclarations | Where-Object { -not $currentDeclarations.Contains($_) } | Sort-Object)

if ($removed.Count -gt 0 -and $currentVersion -eq $baseVersion) {
    Write-Error "Public ABI removed $($removed.Count) declaration(s) while API generation remained ${currentVersion}:`n$($removed -join "`n")"
    exit 1
}

if ($removed.Count -gt 0) {
    Write-Output "Public ABI changed with API generation increase $baseVersion -> $currentVersion."
} else {
    Write-Output "Public ABI is backward compatible at API generation $currentVersion."
}
