# Forecast/Weather Runtime Seam Extraction Plan

## 0) Metadata

- Title: Narrow forecast/weather runtime ownership extraction
- Owner: XCPro Team
- Date: 2026-03-14
- Issue/PR: TBD
- Status: Implemented and verified

## 1) Scope

- Problem statement:
  - `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt`
    still owns three separate runtime state machines:
    - weather rain
    - SkySight satellite
    - forecast primary/wind raster
  - the mixed delegate also owns cross-cutting interaction-release behavior,
    warning/error flows, and status assembly, which weakens reviewability and
    historically kept the weather-rain deferred replay risk on a broad mixed
    owner.
- Why now:
  - this is the next highest-value ownership seam after the MapScreen shell and
    profile/card boundary closure
  - weather rain was the highest-risk teardown/interaction path at seam lock
    time and required the first runtime extraction
- In scope:
  - keep `MapOverlayManagerRuntime` as the shell/runtime coordinator
  - extract leaf runtime owners in this order:
    1. weather rain
    2. SkySight satellite
    3. forecast primary/wind raster
  - add regression tests for:
    - real detach ordering
    - style-change reapply
    - interaction-release during teardown
- Out of scope:
  - forecast/weather feature business logic changes
  - map UI / ViewModel changes
  - provider/network/auth changes
  - repo-wide runtime ownership cleanup
- User-visible impact:
  - none intended
  - behavior parity required except for fixing the prior weather-rain deferred
    replay risk
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| weather-rain runtime apply state | weather-rain runtime delegate | runtime owner methods + status snapshot | broad mixed forecast/weather runtime mutable fields |
| SkySight satellite runtime apply state | SkySight satellite runtime delegate | runtime owner methods + error flow + status snapshot | broad mixed forecast/weather runtime mutable fields |
| forecast raster runtime apply state | forecast raster runtime delegate | runtime owner methods + warning flow + status snapshot | broad mixed forecast/weather runtime mutable fields |
| forecast/weather shell coordination | `MapOverlayManagerRuntime` | thin forwarding methods | leaf runtime delegates mutating each other directly |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| weather-rain latest config | weather-rain runtime delegate | `setWeatherRainOverlay`, `clearWeatherRainOverlay`, `onMapDetached`, style/init reapply | `statusSnapshot()` and runtime render path | weather overlay effect inputs | none | clear, detach, map recreation | monotonic for apply throttling | deferred replay, detach, style reapply |
| weather-rain deferred config | weather-rain runtime delegate | interaction release + detach path only | internal only | latest config + interaction cadence policy | none | successful apply, disable, detach | monotonic | deferred replay regression |
| SkySight runtime config | satellite runtime delegate | satellite set/clear/reapply/detach | error flow + status snapshot | forecast overlay effect inputs | none | clear, detach, map recreation | none for config; wall UTC reference passed through only | error retry, detach, style reapply |
| forecast raster config | forecast raster runtime delegate | forecast set/clear/reapply/detach | warning flow + status snapshot | forecast overlay effect inputs | none | clear, detach, map recreation | none | warning parity, detach, style reapply |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> feature state/viewmodels -> map shell runtime -> leaf runtime delegates -> overlay handles`

- Modules/files touched:
  - `feature:map-runtime`
  - `feature:map` tests and shell wrapper only as needed for runtime regression coverage
  - `docs/refactor`
- Boundary risk:
  - detach ordering between `MapOverlayRuntimeInteractionDelegate`,
    `MapOverlayManagerRuntime.onMapDetached()`, and shell cleanup
  - verify the weather-rain cadence policy stays co-located with the runtime owner and does not drift back into traffic-owned helpers

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt` | leaf runtime owner under a thin shell coordinator | delegate owns one runtime slice, keeps local mutable state private, exposes narrow intent/status methods | forecast/weather leaf delegates remain in `feature:map-runtime` first, not owner-module moves |
| `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt` | interaction-aware deferred runtime rendering with explicit flush path | private mutable runtime state, focused `setMapInteractionActive`, `onMapDetached`, and targeted render helpers | forecast/weather has warning/error flows and map-style reapply that need separate leaf APIs |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| weather-rain runtime state machine | mixed forecast/weather delegate | dedicated weather-rain runtime delegate | highest teardown risk and the former deferred replay hotspot | focused delegate tests + manager detach ordering test |
| SkySight satellite runtime state machine | mixed forecast/weather delegate | dedicated satellite runtime delegate | isolate error + contrast-icon side effects | satellite error and style tests |
| forecast raster runtime state machine | mixed forecast/weather delegate | dedicated forecast raster runtime delegate | isolate warning aggregation and wind-arrow lookup | forecast warning tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapOverlayManagerRuntimeForecastWeatherDelegate` rain fields and methods | rain mutable state sits inside mixed delegate | weather-rain leaf delegate | Phase 1 |
| `MapOverlayManagerRuntimeForecastWeatherDelegate` satellite fields and methods | satellite mutable state sits inside mixed delegate | satellite leaf delegate | Phase 2 |
| `MapOverlayManagerRuntimeForecastWeatherDelegate` forecast raster fields and methods | forecast mutable state sits inside mixed delegate | forecast raster leaf delegate | Phase 3 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Forecast_Weather_Runtime_Seam_Extraction_Plan_2026-03-14.md` | New | seam plan and SSOT contract | required non-trivial refactor plan | production code must not carry execution plan | no |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeWeatherRainDelegate.kt` | New | weather-rain runtime owner | first high-risk leaf seam | `MapOverlayManagerRuntimeForecastWeatherDelegate.kt` is too mixed | yes, Phase 1 split |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapWeatherRainInteractionCadencePolicy.kt` | New | weather-rain interaction cadence policy | keeps rain throttling and transition overrides with the rain runtime owner | traffic-owned cadence helpers should not own weather behavior | no |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeSkySightSatelliteDelegate.kt` | New | SkySight satellite runtime owner | isolates satellite config, contrast-icon side effects, and error flow | coordinator should not own satellite state machine | yes, Phase 2 split |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastRasterDelegate.kt` | New | forecast primary/wind runtime owner | isolates forecast config, warning flow, and wind-arrow lookup | coordinator should not own forecast raster state machine | yes, Phase 3 split |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt` | Existing | thin coordinator across leaf delegates | preserve shell-facing ABI while shrinking mixed owner | `MapOverlayManagerRuntime.kt` should stay a shell coordinator, not re-absorb leaf logic | yes |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt` | Existing | shell coordinator and leaf wiring | existing runtime composition root | UI/ViewModel must not own runtime delegate construction | no |
| `feature/map-runtime/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeSkySightSatelliteDelegateTest.kt` | New | focused satellite style-reapply regression | keeps satellite extraction behavior locked where implementation lives | manager-only tests would hide the leaf seam | no |
| `feature/map-runtime/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastRasterDelegateTest.kt` | New | focused forecast style-reapply regression | keeps forecast extraction behavior locked where implementation lives | manager-only tests would hide the leaf seam | no |
| `feature/map-runtime/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeDetachOrderingTest.kt` | New | runtime detach-order regression | locks teardown risk in the runtime owner module | weather-only tests would miss the real manager detach path | no |
| `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegateWeatherRainTest.kt` | Existing | weather-rain runtime regression coverage | existing focused rain runtime test seam | unrelated broader suites would obscure the regression | no |
| `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerWeatherRainTest.kt` | Existing | shell/manager parity coverage | existing wrapper regression seam | UI tests are too far from runtime ordering | no |
| `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerDetachOrderingTest.kt` | New | real detach ordering regression | locks teardown risk found in review | existing rain tests only covered manual null-map flush | yes, new focused test |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `MapOverlayManagerRuntimeWeatherRainDelegate` | `feature:map-runtime` | `MapOverlayManagerRuntimeForecastWeatherDelegate` | `internal` | focused weather-rain runtime owner | stable internal seam; no planned public exposure |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| weather-rain interaction apply throttling | Monotonic | runtime apply cadence must stay interaction-safe and replay-neutral |
| satellite reference time UTC | Wall | leaf passes selected forecast time through to map rendering only |
| detach ordering tests | Monotonic test clocks | needed to prove deferred-flush behavior deterministically |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none introduced
  - weather-rain interaction throttling remains monotonic runtime behavior only

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| detach release flush re-renders stale rain during teardown | `ARCHITECTURE.md` state/lifecycle ownership | unit test + review | `MapOverlayManagerDetachOrderingTest.kt` |
| deferred older rain frame replays after newer apply | closed deviation `RULES-20260306-13` remains locked by regression coverage | unit test | `MapOverlayManagerRuntimeForecastWeatherDelegateWeatherRainTest.kt` |
| style change loses latest rain config | map overlay runtime correctness | unit test | `MapOverlayManagerWeatherRainTest.kt` |
| mixed delegate remains oversized and multi-owner | file ownership rules and line budget | review + `enforceRules` | runtime delegate files |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| weather-rain interaction release does not replay stale frame or disabled state | `MS-UX-04` | pre-extraction regression risk | zero stale replay in runtime tests | rain delegate + detach ordering tests | Phase 1 |
| style change keeps latest weather-rain render config | `MS-ENG-01` | current runtime apply parity | reapply preserves latest config | manager runtime tests | Phase 1 |

## 3) Data Flow (Before -> After)

Before:

`weather/future forecast effects -> MapOverlayManagerRuntimeForecastWeatherDelegate -> overlay handles`

After Phase 1:

`weather effects -> MapOverlayManagerRuntimeForecastWeatherDelegate -> MapOverlayManagerRuntimeWeatherRainDelegate -> WeatherRainOverlay`

After full seam:

`forecast/weather effects -> thin forecast/weather coordinator -> focused rain / satellite / forecast delegates -> overlay handles`

## 4) Implementation Phases

### Phase 0 - Plan and seam lock

- Goal:
  - lock the forecast/weather runtime seam and phase order
- Files to change:
  - this plan doc only
- Tests to add/update:
  - none
- Exit criteria:
  - file ownership and phased order are explicit

### Phase 1 - Weather rain extraction

- Goal:
  - extract the weather-rain state machine from the mixed delegate
  - fix detach-order interaction release risk
- Files to change:
  - `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeWeatherRainDelegate.kt`
  - `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt`
  - `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`
  - focused tests
- Tests to add/update:
  - existing weather-rain delegate regression suite
  - manager detach-order regression
  - manager style-change reapply regression if needed
- Exit criteria:
  - mixed delegate no longer owns weather-rain mutable state
  - detach path does not flush stale rain on interaction release
  - required verification passes
- Status:
  - complete in production code
  - required verification passed on 2026-03-14

### Phase 2 - SkySight satellite extraction

- Goal:
  - isolate SkySight satellite runtime config, reapply, contrast-icon callback, and runtime error flow
- Exit criteria:
  - mixed delegate no longer owns satellite mutable state
  - error/retry behavior remains covered
- Status:
  - complete in production code
  - focused style-reapply regression added in `feature:map-runtime` and verified on 2026-03-14

### Phase 3 - Forecast raster extraction

- Goal:
  - isolate forecast primary/wind raster config, reapply, warning flow, and wind-arrow lookup
- Exit criteria:
  - mixed delegate becomes a thin coordinator only
  - warning parity and wind-arrow lookup remain covered
- Status:
  - complete in production code
  - focused style-reapply regression added in `feature:map-runtime` and verified on 2026-03-14

## 5) Test Plan

- Unit tests:
  - weather-rain deferred replay parity
  - detach ordering
  - style-change reapply
- Replay/regression tests:
  - none replay-specific beyond deterministic monotonic cadence tests
- UI/instrumentation tests (if needed):
  - none for Phase 1
- Degraded/failure-mode tests:
  - weather-rain disable during interaction
  - null-map flush during detach
- Boundary tests for removed bypasses:
  - weather-rain leaf delegate direct tests
  - shell/manager wrapper ordering tests

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| detach sequencing still allows interaction-release reapply | stale overlay apply during teardown | add manager detach-order regression before closing Phase 1 | XCPro Team |
| refactor hides mixed ownership behind a renamed facade | no real seam gain | keep leaf delegates stateful and make coordinator thin only | XCPro Team |
| style/init paths drift while splitting leaves | overlay parity regressions | preserve existing apply/init helpers and add style reapply tests | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file:
- Decision summary:
  - this is a narrow runtime seam extraction within an existing runtime boundary
- Why this belongs in an ADR instead of plan notes:
  - no durable module/API boundary decision is being introduced in Phase 1

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- For impacted map/overlay behavior, focused SLO-relevant regression coverage exists
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

## 8) Rollback Plan

- What can be reverted independently:
  - weather-rain delegate extraction
  - focused tests
- Recovery steps if regression is detected:
  - revert Phase 1 leaf extraction
  - keep the new tests if they still reproduce the pre-existing issue
