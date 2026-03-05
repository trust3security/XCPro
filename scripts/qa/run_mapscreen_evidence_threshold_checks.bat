@echo off
setlocal
set SCRIPT_DIR=%~dp0
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%run_mapscreen_evidence_threshold_checks.ps1" %*
exit /b %ERRORLEVEL%
