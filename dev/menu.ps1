# Консольное меню рабочего окружения pnFolder. Запуск: dev  (или .\dev.ps1)
# Стрелки или цифры выбирают пункт, Enter открывает, Esc / Backspace / стрелка влево возвращают назад.
# Экран не очищается и не перерисовывается целиком: меняются только нужные строки, поэтому мерцания нет.
$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

$Lib = Split-Path -Parent $PSScriptRoot   # репозиторий pnLibrary, эта папка лежит в нём
$Root = Split-Path -Parent $Lib            # рядом с ним лежат репозитории плагинов
$Dev = Join-Path $PSScriptRoot 'dev.ps1'
$Plugins = 'pnClans', 'pnMarket', 'pnCases', 'pnChat', 'pnSouls', 'pnRtp', 'pnTrades', 'pnBans', 'pnSeller', 'pnRelics', 'pnRelog', 'pnCaptcha', 'pnAuth'
$LocalVersion = '0.1.0-dev.local'
$TestBuild = Join-Path $PSScriptRoot 'test-build'
$ConfigFile = Join-Path $PSScriptRoot 'config.json'

# Java 21 нужна сборке плагинов; версия локальной библиотеки читается init-скриптом.
$jdk21 = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk21) { $env:JAVA_HOME = $jdk21.FullName }
$env:PN_LOCAL_VERSION = $LocalVersion

function Get-Config {
    if (Test-Path $ConfigFile) { try { return (Get-Content $ConfigFile -Raw -Encoding UTF8 | ConvertFrom-Json) } catch { } }
    return [pscustomobject]@{ ServerPlugins = '' }
}

function Save-Config($config) { $config | ConvertTo-Json | Set-Content $ConfigFile -Encoding UTF8 }

# Папка plugins тестового сервера: из настроек меню, а если не задана, из привычного места.
function Get-ServerPlugins {
    $config = Get-Config
    if ($config.ServerPlugins -and (Test-Path ([string]$config.ServerPlugins))) { return [string]$config.ServerPlugins }
    $guess = Join-Path $env:USERPROFILE 'Downloads\Server\Paper-Standalone\plugins'
    if (Test-Path $guess) { return $guess }
    return ''
}

# Если консоль не отдаёт нажатия клавиш (встроенный терминал, перенаправленный ввод), меню работает по номерам.
$script:Keys = $true
try { [void][Console]::KeyAvailable } catch { $script:Keys = $false }

# ---------- данные для шапки (обновляются только после действий, а не при каждом нажатии) ----------

function Invoke-PnGit([string]$dir, [string[]]$gitArgs) { & git -C $dir @gitArgs 2>&1 }

function Get-Branch([string]$dir) { (Invoke-PnGit $dir @('branch', '--show-current')) -join '' }

function Get-Changes([string]$dir) { @(Invoke-PnGit $dir @('status', '--short')).Count }

$script:Info = $null
$script:Pending = $null
$script:Memory = @{}
$script:Tasks = New-Object System.Collections.ArrayList
$script:NextTaskId = 1
$script:Collect = $false
$script:Specs = @()
$script:Detached = $false
$script:InTasks = $false
$script:Frame = 0
$script:LastCrumb = ''
$script:LastTask = $null
$LogDir = Join-Path $env:TEMP 'pnfolder-dev'
$script:LastSize = ''

function Update-Info {
    $m2 = Join-Path $env:USERPROFILE ".m2\repository\io\github\pnfolder\pnlibrary-api\$LocalVersion"
    $build = 'не собрана'
    if (Test-Path $m2) { $build = 'собрана ' + (Get-Item $m2).LastWriteTime.ToString('dd.MM HH:mm') }
    $script:Info = @{
        Branch = Get-Branch $Lib
        Changes = Get-Changes $Lib
        Build = $build
        Built = (Test-Path $m2)
        Guard = (Test-Path (Join-Path $Lib '.git\hooks\pre-push'))
    }
}

# ---------- рисование без мерцания ----------

function Get-Width {
    $width = 78
    try { $width = [Math]::Max(64, [Math]::Min([Console]::WindowWidth - 2, 96)) } catch { }
    return $width
}

# Один фрагмент текста: цвет текста и (необязательно) фона.
function Put([string]$text, $fg = $null, $bg = $null) {
    $p = @{ Object = $text; NoNewline = $true }
    if ($null -ne $fg) { $p.ForegroundColor = $fg }
    if ($null -ne $bg) { $p.BackgroundColor = $bg }
    Write-Host @p
}

# Строка рамки: сегменты @(текст, цвет, фон) внутри │ ... │, добивается пробелами до ширины.
# Кадр собирается в памяти и выводится только там, где он отличается от прошлого: нет мерцания и лагов.
$script:Buf = $null
$script:Prev = $null

function Begin-Frame { $script:Buf = New-Object System.Collections.ArrayList }

# Строка рамки: сегменты @(текст, цвет, фон) внутри │ ... │.
function Row($segments, [int]$width, $bg = $null) {
    if ($null -eq $script:Buf) { Begin-Frame }
    $clean = @()
    $limit = $width - 4
    $used = 0
    foreach ($s in $segments) {
        $text = [string]$s[0]
        $fg = $null; if ($s.Count -gt 1) { $fg = $s[1] }
        $back = $bg; if ($s.Count -gt 2 -and $null -ne $s[2]) { $back = $s[2] }
        if ($used + $text.Length -gt $limit) {
            $text = $text.Substring(0, [Math]::Max(0, $limit - $used))
            if ($text.Length -gt 0) { $text = $text.Substring(0, $text.Length - 1) + [string][char]0x2026 }
        }
        $clean += , @($text, $fg, $back)
        $used += $text.Length
        if ($used -ge $limit) { break }
    }
    [void]$script:Buf.Add(@{ Kind = 'row'; Segs = $clean; Width = $width; Bg = $bg; Pad = [Math]::Max(0, $limit - $used) })
}

function Rule([string]$kind, [int]$width) {
    if ($null -eq $script:Buf) { Begin-Frame }
    [void]$script:Buf.Add(@{ Kind = $kind; Width = $width })
}

function Get-RowSignature($d) {
    if ($d.Kind -ne 'row') { return ($d.Kind + '|' + $d.Width) }
    $parts = foreach ($s in $d.Segs) { '{0}~{1}~{2}' -f $s[0], $s[1], $s[2] }
    return ('row|' + $d.Width + '|' + $d.Bg + '|' + ($parts -join '¦'))
}

function Paint-Row($d, [int]$y) {
    [Console]::SetCursorPosition(0, $y)
    $win = [Console]::WindowWidth
    [Console]::ResetColor()
    if ($d.Kind -ne 'row') {
        $h = [string][char]0x2500
        $left = [string][char]0x251C; $right = [string][char]0x2524
        if ($d.Kind -eq 'top') { $left = [string][char]0x250C; $right = [string][char]0x2510 }
        if ($d.Kind -eq 'bottom') { $left = [string][char]0x2514; $right = [string][char]0x2518 }
        [Console]::ForegroundColor = 'DarkCyan'
        [Console]::Write($left + ($h * ($d.Width - 2)) + $right)
    } else {
        [Console]::ForegroundColor = 'DarkCyan'
        [Console]::Write([string][char]0x2502 + ' ')
        foreach ($s in $d.Segs) {
            [Console]::ResetColor()
            if ($null -ne $s[2]) { [Console]::BackgroundColor = $s[2] }
            if ($null -ne $s[1]) { [Console]::ForegroundColor = $s[1] }
            [Console]::Write($s[0])
        }
        [Console]::ResetColor()
        if ($null -ne $d.Bg) { [Console]::BackgroundColor = $d.Bg }
        [Console]::Write(' ' * $d.Pad)
        [Console]::ResetColor()
        [Console]::ForegroundColor = 'DarkCyan'
        [Console]::Write(' ' + [string][char]0x2502)
    }
    [Console]::ResetColor()
    $tail = $win - $d.Width - 1
    if ($tail -gt 0) { [Console]::Write(' ' * $tail) }
}

function Blank-Rows([int]$from, [int]$to) {
    $blank = ' ' * ([Console]::WindowWidth - 1)
    for ($y = $from; $y -le $to; $y++) { [Console]::SetCursorPosition(0, $y); [Console]::Write($blank) }
}

# Выводит собранный кадр. $Static: после кадра идёт обычный вывод, поэтому кадр потом перерисуется целиком.
function End-Frame([switch]$Static) {
    $rows = $script:Buf
    $script:Buf = $null
    if ($null -eq $rows -or -not $script:Keys) { return }
    try {
        $size = [string][Console]::WindowWidth + 'x' + [Console]::WindowHeight
        if ($size -ne $script:LastSize) { $script:LastSize = $size; $script:Prev = $null; Clear-Host }
        $top = [Console]::WindowTop
        $bottom = $top + [Console]::WindowHeight - 1
        $count = [Math]::Min($rows.Count, [Console]::WindowHeight - 1)
        $signatures = @()
        for ($i = 0; $i -lt $count; $i++) {
            $signature = Get-RowSignature $rows[$i]
            $signatures += $signature
            if ($null -ne $script:Prev -and $i -lt $script:Prev.Count -and $script:Prev[$i] -eq $signature) { continue }
            Paint-Row $rows[$i] ($top + $i)
        }
        if ($null -eq $script:Prev) { Blank-Rows ($top + $count) $bottom }
        elseif ($script:Prev.Count -gt $count) { Blank-Rows ($top + $count) ($top + $script:Prev.Count - 1) }
        $script:Prev = $signatures
        if ($Static) { $script:Prev = $null }
        [Console]::SetCursorPosition(0, $top + $count)
    } catch { $script:Prev = $null }
}

function Move-Top { if ($script:Keys) { try {
        $size = [string][Console]::WindowWidth + 'x' + [Console]::WindowHeight
        if ($size -ne $script:LastSize) { $script:LastSize = $size; Clear-Host }
        [Console]::SetCursorPosition(0, [Console]::WindowTop)
    } catch { } } }

# Затирает пробелами всё, что осталось ниже рамки от прошлого экрана, и ставит курсор под рамку.
function Clear-Below([int]$topRow) {
    if (-not $script:Keys) { return }
    try {
        $height = [Console]::WindowHeight
        $width = [Console]::WindowWidth
        [Console]::SetCursorPosition(0, $topRow)
        $rows = [Math]::Max(0, [Console]::WindowTop + $height - $topRow - 1)
        for ($i = 0; $i -lt $rows; $i++) { [Console]::Out.Write((' ' * ($width - 1)) + "`n") }
        [Console]::SetCursorPosition(0, $topRow)
    } catch { }
}

function Draw-Top([string]$crumb, [int]$width) {
    $info = $script:Info
    Rule 'top' $width
    Row @(@(' pnFolder ', 'Black', 'Cyan'), @('  рабочее окружение, релизов нет', 'DarkGray')) $width
    $changeColor = 'Green'; if ($info.Changes -gt 0) { $changeColor = 'Yellow' }
    $buildColor = 'DarkGray'; if ($info.Built) { $buildColor = 'Green' }
    $guardText = 'защита выкл'; $guardColor = 'Yellow'
    if ($info.Guard) { $guardText = 'защита вкл'; $guardColor = 'Green' }
    Row @(@('ветка ', 'DarkGray'), @($info.Branch, 'Green'), @('   изменений ', 'DarkGray'), @([string]$info.Changes, $changeColor)) $width
    Row @(@('сборка ', 'DarkGray'), @($info.Build, $buildColor), @('   ', 'DarkGray'), @($guardText, $guardColor)) $width
    $taskSegments = Get-TaskSegments
    if ($null -ne $taskSegments) { Row $taskSegments $width }
    Rule 'mid' $width
    $segments = @()
    $parts = $crumb -split '  »  '
    $sep = ' ' + [string][char]0x203A + ' '
    for ($i = 0; $i -lt $parts.Count; $i++) {
        if ($i -gt 0) { $segments += , @($sep, 'DarkGray') }
        $partColor = 'DarkCyan'; if ($i -eq $parts.Count - 1) { $partColor = 'White' }
        $segments += , @([string]$parts[$i], $partColor)
    }
    Row $segments $width
}

# Делит строки «Название|пояснение» на названия и пояснения.
function Split-Items([string[]]$items) {
    $titles = @(); $details = @()
    foreach ($item in $items) {
        $parts = $item -split '\|', 2
        $titles += $parts[0]
        if ($parts.Count -gt 1) { $details += $parts[1] } else { $details += '' }
    }
    return @{ Titles = $titles; Details = $details }
}

# Меню на весь экран. $items: строки «Название|пояснение». Возвращает индекс или -1 («назад»).
# $live: необязательный блок, который заново отдаёт строки (для списков, которые меняются сами).
function Select-Item([string]$crumb, [string[]]$items, [string]$hint = '', [int]$start = 0, [scriptblock]$live = $null) {
    if ($live) { $items = @(& $live) }
    $split = Split-Items $items
    $titles = $split.Titles; $details = $split.Details
    if (-not $script:Keys) {
        Clear-Host
        Write-Host "  pnFolder · $crumb" -ForegroundColor Cyan
        Write-Host ('  ветка ' + $script:Info.Branch + ', изменений: ' + $script:Info.Changes) -ForegroundColor DarkGray
        Write-Host ''
        for ($i = 0; $i -lt $titles.Count; $i++) { Write-Host ('    {0,2}. {1}   {2}' -f ($i + 1), $titles[$i], $details[$i]) }
        Write-Host ''
        if ($hint) { Write-Host "  $hint" -ForegroundColor DarkGray }
        $typed = Read-Host '  Номер пункта (Enter или 0 = назад)'
        $number = 0
        if (-not [int]::TryParse($typed, [ref]$number) -or $number -lt 1 -or $number -gt $titles.Count) { return -1 }
        return $number - 1
    }
    $memKey = ($crumb -split '  \u00BB  ')[-1] + '#' + $crumb.Length
    $index = 0
    if ($start -gt 0) { $index = $start } elseif ($script:Memory.ContainsKey($memKey)) { $index = $script:Memory[$memKey] }
    $index = [Math]::Max(0, [Math]::Min($index, $titles.Count - 1))
    $offset = 0
    $script:Prev = $null
    [Console]::CursorVisible = $false
    $dirty = $true
    try {
        while ($true) {
            if ($dirty) {
                if ($live) {
                    $split = Split-Items @(& $live)
                    $titles = $split.Titles; $details = $split.Details
                    if ($index -ge $titles.Count) { $index = [Math]::Max(0, $titles.Count - 1) }
                }
                $nameWidth = ($titles | ForEach-Object { $_.Length } | Measure-Object -Maximum).Maximum
                $width = Get-Width
                $visible = [Math]::Max(5, [Console]::WindowHeight - 17)
                if ($index -lt $offset) { $offset = $index }
                if ($index -ge $offset + $visible) { $offset = $index - $visible + 1 }
                if ($titles.Count -le $visible) { $offset = 0 }
                $last = [Math]::Min($titles.Count, $offset + $visible)
                Begin-Frame
                Draw-Top $crumb $width
                Row @(, @('', 'Gray')) $width
                if ($offset -gt 0) { Row @(, @(('    ' + [char]0x25B2 + ' ещё ' + $offset + ' выше'), 'DarkGray')) $width }
                for ($i = $offset; $i -lt $last; $i++) {
                    $number = '  '; if ($i -lt 9) { $number = [string]($i + 1) + ' ' }
                    $title = $titles[$i].PadRight($nameWidth)
                    $desc = "   $($details[$i])"
                    $arrow = [string][char]0x25BA + ' '
                    if ($i -eq $index) {
                        Row @(@($arrow, 'Black', 'Cyan'), @($number, 'Black', 'Cyan'), @(' ', 'Black', 'Cyan'), @($title, 'Black', 'Cyan'), @($desc, 'DarkBlue', 'Cyan')) $width 'Cyan'
                    } else {
                        Row @(@('  ', 'Gray'), @($number, 'DarkGray'), @(' ', 'Gray'), @($title, 'White'), @($desc, 'DarkGray')) $width
                    }
                }
                if ($last -lt $titles.Count) { Row @(, @(('    ' + [char]0x25BC + ' ещё ' + ($titles.Count - $last) + ' ниже'), 'DarkGray')) $width }
                Row @(, @('', 'Gray')) $width
                if ($hint) { Row @(, @($hint, 'DarkYellow')) $width }
                Rule 'mid' $width
                $counter = '{0}/{1}' -f ($index + 1), $titles.Count
                $tasksHint = ''; if (-not $script:InTasks) { $tasksHint = '   T  задачи' }
                Row @(@('↑↓', 'Cyan'), @(' выбор   ', 'DarkGray'), @('Enter', 'Cyan'), @(' открыть   ', 'DarkGray'), @('Esc', 'Cyan'), @(' назад   ', 'DarkGray'), @('1-9', 'Cyan'), @(' быстрый выбор', 'DarkGray'), @($tasksHint, 'Cyan'), @(('   ' + $counter), 'DarkGray')) $width
                Rule 'bottom' $width
                End-Frame
                $dirty = $false
            }
            $key = $null
            if ($null -ne $script:Pending) { $key = $script:Pending; $script:Pending = $null }
            else {
                $tick = 0
                while ($null -eq $key) {
                    if ([Console]::KeyAvailable) { $key = [Console]::ReadKey($true); break }
                    Start-Sleep -Milliseconds 50
                    $tick++
                    $changed = Update-Tasks
                    $running = Test-TaskRunning
                    if ($changed -or ($running -and $tick % 5 -eq 0)) { $script:Frame++; $dirty = $true; break }
                }
                if ($null -eq $key) { continue }
            }
            # Удержание стрелки копит очередь нажатий, и список «едет» после того, как клавишу отпустили.
            # Подряд идущие повторы стрелок выбрасываются, первая другая клавиша сохраняется.
            while ([Console]::KeyAvailable) {
                $next = [Console]::ReadKey($true)
                if ($next.Key -eq 'UpArrow' -or $next.Key -eq 'DownArrow') { continue }
                $script:Pending = $next
                break
            }
            $dirty = $true
            switch ($key.Key) {
                'UpArrow' { if ($index -gt 0) { $index-- } else { $index = $titles.Count - 1 } }
                'DownArrow' { if ($index -lt $titles.Count - 1) { $index++ } else { $index = 0 } }
                'PageUp' { $index = [Math]::Max(0, $index - $visible) }
                'PageDown' { $index = [Math]::Min($titles.Count - 1, $index + $visible) }
                'Home' { $index = 0 }
                'End' { $index = $titles.Count - 1 }
                'Enter' { $script:Memory[$memKey] = $index; return $index }
                'RightArrow' { $script:Memory[$memKey] = $index; return $index }
                'Escape' { return -1 }
                'Backspace' { return -1 }
                'LeftArrow' { return -1 }
                default {
                    $ch = [string]$key.KeyChar
                    if ($ch -match '^[tTеЕ]$') {
                        if (-not $script:InTasks) { Menu-Tasks; [Console]::CursorVisible = $false; $script:Prev = $null }
                    } elseif ($ch -ge '1' -and $ch -le '9') {
                        $n = [int]::Parse($ch) - 1
                        if ($n -lt $titles.Count) { $script:Memory[$memKey] = $n; return $n }
                    }
                }
            }
        }
    } finally { [Console]::CursorVisible = $true }
}

# Шапка для экрана с результатом действия: рамка сверху, вывод команды под ней.
function Show-Action([string]$crumb) {
    $script:LastCrumb = $crumb
    if (-not $script:Keys) { Clear-Host; Write-Host "  $crumb" -ForegroundColor Cyan; Write-Host ''; return }
    $width = Get-Width
    $script:Prev = $null
    Begin-Frame
    Draw-Top $crumb $width
    Rule 'bottom' $width
    End-Frame -Static
    Write-Host ''
}

function Wait-Key {
    Write-Host ''
    if ($script:Keys) {
        Write-Host '  Нажми любую клавишу, чтобы вернуться...' -ForegroundColor DarkGray
        [void][Console]::ReadKey($true)
    } else {
        [void](Read-Host 'Нажми Enter, чтобы вернуться')
    }
    Update-Info
}

function Ask([string]$question) {
    Write-Host ''
    if ($script:Keys) { [Console]::CursorVisible = $true }
    Read-Host "  $question"
}

function Confirm-Action([string]$question) {
    Write-Host ''
    if ($script:Keys) {
        Write-Host "  $question (д/н) " -ForegroundColor Yellow -NoNewline
        $key = [Console]::ReadKey($true)
        Write-Host ''
        $answer = [string]$key.KeyChar
    } else {
        $answer = Read-Host "  $question (д/н)"
    }
    return ($answer -match '^(д|Д|y|Y)')
}

function Say([string]$text, $color = 'Gray') { Write-Host "  $text" -ForegroundColor $color }

function Say-All($lines) { foreach ($line in $lines) { Write-Host "  $line" } }

function Run-Dev([string]$command, [string[]]$more = @()) {
    if ($script:Collect) {
        $script:Specs += , @{ File = 'powershell.exe'; Args = (@('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $Dev, $command) + $more); Dir = $Root }
        return
    }
    Write-Host ''
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Dev $command @more | Out-Host
    $script:LastExit = $LASTEXITCODE
}

function Gradle-Run([string]$dir, [string[]]$gradleArgs) {
    if ($script:Collect) {
        $script:Specs += , @{ File = 'cmd.exe'; Args = (@('/c', (Join-Path $dir 'gradlew.bat')) + $gradleArgs + @('--console=plain')); Dir = $dir }
        return
    }
    Push-Location $dir
    & .\gradlew.bat @gradleArgs '--console=plain' | Out-Host
    $script:LastExit = $LASTEXITCODE
    Pop-Location
}

# ---------- фоновые задачи ----------

function Quote-Arg([string]$value) {
    if ($value -match '[\s"]') { return '"' + ($value -replace '"', '\"') + '"' }
    return $value
}

# Сколько задач Gradle обычно выполняет эта сборка: по прошлым запускам считаются проценты.
function Get-TaskStat([string]$label) {
    $config = Get-Config
    if ($config.PSObject.Properties['Stats'] -and $config.Stats.PSObject.Properties[$label]) { return [int]$config.Stats.$label }
    return 0
}

function Save-TaskStat([string]$label, [int]$count) {
    if ($count -lt 3) { return }
    $config = Get-Config
    if (-not $config.PSObject.Properties['Stats']) { $config | Add-Member -NotePropertyName Stats -NotePropertyValue ([pscustomobject]@{}) }
    $config.Stats | Add-Member -NotePropertyName $label -NotePropertyValue $count -Force
    Save-Config $config
}

function New-Task([string]$label, $specs, [string]$after, $context) {
    New-Item -ItemType Directory -Force $LogDir | Out-Null
    $task = [pscustomobject]@{
        Id = $script:NextTaskId; Title = $label; Queue = @($specs); Index = 0; Process = $null
        Status = 'running'; ExitCode = 0; Start = Get-Date; End = $null; Seen = $false; Cancelled = $false
        Logs = @(); Pos = @{}; Partial = @{}; Tail = (New-Object System.Collections.ArrayList)
        TaskLines = 0; Last = ''; Expected = (Get-TaskStat $label); After = $after; Context = $context
        Report = ''; Files = @()
    }
    $script:NextTaskId++
    [void]$script:Tasks.Add($task)
    Start-Spec $task
    return $task
}

function Start-Spec($task) {
    $spec = $task.Queue[$task.Index]
    $out = Join-Path $LogDir ('task{0}-{1}.log' -f $task.Id, $task.Index)
    $err = $out + '.err'
    $argText = ($spec.Args | ForEach-Object { Quote-Arg ([string]$_) }) -join ' '
    try {
        $task.Process = Start-Process -FilePath $spec.File -ArgumentList $argText -WorkingDirectory $spec.Dir -RedirectStandardOutput $out -RedirectStandardError $err -WindowStyle Hidden -PassThru
        $null = $task.Process.Handle
        $task.Logs += $out
        $task.Logs += $err
    } catch {
        $task.Status = 'failed'; $task.ExitCode = -1; $task.End = Get-Date
        [void]$task.Tail.Add('Не удалось запустить: ' + $_.Exception.Message)
    }
}

# Дочитывает новые строки лога задачи (файл пишет сам процесс).
function Read-NewLines($task, [string]$path) {
    if (-not (Test-Path $path)) { return }
    $pos = [long]0
    if ($task.Pos.ContainsKey($path)) { $pos = [long]$task.Pos[$path] }
    $bytes = $null; $count = 0
    try {
        $fs = [IO.File]::Open($path, 'Open', 'Read', 'ReadWrite')
        try {
            if ($fs.Length -le $pos) { return }
            [void]$fs.Seek($pos, 'Begin')
            $bytes = New-Object byte[] ($fs.Length - $pos)
            $count = $fs.Read($bytes, 0, $bytes.Length)
            $task.Pos[$path] = $pos + $count
        } finally { $fs.Dispose() }
    } catch { return }
    $partial = ''; if ($task.Partial.ContainsKey($path)) { $partial = [string]$task.Partial[$path] }
    $lines = ($partial + [Text.Encoding]::UTF8.GetString($bytes, 0, $count)) -split "`r?`n"
    $task.Partial[$path] = $lines[$lines.Count - 1]
    for ($i = 0; $i -lt $lines.Count - 1; $i++) {
        $line = ([string]$lines[$i]).Replace("`t", '    ')
        if ($line.Trim() -eq '') { continue }
        if ($line -match '^> Task ') { $task.TaskLines++ }
        $task.Last = $line.Trim()
        [void]$task.Tail.Add($line)
        if ($task.Tail.Count -gt 50000) { $task.Tail.RemoveAt(0) }
    }
}

function Get-TaskElapsed($task) {
    $end = Get-Date; if ($task.End) { $end = $task.End }
    return ($end - $task.Start).ToString('mm\:ss')
}

# Проценты по прошлым запускам; $null, если сборка запускается впервые.
function Get-Percent($task) {
    if ($task.Status -eq 'done') { return 100 }
    if ($task.Expected -le 0) { return $null }
    return [Math]::Min(99, [int][Math]::Floor($task.TaskLines * 100 / $task.Expected))
}

function Test-TaskRunning { return (@($script:Tasks | Where-Object { $_.Status -eq 'running' }).Count -gt 0) }

# Обновляет одну задачу. Возвращает $true, если она сменила состояние.
function Update-Task($task) {
    if ($task.Status -ne 'running') { return $false }
    foreach ($log in @($task.Logs)) { Read-NewLines $task $log }
    if (-not $task.Process.HasExited) { return $false }
    foreach ($log in @($task.Logs)) { Read-NewLines $task $log }
    $code = $task.Process.ExitCode
    if ($code -ne 0) { $task.Status = 'failed'; $task.ExitCode = $code; $task.End = Get-Date; return $true }
    $task.Index++
    if ($task.Index -lt $task.Queue.Count) { Start-Spec $task; return $true }
    $task.Status = 'done'; $task.End = Get-Date
    Save-TaskStat $task.Title $task.TaskLines
    if ($task.After) {
        try { $task.Report = ((& $task.After $task) | Out-String).TrimEnd() } catch { $task.Report = 'Ошибка после сборки: ' + $_.Exception.Message }
    }
    Update-Info
    return $true
}

function Update-Tasks {
    $changed = $false
    foreach ($task in @($script:Tasks)) { if (Update-Task $task) { $changed = $true } }
    return $changed
}

function Stop-Task($task) {
    if ($task.Status -ne 'running') { return }
    $task.Cancelled = $true
    try { & taskkill.exe /PID $task.Process.Id /T /F 2>&1 | Out-Null } catch { }
    $task.Status = 'cancelled'; $task.End = Get-Date
}

# Строка шапки: что собирается сейчас и что закончилось, пока ты был в меню.
function Get-TaskSegments {
    $running = @($script:Tasks | Where-Object { $_.Status -eq 'running' })
    $unseen = @($script:Tasks | Where-Object { $_.Status -ne 'running' -and -not $_.Seen })
    if ($running.Count -eq 0 -and $unseen.Count -eq 0) { return $null }
    $frames = @('|', '/', '-', '\')
    $frame = $frames[$script:Frame % 4]
    $segments = @()
    $segments += , @('задачи  ', 'DarkGray')
    foreach ($task in @($running | Select-Object -First 2)) {
        $percent = Get-Percent $task
        $percentText = '...'; if ($null -ne $percent) { $percentText = [string]$percent + '%' }
        $text = '[' + $frame + '] ' + $task.Title + ' ' + $percentText + ' ' + (Get-TaskElapsed $task) + '   '
        $segments += , @($text, 'Yellow')
    }
    if ($running.Count -gt 2) { $segments += , @(('+' + ($running.Count - 2) + '   '), 'Yellow') }
    foreach ($task in @($unseen | Select-Object -First 2)) {
        if ($task.Status -eq 'done') { $segments += , @(('[OK] ' + $task.Title + '   '), 'Green') }
        elseif ($task.Status -eq 'cancelled') { $segments += , @(('[отмена] ' + $task.Title + '   '), 'DarkGray') }
        else { $segments += , @(('[ОШИБКА] ' + $task.Title + '   '), 'Red') }
    }
    $segments += , @('T  открыть', 'Cyan')
    return $segments
}

function Draw-Bar([int]$percentValue, [int]$cells) {
    $filled = [Math]::Floor($cells * $percentValue / 100)
    return ([string][char]0x2588 * $filled) + ([string][char]0x2591 * ($cells - $filled))
}

function Task-Icon($task) {
    switch ($task.Status) {
        'running' { return '[..]' }
        'done' { return '[OK]' }
        'cancelled' { return '[--]' }
        default { return '[!!]' }
    }
}

# Цвет строки лога: ошибки красные, предупреждения жёлтые, итог сборки зелёный.
function Get-LineColor([string]$line) {
    if ($line -match '(?i)\berror\b|FAILED|BUILD FAILED|ОШИБКА|Exception|^e: ') { return 'Red' }
    if ($line -match '(?i)\bwarning\b|^w: ') { return 'Yellow' }
    if ($line -match 'BUILD SUCCESSFUL') { return 'Green' }
    if ($line -match '^> Task ') { return 'DarkCyan' }
    return 'Gray'
}

# Экран выполняющейся (или завершённой) задачи. Esc / ← прячут её в фон, C отменяет, L открывает весь лог.
function Watch-Task($task, [string]$crumb = '') {
    if (-not $crumb) { $crumb = $script:LastCrumb }
    if (-not $script:Keys) {
        Write-Host ''
        Write-Host ('  [>] ' + $task.Title) -ForegroundColor Cyan
        while ($task.Status -eq 'running') { Start-Sleep -Milliseconds 500; [void](Update-Tasks) }
        $task.Seen = $true
        Show-TaskResult $task
        return
    }
    [Console]::CursorVisible = $false
    $script:Prev = $null
    $frames = @('|', '/', '-', '\')
    try {
        while ($true) {
            [void](Update-Tasks)
            $script:Frame++
            $width = Get-Width
            $state = $frames[$script:Frame % 4]
            $color = 'Cyan'
            if ($task.Status -eq 'done') { $state = 'OK'; $color = 'Green' }
            elseif ($task.Status -eq 'failed') { $state = '!!'; $color = 'Red' }
            elseif ($task.Status -eq 'cancelled') { $state = '--'; $color = 'DarkGray' }
            Begin-Frame
            Draw-Top $crumb $width
            Row @(, @('', 'Gray')) $width
            Row @(@(('[' + $state + '] '), $color), @($task.Title, 'White'), @(('   ' + (Get-TaskElapsed $task)), 'DarkGray')) $width
            $percent = Get-Percent $task
            $barWidth = [Math]::Max(10, $width - 20)
            if ($null -ne $percent) {
                Row @(@(('    ' + (Draw-Bar $percent $barWidth) + ' '), $color), @(([string]$percent + '%'), 'White')) $width
            } else {
                $cells = @(' ') * $barWidth
                $spot = ($script:Frame * 2) % $barWidth
                for ($k = 0; $k -lt 6; $k++) { $cells[($spot + $k) % $barWidth] = [string][char]0x2588 }
                Row @(@(('    ' + ($cells -join '') + ' '), $color), @(('задач: ' + $task.TaskLines), 'DarkGray')) $width
            }
            $stepText = ''
            if ($task.Queue.Count -gt 1) { $stepText = '    шаг ' + [Math]::Min($task.Index + 1, $task.Queue.Count) + ' из ' + $task.Queue.Count + '   ' }
            if ($null -eq $percent -and $task.Status -eq 'running') { $stepText += 'проценты появятся после первого запуска' }
            Row @(, @($stepText, 'DarkGray')) $width
            Rule 'mid' $width
            $shown = [Math]::Max(5, [Math]::Min(16, [Console]::WindowHeight - 20))
            $lines = @($task.Tail | Select-Object -Last $shown)
            for ($k = 0; $k -lt $shown; $k++) {
                if ($k -lt $lines.Count) { Row @(, @(([string]$lines[$k]), (Get-LineColor ([string]$lines[$k])))) $width }
                else { Row @(, @('', 'Gray')) $width }
            }
            Rule 'mid' $width
            if ($task.Status -eq 'running') {
                Row @(@('Esc', 'Cyan'), @(' в фон   ', 'DarkGray'), @('L', 'Cyan'), @(' весь лог   ', 'DarkGray'), @('C', 'Cyan'), @(' отменить', 'DarkGray')) $width
            } else {
                Row @(@('Enter', 'Cyan'), @(' закрыть   ', 'DarkGray'), @('L', 'Cyan'), @(' весь лог с прокруткой', 'DarkGray')) $width
            }
            Rule 'bottom' $width
            End-Frame
            $key = $null
            for ($k = 0; $k -lt 5 -and -not [Console]::KeyAvailable; $k++) { Start-Sleep -Milliseconds 50 }
            if ([Console]::KeyAvailable) { $key = [Console]::ReadKey($true) }
            while ([Console]::KeyAvailable) { [void][Console]::ReadKey($true) }
            if ($null -eq $key) { continue }
            $ch = [string]$key.KeyChar
            if ($ch -match '^[lLдД]$') { Show-Log $task; [Console]::CursorVisible = $false; $script:Prev = $null; continue }
            if ($task.Status -eq 'running') {
                if ($key.Key -eq 'Escape' -or $key.Key -eq 'LeftArrow' -or $key.Key -eq 'Backspace') { $script:Detached = $true; return }
                if ($ch -match '^[cCсС]$') {
                    if (Confirm-Action 'Отменить задачу?') { Stop-Task $task }
                    $script:Prev = $null
                }
            } else {
                if ($key.Key -eq 'Enter' -or $key.Key -eq 'Escape' -or $key.Key -eq 'Backspace' -or $key.Key -eq 'LeftArrow') { break }
            }
        }
    } finally { [Console]::CursorVisible = $true }
    $task.Seen = $true
    $script:Prev = $null
    Show-TaskResult $task
}

# Просмотр всего лога с прокруткой. Идущая задача дописывается на лету.
function Show-Log($task) {
    if (-not $script:Keys) { foreach ($line in $task.Tail) { Write-Host "  $line" }; return }
    $crumb = 'Главное меню  »  Задачи  »  ' + $task.Title + '  »  лог'
    $top = 0
    $follow = ($task.Status -eq 'running')
    $script:Prev = $null
    [Console]::CursorVisible = $false
    $dirty = $true
    $knownCount = -1
    try {
        while ($true) {
            $changed = Update-Tasks
            $lines = $task.Tail.ToArray()
            if ($lines.Count -ne $knownCount -or $changed) { $dirty = $true; $knownCount = $lines.Count }
            if ($dirty) {
                $width = Get-Width
                $view = [Math]::Max(5, [Console]::WindowHeight - 12)
                $maxTop = [Math]::Max(0, $lines.Count - $view)
                if ($follow) { $top = $maxTop }
                $top = [Math]::Max(0, [Math]::Min($top, $maxTop))
                Begin-Frame
                Draw-Top $crumb $width
                for ($k = 0; $k -lt $view; $k++) {
                    $n = $top + $k
                    if ($n -lt $lines.Count) { Row @(, @(([string]$lines[$n]), (Get-LineColor ([string]$lines[$n])))) $width }
                    else { Row @(, @('', 'Gray')) $width }
                }
                Rule 'mid' $width
                $from = 0; if ($lines.Count -gt 0) { $from = $top + 1 }
                $to = [Math]::Min($lines.Count, $top + $view)
                $followText = 'выкл'; if ($follow) { $followText = 'вкл' }
                Row @(@('↑↓ PgUp PgDn Home End', 'Cyan'), @(' листать   ', 'DarkGray'), @('E', 'Cyan'), @(' ошибка   ', 'DarkGray'), @('F', 'Cyan'), @((' следить (' + $followText + ')   '), 'DarkGray'), @('S', 'Cyan'), @(' блокнот   ', 'DarkGray'), @('Esc', 'Cyan'), @(' назад   ', 'DarkGray'), @(('строки ' + $from + '-' + $to + ' из ' + $lines.Count), 'DarkGray')) $width
                Rule 'bottom' $width
                End-Frame
                $dirty = $false
            }
            $key = $null
            if ([Console]::KeyAvailable) { $key = [Console]::ReadKey($true) }
            else { Start-Sleep -Milliseconds 80; continue }
            if ($key.Key -eq 'UpArrow' -or $key.Key -eq 'DownArrow') { while ([Console]::KeyAvailable) { [void][Console]::ReadKey($true) } }
            $dirty = $true
            $view = [Math]::Max(5, [Console]::WindowHeight - 12)
            $ch = [string]$key.KeyChar
            switch ($key.Key) {
                'UpArrow' { $follow = $false; $top = [Math]::Max(0, $top - 1) }
                'DownArrow' { $top++ }
                'PageUp' { $follow = $false; $top = [Math]::Max(0, $top - $view + 1) }
                'PageDown' { $top += $view - 1 }
                'Home' { $follow = $false; $top = 0 }
                'End' { $follow = ($task.Status -eq 'running'); $top = [Math]::Max(0, $lines.Count - $view) }
                'Escape' { return }
                'Backspace' { return }
                'LeftArrow' { return }
                'Enter' { return }
                default {
                    if ($ch -match '^[qQйЙ]$') { return }
                    elseif ($ch -match '^[fFаА]$') { $follow = -not $follow }
                    elseif ($ch -match '^[sSыЫ]$') { Open-TaskLog $task }
                    elseif ($ch -match '^[eEуУ]$') {
                        $follow = $false
                        $found = -1
                        for ($n = $top + 1; $n -lt $lines.Count; $n++) { if ((Get-LineColor ([string]$lines[$n])) -eq 'Red') { $found = $n; break } }
                        if ($found -lt 0) { for ($n = 0; $n -le $top -and $n -lt $lines.Count; $n++) { if ((Get-LineColor ([string]$lines[$n])) -eq 'Red') { $found = $n; break } } }
                        if ($found -ge 0) { $top = $found }
                    }
                }
            }
        }
    } finally { [Console]::CursorVisible = $true; $script:Prev = $null }
}

# Полный лог одним файлом в блокноте.
function Open-TaskLog($task) {
    New-Item -ItemType Directory -Force $LogDir | Out-Null
    $path = Join-Path $LogDir ('task{0}-full.txt' -f $task.Id)
    $task.Tail.ToArray() | Set-Content $path -Encoding UTF8
    Start-Process notepad.exe $path
}

# Итог под рамкой: успех или причина ошибки.
function Show-TaskResult($task) {
    Write-Host ''
    $time = Get-TaskElapsed $task
    if ($task.Status -eq 'done') { Write-Host "  [OK] Готово за $time" -ForegroundColor Green }
    elseif ($task.Status -eq 'cancelled') { Write-Host "  [--] Отменено ($time)" -ForegroundColor DarkGray }
    else {
        Write-Host "  [ОШИБКА] Не получилось (код $($task.ExitCode), $time). Последние строки:" -ForegroundColor Red
        foreach ($line in @($task.Tail | Select-Object -Last 25)) { Write-Host "    $line" -ForegroundColor DarkGray }
    }
    if ($task.Report) { Write-Host ''; foreach ($line in ($task.Report -split "`r?`n")) { Write-Host "  $line" } }
}

# Запускает сборку в фоне и показывает её ход. Возвращает $true при успехе.
# Если пользователь ушёл в меню, сборка продолжается, $script:Detached = $true, возвращается $false.
function Step([string]$label, [scriptblock]$action, [string]$after = '', $context = $null) {
    $script:Specs = @()
    $script:Collect = $true
    try { [void](& $action) } finally { $script:Collect = $false }
    $task = New-Task $label $script:Specs $after $context
    $script:LastTask = $task
    $script:Detached = $false
    Watch-Task $task
    if ($script:Detached) { return $false }
    return ($task.Status -eq 'done')
}

# ---------- экран «Задачи» ----------

function Menu-Task($task) {
    $crumb = 'Главное меню  »  Задачи  »  ' + $task.Title
    while ($true) {
        $items = @()
        if ($task.Status -eq 'running') { $items += 'Открыть|смотреть ход, можно уйти в фон'; $items += 'Лог с прокруткой|весь вывод, ошибки красным'; $items += 'Отменить|остановить сборку' }
        else {
            $items += 'Итог|что получилось, последние строки'
            $items += 'Лог с прокруткой|весь вывод, ошибки красным, переход к ошибке на E'
            $items += 'Полный лог в блокноте|открыть файл'
            if (@($task.Files).Count -gt 0) { $items += 'Положить на сервер|собранные jar' }
            $items += 'Убрать из списка|только запись, файлы остаются'
        }
        $pick = Select-Item $crumb $items
        if ($pick -eq -1) { return }
        $name = ($items[$pick] -split '\|')[0]
        switch ($name) {
            'Открыть' { Watch-Task $task $crumb; if ($script:Detached) { $script:Detached = $false; continue } ; Wait-Key }
            'Отменить' { if (Confirm-Action 'Отменить задачу?') { Stop-Task $task }; return }
            'Итог' { Show-Action $crumb; $task.Seen = $true; Show-TaskResult $task; Wait-Key }
            'Лог с прокруткой' { Show-Log $task }
            'Полный лог в блокноте' { Open-TaskLog $task }
            'Положить на сервер' { Show-Action $crumb; Deploy-Jars @($task.Files); Wait-Key }
            'Убрать из списка' { $script:Tasks.Remove($task); return }
        }
    }
}

function Menu-Tasks {
    $script:InTasks = $true
    $crumb = 'Главное меню  »  Задачи'
    try {
        while ($true) {
            if ($script:Tasks.Count -eq 0) {
                Show-Action $crumb
                Say 'Задач пока нет. Сборки, которые ты запустишь, появятся здесь и в шапке.' Yellow
                Wait-Key
                return
            }
            $live = {
                $rows = @()
                foreach ($task in @($script:Tasks | Sort-Object Id -Descending)) {
                    $percent = Get-Percent $task
                    $detail = Get-TaskElapsed $task
                    if ($task.Status -eq 'running') { if ($null -ne $percent) { $detail = [string]$percent + '%   ' + $detail } else { $detail = 'идёт   ' + $detail } }
                    elseif ($task.Status -eq 'done') { $detail = 'готово за ' + $detail }
                    elseif ($task.Status -eq 'cancelled') { $detail = 'отменено' }
                    else { $detail = 'ошибка, код ' + $task.ExitCode }
                    $rows += ((Task-Icon $task) + ' #' + $task.Id + ' ' + $task.Title + '|' + $detail)
                }
                $rows += 'Убрать завершённые|очистить список'
                $rows
            }
            $pick = Select-Item $crumb @() 'Enter открывает задачу: смотреть ход, отменить, взять результат.' 0 $live
            if ($pick -eq -1) { return }
            $ordered = @($script:Tasks | Sort-Object Id -Descending)
            if ($pick -ge $ordered.Count) {
                foreach ($task in $ordered) { if ($task.Status -ne 'running') { $script:Tasks.Remove($task) } }
                continue
            }
            Menu-Task $ordered[$pick]
        }
    } finally { $script:InTasks = $false }
}

# ---------- ветки и изменения ----------

function Menu-Branch {
    $crumb = 'Главное меню  »  Ветка и изменения'
    :menu while ($true) {
        $pick = Select-Item $crumb @(
            'Что изменилось|список файлов и размер правок',
            'Сохранить у себя|локальный коммит, на GitHub не уходит',
            'Отменить последний коммит|правки останутся в файлах',
            'Переключить ветку|между ветками этого репозитория',
            'Новая ветка|создать от текущей и перейти в неё',
            'История|последние 20 коммитов',
            'Спрятать правки|stash: убрать на потом, не теряя',
            'Вернуть спрятанные|достать последний stash',
            'Выбросить все правки|без возврата, с подтверждением'
        ) 'Коммиты остаются у тебя. На GitHub уходит только то, что отправишь сам.'
        if ($pick -eq -1) { return }
        Show-Action ($crumb + '  »  действие')
        switch ($pick) {
            0 {
                if ((Get-Changes $Lib) -eq 0) { Say 'Изменений нет, всё сохранено.' Green }
                else { Say-All (Invoke-PnGit $Lib @('status', '--short')); Write-Host ''; Say-All (Invoke-PnGit $Lib @('--no-pager', 'diff', '--stat')) }
            }
            1 {
                if ((Get-Changes $Lib) -eq 0) { Say 'Нечего сохранять.' Yellow }
                else {
                    Say-All (Invoke-PnGit $Lib @('status', '--short'))
                    $message = Ask 'Описание коммита (пусто = отмена)'
                    if ($message) { [void](Invoke-PnGit $Lib @('add', '-A')); Say-All (Invoke-PnGit $Lib @('commit', '-q', '-m', $message)); Say 'Сохранено у тебя локально.' Green }
                }
            }
            2 {
                Say-All (Invoke-PnGit $Lib @('--no-pager', 'log', '--oneline', '-1'))
                if (Confirm-Action 'Отменить этот коммит, оставив правки в файлах?') { Say-All (Invoke-PnGit $Lib @('reset', '--soft', 'HEAD~1')); Say 'Готово.' Green }
            }
            3 {
                $branches = @(Invoke-PnGit $Lib @('branch', '--format=%(refname:short)'))
                $choice = Select-Item ($crumb + '  »  ветки') $branches
                if ($choice -lt 0) { continue menu }
                Show-Action ($crumb + '  »  переключение')
                if ((Get-Changes $Lib) -gt 0) { Say 'Есть несохранённые правки: сначала сохрани их или спрячь.' Yellow }
                else { Say-All (Invoke-PnGit $Lib @('switch', $branches[$choice])) }
            }
            4 {
                $name = Ask 'Имя новой ветки, например dev/api-events (пусто = отмена)'
                if ($name) { Say-All (Invoke-PnGit $Lib @('switch', '-c', $name)) }
            }
            5 { Say-All (Invoke-PnGit $Lib @('--no-pager', 'log', '--oneline', '--decorate', '-20')) }
            6 { Say-All (Invoke-PnGit $Lib @('stash', 'push', '-u', '-m', 'dev-menu')) }
            7 { Say-All (Invoke-PnGit $Lib @('stash', 'pop')) }
            8 {
                Say-All (Invoke-PnGit $Lib @('status', '--short'))
                if (Confirm-Action 'Выбросить ВСЕ несохранённые правки? Вернуть их будет нельзя.') {
                    if (Confirm-Action 'Точно выбросить?') { [void](Invoke-PnGit $Lib @('reset', '--hard')); [void](Invoke-PnGit $Lib @('clean', '-fd')); Say 'Правки выброшены.' Yellow }
                }
            }
        }
        Wait-Key
    }
}

# ---------- сборка и проверка ----------

function Select-Plugin([string]$crumb) {
    $labels = @('Все плагины|подряд, по одному') + ($Plugins | ForEach-Object { '{0}|ветка {1}' -f $_, (Get-Branch (Join-Path $Root $_)) })
    $pick = Select-Item $crumb $labels
    if ($pick -eq -1) { return $null }
    if ($pick -eq 0) { return @('all') }
    return @($Plugins[$pick - 1])
}

function Menu-Build {
    $crumb = 'Главное меню  »  Собрать и проверить'
    :menu while ($true) {
        $pick = Select-Item $crumb @(
            'Собрать для плагинов|библиотека в локальный Maven, НЕ jar для сервера',
            'Проверить плагины|сломались ли они от твоих правок API',
            'Быстрые тесты|только модуль core',
            'Полная проверка библиотеки|тесты, стиль, проверка API',
            'Привести стиль в порядок|spotlessApply',
            'Собрать jar библиотеки|pnLibrary для plugins сервера, любая платформа',
            'Собрать jar плагина|плагин вместе с библиотекой для сервера',
            'Как подключить в плагин|строки для Gradle и Maven'
        ) 'Два разных пути: плагины (компиляция) и сервер (jar в plugins). Подробности: Сервер » Как подключить.'
        if ($pick -eq -1) { return }
        Show-Action ($crumb + '  »  выполняется')
        $script:Detached = $false
        switch ($pick) {
            0 {
                if (Step 'Библиотека из твоих правок' { Run-Dev 'lib-publish' }) {
                    Write-Host ''
                    Say 'Что дальше:' White
                    Say '· «Проверить плагины»: узнать, не сломали ли правки плагины.'
                    Say '· «Собрать jar и поставить на сервер»: проверить в игре.'
                    Say '· Свой плагин можно собрать вручную: версия библиотеки 0.1.0-dev.local, репозиторий mavenLocal().'
                    if (Confirm-Action 'Показать строки подключения для Gradle и Maven?') { Show-Connect 'bukkit' }
                }
            }
            1 {
                $target = Select-Plugin ($crumb + '  »  какие плагины')
                if (-not $target) { continue menu }
                Show-Action ($crumb + '  »  проверка плагинов')
                if (-not (Get-Item (Join-Path $env:USERPROFILE ".m2\repository\io\github\pnfolder\pnlibrary-api\$LocalVersion") -ErrorAction SilentlyContinue)) {
                    Say 'Локальная библиотека ещё не собрана, сначала соберу её.' Yellow
                    if (-not (Step 'Библиотека из твоих правок' { Run-Dev 'lib-publish' })) { if (-not $script:Detached) { Wait-Key }; $script:Detached = $false; continue menu }
                }
                [void](Step 'Проверка плагинов' { Run-Dev 'plugins' $target })
            }
            2 { [void](Step 'Тесты модуля core' { Gradle-Run $Lib @(':modules:core:test', '--offline') }) }
            3 { [void](Step 'Полная проверка библиотеки' { Run-Dev 'lib-check' }) }
            4 { [void](Step 'Стиль кода (spotless)' { Gradle-Run $Lib @('spotlessApply', '--offline') }) }
            5 { Menu-LibraryJar ($crumb + '  »  jar библиотеки'); continue menu }
            6 { Menu-Jars ($crumb + '  »  jar плагина'); continue menu }
            7 { Menu-Connect $crumb; continue menu }
        }
        if ($script:Detached) { $script:Detached = $false; continue menu }
        Wait-Key
    }
}

# Платформы jar библиотеки: название, задача Gradle, маска файла.
$LibraryPlatforms = @(
    @{ Name = 'Bukkit / Paper / Spigot'; Task = 'shadowBukkit'; Mask = 'pnLibrary-*-bukkit-java8.jar' },
    @{ Name = 'BungeeCord'; Task = 'shadowBungee'; Mask = 'pnLibrary-*-bungeecord-java8.jar' },
    @{ Name = 'Velocity'; Task = 'shadowVelocity'; Mask = 'pnLibrary-*-velocity-java17.jar' },
    @{ Name = 'Универсальный (все платформы)'; Task = 'shadowUniversal'; Mask = 'pnLibrary-*-universal.jar' }
)

function Select-LibraryPlatform([string]$crumb) {
    $pick = Select-Item $crumb ($LibraryPlatforms | ForEach-Object { $_.Name + '|' + $_.Task })
    if ($pick -eq -1) { return $null }
    return $LibraryPlatforms[$pick]
}

# После сборки: копирует jar библиотеки в test-build. Контекст: Platform.
function After-LibraryJar($task) {
    New-Item -ItemType Directory -Force $TestBuild | Out-Null
    $platform = $task.Context.Platform
    $libJar = Get-ChildItem (Join-Path $Lib 'distribution\build\libs') -Filter $platform.Mask -ErrorAction SilentlyContinue | Where-Object { $_.Name -notlike '*full*' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $libJar) { return 'Jar не найден после сборки.' }
    Get-ChildItem $TestBuild -Filter ($platform.Mask -replace '\.jar$', '-local.jar') -ErrorAction SilentlyContinue | Remove-Item -Force
    $file = Copy-Item $libJar.FullName (Join-Path $TestBuild ($libJar.BaseName + '-local.jar')) -Force -PassThru
    $task.Files = @($file)
    return ('Готово: {0}  ({1:N1} МБ)' -f $file.FullName, ($file.Length / 1MB))
}

# После сборки: копирует jar библиотеки и плагина в test-build. Контекст: Platform, Plugin.
function After-PluginJars($task) {
    $lines = @(After-LibraryJar $task)
    $files = @($task.Files)
    $pluginJar = Get-ChildItem (Join-Path $Root ($task.Context.Plugin + '\build\libs')) -Filter '*.jar' -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch 'plain|sources|javadoc' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($pluginJar) { $files += Copy-Item $pluginJar.FullName $TestBuild -Force -PassThru }
    $task.Files = $files
    $lines = @('Файлы в ' + $TestBuild)
    foreach ($f in $files) { $lines += ('· {0}  ({1:N1} МБ)' -f $f.Name, ($f.Length / 1MB)) }
    return $lines
}

# Собирает jar библиотеки под платформу и копирует в test-build. Возвращает файл или $null.
function Build-LibraryJar($platform) {
    $task = $platform.Task
    $ok = Step ('jar библиотеки: ' + $platform.Name) { Gradle-Run $Lib @((':distribution:' + $task), '--offline') } 'After-LibraryJar' @{ Platform = $platform }
    if (-not $ok -or @($script:LastTask.Files).Count -eq 0) { return $null }
    return $script:LastTask.Files[0]
}

# Собирает библиотеку и плагин, кладёт jar в test-build. Возвращает список полученных файлов или $null.
function Build-Jars([string]$plugin) {
    $platform = $LibraryPlatforms[0]
    $libTask = $platform.Task
    $task = 'build'
    if (Test-Path (Join-Path $Root "$plugin\build.gradle.kts")) { $task = 'shadowJar' }
    $initFile = Join-Path $PSScriptRoot 'local.init.gradle'
    $ok = Step "jar плагина $plugin" {
        Gradle-Run $Lib @((':distribution:' + $libTask), '--offline')
        Gradle-Run (Join-Path $Root $plugin) @($task, '-x', 'test', '-I', $initFile)
    } 'After-PluginJars' @{ Platform = $platform; Plugin = $plugin }
    if (-not $ok -or @($script:LastTask.Files).Count -eq 0) { return $null }
    return @($script:LastTask.Files)
}

# Необязательные модули библиотеки, которые подключаются отдельной зависимостью.
$OptionalModules = @(
    @{ Id = 'pnlibrary-common'; Title = 'Общие классы (common)'; Note = 'уже входит в api, нужен только если подключаешь отдельно'; Bukkit = $false },
    @{ Id = 'pnlibrary-config-api'; Title = 'Конфиги (config-api)'; Note = 'уже входит в api, нужен только если подключаешь отдельно'; Bukkit = $false },
    @{ Id = 'pnlibrary-minecraft-localization-api'; Title = 'Локализация, API (localization-api)'; Note = 'уже входит в api, нужен только если подключаешь отдельно'; Bukkit = $false },
    @{ Id = 'pnlibrary-currency-api'; Title = 'Валюты и экономика'; Note = 'API валют, баланс, перевод (все платформы)'; Bukkit = $false },
    @{ Id = 'pnlibrary-minecraft-localization'; Title = 'Локализация для Bukkit'; Note = 'сообщения по языку игрока, файлы lang'; Bukkit = $true }
)

# Строки подключения локальной библиотеки: Gradle Kotlin, Gradle Groovy, Maven. $extra: Id выбранных необязательных модулей.
function Get-ConnectEntries([string]$key, [string[]]$extra) {
    $entries = @(
        @{ Id = 'pnlibrary-api'; Scope = 'compileOnly' },
        @{ Id = "pnlibrary-$key-api"; Scope = 'compileOnly' },
        @{ Id = "pnlibrary-bootstrap-$key"; Scope = 'implementation' }
    )
    foreach ($id in $extra) { $entries += @{ Id = $id; Scope = 'compileOnly' } }
    return $entries
}

function Get-ConnectText([string]$format, $entries) {
    $v = $LocalVersion
    $lines = @()
    switch ($format) {
        'kotlin' {
            $lines += 'repositories { mavenLocal() }'; $lines += 'dependencies {'
            foreach ($e in $entries) { $lines += ('    ' + $e.Scope + '("io.github.pnfolder:' + $e.Id + ':' + $v + '")') }
            $lines += '}'
        }
        'groovy' {
            $lines += 'repositories { mavenLocal() }'; $lines += 'dependencies {'
            foreach ($e in $entries) { $lines += ('    ' + $e.Scope + " 'io.github.pnfolder:" + $e.Id + ':' + $v + "'") }
            $lines += '}'
        }
        'maven' {
            foreach ($e in $entries) {
                $scope = ''; if ($e.Scope -eq 'compileOnly') { $scope = '<scope>provided</scope>' }
                $lines += ('<dependency><groupId>io.github.pnfolder</groupId><artifactId>' + $e.Id + '</artifactId><version>' + $v + '</version>' + $scope + '</dependency>')
            }
        }
    }
    return $lines
}

function Show-Connect([string]$key, [string[]]$extra = @()) {
    $entries = Get-ConnectEntries $key $extra
    Say 'Сначала собери библиотеку «для плагинов». Она лежит в локальном Maven, поэтому нужен mavenLocal().' DarkGray
    Write-Host ''
    Say 'Gradle (Kotlin, build.gradle.kts)' White
    foreach ($l in (Get-ConnectText 'kotlin' $entries)) { Say $l Cyan }
    Write-Host ''
    Say 'Gradle (Groovy, build.gradle)' White
    foreach ($l in (Get-ConnectText 'groovy' $entries)) { Say $l Cyan }
    Write-Host ''
    Say 'Maven (pom.xml, в <dependencies>; репозиторий ~/.m2 подхватывается сам)' White
    foreach ($l in (Get-ConnectText 'maven' $entries)) { Say $l Cyan }
    Write-Host ''
    Say 'common, config-api и localization-api уже входят в pnlibrary-api: галочки нужны, только если хочешь указать их явно.' DarkGray
    Say 'Без правки build-файлов плагина: пункт «Проверить плагины» подставляет эту версию сам.' DarkGray
}

function Menu-Connect([string]$crumb) {
    $platforms = @(
        @{ Name = 'Bukkit / Paper / Spigot'; Key = 'bukkit' },
        @{ Name = 'BungeeCord'; Key = 'bungee' },
        @{ Name = 'Velocity'; Key = 'velocity' }
    )
    $pick = Select-Item ($crumb + '  »  платформа') ($platforms | ForEach-Object { $_.Name + '|строки для Gradle и Maven' })
    if ($pick -eq -1) { return }
    $platform = $platforms[$pick]
    $modules = @($OptionalModules | Where-Object { -not $_.Bukkit -or $platform.Key -eq 'bukkit' })
    $selected = @{}
    $position = 0
    $screen = $crumb + '  »  ' + $platform.Name
    while ($true) {
        $items = @()
        foreach ($m in $modules) {
            $mark = '[ ]'; if ($selected[$m.Id]) { $mark = '[x]' }
            $items += ($mark + ' ' + $m.Title + '|' + $m.Note)
        }
        $items += 'Показать строки подключения|с выбранными модулями'
        $choice = Select-Item ($screen + '  »  модули') $items 'Основа (api, платформа, bootstrap) подключается всегда. Enter отмечает модуль.' $position
        if ($choice -eq -1) { return }
        if ($choice -lt $modules.Count) {
            $id = $modules[$choice].Id
            $selected[$id] = -not $selected[$id]
            $position = $choice
            continue
        }
        break
    }
    $extra = @($modules | Where-Object { $selected[$_.Id] } | ForEach-Object { $_.Id })
    Show-Action ($screen + '  »  строки')
    Show-Connect $platform.Key $extra
    if (Confirm-Action 'Скопировать Gradle (Kotlin) в буфер обмена?') {
        Set-Clipboard -Value ((Get-ConnectText 'kotlin' (Get-ConnectEntries $platform.Key $extra)) -join "`n")
        Say 'Скопировано.' Green
    }
    Wait-Key
}

function Menu-LibraryJar([string]$crumb) {
    $platform = Select-LibraryPlatform ($crumb + '  »  платформа')
    if (-not $platform) { return }
    Show-Action ($crumb + '  »  ' + $platform.Name)
    $file = Build-LibraryJar $platform
    if ($script:Detached) { $script:Detached = $false; return }
    if ($file) {
        if (Confirm-Action 'Положить на тестовый сервер прямо сейчас?') { Deploy-Jars @($file) }
    }
    Wait-Key
}

function Menu-Jars([string]$crumb) {
    $target = Select-Plugin ($crumb + '  »  плагин')
    if (-not $target -or $target[0] -eq 'all') { return }
    Show-Action ($crumb + '  »  сборка ' + $target[0])
    $files = Build-Jars $target[0]
    if ($script:Detached) { $script:Detached = $false; return }
    if ($files) {
        if (Confirm-Action 'Положить их на тестовый сервер прямо сейчас?') { Deploy-Jars $files }
    }
    Wait-Key
}

# ---------- сервер ----------

function Set-ServerFolder {
    Say ('Сейчас: ' + $(if (Get-ServerPlugins) { Get-ServerPlugins } else { 'не задана' }))
    $path = Ask 'Путь к папке plugins или к папке сервера (пусто = отмена)'
    if (-not $path) { return }
    $path = $path.Trim('"').Trim()
    if (Test-Path (Join-Path $path 'plugins')) { $path = Join-Path $path 'plugins' }
    if (-not (Test-Path $path)) { Say 'Такой папки нет.' Red; return }
    $config = Get-Config
    $config | Add-Member -NotePropertyName ServerPlugins -NotePropertyValue $path -Force
    Save-Config $config
    Say "Запомнил: $path" Green
}

function Show-ServerJars {
    $folder = Get-ServerPlugins
    if (-not $folder) { Say 'Папка сервера не задана: пункт «Папка plugins».' Yellow; return }
    Say "Папка: $folder" White
    $jars = @(Get-ChildItem $folder -Filter 'pn*.jar' -ErrorAction SilentlyContinue)
    if ($jars.Count -eq 0) { Say 'pnFolder-плагинов там нет.' Yellow; return }
    foreach ($jar in $jars) { Say ('· {0,-60} {1:N1} МБ   {2}' -f $jar.Name, ($jar.Length / 1MB), $jar.LastWriteTime.ToString('dd.MM HH:mm')) }
    $libs = @($jars | Where-Object { $_.Name -like 'pnLibrary-*' -and $_.Name -notlike '*updater*' })
    if ($libs.Count -gt 1) { Write-Host ''; Say 'Внимание: pnLibrary лежит в нескольких версиях, оставь одну.' Yellow }
}

# Кладёт jar на сервер. Старые версии тех же плагинов не удаляются, а переносятся в _old-pnfolder.
function Deploy-Jars($files) {
    $folder = Get-ServerPlugins
    if (-not $folder) { Say 'Папка сервера не задана. Укажи её в пункте «Папка plugins».' Yellow; return }
    Say "Ставлю в: $folder" White
    Write-Host ''
    $old = @()
    foreach ($f in $files) {
        $prefix = ($f.Name -split '-')[0]
        $old += @(Get-ChildItem $folder -Filter "$prefix-*.jar" -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne $f.Name -and $_.Name -like "$prefix-[0-9]*" -and $_.Name -notlike '*updater*' })
    }
    if ($old.Count -gt 0) {
        Say 'Уже лежат другие версии:' Yellow
        foreach ($o in $old) { Say "· $($o.Name)" }
        if (Confirm-Action 'Убрать их в папку _old-pnfolder (не удаляя)? Иначе будут дубли.') {
            $backup = Join-Path $folder ('_old-pnfolder\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
            New-Item -ItemType Directory -Force $backup | Out-Null
            foreach ($o in $old) {
                try { Move-Item $o.FullName $backup -Force -ErrorAction Stop; Say "убрано: $($o.Name)" }
                catch { Say "не удалось убрать $($o.Name): файл занят. Останови сервер и повтори." Red }
            }
        }
    }
    foreach ($f in $files) {
        try { Copy-Item $f.FullName $folder -Force -ErrorAction Stop; Say "положено: $($f.Name)" Green }
        catch { Say "не удалось положить $($f.Name): файл занят. Останови сервер и повтори." Red }
    }
    Write-Host ''
    Say 'Теперь запусти сервер. В консоли должны появиться строки Enabling pnLibrary и Enabling твоего плагина.' Cyan
}

function Show-HowTo {
    $folder = Get-ServerPlugins
    if (-not $folder) { $folder = '(папка не задана: пункт «Папка plugins»)' }
    Say 'Два разных результата: не путай' White
    Write-Host ''
    Say 'А. «Собрать для плагинов»: библиотека кладётся в локальный Maven (папка .m2 на компьютере).'
    Say '   Это НЕ файл для сервера. Он нужен, чтобы плагины компилировались против твоих правок'
    Say '   и ты видел, что сломалось. На сервер это не ставится.'
    Say 'Б. «Собрать jar для сервера»: получаются настоящие jar-файлы (pnLibrary + плагин).'
    Say '   Их кладут в plugins сервера, чтобы проверить в игре.'
    Write-Host ''
    Say 'Как правильно подключить на сервер (путь Б)' White
    Write-Host ''
    Say '1. Собери: «Сервер» → «Собрать и поставить плагин» (или «…библиотеку», если нужна только она).'
    Say "   Меню положит в $TestBuild две вещи: pnLibrary (…-local.jar) и jar плагина."
    Say '2. Останови сервер. На Windows запущенный сервер держит jar, и заменить его нельзя.'
    Say "3. В $folder должен остаться ОДИН pnLibrary-*.jar. Старые меню само уберёт в _old-pnfolder."
    Say '4. Положи оба файла: меню делает это само, когда соглашаешься «Положить на сервер».'
    Say '5. Запусти сервер. В логе ищи:'
    Say '     [pnLibrary] Enabling pnLibrary v…       библиотека включилась' Cyan
    Say '     [<плагин>] Enabling <плагин> v…         плагин включился без LinkageError' Cyan
    Say '6. Команда /pn в игре или консоли показывает версию библиотеки и подключённые плагины.'
    Write-Host ''
    Say 'Если плагин пишет про устаревшую pnLibrary:' Yellow
    Say '   на сервере остался старый pnLibrary-jar. Проверь «Что лежит на сервере».'
    Write-Host ''
    Say 'Для своего плагина без меню: в build.gradle версия библиотеки 0.1.0-dev.local и mavenLocal() в repositories.' DarkGray
}

function Menu-Server {
    $crumb = 'Главное меню  »  Сервер'
    :menu while ($true) {
        $folder = Get-ServerPlugins
        $where = 'не задана'
        if ($folder) { $where = $folder }
        $pick = Select-Item $crumb @(
            "Папка plugins|$where",
            'Собрать и поставить плагин|библиотека из правок и выбранный плагин',
            'Собрать и поставить библиотеку|только pnLibrary, любая платформа',
            'Поставить уже собранное|всё из папки test-build',
            'Что лежит на сервере|pnFolder-jar в plugins, дубли',
            'Как подключить на сервер|пошаговая инструкция'
        ) 'Старые версии не удаляются, а переносятся в _old-pnfolder.'
        if ($pick -eq -1) { return }
        Show-Action ($crumb + '  »  результат')
        switch ($pick) {
            0 { Set-ServerFolder }
            1 { Menu-Jars ($crumb + '  »  jar'); continue menu }
            2 { Menu-LibraryJar ($crumb + '  »  jar библиотеки'); continue menu }
            3 {
                $files = @(Get-ChildItem $TestBuild -Filter '*.jar' -ErrorAction SilentlyContinue)
                if ($files.Count -eq 0) { Say 'В test-build пока пусто: сначала «Собрать и поставить».' Yellow } else { Deploy-Jars $files }
            }
            4 { Show-ServerJars }
            5 { Show-HowTo }
        }
        Wait-Key
    }
}

# ---------- API ----------

function Menu-Api {
    $crumb = 'Главное меню  »  API библиотеки'
    while ($true) {
        $pick = Select-Item $crumb @(
            'Карта API|пакеты и количество типов',
            'Что изменилось|публичное API по сравнению с релизом dev.6',
            'Принять изменения API|apiDump: обновить эталон',
            'Проверить API|apiCheck: совпадает ли с эталоном'
        ) 'После правок API: «Принять изменения», затем «Проверить плагины» в сборке.'
        if ($pick -eq -1) { return }
        Show-Action ($crumb + '  »  результат')
        switch ($pick) {
            0 { Run-Dev 'api-map'; Say-All (Get-Content (Join-Path $PSScriptRoot 'API-MAP.md') -Encoding UTF8 | Select-Object -Skip 4) }
            1 { Run-Dev 'api-diff' }
            2 { Push-Location $Lib; & .\gradlew.bat apiDump --offline; Pop-Location }
            3 { Push-Location $Lib; & .\gradlew.bat apiCheck --offline; Pop-Location }
        }
        Wait-Key
    }
}

# ---------- прочее ----------

function Menu-Other {
    $crumb = 'Главное меню  »  Прочее'
    while ($true) {
        $pick = Select-Item $crumb @(
            'Открыть в IntelliJ|библиотека pnLibrary',
            'Открыть папку проекта|в проводнике',
            'Все репозитории|ветки и изменения плагинов',
            'Отправить ветку на GitHub|не main, с подтверждением',
            'Включить защиту|нельзя пушить в main и теги',
            'Снять защиту|разрешить push в main',
            'Инструкция|как работать с окружением'
        )
        if ($pick -eq -1) { return }
        Show-Action ($crumb + '  »  результат')
        switch ($pick) {
            0 {
                $idea = Get-Command idea64.exe, idea.cmd, idea -ErrorAction SilentlyContinue | Select-Object -First 1
                if (-not $idea) { $idea = Get-ChildItem "$env:LOCALAPPDATA\Programs", 'C:\Program Files\JetBrains' -Filter 'idea64.exe' -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1 }
                if ($idea) { Start-Process $(if ($idea.Source) { $idea.Source } else { $idea.FullName }) $Lib; Say 'IntelliJ открывается.' Green }
                else { Say "IntelliJ не найден. Открой папку вручную: File → Open → $Lib" Yellow; Start-Process explorer.exe $Lib }
            }
            1 { Start-Process explorer.exe $Root; Say 'Открыто.' Green }
            2 { Run-Dev 'status' }
            3 {
                $branch = Get-Branch $Lib
                if ($branch -in @('main', 'master')) { Say 'Нельзя: это основная ветка.' Red }
                elseif (Confirm-Action "Отправить ветку $branch на GitHub?") { Say-All (Invoke-PnGit $Lib @('push', '-u', 'origin', $branch)) }
            }
            4 { Run-Dev 'guard' }
            5 { if (Confirm-Action 'Снять защиту? После этого push в main возможен.') { Run-Dev 'unguard' } }
            6 { Say-All (Get-Content (Join-Path $PSScriptRoot 'README.md') -Encoding UTF8) }
        }
        Wait-Key
    }
}

# ---------- главное меню ----------

Write-Host 'Загружаю меню pnFolder...' -ForegroundColor Cyan
if (-not $script:Keys) { Write-Host 'Консоль не принимает нажатия клавиш: меню работает по номерам.' -ForegroundColor Yellow }
try {
    Update-Info
    if ($script:Keys) { Clear-Host }
    while ($true) {
        $mainItems = {
            $running = @($script:Tasks | Where-Object { $_.Status -eq 'running' }).Count
            $taskNote = 'сборки в фоне: вернуться, посмотреть, отменить'
            if ($running -gt 0) { $taskNote = 'идёт сейчас: ' + $running }
            @(
                'Ветка и изменения|смотреть, сохранять, переключать ветки',
                'Собрать и проверить|библиотека, плагины, jar для сервера',
                'Сервер|поставить сборку на тестовый сервер, инструкция',
                'API библиотеки|карта, что изменилось, проверка',
                'Прочее|IntelliJ, отправка на GitHub, защита',
                ('Задачи|' + $taskNote)
            )
        }
        $pick = Select-Item 'Главное меню' @() 'Esc закрывает меню.' 0 $mainItems
        switch ($pick) {
            -1 {
                $running = @($script:Tasks | Where-Object { $_.Status -eq 'running' })
                if ($running.Count -gt 0) {
                    if (-not (Confirm-Action ('Ещё идут задачи (' + $running.Count + '). Остановить их и выйти?'))) { continue }
                    foreach ($task in $running) { Stop-Task $task }
                }
                if ($script:Keys) { Clear-Host }
                return
            }
            5 { Menu-Tasks }
            0 { Menu-Branch }
            1 { Menu-Build }
            2 { Menu-Server }
            3 { Menu-Api }
            4 { Menu-Other }
        }
    }
} catch {
    try { [Console]::CursorVisible = $true } catch { }
    Write-Host ''
    Write-Host ('Ошибка меню: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host $_.ScriptStackTrace -ForegroundColor DarkGray
    [void](Read-Host 'Enter, чтобы закрыть')
}
