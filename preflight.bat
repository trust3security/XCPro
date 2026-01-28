@echo off
echo Running preflight checks...
echo.

echo [1/3] Enforcing architecture/coding rules...
call gradlew enforceRules
if %ERRORLEVEL% neq 0 (
    echo ERROR: Rule enforcement failed
    exit /b 1
)

echo [2/3] Building debug version...
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    exit /b 1
)

echo [3/3] Running unit tests...
call gradlew test
if %ERRORLEVEL% neq 0 (
    echo ERROR: Unit tests failed
    exit /b 1
)

echo.
echo ========================================
echo Preflight checks completed successfully!
echo ========================================
