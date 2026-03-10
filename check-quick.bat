@echo off
setlocal EnableExtensions

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"
set "TEST_SAFE=%REPO_ROOT%test-safe.bat"

if /I "%~1"=="--help" goto :usage
if /I "%~1"=="-h" goto :usage

pushd "%REPO_ROOT%" >nul

echo Running quick checks...
echo.

echo [1/3] Enforcing architecture/coding rules...
call .\scripts\dev\gradle-run-with-lock-recovery.bat "%GRADLE%" enforceRules
if %ERRORLEVEL% neq 0 (
    echo ERROR: Rule enforcement failed.
    popd >nul
    exit /b 1
)

echo [2/3] Running quick unit tests (safe runner with lock recovery)...
if "%~1"=="" (
    call "%TEST_SAFE%" :feature:map:testDebugUnitTest --tests "com.example.xcpro.sensors.domain.CalculateFlightMetricsUseCaseTest" --tests "com.example.xcpro.adsb.metadata.AircraftMetadataRepositoryImplTest" --tests "com.example.xcpro.map.MapLifecycleManagerScaleBarCleanupTest"
) else (
    call "%TEST_SAFE%" %*
)
if %ERRORLEVEL% neq 0 (
    echo ERROR: Quick unit tests failed.
    popd >nul
    exit /b 1
)

echo [3/3] Assembling quick modules...
call .\scripts\dev\gradle-run-with-lock-recovery.bat "%GRADLE%" :feature:map:assembleDebug :dfcards-library:assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Quick assemble failed.
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
echo   check-quick.bat [test-safe args]
echo.
echo Default (no args):
echo   - enforceRules
echo   - safe targeted tests:
echo       com.example.xcpro.sensors.domain.CalculateFlightMetricsUseCaseTest
echo       com.example.xcpro.adsb.metadata.AircraftMetadataRepositoryImplTest
echo       com.example.xcpro.map.MapLifecycleManagerScaleBarCleanupTest
echo   - :feature:map:assembleDebug :dfcards-library:assembleDebug
echo.
echo Examples:
echo   check-quick.bat
echo   check-quick.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapScreenViewModelTest"
exit /b 0
