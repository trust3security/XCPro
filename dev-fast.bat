@echo off
setlocal

set TARGET=%~1
if "%TARGET%"=="" set TARGET=:app
if not "%TARGET:~0,1%"==":" set TARGET=:%TARGET%

set MODE=%~2
if "%MODE%"=="" set MODE=compile
set TEST_FILTER=%~3

set TASK=
if /I "%MODE%"=="compile" set TASK=%TARGET%:compileDebugKotlin
if /I "%MODE%"=="assemble" set TASK=%TARGET%:assembleDebug
if /I "%MODE%"=="test" set TASK=%TARGET%:testDebugUnitTest
if /I "%MODE%"=="test-clean" set TASK=%TARGET%:testDebugUnitTest
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
    echo   mode: compile ^| assemble ^| test ^| test-clean ^| install
    echo   for mode=test/test-clean you may pass [testFilter] as 3rd arg
    echo.
    echo Examples:
    echo   dev-fast.bat
    echo   dev-fast.bat feature:map compile
    echo   dev-fast.bat feature:map assemble
    echo   dev-fast.bat feature:map test com.example.xcpro.ogn.OgnGliderTrailRepositoryTest
    echo   dev-fast.bat feature:map test-clean com.example.xcpro.ogn.OgnGliderTrailRepositoryTest
    echo   dev-fast.bat app install
    exit /b 1
)

echo Running fast dev task: %TASK%
if /I "%MODE%"=="test" call :cleanup_stale_gradle_workers
if /I "%MODE%"=="test-clean" call :cleanup_stale_gradle_workers

call :run_task
if %ERRORLEVEL% neq 0 (
    echo First attempt failed. Cleaning stale Gradle workers and retrying once...
    call :cleanup_stale_gradle_workers
    call .\gradlew.bat --stop
    call :run_task
    if %ERRORLEVEL% neq 0 (
        echo ERROR: %TASK% failed after retry
        exit /b 1
    )
)

echo SUCCESS: %TASK%
endlocal
exit /b 0

:run_task
if /I "%MODE%"=="test-clean" (
    if not "%TEST_FILTER%"=="" (
        call .\gradlew.bat %TARGET%:clean %TASK% --parallel --build-cache --configuration-cache --console=plain --tests "%TEST_FILTER%"
    ) else (
        call .\gradlew.bat %TARGET%:clean %TASK% --parallel --build-cache --configuration-cache --console=plain
    )
    exit /b %ERRORLEVEL%
)
if /I "%MODE%"=="test" (
    if not "%TEST_FILTER%"=="" (
        call .\gradlew.bat %TASK% --parallel --build-cache --configuration-cache --console=plain --tests "%TEST_FILTER%"
    ) else (
        call .\gradlew.bat %TASK% --parallel --build-cache --configuration-cache --console=plain
    )
    exit /b %ERRORLEVEL%
)
call .\gradlew.bat %TASK% --parallel --build-cache --configuration-cache --console=plain
exit /b %ERRORLEVEL%

:cleanup_stale_gradle_workers
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\dev\kill_stale_gradle_processes.ps1" -ProjectRoot "%CD%"
exit /b 0
