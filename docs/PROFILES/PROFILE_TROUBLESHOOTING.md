# PROFILE TROUBLESHOOTING

## Symptom: App asks to create/select profile at startup

Check:

1. Is bootstrap complete?
   - UI should leave loading spinner first.
2. Is there a bootstrap storage error on screen?
   - If yes, investigate DataStore read failures.
3. Is app package identity unchanged?
   - Debug and release are separate sandboxes.

Primary files:

- `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileStorage.kt`

## Symptom: "I created a new profile and lost my settings"

Likely cause:

- Settings are keyed by profile id (`profile_<id>_...`).
- New profile id means a new settings namespace.

Action:

1. Switch back to the previous profile from profile selection.
2. If startup repeatedly falls into empty state, inspect storage and package identity.

## Startup Recovery Actions

When bootstrap error is shown in profile selection:

1. `Recover Default`
   - Forces canonical `default-profile` to exist and become active.
2. `Import Backup`
   - Import a managed `*_bundle_latest.json` profile bundle.

Expected diagnostics events in logcat tag `ProfileDiagnostics`:

- `profile_bootstrap_read_error`
- `profile_bootstrap_parse_failed`
- `profile_recovery_start`
- `profile_recovery_success` or `profile_recovery_failure`
- `profile_bundle_import_success` / `profile_bundle_import_failure`
- `profile_bundle_export_success` / `profile_bundle_export_failure`

Runtime diagnostics sink:

- `app/src/main/java/com/example/xcpro/profiles/AppProfileDiagnosticsReporter.kt`
  keeps the latest 200 profile diagnostics events in-memory for in-process troubleshooting.

## Expected Baseline After Fix

- On empty valid storage, app auto-creates one default profile.
- Active profile should not be null after successful bootstrap.
- Default profile should not be deletable.
- Public backup folder should exist at `Download/XCPro/profiles/` after
  startup/profile changes.
- Folder should contain namespaced managed artifacts:
  - `*_bundle_latest.json`
  - `*_profiles_index.json`
  - `*_profile_settings.json`
  - one `*_profile_*.json` file per profile.

## Useful Verification Commands

- `./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.profiles.ProfileRepositoryTest"`
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
