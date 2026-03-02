# CHANGE_PLAN_OGN_HOTSPOTS_PROXIMITY_PARITY_2026-03-02.md

## 0) Metadata

- Title: General Proximity parity for OGN, Hotspots, and Thermalling settings presentation
- Owner: XCPro map/ui
- Date: 2026-03-02
- Issue/PR: TBD
- Status: Draft

## 1) Scope

- Problem statement:
  - General Proximity currently opens as an in-place local sheet under the General route.
  - OGN, Hotspots, and Thermalling currently transition to separate nav destinations (`ogn_settings`, `hotspots_settings`, `thermalling_settings`) with different host behavior.
  - This creates visual and interaction inconsistency (background continuity and redraw behavior differ).
- Why now:
  - User request is explicit: OGN, Hotspots, and Thermalling should behave like Proximity.
  - Current split host strategy increases UX drift and maintenance cost.
- In scope:
  - Make OGN, Hotspots, and Thermalling open as local sheet overlays owned by `SettingsRoutes.GENERAL`, matching the Proximity model.
  - Keep OGN/Hotspots/Thermalling settings SSOT and ViewModel logic unchanged.
  - Harden close/back/swipe behavior with deterministic contracts.
  - Add regression tests for sub-sheet transitions and close semantics.
- Out of scope:
  - Any changes to OGN/Hotspots/Thermalling domain policy or repository logic.
  - Map runtime overlay behavior changes.
  - Replay or sensor pipeline changes.
- User-visible impact:
  - OGN, Hotspots, and Thermalling open with the same local overlay behavior as Proximity.
  - Background continuity from General is preserved.
  - No route-level visual jump when opening OGN/Hotspots/Thermalling from General.
  - Existing Thermalling icons and in-sheet iconography remain unchanged.

## 2) Comprehensive Code Pass Findings

1) General route is already a route-hosted `ModalBottomSheet`.
- File: `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/Settings-df.kt`.

2) Proximity is local state inside General.
- `showProximitySettingsSheet` is General-local state and renders a local `ModalBottomSheet`.

3) OGN, Hotspots, and Thermalling are different host styles today.
- `OgnSettingsScreen` is full-screen `Scaffold`.
- `HotspotsSettingsScreen` is route-level `ModalBottomSheet`.
- `ThermallingSettingsScreen` is route-level `ModalBottomSheet`.
- OGN/Hotspots are opened from Proximity via navigation; Thermalling is opened from a General tile.

4) Entry points are currently centralized around General.
- Drawer and map settings shortcut already route to `SettingsRoutes.GENERAL`.
- OGN/Hotspots/Thermalling routes are secondary leaves reached from General.

5) UI behavior tests do not currently cover OGN/Hotspots/Thermalling host parity.
- Existing General policy tests are route/policy focused.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| General settings route visibility | Nav back stack (`SettingsRoutes.GENERAL`) | route presence | local global visibility flags outside nav |
| General sub-sheet visibility (`NONE/PROXIMITY/OGN/HOTSPOTS/THERMALLING`) | General screen local UI state | immutable UI state in composable scope | independent booleans for each sheet owner |
| OGN settings values (icon size/radius/mode/ownship ids) | `OgnTrafficPreferencesRepository` via `OgnSettingsViewModel` | `StateFlow<OgnSettingsUiState>` | UI-side mirrors outside ViewModel |
| Hotspots settings values (retention/display %) | `OgnTrafficPreferencesRepository` via `HotspotsSettingsViewModel` | `StateFlow<HotspotsSettingsUiState>` | UI-side mirrors outside ViewModel |
| Thermalling settings values (automation/enter-exit delays/zoom restore) | `ThermallingModePreferencesRepository` via `ThermallingSettingsViewModel` | `StateFlow<ThermallingSettingsUiState>` | UI-side mirrors outside ViewModel |

### 3.2 Dependency Direction

Remains:

`UI -> domain -> data`

- Modules/files touched (planned):
  - `feature/map/.../screens/navdrawer/Settings-df.kt`
  - `feature/map/.../screens/navdrawer/OgnSettingsScreen.kt`
  - `feature/map/.../screens/navdrawer/HotspotsSettingsScreen.kt`
  - `feature/map/.../screens/navdrawer/ThermallingSettingsScreen.kt`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - `feature/map/.../navigation/SettingsRoutes.kt`
  - tests under `feature/map/src/test/.../screens/navdrawer`
- Boundary risk:
  - UI host migration must not push policy/business logic into UI.
  - ViewModel and repository contracts remain unchanged.

### 3.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN settings sheet host | `AppNavGraph` destination `ogn_settings` + `OgnSettingsScreen` scaffold | `SettingsRoutes.GENERAL` local sub-sheet host | align UX behavior with Proximity | compose tests + manual matrix |
| Hotspots settings sheet host | `AppNavGraph` destination `hotspots_settings` | `SettingsRoutes.GENERAL` local sub-sheet host | align UX behavior with Proximity | compose tests + manual matrix |
| Thermalling settings sheet host | `AppNavGraph` destination `thermalling_settings` | `SettingsRoutes.GENERAL` local sub-sheet host | align UX behavior with Proximity | compose tests + manual matrix |
| Sub-sheet transition ownership | mixed nav callbacks in Proximity actions | one General-local sub-sheet state machine | deterministic transitions and no multi-owner drift | unit/compose tests |

### 3.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `Settings-df.kt` Proximity -> OGN | `navController.navigate("ogn_settings")` | set General sub-sheet state to `OGN` | Phase 2 |
| `Settings-df.kt` Proximity -> Hotspots | `navController.navigate(SettingsRoutes.HOTSPOTS_SETTINGS)` | set General sub-sheet state to `HOTSPOTS` | Phase 2 |
| `Settings-df.kt` General tile -> Thermalling | `navController.navigate(SettingsRoutes.THERMALLING_SETTINGS)` | set General sub-sheet state to `THERMALLING` | Phase 2 |
| General tile -> OGN | direct route navigation | set General sub-sheet state | Phase 2 |
| General tile -> Hotspots | direct route navigation | set General sub-sheet state | Phase 2 |

### 3.3 Time Base

No time-dependent domain logic changes.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| General/Proximity/OGN/Hotspots/Thermalling sheet visibility | N/A | UI navigation/presentation state only |

Explicitly forbidden:
- introducing wall-time logic for UI state transitions
- introducing replay/live branching in settings host logic

### 3.4 Threading and Cadence

- Dispatcher ownership:
  - UI state and navigation callbacks on `Main`.
  - existing ViewModel/repository dispatchers unchanged.
- Primary cadence/gating sensor:
  - none (UI interaction only).
- Hot-path latency budget:
  - sub-sheet open/close should feel immediate (<1 frame scheduling overhead beyond existing Compose recomposition).

### 3.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules: none introduced; replay pipeline unaffected.

### 3.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Duplicate UI state owners for sub-sheets | ARCHITECTURE SSOT | compose test + review | new `GeneralSettingsSubSheetBehaviorTest` |
| Route drift from General single-owner intent | ARCHITECTURE + CHANGE_PLAN_GENERAL_BOTTOM_SHEET | unit/compose test + review | `GeneralSettingsScreenPolicyTest` updates |
| Regression in close/back/swipe behavior | CODING_RULES predictability/UDF | compose/instrumentation/manual matrix | new behavior test + manual evidence |
| Accidental business logic movement into UI | ARCHITECTURE domain boundaries | review + enforceRules baseline | screen/viewmodel diff review |

## 4) Data Flow (Before -> After)

Before:

`Drawer/SettingsShortcut -> SettingsRoutes.GENERAL (sheet) -> Proximity local sheet -> navigate to ogn_settings/hotspots_settings routes; General tile -> thermalling_settings route`

After:

`Drawer/SettingsShortcut -> SettingsRoutes.GENERAL (sheet) -> General-local sub-sheet state machine -> Proximity/OGN/Hotspots/Thermalling local sheets`

## 5) Production-Grade Phased Implementation

### Phase 0 - Baseline Lock and Behavior Matrix

- Goal:
  - Lock existing behavior and define target parity contract before refactor.
- Deliverables:
  - Interaction matrix (open from General tile, open from Proximity, dismiss via swipe/back/icon, return target).
  - Route dependency inventory (`ogn_settings`, `hotspots_settings`, `thermalling_settings`) and callsites.
- Files:
  - this plan doc.
- Exit criteria:
  - contract matrix approved and used as acceptance baseline.

### Phase 1 - Extract Host-Agnostic Settings Content

- Goal:
  - Decouple OGN/Hotspots/Thermalling content from their current route hosts.
- Files:
  - `feature/map/.../screens/navdrawer/OgnSettingsScreen.kt`
  - `feature/map/.../screens/navdrawer/HotspotsSettingsScreen.kt`
  - `feature/map/.../screens/navdrawer/ThermallingSettingsScreen.kt`
  - optional shared content file(s) under same package.
- Required implementation quality:
  - Extract content composables that do not own route navigation host concerns.
  - Keep existing ViewModel bindings and setting mutations unchanged.
  - Preserve visual content and control semantics 1:1.
- Tests:
  - add/adjust UI policy tests for presence of key controls.
- Exit criteria:
  - old routes still work with no behavior change; content now reusable.

### Phase 2 - General Sub-Sheet State Machine Migration

- Goal:
  - Move OGN, Hotspots, and Thermalling opening behavior to General-local sub-sheet model.
- Files:
  - `feature/map/.../screens/navdrawer/Settings-df.kt`
- Required implementation quality:
  - Replace ad-hoc booleans with one explicit sub-sheet state owner (sealed class/enum).
  - Ensure only one local child sheet is visible at a time.
  - Proximity actions open OGN/Hotspots via local state transition, not navigation.
  - General tile taps for OGN/Hotspots/Thermalling use the same local-state transition.
  - Dismiss behavior returns to General base sheet deterministically.
- Tests:
  - add state-machine behavior tests for transitions:
    - `NONE -> PROXIMITY`
    - `PROXIMITY -> OGN`
    - `PROXIMITY -> HOTSPOTS`
    - `NONE -> THERMALLING`
    - child sheet dismiss -> `NONE`
- Exit criteria:
  - OGN/Hotspots/Thermalling opened from General behave visually like Proximity (no route jump).

### Phase 3 - Route Compatibility and Navigation Hardening

- Goal:
  - Prevent route breakage while converging ownership to General.
- Files:
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - `feature/map/.../navigation/SettingsRoutes.kt`
  - `feature/map/.../screens/navdrawer/GeneralSettingsScreenPolicyTest.kt`
- Required implementation quality:
  - Remove internal callsites that navigate directly to `ogn_settings` / `hotspots_settings` / `thermalling_settings`.
  - Keep temporary compatibility route handlers (if retained) that forward to General-owner behavior.
  - Normalize route constants (avoid raw route strings in callsites).
- Tests:
  - route compatibility test (legacy route still resolves or redirects correctly).
  - no duplicate General stack entries on repeated opens.
- Exit criteria:
  - General is the only in-app owner for OGN/Hotspots/Thermalling settings presentation.

### Phase 4 - Close Semantics and UX Contract Hardening

- Goal:
  - Make close vectors deterministic across all General sub-sheets.
- Files:
  - `feature/map/.../screens/navdrawer/Settings-df.kt`
  - tests in `feature/map/src/test/.../screens/navdrawer`
  - optional instrumentation tests in `app/src/androidTest`.
- Required implementation quality:
  - Explicit contract for:
    - swipe-down on child sheet
    - system back on child sheet
    - top app bar actions inside child content
  - no hidden pop/navigate side effects from child sheet dismisses.
- Tests:
  - compose/unit tests for back/map icon contract.
  - instrumentation/manual swipe dismiss evidence.
- Exit criteria:
  - all close vectors are deterministic and documented.

### Phase 5 - Verification, Evidence, and Docs Sync

- Goal:
  - Prove release readiness and architecture compliance.
- Required checks:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Optional when available:
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
  - `./gradlew connectedDebugAndroidTest --no-parallel`
- Docs sync:
  - update `PIPELINE.md` only if actual wiring/ownership text there becomes inaccurate.
  - update `KNOWN_DEVIATIONS.md` only if an approved temporary exception is required.
- Exit criteria:
  - required gates pass and behavior matrix is fully green.

## 6) Test Plan (Consolidated)

- Unit/compose:
  - `GeneralSettingsScreenPolicyTest` updates for route ownership and tiles.
  - New `GeneralSettingsSubSheetBehaviorTest`:
    - transition/state ownership assertions
    - OGN/Hotspots/Thermalling open via local state, not route navigation
    - deterministic dismiss outcomes
- ViewModel tests:
  - existing OGN/Hotspots/Thermalling ViewModel tests (if added) should remain independent of host change.
- Instrumentation/manual:
  - verify visual parity matrix on device/emulator:
    - General -> Proximity -> OGN
    - General -> Proximity -> Hotspots
    - General -> Thermalling
    - General -> OGN direct
    - General -> Hotspots direct
    - General -> Thermalling direct
    - close by swipe/back/map icon for each
- Boundary tests:
  - compatibility behavior for any retained legacy routes.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Nested sheet composition conflicts | medium UI regressions (focus/back/scrim) | single active child-sheet state machine, no simultaneous child sheets | XCPro map/ui |
| Draft text field behavior regression in OGN ownship IDs | medium usability regression | extract content first, preserve ViewModel state and dirty-flag semantics, add focused tests | XCPro map/ui |
| Thermalling slider/switch regression during host extraction | medium UX correctness regression | extract content without changing `ThermallingSettingsViewModel` wiring; keep existing test tags and add focused tests | XCPro map/ui |
| Legacy route breakage | medium navigation regressions | temporary compatibility wrappers and route policy tests | XCPro map/ui |
| Close vector inconsistency | high UX drift | explicit close contract matrix + compose/instrumentation evidence | XCPro map/ui |

## 8) Acceptance Gates

- No violations of `ARCHITECTURE.md` and `CODING_RULES.md`.
- OGN, Hotspots, and Thermalling settings presentation is General-owned and Proximity-parity.
- No duplicate SSOT ownership introduced.
- OGN/Hotspots/Thermalling settings data ownership remains repository/ViewModel based.
- Replay/live behavior remains unchanged.
- Any exception must be recorded in `KNOWN_DEVIATIONS.md` (issue, owner, expiry).

## 9) Rollback Plan

- Independent rollback units:
  - Phase 2 General state-machine migration can be reverted while keeping content extraction.
  - Compatibility route handlers can be re-enabled if route regressions appear.
- Recovery steps:
  1. Revert General local OGN/Hotspots/Thermalling state transition wiring.
  2. Restore direct route navigation from Proximity/General tiles.
  3. Keep extracted content components to reduce future retry risk.
- Rollback trigger:
  - failing close-contract matrix or unresolved navigation regressions in instrumentation.

## 10) Quality Rescore Requirement (Post-Implementation)

After implementation completion, rescore per `docs/ARCHITECTURE/AGENT.md`:

- Architecture cleanliness
- Maintainability/change safety
- Test confidence on risky paths
- Overall map/task slice quality
- Release readiness

Scores must be evidence-based with file and test references.
