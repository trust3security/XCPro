@echo off
setlocal

powershell -ExecutionPolicy Bypass -File "%~dp0run_scia_ogn_hotspots_phase_gates.ps1" %*
set EXIT_CODE=%ERRORLEVEL%

endlocal & exit /b %EXIT_CODE%
