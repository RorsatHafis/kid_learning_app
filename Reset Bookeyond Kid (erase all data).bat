@echo off
rem Erases Bookeyond Kid's local database and emails so you can start testing from nothing.
rem It only touches this project's own containers and data (Compose project "bookeyond-kid").
setlocal
cd /d "%~dp0"
title Reset Bookeyond Kid
echo.
echo  This permanently erases every account, child, learning record, piece of content
echo  and email in your LOCAL Bookeyond Kid. It cannot be undone.
echo  Your administrator login (in .env) is kept and is created again on the next start.
echo.
set /p CONFIRM= Type ERASE and press Enter to continue, or just press Enter to cancel:
if /i not "%CONFIRM%"=="ERASE" (
  echo  Cancelled. Nothing was changed.
  pause
  exit /b 0
)
docker info >nul 2>&1
if errorlevel 1 (
  echo  Docker Desktop is not running. Open it, wait for "Engine running", then try again.
  pause
  exit /b 1
)
docker compose down --volumes
echo.
echo  Erased. Double-click "Start Bookeyond Kid.bat" to start again with an empty database.
pause
