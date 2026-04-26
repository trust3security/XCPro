# Live Ownship Smoothness Phased IP

## 0) Metadata

- Title: Live ownship smoothness phased implementation plan
- Owner: XCPro Team
- Date: 2026-04-21
- Issue/PR: TBD
- Status: Phase 0 diagnostics implemented; Phase 1A debug cadence bridge
  selected for shared live/Condor display proof before production/provider
  decisions. The synthetic thermal map FAB reference path has since been
  removed, so future evidence uses controlled replay captures and live device
  captures instead.

## 1) Scope

- Problem statement: Live flying ownship motion can look stepped when upstream
  live cadence/quality plus live display gating/smoothing are not aligned with
  the icon renderer's visual needs.
- Why now: live turns should be visually continuous without corrupting
  flight-data truth, replay determinism, or battery/performance budgets.
- In scope:
  - Measure live cadence, controlled replay cadence, frame dispatch, pose
    updates, and gating.
  - Improve live GPS request cadence only inside the existing live sensor owners.
  - Add or tune live display-only resampling/prediction inside `feature:map-runtime`.
  - Tune live display frame gating/cadence with MapScreen SLO evidence.
  - Add unit, replay, and performance evidence for every runtime phase.
- Out of scope:
  - Writing smoothed/predicted display positions back to `FlightDataRepository`.
  - Adding task, glide, or navigation-derived fields to `CompleteFlightData`.
  - Changing generic replay behavior unless a regression test proves no
    determinism loss.
  - Broad map overlay refactors unrelated to ownship motion.
  - Making a new external GNSS/fused-provider dependency in Phase 0.
- User-visible impact: live ownship turns should look visually continuous while
  stale/poor live GPS remains honest and safely degraded.
- Rule class touched: Invariant for SSOT/timebase/replay boundaries; Default
  for cadence and display smoothing policy.

## 1A) Confirmed Boundaries / Verified Facts

| Fact | Source of Truth | Why It Matters |
|---|---|---|
| `FlightDataRepository` is the fused-flight-data SSOT and source-gates active samples. | `feature/flight-runtime/src/main/java/com/trust3/xcpro/flightdata/FlightDataRepository.kt`; `docs/ARCHITECTURE/ARCHITECTURE.md` | Live smoothness must not create a second flight-data authority. |
| Map position comes from `FlightDataRepository`; display smoothing is UI-only. | `docs/ARCHITECTURE/CONTRIBUTING.md`; `docs/ARCHITECTURE/PIPELINE.md` | Any interpolation belongs after the repository read path, not in the repository. |
| The former synthetic thermal map FAB reference path has been removed. | `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenReplayCoordinator.kt`; `feature/map/src/main/java/com/trust3/xcpro/map/components/MapActionButtons.kt` | Future live smoothness proof must not depend on deleted map actions or deleted synthetic replay builders. |
| Controlled replay still uses replay cadence, replay timestamps, and replay densification. | `feature/igc/src/main/java/com/trust3/xcpro/replay/IgcReplayModels.kt`; `feature/map/src/main/java/com/trust3/xcpro/replay/ReplaySessionPrep.kt` | Replay can still provide deterministic comparison evidence without a dedicated thermal FAB. |
| Replay emission is cadence-driven by replay timestamps and `gpsStepMs`. | `feature/map/src/main/java/com/trust3/xcpro/replay/ReplaySampleEmitter.kt`; `feature/map/src/main/java/com/trust3/xcpro/replay/IgcReplayControllerRuntimePlayback.kt` | Replay can sustain a clean 100 ms input cadence independent of Android GNSS delivery. |
| Live GPS requests currently use slow 1000 ms and fast 200 ms intervals. | `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt` | Live smoothness cannot be judged unless actual provider delivery and display resampling are measured. |
| Live fast GPS is selected only while `FlyingState.isFlying`; replay remains slow. | `feature/map/src/main/java/com/trust3/xcpro/vario/GpsCadencePolicy.kt` | Takeoff/flying-state latency can delay the live fast-cadence path. |
| Android GPS interval is clamped to a 200 ms minimum in the current adapter. | `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt` | A 100 ms experiment must deliberately change the owner and prove platform support. |
| Live `GPSData` carries monotonic provider time plus accuracy/bearing/speed quality. | `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt`; `feature/flight-runtime/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt` | Display smoothing must use monotonic time and quality gates, not wall time. |
| MapScreen observes repository GPS and maps it to `MapLocationUiModel`. | `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt`; `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelMappers.kt` | ViewModel remains a mapper/orchestrator, not a smoothing owner. |
| Replay display frames target about 60 Hz; live display frames target about 40 Hz. | `feature/map-runtime/src/main/java/com/trust3/xcpro/map/DisplayPoseRenderCadence.kt` | Controlled replay and live do not currently use identical render cadence. |
| Replay display dispatch is always active; live display dispatch is activity-gated by a settle window. | `feature/map-runtime/src/main/java/com/trust3/xcpro/map/DisplayPoseFrameActivityGate.kt` | Live may stop rendering before sparse GPS updates become visually continuous. |
| `DisplayPoseSmoother` is already the visual-only smoothing/dead-reckoning seam. | `feature/map-runtime/src/main/java/com/trust3/xcpro/map/DisplayPoseSmoother.kt` | Future visual improvements should reuse or split this map-runtime owner. |
| Map camera/overlay updates can be skipped by no-op and pixel/angle diff gates. | `feature/map-runtime/src/main/java/com/trust3/xcpro/map/DisplayPoseFrameDiffPolicy.kt`; `feature/map/src/main/java/com/trust3/xcpro/map/MapLocationFilter.kt` | Smooth input can still look stepped if downstream gates suppress too much. |
| The icon overlay is a render sink, not a motion engine. | `feature/map-runtime/src/main/java/com/trust3/xcpro/map/BlueLocationOverlay.kt`; `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapPositionController.kt` | Renderer changes should be last, after cadence and pose evidence. |
| MapScreen `MS-UX-01` requires p95 frame time <= 16.7 ms, p99 <= 24 ms, jank <= 5%. | `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md` | Live smoothness needs SLO proof, not only subjective visual review. |
| `KNOWN_DEVIATIONS.md` lists `RULES-20260305-12` for `MS-UX-01` with expiry 2026-04-15. | `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | As of 2026-04-21 the entry is expired and must be closed or renewed before relying on it. |
| Official Android APIs allow requesting update intervals/min intervals, but actual provider delivery must be measured. | https://developer.android.com/reference/android/location/LocationManager ; https://developer.android.com/reference/android/location/LocationRequest.Builder ; https://developers.google.com/android/reference/com/google/android/gms/location/LocationRequest.Builder | Platform request cadence is not proof of actual live cadence. |

## 1B) Actual Assumptions / Defaults Chosen

| Assumption / Default | Why It Is Not Discoverable | Impact If Wrong | Follow-up / Owner |
|---|---|---|---|
| None for Phase 0 diagnostics. | N/A | N/A | N/A |

## 1C) Unresolved Decisions / Questions

| Decision / Question | Why It Matters | Owner / Decision Maker | Blocking? | Resolution Plan |
|---|---|---|---|---|
| Should production live request 100 ms, keep 200 ms, or adapt by speed/turn rate? | It controls battery, provider load, and actual fix cadence. | XCPro Team | Blocks Phase 1 production default. | Phase 0/1 device evidence, then decide. |
| Should XCPro use current `LocationManager`, API 31+ `LocationRequest`, Play services fused location, or external GNSS for the high-rate path? | It changes dependencies, permissions, testing, and provider semantics. | XCPro Team | Blocks provider migration only. | Compare actual cadence/quality in Phase 1; ADR if dependency/wiring changes. |
| Which physical Tier A and Tier B devices close the live-ownship SLO gate? | Emulator-only evidence is not enough for pilot UX. | XCPro Team | Blocks Phase 4 closure. | Name device set before implementation promotion. |
| What ownship-specific SLI thresholds are acceptable beyond `MS-UX-01`? | General frame-time SLOs do not fully capture live fix gaps/freezes. | XCPro Team | Blocks Phase 4 closure. | Use proposed SLIs in this plan, adjust after baseline. |

## 1D) Phase 1A Debug Cadence Bridge

- Apply a debug-only `CADENCE_BRIDGE` display smoothing profile before changing
  live provider cadence or renderer behavior.
- Keep authoritative GPS speed/position unchanged; the bridge extends only the
  map-runtime live display active window and bounded visual prediction horizon.
- Release keeps the existing `SMOOTH` profile until Condor and phone-live
  evidence proves the debug profile is safe to promote.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Authoritative fused flight sample | `FlightDataRepository` | `StateFlow<CompleteFlightData?>` | Smoothed display pose in repositories, ViewModels, or `CompleteFlightData`. |
| Live GPS request policy | `VarioServiceManager` plus `GpsCadencePolicy` and selected sensor adapter | Runtime cadence request to sensor layer | UI-controlled GPS cadence or ad hoc provider calls from map UI. |
| Raw live GPS provider sample | `SensorRegistry` / sensor source adapter | `GPSData` with monotonic/provider quality fields | Synthetic live fixes inserted upstream of repository for visual reasons. |
| Display pose | `feature:map-runtime` display pose pipeline/smoother | Render snapshot to camera and blue location overlay | Display pose copied back into domain, repository, or navigation calculations. |
| MapScreen visual diagnostics | Map/runtime diagnostics owners in debug builds | Aggregate counters/timers/artifacts | Per-frame production logs or coordinate-bearing history retained as telemetry. |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Live GPS cadence metrics | New debug diagnostics owner near live sensor/runtime adapter | GPS callback boundary only | Debug artifact/export path | Provider callback timestamps and quality flags | None | Source change, service stop, app restart, explicit capture reset | Monotonic provider time / monotonic capture time | Unit tests for bucket math and redaction; smoke evidence on device. |
| Display frame/gate metrics | Existing or extended map-runtime diagnostics | Display pose frame loop/gate owner | MapScreen evidence artifact | Frame timestamps, gate decisions, render update decisions | None | Map lifecycle restart, source mode change, capture reset | Monotonic frame time | Unit tests for counts; SLO artifact validation. |
| Live visual resampler history | New `LiveDisplayPoseResampler` or split under `DisplayPoseSmoother` in `feature:map-runtime` | Raw live fix ingestion and display frame ticks | `DisplayPosePipeline` render pose path | Last accepted live fixes, speed/bearing/accuracy, display clock | None | Source mode change, replay start, profile change, large gap, poor quality | Monotonic live display clock | Fake-clock unit tests for turn continuity, stale gaps, accuracy degradation, replay bypass. |
| Feature/tuning flags | Existing `MapFeatureFlags` or DI/config owner | Build/config initialization only | Runtime constructor/config injection | Build type and approved config | None unless existing settings owner is reused | App restart/config rebuild | N/A | Config tests or constructor tests. |

### 2.2 Dependency Direction

Dependency flow remains:

`Sensor adapter -> flight runtime/repository SSOT -> ViewModel mapper -> Compose effects -> map-runtime display pose -> renderer`

- Modules/files touched:
  - Phase 0: diagnostics-only files under `feature:map-runtime`, possibly
    sensor/runtime diagnostics in the existing live sensor owner, plus tests.
  - Phase 1: `VarioServiceManager`, `GpsCadencePolicy`, `SensorRegistry`, or
    a dedicated provider adapter if approved.
  - Phase 2/3: `DisplayPoseSmoother` or new map-runtime resampler, display
    frame gate/cadence/diff-policy files, plus tests.
- Boundary risk:
  - High if smoothing moves upstream of `FlightDataRepository`.
  - Medium if provider diagnostics require a new cross-module contract.
  - Low if display-only resampling stays internal to `feature:map-runtime`.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/DisplayPoseSmoother.kt` | Existing display-only smoothing/dead-reckoning seam. | Keep prediction visual-only and quality-gated. | Split into a focused live resampler only if the file would become mixed-purpose. |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/DisplayPoseFrameActivityGate.kt` | Existing owner of live/replay frame dispatch decisions. | Keep live activity gating centralized. | Add moving-live quality gates only with tests and diagnostics. |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapRenderSurfaceDiagnostics.kt` | Existing bounded map diagnostics pattern. | Aggregate counters/timers, debug-only artifacts. | Extend only if ownership remains coherent; otherwise add a focused diagnostics class. |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapLocationFlightDataRuntimeBinder.kt` | Existing seam that binds repository samples into map runtime with replay suppression. | Respect live/replay source separation. | No planned deviation. |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Live ownship visual resampling, if added | Partly implicit inside existing display pose smoother/gate | Focused map-runtime visual owner | Isolates live-only display behavior without changing SSOT truth. | Unit tests prove no repository writes and replay bypass/determinism. |
| Live cadence evidence aggregation | Not clearly owned for this specific diagnosis | Focused diagnostics owner at sensor/runtime boundary | Need actual fix cadence evidence separate from requested cadence. | Debug artifact shows requested vs actual intervals without coordinates. |
| Production live provider choice, if changed | Current `SensorRegistry` platform GPS adapter | TBD provider adapter behind existing sensor boundary | Only if evidence proves current provider cannot meet target. | ADR plus provider parity tests. |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| None planned in Phase 0. | N/A | N/A | Phase 0 |
| Any future UI-to-sensor cadence shortcut | Forbidden; not currently planned. | Existing `VarioServiceManager`/sensor owner path. | Phase 1 guardrail |
| Any future renderer-level motion fix | Forbidden as first response. | Upstream evidence, map-runtime display pose owner, then renderer only if proven. | Phase 2/3 guardrail |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/MAPSCREEN/CHANGE_PLAN_LIVE_OWNSHIP_SMOOTHNESS_PHASED_IP_2026-04-21.md` | New | This phased IP. | MapScreen owns visual UX and SLO planning. | Architecture docs define rules, not this slice plan. | No |
| `docs/MAPSCREEN/00_INDEX.md` | Existing | Discoverability link only. | Existing MapScreen doc entrypoint. | New plan should not be hidden. | No |
| `feature/map-runtime/.../LiveDisplayPoseResampler.kt` | New, Phase 2 candidate | Live display-only interpolation/prediction state. | Map-runtime owns visual pose. | UI/ViewModel/repository must not own display prediction. | Yes, if `DisplayPoseSmoother` would exceed focused ownership. |
| `feature/map-runtime/.../DisplayPoseSmoother.kt` | Existing, Phase 2 candidate | Existing smoothing/dead-reckoning policy. | Reuse if the change is a small policy tune. | New file preferred for live-specific resampling complexity. | Split before mixed responsibility. |
| `feature/map-runtime/.../DisplayPoseFrameActivityGate.kt` | Existing, Phase 3 candidate | Live/replay display dispatch gate. | Central frame gate owner. | Renderer should not decide activity gating. | No unless new gate policy grows. |
| `feature/map-runtime/.../DisplayPoseRenderCadence.kt` | Existing, Phase 3 candidate | Display frame interval constants/policy. | Central cadence owner. | Sensor layer owns fix cadence, not render cadence. | No |
| `feature/map-runtime/.../DisplayPoseFrameDiffPolicy.kt` | Existing, Phase 3 candidate | No-op render diff gate. | Central skip policy owner. | Camera/overlay sinks should remain dumb. | No |
| `feature/map-runtime/.../MapRenderSurfaceDiagnostics.kt` or focused diagnostics file | Existing/new, Phase 0 candidate | Aggregate visual frame/gate/render metrics. | Existing diagnostics pattern is here. | Avoid raw production logs and UI state. | Split if live-ownship metrics are too specific. |
| `feature/map/src/main/java/com/trust3/xcpro/map/diagnostics/DebugDiagnosticsFileExporter.kt` | New, Phase 0B | Debug-only aggregate diagnostics file export. | `feature:map` owns Android context/file I/O for MapScreen evidence plumbing. | `feature:map-runtime` should produce diagnostics lines, not write files. | No |
| `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt` | Existing, Phase 1 candidate | Requested GPS interval policy application. | Existing live cadence request owner. | UI and ViewModel must not request provider cadence directly. | No |
| `feature/map/src/main/java/com/trust3/xcpro/vario/GpsCadencePolicy.kt` | Existing, Phase 1 candidate | Cadence selection by replay/live/flying state. | Existing policy owner. | Sensor adapter should apply, not decide product policy. | No |
| `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt` | Existing, Phase 1 candidate | Platform location request and actual callback edge. | Existing Android provider adapter. | Repository and UI should not know provider API details. | Split provider adapter if API migration is approved. |
| `feature/map-runtime/src/test/...` | New/existing tests | Fake-clock display pose/gate/resampler coverage. | Map-runtime behavior is deterministic and testable. | Device tests alone are too late and flaky. | Add focused tests. |
| `feature/map/src/test/...` | New/existing tests | GPS cadence policy and replay/live wiring coverage. | Cadence policy currently lives in map feature. | Map-runtime should not know flight-mode policy. | Add focused tests. |
| `docs/ARCHITECTURE/PIPELINE.md` | Existing, later phase only | Pipeline documentation if wiring changes. | Required by AGENTS documentation sync rules. | Not needed for Phase 0 diagnostics-only. | No |
| `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` | Existing, governance only | Deviation closure/renewal if SLO remains missed. | Only authorized deviation ledger. | Do not hide SLO misses in plan notes. | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| Live ownship diagnostics snapshot | Diagnostics owner near map-runtime/sensor boundary | QA evidence scripts / debug tooling | Internal/debug where possible | Compare controlled replay and live actual cadence/render behavior. | Remove or keep debug-only after SLO closure. |
| Live display pose resampler input/output | `feature:map-runtime` | `DisplayPosePipeline` / render coordinator | Internal module API preferred | Isolate visual-only live prediction from repository truth. | Feature flag rollback; no public API unless unavoidable. |
| Provider high-rate request API, if added | Sensor adapter owner | `VarioServiceManager`/sensor registry | Existing sensor boundary | Only if current Android GPS request cannot meet evidence target. | ADR required if dependency or public boundary changes. |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| No new long-lived scope planned for Phase 0/2. | Diagnostics/resampler should run on existing callbacks/frame loop. | Existing owner dispatcher/frame callback. | Existing lifecycle reset. | Avoid hidden runtime owners. |
| Provider migration scope, if needed | Only if a new provider API requires callback lifecycle ownership. | Existing sensor/service dispatcher by default. | Sensor/service stop and source change. | Must reuse existing sensor lifecycle unless ADR proves otherwise. |

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| None planned for Phase 0. | N/A | N/A | N/A | N/A | N/A |
| Provider compatibility bridge, if introduced. | Sensor adapter owner | Support current and migrated provider during evidence phase. | Single selected provider path. | Phase 1 evidence and ADR decision. | Provider parity tests and device smoke evidence. |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| Requested live GPS cadence | `VarioServiceManager.kt` plus `GpsCadencePolicy.kt` | Sensor adapter | Existing cadence policy/application seam. | No |
| Platform minimum location request interval | `SensorRegistry.kt` or new provider adapter | Sensor adapter only | Platform edge owns API constraints. | No |
| Live display prediction window/dead-reckon limit | `DisplayPoseSmoother.kt` or new `LiveDisplayPoseResampler.kt` | Display pose pipeline | Visual pose owner, downstream of SSOT. | No |
| Live/replay render interval | `DisplayPoseRenderCadence.kt` | Compose display frame effect | Central render cadence owner. | No |
| Frame gate settle window | `DisplayPoseFrameActivityGate.kt` | Display frame effect | Central live/replay dispatch gate owner. | No |
| Live-ownship SLI thresholds | This plan, then SLO matrix if accepted | QA evidence scripts | SLO docs own user-visible proof. | Temporary during Phase 0 only. |

### 2.2I Stateless Object / Singleton Boundary

| Object / Holder | Why `object` / Singleton Is Needed | Mutable State? | Why It Is Non-Authoritative | Why Not DI-Scoped Instance? | Guardrail / Test |
|---|---|---|---|---|---|
| None planned. | N/A | N/A | N/A | N/A | N/A |

If a later phase proposes a new Kotlin `object`, that phase must prove it has
no hidden mutable state and cannot become a service locator or silent fallback.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Live GPS callback intervals | Monotonic provider/capture time | Avoid wall-clock jumps in cadence metrics. |
| Live display frame deltas | Monotonic frame time | Matches Compose frame loop and display clock. |
| Live resampler prediction horizon | Monotonic live display clock | Prediction is display-only and must not compare wall time. |
| Replay sample intervals | Replay/IGC sample time | Replay determinism depends on replay time. |
| Artifact run timestamp | Wall | Metadata only, not used for runtime math. |

Explicitly forbidden comparisons:

- Monotonic vs wall.
- Replay vs wall.
- Live provider elapsed time vs replay sample time.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Sensor callbacks stay in the existing sensor/runtime owner.
  - Display pose work stays on the existing frame/render path.
  - Heavy artifact aggregation must avoid the frame hot path.
- Primary cadence/gating sensor:
  - Phase 0 measures actual GPS fix intervals, repository GPS updates,
    display frame dispatch, gate rejections, diff-policy skips, and overlay
    updates.
  - Phase 1 changes requested GPS cadence only if Phase 0 proves a gap.
  - Phase 2/3 tune display cadence/gating only after raw input evidence exists.
- Hot-path latency budget:
  - Must not regress `MS-UX-01`.
  - Live-ownship diagnostics must be aggregate counters/timers, not per-frame
    string logs.

### 2.4A Logging and Observability Contract

| Boundary / Callsite | Logger Path (`AppLogger` / Platform Edge) | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| Live GPS cadence diagnostics | Existing diagnostics artifact path or `AppLogger` if needed | High if coordinates are logged | Store intervals/accuracy buckets only; no coordinates in release logs | Remove or keep debug-only after closure. |
| Display frame/gate diagnostics | Existing map diagnostics path | Low if no location fields | Aggregate counts/durations only | Keep if useful for SLO package; otherwise remove. |
| Provider experiment logs | Platform edge via approved logging seam | Medium | Build/debug gated; no raw location values | Remove before production default unless retained by diagnostics policy. |

### 2.5 Replay Determinism

- Deterministic for same input: Yes, required.
- Randomness used: No new runtime randomness allowed. The retired synthetic
  thermal replay path is not an active runtime reference.
- Replay/live divergence rules:
  - Replay keeps replay time, replay cadence, and existing replay display mode.
  - Live resampling, if added, applies only to live display pose.
  - Phase 0 diagnostics must report live and replay separately.
  - Any change touching replay display path requires repeat-run replay evidence.

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| Live provider cannot deliver requested 100/200 ms cadence | Degraded | Sensor/cadence diagnostics owner | Motion may remain stepped, but no false confidence | Record actual cadence; keep current stable behavior | Device evidence plus unit tests for metrics buckets. |
| Poor speed/bearing/accuracy quality | Degraded | Display pose smoother/resampler | More damping or raw-fix anchoring; no aggressive prediction | Disable/shorten prediction until quality recovers | Fake-clock quality-gate tests. |
| Long GPS gap while moving | Degraded | Display pose smoother/resampler | Reanchor or hold honestly; avoid wild extrapolation | Stop prediction past configured limit | Stale-gap tests. |
| Source changes live/replay | Recoverable | Display pose pipeline/gate | Reset pose history, no jump from stale mode | Clear resampler and gate state | Source-switch tests. |
| Diagnostics artifact incomplete | Recoverable | QA/evidence scripts | Phase cannot close | Reject run and recapture | Artifact validation tests/scripts. |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| Diagnostics run ID | QA evidence script/debug tooling | Wall metadata plus stable run naming | No for runtime; yes for artifact references | Artifact system owns run identity. |
| Live cadence sample bucket | Diagnostics owner | Monotonic interval | Yes for same captured events | Metrics owner owns aggregation. |
| Display pose sample | Display pose pipeline/resampler | Monotonic display clock | Yes for same input/time sequence | Map-runtime owns visual pose. |

### 2.5C No-Op / Test Wiring Contract

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| None planned. | N/A | N/A | N/A | N/A |

No silent production fallback path should be added. If a provider migration
needs fallback, it must be explicit, logged through approved diagnostics, and
covered by tests.

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Smoothed display pose becomes flight-data truth | SSOT and UI/domain separation | Unit tests + review + `enforceRules` | Resampler/display-pose tests; architecture review. |
| Wall/monotonic/replay time mixed | Timebase rules | Fake-clock tests + review | Display pose/resampler/gate tests. |
| Replay behavior changes | Replay determinism | Repeat-run replay tests | Replay wiring tests and evidence. |
| UI controls sensor cadence directly | Dependency direction | `enforceRules` + review | Cadence policy tests; code review. |
| Hot-path logging or coordinate telemetry | Logging rules / privacy | `enforceRules` + review | Diagnostics tests and logging scan. |
| Frame dispatch duplicates owners | MapScreen `MS-ENG-10` | SLO artifact | MapScreen package evidence. |
| Visual SLO regresses | MapScreen `MS-UX-01` | SLO artifact | `artifacts/mapscreen/...` validation. |

### 2.7 Visual UX SLO Contract

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| General moving map remains smooth under live-ownship changes | `MS-UX-01` | Existing package evidence plus Phase 0 live baseline | p95 <= 16.7 ms, p99 <= 24 ms, jank <= 5% | MapScreen evidence package | Phase 4 |
| No duplicate render-frame owner while tuning live cadence/gate | `MS-ENG-10` | Existing package evidence plus Phase 0 live baseline | duplicate frame-owner count = 0 | MapScreen evidence package | Phase 3/4 |
| Replay scrubbing/playback not regressed if display path touched | `MS-UX-05` | Existing package evidence | No regression and target remains met | Replay evidence package | Phase 3/4 when replay path touched |
| Live GPS actual cadence is known, not assumed | Proposed `LIVE-GPS-01` | Phase 0 baseline | Report p50/p95/p99 actual fix interval; target TBD, candidate p95 <= 250 ms when provider supports it | Debug diagnostics artifact | Phase 0/1 |
| Rendered live ownship does not visibly freeze while moving | Proposed `LIVE-OWN-01` | Phase 0 baseline | Candidate rendered-pose frame gap p95 <= 50 ms and p99 freeze gap <= 150 ms while moving | Map-runtime diagnostics artifact plus screen capture review | Phase 2/4 |
| Live gate does not starve moving display pose | Proposed `LIVE-GATE-01` | Phase 0 baseline | Candidate gate-suppressed moving-frame ratio near 0 after a fresh fix, threshold finalized after baseline | Gate diagnostics | Phase 3/4 |

Proposed `LIVE-*` SLIs are planning targets until Phase 0 captures baseline
data and the team accepts exact thresholds.

## 3) Data Flow (Before -> After)

Before, live:

```text
Android GPS provider
  -> SensorRegistry
  -> UnifiedSensorManager / flight calculator loops
  -> FlightDataRepository (LIVE SSOT)
  -> MapScreenViewModel GPS mapper
  -> MapComposeEffects / LocationManager
  -> DisplayPoseSmoother + live frame gate + diff policy
  -> camera and BlueLocationOverlay
```

Historical synthetic thermal reference, now removed:

```text
map FAB action
  -> retired synthetic replay builder/launcher
  -> replay prep REFERENCE densification
  -> replay display pose and frame dispatch
```

After, intended:

```text
Live GPS provider
  -> existing sensor/runtime owners with measured actual cadence
  -> FlightDataRepository remains the only flight-data SSOT
  -> ViewModel continues mapping raw authoritative GPS to UI model
  -> map-runtime adds/tunes visual-only live resampling and moving-live frame gate
  -> camera and BlueLocationOverlay receive smoother display poses
```

No after-state writes display pose back into the repository, domain, or replay
source.

## 4) Why Controlled Replay Looks Smooth

1. Controlled replay can use deterministic, smooth source geometry, so adjacent
   positions and headings agree.
2. Replay cadence can feed frequent GPS samples into the map.
3. Replay reference mode densifies source points instead of waiting for a real
   Android GNSS provider.
4. Replay display frames are not live-gated in the same way. The replay gate
   dispatches continuously, while live dispatch is activity-window gated.
5. Replay time is internally coherent. Live samples can arrive with provider
   jitter, delayed flying-state fast-cadence activation, and accuracy fields
   that correctly make the smoother more conservative.
6. The blue icon renderer receives already-good pose input. That is why the
   renderer should not be the first place to change.

## 5) Implementation Phases

### Phase 0 - Evidence-Only Baseline

- Goal: Prove the live smoothness gap before changing runtime behavior.
- Implemented diagnostic owners:
  - `feature/map/src/main/java/com/trust3/xcpro/sensors/LiveGpsCadenceDiagnostics.kt`
    owns aggregate phone-GPS cadence/quality evidence.
  - `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapRenderSurfaceDiagnostics.kt`
    owns aggregate raw-fix/frame-gap evidence.
  - `SensorRegistry`, `UnifiedSensorManager`, and `LocationManager` only tap
    existing events into those diagnostics; they do not change runtime behavior.
- Implemented Phase 0B export:
  - `feature/map/src/main/java/com/trust3/xcpro/map/diagnostics/DebugDiagnosticsFileExporter.kt`
    writes the existing compact diagnostics lines to app-private debug storage.
  - Export path for debug builds is
    `files/diagnostics/xcpro-map-diagnostics-latest.txt`, retrievable with
    `adb shell run-as <package> cat files/diagnostics/xcpro-map-diagnostics-latest.txt`.
- Files to change:
  - Add/extend debug diagnostics near map-runtime frame/gate/render owners.
  - Add/extend live GPS actual-cadence diagnostics near the sensor/provider edge.
  - Add tests for metric aggregation and redaction.
- Ownership/file split changes:
  - Diagnostics only; no production state owner changes.
  - No `FlightDataRepository` data model changes.
- Tests to add/update:
  - Metric bucketing/redaction unit tests.
  - Artifact validation script coverage if new artifact fields are added.
- Evidence to capture:
  - Controlled replay run: replay sample interval, display frame interval, gate
    decisions, diff-policy skips, overlay updates.
  - Live run: requested GPS cadence, actual callback interval, repository GPS
    update interval, display frame interval, gate decisions, diff-policy skips,
    overlay updates, accuracy/speed/bearing quality buckets.
- Exit criteria:
  - A report can say exactly whether live is limited by provider cadence,
    repository update cadence, frame gating, diff gating, or render cost.
  - No user-visible behavior changes.
  - No coordinates stored in diagnostics artifacts.

### Phase 1 - Live GPS Cadence Request Experiment

- Goal: Determine whether live can become visually smoother through better
  actual GPS cadence before visual prediction is tuned.
- Files to change:
  - `VarioServiceManager.kt` and `GpsCadencePolicy.kt` only if policy changes.
  - `SensorRegistry.kt` or a focused provider adapter only if request API changes.
  - Tests for cadence policy and provider request construction.
- Ownership/file split changes:
  - Cadence policy remains in existing sensor/runtime owners.
  - UI/ViewModel do not control provider cadence.
- Candidate work:
  - Try 100 ms requested live GPS only behind a debug/experiment flag.
  - Compare current `LocationManager` request behavior against API 31+
    `LocationRequest` or Play services fused location only if approved.
  - Measure actual fix intervals, not just requested intervals.
- Exit criteria:
  - Production default remains unchanged unless battery/performance/actual cadence
    evidence supports the change.
  - If a new provider dependency or public boundary is chosen, create an ADR
    before implementation promotion.

### Phase 2 - Live Display-Only Resampler

- Goal: Make sparse but good live fixes render more continuously by using
  visual-only interpolation/prediction after the repository read path.
- Files to change:
  - New `LiveDisplayPoseResampler.kt` in `feature:map-runtime`, or a focused
    split from `DisplayPoseSmoother.kt`.
  - `DisplayPosePipeline.kt` only for internal wiring if needed.
  - Focused fake-clock tests.
- Ownership/file split changes:
  - New state is display pose state only.
  - No repository/domain/navigation consumers can read resampled positions.
- Required behavior:
  - Use monotonic live display time.
  - Limit prediction horizon.
  - Reanchor on long gaps, source changes, profile changes, or poor accuracy.
  - Respect speed/bearing accuracy and avoid over-predicting uncertain turns.
  - Bypass or preserve replay behavior unless explicitly proven otherwise.
- Exit criteria:
  - Unit tests show smoother turning between 200/500/1000 ms live fixes.
  - Tests show stale/poor-quality inputs degrade safely.
  - Replay repeat-run behavior is unchanged.

### Phase 3 - Moving-Live Frame Gate and Diff Policy Tuning

- Goal: Prevent the display loop from starving visually active live motion while
  preserving idle battery/performance behavior.
- Files to change:
  - `DisplayPoseFrameActivityGate.kt`
  - `DisplayPoseRenderCadence.kt`
  - `DisplayPoseFrameDiffPolicy.kt` or `MapLocationFilter.kt` only if evidence
    proves skip thresholds are suppressing useful live motion.
- Ownership/file split changes:
  - Gate/cadence policy remains centralized in map-runtime.
  - Renderer sinks remain simple.
- Candidate work:
  - Extend live active window while moving and recent GPS quality is acceptable.
  - Consider adaptive live frame interval only during real motion/turning.
  - Avoid 60 Hz live rendering unless metrics justify the cost.
- Exit criteria:
  - `MS-ENG-10` duplicate frame-owner count remains 0.
  - Idle map does not start a permanent hot render loop.
  - Live-ownship frame gaps improve in Phase 0 scenarios.

### Phase 4 - Device SLO Proof and Tuning

- Goal: Prove the improvement on real devices, not only in unit tests or
  controlled replay.
- Files to change:
  - Tuning constants only in canonical owners.
  - QA scripts/artifact readers only if the evidence contract changed.
- Evidence to run:
  - Live baseline vs post-change on agreed Tier A and Tier B physical devices.
  - Controlled replay capture for comparison.
  - MapScreen package evidence for impacted `MS-UX-*` and `MS-ENG-*` IDs.
  - Connected tests if lifecycle/provider behavior changed.
- Exit criteria:
  - `MS-UX-01` passes or an approved time-boxed deviation exists.
  - Proposed `LIVE-*` SLIs pass or thresholds are explicitly revised with
    evidence.
  - No regression in replay if replay display code was touched.

### Phase 5 - Documentation, ADR, and Governance Closure

- Goal: Make the final behavior durable and reviewable.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` if pipeline wiring changes.
  - MapScreen SLO docs if `LIVE-*` SLIs become accepted SLOs.
  - ADR if provider dependency, cross-module API, or durable visual resampler
    contract changes architecture.
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` to close or renew expired
    `RULES-20260305-12`.
- Exit criteria:
  - Architecture docs match implementation.
  - Expired deviation status is resolved.
  - Rollback flag or revert path is documented.

## 6) Test Plan

- Unit tests:
  - `GpsCadencePolicy` for live/replay/flying-state cadence decisions.
  - Provider request construction if `SensorRegistry` or provider adapter changes.
  - Display resampler with fake monotonic clock and synthetic turning paths.
  - Frame gate behavior for moving, idle, stale, source-switch, and poor-quality
    states.
  - Diff-policy thresholds if tuned.
- Replay/regression tests:
  - Removed synthetic thermal map FAB actions stay absent.
  - Replay repeat runs are deterministic for the same input.
  - Replay path does not use live resampler unless explicitly approved/tested.
- UI/instrumentation tests:
  - Connected or device smoke test for live provider lifecycle if provider API
    changes.
  - MapScreen evidence capture for impacted SLOs.
- Degraded/failure-mode tests:
  - Low actual cadence.
  - Poor speed/bearing accuracy.
  - Long GPS gaps.
  - Source switch live to replay and replay to live.
- Boundary tests for removed bypasses:
  - No bypass removal planned in Phase 0.
  - If Phase 1 adds provider abstraction, enforce sensor-boundary usage.
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | Cadence policy and resampler tests. |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | Display pose tests and replay repeat evidence. |
| Persistence / settings / restore | Round-trip / restore / migration tests | Not planned unless a persisted setting is approved. |
| Ownership move / bypass removal / API boundary | Boundary lock tests | Architecture review and targeted tests. |
| UI interaction / lifecycle | UI or instrumentation coverage | Live provider lifecycle and MapScreen evidence if changed. |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | MapScreen package evidence plus live-ownship diagnostics. |

Required checks for implementation phases:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Docs-only Phase 0 planning changes may use lightweight verification such as
`git diff --check`, but runtime phases must run the appropriate Gradle gates.

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Smooth display prediction hides bad live truth. | Pilot may trust a visual pose more than sensor quality supports. | Keep prediction display-only, quality-gated, and bounded; never write to SSOT. | Map-runtime owner |
| Higher GPS cadence increases battery or heat. | Poor field endurance. | Phase 1 experiment behind flag; require battery/performance evidence before default. | Sensor/runtime owner |
| Provider request interval does not improve actual delivery. | Code churn with no UX gain. | Measure actual cadence first; keep fallback/current path. | XCPro Team |
| Poor bearing/speed accuracy creates wrong turn visuals. | Live icon may swing or overshoot. | Accuracy gates, short prediction horizon, reanchor on uncertainty. | Map-runtime owner |
| Frame gate tuning creates idle render loop. | Battery/performance regression. | Moving-live gate only, no-op diff policy retained, `MS-ENG-10` evidence. | Map-runtime owner |
| Replay is accidentally affected. | Loss of deterministic replay/testing. | Replay bypass tests and repeat-run evidence. | Replay/map-runtime owner |
| Expired `MS-UX-01` deviation remains unresolved. | Merge/release governance risk. | Phase 5 must close or renew `RULES-20260305-12`. | XCPro Team |

## 7A) ADR / Durable Decision Record

- ADR required for Phase 0 diagnostics-only work: No.
- ADR required later if:
  - A new location provider dependency is introduced.
  - A durable cross-module provider API is added.
  - The live visual resampler becomes a public or cross-module contract.
  - Map pipeline wiring changes beyond internal map-runtime policy.
- ADR file: TBD using `docs/ARCHITECTURE/ADR_TEMPLATE.md`.
- Decision summary: TBD after Phase 0/1 evidence.
- Why this belongs in an ADR instead of plan notes: provider dependencies,
  cross-module APIs, and long-lived display pipeline contracts affect future
  ownership and replay/sensor guarantees beyond this slice.

## 8) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No discoverable repo/system fact remains listed only as an assumption.
- No duplicate SSOT ownership introduced.
- Display smoothing/resampling remains downstream of `FlightDataRepository`.
- Time base handling is explicit in code and tests.
- Replay behavior remains deterministic.
- Error/degraded-state behavior is explicit and tested where behavior changed.
- Ownership/boundary/public API decisions are captured in an ADR when required.
- Impacted MapScreen visual SLOs pass, or an approved time-boxed deviation is
  recorded.
- Expired `RULES-20260305-12` status is closed or renewed before relying on it.

## 9) Rollback Plan

- What can be reverted independently:
  - Phase 0 diagnostics.
  - Phase 1 cadence request experiment.
  - Phase 2 live resampler feature flag/wiring.
  - Phase 3 gate/cadence tuning.
- Recovery steps if regression is detected:
  1. Disable any experiment/feature flag first.
  2. Revert the smallest phase slice that introduced the regression.
  3. Re-run the impacted unit tests and MapScreen evidence scenario.
  4. Keep diagnostics if they are debug-only and help explain the rollback.
- Hard rollback guardrail:
  - Never "fix" rollback by moving display pose into the repository or changing
    replay truth.

## 10) Plan Quality Rescore

| Area | Score | Notes |
|---|---:|---|
| Fact/assumption separation | 5/5 | Code/docs/platform facts are listed separately from product defaults. |
| SSOT and boundary safety | 5/5 | Repository truth, sensor cadence, and display pose owners remain distinct. |
| Timebase/replay safety | 5/5 | Live monotonic, replay time, and wall metadata are separated. |
| Evidence readiness | 4/5 | Phase 0 defines metrics, but exact `LIVE-*` thresholds need baseline approval. |
| Implementation readiness | 4/5 | Ready for Phase 0; Phase 1+ must wait for evidence and provider/device decisions. |

Verdict: ready to implement Phase 0 diagnostics. Not ready to change live
runtime behavior until Phase 0 evidence identifies the limiting owner.
