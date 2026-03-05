# MapScreen Production-Grade Phased Implementation Plan (IP)

## 0) Metadata

- Title: MapScreen performance and efficiency production upgrade
- Owner: XCPro Team
- Date: 2026-03-05
- Issue/PR: TBD
- Status: In progress (Phase 1 complete, Phase 2 complete, Phase 3 blocked on `MS-UX-01`; deviation `RULES-20260305-12`, expiry 2026-04-15)

Required pre-read order:
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`

## 1) Objective

Raise MapScreen runtime quality from "functionally correct with known hot spots"
to "production-grade under map motion + overlay stress".

Primary outcomes:

- smoother pan/zoom and drag workflows,
- lower style/layer churn,
- lower main-thread pressure in overlay update paths,
- deterministic replay-safe behavior unchanged.

Visual UX contract outcomes (mandatory and measurable):

- `MS-UX-01`: smoother pan/zoom/rotate while moving,
- `MS-UX-02`: less stutter while dragging task points/overlays,
- `MS-UX-03`: fewer traffic/weather/OGN marker jumps,
- `MS-UX-04`: less flicker/z-order popping,
- `MS-UX-05`: cleaner replay scrubbing/playback,
- `MS-UX-06`: faster first-map readiness with fewer redraw bursts.

SLO thresholds are defined in:
`docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`.

## 2) Confirmed Performance Hotspots

1. AAT drag emits full task render sync on each move:
   - causes clear/remove/replot cycle in task overlays.
2. Rain animation frame updates trigger repeated traffic overlay front ordering:
   - can remove/re-add layers frequently while animation is active.
3. Startup overlay reapply is duplicated between map initialization and map-ready callback.
4. ADS-B frame loop evaluates motion frame list more than once per visual frame.
5. OGN target publish path sorts full target list on every publish.
6. `AndroidView.update` triggers `onMapViewBound()` repeatedly, which calls lifecycle
   sync and can invoke `locationManager.onDisplayFrame()` outside intended cadence.
7. Ownship altitude state is emitted at raw flight-data cadence and is used as a key for
   both OGN and ADS-B overlay update side effects.
8. Traffic overlay update hot paths use structural list equality checks and, for ADS-B,
   call front-order operations on every accepted update.
9. Replay observer combine path includes `session.map { selection != null }` without
   `distinctUntilChanged`, causing avoidable recompute when replay session emits progress updates.
10. Replay session debug logging emits on each session update path and is not explicitly
    debug-gated in observer wiring.
11. SCIA trail path does full-list filtering + deep list comparison + full feature rebuild
    (`GeoJson`) per accepted update, which scales poorly with large trail segment counts.
12. ADS-B motion smoothing seeds new entries with `lastSampleMonoMs = 0` and does not
    refresh sample time in the no-animation replacement path; this can produce
    over-long interpolation windows after stable periods.
13. ADS-B store selection path sorts the same candidate set twice per update
    (emergency audio candidate + displayed list).
14. Weather rain frame cache identity omits normalized tile size; frame cache reuse can
    keep a stale `RasterSource` tile-size configuration after render-option changes.
15. Root-level binding aggregation (`rememberMapScreenBindings`) collects many flows in
    one composable, amplifying whole-screen recomposition pressure for single-stream updates.
16. Display-frame pumping can run in dual paths (Compose frame loop + render-frame sync
    repaint trigger), increasing cadence pressure during replay.
17. OGN trail-aircraft row construction does full map/filter/sort/lowercase work
    per recomposition under target-stream churn.
18. OGN key-selection matching allocates per-candidate alias sets in hot loops.
19. Overlay side-effect wiring keys large list objects in `LaunchedEffect`, causing
    frequent effect restart and manager-call churn under dense target updates.

### 2A) Hotspot Source Anchors (Focused Repass)

| Hotspot | Primary Code Anchor |
|---|---|
| Weather/traffic/ownship front-order churn | `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt` |
| Additional traffic bring-to-front path | `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` |
| Startup map/overlay ordering coupling | `feature/map/src/main/java/com/example/xcpro/map/MapInitializerDataLoader.kt` |
| Dual display-frame cadence pressure | `feature/map/src/main/java/com/example/xcpro/map/MapLifecycleManager.kt` |
| Compose frame-loop display-frame trigger | `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt` |
| Replay/map bind callback churn | `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt` |
| ADS-B select-path duplicate sort | `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt` |
| Weather rain frame-cache identity risk | `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt` |
| Root-level list-keyed effect restart pressure | `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt` |

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Task definitions and render snapshot | Task repository/coordinator owners | `TaskRenderSnapshot` via use-case | Compose-side task geometry caches |
| Weather rain runtime preference/state | weather repositories + weather VM | state flow | map runtime ad-hoc source of truth |
| OGN/ADS-B target streams | OGN/ADS-B repositories | flows/snapshots | overlay-owned authoritative mirrors |
| Map camera/display pose state | Map state owners + display pose pipeline | state flows + runtime callbacks | UI-local business state copies |

### 3.1A Field-Level SSOT Registry (Mandatory)

| SSOT ID | Data Item | Owner Type | Canonical Flow/State | Write Boundary | Forbidden Mirror |
|---|---|---|---|---|---|
| SSOT-MAP-01 | map style name | map state store | `mapStyleName` | VM/use-case only | composable-local map style mutable state |
| SSOT-MAP-02 | map camera snapshot | map state store | `savedLocation/savedZoom/savedBearing` | location/runtime manager only | overlay-local camera cache used as source of truth |
| SSOT-MAP-03 | replay session selection/status | replay use-case | `replaySessionState` | replay coordinator/use-case | observer-local replay flags without projection gates |
| SSOT-OGN-01 | OGN target list/snapshot | OGN repository/use-case | `ognTargets`, `ognSnapshot` | traffic use-case only | overlay-owned authoritative target state |
| SSOT-OGN-02 | OGN trail segments | OGN trail repository | `ognGliderTrailSegments` | trail repository/runtime | composable list copies used as authoritative state |
| SSOT-ADSB-01 | ADS-B target list/snapshot | ADS-B repository/use-case | `adsbTargets`, `adsbSnapshot` | traffic use-case only | overlay-owned authoritative ADS-B state |
| SSOT-WR-01 | weather rain selection/options | weather overlay VM/use-case | `overlayState.selectedFrame/renderOptions` | weather use-case only | runtime overlay cache as source of truth |
| SSOT-TASK-01 | task geometry snapshot | tasks use-case/coordinator | `TaskRenderSnapshot` | task domain/use-case only | drag-preview geometry persisted as task source of truth |

### 3.2 Dependency Direction

Must remain:

`UI -> domain/use-cases -> data`

No new direct UI to manager/repository bypasses.

Boundary risk:

- map runtime side effects called from composable lifecycle/update hooks,
- task overlay sync paths that can bypass intended throttling boundaries,
- replay observer fanout that can recompute on non-semantic session updates,
- root-level flow aggregation and list-keyed effects causing broad recomposition/effect restarts,
- cache identity gaps in weather rain frame handling (tile size not part of cache key),
- display-frame dual-pump paths that can over-drive map/update cadence.

### 3.2C Dependency Edge Allowlist/Denylist (Mandatory)

Allowlist:
1. `ui/* -> map use-cases/viewmodel interfaces`.
2. `viewmodel -> use-cases/repository ports`.
3. `runtime overlay managers -> map runtime adapters only (no direct repository ownership)`.
4. `domain/use-case -> data ports/adapters via DI`.

Denylist:
1. `Composable -> repository/manager mutation`.
2. `ViewModel -> MapLibre/Android UI types for business decisions`.
3. `overlay runtime -> authoritative data ownership`.
4. `domain/fusion paths -> direct system wall-time APIs`.
5. `cross-layer shared mutable singleton state`.

### 3.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| AAT drag visual updates | Full task sync path | Dedicated drag-preview runtime path + commit sync path | remove per-move teardown churn | task render parity + drag stress tests |
| Traffic overlay z-order reconciliation | Multi-call distributed `bringToFront` calls | Single idempotent ordering policy owner | prevent repeated remove/add churn | ordering policy tests and reorder counters |
| Map-view bound lifecycle sync trigger | `AndroidView.update` driven callback side effects | owner-state transition driven lifecycle sync | prevent recomposition-induced display-frame work | recomposition/lifecycle sync tests |
| Replay selection projection in observer combine | raw session projection | distinct selection projection | avoid no-op recompute on progress ticks | observer recompute count tests |
| ADS-B smoothing sample-timestamp handling | implicit zero/default sample time in entry paths | explicit monotonic sample-time ownership for all entry transitions | prevent over-long interpolation windows | ADS-B smoother timing tests |
| Weather rain frame-cache identity | cache keyed by frame time + URL | cache keyed by frame time + URL + normalized tile-size | prevent stale source config reuse | weather tile-size reconfiguration tests |
| Root map-state fanout | root composable-wide binding collection | feature-scoped collectors and stable runtime input models | reduce broad recomposition pressure | compose recomposition counters + trace |
| Display-frame cadence ownership | mixed Compose frame loop + render-sync trigger | single cadence owner per mode (replay/live + sync policy) | remove duplicate frame-pressure paths | frame cadence counters + replay scrub tests |

### 3.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| map task drag callback chain | drag move -> full task render sync | drag move -> preview update; gesture end/mutation -> full sync | Phase 1 |
| weather apply paths | frame tick -> repeated traffic front calls | centralized ordering reconciliation called only when dirty | Phase 1 |
| map-view update hook | recomposition -> lifecycle sync + display frame | guarded one-shot lifecycle sync on owner-state transitions | Phase 1 |
| replay observer combine | session progress update triggers full combine path | `distinctUntilChanged` on replay-selection projection | Phase 2 |
| ADS-B smoother entry update path | stationary/no-animation paths keep stale sample timestamp | normalize sample-time update for all target update branches | Phase 2 |
| weather rain frame cache | cache hit path ignores tile-size config | include tile-size in cache identity and rebuild on mismatch | Phase 2 |
| root map bindings/effects | one aggregate binding + list-keyed effects | split into scoped collectors + signature/version keyed effects | Phase 1-2 |
| render loop ownership | continuous Compose `withFrameNanos` and repaint-trigger sync | explicit single-owner render driver per sync mode | Phase 1 |

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Task drag visual throttling cadence | Monotonic/frame clock | animation smoothness without wall-time drift |
| Weather animation ticks | UI timer/wall for visual selection only | visual-only sequencing |
| ADS-B motion interpolation | Monotonic | stable delta computation |
| Replay map pose and timing | Replay clock | deterministic replay behavior |

Forbidden comparisons:

- monotonic vs wall
- replay vs wall

### 3.3A Hotspot Timebase Declaration (Mandatory)

| TB ID | Hotspot Path | Required Time Base | Forbidden Alternative | Validation Artifact |
|---|---|---|---|---|
| TB-01 | ADS-B smoothing sample/retarget durations | monotonic | wall-time/replay clock | `test AdsbDisplayMotionSmoother*` |
| TB-02 | render-frame cadence governance | monotonic frame cadence | wall-time delay loops for core frame driver | `trace frame cadence report` |
| TB-03 | replay scrub/render stabilization | replay clock + monotonic render timing | wall-time derived replay deltas | `replay scrub perf harness` |
| TB-04 | weather rain transition timing | visual wall-time only | mixing visual wall-time with fusion/replay time | `weather rain transition tests` |
| TB-05 | task drag preview cadence | monotonic/frame clock | wall-time debounce in drag hot path | `task drag latency harness` |

### 3.4 Replay Determinism

- Deterministic for same input: Yes (required)
- Randomness: None planned
- Live/replay divergence: unchanged by this IP unless explicitly documented in phase notes

### 3.4A Replay Determinism Invariants (Mandatory)

| DET ID | Invariant | Pass Rule | Evidence |
|---|---|---|---|
| DET-01 | identical replay input yields identical overlay ordering transitions | same ordered transition log for run A/B | replay determinism diff report |
| DET-02 | scrub to timestamp produces stable map pose and selected overlay frame | p95/p99 within `MS-UX-05` and no no-op recompute churn | scrub harness + observer metrics |
| DET-03 | replay selection projection changes only on semantic selection changes | no recompute increments on progress-only session ticks | observer projection unit tests |
| DET-04 | render cadence owner is single-path per replay mode | no dual cadence counter increments in same frame window | cadence ownership telemetry |

### 3.5 Boundary Adapter Check

No new boundary adapters are required for this IP by default.
If any new persistence/network/device behavior is added during phases,
the domain-port + data-adapter + DI binding pattern is mandatory.

### 3.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Task drag triggers full teardown per move | ARCHITECTURE.md (UDF/SSOT + layering), CODING_RULES.md (UI boundaries) | unit/integration + review | task render sync tests (new) |
| Weather animation causes repeated z-order churn | ARCHITECTURE.md (runtime correctness), CODING_RULES.md (hot path/logging discipline) | unit/integration + perf counters | overlay ordering policy tests (new) |
| Recomposition triggers lifecycle/display work | ARCHITECTURE.md (lifecycle correctness) | integration + review | map host lifecycle sync tests (new) |
| Replay progress drives no-op observer recompute | ARCHITECTURE.md (determinism and explicit data flow) | unit tests | MapScreenObservers replay-projection tests (new) |
| SCIA dense path causes excessive full-list rebuilds | CODING_RULES.md (predictable hot paths) | stress/perf tests | SCIA dense trail stress suite (new) |
| Time-base drift while optimizing cadence | ARCHITECTURE.md (time base rules) | enforceRules + unit tests | existing + new cadence tests |
| ADS-B smoother interpolation window drift | ARCHITECTURE.md (runtime correctness) + CODING_RULES.md (predictable behavior) | unit tests | AdsbDisplayMotionSmoother transition tests (new) |
| ADS-B store double-sort hot path | CODING_RULES.md (hot path efficiency) | micro-benchmark + unit tests | AdsbTrafficStore select-cost tests (new) |
| Weather rain tile-size cache mismatch | ARCHITECTURE.md (runtime correctness) | unit/integration tests | WeatherRainOverlay cache identity tests (new) |
| Root-level recomposition/effect restart pressure | CODING_RULES.md (maintainability + hot paths) | compose/runtime trace | MapScreen root/binding recomposition harness (new) |
| OGN key lookup allocation churn | CODING_RULES.md (hot path predictability) | allocation profile + unit tests | OgnAddressing selection lookup tests (new) |

## 4) Data Flow (Before -> After)

Before:

`Flight/traffic/task/weather flows -> ViewModel/UI effects -> runtime overlay updates`

Key pain points in current flow:

1. AAT drag events hit full task sync path.
2. Weather frame ticks can trigger repeated traffic ordering calls.
3. Map-view update callback can trigger lifecycle sync side effects repeatedly.
4. Replay session progress can fan out observer recomputation when replay-selection state is unchanged.
5. SCIA trail updates propagate through full-list filter/compare/rebuild stages.
6. ADS-B smoother can use stale sample timestamps, stretching interpolation windows.
7. Weather rain cache can reuse a frame layer/source with stale tile-size configuration.
8. Root-level flow aggregation/list-keyed effects can trigger broad recomposition + effect restart churn.
9. Render cadence can be over-driven by dual frame-pump paths.
10. ADS-B selection and OGN row/selection helpers include avoidable sort/allocation overhead.

After:

`Flight/traffic/task/weather flows -> deduped/guarded runtime policy layers -> minimal overlay mutations`

Targeted changes:

1. Drag preview path separated from full task sync path.
2. Single idempotent overlay ordering policy.
3. Lifecycle sync bound to owner-state transitions, not recomposition churn.
4. Replay observer projection deduped by semantic replay-selection state.
5. SCIA trail path optimized to reduce repeated full-list heavy stages.
6. ADS-B smoothing uses explicit monotonic sample-time transitions for all entry paths.
7. Weather rain frame cache identity includes tile-size to guarantee config-correct reuse.
8. Root bindings/effects are split and keyed by lightweight signatures where possible.
9. Display-frame cadence is single-owner per mode to prevent duplicate pumping.
10. ADS-B/OGN hot-path sort/allocation churn is reduced with deterministic, low-allocation selectors.

### 4A) Sequence Contracts (Mandatory)

| Sequence ID | Trigger | Ordered Steps | Cadence/Backpressure Owner | Fallback Rule |
|---|---|---|---|---|
| DF-LIVE-01 | live flight sample | VM projection -> overlay runtime signature check -> minimal overlay mutation | overlay runtime scheduler | skip mutation on unchanged signature |
| DF-REPLAY-01 | replay frame/scrub | replay projection -> display pose -> overlay apply gate -> render | replay render coordinator | freeze last stable frame on transient gap |
| DF-START-01 | map style ready | style callback -> single startup overlay apply -> status counters | map runtime controller | abort stale generation callback |
| DF-DRAG-01 | task drag move/end | move -> preview-only updates; end -> commit sync | task drag preview coordinator | force final sync on gesture end/cancel |
| DF-WRAIN-01 | weather frame change | frame selection -> cache identity check -> render/cross-fade -> ordering policy | weather runtime delegate | rebuild frame source on tile-size/key mismatch |

## 5) Phased Plan

### Phase 0 - Baseline and Instrumentation Lock

Goal:
- establish measurable baseline before behavior changes.

Work:
- add trace points and counters for:
  - task render sync frequency during drag,
  - overlay layer reorder count per minute,
  - startup overlay apply count,
  - lifecycle sync invocations from map-view bind/update paths,
  - ownship-altitude-driven overlay update frequency,
  - replay observer recompute count due session-progress-only events,
  - SCIA trail filter/render durations and rendered segment counts,
  - ADS-B frame-build duration and frame list cardinality,
  - ADS-B smoothing interpolation-duration distribution and retarget sample deltas,
  - ADS-B selection sort-count and select-phase duration,
  - weather rain cache-hit/cache-rebuild counts by tile-size config,
  - root recomposition counts for `MapScreenRoot`/bindings and list-effect restart counts,
  - OGN selection helper allocation/lookup cost,
  - OGN trail-aircraft row build duration and sort-count.

Exit criteria:
- baseline report captured for representative scenarios.

### Phase 1 - High-Impact Churn Removal

Goal:
- remove the largest avoidable runtime churn first.

Work:
- decouple AAT drag preview updates from full task style teardown/rebuild.
- implement idempotent central layer-order policy to avoid repeated traffic overlay remove/add on weather animation ticks.
- remove duplicate startup reapply pass.
- make map-view bound lifecycle sync one-shot/idempotent and remove recomposition-driven
  display-frame side effects.
- make render cadence single-owner when render-frame sync is enabled (remove dual-pump behavior).
- partition root-level binding/effect fanout to reduce whole-screen recomposition/effect restart pressure.

Exit criteria:
- drag no longer causes full task style teardown per move.
- weather animation no longer triggers repetitive front-order churn.
- startup overlay apply occurs once per map generation.
- map recomposition does not trigger lifecycle/display-frame work loops.
- render cadence no longer shows duplicate frame-pump pressure in replay/live paths.
- root-level recomposition pressure reduced for traffic/weather/scia update bursts.

### Phase 2 - Overlay Hot-Path Micro-Optimization

Goal:
- reduce per-frame CPU overhead in traffic overlays.

Work:
- ADS-B: reuse per-frame computed target frame list for emergency-check + feature-build path.
- ADS-B: fix smoother sample-time handling in stationary/no-animation branches.
- ADS-B: remove duplicate full sort in `AdsbTrafficStore.select` via single-order pass/selection.
- OGN: avoid full re-sort on unchanged ordering keys; apply conditional incremental ordering strategy.
- add ownship altitude cadence governance for overlay label updates (quantize/throttle at
  the state source or runtime boundary).
- reduce deep list-equality work on high-frequency update paths by using lightweight
  versioning/signature checks where possible.
- dedupe replay observer triggers (`distinctUntilChanged` on replay-selection projection)
  and bound replay debug logging overhead.
- optimize SCIA trail path with change signatures and/or incremental rendering strategy
  to avoid full collection rebuild on each update.
- fix weather rain frame-cache identity to include tile-size and force safe rebuild on mismatch.
- reduce OGN key-selection allocation churn by eliminating per-candidate alias-set creation.

Exit criteria:
- reduced ADS-B frame callback cost under dense target scenes.
- reduced OGN publish CPU cost under high update cadence.
- reduced ownship-altitude-driven overlay update churn and lower list-comparison overhead.
- reduced replay observer no-op recompute work.
- reduced SCIA trail render cost under dense segment scenarios.
- ADS-B interpolation stability no longer degrades after stable/no-animation periods.
- weather rain overlay reflects tile-size config changes without stale source reuse.
- OGN selection and row-building paths show reduced allocation and sort churn.

### Phase 3 - Stress Hardening and Cadence Governance

Goal:
- lock stable behavior under sustained stress.

Work:
- add explicit cadence/backpressure policy for high-frequency visual updates.
- ensure frame/update coalescing where appropriate.
- verify no UI-layer business logic creep was introduced.

Exit criteria:
- no runaway update loops under weather animation + traffic + task edit concurrency.
- architecture gates remain clean.

### Phase 4 - Release Gate and Evidence Pack

Goal:
- make changes release-defensible.

Work:
- full required checks and evidence collection.
- final quality rescore with residual-risk ledger.
- update architecture pipeline doc only if wiring changed.

Exit criteria:
- required checks pass.
- quality rescore >= 4.5/5 for release readiness.
- no unapproved deviations introduced.

### 5A) Work Package Ledger (Mandatory)

| WP ID | Phase | Scope | Owner Role | Depends On | Entry Gate | Exit Gate | Required Artifact |
|---|---|---|---|---|---|---|---|
| WP-01 | 1 | drag preview split + commit sync | map runtime owner | baseline counters | Phase 0 complete | `MS-UX-02` pass | `artifacts/mapscreen/phase1/wp-01/<timestamp>/` |
| WP-02 | 1 | centralized overlay ordering policy | overlay runtime owner | WP-01 | reorder baseline present | `MS-UX-04` pass | `artifacts/mapscreen/phase1/wp-02/<timestamp>/` |
| WP-03 | 1 | lifecycle sync idempotency + render owner unification + root fanout split | lifecycle/runtime owner | WP-02 | lifecycle/cadence/recomposition counters active | `MS-ENG-06`, `MS-ENG-09`, `MS-ENG-10` pass | `artifacts/mapscreen/phase1/wp-03/<timestamp>/` |
| WP-04 | 2 | ADS-B frame reuse + smoother timestamp integrity | ADS-B owner | WP-03 | ADS-B baseline captured | `MS-ENG-03`, `MS-ENG-07` pass | `artifacts/mapscreen/phase2/wp-04/<timestamp>/` |
| WP-05 | 2 | weather rain cache-key identity + tile-size-safe rebuild | weather owner | WP-03 | weather baseline captured | `MS-ENG-08` pass | `artifacts/mapscreen/phase2/wp-05/<timestamp>/` |
| WP-06 | 2 | replay projection dedupe + logging guard | replay owner | WP-03 | replay baseline captured | `MS-UX-05` pass | `artifacts/mapscreen/phase2/wp-06/<timestamp>/` |
| WP-07 | 2 | OGN ordering/trail/selection sort-allocation churn reduction | OGN owner | WP-03 | OGN baseline captured | `MS-ENG-04`, `MS-ENG-05`, `MS-ENG-11` pass | `artifacts/mapscreen/phase2/wp-07/<timestamp>/` |
| WP-08 | 3 | mixed-load cadence governance hardening | performance owner | WP-04..07 | all P2 baselines captured | `MS-UX-01` pass | `artifacts/mapscreen/phase3/wp-08/<timestamp>/` |
| WP-09 | 4 | final evidence pack and release gate | release owner | WP-01..08 | all required artifacts present | all mandatory SLOs pass | `artifacts/mapscreen/phase4/wp-09/<timestamp>/` |

## 6) Quality Target Scorecard

| Category | Current (estimated) | Target |
|---|---:|---:|
| Runtime smoothness under map movement | 3.7/5 | 4.6/5 |
| Overlay efficiency under concurrent overlays | 3.5/5 | 4.5/5 |
| Maintainability of runtime map pipeline | 4.0/5 | 4.6/5 |
| Test confidence in risky paths | 3.6/5 | 4.5/5 |
| Release readiness | 3.8/5 | 4.5/5 |

### 6A) Score Computation Model (Mandatory)

`SectionScore = 0.40 * ArchitectureEvidence + 0.35 * FlowEvidence + 0.25 * GateEvidence`

Metric rules:
1. `ArchitectureEvidence` (0..100): SSOT registry completeness + dependency/timebase/determinism invariants mapped to tests.
2. `FlowEvidence` (0..100): sequence contracts defined, cadence owner explicit, fallback rules explicit.
3. `GateEvidence` (0..100): phase work packages have owners/dependencies, numeric entry/exit gates, and required artifacts.

Promotion rule:
1. Any missing mandatory artifact caps section score at `94`.
2. Any unresolved ownership/dependency ambiguity caps section score at `90`.
3. Any missing deterministic replay invariant evidence caps section score at `92`.

## 7) Acceptance Gates

1. No architecture-rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
2. No duplicate SSOT ownership introduced.
3. Replay determinism preserved.
4. Hotspot regressions covered by tests.
5. Required verification commands pass:
   - `python scripts/arch_gate.py`
   - `./gradlew enforceRules`
   - `./gradlew testDebugUnitTest`
   - `./gradlew assembleDebug`
   - when relevant (device/emulator available):
     - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
     - `./gradlew connectedDebugAndroidTest --no-parallel`
6. `KNOWN_DEVIATIONS.md` untouched unless explicitly approved with issue/owner/expiry.
7. No merge unless impacted `MS-UX-*` and `MS-ENG-*` SLOs pass with evidence.
8. Any accepted SLO miss must be time-boxed in `KNOWN_DEVIATIONS.md`
   with issue, owner, expiry, and rollback plan.
9. Every promoted package includes mandatory artifact bundle under
   `artifacts/mapscreen/<phase>/<package-id>/<timestamp>/`.
10. Phase evidence includes AGENT.md minimum evidence items:
    - `scripts/arch_gate.py` result,
    - timebase path citations (`core/time/.../Clock.kt`, `app/.../TimeModule.kt`),
    - pass/fail summary for required verification commands.

## 8) Out of Scope (This IP)

- New map product features unrelated to performance.
- Changing user-visible map behavior semantics unless needed for correctness/performance.
- Re-architecture beyond targeted hotspot fixes.

## 9) Focused Repass Re-Score (/100)

Date: 2026-03-05 (focused repass)

| Section | Score (/100) | Re-score rationale |
|---|---:|---|
| 0) Metadata | 100 | pre-read order and ownership contract are explicit and complete. |
| 1) Objective | 98 | user-visible UX outcomes remain explicit and tightly mapped to mandatory SLO and gate evidence. |
| 2) Confirmed Hotspots | 98 | hotspot inventory now covers missed high-impact runtime/caching/cadence/allocation gaps and is tied to concrete source anchors. |
| 3) Architecture Contract | 98 | SSOT registry, dependency allowlist/denylist, hotspot timebase declaration, and replay determinism invariants are explicit, test-mapped, and AGENT evidence-linked. |
| 4) Data Flow | 97 | sequence contracts define trigger, ordered steps, cadence owner, and fallback behavior for all critical paths with package-level evidence binding. |
| 5) Phased Plan | 98 | phase plan uses ticketized work packages with explicit owner/dependency/entry/exit gates and consistent artifact directories. |
| 6) Quality Target Scorecard | 97 | score computation model, promotion caps, statistical rules, and AGENT evidence requirements are explicit and auditable. |
| 7) Acceptance Gates | 97 | gates align with artifact bundles, command contract, and deterministic replay evidence requirements. |
| 8) Out of Scope | 98 | scope boundaries remain clear and reduce uncontrolled drift risk. |
| Overall Plan Readiness | 98 | production-grade, execution-ready, and evidence-anchored for phased delivery with AGENT contract traceability. |

## 10) Implementation Status Snapshot (2026-03-05)

| Work Package | Status | Implemented Scope | Remaining Scope |
|---|---|---|---|
| WP-01 | Complete | drag preview and commit-sync split is live; per-move full task sync avoided by default path. | none |
| WP-04 | Partial | ADS-B smoother timestamp integrity fixed (no zero-seeded stationary entries; no-animation branch refreshes sample time; guarded delta floor on invalid/backwards sample time); overlay frame loop now uses single snapshot per visual frame; store select path removes duplicate emergency-candidate full sort (single display sort + linear candidate selection). | evidence artifact pack and remaining ADS-B dense-scene perf tuning if needed. |
| WP-05 | Partial | weather package evidence automation is live (`pkg-w1` one-command capture wrapper + package config) and weather cache identity regression coverage now includes same-frame tile-size transition rebuild checks. | Tier A/B perf capture and `MS-ENG-08` pass evidence attachment. |
| WP-06 | Partial | replay observer dedupes selection-presence projection via semantic `distinctUntilChanged` gate; replay session debug log stream is now debug-gated to avoid release overhead. | replay scrub/perf evidence package still pending. |
| WP-07 | Partial | OGN/SCIA hot paths now use allocation-light lookup matching (no per-candidate alias-set creation), remove nested `setOf(...)` loop allocations in selection clear paths, reduce SCIA trail combine/delegate deep equality to segment-ID identity checks, and cache lowercase sort keys for trail-aircraft rows. Added focused regression tests for OGN alias parity and SCIA identity comparators. | package artifact + Tier A/B perf evidence still pending for `MS-ENG-04`, `MS-ENG-05`, `MS-ENG-11`. |
| WP-08 | Partial | mixed-load cadence hardening is started: ownship altitude fanout to OGN/ADS-b overlays is quantized at runtime boundary (`quantizeOverlayOwnshipAltitudeMeters`, 2 m default), focused unit coverage added (`MapScreenRootEffectsTest`), phase-3 evidence automation is wired (`pkg-e1` capture config + wrappers), and automated threshold verification is added (`verify_mapscreen_package_evidence.ps1`, `run_mapscreen_evidence_threshold_checks.ps1`). | attach Tier A/B mixed-load captures and validate `MS-UX-01` with no regression against prior mandatory SLOs. |

Verification snapshot for this checkpoint:
- `python scripts/arch_gate.py` passed.
- `./gradlew enforceRules` passed.
- `./gradlew testDebugUnitTest` passed.
- `./gradlew assembleDebug` passed.
- `./gradlew :feature:map:testDebugUnitTest --tests com.example.xcpro.map.ui.MapScreenRootEffectsTest` passed.
- `./gradlew :feature:map:testDebugUnitTest --tests com.example.xcpro.ogn.OgnAddressingTest --tests com.example.xcpro.ogn.OgnTrailSelectionPreferencesRepositoryTest --tests com.example.xcpro.map.OgnGliderTrailOverlayRenderPolicyTest` passed.
- `powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_pkg_e1_evidence_capture.ps1` passed.
- `powershell -ExecutionPolicy Bypass -File scripts/qa/seed_mapscreen_metric_templates.ps1` passed.
- `powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_evidence_threshold_checks.ps1 -AllowPending` passed.

Latest evidence bundles:
- `artifacts/mapscreen/phase2/pkg-d1/20260305-151746/` (MAPSCREEN-004 + MAPSCREEN-013 / WP-04 + WP-07 checkpoint; generated via `run_mapscreen_pkg_d1_evidence_capture.ps1`)
- `artifacts/mapscreen/phase2/pkg-g1/20260305-151627/` (MAPSCREEN-011 + MAPSCREEN-012 / WP-06 + WP-07 checkpoint; generated via `run_mapscreen_pkg_g1_evidence_capture.ps1`)
- `artifacts/mapscreen/phase2/pkg-w1/20260305-145642/` (MAPSCREEN-014 / WP-05 checkpoint; generated via `run_mapscreen_pkg_w1_evidence_capture.ps1`)
- `artifacts/mapscreen/phase3/pkg-e1/20260305-153337/` (MAPSCREEN-006 + MAPSCREEN-007 / WP-08 checkpoint; generated via `run_mapscreen_pkg_e1_evidence_capture.ps1`)

Promotion note:
- All bundles are `blocked_pending_perf_evidence` until device-tier SLO capture is attached (`MS-ENG-03`, `MS-ENG-04`, `MS-ENG-05`, `MS-ENG-07`, `MS-ENG-08`, `MS-ENG-11`, `MS-UX-05`, `MS-UX-01`).

Update (2026-03-05, strict contract repass):
- Strict run `20260305-193049` failed at phase 5 (`pkg-e1`):
  - Tier A: `p95=25 ms`, `p99=34 ms`, `jank=6.56%`
  - Tier B: `p95=25 ms`, `p99=34 ms`, `jank=6.74%`
- Focused runtime repass implemented:
  - interaction deactivation grace window before deferred overlay flush,
  - stronger interaction cadence floors for OGN/ADS-B/weather/front-order,
  - live display-pose cadence reduction and mode-change card-prepare throttling.
- Strict run `20260305-195205` still failed at phase 5 (`pkg-e1`):
  - Tier A: `p95=25 ms`, `p99=34 ms`, `jank=6.39%`
  - Tier B: `p95=24 ms`, `p99=34 ms`, `jank=6.21%`
- Phase-2 lanes remain green (`pkg-d1`, `pkg-g1`, `pkg-w1`), and phase-3 blocker remains isolated to `MS-UX-01`.
- Time-boxed deviation recorded: `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` issue `RULES-20260305-12`.
- Deferred-fix decision (2026-03-05): `MS-UX-01` final optimization is postponed to a follow-up cycle; strict-green completion remains blocked until `pkg-e1` phase-5 passes.
