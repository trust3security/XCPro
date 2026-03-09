# PROFILES

This folder documents profile behavior in XCPro.

## Code Locations

- Profile feature module: `feature/profile/`
- Main profile package: `feature/profile/src/main/java/com/example/xcpro/profiles/`
- Startup gate that decides profile selection vs app content:
  `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`

## Documents

- `PROFILE_STARTUP_AND_DEFAULT_POLICY.md`
- `PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`
- `PROFILE_TROUBLESHOOTING.md`
- `MANUAL_E2E_PROFILE_RESTORE_CHECKLIST_2026-03-07.md`
- `CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07.md`
- `PROFILE_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`
- `AGENT_CONTRACT_PROFILE_PHASES_0_TO_6_2026-03-07.md`
- `AGENT_CONTRACT_PROFILE_PHASES_0_TO_6_COLLAB_2026-03-08.md`
- `EXECUTION_LOG_PROFILE_PHASES_0_6.md`
- `EXECUTION_LOG_PROFILE_PHASES_0_6_COLLAB_2026-03-08.md`

## Why This Exists

Profile identity is used as a key for many user settings in map/navdrawer flows.
If active profile identity changes, settings can appear missing even when they still exist
under a different profile key.
