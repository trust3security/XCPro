@echo off
setlocal EnableExtensions

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"
set "SCOPE=%~1"
set "MODE=%~2"

if "%SCOPE%"=="" set "SCOPE=map"
if "%MODE%"=="" set "MODE=assemble"

call :resolve_scope "%SCOPE%"
if %ERRORLEVEL% neq 0 goto :usage

call :resolve_task
if %ERRORLEVEL% neq 0 goto :usage

pushd "%REPO_ROOT%" >nul

echo Repairing generated build state for %DISPLAY_SCOPE%...
echo.

call "%GRADLE%" --stop >nul 2>nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%REPO_ROOT%scripts\dev\kill_stale_gradle_processes.ps1" -ProjectRoot "%REPO_ROOT%."

call :cleanup_scope
call :purge_gradle_wrapper_locks

if /I "%MODE%"=="none" (
    echo.
    echo Repair completed. No Gradle task requested.
    popd >nul
    exit /b 0
)

echo Re-running %TASK%...
call .\scripts\dev\gradle-run-with-lock-recovery.bat "%GRADLE%" %TASK% --daemon --parallel --build-cache --configuration-cache --console=plain
set "EXIT_CODE=%ERRORLEVEL%"

popd >nul
exit /b %EXIT_CODE%

:resolve_scope
set "RAW_SCOPE=%~1"
set "MODULE_SCOPE="
set "DISPLAY_SCOPE="

if /I "%RAW_SCOPE%"=="map" (
    set "MODULE_SCOPE=feature:map"
    set "DISPLAY_SCOPE=feature:map"
    exit /b 0
)
if /I "%RAW_SCOPE%"=="app" (
    set "MODULE_SCOPE=app"
    set "DISPLAY_SCOPE=app"
    exit /b 0
)
if /I "%RAW_SCOPE%"=="profile" (
    set "MODULE_SCOPE=feature:profile"
    set "DISPLAY_SCOPE=feature:profile"
    exit /b 0
)
if /I "%RAW_SCOPE%"=="variometer" (
    set "MODULE_SCOPE=feature:variometer"
    set "DISPLAY_SCOPE=feature:variometer"
    exit /b 0
)
if /I "%RAW_SCOPE%"=="cards" (
    set "MODULE_SCOPE=dfcards-library"
    set "DISPLAY_SCOPE=dfcards-library"
    exit /b 0
)
if /I "%RAW_SCOPE%"=="all" (
    set "MODULE_SCOPE=all"
    set "DISPLAY_SCOPE=all KSP modules"
    exit /b 0
)

set "RAW_SCOPE=%RAW_SCOPE:"=%"
if "%RAW_SCOPE:~0,1%"==":" set "RAW_SCOPE=%RAW_SCOPE:~1%"
set "FS_SCOPE=%RAW_SCOPE::=\%"

if exist "%REPO_ROOT%%FS_SCOPE%\build.gradle.kts" (
    set "MODULE_SCOPE=%RAW_SCOPE%"
    set "DISPLAY_SCOPE=%RAW_SCOPE%"
    exit /b 0
)

echo ERROR: Unknown scope '%~1'.
exit /b 1

:resolve_task
set "TASK="

if /I "%MODE%"=="none" exit /b 0

if /I "%MODULE_SCOPE%"=="all" (
    if /I "%MODE%"=="compile" set "TASK=:app:compileDebugKotlin"
    if /I "%MODE%"=="assemble" set "TASK=:app:assembleDebug"
    if /I "%MODE%"=="test" set "TASK=testDebugUnitTest"
)

if not "%TASK%"=="" exit /b 0

if /I "%MODE%"=="compile" set "TASK=:%MODULE_SCOPE%:compileDebugKotlin"
if /I "%MODE%"=="assemble" set "TASK=:%MODULE_SCOPE%:assembleDebug"
if /I "%MODE%"=="test" set "TASK=:%MODULE_SCOPE%:testDebugUnitTest"

if not "%TASK%"=="" exit /b 0

echo ERROR: Unknown mode '%MODE%'.
exit /b 1

:cleanup_scope
if /I "%MODULE_SCOPE%"=="all" (
    call :cleanup_module "app"
    call :cleanup_module "feature:map"
    call :cleanup_module "feature:profile"
    call :cleanup_module "feature:variometer"
    call :cleanup_module "dfcards-library"
    exit /b 0
)

call :cleanup_module "%MODULE_SCOPE%"
exit /b 0

:cleanup_module
set "MODULE_PATH=%~1"
set "FS_MODULE=%MODULE_PATH::=\%"

if exist "%REPO_ROOT%%FS_MODULE%\build\kspCaches" (
    echo Removing %FS_MODULE%\build\kspCaches
    rmdir /s /q "%REPO_ROOT%%FS_MODULE%\build\kspCaches"
)
if exist "%REPO_ROOT%%FS_MODULE%\build\generated\ksp" (
    echo Removing %FS_MODULE%\build\generated\ksp
    rmdir /s /q "%REPO_ROOT%%FS_MODULE%\build\generated\ksp"
)
if exist "%REPO_ROOT%%FS_MODULE%\build\test-results\testDebugUnitTest\binary" (
    echo Removing %FS_MODULE%\build\test-results\testDebugUnitTest\binary
    rmdir /s /q "%REPO_ROOT%%FS_MODULE%\build\test-results\testDebugUnitTest\binary"
)
exit /b 0

:purge_gradle_wrapper_locks
for /r "%USERPROFILE%\.gradle\wrapper\dists" %%F in (*.lck) do del /f /q "%%F" >nul 2>nul
exit /b 0

:usage
echo Usage:
echo   repair-build.bat [scope] [mode]
echo.
echo Scope:
echo   map ^| app ^| profile ^| variometer ^| cards ^| all ^| Gradle module path
echo.
echo Mode:
echo   compile ^| assemble ^| test ^| none
echo.
echo Examples:
echo   repair-build.bat
echo   repair-build.bat feature:map test
echo   repair-build.bat all assemble
echo   repair-build.bat app none
exit /b 1
