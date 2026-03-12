@echo off
setlocal EnableExtensions

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"

if /I "%~1"=="--help" goto :usage
if /I "%~1"=="-h" goto :usage
if /I "%~1"=="?" goto :usage

pushd "%REPO_ROOT%" >nul

echo Running quick checks...
echo.

echo [1/3] Running fast architecture gate...
call "%GRADLE%" enforceArchitectureFast
if %ERRORLEVEL% neq 0 (
    echo ERROR: Fast architecture gate failed.
    popd >nul
    exit /b 1
)

echo [2/3] Assembling target modules...
call "%GRADLE%" :feature:map:assembleDebug :dfcards-library:assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: module assemble failed.
    popd >nul
    exit /b 1
)

if "%~1"=="" (
    echo [3/3] Skipping unit tests ^(compile-only mode^).
    echo To run tests, pass gradle args explicitly, e.g.
    echo   check-quick.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelTest"
    popd >nul
    exit /b 0
)

echo [3/3] Running requested task(s) explicitly...
call "%GRADLE%" %*
if %ERRORLEVEL% neq 0 (
    echo ERROR: Requested task^(s^) failed.
    popd >nul
    exit /b 1
)

echo.
echo ========================================
echo Quick checks completed successfully!
echo ========================================
popd >nul
exit /b 0

:usage
echo Usage:
echo   check-quick.bat [gradle test args]
echo   Add gradle args to run tests/retry tasks explicitly.
echo.
echo Default behavior:
echo   - enforceArchitectureFast
echo   - :feature:map:assembleDebug :dfcards-library:assembleDebug
echo   - no tests unless explicit gradle args are provided
echo.
echo Examples:
echo   check-quick.bat
echo   check-quick.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelTest"
exit /b 0
