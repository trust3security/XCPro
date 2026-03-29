# Blue Location Overlay Viewport Scaling Phased IP

## 0) Metadata

- Title: Reduce blue ownship triangle size at wider visible map radius
- Owner: XCPro Team
- Date: 2026-03-29
- Issue/PR: TBD
- Status: Draft

Required pre-read order:
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`
8. `docs/MAPSCREEN/01_MAPSCREEN_PRODUCTION_GRADE_PHASED_IP_2026-03-05.md`
9. `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
10. `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`

## 1) Scope

- Problem statement:
  - The current blue ownship triangle is rendered at a fixed visual size regardless of visible map radius.
  - At wider map scales this makes ownship look oversized relative to surrounding traffic and terrain context.
  - Unlike OGN and ADS-B overlays, ownship currently has no viewport-aware size policy.
- Why now:
  - OGN and ADS-B already scale down visually as the viewport widens.
  - Ownship now stands out as the remaining fixed-size aircraft marker on MapScreen.
  - The requested behavior is narrow, user-visible, and can be implemented without an architecture rewrite.
- In scope:
  - Add viewport-aware size scaling for the blue ownship triangle overlay.
  - Use visible radius around ownship as the policy metric.
  - Define visible radius as the nearest screen-edge radius from the ownship screen point, converted with map distance-per-pixel.
  - Keep current icon artwork, rotation, overlap behavior, and z-order behavior.
  - Apply scaling on first render, camera idle, and style recreation.
- Out of scope:
  - No shared cross-overlay viewport policy framework.
  - No changes to ownship location source, smoothing, tracking, or camera-follow behavior.
  - No changes to OGN or ADS-B sizing logic.
  - No user setting for ownship icon size in this change.
  - No bitmap redesign in `SailplaneIconBitmapFactory.kt`.
- User-visible impact:
  - Ownship triangle remains current size at close radius.
  - Ownship triangle becomes smaller in three steps as visible radius widens.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ownship geographic position / heading | existing map position pipeline owners | current `updateLocation(...)` inputs into `BlueLocationOverlay` | new ViewModel or repository mirror for visual size |
| blue ownship rendered icon scale | `BlueLocationOverlay` runtime owner | internal overlay cached render state | persisted setting, `MapStateStore` field, or UI-local mirror |
| visible-radius scale thresholds | dedicated viewport policy file | pure function(s) | inline constants duplicated across initializer/tests/overlay |
| viewport metrics snapshot for ownship scaling | `BlueLocationOverlay` runtime owner | internal cached metrics snapshot using `MapCameraViewportMetrics` | raw `MapView` handle retained as policy state |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| blue ownship current icon scale multiplier | `BlueLocationOverlay` | overlay initialization, viewport refresh hook, style reapply path | internal layer styling only | visible radius around ownship + policy thresholds | none | overlay cleanup, overlay recreation | none | overlay runtime tests |
| visible radius around ownship | `BlueLocationOverlay` or focused helper owned by its policy seam | recomputed on demand from cached viewport metrics | internal policy resolution only | ownship screen point + nearest viewport edge + distance per pixel | none | each recompute | none | policy and overlay tests |
| viewport metrics snapshot for ownship scaling | `BlueLocationOverlay` | startup hook, camera-idle hook, style reapply path | internal scale refresh only | `MapView` width/height/pixel ratio projected into `MapCameraViewportMetrics` | none | overlay cleanup, overlay recreation | none | overlay tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

Modules/files touched:
- `feature:map-runtime`
- `feature:map`
- `docs/MAPSCREEN`

Boundary risk:
- do not move viewport policy into `MapInitializer`
- do not add ViewModel-owned or store-owned visual scale state
- do not widen the boundary by passing `MapView` into policy logic; prefer `MapCameraViewportMetrics`
- do not add a shared cross-feature viewport abstraction unless a later ADR explicitly chooses that path

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt` | current ownship overlay owner | keep visual state local to the runtime overlay and restyle the existing layer | add viewport-aware scale state and reapply path |
| `feature/map/src/main/java/com/example/xcpro/map/LiveFollowWatchAircraftOverlay.kt` | runtime-owned aircraft icon scaling with style reapply | overlay caches current scale and reapplies it on style/runtime recovery | blue ownship uses visible-radius metric instead of raw zoom bands |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapScaleBarController.kt` | existing map scale owner already computes viewport distance-per-pixel from map + viewport | reuse the same meters-per-pixel family and viewport metric inputs | ownship policy uses ownship screen point to nearest edge, not scale-bar max width |
| `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlaySupport.kt` | viewport-range math already exists in traffic overlays | reuse the pattern of deriving a viewport metric from geometry rather than persisting visual state | ownship policy uses ownship-centered visible radius instead of camera-center range |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| blue ownship icon scale policy | fixed inline `iconSize(1.0f)` inside `BlueLocationOverlay` | dedicated blue viewport policy file + `BlueLocationOverlay` cache | keep thresholds canonical and keep overlay runtime-focused | policy tests + overlay tests |
| viewport metrics snapshot for ownship sizing | none | `MapInitializer` startup/camera-idle runtime hook passing `MapCameraViewportMetrics` | reuse existing overlay-runtime update trigger path without leaking `MapView` | initializer tests + manual zoom QA |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `BlueLocationOverlay.createLocationLayer()` | hard-coded fixed `iconSize(1.0f)` with no viewport policy owner | layer size derived from `currentIconScale` resolved through a dedicated policy seam | Phase 1 |
| `MapInitializer` ownship setup path | overlay is initialized with no viewport-aware size application | explicit metrics snapshot push after initialization and on camera idle | Phase 2 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/MAPSCREEN/CHANGE_PLAN_BLUE_LOCATION_OVERLAY_VIEWPORT_SCALING_PHASED_IP_2026-03-29.md` | New | change contract for this slice | feature behavior plan belongs in docs, not code comments | not an ADR because no durable boundary move is planned | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationViewportScalePolicy.kt` | New | canonical visible-radius thresholds and pure scale-resolution helper | policy/math belongs outside the overlay body | not `MapInitializer`; not ViewModel; not a shared traffic file | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt` | Existing | ownship overlay runtime state, style restyle, style recovery, current scale cache | this file already owns ownship layer/image/source lifecycle | not `MapStateStore`, not UI, not repository | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt` | Existing | startup and camera-idle trigger for ownship viewport refresh | current camera-driven overlay update seam already lives here | not ViewModel; not overlay manager, because blue ownship is not routed through traffic delegates | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntimeMapLifecycleDelegate.kt` | Existing | style-change recreation seam if constructor or refresh call contract changes | this file recreates `BlueLocationOverlay` on style changes today | not `MapInitializer`, because style changes also arrive later in runtime lifecycle | No |
| `feature/map/src/test/java/com/example/xcpro/map/BlueLocationOverlayTest.kt` | Existing | runtime behavior regression tests | existing test owner for this overlay | not traffic tests; not generic map tests | No |
| `feature/map-runtime/src/test/java/com/example/xcpro/map/BlueLocationViewportScalePolicyTest.kt` | New | pure policy threshold tests | policy file lives in `feature:map-runtime` and should stay `internal` there | not `feature:map` because that would force wider visibility or boundary drift | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| blue ownship viewport metrics update method on `BlueLocationOverlay` | `feature:map-runtime` | `MapInitializer`, possibly `MapOverlayRuntimeMapLifecycleDelegate` | public cross-module, narrow consumer set | `feature:map` needs to pass viewport metrics after init and on camera idle without exposing `MapView` | keep narrow; no compatibility shim planned |

### 2.2F Scope Ownership and Lifetime

No new long-lived scopes are planned.

### 2.2G Compatibility Shim Inventory

No compatibility shim is planned.

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| ownship visible-radius bands `<5km`, `>=5km`, `>=10km`, `>=20km` | `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationViewportScalePolicy.kt` | `BlueLocationOverlay` and policy tests | this is a single-feature visual policy with no broader consumer set | No |
| ownship scale multipliers `1.00`, `0.75`, `0.50`, `0.25` | `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationViewportScalePolicy.kt` | `BlueLocationOverlay` and policy tests | same rationale as above | No |

### 2.2I Stateless Object / Singleton Boundary

No Kotlin `object` is required. Prefer top-level pure functions unless implementation proves a focused holder is cleaner.

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| ownship viewport scale | none | purely geometric from map viewport metrics and ownship position |
| ownship viewport refresh triggers | none | driven by lifecycle/camera events, not elapsed time |

Explicitly forbidden comparisons:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - existing MapLibre/UI runtime path on main thread
- Primary cadence/gating sensor:
  - initial overlay setup
  - camera idle
  - style recreation
- Hot-path latency budget:
  - no new continuous frame loop
  - no bitmap regeneration per zoom event
  - restyle-only update should stay within existing overlay apply budgets

### 2.4A Logging and Observability Contract

No new production logging is planned. If temporary debug logging is needed during implementation, it must be removed before merge.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none planned
  - replay should produce the same ownship scale for the same ownship position and map viewport sequence

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| map style unavailable during refresh | Recoverable | `BlueLocationOverlay` | no crash; overlay waits for style | retry on next initialize/style-ready path | overlay tests |
| visible radius cannot be resolved from current viewport | Degraded | `BlueLocationOverlay` policy path | keep base size `1.00` instead of failing | fall back to base multiplier until valid geometry exists | policy tests + overlay tests |
| ownship screen point is offscreen or viewport metrics are zero | Degraded | `BlueLocationOverlay` policy path | keep current/base size instead of snapping unpredictably | reuse last valid scale if available, else base multiplier | policy tests + overlay tests |
| ownship location unavailable | Degraded | existing ownship update path | no visible ownship update beyond current behavior | do not invent a new visual state | existing overlay tests |

### 2.5B Identity and Model Creation Strategy

No IDs or timestamps are created in this change.

### 2.5C No-Op / Test Wiring Contract

No new `NoOp` or convenience production path is planned.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| viewport math/policy drifts into initializer or UI | `ARCHITECTURE.md` responsibility matrix | review + unit test | `BlueLocationViewportScalePolicyTest.kt` |
| fixed-size path survives in ownship layer creation | canonical policy owner + file ownership rules | unit test + review | `BlueLocationOverlayTest.kt` |
| style recreation loses ownship scale | map runtime overlay ownership rules | unit test | `BlueLocationOverlayTest.kt` |
| camera idle trigger regresses or is omitted | map runtime lifecycle review | targeted test + review | initializer/lifecycle tests if needed |
| new direct time usage appears in runtime policy | timebase rules | `enforceRules` + review | affected files |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| ownship triangle is less dominant at wide radius | `MS-UX-01` | current fixed-size ownship marker | no regression in pan/zoom smoothness while ownship size changes on camera idle | manual zoom QA, optional SLO evidence if runtime impact appears | Phase 3 |
| ownship overlay does not flicker or pop on style change | `MS-UX-04` | current ownship steady-state behavior | no redundant ownship size flicker at style recreation | overlay regression tests + manual style-change QA | Phase 2 |
| startup ownship size is correct immediately | `MS-UX-06` | current fixed-size startup | wide-radius startup applies smaller size without flash | manual startup QA | Phase 3 |
| ownship restyle stays within overlay apply budget | `MS-ENG-01` | current overlay apply cost | no obvious regression; restyle-only path | review + targeted verification | Phase 3 |

## 3) Data Flow (Before -> After)

Before:

```text
MapInitializer
  -> BlueLocationOverlay.initialize()
  -> BlueLocationOverlay.createLocationLayer()
  -> fixed iconSize(1.0f)
  -> updateLocation() changes position/rotation only
```

After:

```text
MapInitializer startup / camera idle
  -> BlueLocationOverlay.setViewportMetrics(...)
  -> BlueLocationViewportScalePolicy.resolve(...)
  -> BlueLocationOverlay caches currentIconScale
  -> SymbolLayer iconSize(...) restyled only when band changes
  -> updateLocation() continues to own position/rotation only
```

## 4) Implementation Phases

### Phase 0 - Contract lock

- Goal:
  - Freeze the metric, thresholds, and file owners before code changes.
- Files to change:
  - this plan only
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - none
- Exit criteria:
  - metric is locked to visible radius around ownship
  - thresholds are locked
  - file plan is agreed

### Phase 1 - Pure policy owner

- Goal:
  - Add the canonical blue ownship viewport policy seam.
- Files to change:
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationViewportScalePolicy.kt`
  - `feature/map-runtime/src/test/java/com/example/xcpro/map/BlueLocationViewportScalePolicyTest.kt`
- Ownership/file split changes in this phase:
  - move thresholds and multiplier math out of `BlueLocationOverlay`
- Tests to add/update:
  - threshold boundary tests
  - invalid/unavailable viewport fallback tests
  - offscreen/zero-metrics fallback tests
- Exit criteria:
  - one canonical owner exists for thresholds
  - no duplicated constants remain in implementation files

### Phase 2 - Overlay runtime implementation

- Goal:
  - Make `BlueLocationOverlay` apply viewport-aware `iconSize(...)` while preserving current rotation and visibility behavior.
- Files to change:
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayRuntimeMapLifecycleDelegate.kt` if constructor or style-reapply contract needs it
  - `feature/map/src/test/java/com/example/xcpro/map/BlueLocationOverlayTest.kt`
- Ownership/file split changes in this phase:
  - `BlueLocationOverlay` owns current scale cache and style reapply behavior
- Tests to add/update:
  - initial scale application
  - same-band no-op
  - band crossing updates
  - style recreation retains scale
  - `bringToFront()` retains scale
  - cached viewport metrics are reused on style reapply
- Exit criteria:
  - ownship size changes only through the overlay owner
  - no bitmap regeneration on zoom/camera idle
  - no behavior regression in rotation/visibility

### Phase 3 - Runtime wiring and verification

- Goal:
  - Trigger ownship viewport refresh at the correct runtime points and validate release-grade behavior.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
  - any narrow lifecycle test file required by the chosen hook
- Ownership/file split changes in this phase:
  - initializer remains trigger-only; policy stays out of it
- Tests to add/update:
  - startup applies correct band
  - camera idle applies new band
  - no crash before first ownship update or style-ready path
- Exit criteria:
  - first render applies the expected ownship size
  - camera idle updates size correctly
  - style recreation and startup do not flash the wrong size

## 5) Test Plan

- Unit tests:
  - `BlueLocationViewportScalePolicyTest`
  - `BlueLocationOverlayTest`
- Replay/regression tests:
  - none beyond deterministic overlay tests unless replay-specific regressions are discovered
- UI/instrumentation tests (if needed):
  - not required by default for the first pass
- Degraded/failure-mode tests:
  - invalid viewport / missing style fallback
  - style recreation with same ownship pose
- Boundary tests for removed bypasses:
  - fixed-size path replaced by policy-driven icon size
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | `BlueLocationViewportScalePolicyTest.kt` |
| Time-base / replay / cadence | Determinism confirmation + review | no new clock usage; same-input same-output overlay tests |
| Persistence / settings / restore | Not applicable | none |
| Ownership move / bypass removal / API boundary | Boundary lock tests | `BlueLocationOverlayTest.kt` and narrow initializer tests |
| UI interaction / lifecycle | UI or instrumentation coverage when needed | manual map zoom/style QA |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | no new loop; restyle-only verification; SLO evidence if needed |

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

Manual QA required before merge:
- zoom out until visible radius crosses 5 km, 10 km, and 20 km, confirm stepped size reductions
- zoom back in and confirm the triangle returns to current size below 5 km
- start MapScreen at wide radius and confirm reduced size applies immediately
- trigger map style change and confirm no ownship size flash
- confirm ownship rotation and z-order remain unchanged

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| threshold interpretation feels wrong in practice | visual mismatch with pilot expectation | lock metric now and verify on device before merge | XCPro Team |
| off-center ownship radius calculation is too coarse | size changes earlier/later than expected when panned | prefer ownship-based visible radius and test panned-map scenarios | XCPro Team |
| style recreation resets size | visible flash/regression | overlay caches current scale and tests style recovery explicitly | XCPro Team |
| bitmap is regenerated on every refresh | runtime overhead and avoidable churn | restyle layer size only; keep bitmap factory unchanged | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: N/A
- Decision summary:
  - this is a narrow single-overlay visual policy addition
  - it does not introduce a new durable cross-feature architecture boundary
- Why this belongs in an ADR instead of plan notes:
  - not applicable

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling remains explicit and unchanged
- Replay behavior remains deterministic
- Error/degraded behavior is explicit for missing style or invalid viewport geometry
- No ADR is introduced unless scope broadens into a shared viewport framework
- For map/overlay interaction changes: impacted SLOs show no regression or an approved deviation is recorded
- `KNOWN_DEVIATIONS.md` remains unchanged unless an approved exception is needed

## 8) Rollback Plan

- What can be reverted independently:
  - viewport policy file
  - overlay scale logic
  - initializer trigger hook
- Recovery steps if regression is detected:
  - revert the ownship viewport-scaling slice only
  - return `BlueLocationOverlay` to fixed `iconSize(1.0f)`
  - remove startup/camera-idle ownship refresh calls
- User-visible fallback after rollback:
  - ownship triangle returns to current fixed-size behavior
