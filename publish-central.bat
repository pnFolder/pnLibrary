@echo off
setlocal
if "%~1"=="" goto usage
call "%~dp0tools\release\dispatch-github.bat" central "%~1" "Publish existing version to Maven Central" true
exit /b %errorlevel%

:usage
echo Usage: publish-central.bat VERSION
exit /b 2
