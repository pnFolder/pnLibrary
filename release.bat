@echo off
setlocal
if "%~1"=="" goto usage
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\release\release.ps1" -Action release -Version "%~1" -Message "%~2"
exit /b %errorlevel%

:usage
echo Usage: release.bat VERSION "COMMIT MESSAGE"
echo Example: release.bat 2.0.0-beta.7 "release: 2.0.0-beta.7"
exit /b 2
