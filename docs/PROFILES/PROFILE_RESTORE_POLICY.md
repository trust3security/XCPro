# Profile Restore Policy

Date: 2026-04-03
Status: Active
Owner: `feature:profile`

## Purpose

Define release-grade behavior for profile restore, reinstall, and clean-start testing.

## Product Policy

- Profiles are treated as user data.
- Android backup and device transfer remain enabled for profile state.
- Uninstall and reinstall may restore profiles if Android restores app-private data.
- Restore is silent when the restored state is valid and an active profile resolves successfully.
- The first-launch aircraft picker is shown only when hydration completes with no valid profiles and no bootstrap error.

## Authoritative Storage

- Startup truth is the app-private DataStore profile store.
- For the debug app package, the canonical private file is:
  - `/data/user/0/com.example.openxcpro.debug/files/datastore/profile_preferences.preferences_pb`
- The managed Downloads mirror is not startup truth. It is user-visible export/debug output only:
  - `/sdcard/Download/XCPro/profiles/`
  - `*_bundle_latest.json`
  - `*_profiles_index.json`
  - per-profile/settings JSON files

## Startup Outcomes

- Valid existing or restored profile state:
  - skip first-launch picker
  - enter normal app flow
- Empty valid storage:
  - show first-launch aircraft picker
- Parse/read/bootstrap failure:
  - show explicit recovery UI
  - do not treat the state as clean first launch

## QA Guidance

- To test a true clean start, clear app data:
  - `adb shell pm clear com.example.openxcpro.debug`
- To inspect private stored profile state:
  - `adb shell run-as com.example.openxcpro.debug ls /data/user/0/com.example.openxcpro.debug/files/datastore`
- To inspect the public managed backup mirror:
  - `adb shell ls /sdcard/Download/XCPro/profiles`
- Uninstall/reinstall alone is not a reliable clean-start test while Android backup restore is enabled.
