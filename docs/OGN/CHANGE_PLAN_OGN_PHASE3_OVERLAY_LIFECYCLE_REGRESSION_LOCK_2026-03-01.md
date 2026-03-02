# CHANGE_PLAN_OGN_PHASE3_OVERLAY_LIFECYCLE_REGRESSION_LOCK_2026-03-01.md

## Purpose

Define the Phase 3 implementation plan to close the remaining OGN map-runtime
gaps after render-init fast-path rollout:
- lifecycle regression lock coverage
- detach/scheduled-render cancellation coverage
- optional style-loss self-heal hardening

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN Phase 3 overlay lifecycle regression lock
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Implemented (core Phases 1/2/3 complete; Phase 4 optional and deferred)
- Parent plan:
  - `docs/OGN/CHANGE_PLAN_OGN_REMAINING_GAPS_2026-03-01.md`

## 0A) Implementation Update (2026-03-01)

Implemented now:
- Added a minimal injectable overlay-factory seam in:
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - preserves default runtime behavior; enables deterministic lifecycle unit tests.
- Added focused Phase 3 regression lock tests:
  - `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerOgnLifecycleTest.kt`
  - covers:
    - render-path one-time initialization for created traffic overlay
    - style-recreate lifecycle (`cleanup` old + `initialize` new + render cached data)
    - deferred render cancellation on `onMapDetached()`

Verification run:
- `python scripts/arch_gate.py` passed
- `./gradlew enforceRules` passed
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapOverlayManagerOgnLifecycleTest"` passed
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapOverlayManagerOgnLifecycleTest" --tests "com.example.xcpro.map.ui.MapRuntimeControllerWeatherStyleTest" --tests "com.example.xcpro.map.OgnGliderTrailOverlayRenderPolicyTest"` passed
- `./gradlew assembleDebug` passed

Note:
- `./gradlew testDebugUnitTest` currently fails in unrelated app-slice test:
  - `ProfileRepositoryTest > ioReadError_preservesLastKnownGoodState`
  - outside this Phase 3 OGN change scope.

## 1) Code Pass Findings (2026-03-01)

1. Dedicated Phase 3 lifecycle tests are still missing.
- Evidence:
  - Existing OGN map tests include formatter/policy and trail trim tests but no
    manager lifecycle lock test:
    - `feature/map/src/test/java/com/example/xcpro/map/OgnGliderTrailOverlayRenderPolicyTest.kt`
    - no `MapOverlayManagerOgnLifecycleTest` currently present.
- Impact:
  - Future refactors can accidentally reintroduce init-on-render or break style
    recreate behavior without fast feedback.

2. Pending render cancellation on detach is implemented but not directly tested.
- Evidence:
  - `MapOverlayManager.onMapDetached()` cancels pending OGN jobs:
    `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`.
  - No focused test currently asserts deferred render jobs do not execute after
    detach.
- Impact:
  - A regression here can cause stale/burst rendering after map teardown.

3. Style-loss self-heal fallback is not explicitly implemented in OGN overlays.
- Evidence:
  - `render()` returns early when style/source is missing in:
    - `OgnTrafficOverlay.kt`
    - `OgnThermalOverlay.kt`
    - `OgnGliderTrailOverlay.kt`
  - Recovery currently depends on style lifecycle hooks (`initializeTrafficOverlays`)
    in `MapOverlayManager`.
- Impact:
  - If style/source/layer is dropped unexpectedly outside normal style-change
    callbacks, overlay can remain blank until next lifecycle reinit path.

4. MapOverlayManager OGN lifecycle behavior is hard to unit-test at fine granularity.
- Evidence:
  - OGN overlay construction is concrete/private inside `MapOverlayManager`
    (`createOgnTrafficOverlay`, `createOgnThermalOverlay`, `createOgnGliderTrailOverlay`).
- Impact:
  - Verifying initialize/render call contracts around style epochs requires more
    brittle integration-style setup than necessary.

## 2) Scope

- In scope:
  - add focused Phase 3 lifecycle tests for manager-owned OGN overlay init model
  - add detach cancellation regression lock tests for throttled renders
  - optionally add bounded overlay self-heal fallback for unexpected style/source loss
  - sync OGN docs with final lifecycle contract
- Out of scope:
  - repository/source-time ordering logic
  - DDB metadata scheduling logic
  - non-OGN overlay redesign

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN overlay runtime instances | `MapScreenState`/`MapOverlayManager` | UI runtime fields | ViewModel state mirrors |
| OGN render cadence state | `MapOverlayManager` | internal `OgnRenderThrottleState` | overlay-local timers |
| style/source/layer recovery state (if added) | overlay runtime object | internal cooldown markers | global singleton recovery state |

### 3.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

All planned changes stay in map UI runtime/tests.

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| OGN render throttle (`lastRenderMonoMs`) | Monotonic | stable cadence and delay math |
| deferred render job delay | Monotonic | cancellation-safe elapsed timing |
| style epoch identity | logical token | lifecycle ownership, not elapsed time |

No wall/monotonic cross-subtraction introduced.

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - add failing/expectation tests that reproduce current lifecycle blind spots.
- Files:
  - new tests under `feature/map/src/test/java/com/example/xcpro/map/`
- Exit:
  - failing/pending expectations prove current coverage gaps.

### Phase 1 - Testability seam (minimal)

- Goal:
  - make `MapOverlayManager` OGN lifecycle testable without changing runtime behavior.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
- Change:
  - add small injectable overlay-factory seam (constructor defaults keep current behavior).
- Tests:
  - verify factory-produced overlays preserve current initialize/render sequence.
- Exit:
  - no behavior change; test seam available.

### Phase 2 - Lifecycle regression lock tests

- Goal:
  - lock manager-owned fast-path behavior.
- Files:
  - new `MapOverlayManagerOgnLifecycleTest.kt`
- Tests:
  - repeated `updateOgn*` with existing overlays does not call `initialize()`
  - style recreate path performs cleanup and one init pass per overlay
  - style recreate still renders latest cached OGN payload
- Exit:
  - init-on-render regression becomes test-detectable.

### Phase 3 - Detach/cancel regression lock

- Goal:
  - guarantee pending throttled renders do not run after detach.
- Files:
  - `MapOverlayManagerOgnLifecycleTest.kt` (or dedicated cancel test file)
- Tests:
  - schedule deferred OGN render (Balanced/Battery mode), call `onMapDetached()`,
    advance virtual time, assert deferred render did not execute.
- Exit:
  - teardown safety is locked by unit tests.

### Phase 4 - Optional style-loss self-heal hardening

- Goal:
  - recover from unexpected source/layer disappearance without reintroducing
    per-frame full init overhead.
- Files:
  - `OgnTrafficOverlay.kt`
  - `OgnThermalOverlay.kt`
  - `OgnGliderTrailOverlay.kt`
- Change:
  - add bounded, cooldown-guarded reinit attempt when required source/layer is missing.
- Tests:
  - missing source/layer triggers one bounded recovery attempt
  - repeated missing state does not create hot-loop reinit spam
- Exit:
  - robust fail-open behavior with bounded cost.

### Phase 5 - Verification and docs sync

- Goal:
  - run full required gates and align OGN docs.
- Files:
  - `docs/OGN/CHANGE_PLAN_OGN_REMAINING_GAPS_2026-03-01.md`
  - `docs/OGN/OGN.md` (if runtime contract wording changes)
  - `docs/OGN/README.md`
- Required checks:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Focused reruns:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.*Ogn*"`
- Exit:
  - docs and tests reflect final Phase 3 contract.

## 5) Acceptance Gates

- No architecture-rule violations.
- OGN render path remains fast-path (no unconditional init in render).
- Style lifecycle behavior remains correct after map style changes.
- Pending throttled renders are canceled safely on detach.
- Optional self-heal path, if implemented, is bounded and test-covered.

## 6) Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| test seam introduces behavioral drift | medium | keep defaults identical; verify no-op diff via focused tests |
| lifecycle tests become brittle against MapLibre internals | medium | assert manager policy contracts, not SDK internals |
| self-heal fallback reintroduces hot-path cost | low/medium | cooldown guard + one-attempt-per-episode policy |

## 7) Rollback Plan

1. Revert Phase 4 self-heal logic independently.
2. Revert Phase 1 seam if unnecessary after tests land.
3. Keep lifecycle tests when possible to preserve regression detection.
4. Re-run required checks before merge.
