$ErrorActionPreference = 'Stop'
$platform = $env:PLATFORM
if ($platform -notin @('paper', 'folia', 'velocity', 'bungee')) { throw 'Unsupported native platform' }
if ($env:SERVER_URL -notmatch '^https://') { throw "Set NATIVE_$($platform.ToUpperInvariant())_URL to an HTTPS server JAR URL" }
if ($env:SERVER_SHA256 -notmatch '^[0-9a-fA-F]{64}$') { throw "Set NATIVE_$($platform.ToUpperInvariant())_SHA256 to the pinned JAR hash" }
if ($platform -in @('paper', 'folia') -and $env:ACCEPTED_EULA -notmatch '(?m)^eula=true\s*$') {
    throw 'NATIVE_MINECRAFT_EULA must contain an already accepted eula.txt; this workflow cannot accept it for you'
}
$directory = Join-Path $PWD 'build/native-software'
New-Item -ItemType Directory -Path $directory -Force | Out-Null
$server = Join-Path $directory 'server.jar'
Invoke-WebRequest -Uri $env:SERVER_URL -OutFile $server
if ((Get-FileHash -LiteralPath $server -Algorithm SHA256).Hash -ne $env:SERVER_SHA256) { throw 'Server JAR hash mismatch' }
if ($platform -in @('paper', 'folia')) {
    [IO.File]::WriteAllText((Join-Path $directory 'eula.txt'), $env:ACCEPTED_EULA)
}
