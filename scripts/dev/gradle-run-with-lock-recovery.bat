@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "GRADLE_CMD=%~1"
if "%GRADLE_CMD%"=="" (
    echo ERROR: Missing gradle command.
    exit /b 1
)

shift

if defined XC_DISABLE_GRADLE_PARALLEL (
    echo [gradle-run-with-lock-recovery] XC_DISABLE_GRADLE_PARALLEL=%XC_DISABLE_GRADLE_PARALLEL%
)

set "GRADLE_PARALLEL_MODE="
if not "%XC_DISABLE_GRADLE_PARALLEL%"=="" set "GRADLE_PARALLEL_MODE=--no-parallel"

set "GRADLE_ARGS="

:__collect_args
if "%~1"=="" goto :__run_gradle
if defined GRADLE_ARGS (
    set "GRADLE_ARGS=!GRADLE_ARGS! %1"
) else (
    set "GRADLE_ARGS=%1"
)
shift
goto :__collect_args

:__run_gradle
if defined GRADLE_PARALLEL_MODE (
    echo [gradle-run-with-lock-recovery] forcing --no-parallel mode
    if defined GRADLE_ARGS (
        set "GRADLE_ARGS=%GRADLE_ARGS% %GRADLE_PARALLEL_MODE%"
    ) else (
        set "GRADLE_ARGS=%GRADLE_PARALLEL_MODE%"
    )
)

set "SCRIPT_DIR=%~dp0"
set "RECOVER_SCRIPT=%SCRIPT_DIR%recover-gradle-file-locks.bat"

if not defined GRADLE_ARGS (
    call "%GRADLE_CMD%"
) else (
    call "%GRADLE_CMD%" %GRADLE_ARGS%
)

if %ERRORLEVEL% neq 0 (
    echo [lock-recovery] Gradle command failed. Running one lock-recovery retry...
    if exist "%RECOVER_SCRIPT%" (
    call "%RECOVER_SCRIPT%"
    )
    if not defined GRADLE_ARGS (
        call "%GRADLE_CMD%"
    ) else (
        call "%GRADLE_CMD%" %GRADLE_ARGS%
    )
)

exit /b %ERRORLEVEL%
