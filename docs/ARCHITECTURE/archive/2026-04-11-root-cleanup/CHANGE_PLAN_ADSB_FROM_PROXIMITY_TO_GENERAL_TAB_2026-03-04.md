# CHANGE_PLAN_ADSB_FROM_PROXIMITY_TO_GENERAL_TAB_2026-03-04.md

## 0) Metadata

- Title: ADS-b Settings Promotion from Proximity to Dedicated General Tab
- Owner: XCPro map/ui
- Date: 2026-03-04
- Issue/PR: TBD
- Status: Draft

## 1) Scope

- Problem statement:
  - ADS-b settings are currently discoverable through `General -> Proximity -> ADS-b Traffic`.
  - This adds one extra navigation step and couples ADS-b discoverability to the Proximity sub-sheet.
  - Requested UX is explicit: move ADS-b out of Proximity and make it a first-class tab/tile in `General`.
- Why now:
  - Direct discoverability for a high-value safety/traffic feature.
  - Reduces General settings flow friction and lowers support confusion ("where is ADS-b?").
- In scope:
  - Add a dedicated `ADS-b` tab/tile on the General root grid.
  - Remove ADS-b action from `ProximitySettingsSheet`.
  - Keep existing ADS-b settings screen/use-case/repository behavior unchanged.
  - Add regression tests for General tile visibility and Proximity content change.
  - Normalize ADS-b route usage through `SettingsRoutes` constant (no raw string callsites).
- Out of scope:
  - ADS-b domain logic or repository policy changes.
  - Proximity tiering logic, alerts, or map rendering behavior.
  - Rework of other General tiles beyond minimum layout adjustments required for this move.
- User-visible impact:
  - `ADS-b` appears directly in `General`.
  - `Proximity` no longer shows `ADS-b Traffic`.
  - Opening ADS-b from General still lands on the existing ADS-b settings screen.

## 1A) Comprehensive Code Pass Findings (Current State)

1) General root and Proximity ownership
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntime.kt`
  owns General grid tiles and local `GeneralSubSheet` transitions.
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntimeSheets.kt`
  defines `ProximitySettingsSheet` actions.

2) ADS-b is currently nested under Proximity
- `ProximitySettingsSheet(...)` exposes an `ADS-b Traffic` action.
- `SettingsDfRuntime.kt` wires that action to `navController.navigate("adsb_settings")`.

3) ADS-b route constant is not centralized
- `feature/map/src/main/java/com/trust3/xcpro/navigation/SettingsRoutes.kt`
  does not currently define an ADS-b route constant.
- `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt` registers `"adsb_settings"` directly.

4) ADS-b settings architecture is already correct and must remain unchanged
- UI: `AdsbSettingsScreen` + `AdsbSettingsViewModel`.
- Domain/use-case: `AdsbSettingsUseCase`.
- SSOT: `AdsbTrafficPreferencesRepository`.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| General sub-sheet selection (`NONE/PROXIMITY/WEATHER/OGN/HOTSPOTS/THERMALLING`) | General host composable local state (`SettingsDfRuntime.kt`) | local UI state | ad-hoc duplicate booleans per child sheet |
| ADS-b settings values (icon size, distance, vertical filters, emergency audio prefs) | `AdsbTrafficPreferencesRepository` via `AdsbSettingsViewModel` | StateFlow fields in VM | UI-local mirrors outside VM |
| ADS-b settings route identity | `SettingsRoutes.ADSB_SETTINGS` (new constant) | route constant used by nav graph + callsites | hardcoded `"adsb_settings"` strings in multiple files |

### 2.2 Dependency Direction

Confirmed unchanged:

`UI -> domain -> data`

- UI changes stay in General/Proximity composables and nav routing.
- No new UI -> repository shortcuts.
- Existing ViewModel/use-case/repository chain remains intact.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| ADS-b entrypoint in General UX | `ProximitySettingsSheet` action | General root tile/tab | direct discoverability, one less click | UI tests + manual matrix |
| ADS-b route identifier | raw string callsites | `SettingsRoutes` constant | route contract safety and refactorability | unit/policy tests + grep |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `SettingsDfRuntime.kt` Proximity -> ADS-b | `navController.navigate("adsb_settings")` from Proximity callback | remove callback path; General root tile navigates via `SettingsRoutes.ADSB_SETTINGS` | Phase 2 |
| `AppNavGraph.kt` ADS-b route registration | hardcoded `"adsb_settings"` | `SettingsRoutes.ADSB_SETTINGS` | Phase 1 |
| General/ADS-b navigation callsites | raw string route usage | route constant usage | Phase 1-2 |

### 2.3 Time Base

No time-dependent logic changes.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| General tile/sub-sheet visibility | N/A | UI presentation state only |
| ADS-b route navigation event | N/A | navigation event only |

Explicitly forbidden:
- introducing wall-time-based navigation logic
- introducing replay/live branching in this UI-only migration

### 2.4 Threading and Cadence

- Dispatcher ownership: `Main` (UI interaction/navigation only).
- Primary cadence/gating sensor: none (tap-driven UI flow).
- Hot-path latency budget: no extra asynchronous work on tile tap; open target screen immediately.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules: unchanged (no replay pipeline modifications).

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| ADS-b still discoverable only through Proximity | UX/policy contract for General ownership | compose/unit test + review | `GeneralSettingsScreenPolicyTest` update |
| Proximity still exposes removed ADS-b action | SSOT ownership of navigation entrypoints | compose/unit test + review | `GeneralSettingsSubSheetBehaviorTest` (new or updated) |
| Route string drift (`"adsb_settings"` in multiple places) | maintainability/change safety | unit test + review/grep | `SettingsRoutes` tests + code review grep |
| Accidental behavior changes in ADS-b settings content | architecture layering | existing ADS-b VM/screen tests + review | `AdsbSettings*` test suite (existing + smoke) |

## 3) Data Flow (Before -> After)

Before:

`General root -> Proximity sub-sheet -> ADS-b action -> navigate("adsb_settings") -> AdsbSettingsScreen`

After:

`General root -> ADS-b tile/tab -> navigate(SettingsRoutes.ADSB_SETTINGS) -> AdsbSettingsScreen`

No change to:

`AdsbSettingsScreen -> AdsbSettingsViewModel -> AdsbSettingsUseCase -> AdsbTrafficPreferencesRepository`

## 4) Production-Grade Phased Implementation

### Phase 0 - Baseline Contract and Safety Net

- Goal:
  - Lock current behavior and define target UX contract before edits.
- Files:
  - `docs/ARCHITECTURE/CHANGE_PLAN_ADSB_FROM_PROXIMITY_TO_GENERAL_TAB_2026-03-04.md`
- Tests to add/update:
  - baseline policy assertions in `GeneralSettingsScreenPolicyTest` (tile presence/order expectations).
- Exit criteria:
  - baseline + target matrix documented (open paths, close paths, expected destination).

### Phase 1 - Route Contract Normalization

- Goal:
  - Remove raw ADS-b route strings from navigation wiring.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/navigation/SettingsRoutes.kt`
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
- Tests to add/update:
  - route constant coverage in existing settings/navigation policy tests.
- Exit criteria:
  - ADS-b route registration and navigation callsites use `SettingsRoutes.ADSB_SETTINGS`.

### Phase 2 - General Tile Promotion + Proximity Cleanup

- Goal:
  - Move ADS-b entry from Proximity into General root tile grid.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntimeSheets.kt`
- Required implementation quality:
  - Add dedicated General `ADS-b` tile/tab that navigates directly to ADS-b settings.
  - Remove `onOpenAdsb` plumbing from `ProximitySettingsSheet` and callsites.
  - Preserve existing Proximity actions for non-ADS-b items.
  - Keep General state ownership explicit (single owner for sub-sheet transitions).
- Tests to add/update:
  - General root shows `ADS-b` tile.
  - Proximity sheet no longer renders `ADS-b Traffic`.
- Exit criteria:
  - ADS-b reachable directly from General root; no ADS-b entry under Proximity.

### Phase 3 - Behavioral Hardening and Regression Net

- Goal:
  - Ensure no navigation regressions and deterministic behavior from all entrypoints.
- Files to change:
  - tests only (feature/map test suite; optional androidTest if available).
- Tests to add/update:
  - `GeneralSettingsScreenPolicyTest`:
    - `ADS-b` tile visible in General.
    - `Proximity` no longer contains ADS-b action text.
  - optional `GeneralSettingsSubSheetBehaviorTest`:
    - Proximity transitions unaffected for OGN/Hotspots/Look and Feel/Colors.
  - navigation policy checks:
    - opening ADS-b from General resolves to `SettingsRoutes.ADSB_SETTINGS`.
- Exit criteria:
  - all added/updated tests pass; no behavior regressions in General/Proximity flow.

### Phase 4 - Docs Sync and Release Verification

- Goal:
  - Align architecture docs and prove release readiness.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` (only if settings entrypoint text is now inaccurate)
  - plan status update in this file.
- Required checks:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Optional when environment allows:
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
  - `./gradlew connectedDebugAndroidTest --no-parallel`
- Exit criteria:
  - required gates pass and docs accurately describe final wiring.

## 5) Test Plan (Consolidated)

- Unit/compose:
  - `GeneralSettingsScreenPolicyTest` update for:
    - General contains `ADS-b`.
    - Proximity sheet no longer lists `ADS-b Traffic`.
  - optional sub-sheet behavior coverage for non-ADS-b Proximity actions.
- Navigation policy tests:
  - route constant usage (`SettingsRoutes.ADSB_SETTINGS`) and nav graph mapping.
- Manual verification matrix:
  - `Settings -> General -> ADS-b` opens ADS-b settings.
  - `Settings -> General -> Proximity` does not include ADS-b row.
  - ADS-b screen back/map actions still return correctly.
- Required commands:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| General grid overcrowding after adding ADS-b tile | medium UX/readability | keep minimal layout delta and validate tap targets on compact screens | XCPro map/ui |
| Broken ADS-b entry if route string drift remains | medium navigation regression | centralize on `SettingsRoutes.ADSB_SETTINGS` and add policy test | XCPro map/ui |
| Hidden dependency on Proximity callback path | medium runtime regression | remove `onOpenAdsb` in one cohesive change + compile/test gates | XCPro map/ui |
| Regression in Proximity semantics for OGN/Hotspots/Look and Feel/Colors | low-medium UX drift | targeted sub-sheet behavior tests and manual checklist | XCPro map/ui |

## 7) Acceptance Gates

- No violations of `ARCHITECTURE.md` or `CODING_RULES.md`.
- ADS-b is directly discoverable as its own General tab/tile.
- Proximity no longer contains ADS-b action.
- ADS-b settings data ownership remains in existing repository/viewmodel chain.
- Replay behavior remains deterministic and unchanged.
- No new deviations added unless explicitly approved (`KNOWN_DEVIATIONS.md` issue/owner/expiry).

## 8) Rollback Plan

- Independent rollback units:
  - Revert General tile addition while retaining route constant normalization.
  - Re-enable Proximity ADS-b action if urgent UX rollback is needed.
- Recovery steps:
  1. Restore `onOpenAdsb` action in `ProximitySettingsSheet`.
  2. Remove/disable new General ADS-b tile.
  3. Keep `SettingsRoutes.ADSB_SETTINGS` normalization (safe improvement).
  4. Re-run required verification commands.

## 9) Post-Implementation Quality Rescore (Mandatory)

After implementation, rescore per `docs/ARCHITECTURE/AGENT.md`:

- Architecture cleanliness
- Maintainability/change safety
- Test confidence on risky paths
- Overall map/task slice quality
- Release readiness

All scores must include concrete evidence (changed files + test/gate results).
