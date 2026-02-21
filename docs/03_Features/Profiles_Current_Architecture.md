# Profiles Current Architecture

Created: 2026-02-16  
Status: Active reference

## Purpose

Document what is implemented today so profile work can be planned without guesswork.

## Terminology

The word "profile" is currently used for multiple concepts:

1. User flight profile (pilot + aircraft identity and preferences).
2. Flight card profile mappings (cards/templates/visibilities keyed by profile ID).
3. Orientation profile (cruise/circling logic in map orientation engine).
4. Display/GPS sampling profiles (smoothing and live GPS cadence helpers).

Only #1 and #2 are part of "user profile management."

## SSOT and Data Owners

| Data | Owner | Storage | Main File |
|---|---|---|---|
| User profile list | `ProfileRepository` | DataStore JSON (`profiles_json`) | `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt` |
| Active user profile ID | `ProfileRepository` | DataStore key (`active_profile_id`) | `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileStorage.kt` |
| Profile card mappings | `FlightDataViewModel` + `FlightProfileStore` | `CardPreferences` keys (`profile_<id>_*`) | `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt` |
| Profile card mode visibilities | `FlightDataViewModel` + `FlightVisibility` | `CardPreferences` keys (`profile_<id>_<MODE>_visible`) | `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightVisibility.kt` |
| Theme per profile | `ThemePreferencesRepository` | SharedPreferences (`profile_<id>_color_theme`) | `feature/map/src/main/java/com/example/xcpro/ui/theme/ThemePreferencesRepository.kt` |
| Look and feel per profile | `LookAndFeelPreferences` | SharedPreferences (`profile_<id>_*`) | `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelPreferences.kt` |

## Current End-to-End Flows

### Flow A: App startup and profile selection

1. App loads `ProfileViewModel` state.
2. If no profiles or no active profile, `MainActivityScreen` shows profile selection.
3. User creates/selects profile.
4. `ProfileRepository` persists `profiles_json` and `active_profile_id`.
5. App continues to map with active profile in memory.

Primary files:

- `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSelectionScreen.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

### Flow B: Profile change and map/cards rehydration

1. Active profile changes in `ProfileViewModel`.
2. Map effects reload visible flight modes from `CardPreferences`.
3. Map effects call `prepareCardsForProfile(profile, mode, size)`.
4. `FlightDataViewModel` sets active profile ID + flight mode and applies template/cards.
5. Card UI reflects profile-specific template/card state.

Primary files:

- `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataMgmt.kt`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataProfileCoordinator.kt`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataStateMapper.kt`

### Flow C: Profile-scoped settings (theme/look and feel)

1. Active profile ID is read from `ProfileViewModel`.
2. Settings view models call `setProfileId(activeProfileId)`.
3. Repositories read/write keys scoped by profile ID.
4. UI/theme updates for current profile.

Primary files:

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootStateBindings.kt`
- `feature/map/src/main/java/com/example/xcpro/ui/theme/Theme.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ColorsScreen.kt`

## Current Gaps to Track

Use `docs/03_Features/Profiles_Workboard.md` as the active issue and phase tracker.

Known non-import/export gaps:

1. Edit button in profile selection list is currently a no-op path.
2. One profiles screen uses `UserProfile.isActive`, while active state is actually `active_profile_id`.
3. Quick switcher active row has no visible active marker text.
4. Profile settings save/delete navigates back before operation success/failure is surfaced.

## Deep Dive Findings (2026-02-17)

These findings came from a full profile-path sweep across app startup, map effects,
profile UI, dfcards, look-and-feel, and theme settings.

### High-impact correctness gaps

1. Color theme observer reads the wrong SharedPreferences file in look-and-feel flow.
   `observeColorThemeId(...)` is backed by `stringFlow(...)`, but `stringFlow(...)`
   always reads `lookAndFeelPrefs` while color theme is stored in `colorPrefs`.
   Result: color theme updates may not be observed consistently in look-and-feel UI.
   File: `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelPreferences.kt`

2. Fallback profile IDs are inconsistent across subsystems when active profile is null.
   Theme/look-and-feel/flight-mgmt use `"default"` while dfcards normalizes null to
   `"__default_profile__"`.
   Result: profile-scoped state can split across two fallback buckets.
   Files:
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ColorsScreen.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt`
   - `feature/map/src/main/java/com/example/xcpro/ui/theme/Theme.kt`
   - `feature/map/src/main/java/com/example/xcpro/flightdata/FlightMgmtPreferencesRepository.kt`
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightVisibility.kt`

3. No guaranteed default profile bootstrap.
   First-run and missing/invalid active-profile recovery still depend on UI gating instead
   of repository reconciliation.
   Files:
   - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

### Model and wiring gaps

1. `UserProfile.isActive` is not the authoritative active source.
   Active state is tracked by `active_profile_id`, but at least one screen still checks
   `profile.isActive`.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileModels.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Profiles.kt`

2. `ProfileCreationRequest.copyFromProfile` is currently unused.
   File: `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileModels.kt`
   Consumer: `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

3. `lastUsed` is modeled but never updated by profile-selection flow.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileModels.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

4. Profile preference fields in `UserProfile.preferences` are mostly not connected to
   runtime settings repositories (units/theme/card behavior are managed elsewhere).
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileModels.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`
   - `core/common/src/main/java/com/example/xcpro/common/units/UnitsRepository.kt`

## Additional Deep Dive Findings (Second Pass, 2026-02-17)

### Runtime and lifecycle gaps

1. `ProfileSettingsScreen` can pop immediately on transient/null profile lookup.
   The screen exits as soon as `uiState.profiles.find { it.id == profileId }` is null, without
   waiting for profile hydration.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`

2. Active-profile startup gating still depends on transient UI state with no explicit
   hydration-ready signal.
   `MainActivityScreen` gates by `profiles.isEmpty` / `activeProfile == null` while
   `ProfileViewModel` initializes from an empty state and hydrates asynchronously.
   Files:
   - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`

3. "Skip for now" can bypass active-profile gating and continue with null active profile.
   The skip action closes the selection UI locally, but does not resolve active profile state.
   Because gating is local-state driven, app flow can continue while `activeProfile` remains null.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionContent.kt`
   - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`

### Deletion and cleanup gaps

1. Profile deletion does not cascade to profile-scoped downstream stores.
   `ProfileRepository.deleteProfile(...)` updates only profile list + active ID persistence.
   Card/theme/look-and-feel/flight-mode persisted entries are not cleared by this path.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
   - `feature/map/src/main/java/com/example/xcpro/ui/theme/ThemePreferencesRepository.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelPreferences.kt`
   - `feature/map/src/main/java/com/example/xcpro/flightdata/FlightMgmtPreferencesRepository.kt`

2. `FlightDataViewModel.clearProfile(...)` and `CardPreferences.clearProfile(...)` exist but are
   not wired into the user-profile delete flow.
   Files:
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataUiEventHandler.kt`
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardPreferences.kt`

### Fallback behavior gaps when active profile is null

1. Status bar style fallback bypasses profile-keyed defaults.
   `MainActivity.applyUserStatusBarStyle(null)` resolves directly to transparent instead of
   reading the `"default"` fallback profile key used in look-and-feel screens.
   Files:
   - `app/src/main/java/com/example/xcpro/MainActivity.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt`

2. Visible flight-mode loading is skipped when active profile is null, leaving previous in-memory
   visibility state in place.
   Files:
   - `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`

3. Flight mode selection can transiently hydrate from the `"default"` fallback before the actual
   active profile ID is applied in Flight Data settings flow.
   `FlightMgmtPreferencesViewModel` starts with `profileId = "default"` and `lastFlightMode`
   initial value derived from that key, while `FlightDataMgmt` applies `lastFlightMode` on
   active-profile changes.
   Files:
   - `feature/map/src/main/java/com/example/xcpro/flightdata/FlightMgmtPreferencesViewModel.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataMgmt.kt`

### Layering gap

1. UI composable directly creates a preference repository (`CardPreferences(context)`) instead of
   consuming a use-case/repository via DI.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileQuickActions.kt`

## Additional Deep Dive Findings (Third Pass, 2026-02-17)

### Storage fault-handling gap

1. Profile storage flows do not handle DataStore read I/O exceptions before mapping values.
   `profilesJsonFlow` and `activeProfileIdFlow` are direct `data.map` streams with no error
   recovery layer (`catch { ... }`).
   Risk: profile streams can fail/cancel on storage read problems instead of degrading to a safe
   recovery path.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileStorage.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

### Repository contract gap

1. `updateProfile(...)` returns success even when the profile ID does not exist.
   The current implementation maps the list and persists without a "not found" check.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`

### Input-validation gap

1. Profile edit save path allows empty profile names.
   The settings screen save action has no validation gate, and update path accepts the edited
   value as-is.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`

### Dead-code drift

1. Several profile helper APIs/composables appear unreferenced by runtime call sites, increasing
   maintenance drift risk.
   Candidates:
   - `ProfileIndicator`, `ProfileQuickSwitcher`, `FlightModeIndicator`
   - `ProfileViewModel.needsProfileSelection()`
   - `ProfileRepository.hasProfiles()` / `hasActiveProfile()`
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileQuickActions.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

## Additional Deep Dive Findings (Fourth Pass, 2026-02-17)

### SSOT ownership gap

1. Color-theme ownership is split across two repositories with overlapping keys.
   `ThemePreferencesRepository` and `LookAndFeelPreferences` both read/write
   `profile_<id>_color_theme` in `ColorThemePrefs`.
   Risk: drift in behavior/observers and duplicate policy logic.
   Files:
   - `feature/map/src/main/java/com/example/xcpro/ui/theme/ThemePreferencesRepository.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelPreferences.kt`

### Data integrity and validation gaps

1. Corrupt profile JSON currently collapses to empty profile list with no quarantine path.
   `parseProfiles(...)` falls back to `emptyList()` on parse error.
   Risk: follow-up writes can overwrite stored profile payload with a reduced state.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

2. `createProfile(...)` does not enforce repository-level input validation.
   UI create dialog blocks blank names, but repository contract accepts raw request values.
   Risk: non-UI callers can persist invalid profile names.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionDialogs.kt`

### Fallback-policy gap

1. Deleting the active profile falls back to `remaining.firstOrNull()` instead of a canonical policy.
   Current behavior depends on list ordering, not explicit fallback rules.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

## Additional Deep Dive Findings (Fifth Pass, 2026-02-17)

### Repository contract ambiguity

1. `setActiveProfile(...)` implicitly upserts unknown profiles.
   If the target ID is not found, repository merges the passed profile into the list and persists it.
   Risk: stale/non-authoritative callers can create phantom profiles through a "select" path.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
   - `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTest.kt`

### Incomplete cleanup behavior

1. dfcards clear-profile helper does not clear in-memory visibility map entries.
   `FlightDataUiEventHandler.clearProfile(...)` removes cards/templates and preference keys, but
   does not remove profile rows from `profileModeVisibilities`.
   Risk: stale visibility state can remain in memory after clear operations.
   Files:
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataUiEventHandler.kt`
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`

### Destructive-action UX risk

1. Profile delete actions execute immediately with no confirmation dialog.
   This exists in both selection-list and settings-screen delete affordances.
   Risk: accidental profile deletion and avoidable error churn when last/default deletion is rejected.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionList.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`

## Additional Deep Dive Findings (Sixth Pass, 2026-02-17)

### Persistence consistency gap

1. Profile list and active profile ID are persisted via separate DataStore edits.
   Repository writes `profiles_json` and `active_profile_id` in separate calls, which are not
   atomic as one combined state transition.
   Risk: partial write windows and inconsistent on-disk pair after failure/interruption.
   Files:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileStorage.kt`

### UI state projection gap

1. `ProfileViewModel` can transiently preserve stale `activeProfile` when repository emits null.
   In profiles collector, active profile is set as `useCase.activeProfile.value ?: previous`.
   Risk: short-lived stale active-profile UI state and gating/style decisions based on stale value.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileViewModel.kt`

### Mutation serialization gap

1. Repository write paths are unsynchronized across create/select/update/delete operations.
   Mutations are derived from current in-memory state with no mutex/serialization guard.
   Risk: lost updates or last-writer-wins anomalies under concurrent profile operations.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

## Additional Deep Dive Findings (Seventh Pass, 2026-02-17)

### Mutation failure semantics gap

1. Repository mutation paths mutate in-memory state before persistence and do not rollback on
   storage write failure.
   `createProfile`, `setActiveProfile`, `updateProfile`, and `deleteProfile` update
   `_profiles`/`_activeProfile` first, then persist.
   Risk: UI can show a failed operation while runtime state has already changed, causing memory/disk
   divergence until next reload/reconciliation.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

### Active-invariant repair gap in mutation paths

1. `deleteProfile(...)` only repairs active state when the deleted ID equals current active profile.
   If active state is already null/invalid and a non-active row is deleted, repository can end with
   `profiles.isNotEmpty()` and `activeProfile == null`.
   Risk: null-active runtime persists despite available profiles.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

2. `createProfile(...)` only auto-activates when list size becomes `1`.
   In an already-invalid state (profiles exist, active missing), create path does not repair the
   active-profile invariant by itself.
   Risk: callers can continue operating in null-active state after successful create.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`

### Selection action-gating gap

1. Profile selection screen still renders "Continue to Flight Map" whenever
   `state.activeProfile != null`, even while `state.isLoading`.
   Risk: user can continue while a select/create mutation is still in-flight, potentially using a
   stale active profile.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionContent.kt`

2. Skip action availability is keyed only by profile/active null state and does not consider
   loading state.
   Risk: skip can still fire during profile hydration/mutation transitions.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionContent.kt`

### Time-source discipline gap

1. `UserProfile` timestamp defaults call `System.currentTimeMillis()` directly.
   Risk: profile metadata timestamps are non-deterministic and bypass injected-clock policy used
   elsewhere.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileModels.kt`

## Additional Deep Dive Findings (Eighth Pass, 2026-02-17)

### Cross-screen profile visibility consistency gap (dfcards)

1. Profile mode-visibility hydration only includes profile IDs discovered from template/card
   mappings (plus active profile at hydration time).
   When switching later to a profile that has persisted visibility keys but no loaded
   template/card mapping, `setActiveProfile(...)` seeds in-memory defaults via
   `ensureVisibilityEntry(...)` instead of loading persisted visibility values first.
   Risk: map and flight-data screens can diverge for the same profile after switch
   (map path can still read persisted visibility, while dfcards in-memory state shows defaults),
   and subsequent writes can overwrite prior persisted visibility intent.
   Files:
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightProfileStore.kt`
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/flightdata/FlightDataScreensTab.kt`
   - `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataIngest.kt`

### Profile settings local state freshness gap

1. `ProfileSettingsScreen` initializes editable state with `remember { mutableStateOf(profile) }`
   and no key.
   Risk: if the profile object changes while the screen stays open (hydration completion or
   external mutation), local editable state can become stale and save outdated data.
   File:
   - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsScreen.kt`

### Test coverage gap

1. `FlightDataViewModel` unit tests do not cover visibility hydration and cross-profile
   visibility restoration behavior.
   Risk: regressions in profile visibility persistence/switching can pass current tests.
   File:
   - `dfcards-library/src/test/java/com/example/dfcards/FlightDataViewModelUnitsTest.kt`

### Profile entrypoint gap

1. Manage Account screen still exposes an "Edit Profile" row with a TODO no-op action.
   Risk: user-visible profile-management path appears available but does nothing.
   File:
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ManageAccount.kt`

## Default Profile Constraints (Phase 1)

For the first implementation milestone, default-profile behavior should satisfy:

1. Repository-level reconciliation (not UI-only).
2. Stable default profile ID (`"default"`) to align existing fallback-key usage.
3. Automatic fallback when active ID is missing/invalid.
4. Guard against deleting the default profile.
5. Deterministic startup state before map/render flows consume profile-scoped settings.

## Long-Term Direction

The modernization target remains in `PROFILES.md` (ProfileManager, unified profile bundles, welcome default profile).  
Treat this document as "what exists now" and `PROFILES.md` as "what we want."
