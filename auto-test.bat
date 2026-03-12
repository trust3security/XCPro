@echo off
echo Starting automated build cycle...
set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"
echo.

echo [1/2] Running fast architecture gate...
call "%GRADLE%" enforceArchitectureFast
if %ERRORLEVEL% neq 0 (
    echo ERROR: Fast architecture gate failed
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
