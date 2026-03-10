@echo off
setlocal

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"
set TARGET=%~1
if "%TARGET%"=="" set TARGET=:app
if not "%TARGET:~0,1%"==":" set TARGET=:%TARGET%

set MODE=%~2
if "%MODE%"=="" set MODE=compile
set DEBUG_PACKAGE=com.example.openxcpro.debug

set TASK=
if /I "%MODE%"=="compile" set TASK=%TARGET%:compileDebugKotlin
if /I "%MODE%"=="assemble" set TASK=%TARGET%:assembleDebug
if /I "%MODE%"=="install" (
    if /I not "%TARGET%"==":app" (
        echo ERROR: install mode supports app only. Use target app.
        exit /b 1
    )
    set TASK=:app:installDebug
)
if /I "%MODE%"=="reinstall" (
    if /I not "%TARGET%"==":app" (
        echo ERROR: reinstall mode supports app only. Use target app.
        exit /b 1
    )
    set TASK=:app:installDebug
)

if "%TASK%"=="" (
    echo Usage: dev-fast.bat [target] [mode]
    echo   target: app ^| feature:map ^| any Gradle module path
    echo   mode: compile ^| assemble ^| install ^| reinstall
    echo.
    echo Examples:
    echo   dev-fast.bat
    echo   dev-fast.bat feature:map compile
    echo   dev-fast.bat feature:map assemble
    echo   dev-fast.bat app install
    echo   dev-fast.bat app reinstall
    exit /b 1
)

echo Running fast dev task: %TASK%
if /I "%MODE%"=="reinstall" call :require_adb_device

call :run_task
if %ERRORLEVEL% neq 0 (
    echo ERROR: %TASK% failed
    exit /b 1
)

echo SUCCESS: %TASK%
endlocal
exit /b 0

:run_task
if /I "%MODE%"=="reinstall" (
    call adb shell am force-stop %DEBUG_PACKAGE% >nul 2>nul
    call adb uninstall %DEBUG_PACKAGE% >nul 2>nul
    call "%GRADLE%" :app:clean :feature:map:clean :feature:variometer:clean %TASK% --no-build-cache --no-configuration-cache --rerun-tasks --console=plain
    exit /b %ERRORLEVEL%
)
call "%GRADLE%" %TASK% --parallel --build-cache --configuration-cache --console=plain
exit /b %ERRORLEVEL%

:require_adb_device
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: adb not found in PATH.
    exit /b 1
)
call adb start-server >nul 2>nul
set DEVICE_FOUND=0
for /f "skip=1 tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" set DEVICE_FOUND=1
)
if not "%DEVICE_FOUND%"=="1" (
    echo ERROR: no authorized device/emulator attached.
    exit /b 1
)
exit /b 0
