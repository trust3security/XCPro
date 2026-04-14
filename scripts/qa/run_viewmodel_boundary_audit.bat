@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\ci\enforce_rules.ps1" -AuditViewModelBoundariesOnly
exit /b %ERRORLEVEL%
