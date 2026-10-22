param(
    [string]$BaseRef,
    [string]$BaseApiPath,
    [string]$CurrentApiPath = 'modules/api/api/api.api',
    [string]$BaseVersionPath,
    [string]$CurrentVersionPath = 'modules/api/src/main/kotlin/org/pnfolder/pnlibrary/api/version/PnLibraryApi.kt'
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

function Get-RepositoryApiDeclarations {
    param([string]$Ref)
    $paths = if ($Ref) {
        @(& git ls-tree -r --name-only $Ref | Where-Object { $_ -match '^(modules|platforms)/.+/api/[^/]+\.api$' })
    } else {
        $modules = @(Get-Content -LiteralPath 'gradle/public-modules.properties' |
            Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') } |
            ForEach-Object { $_.Split('=')[0].Trim() })
        @($modules + 'modules/runtime-spi' | ForEach-Object {
            Get-ChildItem -LiteralPath (Join-Path $_ 'api') -Filter '*.api' | ForEach-Object {
                [IO.Path]::GetRelativePath((Get-Location).Path, $_.FullName).Replace('\', '/')
            }
        })
    }
    $declarations = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
    foreach ($path in $paths) {
        $dump = Read-InputText $path $Ref
        foreach ($entry in (Get-ApiDeclarations $dump)) { [void]$declarations.Add("${path}::$entry") }
    }
    return ,$declarations
}

$allModules = $BaseRef -and -not $PSBoundParameters.ContainsKey('CurrentApiPath')
$baseApi = if ($allModules) { '' } elseif ($BaseRef) {
    Read-GitFileWithFallback $BaseRef 'modules/api/api/api.api' 'pnlibrary-api/api/pnlibrary-api.api'
} else {
    Read-InputText $BaseApiPath $null
}
$baseVersionSource = if ($BaseRef) {
    Read-GitFileWithFallback $BaseRef `
        'modules/api/src/main/kotlin/org/pnfolder/pnlibrary/api/version/PnLibraryApi.kt' `
        'pnlibrary-api/src/main/kotlin/org/pnfolder/pnlibrary/api/version/PnLibraryApi.kt'
} else {
    Read-InputText $BaseVersionPath $null
}
$currentApi = if ($allModules) { '' } else { Read-InputText $CurrentApiPath $null }
$currentVersionSource = Read-InputText $CurrentVersionPath $null

$baseVersion = Read-ApiGeneration $baseVersionSource
$currentVersion = Read-ApiGeneration $currentVersionSource
if ($currentVersion -lt $baseVersion) {
    throw "API generation must not decrease: $baseVersion -> $currentVersion"
}

$baseDeclarations = if ($allModules) { Get-RepositoryApiDeclarations $BaseRef } else { Get-ApiDeclarations $baseApi }
$currentDeclarations = if ($allModules) { Get-RepositoryApiDeclarations '' } else { Get-ApiDeclarations $currentApi }
$removed = @($baseDeclarations | Where-Object { -not $currentDeclarations.Contains($_) } | Sort-Object)

if ($removed.Count -gt 0 -and $currentVersion -le $baseVersion) {
    Write-Error "Public ABI removed $($removed.Count) declaration(s) without raising the API generation above ${baseVersion}:`n$($removed -join "`n")"
    exit 1
}

if ($removed.Count -gt 0) {
    Write-Output "Public ABI changed with API generation increase $baseVersion -> $currentVersion."
} else {
    Write-Output "Public ABI is backward compatible at API generation $currentVersion."
}
