# PROFILES

This folder documents profile behavior in XCPro.

## Code Locations

- Profile feature module: `feature/profile/`
- Main profile package: `feature/profile/src/main/java/com/example/xcpro/profiles/`
- Startup gate that decides profile selection vs app content:
  `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`

## Documents

- `PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md`
- `PROFILE_STARTUP_AND_DEFAULT_POLICY.md`
- `PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`
- `PROFILE_TROUBLESHOOTING.md`
- `MANUAL_E2E_PROFILE_RESTORE_CHECKLIST_2026-03-07.md`
- `CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07.md`
- `PROFILE_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`
- `PROFILE_PRODUCTION_GRADE_PHASED_IP_AIRCRAFT_PROFILE_FILES_2026-03-10.md`
- `PROFILE_PRODUCTION_GRADE_PHASED_IP_PROFILE_GAP_CLOSURE_2026-03-10.md`
- `AGENT_CONTRACT_PROFILE_PHASES_0_TO_6_2026-03-07.md`
- `AGENT_CONTRACT_PROFILE_PHASES_0_TO_6_COLLAB_2026-03-08.md`
- `AGENT_CONTRACT_PROFILE_GAP_CLOSURE_AUTOMATION_2026-03-10.md`
- `EXECUTION_LOG_PROFILE_PHASES_0_6.md`
- `EXECUTION_LOG_PROFILE_PHASES_0_6_COLLAB_2026-03-08.md`

## Starter Files

- `examples/xcpro-aircraft-profile-sailplane-asg-29-2026-03-10.json`
- `examples/xcpro-aircraft-profile-hang-glider-moyes-litespeed-rs-2026-03-10.json`

These are minimal portable aircraft-profile files using the current `2.0`
bundle schema with one profile and an empty settings snapshot. They are intended
to validate the first real workflow:

- import one file
- import the second file
- switch between the two profiles in-app
- export one profile back out through the Android document picker

## Why This Exists

Profile identity is used as a key for many user settings in map/navdrawer flows.
If active profile identity changes, settings can appear missing even when they still exist
under a different profile key.

For current product direction, treat
`PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md` as the user-facing decision
document for portable aircraft-profile files.
