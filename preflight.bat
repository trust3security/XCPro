@echo off
setlocal
echo Running preflight checks...
echo.

echo [1/4] Enforcing architecture/coding rules...
call gradlew enforceRules
if %ERRORLEVEL% neq 0 (
    echo ERROR: Rule enforcement failed
    exit /b 1
)

echo [2/4] Building debug version...
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    exit /b 1
)

echo [3/4] Running debug unit tests...
call gradlew testDebugUnitTest
if %ERRORLEVEL% neq 0 (
    echo ERROR: Unit tests failed
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
    call gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
    if %ERRORLEVEL% neq 0 (
        echo ERROR: App connected tests failed
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
endlocal
