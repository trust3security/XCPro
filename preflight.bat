@echo off
setlocal EnableExtensions

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"
set "TEST_SAFE=%REPO_ROOT%test-safe.bat"

pushd "%REPO_ROOT%" >nul

echo Running preflight checks...
echo.

echo [1/4] Enforcing architecture/coding rules...
call "%GRADLE%" enforceRules
if %ERRORLEVEL% neq 0 (
    echo ERROR: Rule enforcement failed
    popd >nul
    exit /b 1
)

echo [2/4] Building debug version...
call "%GRADLE%" assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    popd >nul
    exit /b 1
)

echo [3/4] Running debug unit tests (safe runner with lock recovery)...
call "%TEST_SAFE%" testDebugUnitTest
if %ERRORLEVEL% neq 0 (
    echo ERROR: Unit tests failed
    popd >nul
    exit /b 1
)

echo [4/4] Running app connected tests when device/emulator is attached...
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo SKIP: adb not found. Skipping connected tests.
    goto :done
)

set DEVICE_FOUND=0
for /f "skip=1 tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" set DEVICE_FOUND=1
)

if "%DEVICE_FOUND%"=="1" (
    call "%GRADLE%" :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
    if %ERRORLEVEL% neq 0 (
        echo ERROR: App connected tests failed
        popd >nul
        exit /b 1
    )
) else (
    echo SKIP: no authorized device/emulator attached. Skipping connected tests.
)

:done
echo.
echo ========================================
echo Preflight checks completed successfully!
echo ========================================
popd >nul
endlocal
exit /b 0
