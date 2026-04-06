@echo off
setlocal

powershell -ExecutionPolicy Bypass -File "%~dp0run_mapscreen_pkg_f1_evidence_capture.ps1" %*
set EXIT_CODE=%ERRORLEVEL%

endlocal & exit /b %EXIT_CODE%
