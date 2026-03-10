@echo off
setlocal EnableExtensions

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"
pushd "%REPO_ROOT%" >nul

echo Running preflight checks...
echo.

echo [1/2] Enforcing architecture/coding rules...
call "%GRADLE%" enforceRules
if %ERRORLEVEL% neq 0 (
    echo ERROR: Rule enforcement failed
    popd >nul
    exit /b 1
)

echo [2/2] Building debug version...
call "%GRADLE%" assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    popd >nul
    exit /b 1
)

:done
echo.
echo ========================================
echo Preflight checks completed successfully.
echo ========================================
popd >nul
endlocal
exit /b 0
