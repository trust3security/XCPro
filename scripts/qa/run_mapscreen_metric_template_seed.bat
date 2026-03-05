@echo off
setlocal
set SCRIPT_DIR=%~dp0
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%seed_mapscreen_metric_templates.ps1" %*
exit /b %ERRORLEVEL%
