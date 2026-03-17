@echo off
setlocal

powershell -ExecutionPolicy Bypass -File "%~dp0run_change_verification.ps1" %*
set EXIT_CODE=%ERRORLEVEL%

endlocal & exit /b %EXIT_CODE%
