@echo off
setlocal
set SCRIPT_DIR=%~dp0
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%run_mapscreen_overnight_agent_contract.ps1" %*
set EXIT_CODE=%ERRORLEVEL%
exit /b %EXIT_CODE%
