# Map View Smoothness Release-Grade Phased IP

## 0) Metadata

- Title: Reduce map-view jerk and hot-path inefficiency in live/replay tracking
- Owner: Codex
- Date: 2026-03-15
- Issue/PR: TBD
- Status: Phases 1-4 implemented; Phase 5 manual smoke and close-out pending
- Execution rules:
  - Scope freeze: this plan covers only follow-camera cadence, hot collector narrowing, aircraft overlay idempotence, and render-frame sync redesign.
  - No ad hoc cleanup, rename-only churn, gesture rewrites, or unrelated map feature work.
  - Preserve MVVM + UDF + SSOT layering and replay determinism.
  - Treat the full 4-part plan as required. Phase 1 alone is not considered a complete smoothness fix because the current problem is both over-animation and hot-state fanout.
  - Land one phase at a time with independent rollback and evidence.

## 1) Scope

Implementation status:
- Phase 1 complete: continuous follow camera motion now routes through `MapFollowCameraMotionPolicy` and uses direct move in the hot follow path.
- Phase 2 complete: hot orientation/location/zoom collectors moved out of `MapScreenRoot` into `MapScreenHotPathBindings` / `MapScreenHotPathEffects`.
- Phase 3 complete: `BlueLocationOverlay` now owns steady-state no-op decisions and `MapPositionController` no longer forces visibility every frame.
- Phase 4 complete: render sync is now event-driven; `MapComposeEffects` does not own the frame loop when `useRenderFrameSync` is enabled, and `LocationManager` requests repaint from raw-fix/orientation events instead of a perpetual timer path.
- Phase 5 remaining: manual smoke and final close-out only.

- Problem statement:
  - The current follow-camera path still uses long SDK camera animations in a hot update loop.
  - High-frequency orientation, zoom, and GPS-derived state is still collected too high in the UI tree and pushed through a wide root binding path.
  - The aircraft overlay path still performs avoidable hot-loop work by re-checking readiness and mutating visibility on every accepted update.
  - `useRenderFrameSync` still relies on a timer-driven frame loop that can force repainting instead of only responding to real render cadence.
- Why now:
  - This is the next professional-quality improvement on the active map path after the ownership seams already completed in map shell, profile/card, forecast/weather runtime, and IGC repository work.
  - The current issues sit directly on the tracking/render path, so they can manifest as visible jerk, roughness, and avoidable battery/CPU cost.
  - The highest-value fix is not just removing camera animation. The full 4-part plan is intended to fix both over-animation and hot-state fanout.
- In scope:
  - Follow-camera motion policy in the live/replay hot path.
  - Root/binding/effect placement for high-frequency map orientation and GPS collectors.
  - Aircraft overlay update idempotence and readiness/visibility handling.
  - Render-frame sync ownership if the feature flag remains enabled.
- Out of scope:
  - Broad gesture-system redesign.
  - Product-level changes to map behavior, glider bias policy, or replay semantics.
  - Unrelated manager/controller cleanup.
  - New architecture tracks outside the affected map/view runtime.
- User-visible impact:
  - Smoother live/replay follow behavior.
  - Lower map roughness under mixed GPS/orientation load.
  - Fewer unnecessary map/overlay mutations on the hot path.
  - No intended functional change to tracking mode semantics or map features.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Raw live/replay pose stream | existing display-pose pipeline owners (`DisplayPoseCoordinator` / `DisplayPosePipeline`) | runtime callbacks and snapshot accessors | UI-owned copies of authoritative pose state |
| Continuous follow camera motion policy | dedicated runtime camera motion policy introduced by this plan | pure decision API consumed by `MapTrackingCameraController` | duplicated "live vs replay vs discrete" motion rules in UI or controller callsites |
| Map follow state (`isTrackingLocation`, return button visibility, padding preference inputs) | `MapStateStore` through `MapStateReader` / `MapStateActions` | state flows and action methods | shadow tracking flags in Compose runtime state |
| Orientation stream authority | existing `MapOrientationManager` | orientation flow | broad root-level mirrors or duplicate throttling in `MapScreenRoot.kt` |
| GPS/map location UI state | existing `MapScreenViewModel` + `MapStateStore` outputs | narrow effect/binding readers near runtime boundary | root-level duplicated map location state |
| Aircraft overlay runtime state (last rendered location/rotation/visibility) | `BlueLocationOverlay` | imperative runtime adapter methods | visibility/readiness mirrors outside the overlay owner |
| Render-frame callback ownership | `RenderFrameSync` and the runtime frame consumer introduced/narrowed by this plan | frame callback boundary only | parallel timer loop forcing repaint while render sync is enabled |

### 2.1A State Contract (Mandatory for new or changed state)

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Follow-camera motion mode (`continuous move` vs `discrete animate`) | new runtime camera motion policy | `MapTrackingCameraController` only | `MapTrackingCameraController.updateCamera(...)` | feature flags, replay/live mode, explicit transition type | none | reset when tracking mode/time base changes | Monotonic / Replay | camera policy unit tests, replay parity tests |
| Hot orientation runtime feed | `MapOrientationManager` | existing sensor/live-data inputs | narrow effect host near runtime wiring | sensor/live flight data | none | existing orientation reset rules | Monotonic / Replay | collector placement tests, recomposition evidence |
| Hot map location/runtime feed | existing VM/state owners | existing runtime and VM mutators only | narrow effect host near runtime wiring | GPS/replay/display pose | existing owners | existing location clear/reset rules | Monotonic / Replay | collector placement tests, live/replay smoke |
| Aircraft overlay visible flag | `BlueLocationOverlay` | `setVisible(...)` only | overlay runtime | explicit show/hide events only | none | cleanup/style loss | Not time-based | overlay idempotence tests |
| Aircraft overlay rendered pose cache | `BlueLocationOverlay` | `updateLocation(...)` only | overlay runtime only | latest accepted overlay update | none | cleanup/style reset | Monotonic / Replay | overlay no-op and update tests |
| Render-frame dispatch enablement | existing feature flag + `RenderFrameSync` | feature-flag/config owners | runtime frame driver | feature flag | config owner | unbind or feature disabled | Monotonic / Render frame | render-sync unit tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> runtime adapters / effects -> data/runtime owners`

- Modules/files touched:
  - `feature:map-runtime`
  - `feature:map`
  - documentation under `docs/refactor`
- Any boundary risk:
  - do not move business/domain logic into Compose effect hosts
  - do not make `MapScreenRoot.kt` a second runtime state owner
  - do not let render sync introduce a second frame clock beside the display-pose pipeline

### 2.2A Reference Pattern Check (Mandatory)

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/map/DisplayPoseRenderCoordinator.kt` | already acts as a narrow runtime owner over a hot render loop | keep hot-path decisions in focused runtime owners rather than broad UI hosts | add a focused camera motion policy instead of embedding more hot-path branching in the coordinator |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootStateBindings.kt` | already narrows root ownership into focused binding helpers | move high-frequency collection into focused binding/effect hosts instead of `MapScreenRoot.kt` | add hot-path-specific effect/binding helpers because current root still collects too much |

### 2.2B Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Continuous follow camera motion decision | `MapTrackingCameraController.kt` / `MapPositionController.kt` inline logic | focused runtime motion policy | keep one canonical owner for hot follow motion semantics | policy tests + tracking smoke |
| High-frequency orientation collection | `MapScreenRoot.kt` and `rememberMapRuntimeController(...)` helper path | focused hot-path effect/binding host in `ui/effects` | root and its runtime-controller helper should not recompose on every orientation sample | recomposition evidence + wiring tests |
| High-frequency map location / zoom collection used only by runtime/effects | wide `MapScreenBindingGroups.kt` path | focused hot-path binding/effect host | narrow root fanout and reduce broad recomposition pressure | binding/effect tests + recomposition evidence |
| Aircraft overlay readiness and visible-state churn | `MapPositionController.kt` calling broad overlay methods every frame + `BlueLocationOverlay.kt` self-heal on every update | idempotent overlay owner with explicit readiness/visibility behavior | prevent avoidable hot-loop work and repeated style mutations | overlay tests + debug trace |
| Render-driven display frame dispatch | `MapComposeEffects.DisplayPoseEffects` timer loop plus `LocationManager.onDisplayFrame()` forced repaint path | render-frame callback owner and narrow runtime frame pump | if render sync stays, it must be render-driven rather than timer-forced, while preserving replay/snail-trail frame delivery | render-sync tests + frame trace |

### 2.2C Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapTrackingCameraController.kt` | direct inline selection of long animation for continuous follow | dedicated follow camera motion policy with explicit continuous/discrete rules | Phase 1 |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapPositionController.kt` | unconditional `setBlueLocationVisible(true)` on every overlay update | overlay visibility updates only on state transitions | Phase 3 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` | direct root-level collection of hot orientation state | focused effect/binding host near runtime surface | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindingGroups.kt` | wide binding object carries hot location/zoom data through the root path | focused hot-path binding group used only where needed | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt` | root helper path still consumes hot orientation inputs through `rememberMapRuntimeController(...)` | focused hot-path effect host owns runtime camera effect wiring | Phase 2 |
| `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt` | perpetual frame loop calls `onDisplayFrame()` even when render sync is enabled | render-frame-driven dispatch path | Phase 4 |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/LocationManager.kt` | `triggerRepaint()` forced from display loop when render sync is enabled | runtime frame consumer that only renders on real frame callbacks | Phase 4 |

### 2.2D File Ownership Plan (Mandatory)

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/map/LocationManager.kt` | Existing | runtime orchestration only; no long follow-loop animation policy and no timer-driven repaint forcing | already owns location/render runtime wiring | keep as coordinator, not policy bucket | Yes |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapTrackingCameraController.kt` | Existing | tracking-camera application using a narrow motion policy | already owns follow-camera application | should consume policy, not embed broader behavior matrix | Yes |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapPositionController.kt` | Existing | camera application and overlay handoff only | already owns accepted camera/overlay application | should stop owning overlay visibility churn policy | Yes |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapFollowCameraMotionPolicy.kt` | New | canonical owner for continuous-follow vs discrete-transition camera motion rules | runtime-only hot-path policy | too hot-path-specific for UI or generic feature flags | New file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt` | Existing | low-frequency screen assembler only | already the route root | should stop collecting hot runtime state | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt` | Existing | low-frequency helper wiring only after the split; no hot orientation-driven runtime effect ownership | already owns route helper composition | the hot camera-effect path should move out rather than keep piggybacking on a root helper | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindingGroups.kt` | Existing | low-frequency grouped bindings only after split | existing binding grouping file | hot-path bindings need a narrower owner | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt` | Existing | stable shared map effects only; no forced frame loop when render sync is enabled | existing effect hub | split hot-path effect ownership before extending | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapScreenHotPathEffects.kt` | New | high-frequency orientation/GPS/render-frame effect ownership near runtime boundary | keeps hot collectors out of `MapScreenRoot.kt` | too runtime-adjacent for broad root binding files | New file |
| `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenHotPathBindings.kt` | New | focused binding readers for hot location/zoom/runtime-only state | prevents wide root binding fanout | avoids bloating `MapScreenBindingGroups.kt` again | New file |
| `feature/map/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt` | Existing | single owner of aircraft overlay runtime state and idempotent visible/update behavior | already owns the overlay runtime objects | should stay the authoritative overlay owner | Yes |
| `feature/map/src/main/java/com/example/xcpro/map/RenderFrameSync.kt` | Existing | render-frame callback owner and enablement gate | already owns MapView render binding | should remain the single render-sync callback surface | Yes |

### 2.2E Module and API Surface (Mandatory when boundaries change)

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `MapFollowCameraMotionPolicy` | `feature:map-runtime` | `MapTrackingCameraController` | internal | canonical hot-path camera motion policy | permanent runtime owner if Phase 1 lands |
| `MapScreenHotPathEffects` | `feature:map` UI effects | `MapScreenRoot` / compose effect wiring | internal | isolate high-frequency collectors from root | permanent replacement for root-level hot collectors |
| `MapScreenHotPathBindings` | `feature:map` UI | `MapScreenHotPathEffects` | internal | narrow hot location/zoom reads without widening root bindings | permanent if it keeps root fanout narrow |
| narrowed render-frame delivery contract for replay/snail-trail | existing runtime/UI seam | `LocationManager`, `MapScreenRuntimeEffects`, `SnailTrailManager` | internal | preserve frame-synced replay behavior while removing repaint forcing | compatibility preserved across Phase 4 |

### 2.2H Canonical Formula / Policy Owner (Mandatory when math/constants/policy are touched)

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| continuous follow camera motion policy | `MapFollowCameraMotionPolicy.kt` | `MapTrackingCameraController.kt` | hot-path motion semantics should have one runtime owner | No |
| overlay idempotence thresholds / no-op checks | `BlueLocationOverlay.kt` | `MapPositionController.kt` via overlay port | overlay owner should decide whether a visible/update call is a no-op | No |
| render-frame dispatch gating | `RenderFrameSync.kt` | `LocationManager.kt`, compose/runtime frame wiring | render callback ownership belongs at the render binding boundary | No |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| continuous live follow cadence | Monotonic | driven by render/update cadence and local gating |
| replay follow cadence | Replay + monotonic scheduling boundary | replay semantics must remain deterministic while rendering on real frames |
| orientation sampling/render dispatch | Monotonic / render frame | tied to frame delivery and sensor update cadence |
| overlay update cache | Monotonic / replay-derived | reflects accepted rendered frames only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - `Main`: map camera application, Compose effect collection, render-frame callback delivery, overlay mutation
  - existing background sensor/data owners remain unchanged
- Primary cadence/gating sensor:
  - render frame cadence and accepted display pose updates
- Hot-path latency budget:
  - no overlapping camera animations in continuous follow
  - no broad root recomposition on orientation/GPS ticks
  - no per-frame overlay readiness/visibility churn when state is unchanged

### 2.4A Logging and Observability Contract (Mandatory when logging changes touch hot paths, privacy-sensitive data, or platform edges)

| Boundary / Callsite | Logger Path (`AppLogger` / Platform Edge) | Sensitive Data Risk | Gating / Redaction | Temporary Removal Plan |
|---|---|---|---|---|
| camera follow hot loop | platform debug logging only where already used | moderate location/bearing data | keep debug-only and reduce cadence if hot-loop logs distort profiling | remove or further gate if traces stay noisy |
| overlay update hot loop | `AppLogger` | moderate location data | keep rate-limited debug only | further reduce after Phase 3 if needed |
| render-frame sync path | no new per-frame logs by default | low | trace only in focused profiling builds | strip after perf validation |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - replay follow remains driven by replay pose/time base
  - Phase 1 may change camera application mechanics, but not replay target pose semantics
  - Phase 4 may narrow frame dispatch ownership, but must not change replay-selected pose or duplicate frame ownership

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| continuous follow still animates in hot loop | `CODING_RULES.md` hot-path ownership / perf discipline | unit test + review | new camera motion policy tests |
| root still collects hot orientation/GPS state | SSOT / narrow-owner rules | review + recomposition evidence | `MapScreenRoot.kt`, `MapScreenHotPathEffects.kt` |
| overlay still mutates visibility every frame | narrow-owner / idempotence expectation | unit test + review | `BlueLocationOverlay` tests |
| render sync still forces repaint from a timer loop | single-owner cadence rule | unit test + review | `RenderFrameSync` and `LocationManager` tests |
| replay/snail-trail frame delivery regresses while render sync is narrowed | replay determinism and cadence ownership rules | regression tests | `MapScreenRuntimeEffects`, `SnailTrailManager`, replay tests |
| replay semantics drift while changing camera/frame wiring | replay determinism rules | regression tests | replay-follow tests and smoke evidence |

### 2.7 Visual UX SLO Contract (Mandatory for map/overlay/replay interaction changes)

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| live follow path no longer rubber-bands under steady GPS/orientation updates | `MS-UX-01` | existing map smoothness baseline | no overlapping follow animation and no visible snapback | manual follow smoke + trace | Phase 1 |
| root recomposition pressure materially narrows on hot orientation/GPS ticks | `MS-ENG-09` | current root recomposition profile | root recompositions materially below baseline on hot-path updates | recomposition harness/evidence | Phase 2 |
| overlay update path stops redundant visible/style churn | `MS-ENG-08` | current overlay trace | steady-state redundant visibility/readiness writes reduced to zero | overlay debug trace + tests | Phase 3 |
| render sync does not force extra repaint when enabled | `MS-ENG-10` | current timer-driven frame path | render-driven dispatch only; duplicate frame-owner count remains `0` | frame trace + unit tests | Phase 4 |

## 3) Data Flow (Before -> After)

Before:

```text
DisplayPose pipeline
  -> MapComposeEffects.DisplayPoseEffects while(frame)
    -> LocationManager.onDisplayFrame()
      -> triggerRepaint() when render sync enabled
      -> DisplayPoseRenderCoordinator
        -> MapTrackingCameraController (long animation in hot follow path)
        -> MapPositionController
          -> BlueLocationOverlay.update + setVisible(true) every frame

MapOrientationManager / map location / zoom
  -> MapScreenRoot
    -> broad bindings
      -> runtime/effects consumers
```

After:

```text
DisplayPose pipeline
  -> render-driven or direct frame owner
    -> DisplayPoseRenderCoordinator
      -> MapFollowCameraMotionPolicy
        -> MapTrackingCameraController (continuous move, discrete animate)
      -> MapPositionController
        -> BlueLocationOverlay (idempotent update/visibility)

MapOrientationManager / hot location / zoom
  -> focused hot-path bindings/effects near runtime boundary
    -> runtime/effects consumers

MapScreenRoot
  -> low-frequency screen assembly only
```

The full 4-part plan is required to reach the after-state. Phase 1 improves one major jerk source, but Phase 2 is required to address the hot-state fanout that can still make the map feel rough.

## 4) Implementation Phases

### Phase 0 - Baseline and Seam Lock

- Goal:
  - lock the performance seam, capture baseline evidence, and freeze non-goals before code changes
- Files to change:
  - `docs/refactor/Map_Smoothness_IP_2026-03-15.md`
  - evidence artifacts under `artifacts/mapscreen/smoothness/phase0/<timestamp>/`
- Ownership/file split changes in this phase:
  - none in production code
- Tests to add/update:
  - none before baseline capture
  - define focused smoke scripts for live follow, replay follow, mixed-load pan/zoom, and overlay visibility traces
- Exit criteria:
  - baseline traces and manual observations captured
  - exact phase boundaries documented
  - no production code changes mixed into planning

### Phase 1 - Remove Long Camera Animation from the Follow Loop

- Goal:
  - stop using long SDK camera animations for continuous follow updates while preserving animated discrete transitions
- Files to change:
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/LocationManager.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapTrackingCameraController.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapPositionController.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapFollowCameraMotionPolicy.kt`
  - affected camera/runtime tests
- Ownership/file split changes in this phase:
  - move continuous-follow motion rules into `MapFollowCameraMotionPolicy.kt`
  - keep `MapTrackingCameraController.kt` as the caller that applies the chosen motion mode
  - continuous live/replay follow should use direct move or a zero-duration path
  - animated transitions remain only for explicit discrete actions such as return/recenter/task-edit jumps if already intended
- Tests to add/update:
  - policy tests for live follow, replay follow, and discrete transition cases
  - regression tests proving replay follow remains deterministic
  - smoke path for steady live tracking and replay tracking
- Exit criteria:
  - no long animation remains in the continuous follow loop
  - continuous follow motion rules have one canonical owner
  - `MS-UX-01` evidence shows no regression and reduced follow jitter

### Phase 2 - Move High-Frequency Orientation/GPS Collectors out of `MapScreenRoot`

- Goal:
  - stop pushing hot orientation/GPS/zoom/location state through `MapScreenRoot.kt` and the wide binding path
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindingGroups.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapScreenHotPathEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenHotPathBindings.kt`
  - affected UI/runtime binding tests
- Ownership/file split changes in this phase:
  - `MapScreenRoot.kt` becomes low-frequency screen assembly only
  - `MapScreenRootHelpers.kt` stops owning the hot `MapCameraEffects` path through `rememberMapRuntimeController(...)`
  - hot orientation collection moves into `MapScreenHotPathEffects.kt`
  - hot location/zoom reads move into `MapScreenHotPathBindings.kt`
  - `MapScreenBindingGroups.kt` retains only low-frequency grouped bindings
- Tests to add/update:
  - binding/effect tests proving hot collectors are no longer rooted in `MapScreenRoot.kt`
  - recomposition evidence or harness updates for root fanout
  - smoke on live/replay follow after collector relocation
- Exit criteria:
  - `MapScreenRoot.kt` no longer collects hot orientation flow directly
  - hot location/zoom runtime state no longer rides the broad root binding object
  - `MS-ENG-09` evidence shows meaningful root recomposition narrowing

### Phase 3 - Make the Aircraft Overlay Path Idempotent

- Goal:
  - stop mutating overlay visibility/readiness/style state on every accepted update when nothing actually changed
- Files to change:
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/MapPositionController.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt`
  - affected overlay/port tests
- Ownership/file split changes in this phase:
  - `BlueLocationOverlay.kt` becomes the sole decider for whether visibility/update calls are no-ops
  - hot-loop readiness/self-heal checks move off the per-update path where possible
  - `MapPositionController.kt` stops calling `setBlueLocationVisible(true)` every frame as a safety blanket
- Tests to add/update:
  - overlay tests for repeated `setVisible(true)` no-op behavior
  - overlay tests for unchanged location/rotation update no-op behavior
  - style/reset tests to prove explicit recovery still works
- Exit criteria:
  - overlay visibility is only mutated on real state transitions
  - readiness/self-heal is no longer performed on every accepted overlay update
  - `MS-ENG-08` trace shows redundant overlay churn removed

### Phase 4 - Redesign `useRenderFrameSync` so it is not Timer-Driven Repaint Forcing

- Goal:
  - if render-frame sync stays, make it render-driven rather than a perpetual frame loop that forces repaint
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt`
  - `feature/map-runtime/src/main/java/com/example/xcpro/map/LocationManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/RenderFrameSync.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapScreenReplayCoordinator.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapReplaySnapshotControllers.kt`
  - any minimal runtime binding files needed to connect the render callback
  - affected render-sync tests
- Ownership/file split changes in this phase:
  - `RenderFrameSync.kt` remains the render callback owner
  - the runtime render consumer responds to actual render frames instead of forcing them from the Compose timer loop
  - remove duplicate cadence ownership between `MapComposeEffects` and `RenderFrameSync`
  - preserve current replay feature-flag snapshot/restore behavior and snail-trail display-pose delivery semantics while narrowing the frame owner
- Tests to add/update:
  - render-sync tests proving no forced repaint path remains when sync is enabled
  - regression tests for disabled-sync behavior
  - regression tests for replay feature-flag snapshot/restore around render sync
  - regression tests for snail-trail display-pose updates with render sync on/off
  - mixed live/replay smoke with render sync on
- Exit criteria:
  - no timer-driven `triggerRepaint()` forcing remains in the enabled render-sync path
  - render sync has one callback owner only
  - replay/snail-trail frame delivery semantics remain correct under both render-sync modes
  - `MS-ENG-10` evidence passes

### Phase 5 - Release Hardening and Close-Out

- Goal:
  - prove the seam is safe, update durable docs, and stop refactoring this slice
- Files to change:
  - `docs/refactor/Map_Smoothness_IP_2026-03-15.md`
  - `docs/ARCHITECTURE/PIPELINE.md` if runtime flow changed materially
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` only if an approved temporary exception is required
- Ownership/file split changes in this phase:
  - none new; close-out only
- Tests to add/update:
  - none unless stabilization finds a gap
  - manual smoke checklist for live follow, replay follow, map pan/zoom under load, and overlay visibility after style/lifecycle changes
- Exit criteria:
  - required checks pass
  - manual smoke completed
  - no stale deviation or stale plan status remains

## 5) Test Plan

- Unit tests:
  - camera motion policy tests
  - hot-path binding/effect placement tests
  - overlay idempotence tests
  - render-sync ownership tests
- Replay/regression tests:
  - replay follow parity
  - replay render-sync behavior when enabled
- UI/instrumentation tests (if needed):
  - live follow and replay follow smoke on device/emulator
  - style/lifecycle recovery smoke for aircraft overlay
- Degraded/failure-mode tests:
  - map style unavailable/rebind path for overlay recovery
  - render sync disabled/enabled toggling
- Boundary tests for removed bypasses:
  - root no longer directly owns hot collectors
  - follow-loop motion no longer comes from ad hoc inline animation decisions
  - render sync no longer depends on timer-driven repaint forcing
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | camera motion policy tests |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | replay follow tests, render-sync tests |
| Persistence / settings / restore | Round-trip / restore / migration tests | not applicable unless hidden settings surface changes |
| Ownership move / bypass removal / API boundary | Boundary lock tests | hot-path binding/effect tests, overlay idempotence tests |
| UI interaction / lifecycle | UI or instrumentation coverage | live/replay smoke, style/lifecycle overlay smoke |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | baseline/phase traces under `artifacts/mapscreen/smoothness/...` |

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
| Phase 1 improves camera cadence but root hot-state fanout still makes the map feel rough | High | keep Phase 2 as a required phase, not optional cleanup | Codex |
| render-sync redesign changes replay behavior unintentionally | High | keep replay determinism explicit and gate with replay regression tests | Codex |
| overlay idempotence accidentally suppresses legitimate style recovery | High | keep explicit recovery/style reset tests and separate steady-state no-op logic from lifecycle recovery | Codex |
| root/binding narrowing turns into a broad UI rewrite | Medium | limit Phase 2 to hot collectors only and keep low-frequency route assembly in place | Codex |
| profiling signal is distorted by hot-loop debug logging | Medium | note debug-only distortion and capture evidence consistently | Codex |
| plan drifts into gesture-system redesign | Medium | keep gestures explicitly out of scope for this IP | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file: n/a
- Decision summary:
  - plan-only work at this stage
  - use the phased IP and close-out docs unless implementation changes a durable cross-module architecture decision
- Why this belongs in an ADR instead of plan notes:
  - not required yet; no new durable cross-module policy is being introduced by the plan alone

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- The full 4-part plan remains explicit: camera cadence, hot collector narrowing, overlay idempotence, and render-driven frame sync
- `MapScreenRoot.kt` no longer owns high-frequency orientation/GPS collection after Phase 2
- Continuous follow no longer uses long SDK camera animation after Phase 1
- Aircraft overlay steady-state updates are idempotent after Phase 3
- If `useRenderFrameSync` remains enabled, its dispatch path is render-driven and single-owner after Phase 4
- Replay behavior remains deterministic
- Impacted map-screen SLOs pass, or an approved deviation is recorded in `KNOWN_DEVIATIONS.md` with issue, owner, and expiry

## 8) Rollback Plan

- What can be reverted independently:
  - Phase 1 follow-camera cadence work
  - Phase 2 hot collector narrowing
  - Phase 3 overlay idempotence work
  - Phase 4 render-sync redesign
- Recovery steps if regression is detected:
  - revert only the last completed phase
  - preserve tests and evidence artifacts that still match the architecture contract
  - rerun `./gradlew enforceRules`, `./gradlew testDebugUnitTest`, and `./gradlew assembleDebug`
  - keep rollback evidence under `artifacts/mapscreen/smoothness/rollback/<issue-id>/`
