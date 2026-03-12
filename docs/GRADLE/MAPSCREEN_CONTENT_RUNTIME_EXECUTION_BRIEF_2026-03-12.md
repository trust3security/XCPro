# MAPSCREEN_CONTENT_RUNTIME_EXECUTION_BRIEF_2026-03-12

## Purpose

Define the next release-grade, low-churn extraction step for the
`MapScreenContentRuntime*` hotspot after profiling confirmed that the first
invalidated `map-impl` build is still dominated by `:feature:map:compileDebugKotlin`.

This brief is intentionally narrow. It exists to avoid another broad refactor
that compiles but does not improve the edit loop.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/AGENT.md`
6. `docs/GRADLE/FEATURE_MAP_BUILD_HOTSPOT_ANALYSIS_2026-03-11.md`

## 0) Metadata

- Title: MapScreenContentRuntime traffic-runtime seam extraction
- Owner: XCPro Team
- Date: 2026-03-12
- Issue/PR: TBD
- Status: Draft

## 1) Evidence

Profiled first invalidated `map-impl` build, no build cache:

- Report: `build/reports/profile/profile-2026-03-12-10-24-01.html`
- Total build time: `70.01s`
- Dominant tasks:
  - `:feature:map:compileDebugKotlin` = `49.638s`
  - `:feature:map:kspDebugKotlin` = `7.529s`
  - `:app:kspDebugKotlin` = `3.686s`
  - `:app:compileDebugKotlin` = `0.042s`

Conclusion:

- the real first-spike problem is still `feature:map` Kotlin compile surface
- `app` Kotlin compile is not the bottleneck
- the next change must reduce `feature:map` ownership in a high-edit runtime
  cluster, not add more general Gradle tuning

## 2) Scope

- Problem statement:
  - `MapScreenContentRuntime.kt` still owns traffic runtime state collection,
    traffic UI-state derivation, traffic detail/panel hosting, forecast/weather
    state collection, bottom-sheet visibility, and several local UI states in
    one map-owned file.
- Why now:
  - profiling shows `feature:map` Kotlin compile is still the dominant first
    invalidation cost
  - a bounded runtime seam is lower risk than another forecast/runtime move
    after the last forecast-runtime slice was a net loss
- In scope:
  - move traffic-runtime collection/hosting out of
    `feature/map/.../MapScreenContentRuntime.kt`
  - keep the existing package names and public behavior stable
  - preserve all current SSOT owners and ViewModel owners
- Out of scope:
  - DI/Hilt rewiring
  - `MapScreenViewModel` changes
  - `MapOverlayManager` changes
  - forecast/weather runtime moves
  - bottom-sheet redesign
  - line-budget cleanup for its own sake
- User-visible impact:
  - none intended

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN trail selection keys | `OgnTrailSelectionViewModel` | `selectedTrailAircraftKeys` state flow | map-shell copies of selected keys |
| Traffic detail selection | traffic feature state owners behind `MapTrafficUiBinding` | `selectedOgnTarget`, `selectedOgnThermal`, `selectedAdsbTarget` | map-local mirrored selected target state |
| Traffic panel visibility | `rememberMapTrafficContentUiState(...)` in `feature:traffic` | `MapTrafficContentUiState` | second panel-visibility reducer in `feature:map` |
| Bottom-sheet visible flag | `MapScreenContentRuntime` local saveable state | local Compose state | feature-owned duplicate sheet visibility state |
| Forecast/weather overlay state | forecast/weather viewmodels | `overlayState` flows | traffic-owned or map-owned copies |

### 3.2 Dependency Direction

Dependency flow must remain:

`UI shell -> feature-owned UI runtime host -> ViewModel state -> feature SSOT`

No repository or manager access may be introduced into Composables beyond the
current injected/viewmodel-backed UI layer.

Modules/files touched in the first slice:

- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
- new or updated traffic UI host files under
  `feature/traffic/src/main/java/com/example/xcpro/map/ui/`

Boundary risk:

- accidentally moving bottom-sheet ownership into `feature:traffic`
- accidentally duplicating trail-selection or traffic-detail state

### 3.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| `OgnTrailSelectionViewModel` lookup inside map shell | `MapScreenContentRuntime.kt` | new traffic-owned runtime host/state helper | reduce map-shell wiring surface | compile + unit tests |
| selected trail-key collection for traffic UI | `MapScreenContentRuntime.kt` | new traffic-owned runtime host/state helper | keep traffic selection wiring in traffic feature | compile + unit tests |
| traffic panels/detail-sheet render callsite | `MapScreenContentRuntime.kt` | new traffic-owned runtime host layer | move traffic-only presentation hosting out of map shell | compile + unit tests |
| ownship-to-traffic-coordinate mapping for traffic panels | `MapScreenContentRuntime.kt` | new traffic-owned runtime host layer | keep traffic detail rendering inputs near traffic UI | compile + unit tests |

### 3.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapScreenContentRuntime.kt` -> `hiltViewModel<OgnTrailSelectionViewModel>()` | map shell reaches into traffic-owned trail-selection VM directly | traffic-owned `remember...` helper or host composable | 1 |
| `MapScreenContentRuntime.kt` -> `rememberMapTrafficContentUiState(...)` | map shell derives traffic UI state directly | traffic-owned runtime helper returns the state | 1 |
| `MapScreenContentRuntime.kt` -> `MapTrafficPanelsAndSheetsLayer(...)` | map shell hosts traffic-only panels/sheets directly | traffic-owned host composable | 1 |

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| traffic debug-panel auto-dismiss timers | Monotonic frame time | existing reducer already uses frame-time driven Compose effects |
| bottom-sheet visible state | UI local state | presentation only |
| traffic selection state | existing traffic/viewmodel owners | unchanged |

Explicitly forbidden:

- introducing wall-time logic in the new traffic runtime host
- changing replay/live timing behavior

### 3.4 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - unchanged
  - this slice must not add replay-specific branches

### 3.5 Boundary Adapter Check

This slice does not touch:

- persistence
- sensors
- network
- file I/O
- device APIs

No new ports/adapters are required.

### 3.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| duplicated traffic UI state | ARCHITECTURE SSOT | code review + compile | modified runtime host files |
| map shell still reaches into traffic trail-selection VM after the slice | dependency direction + ownership | compile + diff review | `MapScreenContentRuntime.kt` |
| traffic host silently changes sheet/detail behavior | UDF/UI behavior preservation | unit test + manual smoke | traffic UI tests, bottom-sheet smoke |
| broad refactor creep | release-grade/no churn requirement | scope stop rule | this brief |

## 4) First Slice Definition

### 4.1 Exact Slice

Implement only this:

1. Add a traffic-owned runtime state/helper under `feature:traffic` that:
   - obtains `OgnTrailSelectionViewModel`
   - collects `selectedTrailAircraftKeys`
   - derives `MapTrafficContentUiState`
   - exposes the trail-selection callback used by traffic panels/bottom tabs
2. Add a traffic-owned host composable under `feature:traffic` that:
   - receives `MapTrafficUiBinding`, `MapTrafficUiActions`,
     `MapLocationUiModel?`, and `UnitsPreferences`
   - computes `TrafficMapCoordinate?`
   - renders `MapTrafficPanelsAndSheetsLayer(...)`
3. Update `MapScreenContentRuntime.kt` to:
   - stop creating `OgnTrailSelectionViewModel`
   - stop collecting `selectedTrailAircraftKeys`
   - stop computing `TrafficMapCoordinate` for traffic panels
   - use the new traffic-owned helper/host instead
4. Keep `MapBottomTabsSection(...)` in `feature:map` for this slice.
   - it may continue to receive `ognTrailAircraftRows`
   - do not move forecast/weather/QNH or sheet visibility with this change

### 4.2 What Must Not Move In This Slice

- `isBottomTabsSheetVisible`
- `selectedBottomTabName`
- forecast `hiltViewModel()` acquisition
- weather `hiltViewModel()` acquisition
- `MapBottomTabsSection(...)`
- `MapAuxiliaryPanelsAndSheetsSection(...)`
- `MapOverlayStack(...)`
- any DI module

### 4.3 Stop Conditions

Stop and back out the slice if any of these become necessary:

- changing DI bindings or module dependencies
- moving forecast/weather code in the same change
- changing `MapScreenViewModel`
- changing `MapOverlayManager`
- adding a second traffic state owner
- touching more than the targeted map file plus a small traffic host/helper pair

## 5) Data Flow

Before:

```text
MapScreenContentRuntime
  -> hiltViewModel(OgnTrailSelectionViewModel)
  -> selectedTrailAircraftKeys.collectAsStateWithLifecycle()
  -> rememberMapTrafficContentUiState(...)
  -> MapTrafficPanelsAndSheetsLayer(...)
```

After:

```text
MapScreenContentRuntime
  -> traffic-owned runtime helper/host
     -> hiltViewModel(OgnTrailSelectionViewModel)
     -> selectedTrailAircraftKeys.collectAsStateWithLifecycle()
     -> rememberMapTrafficContentUiState(...)
     -> MapTrafficPanelsAndSheetsLayer(...)
```

SSOT does not change. Ownership of the traffic UI runtime seam does.

## 6) Implementation Phases

### Phase 0: Brief Lock

- Goal:
  - commit this brief before code changes
- Files:
  - this file
  - `docs/GRADLE/README.md`
- Exit criteria:
  - exact first slice is documented

### Phase 1: Traffic Runtime Helper/Host

- Goal:
  - add the traffic-owned runtime helper/host and switch
    `MapScreenContentRuntime.kt` to it
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - new or updated files under
    `feature/traffic/src/main/java/com/example/xcpro/map/ui/`
- Tests:
  - update or add focused traffic UI tests only if compile fallout demands it
- Exit criteria:
  - compile gates pass
  - no behavior change intended

### Phase 2: Measurement Gate

- Goal:
  - determine whether the slice reduced the `map-impl` path
- Checks:
  - rerun `:app:compileDebugKotlin --profile`
  - compare against the retained historical `map-impl` baseline
  - compare against current kept state
- Exit criteria:
  - keep only if the slice is neutral-to-positive and remains low churn
  - back out if it is another net loss

## 7) Test Plan

- Unit tests:
  - existing traffic UI tests where available
  - add only narrowly if a host/helper extraction changes test compilation
- Replay/regression tests:
  - none expected for this slice; replay behavior must remain untouched
- UI/instrumentation tests:
  - not required unless behavior drift appears
- Degraded/failure-mode tests:
  - compile/run with no selected traffic details
  - compile/run with selected OGN/ADS-B details still active

Required verification:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Practical local loop for this slice:

```bash
./gradlew :feature:traffic:compileDebugKotlin :feature:map:compileDebugKotlin :app:assembleDebug
./gradlew :feature:traffic:compileDebugUnitTestKotlin :feature:map:compileDebugUnitTestKotlin
./gradlew :app:compileDebugKotlin --profile --console=plain
```

## 8) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| slice only moves a small amount of code and gives no speed win | Medium | benchmark immediately; back out if negative | XCPro Team |
| accidental sheet/detail behavior drift | Medium | preserve callbacks and state names exactly | XCPro Team |
| host/helper grows into another large file | Low | cap the new files to one helper + one host only | XCPro Team |

## 9) Acceptance Gates

- No new architecture violations
- No new SSOT owner
- No DI or module graph changes
- No replay behavior changes
- compile/test/assemble gates pass
- `map-impl` is at least neutral, preferably improved
- if benchmark regresses materially, do not keep the slice

## 10) Rollback Plan

- Revert only the new traffic runtime host/helper files and the small
  `MapScreenContentRuntime.kt` callsite change.
- Restore the previous direct `OgnTrailSelectionViewModel` and
  `MapTrafficPanelsAndSheetsLayer(...)` wiring in `MapScreenContentRuntime.kt`.
- Re-run:
  - `:feature:traffic:compileDebugKotlin`
  - `:feature:map:compileDebugKotlin`
  - `:app:assembleDebug`
