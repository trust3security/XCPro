# PROFILE_PRODUCTION_GRADE_PHASED_IP_PROFILE_GAP_CLOSURE_2026-03-10

## Purpose

Create a focused, production-grade implementation plan to close the current
"not saved in profile" gaps so aircraft profiles are more complete and
portable across devices.

This plan is intentionally scoped to gaps identified on `2026-03-10`.

## Goal

After completion, a pilot can export/import aircraft profiles and retain the
missing settings that are aircraft-relevant, without breaking SSOT, MVVM/UDF,
or security boundaries.

## Scope

In scope (gap closure targets):

1. Map style setting
2. Snail trail settings
3. Orientation settings fields not currently in snapshot
4. QNH manual preference (with safety guardrails)
5. Waypoint file selection manifest state
6. Airspace file selection + selected classes manifest state
7. ADS-B default-medium-unknown rollout flags currently not in snapshot
8. Forecast credentials portability policy and implementation path

Out of scope:

1. Raw waypoint/airspace file binaries in profile JSON
2. Replay working state/history
3. Nav drawer expansion/collapse UI state

## Architecture Contract

### SSOT Ownership (target)

| Data | Owner | Profile scoped | Profile section |
|---|---|---|---|
| Map style | `MapStyleRepository` (or profile-scoped replacement) | Yes | `tier_a.map_style_preferences` |
| Snail trail settings | `MapTrailPreferences` | Yes | `tier_a.snail_trail_preferences` |
| Orientation extended fields | `MapOrientationSettingsRepository` / `MapOrientationPreferences` | Yes | `tier_a.orientation_preferences` (expanded payload) |
| Manual QNH | `QnhPreferencesRepository` / `QnhRepositoryImpl` | Yes | `tier_a.qnh_preferences` |
| Waypoint selection manifest | `WaypointFilesRepository` + config adapter | Yes | `tier_a.waypoint_file_preferences` |
| Airspace selection + classes manifest | `AirspaceRepository` + config adapter | Yes | `tier_a.airspace_preferences` |
| ADS-B rollout flags (default-medium-unknown) | `AdsbTrafficPreferencesRepository` | No (global unless policy changes) | `tier_a.adsb_traffic_preferences` (expanded payload) |
| Forecast credentials | `ForecastCredentialsRepository` | Policy-driven | dedicated secure path (see Phase 5) |

### Dependency Direction

Must remain:

`UI -> use case -> repository/data`

No ViewModel direct persistence/file I/O.

### Time Base

| Value | Time base | Rule |
|---|---|---|
| Export timestamp | Wall | metadata only |
| QNH recency guard timestamp | Wall | safety gating only, never fusion timing |
| Runtime profile switching | n/a | no new time coupling |

### Replay Determinism

Replay behavior must remain unchanged by this work. Profile snapshot/restore
changes must not alter replay clocks, replay pipelines, or fusion timing.

## Gap Decisions (Focused)

### A) Include in profile by default

1. Map style
2. Snail trail settings
3. Orientation extra fields:
   - `autoResetEnabled`
   - `autoResetTimeoutSeconds`
   - `bearingSmoothingEnabled`
4. Waypoint selection manifest (file names + enabled flags only)
5. Airspace selection manifest + selected classes (no file binary embedding)
6. ADS-B default-medium-unknown rollout fields

### B) Include with safety controls

1. Manual QNH
   - Save value + captured-at timestamp
   - Import applies only when recency guard passes, otherwise staged as pending

### C) Credentials policy

Forecast credentials are not safe in plain bundle JSON by default.

Production policy:

1. Default behavior: excluded from normal profile export/import
2. Optional behavior (Phase 5): explicit user-opt-in secure credentials export
   path with encryption and warning UX

## Implementation Phases

## Phase 0 - Contract Freeze

Goal:

1. Freeze section taxonomy and include/exclude policy for this gap set.

Files:

1. `docs/PROFILES/PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`
2. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSnapshot.kt`
3. this plan document

Tests:

1. Section-ID presence tests for new IDs.

Exit criteria:

1. New section IDs approved and documented.
2. Credentials policy explicitly approved (default exclude vs secure opt-in).

## Phase 1 - Repository Profile Scoping

Goal:

1. Make runtime owners profile-aware where needed so profile switching works
   without full import every time.

Changes:

1. Map style owner: profile key namespace support.
2. Snail trail owner: profile key namespace support.
3. QNH preference owner: profile key namespace support (with metadata timestamp).
4. Waypoint manifest owner: profile key namespace for selected files.
5. Airspace manifest owner: profile key namespace for selected files/classes.

Files (expected):

1. `feature/map/src/main/java/com/example/xcpro/map/MapStyleRepository.kt`
2. `feature/map/src/main/java/com/example/xcpro/map/trail/MapTrailPreferences.kt`
3. `feature/map/src/main/java/com/example/xcpro/map/QnhPreferencesRepository.kt`
4. `feature/map/src/main/java/com/example/xcpro/ConfigurationRepository.kt`
5. `feature/map/src/main/java/com/example/xcpro/flightdata/WaypointFilesRepository.kt`
6. `feature/map/src/main/java/com/example/xcpro/utils/AirspaceRepository.kt`

Tests:

1. Profile A/B isolation tests per repository.
2. Active-profile switch hydration tests.

Exit criteria:

1. Switching active profile updates these settings in runtime without
   cross-profile bleed.

## Phase 2 - Snapshot/Restore Schema Expansion

Goal:

1. Add missing sections/fields to profile snapshot and restore paths.

Changes:

1. Add new section snapshots:
   - `map_style_preferences`
   - `snail_trail_preferences`
   - `qnh_preferences`
   - `waypoint_file_preferences`
   - `airspace_preferences`
2. Extend `orientation_preferences` payload with missing fields.
3. Extend `adsb_traffic_preferences` payload with default-medium-unknown
   rollout fields.
4. Add restore logic with backward compatibility for older bundles.

Files (expected):

1. `app/src/main/java/com/example/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
2. `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
3. `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
4. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSnapshot.kt`

Tests:

1. New section round-trip tests.
2. Legacy-bundle compatibility tests.
3. Partial-failure section reporting tests.

Exit criteria:

1. Export includes new section payloads.
2. Import restores successfully and reports failures per section.

## Phase 3 - Profile Scope Import and Cleanup Integration

Goal:

1. Ensure new profile-scoped data participates in import-scope filtering and
   profile deletion cleanup.

Changes:

1. Add new profile-scoped section IDs to scoped import filter set.
2. Extend profile-scoped data cleaner to clear new repositories.
3. Ensure active profile switch wiring updates all new profile-aware owners.

Files (expected):

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
2. `app/src/main/java/com/example/xcpro/profiles/AppProfileScopedDataCleaner.kt`
3. map/profile switch runtime wiring classes

Tests:

1. `PROFILE_SCOPED_SETTINGS` import-scope behavior tests.
2. Profile delete cleanup tests.

Exit criteria:

1. No orphaned settings remain after profile deletion.
2. Scoped import restores only intended sections.

## Phase 4 - QNH Safety Hardening

Goal:

1. Make QNH portability safe for real-world usage.

Changes:

1. Add QNH metadata (`capturedAtWallMs`, source marker).
2. Add recency guard on import (policy threshold configurable).
3. If stale: do not auto-apply to live QNH; surface "staged/not applied".

Tests:

1. Fresh QNH applies.
2. Stale QNH does not auto-apply.
3. Behavior is deterministic and policy-driven.

Exit criteria:

1. Import cannot silently apply stale manual QNH.

## Phase 5 - Secure Credentials Portability (Optional but Planned)

Goal:

1. Provide secure, explicit forecast-credential portability if product approves.

Changes:

1. Add opt-in "Export credentials securely" flow.
2. Use encrypted payload (separate artifact or encrypted section) with explicit
   warning and passphrase/keystore policy.
3. Keep default profile export credential-free.

Tests:

1. Credentials excluded in default export.
2. Secure export/import path decrypts correctly.
3. Wrong key/passphrase fails safely.

Exit criteria:

1. No plain-text credentials in normal profile bundle.

## Phase 6 - Production Verification and Release Gate

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Targeted profile checks:

```bash
./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.profiles.*"
./gradlew :feature:map:testDebugUnitTest --tests "*Profile*"
./gradlew :feature:profile:testDebugUnitTest
```

Manual verification:

1. Create two aircraft profiles with different values for each new section.
2. Switch between profiles and verify runtime changes.
3. Export one profile and import on another device/install.
4. Verify missing files are reported (waypoint/airspace manifest) but import is
   not corrupted.
5. Verify QNH stale guard behavior.

Exit criteria:

1. All new sections pass round-trip and switch-isolation behavior.
2. No architecture-rule drift.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Cross-profile setting bleed | Wrong aircraft setup at runtime | repository-level profile namespace tests |
| File-manifest import points to missing local files | Broken overlays/lists after import | mark missing entries and keep import non-fatal |
| Stale QNH applied after migration | Flight-safety risk | recency guard + staged-not-applied policy |
| Credentials leaked via export JSON | Security/privacy incident | default exclude + encrypted opt-in only |
| Schema drift across versions | Import failures | backward-compatible payload parsing + version tests |

## Acceptance Gates

1. New profile data has one SSOT owner per setting.
2. No business logic in UI/ViewModels.
3. Import scopes behave exactly as declared.
4. Replay determinism unaffected.
5. Credentials are never exported in plain text by default.

## Immediate Execution Order

1. Phase 0 (contract freeze)
2. Phase 1 (runtime profile scoping)
3. Phase 2 (snapshot/restore expansion)
4. Phase 3 (scope/cleanup integration)
5. Phase 4 (QNH safety hardening)
6. Phase 5 (secure credentials portability if approved)
7. Phase 6 (release verification)

