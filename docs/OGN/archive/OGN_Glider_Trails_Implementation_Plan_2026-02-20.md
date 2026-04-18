# OGN Glider Trails Implementation Plan (2026-02-20)

## 0) Metadata

- Title: OGN multi-glider snail trails with sink/climb color and thickness encoding
- Owner: XCPro Team
- Date: 2026-02-20
- Issue/PR: local integration pass (working tree)
- Status: Implemented (v1) with deep-pass updates

## 1) Scope

- Problem statement:
  OGN currently shows glider icons and thermal hotspots, but not per-glider path history with pilot-readable sink/climb encoding.
- Why now:
  Pilots need immediate tactical readability of where gliders found sink versus strong climb.
- In scope:
  - Track all OGN gliders in current OGN receive area.
  - Render per-glider line trails on map.
  - Encode sink/climb by both color and width:
    - Stronger sink = darker navy and thinner line.
    - Stronger climb = thicker line and color from yellow toward dark purple.
  - Add user control to show/hide OGN trails.
  - Keep implementation fully compliant with MVVM + UDF + SSOT + injected time rules.
- Out of scope:
  - Changing OGN network protocol parsing shape beyond needed trail inputs.
  - Replay redesign.
  - Cross-session persistence of trails to disk.
  - Collision-avoidance semantics.
- User-visible impact:
  Pilots can visually compare weak/strong sink and climb among all tracked OGN gliders.

## 2) Architecture Contract

This plan must comply with:

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
- `docs/ARCHITECTURE/CONTRIBUTING.md`

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Raw OGN targets | `OgnTrafficRepository` | `StateFlow<List<OgnTrafficTarget>>` | Trail logic in UI/VM |
| OGN trail visibility preference | `OgnTrafficPreferencesRepository` | `Flow<Boolean>` | Composable-local toggle authority |
| OGN trail length preference (if enabled) | `OgnTrafficPreferencesRepository` | `Flow<Int>` | VM hardcoded mirror as SSOT |
| Derived OGN trail segments | `OgnGliderTrailRepository` (new) | `StateFlow<List<OgnGliderTrailSegment>>` | Map runtime owning business state |
| Runtime map layers for OGN trails | `OgnGliderTrailOverlay` via `MapOverlayManager` | runtime only | Repository or VM storing map layer handles |
| Selected details states (OGN/thermal/ADS-B) | `MapScreenViewModel` | existing state flows | Overlay-owned selection state |

### 2.2 Dependency Direction

Required direction remains:

`UI -> domain/use-case -> data`

Planned touched areas:

- `feature/map/src/main/java/com/trust3/xcpro/ogn/*`
- `feature/map/src/main/java/com/trust3/xcpro/map/*`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/*`
- `docs/OGN/*` and `docs/ARCHITECTURE/PIPELINE.md`

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| OGN per-glider trail derivation | none | `OgnGliderTrailRepository` | Keep business math out of UI | new unit tests |
| OGN trail render lifecycle | none | `MapOverlayManager` + `OgnGliderTrailOverlay` | Keep runtime map state in map runtime layer | overlay integration tests/manual QA |
| OGN trail preference persistence | none | `OgnTrafficPreferencesRepository` | SSOT preference ownership | preference tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Composable trail math | Potential risk | No UI math; all mapping in repository/domain helper | Phase 1 |
| Runtime overlay computes vario style | Potential risk | Overlay reads precomputed color/width from segment model | Phase 3 |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| OGN sample freshness (`lastSeenMillis`) | Monotonic | Stable sequencing and stale handling |
| Segment timestamp and pruning windows | Monotonic | Deterministic trail lifecycle |
| Preference timestamps (if any UI label) | Wall | UI only |

Forbidden:

- Monotonic vs wall subtraction in domain/repository logic.
- Any direct `System.currentTimeMillis`, `Date()`, `Instant.now()` in domain/fusion paths.

### 2.4 Threading and Cadence

- Repository derivation and pruning: `Dispatchers.Default`.
- DataStore preference writes: repository-managed IO path.
- Map rendering and layer updates: main/UI runtime.
- No per-frame business recomputation. Update only on fresh OGN sample batches and controlled housekeeping cadence.

### 2.5 Replay Determinism

- This feature is for live OGN pathing.
- Replay behavior is unchanged in this increment.
- No randomness introduced.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Business math leaks into UI | CODING_RULES ViewModel/UI rules | unit + review | `OgnGliderTrailRepositoryTest.kt` |
| Hidden singleton state | ARCHITECTURE DI rules | review + DI wiring tests | Hilt module and constructors |
| Non-deterministic time use | ARCHITECTURE Timebase | enforceRules + unit tests | repository tests with fake clock |
| Undocumented thresholds | Forbidden pattern #4 | code comments + plan constants table | new trail constants block |

## 3) Data Flow (Before -> After)

Before:

`OgnTrafficRepository.targets -> MapScreenViewModel -> OgnTrafficOverlay (icons only)`

After:

`OgnTrafficRepository.targets -> OgnGliderTrailRepository -> OgnTrafficUseCase -> MapScreenViewModel -> MapRootEffects -> MapOverlayManager -> OgnGliderTrailOverlay`

Preference gate:

`OgnTrafficPreferencesRepository.showGliderTrailsEnabledFlow -> OgnTrafficUseCase -> MapScreenViewModel -> UI/overlay gating`

## 4) Visual and Math Contract

## 4.1 Color Contract

Use existing 19-step vario palette already present in XCPro snail/thermal logic:

- Strong sink: deep navy.
- Near zero: yellow.
- Strong climb: dark purple.

References:

- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailPalette.kt`
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnThermalColorScale.kt`

## 4.2 Width Contract (New for OGN trails)

Constants:

- `MAX_ABS_VARIO_MPS = 12kt * 0.514444 = 6.173328`
- `ZERO_WIDTH_PX = 2.0`
- `SINK_MIN_WIDTH_PX = 0.8`
- `CLIMB_MAX_WIDTH_PX = 7.5`

Mapping:

- `v = clamp(varioMps, -MAX_ABS_VARIO_MPS, MAX_ABS_VARIO_MPS)`
- If `v <= 0`:
  - `t = abs(v) / MAX_ABS_VARIO_MPS`
  - `widthPx = lerp(ZERO_WIDTH_PX, SINK_MIN_WIDTH_PX, t)`
- Else:
  - `t = v / MAX_ABS_VARIO_MPS`
  - `widthPx = lerp(ZERO_WIDTH_PX, CLIMB_MAX_WIDTH_PX, t)`

Result:

- More negative vario -> thinner line.
- More positive vario -> thicker line.

## 4.3 Segment Inclusion Rules

Only emit segment if:

1. Target sample is fresh (`lastSeenMillis` strictly increases per target).
2. Coordinates are valid.
3. Segment distance >= minimum threshold (anti-noise, zoom-aware or fixed baseline).
4. Vario value is finite.

## 5) Implementation Phases

## Phase 0 - Baseline and contracts

- Goal:
  Lock current behavior and define constants/owners before code.
- Files:
  - New docs in `docs/OGN/*` (this plan + research).
- Tests:
  - Confirm existing OGN and thermal tests remain green.
- Exit:
  - Plan approved.

## Phase 1 - Pure logic and models

- Goal:
  Implement OGN trail domain math and state machine outside UI.
- Planned files:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailModels.kt` (new)
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailMath.kt` (new)
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailRepository.kt` (new)
- Tests:
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnGliderTrailMathTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnGliderTrailRepositoryTest.kt`
- Exit:
  - Deterministic unit tests for color index and width mapping.
  - Fresh-sample dedup and pruning validated.

## Phase 2 - SSOT and use-case wiring

- Goal:
  Expose trails and preferences through existing OGN use-case path.
- Planned files:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt`
  - OGN DI module files.
- Tests:
  - Extend `OgnTrafficPreferencesRepositoryTest.kt` for new keys.
  - ViewModel wiring tests in `MapScreenViewModelTest.kt`.
- Exit:
  - New preference and segment flows available from `MapScreenViewModel`.

## Phase 3 - Map runtime overlay and UI controls

- Goal:
  Render OGN glider trails and provide user toggle.
- Planned files:
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnGliderTrailOverlay.kt` (new)
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenState.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/components/OgnGliderTrailsButton.kt` (new)
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
- Tests:
  - UI/VM wiring assertions for toggle and gating.
- Exit:
  - Trails draw only when OGN overlay and OGN trails toggle are enabled.
  - No business math in UI layer.

## Phase 4 - Hardening and compliance

- Goal:
  Validate performance, edge cases, and architecture guards.
- Planned work:
  - Segment cap and pruning checks under dense traffic.
  - Style-reload reapply behavior.
  - Documentation sync (`docs/ARCHITECTURE/PIPELINE.md`, `docs/OGN/OGN.md`).
- Exit:
  - Required gradle checks pass.
  - Manual map QA complete.

## 6) Test Plan

- Unit tests:
  - Width mapping monotonicity (sink side decreases; climb side increases).
  - Color index clamping and zero-lift midpoint behavior.
  - Fresh sample gating by monotonic time.
  - Retention pruning and global cap behavior.
- Integration tests:
  - Flow path from repository -> use-case -> VM -> overlay manager.
- UI/manual tests:
  - Toggle on/off behavior.
  - Visual check with synthetic strong sink and strong climb samples.
  - Style change preserves trail layer functionality.
- Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when environment is available:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Too many segments in busy areas | Frame drops | Per-target and global segment caps, distance thinning, retention window | XCPro Team |
| Vario noise causes visual flicker | Poor readability | Minimum segment distance/time and optional style smoothing in repository | XCPro Team |
| Layer order conflicts with OGN icons/thermals | Tap/readability regressions | Explicit layer insertion tests and style-reload verification | XCPro Team |
| User confusion between thermal spots and trails | Misinterpretation | Separate toggle and short inline help text | XCPro Team |

## 8) Acceptance Gates

1. All OGN gliders in active OGN receive area are trail-tracked while OGN is enabled.
2. Sink/climb line encoding matches requested rule:
   - stronger sink -> thinner and darker navy
   - stronger climb -> thicker and color toward dark purple
3. No business math in UI/Compose/ViewModel convenience paths.
4. No global mutable singleton state introduced.
5. All thresholds/constants are centralized and documented.
6. No non-deterministic time calls in deterministic domain paths.

## 9) Rollback Plan

- Revert `OgnGliderTrailOverlay` wiring first (UI/runtime only rollback).
- Keep repository behind a disabled preference until stabilized.
- If needed, disable only trail rendering while keeping OGN markers/thermals unchanged.

## 10) Implemented Product Decisions

1. OGN trails default is `OFF` on first release.
2. Default trail history window is `20 minutes`.
3. A dedicated map FAB (`TR`) is implemented now.
4. Sink is implemented as solid thin lines (no dotted sink in v1).

## 10.1 Deep-Pass Misses Found and Fixed

1. Miss:
   Trail-sample pruning used active-target membership, which could leave stale samples alive when upstream snapshots stopped changing, causing housekeeping reschedule risk in deterministic tests.
   Fix:
   Sample retention now prunes by sample age (`nowMonoMs - sourceSeenMonoMs`) regardless of active-id membership.
   Code:
   `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnGliderTrailRepository.kt`.

2. Miss:
   Preference test default assumption was brittle when DataStore state leaked between tests.
   Fix:
   Test setup now explicitly resets `showGliderTrailsEnabled` to `false`.
   Code:
   `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`.

3. Miss:
   Initial plan did not explicitly call out runtime layer ownership for OGN trail overlay in lifecycle cleanup.
   Fix:
   Runtime cleanup now includes `ognGliderTrailOverlay` in map lifecycle teardown.
   Code:
   `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt`.

4. Miss:
   Repository tests validated retention pruning when fresh input arrived, but did not validate timer-driven housekeeping when upstream target lists stayed quiet.
   Fix:
   Added explicit housekeeping test that advances virtual time and injected monotonic clock to prove deterministic segment expiry without new upstream emissions.
   Code:
   `feature/map/src/test/java/com/trust3/xcpro/ogn/OgnGliderTrailRepositoryTest.kt` (`historyPrunesViaHousekeepingWhenUpstreamIsQuiet`).


## 11) Compliance Check Against Forbidden Patterns

1. Do not substitute unrelated signals:
   - Use only OGN target vertical speed for trail styling.
2. Do not push business math into UI:
   - Color and width mapping stays in repository/domain helpers.
3. Do not add global mutable singletons:
   - Repository and overlay are DI-managed instances.
4. Do not use scattered undocumented thresholds:
   - Single constants block in OGN trail math/repository.
5. Do not use non-deterministic time sources:
   - Injected clock and monotonic sample sequencing only.
