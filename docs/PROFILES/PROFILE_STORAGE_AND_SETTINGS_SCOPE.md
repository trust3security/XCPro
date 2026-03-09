# PROFILE STORAGE AND SETTINGS SCOPE

## Profile Storage (Authoritative)

Profile list and active profile id are stored in DataStore:

- DataStore name: `profile_preferences`
- Keys:
  - `profiles_json`
  - `active_profile_id`

File:

- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileStorage.kt`

## Why Settings Can Look "Lost"

Many UI and map settings are namespaced by `profileId` using keys like
`profile_<profileId>_...` in SharedPreferences/DataStore repositories.

Examples:

- `feature/map/.../ThemePreferencesRepository.kt`
- `feature/map/.../LookAndFeelPreferences.kt`
- `feature/map/.../FlightMgmtPreferencesRepository.kt`

If app startup lands on a different profile id (for example after creating a
new profile), those repositories read a different settings namespace.
The old settings still exist but under the previous profile id.

## Current Mitigation

- Empty bootstrap now provisions one stable default profile (`default-profile`).
- Active profile repair is deterministic at bootstrap.
- Bootstrap and profile mutations are serialized.
- Profile snapshot is mirrored to a public backup folder for user copy/USB flows:
  `Download/XCPro/profiles/`.
- Backup layout:
  - Per-profile files: `profile_<sanitized-id>_<hash>.json`
  - Active/index metadata: `profiles_index.json`

This reduces accidental profile-id churn during startup and keeps settings attached
to a stable profile identity by default.

## App Identity Note

Changing app package identity (`applicationId` or debug suffix) creates a different
Android sandbox and appears as empty profile storage.

Reference:

- `app/build.gradle.kts`
- `docs/ARCHITECTURE/README.md` (Profile sandbox contract)
