# PROFILE STARTUP AND DEFAULT POLICY

## Scope

This document describes app startup behavior for profiles and the deterministic
fallback policy for empty profile storage.

## Startup Flow (Current)

1. `ProfileRepository` subscribes to `ProfileStorage.snapshotFlow`.
2. `ProfileViewModel` combines profiles, active profile, bootstrap completion, and bootstrap error.
3. `MainActivityScreen` gates startup:
   - If not hydrated: loading spinner
   - If active profile is null and bootstrap error exists: storage error UI
   - Else if no profile or no active profile: profile selection UI
   - Else: normal app content

Primary files:

- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`
- `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`

## Default Profile Policy (Current)

When a valid storage snapshot is empty, repository bootstrap creates a deterministic
default profile and persists it atomically with active profile id.

Policy values:

- Default profile id: `default-profile`
- Default profile name: `Default`
- Default aircraft type: `PARAGLIDER`

Implementation anchors:

- `DEFAULT_PROFILE_ID` in `ProfileRepository.kt`
- `ensureBootstrapProfile(...)` in `ProfileRepository.kt`
- `persistState(...)` bootstrap repair path in `ProfileRepository.kt`

## Determinism and Race Protection

- Snapshot hydration is serialized through the same mutex as profile mutations.
- Mutations wait for bootstrap completion before write operations.
- This avoids startup-vs-mutation interleaving that can flip active profile state.
- Startup and profile mutations also trigger backup mirror sync to
  `Download/XCPro/profiles/`, which creates the folder path when missing.
- Backup files are written as one JSON file per profile plus
  `profiles_index.json` for active profile/index metadata.

Implementation anchors:

- `handleStorageSnapshot(...)` mutex path
- `awaitBootstrapCompletion()`
- `createProfile(...)`, `importProfiles(...)`, `setActiveProfile(...)`,
  `updateProfile(...)`, `deleteProfile(...)`

## Deletion Rule

Default profile is protected and cannot be deleted.

- Guard: `if (profileId == DEFAULT_PROFILE_ID) error("Cannot delete the default profile")`
