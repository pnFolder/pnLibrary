<#
.SYNOPSIS
Starts a real proxy in a new isolated build fixture and exercises the managed catalog commands.
.DESCRIPTION
Requires an existing Velocity or BungeeCord server JAR. Never copies its configuration, secrets,
plugins or player data. Binds only to loopback; does not download server software or accept an EULA.
This is a command/lifecycle smoke test, not proof of a complete update/restart/rollback cycle.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Velocity', 'BungeeCord')][string]$Platform,
    [Parameter(Mandatory)][string]$ServerJar,
    [Parameter(Mandatory)][string]$LibraryJar,
    [ValidateRange(1024, 65535)][int]$Port = 29671,
    [string]$Java = 'java'
)
$ErrorActionPreference = 'Stop'
$serverSource = (Resolve-Path -LiteralPath $ServerJar).Path
$librarySource = (Resolve-Path -LiteralPath $LibraryJar).Path
$repository = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$fixture = Join-Path $repository ('build/update-catalog-live/' + $Platform + '-' + [guid]::NewGuid().ToString('N'))
$plugins = Join-Path $fixture 'plugins'
[IO.Directory]::CreateDirectory($plugins) | Out-Null
Copy-Item -LiteralPath $serverSource -Destination (Join-Path $fixture 'server.jar')
Copy-Item -LiteralPath $librarySource -Destination (Join-Path $plugins 'pnLibrary.jar')
$utf8 = [Text.UTF8Encoding]::new($false)
if ($Platform -eq 'Velocity') {
    $configuration = @"
config-version = "2.8"
bind = "127.0.0.1:$Port"
motd = "Isolated pnLibrary verification"
show-max-players = 1
online-mode = true
player-info-forwarding-mode = "NONE"
forwarding-secret-file = "forwarding.secret"
[servers]
fixture = "127.0.0.1:1"
try = ["fixture"]
[forced-hosts]
"lobby.example.com" = ["fixture"]
"factions.example.com" = ["fixture"]
"minigames.example.com" = ["fixture"]
[advanced]
announce-proxy-commands = false
log-player-connections = false
"@
    [IO.File]::WriteAllText((Join-Path $fixture 'velocity.toml'), $configuration, $utf8)
    $readyPattern = 'Done \('
    $stopCommand = 'shutdown'
} else {
    $configuration = @"
online_mode: true
ip_forward: false
player_limit: 1
servers:
  fixture:
    address: 127.0.0.1:1
    restricted: true
listeners:
- host: 127.0.0.1:$Port
  query_enabled: false
  motd: Isolated pnLibrary verification
  max_players: 1
  priorities: [fixture]
  force_default_server: true
  tab_list: GLOBAL_PING
  bind_local_address: true
  ping_passthrough: false
  forced_hosts: {}
disabled_commands: []
permissions: {}
groups: {}
"@
    [IO.File]::WriteAllText((Join-Path $fixture 'config.yml'), $configuration, $utf8)
    [IO.File]::WriteAllText((Join-Path $fixture 'modules.yml'), "version: 2`nmodules: []`n", $utf8)
    $readyPattern = 'Listening on '
    $stopCommand = 'end'
}
$metrics = Join-Path $plugins 'bStats'
[IO.Directory]::CreateDirectory($metrics) | Out-Null
[IO.File]::WriteAllText((Join-Path $metrics 'config.yml'), "enabled: false`n", $utf8)
$start = [Diagnostics.ProcessStartInfo]::new()
$start.FileName = $Java
$start.WorkingDirectory = $fixture
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
$start.RedirectStandardOutput = $true
$start.RedirectStandardError = $true
$start.RedirectStandardInput = $true
@('-Xms128M', '-Xmx512M', '-Dfile.encoding=UTF-8', '-Dstdout.encoding=UTF-8', '-Dstderr.encoding=UTF-8', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'server.jar') | ForEach-Object { $start.ArgumentList.Add($_) }
$process = [Diagnostics.Process]::new()
$process.StartInfo = $start
$transcript = [Text.StringBuilder]::new()
$commands = @('pnupdate list', 'pnupdate available', 'pnupdate history', 'pnupdate backups', 'pnupdate plan')
$ready = $false
$sent = 0
$nextCommand = [datetime]::MaxValue
$timer = [Diagnostics.Stopwatch]::StartNew()
Write-Output "Isolated fixture: $fixture"
try {
    if (-not $process.Start()) { throw 'Could not start proxy JVM' }
    $errorOutput = $process.StandardError.ReadToEndAsync()
    $lineTask = $process.StandardOutput.ReadLineAsync()
    while ($timer.Elapsed.TotalSeconds -lt 100) {
        if ($lineTask.IsCompleted) {
            $line = $lineTask.GetAwaiter().GetResult()
            if ($null -ne $line) {
                [void]$transcript.AppendLine($line)
                if ($line -match $readyPattern -and -not $ready) {
                    $ready = $true
                    $nextCommand = [datetime]::UtcNow.AddSeconds(2)
                }
                $lineTask = $process.StandardOutput.ReadLineAsync()
            } elseif ($process.HasExited) { break }
        }
        if ($ready -and [datetime]::UtcNow -ge $nextCommand -and $sent -le $commands.Count) {
            if ($sent -lt $commands.Count) {
                $process.StandardInput.WriteLine($commands[$sent])
                [void]$transcript.AppendLine('>>> ' + $commands[$sent])
            } else { $process.StandardInput.WriteLine($stopCommand) }
            $process.StandardInput.Flush()
            $sent++
            $nextCommand = [datetime]::UtcNow.AddSeconds(2)
        }
        Start-Sleep -Milliseconds 20
    }
    if (-not $process.HasExited) { throw 'Proxy exceeded 100-second smoke-test timeout' }
    [void]$transcript.AppendLine($errorOutput.GetAwaiter().GetResult())
    $output = $transcript.ToString()
    [IO.File]::WriteAllText((Join-Path $fixture 'verification.log'), $output, $utf8)
    if (-not $ready) { throw 'Proxy never reached readiness; inspect verification.log' }
    if ($process.ExitCode -ne 0) { throw "Proxy exited with code $($process.ExitCode)" }
    if ($sent -ne $commands.Count + 1) { throw 'Not all verification commands were sent' }
    if ($output -notmatch 'pnLibrary' -or $output -notmatch 'No update plan|Update plan|BLOCKED|UP_TO_DATE') {
        throw 'Expected pnLibrary command output was not observed; inspect verification.log'
    }
    @('Каталог pnLibrary', 'История pnLibrary', 'Резервные наборы pnLibrary', 'pnlibrary ·') | ForEach-Object {
        if (-not $output.Contains($_)) { throw "Missing catalog response: $_; inspect verification.log" }
    }
    if ($output -match 'Unknown command|Command not found|Exception|Could not load plugin|Unable to load plugin') {
        throw 'Proxy reported a command or startup failure; inspect verification.log'
    }
    Write-Output "PASS: $Platform catalog commands and clean shutdown. Log: $(Join-Path $fixture 'verification.log')"
} finally {
    if ($process.Id -and -not $process.HasExited) {
        $process.Kill($true)
        $process.WaitForExit()
    }
    [IO.File]::WriteAllText((Join-Path $fixture 'verification.log'), $transcript.ToString(), $utf8)
    $process.Dispose()
}
