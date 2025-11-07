# Map Feature Test Status - November 7, 2025

## What's Done
- Added `MapScreenStateTest` to assert SSOT flows (`updateFlightMode`, `showRecenterButtonFlow`, `updateMapStyle`).
- Added Compose UI tags/slots plus `MapTaskScreenUiTest` for bottom sheet and minimized indicator visibility.
- Updated `feature/map/build.gradle.kts` with Compose test dependencies.

## Blocker
- `:feature:map:testDebugUnitTest` fails before execution because Windows can't delete `feature\map\build\test-results\testDebugUnitTest\binary\output.bin`.
- Every Gradle run (even `--no-daemon` after deleting `feature\map\build`) recreates the file and immediately hits the same lock.

## To Resume
1. **Release the file handle**
   - Close Android Studio plus any explorer windows showing `feature\map\build`.
   - Run Sysinternals Handle to find the owning PID:
     `C:\Users\Asus\Tools\Handle\handle.exe "C:\Users\Asus\AndroidStudioProjects\XCPro\feature\map\build\test-results\testDebugUnitTest\binary\output.bin"`
   - `Stop-Process -Id <PID> -Force` (or close via Process Explorer).
   - If no process shows up yet deletion still fails, reboot to flush stale handles.
2. **Clean folder**
   `Remove-Item -Path 'feature\map\build\test-results\testDebugUnitTest' -Recurse -Force`
3. **Re-run tests**
   `./gradlew.bat :feature:map:testDebugUnitTest --no-daemon`
4. If the task now runs but tests fail, capture the stack trace and continue fixing; otherwise confirm a green build.

## Files Touched
- `feature/map/src/main/java/com/example/xcpro/map/ui/task/MapTaskScreenUi.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenStateTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/ui/task/MapTaskScreenUiTest.kt`
- `feature/map/build.gradle.kts`

> Once the OS releases `output.bin`, everything should compile and test normally.
