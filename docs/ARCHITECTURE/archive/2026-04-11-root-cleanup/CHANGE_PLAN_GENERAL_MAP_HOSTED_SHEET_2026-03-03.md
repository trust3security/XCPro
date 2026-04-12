# CHANGE_PLAN_GENERAL_MAP_HOSTED_SHEET_2026-03-03.md

## 0) Metadata

- Title: General Settings Sheet Hosted in MapScreen (No Map Route Transition)
- Owner: XCPro map/ui
- Date: 2026-03-03
- Issue/PR: TBD
- Status: Implemented (2026-03-03)
- Agent execution contract:
  - `docs/ARCHITECTURE/archive/2026-04-11-root-cleanup/AGENT_EXECUTION_CONTRACT_GENERAL_MAP_HOSTED_SHEET_2026-03-03.md`

## 1) Scope

- Problem statement:
  - `General` is currently opened via nav route `SettingsRoutes.GENERAL` (`"settings"`), then rendered as a route-hosted `ModalBottomSheet`.
  - Entering/exiting that route can dispose/recreate map composition/runtime ownership, increasing MapView rebind/re-init risk and lifecycle churn.
  - Requested behavior is to open `General` as a MapScreen-hosted modal so the map route remains active and does not re-render from route swaps.
- Why now:
  - User-requested UX and performance hardening.
  - Existing architecture document already marked map-hosted strategy as deferred; this plan productizes that path.
- In scope:
  - Host `General` sheet inside MapScreen UI layer.
  - Rewire map settings shortcut and drawer `General` item to open local sheet owner (no route transition).
  - Preserve current `General` content, tiles, and child settings navigation behavior.
  - Add phased compatibility strategy for legacy callers that still navigate `"settings"`.
- Out of scope:
  - Re-design of General tiles/content.
  - New domain/business logic.
  - Replay/fusion/task algorithm changes.
- User-visible impact:
  - From MapScreen, opening/closing `General` no longer navigates away from map route.
  - Close behavior remains deterministic (icon, back, swipe-down).

## 1A) Comprehensive Code Pass Refresh (2026-03-03)

Current-state findings after workspace re-pass:

1) General is still route-owned and map entrypoints still navigate to route.
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  (`onSettingsTap -> navController.navigate(SettingsRoutes.GENERAL)`).
- `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerMenuSections.kt`
  (`Settings -> General -> navigate(SettingsRoutes.GENERAL)`).
- `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
  (`navController.navigate("settings")`).

2) Route host remains active in NavGraph.
- `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  still registers `composable(SettingsRoutes.GENERAL) { SettingsScreen(...) }`.

3) Route close path remains back-stack based.
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/SettingsDfRuntime.kt`
  uses `closeGeneralToMap/closeGeneralToDrawer` with `popBackStack("map", false)`.

4) Regression test net shrank in related map scaffold area.
- `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenScaffoldPolicyTest.kt`
  is currently deleted in workspace changes; this plan now requires replacement
  coverage for map General entrypoint policy before migration cutover.

Production-grade implication:
- Host-migration is still required to meet the "no map route transition" target.
- Add/restore targeted map-entrypoint policy tests before rewiring (Phase 0).

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| General sheet visibility while on map | `MapModalManager` (UI runtime owner) | `StateFlow<Boolean>` or single-modal enum state | Local booleans in unrelated composables, route + modal dual ownership |
| General sub-sheet selection (`PROXIMITY`, `WEATHER`, `OGN`, `HOTSPOTS`, `THERMALLING`) | General sheet host composable (UI transient state) | `rememberSaveable` local state | Mirrored state in ViewModel/repository |
| Drawer expand/collapse state | `ConfigurationRepository` via `NavDrawerViewModel` | persisted nav drawer config | New settings-specific persistence path |
| Map settings shortcut placement/size | `MapWidgetLayoutRepository` | `MapWidgetLayoutViewModel.offsets` | ad-hoc widget offset persistence |

### 2.2 Dependency Direction

Confirmed unchanged:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map` UI map/navdrawer/settings files
  - `app` nav graph and route compatibility only
  - `docs/ARCHITECTURE` updates
- Any boundary risk:
  - Medium: navigation-to-modal ownership migration and back-press arbitration.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| General sheet host container | `AppNavGraph` destination `SettingsRoutes.GENERAL` | MapScreen UI host (`MapScreenContent` + `MapModalManager`) | Keep map route active and avoid route churn | compose + manual lifecycle matrix |
| Map settings shortcut open action | `navController.navigate(SettingsRoutes.GENERAL)` | modal open callback to map modal owner | Remove route transition on map | map UI test |
| Drawer `General` action on map | `navController.navigate(SettingsRoutes.GENERAL)` | map-hosted modal open callback | unify map entrypoints | navdrawer test + manual |
| Legacy `"settings"` route support | full owner | temporary compatibility shim | safe phased rollout | route compatibility test |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map/.../MapScreenScaffoldInputs.kt` settings tap | direct `navController.navigate(SettingsRoutes.GENERAL)` | invoke map modal owner open callback | Phase 3 |
| `feature/map/.../navdrawer/DrawerMenuSections.kt` General item | direct `navController.navigate(SettingsRoutes.GENERAL)` | invoke callback passed from `NavigationDrawer` | Phase 3 |
| `app/.../MainActivityScreen.kt` waypoints nav to `"settings"` | route navigation | staged migration to map-first intent + one-shot open-general signal | Phase 4 |

### 2.3 Time Base

No new time-dependent logic introduced.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| General sheet visibility | N/A | UI transient modal state only |
| Navigation compatibility trigger (if used) | N/A | route/intents, not clock-based |

Forbidden comparisons remain unchanged:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - UI modal open/close on Main
  - No new background loops
- Primary cadence/gating sensor:
  - user input events only (tap/back/swipe)
- Hot-path latency budget:
  - open/close interaction under one frame budget when possible; no blocking operations in modal handlers.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (no replay pipeline change)
- Randomness used: No
- Replay/live divergence rules:
  - unchanged; this plan is UI-host migration only.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Dual ownership of General visibility (route + map modal) | SSOT ownership rules (`ARCHITECTURE.md`) | review + compose tests | new map-host General visibility tests |
| Back behavior regression (drawer/modal/task) | UDF/UI side-effect discipline | compose/instrumentation + manual matrix | `MapScreenBackHandler` tests |
| Reintroduction of route transition for map settings shortcut | Map UI contract in `PIPELINE.md` | unit/compose test + review | `MapScreenScaffoldInputs` tests |
| Hidden lifecycle churn still present | maintainability/perf contract | instrumentation/manual evidence | repeated open/close lifecycle matrix |
| Route compatibility break for legacy callers | navigation stability | unit test + staged rollout flag | nav graph tests, caller migration checklist |

## 3) Data Flow (Before -> After)

Before:

`Map settings shortcut or Drawer->General -> navController.navigate("settings") -> NavHost route SettingsRoutes.GENERAL -> SettingsScreen ModalBottomSheet -> close via popBackStack("map")`

After (target):

`Map settings shortcut or Drawer->General -> MapModalManager.showGeneralSettingsModal() -> MapScreen-hosted General ModalBottomSheet -> close via modal owner (back/swipe/icon) with map route unchanged`

Compatibility path during rollout:

`Legacy caller -> navigate("settings") -> compatibility shim -> open map-hosted General and return`

## 4) Implementation Phases

### Phase 0 - Baseline and Safety Net
- Goal:
  - Freeze current behavior and establish regression guardrails before host migration.
- Files to change:
  - tests only (no behavior change)
- Tests to add/update:
  - map open/close behavior tests for General entrypoints.
  - back-handler precedence tests for drawer/modal/task panel interaction.
- Exit criteria:
  - Baseline verified, no behavior drift introduced.

### Phase 1 - Extract Reusable General Sheet Host
- Goal:
  - Separate General content/sheet host into reusable composable(s) independent from nav-route ownership.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/SettingsDfRuntime.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/SettingsDfRuntimeSheets.kt` (if needed)
- Tests to add/update:
  - `GeneralSettingsScreenPolicyTest` updates for extracted host.
- Exit criteria:
  - Existing route still works, but host can be invoked from map context.

### Phase 2 - Map Modal Ownership and Arbitration
- Goal:
  - Extend map modal owner to include General visibility with deterministic back handling.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/MapModalManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
- Tests to add/update:
  - modal manager tests (open/close/back semantics, collision policy).
- Exit criteria:
  - General sheet can be hosted in MapScreen with deterministic close precedence.

### Phase 3 - Rewire Map Entry Points
- Goal:
  - Remove map-route General navigation for map-specific entrypoints.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/navdrawer/NavigationDrawer.kt`
  - `feature/map/src/main/java/com/example/xcpro/navdrawer/DrawerMenuSections.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputModel.kt` (callback plumbing)
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffold.kt` (if callback propagation changes)
- Tests to add/update:
  - settings shortcut tap opens map-local General.
  - drawer General item opens map-local General.
- Exit criteria:
  - Settings shortcut + drawer General no longer trigger route transitions.

### Phase 4 - Compatibility and Legacy Route Retirement
- Goal:
  - Migrate remaining external callers and retire or deprecate `SettingsRoutes.GENERAL` destination safely.
- Files to change:
  - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`
  - `app/src/main/java/com/example/xcpro/AppNavGraph.kt`
  - `feature/map/src/main/java/com/example/xcpro/navigation/SettingsRoutes.kt` (if route is deprecated)
- Tests to add/update:
  - caller compatibility tests
  - no-stale-route tests
- Exit criteria:
  - No production caller requires route-hosted General.

### Phase 5 - Hardening, Docs, and Release Verification
- Goal:
  - Production-grade confidence and docs sync.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - this plan + any migration notes
- Tests to add/update:
  - instrumentation/compose matrix for repeated open/close and back gestures.
- Exit criteria:
  - Required gates pass and docs match final wiring.

## 5) Test Plan

- Unit tests:
  - `MapModalManager` state transitions and back behavior.
  - callback policy tests for map settings shortcut and drawer General action.
- Replay/regression tests:
  - none required beyond existing replay suite (UI-only change), but run standard regression set.
- UI/instrumentation tests:
  - open/close General from map shortcut and drawer.
  - verify map interaction resumes immediately after closing sheet.
  - repeated open/close stress (no crash/no stuck overlays).
- Degraded/failure-mode tests:
  - back-press precedence when drawer and modal states overlap.
  - map task panel visible while General open.
- Boundary tests for removed bypasses:
  - assert no map-path `navigate(SettingsRoutes.GENERAL)` callsites remain.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Modal collision with existing map sheets/details | High | single modal owner policy + back-priority matrix + tests | XCPro map/ui |
| Legacy caller still depends on `"settings"` route | Medium | compatibility shim phase + grep gate for callsites | XCPro app/navigation |
| Regression in close semantics (icon/swipe/back) | Medium | shared close handlers and explicit tests per trigger path | XCPro map/ui |
| Hidden map lifecycle churn persists | Medium | instrumentation/manual lifecycle evidence before cutover | XCPro map/ui |
| Overly large diff introduces instability | Medium | phased rollout + small PR slices + feature-flag option | XCPro map/ui |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced for General visibility.
- Time base declarations remain explicit and unchanged.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved.
- Map entrypoints to General operate without route transition.

## 8) Rollback Plan

- What can be reverted independently:
  - Entry-point rewiring (shortcut/drawer callbacks) can be reverted while keeping extracted host.
  - Map modal ownership can be reverted to route-hosted behavior if collision bugs appear.
  - Compatibility route can remain active as fallback during rollback.
- Recovery steps if regression is detected:
  1. Re-enable route-hosted General path for all callers.
  2. Disable map-hosted General via feature flag or callback gating.
  3. Keep extracted sheet content unchanged to reduce churn.
  4. Re-run required verification gates and publish incident notes in plan.

## 9) Implementation Advice (Production)

- Prefer a staged migration with compatibility first, not big-bang removal.
- Keep exactly one authoritative modal owner in MapScreen to prevent overlap bugs.
- Preserve existing `General` UI content verbatim in Phase 1; migrate host ownership first, redesign later.
- Land test scaffolding before navigation rewiring so failures are actionable.
- Add explicit grep/check in review notes for remaining `navigate(SettingsRoutes.GENERAL)` map callsites.

## 10) Re-pass Verification Snapshot (2026-03-03)

Executed on current workspace during comprehensive refresh:

- `python scripts/arch_gate.py` -> PASS
- `./gradlew enforceRules` -> PASS
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.screens.navdrawer.GeneralSettingsScreenPolicyTest"` -> PASS
- `./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.MapOverlayWidgetGesturesTest"` -> PASS
- `./gradlew testDebugUnitTest` -> PASS
- `./gradlew assembleDebug` -> PASS

Notes:
- Workspace contains unrelated concurrent edits outside this plan scope.
- This plan remains the authoritative phased migration path for map-hosted General.

## 11) Implementation Snapshot (Executed 2026-03-03)

Delivered:
- Map-owned `General` modal visibility is authoritative in `MapModalManager` and hosted in
  `MapScreenScaffold` via `GeneralSettingsSheetHost`.
- Map settings shortcut no longer navigates to `SettingsRoutes.GENERAL`; it now opens the map modal owner.
- Drawer `Settings -> General` now uses a callback path from `NavigationDrawer` to the map modal owner.
- Added compatibility signal `MapNavigationSignals.OPEN_GENERAL_SETTINGS_ON_MAP`.
- `MainActivityScreen` legacy path now targets `map` and sets the one-shot open-general signal.
- `AppNavGraph` keeps `SettingsRoutes.GENERAL` as a compatibility redirect shim to map + signal.

Added/updated tests:
- `feature/map/src/test/java/com/example/xcpro/map/MapModalManagerTest.kt`
- `feature/map/src/test/java/com/example/xcpro/navdrawer/DrawerMenuSectionsTest.kt`

Phase gate verification:
- Phase 2:
  - `./gradlew :app:assembleDebug` PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapModalManagerTest"` PASS
- Phase 3:
  - `./gradlew :app:assembleDebug` PASS
  - `./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.MapOverlayWidgetGesturesTest"` PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.navdrawer.DrawerMenuSectionsTest"` PASS
- Phase 4:
  - `./gradlew :app:assembleDebug` PASS
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.screens.navdrawer.GeneralSettingsScreenPolicyTest" --tests "com.example.xcpro.navdrawer.DrawerMenuSectionsTest"` PASS

## 12) Final Verification Matrix (2026-03-03)

- `python scripts/arch_gate.py` -> PASS
- `./gradlew enforceRules` -> PASS
- `./gradlew testDebugUnitTest` -> PASS
- `./gradlew assembleDebug` -> PASS

Note:
- `./gradlew testDebugUnitTest` failed once on
  `ProfileRepositoryTest.ioReadError_preservesLastKnownGoodState`, then passed on immediate rerun.
  The failure was treated as pre-existing/flaky because this change set does not touch profile repository paths.

