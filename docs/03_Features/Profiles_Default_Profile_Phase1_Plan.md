# Profiles Default Profile Phase 1 Plan

Created: 2026-02-17  
Status: Draft  
Owner: XCPro Team  
Scope: First-run and missing-profile recovery baseline

## Goal

Guarantee that app runtime always has a valid active profile by introducing a repository-level
default profile bootstrap and fallback reconciliation.

## Why This First

Many profile-scoped settings (cards, look-and-feel, theme, flight-mode prefs) rely on profile IDs.
Without guaranteed active profile reconciliation, different subsystems can drift into different
fallback keys and produce inconsistent user state.

## Target Behavior

1. First launch with empty store:
   - create default profile,
   - set it active,
   - persist both list and active ID.

2. Launch with profiles present but invalid/missing active ID:
   - fallback active to default profile,
   - persist corrected active ID.

3. Launch with corrupted profile payload:
   - recover to default profile baseline (no crash path).

4. Default profile cannot be deleted.

5. Opening profile settings by ID is stable during hydration and does not auto-close
   due to transient null profile lookup.

6. Selection flow does not allow "continue without profile" when profiles exist but no
   active profile is resolved.

7. Profile storage read errors degrade safely to recoverable state instead of terminating
   profile hydration streams.

8. Corrupt profile payload handling is non-destructive (no silent profile-history wipe on
   follow-up writes).

9. Deleting the active profile resolves to a deterministic fallback policy (not list-order
   dependent behavior).

10. Clear-profile/delete paths remove all profile-scoped card state consistently, including
    in-memory visibility rows.

11. Profile deletion UX requires explicit confirmation before mutation is dispatched.

## Architecture Contract

### SSOT Ownership

| Data | Owner | Exposed As | Notes |
|---|---|---|---|
| Profile list | `ProfileRepository` | `StateFlow<List<UserProfile>>` | Includes default profile invariant. |
| Active profile ID | `ProfileRepository` | internal + `activeProfile` flow | Persisted as `active_profile_id`. |
| Active profile object | `ProfileRepository` | `StateFlow<UserProfile?>` | Derived from profile list + active ID. |

### Dependency Direction

`UI -> use case -> repository -> storage`

No new direct storage access from UI or ViewModel.

### Time Base

Profile bootstrap itself is not time-sensitive.  
If metadata timestamps are updated (`createdAt`, `lastUsed`), use existing repository policy.

## Implementation Steps

### Step 1: Add Default Profile Identity

File: `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

1. Add fixed default profile ID constant: `"default"`.
2. Add default profile builder with deterministic baseline fields.

### Step 2: Add Reconciliation Pass

File: `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

Add `reconcileProfilesAndActiveId(profiles, activeId)` logic that:

1. Ensures default profile exists in list.
2. Ensures active ID resolves to existing profile.
3. Returns corrected list + corrected active ID + changed flags.
4. Persists corrected state only when changed.

Trigger reconciliation in both collectors after loading current snapshots.

### Step 3: Add Delete Guard for Default

File: `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

Reject delete when `profileId == defaultProfileId`.

### Step 4: UI Guards

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionList.kt`
2. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`

Disable/hide delete affordance for default profile to match repository rule.

### Step 5: Fallback Key Alignment

Ensure Phase 1 uses a single fallback identity policy.

Reference files:

1. `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightVisibility.kt`
2. `feature/map/src/main/java/com/example/xcpro/ui/theme/Theme.kt`
3. `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt`
4. `feature/map/src/main/java/com/example/xcpro/flightdata/FlightMgmtPreferencesRepository.kt`

If fallback migration is not completed in this phase, document temporary compatibility rules
explicitly in `Profiles_Current_Architecture.md`.

### Step 6: Delete-Cascade Cleanup

Goal: when a user profile is deleted, remove profile-scoped data keyed by that profile ID.

Target stores:

1. Card preferences (`profile_<id>_*` keys).
2. Theme preferences (`profile_<id>_*` color keys).
3. Look-and-feel preferences (`profile_<id>_*` style keys).
4. Flight-management preferences (`profile_<id>_last_flight_mode`).

Implementation note:

1. Keep orchestration in use-case/repository layer (not UI/Composable code).
2. Ensure delete operations avoid partially-cleared profile state.

### Step 7: Hydration-Aware Settings Routing

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`
2. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`

Replace immediate `popBackStack()` on first null lookup with a hydration-aware flow:

1. show loading while profile state is unresolved,
2. only pop/show error once profile list is hydrated and target ID is confirmed missing.

### Step 8: Selection-Skip Invariant

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionContent.kt`
2. `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`

Enforce active-profile invariant in selection flow:

1. remove/disable skip when profiles exist but no active profile is selected, or
2. implement skip as explicit activation of canonical default profile.

### Step 9: Flight Mode Hydration Ordering

Files:

1. `feature/map/src/main/java/com/example/xcpro/flightdata/FlightMgmtPreferencesViewModel.kt`
2. `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataMgmt.kt`

Prevent transient mode application from fallback `"default"` key before resolved profile ID:

1. bind profile ID first,
2. apply last-flight-mode only after profile-bound flow emits.

### Step 10: Storage Read Recovery

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileStorage.kt`
2. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

Add profile-storage read fault handling:

1. guard DataStore read flows with recoverable error handling,
2. ensure repository hydration continues with safe fallback (`empty`/default reconciliation path)
   when recoverable read errors occur.

### Step 11: Update and Validation Guards

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
2. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`

Add profile update contract checks:

1. `updateProfile` returns not-found failure when target ID is absent,
2. edit-save path rejects blank profile names with visible feedback.

### Step 12: Parse-Failure Integrity Policy

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

Do not treat JSON parse failure as normal empty state:

1. record/flag parse-failure condition explicitly,
2. avoid destructive persistence while state is in parse-failure recovery mode unless user
   confirms reset/migration path.

### Step 13: Deterministic Delete Fallback

File:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

When deleting the active profile:

1. use canonical fallback selection (default profile first when present),
2. if no default exists yet, apply explicit deterministic ordering rule (documented in code/tests),
   not `remaining.firstOrNull()` by incidental list order.

### Step 14: Create-Path Input Invariants

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
2. `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionDialogs.kt`

Enforce repository-level create invariants:

1. trim and validate profile name (non-blank),
2. fail invalid create requests regardless of caller path (UI/non-UI).

### Step 15: Clear-Profile Completeness (dfcards)

Files:

1. `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataUiEventHandler.kt`
2. `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`

Ensure clear-profile covers all card-profile state slices:

1. remove cards map entry,
2. remove template map entry,
3. remove visibility map entry,
4. clear persisted profile keys.

### Step 16: Delete Confirmation Guards

Files:

1. `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionList.kt`
2. `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`

Add explicit confirmation flow before delete dispatch:

1. require user confirmation action,
2. show invariant-aware messaging (default/last profile cannot be deleted),
3. keep user on screen when deletion fails.

### Deferred Follow-Up (Phase 2)

1. Consolidate color-theme ownership under one repository/use-case path.
   Current ownership is split between:
   - `feature/map/src/main/java/com/example/xcpro/ui/theme/ThemePreferencesRepository.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelPreferences.kt`

2. Split profile-selection and profile-creation semantics in repository API.
   Current `setActiveProfile(...)` behavior can implicitly insert missing profiles.

## Tests

### Unit

File: `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTest.kt`

Add:

1. Seeds default profile when store is empty.
2. Falls back to default when active ID missing.
3. Falls back to default when active ID unknown.
4. Prevents deleting default profile.
5. Idempotence: repeated hydration does not duplicate default profile.
6. Delete cascade removes profile-scoped keys from supported stores.
7. Profile settings route does not auto-pop during normal hydration.
8. Selection flow cannot continue with null active profile when profiles exist.
9. Flight mode restore uses resolved active profile key (no transient wrong-profile mode).
10. Recoverable profile-storage read errors do not kill hydration streams.
11. Update-profile for missing ID returns failure instead of silent success.
12. Blank-name profile saves are rejected.
13. Parse-failure state does not silently overwrite existing profile payload on next write path.
14. Active-profile delete fallback follows canonical deterministic policy.
15. Create-profile with blank/invalid names fails at repository layer.
16. Clear-profile removes visibility map entries in memory (not only cards/templates).
17. Delete actions require explicit confirmation in both selection and settings screens.

### Optional UI/Integration

1. Startup with empty store opens map with default active profile (no create-profile hard block).
2. Deleting non-default active profile falls back to default or next valid profile per policy.
3. Opening profile settings from profile list remains on-screen while state hydrates.
4. Selecting "skip" does not leave app running with null active profile.
5. Corrupted/unreadable profile storage still reaches deterministic default-profile recovery.

## Verification

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Existing users may have fallback data under alternate keys. | Add explicit key-compatibility plan and migration note before merge. |
| UI still references stale `isActive` model field. | Keep PROFILES-20260216-02 in same fix train. |
| Temporary startup flicker if gating remains UI-only. | Add "hydration-ready" state or reconcile before first gate decision. |
| Delete cleanup may become partial across stores. | Centralize cleanup in one orchestrator and add integration tests for key removal. |
| Flight mode may be briefly set from fallback key during profile switch. | Gate mode application on profile-bound preference flow emission. |
| Storage flow failures may still terminate hydration unexpectedly. | Add explicit recoverable-error tests for profile storage flow behavior. |

## Exit Criteria

1. App always has valid active profile after repository hydration.
2. Default profile cannot be deleted via repository or UI.
3. Tests cover bootstrap, fallback, delete guard, and delete-cascade behavior.
4. Settings routing is hydration-safe for existing profile IDs.
5. Selection flow enforces non-null active profile invariant.
6. Flight mode restore order is profile-bound and deterministic.
7. Storage read failures are handled via safe recovery path.
8. Profile update/edit validation guards are enforced.
9. Parse-failure handling avoids silent destructive overwrite.
10. Active-delete fallback behavior is deterministic and policy-driven.
11. Workboard statuses updated for completed tasks.
12. Clear-profile/delete paths leave no stale in-memory profile card visibility state.
13. Delete mutation is confirmation-gated in profile UIs.
