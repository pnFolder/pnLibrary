@echo off
setlocal
where gh >nul 2>nul || (
  echo GitHub CLI is not installed: https://cli.github.com/
  exit /b 1
)
gh auth status >nul 2>nul || (
  echo Sign in first: gh auth login
  exit /b 1
)
for /f "delims=" %%B in ('git branch --show-current') do set "BRANCH=%%B"
if not defined BRANCH (
  echo Cannot determine the current Git branch.
  exit /b 1
)
echo Starting GitHub Actions for version %~2 on branch %BRANCH%...
gh workflow run release.yml --ref "%BRANCH%" -f mode="%~1" -f version="%~2" -f release_notes="%~3" -f publish_central="%~4"
if errorlevel 1 exit /b %errorlevel%
echo Workflow started.
gh run list --workflow release.yml --limit 1
exit /b 0
