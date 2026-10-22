param([switch]$ValidateOnly)
$ErrorActionPreference = 'Stop'

# Shared by local releases and both Actions entry points. An unprotected signing key needs no password.
$required = @(
    'mavenCentralUsername', 'mavenCentralPassword', 'signingInMemoryKey',
    'POM_LICENSE_NAME', 'POM_LICENSE_URL'
)
foreach ($property in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("ORG_GRADLE_PROJECT_$property"))) {
        throw "Missing publishing configuration: ORG_GRADLE_PROJECT_$property"
    }
}
if ($ValidateOnly) { Write-Output 'Maven Central configuration is present.'; return }

$wrapper = if ($IsWindows) { Join-Path $PSScriptRoot '../../gradlew.bat' } else { Join-Path $PSScriptRoot '../../gradlew' }
& $wrapper publishAndReleaseToMavenCentral --no-daemon --no-configuration-cache --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Maven Central publishing failed' }
