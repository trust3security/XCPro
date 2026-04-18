# CHANGE_PLAN_GENERAL_BOTTOM_SHEET_2026-03-02.md

## 0) Metadata

- Title: General Settings -> Production-Grade Bottom Sheet Close UX
- Owner: XCPro map/ui
- Date: 2026-03-02
- Issue/PR: TBD
- Status: Draft (comprehensive code pass applied)

Follow-up (2026-03-03):
- Requested next step is map-hosted General sheet (no route transition on open/close).
- Tracked in: `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/CHANGE_PLAN_GENERAL_MAP_HOSTED_SHEET_2026-03-03.md`.
- Re-pass status: route-hosted General remains active in code; migration is not yet applied.

## 1) Scope

- Problem statement:
  - `SettingsRoutes.GENERAL` currently hosts a full-screen `Scaffold` UI.
  - Requested UX is bottom-sheet interaction while preserving current General layout and icon actions, plus swipe-down return to map.
- Why now:
  - General is opened from both nav drawer and map settings shortcut.
  - Dismiss behavior must be deterministic and consistent across icon tap, back press, and swipe-down.
- In scope:
  - Keep `SettingsRoutes.GENERAL` as the single route owner.
  - Convert General host container from full-screen scaffold to `ModalBottomSheet`.
  - Preserve existing General tile layout, routes, and visuals.
  - Harden open/close semantics from all entrypoints.
  - Add explicit regression tests for close behavior.
- Out of scope:
  - General content redesign.
  - New settings routes.
  - Map/task/domain pipeline rewiring.
  - Migration to a new navigation framework.
- User-visible impact:
  - General feels like a sheet and can be dismissed by swipe-down.
  - Icon actions remain available and return to map consistently.

## 2) Comprehensive Code Pass Findings

1) Entry points are centralized and stable.
- Drawer path: `feature/map/src/main/java/com/trust3/xcpro/navdrawer/DrawerMenuSections.kt`.
- Map settings shortcut path: `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt`.
- Route registration: `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`.

2) General screen behavior is currently inconsistent with sheet-style settings screens.
- General back icon currently pops and opens drawer.
- Newer sheet settings (`Hotspots`, `Thermalling`) use `ModalBottomSheet` + `navigateUp()` for dismiss.

3) Existing test coverage is policy-only, not close-contract coverage.
- `GeneralSettingsScreenPolicyTest` validates tile routing/policy, not dismiss routes.

4) Duplicate navigation push risk exists.
- Current settings shortcut uses route guard (`currentDestination?.route`) but does not set `launchSingleTop`.

5) Map lifecycle implications depend on host strategy.
- Route-level sheet host keeps current behavior profile (no map-overlay arbitration changes).
- Map-overlay host would alter map visibility and traffic-stream gating behavior and is out of scope for this request.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| General route identity | `SettingsRoutes.GENERAL` | nav route string | alias routes for same screen |
| General visibility | Nav back stack | route presence | local "showGeneral" booleans outside nav owner |
| General proximity mini-sheet | General UI composable local state | `showProximitySettingsSheet` | external persistence/VM for transient-only state |
| Drawer state | existing `MapUiState` + `DrawerState` bridge | current map side-effects flow | General-local drawer mirrors |

### 3.2 Dependency Direction

Remains:

`UI -> domain -> data`

No new UI -> data shortcuts.

### 3.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| General host container (full-screen vs sheet) | `SettingsScreen` scaffold host | `SettingsScreen` bottom-sheet host | match requested swipe-down UX | compose tests + manual checks |
| General close contract (icon/back/swipe) | mixed local lambdas | one unified close-to-map callback | deterministic UX and fewer regressions | unit + instrumentation checks |

### 3.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| settings shortcut route open | route check only | route check + `launchSingleTop = true` | Phase 3 |
| General close actions | mixed `navigateUp` / `pop+openDrawer` paths | unified `closeToMap()` used by icons + sheet dismiss | Phase 2 |

### 3.3 Host Strategy Decision

Selected strategy for this plan:
- Route-hosted `ModalBottomSheet` on `SettingsRoutes.GENERAL`.

Why:
- Lowest architecture risk for current map/task stack.
- Aligns with existing settings-sheet pattern in this codebase.
- Satisfies requested swipe-down + icon return behavior.

Deferred strategy (separate plan if requested):
- Map-overlay sheet host inside map route.
- Requires explicit modal arbitration with existing map sheets.

### 3.4 Time Base

No new time-dependent logic.

| Value | Time Base | Why |
|---|---|---|
| General sheet open/close | N/A | navigation/UI state only |

### 3.5 Threading and Cadence

- Main only for UI navigation/gesture callbacks.
- No new background work.
- No change to sensor/fusion cadence.

### 3.6 Replay Determinism

- Deterministic for same input: Yes.
- Randomness: No.
- Replay/live divergence introduced: No.

### 3.7 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Duplicate General state owners | SSOT rules (`ARCHITECTURE.md`) | review + tests | updated General tests |
| Navigation duplication on rapid taps | UDF predictability | compose/unit test + code review | shortcut path tests |
| Close semantics drift between swipe/back/icon | UX contract stability | compose + instrumentation + manual matrix | new General sheet tests |
| Behavior drift in existing tiles | regression safety | existing policy tests retained | `GeneralSettingsScreenPolicyTest` |

## 4) Data Flow (Before -> After)

Before:

`Drawer/SettingsShortcut -> navigate(settings) -> full-screen General -> mixed close paths`

After:

`Drawer/SettingsShortcut -> navigate(settings, singleTop) -> General ModalBottomSheet -> unified closeToMap() via swipe/back/icon`

## 5) Production-Grade Phased Implementation

### Phase 0 - Baseline Lock and Contract Matrix

- Goal:
  - Lock expected UX and evidence before editing.
- Deliverables:
  - Behavior matrix with exact expected outcomes:
    - open from drawer.
    - open from map settings shortcut.
    - top-left back icon.
    - top-right map icon.
    - system back.
    - swipe-down dismiss.
  - Confirm target close rule: all close vectors return to map route deterministically.
- Files:
  - `docs/ARCHITECTURE/CHANGE_PLAN_GENERAL_BOTTOM_SHEET_2026-03-02.md`.
- Exit criteria:
  - Matrix approved and used as acceptance baseline.

### Phase 1 - Extract Stateless General Content

- Goal:
  - Separate content from host to prevent visual drift during host conversion.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`.
- Required implementation quality:
  - Content composable remains route-host agnostic.
  - Keep exact row order, route mapping, spacing, and icon use.
  - Preserve proximity sub-sheet behavior.
- Tests:
  - Existing `GeneralSettingsScreenPolicyTest` remains green.
- Exit criteria:
  - No UX behavior change from baseline.

### Phase 2 - Bottom Sheet Host Conversion with Unified Close Semantics

- Goal:
  - Switch host to `ModalBottomSheet` and unify close behavior.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Settings-df.kt`.
- Required implementation quality:
  - Use `rememberModalBottomSheetState(skipPartiallyExpanded = true)`.
  - Use one shared `closeToMap()` callback:
    - first try `popBackStack("map", inclusive = false)`.
    - fallback `navigateUp()` only if map is not found.
  - Wire same callback to:
    - `onDismissRequest` (swipe/scrim path),
    - top-left back icon,
    - top-right map icon.
  - Keep visual parity (header/title/content) and avoid extra chrome.
- Tests:
  - Add tests for back/map icon behavior calling same close contract.
- Exit criteria:
  - Swipe-down/back/icon all land on map route.

### Phase 3 - Entry-Point Navigation Hardening

- Goal:
  - Remove duplicate-open edge cases and keep route ownership single.
- Files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt`.
  - `feature/map/src/main/java/com/trust3/xcpro/navdrawer/DrawerMenuSections.kt`.
- Required implementation quality:
  - Add `launchSingleTop = true` on `SettingsRoutes.GENERAL` navigation.
  - Keep existing task-mode drawer-block guard for settings shortcut.
  - Preserve current behavior when already on General route.
- Tests:
  - Add rapid-tap/open idempotency test where feasible.
- Exit criteria:
  - No duplicate back-stack entries for repeated open requests.

### Phase 4 - Regression Test Expansion (Unit + Compose + Device when available)

- Goal:
  - Lock contract behavior for production confidence.
- Files:
  - `feature/map/src/test/java/com/trust3/xcpro/screens/navdrawer/GeneralSettingsScreenPolicyTest.kt`.
  - add `GeneralSettingsSheetBehaviorTest.kt` (same package) if needed.
  - optional device test under `app/src/androidTest` if environment supports it.
- Required tests:
  - Existing policy tests retained.
  - New tests for:
    - back icon close contract.
    - map icon close contract.
    - route targets for key tiles unchanged.
    - swipe-down dismiss path (instrumentation/manual evidence if compose test cannot reliably simulate drag-dismiss).
- Exit criteria:
  - All old tests pass; new contract tests pass.

### Phase 5 - Verification, Evidence, and Docs Sync

- Goal:
  - Prove production readiness with required gates.
- Required commands:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Optional when available:
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
- Evidence expectations:
  - command pass/fail summary.
  - files changed list.
  - manual matrix result log (all close vectors).
- Exit criteria:
  - Required gates pass.
  - No architecture drift introduced.

## 6) Test Plan (Consolidated)

- Unit/compose:
  - preserve existing General policy tests.
  - add close contract tests for top-bar actions.
  - add route idempotency test for repeated open requests.
- Instrumentation (when available):
  - swipe-down dismiss from General sheet returns to map.
- Manual matrix:
  - open from drawer and shortcut.
  - close via back icon, map icon, system back, swipe-down.
  - verify tile navigation unchanged.

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Close behavior drifts across vectors | High | single `closeToMap()` callback wired everywhere | XCPro map/ui |
| Duplicate settings route pushes | Medium | add `launchSingleTop` + route guard | XCPro map/ui |
| Swipe dismiss hard to assert in JVM compose tests | Medium | add instrumentation/manual evidence requirement | XCPro map/ui |
| Visual drift from extraction refactor | Medium | Phase 1 parity check + existing policy tests | XCPro map/ui |

## 8) Acceptance Gates

- General keeps existing content and visuals.
- General host is bottom-sheet style.
- All close vectors (icons/back/swipe) return to map route.
- Drawer and settings shortcut open behavior remains correct.
- No duplicate `settings` back-stack entries on rapid repeated opens.
- Existing General tile route behavior unchanged.
- Required verification commands pass.

## 9) Rollback Plan

- Revert only General host conversion in `Settings-df.kt`.
- Keep extracted content if safe, otherwise revert extraction too.
- Keep route constants and entrypoints unchanged.
- Re-run required verification commands.

