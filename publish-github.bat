@echo off
setlocal
if "%~1"=="" goto usage
if "%~2"=="" goto usage
call "%~dp0tools\release\dispatch-github.bat" full "%~1" "%~2" false
exit /b %errorlevel%

:usage
echo Usage: publish-github.bat VERSION "RELEASE NOTES"
exit /b 2
