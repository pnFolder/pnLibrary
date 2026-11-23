# Один раз: добавляет папку dev в PATH пользователя, чтобы команда dev работала из любой консоли.
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$path = [Environment]::GetEnvironmentVariable('Path', 'User')
if (($path -split ';') -contains $dir) { 'Уже добавлено. Открой новую консоль и набери: dev'; return }
[Environment]::SetEnvironmentVariable('Path', ($path.TrimEnd(';') + ';' + $dir), 'User')
'Готово. Открой НОВУЮ консоль и набери: dev'
