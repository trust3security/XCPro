@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0prepare_connected_device_for_tests.ps1" %*
exit /b %ERRORLEVEL%
