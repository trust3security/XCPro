# CHANGE_PLAN_OGN_OVERLAY_RENDER_INIT_FASTPATH_2026-03-01.md

## Purpose

Define a detailed, release-grade phased plan to remove repeated OGN overlay
initialization work from the render hot path while preserving current behavior.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: OGN overlay render-init fast-path hardening
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Implemented (core scope complete)
- Parent plan:
  - `docs/OGN/CHANGE_PLAN_OGN_QUALITY_HARDENING_2026-03-01.md` (Phase 3)

## 0A) Implementation Update (2026-03-01)

Implemented now:
- Removed unconditional `initialize()` calls from:
  - `OgnTrafficOverlay.render()`
  - `OgnThermalOverlay.render()`
  - `OgnGliderTrailOverlay.render()`
- Moved one-time runtime initialization ownership to `MapOverlayManager` when
  OGN overlay instances are created in render paths.
- Preserved existing style lifecycle behavior in `initializeTrafficOverlays(...)`
  and style-change handling.

Verification:
- targeted OGN repository regression tests passed:
  - `OgnTrafficRepositoryConnectionTest`
  - `OgnTrafficRepositoryPolicyTest`
- `python scripts/arch_gate.py` passed.
- `./gradlew enforceRules` passed.
- `./gradlew testDebugUnitTest` passed.
- `./gradlew testDebugUnitTest --rerun-tasks` passed.
- `./gradlew assembleDebug` passed.

## 1) Scope

- Problem statement:
  - `OgnTrafficOverlay.render()`, `OgnThermalOverlay.render()`, and
    `OgnGliderTrailOverlay.render()` currently call `initialize()` every render.
  - This repeatedly executes style lookups and "exists?" checks on hot paths.
- Why now:
  - OGN correctness/reliability is release-ready.
  - This plan records the completed performance hardening and verification evidence.
- In scope:
  - OGN overlay lifecycle contract so initialization is style-epoch scoped, not
    render scoped.
  - Map overlay manager wiring to own initialization timing.
  - Safety fallback for unexpected style/source/layer loss.
  - Unit tests for lifecycle and render behavior invariants.
- Out of scope:
  - OGN traffic policy, parser, DDB behavior, or any SSOT data semantics.
  - ADS-B and forecast overlay refactors (unless tiny shared helper extraction).
- User-visible impact:
  - Lower UI-thread overhead during OGN-heavy map sessions.
  - No behavioral change in marker/thermal/trail output.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| OGN overlay style-epoch init state | `MapOverlayManager` + each OGN overlay runtime object | internal runtime fields | ViewModel or repository init flags |
| OGN overlay render targets | `MapOverlayManager` runtime cache | internal lists (`latestOgn*`) | overlay-local mirrored target owners |
| Safety fallback re-init throttle state | OGN overlay runtime object | internal monotonic fields | global static fallback state |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnThermalOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/OgnGliderTrailOverlay.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
  - map tests under `feature/map/src/test/java/com/trust3/xcpro/map/*`
- Boundary risk:
  - Low: all changes remain in UI runtime layer and do not alter repository data.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| render-time initialize call | each overlay `render()` | `MapOverlayManager` lifecycle + style-epoch contract | remove repeated init checks | overlay manager and overlay unit tests |
| style-loss recovery | implicit re-run via render->initialize | explicit guarded fallback branch | preserve robustness without hot-path checks | failure-mode tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `OgnTrafficOverlay.render()` | direct `initialize()` call each render | pre-initialized fast render path + guarded fallback | Phase 1 |
| `OgnThermalOverlay.render()` | direct `initialize()` call each render | pre-initialized fast render path + guarded fallback | Phase 1 |
| `OgnGliderTrailOverlay.render()` | direct `initialize()` call each render | pre-initialized fast render path + guarded fallback | Phase 1 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| render-throttle timestamps | Monotonic | existing OGN display update cadence |
| fallback re-init cooldown | Monotonic | avoid re-init loop after style loss |
| style-epoch token | N/A logical counter | style lifecycle identity, not elapsed time |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - map overlay runtime remains on map/UI thread.
  - no repository/domain threading changes.
- Primary cadence/gating sensor:
  - `MapOverlayManager` OGN display mode render cadence.
- Hot-path latency budget:
  - render path should avoid style/source/layer existence scans after init.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (UI runtime only; no randomization).
- Randomness used: No.
- Replay/live divergence rules:
  - no replay pipeline change; this is map runtime render plumbing only.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| render path still performs full init checks | architecture perf/hot-path intent | unit tests + review | new OGN overlay init tests |
| style change loses layers/icons | pipeline runtime correctness | map manager tests | new style-change lifecycle tests |
| accidental data-layer leakage | UI/runtime boundaries | review + enforceRules | touched map runtime files |
| fallback loops on style failure | stability/no unbounded loops | failure-mode tests | new guarded fallback tests |

## 3) Data Flow (Before -> After)

Before:

`MapOverlayManager -> Ogn*Overlay.render() -> initialize() -> style checks -> setGeoJson`

After:

`MapOverlayManager style event -> ensureInitialized(styleEpoch)`
`MapOverlayManager render tick -> Ogn*Overlay.renderFast() -> setGeoJson`
`On missing source/layer -> guarded one-shot recoverInit(styleEpoch) -> renderFast`

## 4) Implementation Phases

### Phase 0 - Baseline and instrumentation

- Goal:
  - establish current behavior/perf baseline and lock invariants with tests.
- Files to change:
  - add tests under `feature/map/src/test/java/com/trust3/xcpro/map/`
- Tests to add/update:
  - verify repeated renders currently invoke initialization path checks.
  - lock style-change reinit behavior as baseline contract.
- Exit criteria:
  - deterministic tests capture current lifecycle behavior.

### Phase 1 - Overlay lifecycle contract split

- Goal:
  - split `initialize` and `render` responsibilities in each OGN overlay.
- Files to change:
  - `OgnTrafficOverlay.kt`
  - `OgnThermalOverlay.kt`
  - `OgnGliderTrailOverlay.kt`
- Design:
  - keep explicit `initializeForCurrentStyle()` method.
  - make `render()` fast-path (no unconditional initialize call).
  - add `isInitializedForStyleEpoch` (or equivalent token) per overlay.
- Tests to add/update:
  - render after successful init does not call init branch.
  - icon/layer/source setup still happens once per style epoch.
- Exit criteria:
  - functional rendering unchanged, hot path no longer self-initializes.

### Phase 2 - Manager-owned style epoch wiring

- Goal:
  - make `MapOverlayManager` the single owner of style-epoch init timing.
- Files to change:
  - `MapOverlayManager.kt`
  - optionally `MapRuntimeController.kt` (if style token exposure needed)
- Design:
  - increment style epoch on map ready/style changed/recreate events.
  - call overlay init once when overlay instance is created or style changes.
  - retain existing `initializeTrafficOverlays(...)` semantics.
- Tests to add/update:
  - style change triggers exactly one re-init per OGN overlay.
  - normal render ticks do not trigger re-init.
- Exit criteria:
  - init is lifecycle-driven, not render-driven.

### Phase 3 - Guarded self-heal fallback

- Goal:
  - preserve robustness if map style drops OGN source/layers unexpectedly.
- Files to change:
  - `OgnTrafficOverlay.kt`
  - `OgnThermalOverlay.kt`
  - `OgnGliderTrailOverlay.kt`
- Design:
  - render fast path checks only source/layer presence needed to write.
  - if missing, perform one guarded recover-init per cooldown window.
  - log warning once per episode; avoid per-frame error spam.
- Tests to add/update:
  - simulated missing source/layer recovers within one render cycle.
  - repeated missing state does not cause unbounded init loops.
- Exit criteria:
  - safe fail-open behavior with bounded recovery cost.

### Phase 4 - Validation and perf regression gate

- Goal:
  - verify no behavioral regression and confirm hot-path overhead reduction.
- Files to change:
  - tests/docs only unless fixups required.
- Tests to add/update:
  - existing OGN map policy tests still pass.
  - add targeted runtime tests for update mode + render cadence interactions.
- Exit criteria:
  - required checks pass.
  - debug/perf evidence shows reduced repeated init work.

### Phase 5 - Docs, release note, and quality rescore

- Goal:
  - sync architecture/pipeline docs and finalize quality score.
- Files to change:
  - `docs/OGN/OGN.md`
  - `docs/ARCHITECTURE/PIPELINE.md` (if wording needs update)
  - parent quality plan status/rescore section
- Tests to add/update:
  - none beyond phase-4 fixups.
- Exit criteria:
  - docs match code behavior and score target (`>=9.5/10`) is evidenced.

## 5) Test Plan

- Unit tests:
  - new `OgnTrafficOverlayInitializationTest`
  - new `OgnThermalOverlayInitializationTest`
  - new `OgnGliderTrailOverlayInitializationTest`
  - new `MapOverlayManagerOgnLifecycleTest`
- Replay/regression tests:
  - N/A (UI runtime change only).
- UI/instrumentation tests (if needed):
  - optional style-switch instrumentation smoke with OGN traffic enabled.
- Degraded/failure-mode tests:
  - missing style source/layer during render.
  - rapid style changes with pending throttled renders.
- Boundary tests for removed bypasses:
  - render no longer calls full initialize in steady state.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| missed init after style change | empty/missing OGN layers | style-epoch tests + fallback recover-init | XCPro Team |
| fallback path reintroduces hot-path overhead | perf benefit reduced | cooldown + one-shot episode guard | XCPro Team |
| hidden behavior drift in thermal/trail overlays | visual regressions | include all OGN overlays in same lifecycle contract | XCPro Team |
| difficult perf proof | unclear ROI | add debug counters for init-attempt vs render counts | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No new SSOT duplication or data-layer coupling introduced.
- Render path does not unconditionally call initialize for OGN overlays.
- Style change and style-loss recovery remain correct and bounded.
- Required checks pass.
- OGN quality score uplift target:
  - from `9.3/10` to `>=9.5/10` after completion.
  - achieved: `9.5/10` (`95/100`) in the parent quality rescore.

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 3 fallback logic.
  - Phase 2 manager epoch wiring.
  - Phase 1 overlay render/init split.
- Recovery steps if regression is detected:
  1. Revert newest phase only.
  2. Keep tests and counters for diagnosis.
  3. Re-run:
     - `./gradlew enforceRules`
     - `./gradlew testDebugUnitTest`
     - `./gradlew assembleDebug`
