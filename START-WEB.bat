@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js was not found. Install Node.js 22 LTS or 24 LTS, then reopen this file.
  pause
  exit /b 1
)
node scripts\serve-web.cjs
pause
