@echo off
echo Building debug APK...
call .\gradlew.bat assembleDebug
echo.
if errorlevel 1 (
    echo oe Build failed!
) else (
    echo ... Build successful!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
)
pause
