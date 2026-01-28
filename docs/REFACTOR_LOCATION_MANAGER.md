# REFACTOR_LOCATION_MANAGER.md

## Purpose
Refactor `LocationManager.kt` to separate concerns, improve testability, and
enforce SSOT + time base rules from `ARCHITECTURE.md` and `CODING_RULES.md`.

This plan is required by:
- `ARCHITECTURE.md` section 14 (Change Safety Requirements)
- `CODING_RULES.md` section 15A (Regression Resistance Rules)

## Scope
**In scope**
- Extract pure logic from `LocationManager.kt` into testable components
- Isolate MapLibre and Android framework usage
- Make time base transitions explicit and verifiable
- Reduce shared mutable state and duplicated flags

**Out of scope**
- Changing app behavior or UX (unless needed to fix a bug)
- Moving SSOT ownership outside repositories
- Redesigning map UI or sensor pipeline

## Progress Update (2026-01-25)
- Phase 1 complete: `MapCameraPolicy` with unit tests.
- Phase 2 complete: `DisplayPoseCoordinator` with time base tests.
- Phase 3 complete: `MapCameraController` adapter for MapLibre.
- Phase 4 complete: `MapUserInteractionController` with state tests.
- Phase 5 complete: `RenderFrameSync` listener isolation.
- New orchestration extracted: `MapTrackingCameraController`,
  `MapCameraUpdateGate`, `MapShiftBiasResetter`, `MapCameraPreferenceReader`.
- User interaction camera access now uses `MapCameraControllerProvider`,
  enabling unit tests for return/recenter flows.
- Frame logging extracted into `DisplayPoseFrameLogger`.
- `MapTrackingCameraController` now depends on `MapViewSizeProvider`
  instead of `MapScreenState`.
- `LocationManager.kt` reduced from 607 lines to 446 lines.

## Current Risks / Drivers
- `LocationManager` mixes UI control, time base handling, sensor inputs,
  and MapLibre calls in one class.
- Hard to unit test due to MapLibre types and Android dependencies.
- Duplicate state writes (ex: `showReturnButton` set twice).
- Several private fields are shared across threads without clear ownership.
- Potential SSOT bypass if raw GPS updates reach UI without repository/use case.

## SSOT Ownership Flow (Target)
Sensors / Replay Sources
 -> Repository (SSOT)
   -> UseCase (derive display values)
     -> ViewModel (UI state)
       -> LocationManager (UI-only display + MapLibre)

`LocationManager` must only consume SSOT-derived data for display and must not
own or derive business state.

## Time Base Rules (Target)
- Live data uses monotonic time for deltas/validity.
- Replay uses IGC timestamps as simulation time.
- No mixing monotonic and wall time in logic.
- Display time must follow the fix time base (live monotonic if present,
  replay IGC time).

## State Machine (Explicit)
**Tracking State**
- `TrackingOn`
- `TrackingOff`

**User Interaction State**
- `ReturnHidden`
- `ReturnVisible`

**Initialization State**
- `NotCentered`
- `Centered`

Transitions:
- First valid fix: `NotCentered -> Centered`
- User pan: `ReturnHidden -> ReturnVisible`, `TrackingOn -> TrackingOff`
- Return to saved: `ReturnVisible -> ReturnHidden`, `TrackingOff -> TrackingOn`
- Recenter: `ReturnVisible -> ReturnHidden` (if appropriate)

These transitions must be enforced through `MapStateActions` only.

## Phased Plan

### Phase 1 - Extract Pure Camera Policy
**Owner:** TBD
**Goal:** Remove camera update decision logic from `LocationManager`.
**Status:** Completed (2026-01-25)

Create a pure Kotlin component (no Android/MapLibre) that:
- Decides if camera should update
- Computes padding with bias
- Contains bearing delta math

Move logic from:
- `shouldUpdateCamera`
- `computeSmoothedPadding`
- `computeBasePadding`
- `computeBiasOffset`
- `applyBiasToPadding`
- `shortestDeltaDegrees`

**Acceptance:**
- `LocationManager` delegates to the new policy object.
- Unit tests cover thresholds, time base, and bias behaviors.

### Phase 2 - Extract Display Pose Coordinator
**Owner:** TBD
**Goal:** Isolate time base handling and pose selection.
**Status:** Completed (2026-01-25)

Create `DisplayPoseCoordinator` (pure Kotlin):
- Owns `DisplayClock` and `DisplayPosePipeline`
- Handles time base switches
- Produces a `DisplayPoseSnapshot`

Move logic from:
- `pushRawFix`
- pose selection portion of `renderDisplayFrame`

**Acceptance:**
- Time base switch resets smoothing and map shift bias.
- Unit tests verify replay timestamps and snapshot validity.

### Phase 3 - MapLibre Adapter
**Owner:** TBD
**Goal:** Test camera orchestration without MapLibre types.
**Status:** Completed (2026-01-25)

Create an interface:
```
interface MapCameraController {
  val cameraPosition: MapCameraPositionSnapshot
  fun moveCamera(position: MapCameraPositionSnapshot)
  fun animateCamera(position: MapCameraPositionSnapshot, durationMs: Int)
  fun setPadding(left: Int, top: Int, right: Int, bottom: Int)
}
```
Add a MapLibre-backed implementation in UI layer.

**Acceptance:**
- `LocationManager` uses the adapter.
- No MapLibre types in pure logic modules.

### Phase 4 - Extract User Interaction Controller
**Owner:** TBD
**Goal:** Centralize user interaction state transitions.
**Status:** Completed (2026-01-25)

Create `MapUserInteractionController` that:
- Handles `saveLocation`, `showReturnButton`,
  `returnToSavedLocation`, `recenterOnCurrentLocation`,
  `handleUserInteraction`
- Removes duplicated assignments to `showReturnButton`

**Acceptance:**
- Single state transition path via `MapStateActions`.
- Behavior unchanged in UI flows.
- Camera access is injected via `MapCameraControllerProvider` to keep
  tests independent of MapLibre types.

### Phase 5 - Render Frame Sync Isolation
**Owner:** TBD
**Goal:** Move render frame listener and thread checks out of `LocationManager`.
**Status:** Completed (2026-01-25)

Create `RenderFrameSync`:
- Owns `MapView.OnWillStartRenderingFrameListener`
- Emits a callback to render a frame

**Acceptance:**
- `LocationManager` no longer owns MapView listener details.

### Phase 6 - Final Orchestration Cleanup
**Owner:** TBD
**Goal:** Make `LocationManager` a thin coordinator.
**Status:** In progress (2026-01-25)

Completed in this phase:
- Camera tracking and initial centering moved to `MapTrackingCameraController`.
- Time base switch resets now delegate to the tracking controller.
- Camera gating is now owned by `MapCameraUpdateGate`.
- Frame logging moved to `DisplayPoseFrameLogger`.
- Map size access is now injected via `MapViewSizeProvider`.

Remaining:
- Continue reducing `LocationManager` responsibilities where practical.
- Consider extracting frame logging/debug reporting if it grows further.

`LocationManager` should:
- Receive SSOT-derived data only
- Delegate to extracted components
- Avoid direct mutable state where possible

**Acceptance:**
- No direct business logic in `LocationManager`.
- Thread ownership is explicit and documented.

## Testing Plan
Unit tests (pure Kotlin):
- Camera policy thresholds and bearing delta
- Padding + bias output for different modes and speeds
- Time base switching behavior in pose coordinator
- Snapshot validity after first fix
- Tracking camera orchestration (initial centering + time base switch)

Optional integration tests:
- Replay run verifies camera tracking in replay mode
- Initial centering + return/recenter behavior

## Risks and Mitigations
- **Behavior drift:** keep a replay test to compare camera updates.
- **Time base bugs:** add tests for live vs replay transition and timing.
- **UI regressions:** keep changes isolated and incremental by phase.

## Notes
- Avoid non-ASCII characters per `CODING_RULES.md` section 14.
- Follow package structure rules when creating new classes.
