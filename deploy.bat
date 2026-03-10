@echo off
setlocal

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"

echo ====================================
echo Building and Deploying to Phone
echo ====================================

echo.
echo [1/3] Building debug APK...
call .\scripts\dev\gradle-run-with-lock-recovery.bat "%GRADLE%" assembleDebug
if errorlevel 1 (
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo.
echo [2/3] Installing on phone...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo ERROR: Install failed!
    echo Make sure your phone is connected and USB debugging is enabled.
    pause
    exit /b 1
)

echo.
echo [3/3] Starting app...
adb shell am start -n com.example.xcpro/.MainActivity

echo.
echo ... SUCCESS: App deployed and started!
echo.
endlocal
pause
