@echo off
setlocal

set "PROJECT_ROOT=%CD%"
set "SCRIPT_DIR=%~dp0"
set "LOCK_SCRIPT=%SCRIPT_DIR%recover-gradle-file-locks.ps1"

if /I "%~1"=="--aggressive" (
    echo Running aggressive lock recovery for %PROJECT_ROOT%
    powershell -NoProfile -ExecutionPolicy Bypass -File "%LOCK_SCRIPT%" -ProjectRoot "%PROJECT_ROOT%" -Aggressive
    exit /b %ERRORLEVEL%
)

echo Running lock recovery for %PROJECT_ROOT%
powershell -NoProfile -ExecutionPolicy Bypass -File "%LOCK_SCRIPT%" -ProjectRoot "%PROJECT_ROOT%"
exit /b %ERRORLEVEL%
