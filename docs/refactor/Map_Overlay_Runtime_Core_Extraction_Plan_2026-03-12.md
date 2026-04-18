# Map Overlay Runtime Core Extraction Plan

## 0) Metadata

- Title: Extract map overlay/runtime core into `:feature:map-runtime`
- Owner: Codex
- Date: 2026-03-12
- Issue/PR: TBD
- Status: In Progress
- Progress note:
  - 2026-03-12: Phase B implemented; moved the forecast/weather runtime leaf cluster into `:feature:map-runtime`, replaced direct `MapScreenState` access with `ForecastWeatherOverlayRuntimeState`, and kept `MapOverlayManagerRuntime` in `feature:map` as the shell-facing owner for the next runtime-core slice.
  - 2026-03-12: Verification for Phase B:
    - `./gradlew :feature:map-runtime:compileDebugKotlin` passed
    - `./gradlew :feature:map:compileDebugKotlin` passed
    - full repo gates remain blocked only by the existing unrelated failures in `ProfileRepositoryTest.kt` and the `MapScreenViewModel.kt` line-budget gate
  - 2026-03-12: Exact Phase C seam pass completed:
    - `MapOverlayManagerRuntimeBaseOpsDelegate.kt`, `MapOverlayRuntimeMapLifecycleDelegate.kt`, `MapOverlayRuntimeStatusCoordinator.kt`, and `MapOverlayRuntimeStateAdapter.kt` are still shell-mixed and must stay in `feature:map` for the next slice
    - traffic/OGN runtime delegates are already leaf-owned in `feature:traffic`
    - forecast/weather overlay runtime leaf ownership is already moved into `:feature:map-runtime`
    - the next safe runtime-core move is to narrow `MapOverlayManagerRuntime` behind shell adapters first, then move the owner in a second step
  - 2026-03-12: Exact Phase C1 constructor pass completed:
    - `MapOverlayManagerRuntime.kt` only touches `MapScreenState` to construct shell collaborators (`MapOverlayRuntimeStateAdapter`, `MapForecastWeatherOverlayRuntimeStateAdapter`, `MapOverlayRuntimeMapLifecycleDelegate`, `MapOverlayRuntimeStatusCoordinator`)
    - the public `MapOverlayManager` API does not need to change for C1; the shell adapter can absorb collaborator construction
    - `MapOverlayRuntimeInteractionDelegate.kt` is already runtime-only and does not block the owner move
  - 2026-03-12: Phase C1 implemented:
    - added runtime-side shell-port contracts for overlay lifecycle/status
    - moved `MapOverlayRuntimeCounters` into `:feature:map-runtime`
    - `MapOverlayManagerRuntime.kt` no longer owns `MapScreenState` or constructs shell delegates internally
    - `MapOverlayManager.kt` now owns shell collaborator construction and attaches those ports while preserving its external API
  - 2026-03-12: Verification for Phase C1:
    - `./gradlew :feature:map-runtime:compileDebugKotlin` passed
    - `./gradlew :feature:map:compileDebugKotlin` passed
  - 2026-03-12: Exact Phase C2 seam pass completed:
    - `MapOverlayManagerRuntime.kt` is now the real move payload; C1 removed the direct `MapScreenState` blocker
    - `MapOverlayManager.kt` is already the only production instantiation site and can remain the shell adapter unchanged at the API level
    - the existing `MapOverlayManager*` behavior tests instantiate the shell adapter and should stay in `feature:map`
    - `MapOverlayRuntimeInteractionDelegate.kt` is runtime-only and can move with the owner if keeping it co-located reduces split ownership
    - no active rule script path currently hardcodes `MapOverlayManagerRuntime.kt`; the remaining path references are active plans/pipeline notes or historical docs
  - 2026-03-12: Follow-up Phase C2 seam pass completed:
    - `MapOverlayRuntimeInteractionDelegate.kt` is no longer optional for the owner move; `MapOverlayManagerRuntime.kt` constructs it directly, so leaving it in `feature:map` would create a `:feature:map-runtime -> :feature:map` back-edge
    - `MapOverlayRuntimeInteractionDelegateTest.kt` must move with the delegate because the helper is `internal`; keeping the test in `feature:map` would require widening visibility and add churn
    - `MapOverlayManagerRuntime.kt` still carries stale shell imports (`AirspaceUseCase`, `WaypointFilesUseCase`, `SnailTrailManager`) that must be removed in the move slice because `:feature:map-runtime` does not depend on those owners
    - no direct tests instantiate `MapOverlayManagerRuntime`, so the remaining overlay-manager behavior tests can stay shell-owned in `feature:map`
  - 2026-03-12: Phase C2 implemented:
    - moved `MapOverlayManagerRuntime.kt` into `:feature:map-runtime`
    - moved `MapOverlayRuntimeInteractionDelegate.kt` plus `MapOverlayRuntimeInteractionDelegateTest.kt` into `:feature:map-runtime`
    - kept `MapOverlayManager.kt` and the shell-owned `MapOverlayManager*` behavior tests in `feature:map`
    - removed stale shell imports from the moved runtime owner
  - 2026-03-12: Verification for Phase C2:
    - `./gradlew :feature:map-runtime:compileDebugKotlin` passed
    - `./gradlew :feature:map:compileDebugKotlin` passed
    - `./gradlew :feature:map-runtime:testDebugUnitTest` passed
    - `./gradlew assembleDebug` passed
    - `./gradlew enforceRules` still fails only on the existing `MapScreenViewModel.kt` line-budget gate
    - `./gradlew testDebugUnitTest` still fails on the existing unrelated test compile blockers in `GlideTargetRepositoryTest.kt` and `MapScreenViewModelTestRuntime.kt`
  - 2026-03-12: Exact Phase C3 seam pass completed:
    - `MapOverlayManagerRuntimeBaseOpsDelegate.kt` is the next clean runtime-owner payload; it remains part of the same overlay/runtime cluster already being moved
    - the current delegate still carries an unused `SnailTrailManager` constructor dependency and import; remove that in C3 instead of dragging trail ownership into the slice
    - there is no dedicated `MapOverlayManagerRuntimeBaseOpsDelegateTest`; behavior coverage continues to come through the shell-owned `MapOverlayManager*` tests in `feature:map`
    - moving the delegate into `:feature:map-runtime` requires making its debug logging explicit because the module namespace is `com.trust3.xcpro.map.runtime`; do not rely on the shell module's implicit `BuildConfig`
    - `MapOverlayRuntimeMapLifecycleDelegate.kt`, `MapOverlayRuntimeStatusCoordinator.kt`, and `MapOverlayManagerRuntimeStatus.kt` stay shell-owned for C3
  - 2026-03-12: Phase C3 implemented:
    - moved `MapOverlayManagerRuntimeBaseOpsDelegate.kt` into `:feature:map-runtime`
    - replaced direct `feature:map` use-case/helper dependencies with shell-supplied refresh closures from `MapOverlayManager.kt`
    - removed the unused `SnailTrailManager` constructor dependency from the delegate
    - made the delegate's debug logging explicitly runtime-module-owned
    - kept lifecycle/status/reporting adapters shell-owned in `feature:map`
  - 2026-03-12: Verification for Phase C3:
    - `./gradlew :feature:map-runtime:compileDebugKotlin` passed
    - `./gradlew :feature:map:compileDebugKotlin` passed

## 1) Scope

- Problem statement:
  - The retained `feature:map` shell is still the main incremental compile bottleneck after the forecast, weather, tasks, and settings extractions.
  - The largest remaining architecture-aligned runtime seam is the overlay/runtime core centered on `MapOverlayManagerRuntime`.
- Why now:
  - Warm edit timings across the remaining shell files are all still in the same `44s` to `50s` band, which means the next compile-speed win must come from a real module-boundary change rather than another helper-only extraction.
  - `:feature:map-runtime` now exists and already owns runtime-facing map contracts, so the runtime-core move can proceed in bounded slices.
- In scope:
  - Dedicated boundary plan for the overlay/runtime core extraction.
  - Exact dependency pass for the `MapOverlayManagerRuntime` cluster.
  - First real extraction slice:
    - move the forecast/weather overlay runtime leaf cluster from `feature:map` to `:feature:map-runtime`
    - replace direct `MapScreenState` dependency in that cluster with a narrow shell adapter contract
- Out of scope:
  - `MapRuntimeController.kt`
  - `MapOverlayStack.kt`
  - `MapScreenRoot.kt` / `MapScreenScaffold*`
  - `MapScreenContentRuntime*`
  - `MapTaskScreenManager.kt`
  - `LocationManager.kt`
  - `SnailTrailManager.kt`
  - `MapScreenViewModel.kt` line-budget work
  - unrelated app test failures
- User-visible impact:
  - No intended behavior change.
  - Goal is ownership movement and compile-scope reduction only.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Forecast/weather overlay runtime handles (`forecastOverlay`, `forecastWindOverlay`, `skySightSatelliteOverlay`, `weatherRainOverlay`) | `MapScreenState` | narrow `ForecastWeatherOverlayRuntimeState` adapter | second runtime handle cache in `feature:map-runtime` |
| Forecast overlay runtime config | `MapOverlayManagerRuntimeForecastWeatherDelegate` | internal state + runtime warning flows | duplicate shell-owned overlay config mirrors |
| Weather-rain deferred config and cadence state | `MapOverlayManagerRuntimeForecastWeatherDelegate` | internal state | duplicate shell interaction caches |
| SkySight runtime error state | `MapOverlayManagerRuntimeForecastWeatherDelegate` | `StateFlow<String?>` | shell-owned duplicate error state |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI(shell) -> runtime/core leaf modules`

- Modules/files touched:
  - `feature:map`
  - `feature:map-runtime`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/refactor/Feature_Map_Compile_Speed_Release_Grade_Phased_Plan_2026-03-12.md`
- Any boundary risk:
  - The moved runtime leaf must not depend directly on `MapScreenState`.
  - The moved runtime leaf must not depend on shell-only overlay classes.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Forecast/weather overlay runtime delegate | `feature:map` | `:feature:map-runtime` | Already runtime-only and cohesive | module compile + manager behavior tests |
| Forecast raster runtime implementation | `feature:map` | `:feature:map-runtime` | Runtime-only MapLibre renderer | module compile + forecast warning tests |
| SkySight satellite runtime implementation | `feature:map` | `:feature:map-runtime` | Runtime-only MapLibre renderer | module compile + satellite error tests |
| Weather-rain runtime implementation | `feature:map` | `:feature:map-runtime` | Runtime-only MapLibre renderer | weather-rain regression tests |
| Forecast/weather runtime state contract | direct `MapScreenState` access | `ForecastWeatherOverlayRuntimeState` | Prevent shell back-edge from moved runtime leaf | compile + adapter tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapOverlayManagerRuntimeForecastWeatherDelegate` | direct `MapScreenState` access | `ForecastWeatherOverlayRuntimeState` adapter | Phase B |
| forecast/weather overlay runtime init/apply helpers | direct `MapScreenState` access | `ForecastWeatherOverlayRuntimeState` adapter | Phase B |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Weather-rain interaction defer/apply cadence | Monotonic | throttling deferred applies during map interaction |
| SkySight fallback reference time | Wall | remote imagery selection is UTC-wall-time based |
| Forecast/weather overlay enabled/config flags | N/A | configuration state, not time-derived |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - MapLibre runtime apply/init stays on the existing UI/runtime path
  - no new background ownership is introduced in this slice
- Primary cadence/gating sensor:
  - user interaction cadence for weather-rain defer/apply
- Hot-path latency budget:
  - preserve existing overlay interaction/runtime behavior; no additional frame-delay budget introduced

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - unchanged in this slice
  - runtime extraction must not alter existing overlay command sequencing for identical input state

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Shell/runtime reverse dependency | `ARCHITECTURE.md` dependency direction | review + compile verification | this plan + `:feature:map-runtime:compileDebugKotlin` |
| Direct shell handle coupling | `CODING_RULES.md` architecture drift / no ad hoc manager state | unit test + review | `MapOverlayManagerRuntimeForecastWeatherDelegateWeatherRainTest.kt` |
| Behavior drift in weather-rain deferred replay | `KNOWN_DEVIATIONS.md` entry 3 removal path | unit test | `MapOverlayManagerRuntimeForecastWeatherDelegateWeatherRainTest.kt` |
| Forecast/satellite runtime warning regressions | `CODING_RULES.md` regression-resistance rules | unit tests | `MapOverlayManagerForecastWarningTest.kt`, `MapOverlayManagerSkySightSatelliteErrorTest.kt` |

### 2.7 Visual UX SLO Contract (Mandatory for map/overlay/replay interaction changes)

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Forecast/weather overlays apply identically after extraction | `MS-ENG-*` impacted runtime parity only | current unit/runtime behavior | no behavior drift | manager/runtime tests + assemble | Phase B |
| Weather-rain interaction release does not replay stale frames | `MS-ENG-*` interaction/runtime parity | existing regression coverage | preserved | `MapOverlayManagerRuntimeForecastWeatherDelegateWeatherRainTest.kt` | Phase B |

## 3) Data Flow (Before -> After)

Before:

```text
feature:map shell
  -> MapOverlayManagerRuntime
    -> feature:map forecast/weather runtime delegate
      -> feature:map forecast/weather overlay runtime classes
```

After Phase B:

```text
feature:map shell
  -> MapOverlayManagerRuntime
    -> feature:map-runtime forecast/weather runtime delegate
      -> feature:map-runtime forecast/weather overlay runtime classes
```

## 4) Implementation Phases

### Phase A - Exact Boundary Lock

- Goal:
  - confirm exact payload and blockers for the overlay/runtime core move
- Files to change:
  - this plan
  - `docs/refactor/Feature_Map_Compile_Speed_Release_Grade_Phased_Plan_2026-03-12.md`
- Tests to add/update:
  - none
- Exit criteria:
  - exact file move set is documented
  - shell-owned and runtime-owned files are explicit

### Phase B - Forecast/Weather Runtime Leaf Extraction

- Goal:
  - move the forecast/weather overlay runtime leaf cluster into `:feature:map-runtime`
  - replace direct `MapScreenState` dependency with a narrow runtime-state adapter
- Status:
  - Implemented 2026-03-12
- Files to change:
  - move from `feature:map` to `:feature:map-runtime`:
    - `ForecastRasterOverlay.kt`
    - `ForecastRasterOverlayRuntime.kt`
    - `ForecastRasterOverlayRuntimeWindRenderer.kt`
    - `ForecastRasterOverlayRuntimeWindGlyphs.kt`
    - `SkySightSatelliteOverlay.kt`
    - `WeatherRainOverlay.kt`
    - `MapOverlayManagerRuntimeForecastWeatherDelegate.kt`
    - `MapOverlayManagerRuntimeForecastWeatherApply.kt`
    - `MapOverlayManagerRuntimeForecastWeatherInit.kt`
    - `MapOverlayManagerRuntimeForecastWeatherModels.kt`
    - `MapOverlayManagerRuntimeForecastWeatherSafety.kt`
    - `MapOverlayManagerRuntimeForecastWeatherStatus.kt`
  - add:
    - `ForecastWeatherOverlayRuntimeState.kt`
    - shell adapter over `MapScreenState`
    - shared map-runtime layer ID constant for blue-location anchoring
  - update:
    - `MapOverlayManagerRuntime.kt`
    - module build files
    - relevant tests
- Tests to add/update:
  - update direct delegate regression test to use the new runtime-state contract
- Exit criteria:
  - moved forecast/weather runtime leaf compiles in `:feature:map-runtime`
  - `feature:map` depends on the moved leaf only through the narrow adapter and public runtime API
  - manager behavior tests remain green aside from unrelated repo failures

### Phase C1 - Overlay Runtime Core Boundary Narrowing

- Goal:
  - narrow `MapOverlayManagerRuntime` so the real runtime owner can move without bringing shell-mixed delegates across the boundary
  - keep `MapOverlayManager.kt` as the thin shell adapter
- Exact dependency findings:
  - keep shell-owned for now:
    - `MapOverlayManagerRuntimeBaseOpsDelegate.kt`
    - `MapOverlayRuntimeStateAdapter.kt`
    - `MapOverlayRuntimeMapLifecycleDelegate.kt`
    - `MapOverlayRuntimeStatusCoordinator.kt`
    - `MapOverlayManagerRuntimeStatus.kt`
  - already leaf-owned and out of this move:
    - `feature/traffic/.../MapOverlayManagerRuntimeTrafficDelegate.kt`
    - `feature/traffic/.../MapOverlayManagerRuntimeOgnDelegate.kt`
    - `feature/traffic/.../TrafficOverlayRuntimeState.kt`
    - forecast/weather runtime leaf already moved in Phase B
- Files to change:
  - `MapOverlayManagerRuntime.kt`
  - `MapOverlayManager.kt`
  - narrow shell-facing runtime collaborator interfaces/adapters proven necessary by the seam pass
  - supporting runtime-only helpers only where they reduce direct shell-type ownership
- Tests to add/update:
  - keep shell-owned status/adapter tests in `feature:map`
  - keep `MapOverlayManager` behavior tests in `feature:map`; constructor changes should stay internal to the shell adapter
- Exit criteria:
  - `MapOverlayManagerRuntime` no longer directly depends on `MapScreenState`
  - `MapOverlayManagerRuntime` no longer constructs shell-mixed delegates internally
  - `MapOverlayManager.kt` owns shell collaborator construction while preserving its public API
  - the remaining runtime owner can be moved in one bounded follow-up slice

### Phase C2 - Overlay Manager Runtime Owner Move

- Goal:
  - move `MapOverlayManagerRuntime.kt` into `:feature:map-runtime` behind the narrowed Phase C1 boundary
  - keep `MapOverlayManager.kt` in `feature:map` as the thin shell adapter
- Status:
  - Implemented 2026-03-12
- Files to change:
  - `MapOverlayManagerRuntime.kt`
  - `MapOverlayRuntimeInteractionDelegate.kt`
  - module wiring and owner-side tests
- Tests to add/update:
  - keep `MapOverlayManager` behavior tests in `feature:map`
  - keep shell adapter/status tests in `feature:map`
  - move `MapOverlayRuntimeInteractionDelegateTest.kt` into `:feature:map-runtime`
- Exit criteria:
  - `MapOverlayManager.kt` remains a shell adapter only
  - `MapOverlayManagerRuntime.kt` compiles and tests in `:feature:map-runtime`
  - no shell/runtime back-edge is introduced
  - no stale shell-only imports remain in the moved runtime owner

### Phase C3 - Base Ops Delegate Owner Move

- Goal:
  - move `MapOverlayManagerRuntimeBaseOpsDelegate.kt` into `:feature:map-runtime` as the next real overlay/runtime owner move
  - keep `MapOverlayManager.kt` as the shell adapter and keep lifecycle/status/reporting delegates shell-owned
- Status:
  - Implemented 2026-03-12
- Exact dependency findings:
  - remove the unused `SnailTrailManager` constructor dependency/import from `MapOverlayManagerRuntimeBaseOpsDelegate.kt`
  - keep shell-owned for this slice:
    - `MapOverlayRuntimeMapLifecycleDelegate.kt`
    - `MapOverlayRuntimeStatusCoordinator.kt`
    - `MapOverlayManagerRuntimeStatus.kt`
    - `MapOverlayRuntimeStateAdapter.kt`
  - existing behavior coverage remains through shell-owned `MapOverlayManager*` tests; no dedicated delegate test currently exists
  - when moved, the delegate must use an explicit runtime-module debug source rather than implicitly relying on the shell module's `BuildConfig`
- Files to change:
  - `MapOverlayManagerRuntimeBaseOpsDelegate.kt`
  - `MapOverlayManager.kt`
  - module imports/wiring as needed for the moved delegate
- Tests to add/update:
  - keep existing `MapOverlayManager*` behavior tests in `feature:map`
  - only add direct delegate coverage if the move introduces new runtime-only logic not already covered through the shell adapter
- Exit criteria:
  - `MapOverlayManagerRuntimeBaseOpsDelegate.kt` compiles from `:feature:map-runtime`
  - `MapOverlayManager.kt` still owns shell construction and public behavior
  - no trail/runtime back-edge is introduced
  - no implicit shell `BuildConfig` dependency remains in the moved delegate

### Phase D - Remaining Runtime/Trail Cleanup

- Goal:
  - reassess whether trail/runtime and lifecycle support should move in follow-up slices
- Files to change:
  - `SnailTrailManager.kt`
  - `SnailTrailOverlay.kt`
  - remaining runtime-only lifecycle helpers if still justified by measurements
- Tests to add/update:
  - trail/runtime tests as needed
- Exit criteria:
  - only proceed if fresh measurements justify another move

## 5) Test Plan

- Unit tests:
  - `MapOverlayManagerRuntimeForecastWeatherDelegateWeatherRainTest.kt`
  - `MapOverlayManagerForecastWarningTest.kt`
  - `MapOverlayManagerSkySightSatelliteErrorTest.kt`
  - `MapOverlayManagerWeatherRainTest.kt`
- Replay/regression tests:
  - none added in this slice; replay behavior should remain unchanged
- UI/instrumentation tests (if needed):
  - none for Phase B
- Degraded/failure-mode tests:
  - runtime warning/error tests stay in scope
- Boundary tests for removed bypasses:
  - delegate test updated to exercise the new runtime-state adapter contract indirectly

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Moved runtime leaf still depends on shell-owned types | High | use a narrow runtime-state contract instead of direct `MapScreenState` access | Codex |
| Blue-location anchor constant creates a hidden shell dependency | Medium | extract a shared map-runtime layer ID constant and reuse it from the shell | Codex |
| Forecast/weather behavior drifts during extraction | High | keep manager API unchanged and retain regression tests before/after move | Codex |
| Compile gain is narrower than expected | Medium | keep later phases gated on fresh measurements; stop if payoff is speculative | Codex |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling remains explicit
- Replay behavior remains deterministic
- Forecast/weather overlay runtime behavior remains unchanged under existing tests
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

## 8) Rollback Plan

- What can be reverted independently:
  - Phase B forecast/weather runtime leaf move
  - dedicated runtime-state adapter contract
- Recovery steps if regression is detected:
  - revert the Phase B move set as one slice
  - restore moved files to `feature:map`
  - rerun `:feature:map:compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug`
