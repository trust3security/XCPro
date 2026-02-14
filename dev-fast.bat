@echo off
setlocal

set TARGET=%~1
if "%TARGET%"=="" set TARGET=:app
if not "%TARGET:~0,1%"==":" set TARGET=:%TARGET%

set MODE=%~2
if "%MODE%"=="" set MODE=compile

set TASK=
if /I "%MODE%"=="compile" set TASK=%TARGET%:compileDebugKotlin
if /I "%MODE%"=="assemble" set TASK=%TARGET%:assembleDebug
if /I "%MODE%"=="test" set TASK=%TARGET%:testDebugUnitTest
if /I "%MODE%"=="install" (
    if /I not "%TARGET%"==":app" (
        echo ERROR: install mode supports app only. Use target app.
        exit /b 1
    )
    set TASK=:app:installDebug
)

if "%TASK%"=="" (
    echo Usage: dev-fast.bat [target] [mode]
    echo   target: app ^| feature:map ^| any Gradle module path
    echo   mode: compile ^| assemble ^| test ^| install
    echo.
    echo Examples:
    echo   dev-fast.bat
    echo   dev-fast.bat feature:map compile
    echo   dev-fast.bat feature:map assemble
    echo   dev-fast.bat app install
    exit /b 1
)

echo Running fast dev task: %TASK%
call .\gradlew.bat %TASK% --parallel --build-cache
if errorlevel 1 (
    echo ERROR: %TASK% failed
    exit /b 1
)

echo SUCCESS: %TASK%
endlocal
