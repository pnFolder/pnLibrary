@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\release\release.ps1" -Action github -Message "%~1"
exit /b %errorlevel%
