@echo off
setlocal

set "REPO_ROOT=%~dp0"
set "GRADLE=%REPO_ROOT%gradlew.bat"

echo Building debug APK...
call "%GRADLE%" assembleDebug
echo.
if errorlevel 1 (
    echo ERROR: Build failed!
) else (
    echo ... Build successful!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
)
pause
