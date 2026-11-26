<#
  Рабочее окружение pnFolder: правим библиотеку и сразу видим, что ломается в плагинах. Релизов нет.

  Команды:
    .\dev.ps1                       меню (то же, что команда dev)
    .\dev.ps1 status                 ветки и несохранённые правки всех репозиториев
    .\dev.ps1 lib-check              тесты, spotless и проверка публичного API библиотеки
    .\dev.ps1 lib-publish            собрать библиотеку из рабочей папки в локальный Maven (версия 0.1.0-dev.local)
    .\dev.ps1 plugins [имена|all]    собрать плагины против локальной библиотеки: видно, что сломали правки API
    .\dev.ps1 api-diff [тег]         что изменилось в публичном API по сравнению с релизом (по умолчанию v0.1.0-dev.6)
    .\dev.ps1 api-map                обновить карту API (dev\API-MAP.md)
    .\dev.ps1 guard                  запретить push в main/master и теги во всех репозиториях (защита от случайного релиза)
    .\dev.ps1 unguard                снять запрет
#>
param(
    [Parameter(Position = 0)][string]$Command = 'menu',
    [Parameter(Position = 1, ValueFromRemainingArguments = $true)][string[]]$Rest
)

$ErrorActionPreference = 'Stop'
$Lib = Split-Path -Parent $PSScriptRoot   # репозиторий pnLibrary, эта папка лежит в нём
$Root = Split-Path -Parent $Lib            # рядом с ним лежат репозитории плагинов
$LocalVersion = '0.1.0-dev.local'
$Init = Join-Path $PSScriptRoot 'local.init.gradle'
$Plugins = 'pnClans', 'pnMarket', 'pnCases', 'pnChat', 'pnSouls', 'pnRtp', 'pnTrades', 'pnBans', 'pnSeller', 'pnRelics', 'pnRelog', 'pnCaptcha', 'pnAuth'

# Java 21 нужна всем плагинам: pnMarket не собирается на более новой.
$jdk = Get-ChildItem 'C:\Program Files\Eclipse Adoptium' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
$env:PN_LOCAL_VERSION = $LocalVersion

function Gradle([string]$dir, [string[]]$gradleArgs) {
    Push-Location $dir
    try { & .\gradlew.bat @gradleArgs "--console=plain"; return $LASTEXITCODE } finally { Pop-Location }
}

switch ($Command) {
    'menu' { & (Join-Path $PSScriptRoot 'menu.ps1') }
    'status' {
        foreach ($name in @('pnLibrary') + $Plugins) {
            $dir = Join-Path $Root $name
            if (-not (Test-Path (Join-Path $dir '.git'))) { continue }
            $branch = git -C $dir branch --show-current
            $changed = (git -C $dir status --short | Measure-Object).Count
            '{0,-10} {1,-28} изменений: {2}' -f $name, $branch, $changed
        }
    }
    'lib-check' {
        exit (Gradle $Lib @('build', '--no-daemon'))
    }
    'lib-publish' {
        $code = Gradle $Lib @('publishToMavenLocal', '-x', 'test', '--no-build-cache', "-Pversion=$LocalVersion")
        if ($code -eq 0) { "Библиотека собрана как $LocalVersion. Теперь: .\dev.ps1 plugins all" }
        exit $code
    }
    'plugins' {
        $names = if (-not $Rest -or $Rest -contains 'all') { $Plugins } else { $Rest }
        $failed = @()
        foreach ($name in $names) {
            $dir = Join-Path $Root $name
            "=== $name ==="
            $code = Gradle $dir @('build', '-x', 'test', '-I', $Init)
            if ($code -ne 0) { $failed += $name }
        }
        if ($failed) { "Сломались: $($failed -join ', ')"; exit 1 }
        'Все выбранные плагины собираются против локальной библиотеки.'
    }
    'api-diff' {
        $tag = if ($Rest) { $Rest[0] } else { 'v0.1.0-dev.6' }
        git -C $Lib diff --stat $tag -- '*.api'
        git -C $Lib diff $tag -- '*.api'
    }
    'api-map' {
        $base = Join-Path $Lib 'modules\api\src\main\kotlin\org\pnfolder\pnlibrary\api'
        $decl = '^\s*(public |internal |abstract |open |sealed |data |enum |fun |annotation )*(class|interface|object) '
        $lines = @('# Карта публичного API pnLibrary', '', "Сгенерировано $(Get-Date -Format 'yyyy-MM-dd HH:mm') из modules/api. Обновить: .\dev.ps1 api-map", '',
            '| Пакет | Файлов | Типов | Файлы |', '|---|---|---|---|')
        Get-ChildItem $base -Directory | ForEach-Object {
            $files = Get-ChildItem $_.FullName -Recurse -Filter *.kt
            $types = ($files | Select-String -Pattern $decl | Measure-Object).Count
            $sample = ($files | Select-Object -First 4 | ForEach-Object { $_.BaseName }) -join ', '
            $lines += '| api.{0} | {1} | {2} | {3} |' -f $_.Name, $files.Count, $types, $sample
        }
        $lines | Set-Content (Join-Path $PSScriptRoot 'API-MAP.md') -Encoding UTF8
        "Готово: $(Join-Path $PSScriptRoot 'API-MAP.md')"
    }
    'guard' {
        $hook = "#!/bin/sh`n# pnFolder dev: релизов пока нет. Запрещены push в main/master и любые теги.`nwhile read local_ref local_sha remote_ref remote_sha; do`n  case `"`$remote_ref`" in`n    refs/heads/main|refs/heads/master|refs/tags/*) echo `"pnFolder dev: push в `$remote_ref запрещён (dev.ps1 unguard снимает запрет)`"; exit 1;;`n  esac`ndone`nexit 0`n"
        foreach ($name in @('pnLibrary') + $Plugins) {
            $dir = Join-Path $Root $name
            if (-not (Test-Path (Join-Path $dir '.git'))) { continue }
            [IO.File]::WriteAllText((Join-Path $dir '.git\hooks\pre-push'), $hook.Replace("`r`n", "`n"))
            "защита включена: $name"
        }
    }
    'unguard' {
        foreach ($name in @('pnLibrary') + $Plugins) {
            $hook = Join-Path $Root "$name\.git\hooks\pre-push"
            if (Test-Path $hook) { Remove-Item $hook; "защита снята: $name" }
        }
    }
    default { Get-Help $PSCommandPath | Out-String }
}
