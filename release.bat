@echo off
setlocal
if "%~1"=="" goto usage
if "%~2"=="" goto usage
call "%~dp0tools\release\dispatch-github.bat" full "%~1" "%~2" true
exit /b %errorlevel%

:usage
echo Usage: release.bat VERSION "RELEASE NOTES"
echo Example: release.bat 2.0.0-beta.7 "Diagnostics history improvements"
exit /b 2
