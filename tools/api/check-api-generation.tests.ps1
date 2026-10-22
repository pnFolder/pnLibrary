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
    $repo = Join-Path $testRoot 'all-modules'
    @('gradle', 'modules/api/api', 'modules/features/config-api/api', 'modules/runtime-spi/api', 'modules/api/src/main/kotlin/org/pnfolder/pnlibrary/api/version') |
        ForEach-Object { New-Item -ItemType Directory -Path (Join-Path $repo $_) -Force | Out-Null }
    [IO.File]::WriteAllText((Join-Path $repo 'gradle/public-modules.properties'), "modules/api=modules/features/config-api`nmodules/features/config-api=`n")
    [IO.File]::WriteAllText((Join-Path $repo 'modules/api/api/api.api'), $base)
    [IO.File]::WriteAllText((Join-Path $repo 'modules/runtime-spi/api/runtime-spi.api'), $base)
    $configDump = Join-Path $repo 'modules/features/config-api/api/config-api.api'
    [IO.File]::WriteAllText($configDump, $base)
    [IO.File]::WriteAllText((Join-Path $repo 'modules/api/src/main/kotlin/org/pnfolder/pnlibrary/api/version/PnLibraryApi.kt'), 'const val VERSION: Int = 4')
    & git -C $repo init --quiet
    & git -C $repo -c user.name=ApiPolicyTest -c user.email=test@example.invalid add .
    & git -C $repo -c user.name=ApiPolicyTest -c user.email=test@example.invalid commit --quiet -m baseline
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare the multi-module API fixture' }
    [IO.File]::WriteAllText($configDump, $removal)
    Push-Location -LiteralPath $repo
    try {
        $output = & pwsh -NoProfile -File $checker -BaseRef HEAD 2>&1 | Out-String
        if ($LASTEXITCODE -ne 1 -or $output -notmatch 'modules/features/config-api/api/config-api.api') {
            throw "Removing a config API declaration was not rejected: $output"
        }
        [IO.File]::WriteAllText((Join-Path $repo 'gradle/public-modules.properties'), "modules/api=`n")
        $output = & pwsh -NoProfile -File $checker -BaseRef HEAD 2>&1 | Out-String
        if ($LASTEXITCODE -ne 1 -or $output -notmatch 'modules/features/config-api/api/config-api.api') {
            throw "Removing a module from the registry bypassed compatibility checks: $output"
        }
    } finally { Pop-Location }
    Write-Output 'API generation policy tests passed.'
} finally {
    $resolved = [IO.Path]::GetFullPath($testRoot)
    if ([IO.Path]::GetDirectoryName($resolved).TrimEnd('\', '/') -ne [IO.Path]::GetTempPath().TrimEnd('\', '/') -or
        -not [IO.Path]::GetFileName($resolved).StartsWith('pnlibrary-api-policy-')) {
        throw "Refusing to remove an unexpected test directory: $resolved"
    }
    Remove-Item -LiteralPath $testRoot -Recurse -Force
}

# Expected-failure checks above leave a non-zero $LASTEXITCODE; reaching here means every assertion passed.
exit 0
