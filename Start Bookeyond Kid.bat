@echo off
rem Double-click to start Bookeyond Kid (needs Docker Desktop running).
setlocal
cd /d "%~dp0"
title Bookeyond Kid
echo.
echo  Bookeyond Kid
echo  -------------

docker info >nul 2>&1
if errorlevel 1 (
  echo.
  echo  Docker Desktop is not running.
  echo  Open Docker Desktop, wait until it says "Engine running",
  echo  then double-click this file again.
  echo.
  pause
  exit /b 1
)

if not exist ".env" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\windows\first-run.ps1"
  if errorlevel 1 (
    echo.
    echo  Setup was not finished. Double-click this file to try again.
    pause
    exit /b 1
  )
)

echo.
echo  Starting. The first time this builds everything and can take 5-10 minutes...
echo.
docker compose up -d --build --wait
if errorlevel 1 (
  echo.
  echo  Bookeyond Kid did not start. The last lines from the server:
  echo.
  docker compose logs --tail 30 backend
  echo.
  echo  Try double-clicking this file again. If it keeps failing, keep this window
  echo  open and share what it says.
  pause
  exit /b 1
)

echo.
echo  Bookeyond Kid is running.
echo.
echo    App:     http://localhost:5173
echo    Emails:  http://localhost:8025   (every email the app sends lands here)
echo.
echo  To stop it, double-click "Stop Bookeyond Kid.bat".
echo.
start "" http://localhost:5173
pause
