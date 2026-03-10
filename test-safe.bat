@echo off
setlocal EnableExtensions

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"
set "TEST_PARALLEL_FORKS=%XC_TEST_PARALLEL_FORKS%"
if "%TEST_PARALLEL_FORKS%"=="" set "TEST_PARALLEL_FORKS=2"
set "XC_TEST_PARALLEL_FORKS=%TEST_PARALLEL_FORKS%"
set "GRADLE_COMMON_ARGS=--daemon --parallel --build-cache --configuration-cache --console plain"

if /I "%~1"=="--help" goto :usage
if /I "%~1"=="-h" goto :usage

if "%~1"=="" (
    set "TEST_ARGS=testDebugUnitTest"
) else (
    set "TEST_ARGS=%*"
)

pushd "%REPO_ROOT%" >nul

echo Running safe test command...
echo   "%GRADLE%" %GRADLE_COMMON_ARGS% %TEST_ARGS%
echo   parallel forks: %TEST_PARALLEL_FORKS%
echo.

call :purge_test_result_locks

call .\scripts\dev\gradle-run-with-lock-recovery.bat "%GRADLE%" %GRADLE_COMMON_ARGS% %TEST_ARGS%
if %ERRORLEVEL% neq 0 (
    echo ERROR: Test command failed after recovery retry.
    popd >nul
    exit /b 1
)

:success
echo.
echo ========================================
echo Safe test command completed successfully.
echo ========================================
popd >nul
exit /b 0

:purge_test_result_locks
set "MAP_BINARY_DIR=%REPO_ROOT%feature\map\build\test-results\testDebugUnitTest\binary"
set "APP_BINARY_DIR=%REPO_ROOT%app\build\test-results\testDebugUnitTest\binary"
set "MAP_BINARY_FILE=%MAP_BINARY_DIR%\output.bin"
set "APP_BINARY_FILE=%APP_BINARY_DIR%\output.bin"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$targets = @('%MAP_BINARY_FILE%', '%APP_BINARY_FILE%', '%MAP_BINARY_DIR%', '%APP_BINARY_DIR%');" ^
  "foreach ($path in $targets) {" ^
  "  for ($i = 0; $i -lt 40; $i++) {" ^
  "    try {" ^
  "      if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force -Recurse -ErrorAction Stop }" ^
  "      break" ^
  "    } catch {" ^
  "      Start-Sleep -Milliseconds 250" ^
  "    }" ^
  "  }" ^
  "}"

if exist "%MAP_BINARY_FILE%" echo WARN: lock remains on %MAP_BINARY_FILE%
if exist "%APP_BINARY_FILE%" echo WARN: lock remains on %APP_BINARY_FILE%
exit /b 0

:usage
echo Usage:
echo   test-safe.bat [gradle task(s)/argument(s)]
echo.
echo Optional env override:
echo   set XC_TEST_PARALLEL_FORKS=1^|2^|3...
echo.
echo Examples:
echo   test-safe.bat
echo   test-safe.bat :feature:map:testDebugUnitTest
echo   test-safe.bat testDebugUnitTest --tests "com.example.xcpro.sensors.domain.CalculateFlightMetricsUseCaseTest"
echo.
echo Default when no args:
echo   testDebugUnitTest
exit /b 0
