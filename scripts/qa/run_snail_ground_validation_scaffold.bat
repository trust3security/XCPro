@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0scaffold_snail_ground_validation.ps1" %*
