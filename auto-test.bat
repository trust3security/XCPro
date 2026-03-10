@echo off
echo Starting automated build cycle...
set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"
echo.

echo [1/2] Running rule enforcement...
call "%GRADLE%" enforceRules
if %ERRORLEVEL% neq 0 (
    echo ERROR: Rule enforcement failed
    exit /b 1
)

echo [2/2] Building debug version...
call "%GRADLE%" assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    exit /b 1
)

echo.
echo ========================================
echo Automated build completed.
echo For optional lint/tests/devices, run those tasks explicitly.
echo ========================================

exit /b 0
