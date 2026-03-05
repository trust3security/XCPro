@echo off
setlocal
set SCRIPT_DIR=%~dp0
powershell -ExecutionPolicy Bypass -File "%SCRIPT_DIR%run_mapscreen_pkg_e1_evidence_capture.ps1" %*
exit /b %ERRORLEVEL%
