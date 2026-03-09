# MANUAL E2E PROFILE RESTORE CHECKLIST (DEVICE)

Use this for fast on-device validation of full profile persistence and backup/restore.

## Preconditions

1. App is installed in debug variant (`com.example.openxcpro.debug`).
2. Device is connected and visible via `adb devices`.
3. Profile backup folder is accessible at `/sdcard/Download/XCPro/profiles/`.

## Session Setup

1. Clear profile logs:
   - `adb logcat -c`
2. Start clean app session:
   - `adb shell am force-stop com.example.openxcpro.debug`
   - `adb shell monkey -p com.example.openxcpro.debug -c android.intent.category.LAUNCHER 1`

## E2E Flow

1. Create/select a test profile (example name: `E2E_A`).
2. Change and save settings in this profile:
   - Move at least one draggable map widget (hamburger/settings/card cluster).
   - Change card layout/position selection.
   - Change at least one theme/look-and-feel setting.
   - Change at least one glider polar value.
3. Force-stop and relaunch app:
   - `adb shell am force-stop com.example.openxcpro.debug`
   - `adb shell monkey -p com.example.openxcpro.debug -c android.intent.category.LAUNCHER 1`
4. Verify all values from step 2 are still present.
5. Export profile bundle from profile UI.
6. Verify managed backup artifacts exist:
   - `adb shell ls -la /sdcard/Download/XCPro/profiles/`
   - Expect namespaced files such as:
     - `*_bundle_latest.json`
     - `*_profiles_index.json`
     - `*_profile_settings.json`
     - `*_profile_*.json`
7. Create/select a second profile (example `E2E_B`) and change values so it is visibly different.
8. Import `*_bundle_latest.json` from `/Download/XCPro/profiles/`.
9. Select imported `E2E_A` and verify:
   - Card/map widget positions restored.
   - Theme/look-and-feel restored.
   - Glider polar restored.
10. Optional guardrail check:
   - Try importing `*_profiles_index.json`.
   - Expect explicit index-only import guidance error.

## Diagnostics Capture

1. Dump profile diagnostics:
   - `adb logcat -d ProfileDiagnostics:D *:S`
2. Confirm expected events appear:
   - `profile_bundle_export_success`
   - `profile_bundle_import_success`
   - If recovery was used: `profile_recovery_start` and `profile_recovery_success`

## Pass Criteria

1. No startup profile-loss prompt after normal restart.
2. Profile-scoped UI/layout/polar settings survive restart.
3. Export creates managed artifacts in `Download/XCPro/profiles/`.
4. Bundle import (`*_bundle_latest.json`) restores expected settings.
5. Diagnostics show successful export/import events and no recovery failures.
