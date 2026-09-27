@echo off
rem Double-click to stop Bookeyond Kid. Everything you created is kept for next time.
setlocal
cd /d "%~dp0"
title Stop Bookeyond Kid
docker info >nul 2>&1
if errorlevel 1 (
  echo  Docker Desktop is not running, so Bookeyond Kid is already stopped.
  pause
  exit /b 0
)
docker compose stop
echo.
echo  Bookeyond Kid has stopped. Your accounts, children and learning history are kept.
pause
