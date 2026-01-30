@echo off
echo Installing existing APK on phone...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo oe Install failed! Make sure phone is connected.
) else (
    echo ... Install successful!
    echo Starting app...
    adb shell am start -n com.example.xcpro/.MainActivity
)
pause
