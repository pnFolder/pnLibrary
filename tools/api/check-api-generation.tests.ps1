$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'check-api-generation.ps1'
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("pnlibrary-api-policy-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $testRoot | Out-Null

function Invoke-Case {
    param(
        [string]$Name,
        [string]$BaseApi,
        [string]$CurrentApi,
        [int]$BaseVersion,
        [int]$CurrentVersion,
        [int]$ExpectedExit
    )

    $caseRoot = Join-Path $testRoot $Name
    New-Item -ItemType Directory -Path $caseRoot | Out-Null
    $baseApiPath = Join-Path $caseRoot 'base.api'
    $currentApiPath = Join-Path $caseRoot 'current.api'
    $baseVersionPath = Join-Path $caseRoot 'base-version.kt'
    $currentVersionPath = Join-Path $caseRoot 'current-version.kt'
    [System.IO.File]::WriteAllText($baseApiPath, $BaseApi)
    [System.IO.File]::WriteAllText($currentApiPath, $CurrentApi)
    [System.IO.File]::WriteAllText($baseVersionPath, "const val VERSION: Int = $BaseVersion")
    [System.IO.File]::WriteAllText($currentVersionPath, "const val VERSION: Int = $CurrentVersion")

    & pwsh -NoProfile -File $checker `
        -BaseApiPath $baseApiPath `
        -CurrentApiPath $currentApiPath `
        -BaseVersionPath $baseVersionPath `
        -CurrentVersionPath $currentVersionPath | Out-Host
    if ($LASTEXITCODE -ne $ExpectedExit) {
        throw "$Name expected exit $ExpectedExit but received $LASTEXITCODE"
    }
}

$base = @'
public final class example/Api {
	public final fun existing ()V
}
'@
$addition = @'
public final class example/Api {
	public final fun existing ()V
	public final fun added ()V
}
'@
$removal = @'
public final class example/Api {
}
'@

try {
    Invoke-Case 'compatible-addition' $base $addition 4 4 0
    Invoke-Case 'forbidden-removal' $base $removal 4 4 1
    Invoke-Case 'generation-increase' $base $removal 4 5 0
    Invoke-Case 'generation-decrease' $base $addition 4 3 1
    Write-Output 'API generation policy tests passed.'
} finally {
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}
