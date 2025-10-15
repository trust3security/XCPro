@echo off
echo Starting automated build and test cycle...
echo.

echo [1/5] Cleaning project...
call gradlew clean
if %ERRORLEVEL% neq 0 (
    echo ERROR: Clean failed
    exit /b 1
)

echo [2/5] Building debug version...
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    exit /b 1
)

echo [3/5] Running lint checks...
call gradlew lint
if %ERRORLEVEL% neq 0 (
    echo WARNING: Lint issues found - check reports
)

echo [4/5] Running unit tests...
call gradlew test
if %ERRORLEVEL% neq 0 (
    echo ERROR: Unit tests failed
    exit /b 1
)

echo [5/5] Running instrumented tests (requires connected device)...
call gradlew connectedAndroidTest
if %ERRORLEVEL% neq 0 (
    echo WARNING: Instrumented tests failed or no device connected
)

echo.
echo ========================================
echo Automated testing completed!
echo Check the following for detailed reports:
echo - Build: app/build/reports/
echo - Lint: app/build/reports/lint-results.html
echo - Tests: app/build/reports/tests/
echo ========================================