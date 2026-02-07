> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# RacingTask_Refactor.md

Purpose: Plan the multi-phase refactor to implement Racing task navigation
in a safe, architecture-compliant way. This is the execution plan and
single source of truth for the work.

Scope: Racing task navigation only (start/turn/finish transitions, state
ownership, events, UI wiring). AAT is out of scope. Full FAI start/finish
constraints are out of scope for now.

Non-goals (explicit for Phase 1-3):
- No enforcement of FAI start/finish limits (time/height/speed).
- No new task validation rules beyond existing validator.
- No UI redesign beyond small status indicators and event hooks.

References:
- docs/02_Tasks/racingtask.md
- docs/02_Tasks/Racing_XCSoar_Navigation.md
- docs/02_Tasks/Racing_Tasks.md
- docs/02_Tasks/Task_Type_Separation.md
- ARCHITECTURE.md
- CODING_RULES.md

----------------------------------------------------------------------
1) Architecture constraints that govern this work

These are hard constraints and shape the design:
- Zero cross-contamination between Racing and AAT modules.
- Navigation logic must be testable without Android framework.
- No business logic in Compose UI.
- State has exactly one owner (SSOT).
- Use monotonic/replay time for transitions, not wall time.
- No "xcsoar" string in production Kotlin code.

Implication:
- Racing navigation code stays inside racing package.
- A task-agnostic controller may route events, but cannot calculate geometry.
- GPS update handling must live outside Compose.

Time-base enforcement rule (hard):
- All navigation timestamps use GPSData.timeForCalculationsMillis.
- No System.currentTimeMillis in navigation, controller, or tests.
- Add a test that fails if wall time leaks into navigation event timestamps.

----------------------------------------------------------------------
2) Outcome definition (target behavior)

Minimal behavior (Phase 1):
- Start is detected on exit from start OZ.
  - Start line: exit from line sector (prev inside, current outside).
    - transition constraint: both fixes inside the start circle (line length / 2)
  - Start cylinder/sector: inside -> outside.
  - Start time = previous fix time (last in-sector fix).
- Turnpoints advance on entry into the OZ.
- Finish is detected on entry into the finish OZ.
- All transitions use last GPS vs current GPS.

Additional behavior (Phase 2-3):
- Hysteresis and debounce to avoid jitter.
- Event pipeline for UI and optional audio cues.
- Clear states: PENDING/ACTIVE/COMPLETED/FINISHED.

Navigation state machine (explicit):
States:
- PENDING_START
- STARTED (transient, emitted on start crossing)
- IN_PROGRESS
- FINISHED
- INVALIDATED

Transitions:
- PENDING_START -> STARTED: start line/sector/cylinder crossing detected.
- STARTED -> IN_PROGRESS: next fix after start (auto-normalized).
- IN_PROGRESS -> IN_PROGRESS: no zone transition.
- IN_PROGRESS -> FINISHED: finish line/cylinder crossing detected.
- Any -> INVALIDATED: (future) rule violation such as skipped TP or time gate.

State invariants:
- currentLegIndex is always within 0..lastIndex.
- lastTransitionTimeMillis uses GPSData.timeForCalculationsMillis.
- lastFix timestamp uses GPSData.timeForCalculationsMillis.

----------------------------------------------------------------------
3) Ownership and data flow (SSOT)

Source of GPS:
- FlightDataRepository (SSOT) -> FlightDataUiAdapter (MapScreenObservers; bridge).

Navigation state owner:
- Racing navigation state is owned in Racing module.
- Coordinator is a router only.
- UI reads state from ViewModel or state store only.

Proposed flow:
FlightDataRepository -> TaskNavigationController -> RacingNavigationEngine
-> RacingNavigationStateStore -> ViewModel/UI

SSOT ownership diagram (explicit):

  Sensors / Replay
        |
        v
  FlightDataRepository (SSOT)
        |
        v
  TaskNavigationController
        |
        v
  RacingNavigationEngine (pure)
        |
        v
  RacingNavigationStateStore (SSOT for navigation)
        |
        v
  ViewModel -> UI (read only)

----------------------------------------------------------------------
4) Phase plan

Phase 0: Baseline and guardrails
Deliverables:
- Add this document.
- Confirm build passes with current task code.
- Add preflight checks for separation (rg/grep).
- Confirm time base for GPS samples (monotonic or replay).

Phase 1: Pure navigation engine (core transitions)
Deliverables:
- RacingNavigationEngine (pure, no Android).
- RacingZoneDetector (context-aware OZ checks).
- RacingNavigationState and RacingNavigationEvent types.
- Unit tests for:
  - Start line exit (prev inside, now outside) with transition constraint.
  - Cylinder entry/exit transitions.
  - FAI quadrant orientation and entry.
  - Keyhole cylinder vs sector entry.
- No UI wiring yet.

Phase 2: SSOT wiring
Deliverables:
- RacingNavigationStateStore (StateFlow).
- TaskNavigationController that consumes a Flow<RacingNavigationFix>.
- Map-layer adapter converts FlightDataRepository -> RacingNavigationFix.
- Coordinator routing for Racing only (no AAT logic).
- Feature flag or safe switch to disable auto-advance.
- Racing-specific AdvanceState to gate auto-advance.
- bind() returns a Job; callers must cancel on rebind to avoid duplicate collectors.
- Integration tests: controller + flow inputs (no Android).

Phase 3: UX integration + stability
Deliverables:
- Hysteresis/debounce (N consecutive samples).
- Navigation events consumed by UI for:
  - Active waypoint indicator.
  - Turnpoint reached toast/event.
  - Task started/finished state display.
- Manual advance remains available and disarms auto.

Phase 3.5: Replay visual alignment (XCSoar parity)
Goal:
- During TAS replay, ensure the blue glider marker uses the same raw fixes
  that drive navigation events (no visual smoothing/prediction in replay).

Why:
- Navigation uses raw GPS fixes (exit transitions). Display smoothing can
  lag the marker behind the raw fix, making the start event appear early.
- XCSoar renders the live/replay position from the same Basic() stream.

Deliverables:
- Map display pose mode (UI-only):
  - `SMOOTHED` (default)
  - `RAW_REPLAY` (raw fix pose, no prediction/low-pass)
- `MapStateStore` owns display pose mode (SSOT for UI-only behavior).
- `MapScreenViewModel` switches mode to `RAW_REPLAY` only during replay
  (racing replay and/or demo replay, gated by a feature flag).
- `LocationManager` uses display pose mode:
  - `SMOOTHED`: existing `DisplayPoseSmoother` path
  - `RAW_REPLAY`: render marker from last raw fix directly (no prediction)
- No changes to navigation logic or repositories.

Time base rules:
- Raw replay pose uses replay fix timestamps (IGC time), consistent with
  `DisplayClock` replay time base.
- Live mode remains unchanged (monotonic/wall rules preserved).

Tests:
- Pure unit test for a new `DisplayPoseSelector` (or equivalent) that:
  - returns raw pose when mode=RAW_REPLAY
  - returns smoothed pose when mode=SMOOTHED
- Replay validation test remains the acceptance contract for event timing.

Non-goals:
- No change to task scoring logic.
- No change to sensor fusion or FlightDataRepository.
- No UI/business logic mixing; this is display-only.

Phase 4: Validation and replay verification
Deliverables:
- Replay test cases to validate transitions.
- Deterministic replay builder that generates a replay log from the active task
  (debug-only) and feeds IgcReplayController.
- Debug overlays (optional) to show inside/outside state.
- Clean logging (no tight-loop logs in release).

Phase 5: Extended FAI compliance (later, separate plan)
Deliverables:
- Start/finish time/height/speed constraints.
- Task legality validation (min separation, triangle rules).

----------------------------------------------------------------------
5) File and module impact map (initial target)

New (Racing-only):
- feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/
  - RacingNavigationEngine.kt
  - RacingZoneDetector.kt
  - RacingNavigationState.kt
  - RacingNavigationEvent.kt
  - RacingTransitionDetector.kt (if needed)

New (map/task integration):
- feature/map/src/main/java/com/example/xcpro/map/TaskNavigationController.kt
  - Subscribes to FlightDataRepository
  - Uses RacingNavigationEngine only when TaskType.RACING

Existing (update):
- feature/map/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt
  - Add routing hooks for navigation events.
- feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt
  - Wire TaskNavigationController lifecycle only.
- feature/map/src/main/java/com/example/xcpro/tasks/TaskRepository.kt
  - Consume navigation state if needed for UI.

----------------------------------------------------------------------
6) Risk checklist and mitigation

Risk: Cross-contamination between Racing and AAT
Mitigation:
- Keep navigation code in racing package only.
- No shared geometry utilities or models.
- Run separation checks pre-commit.

Risk: Incorrect time base
Mitigation:
- Use GPSData.timeForCalculationsMillis.
- No System.currentTimeMillis in transitions.

Risk: UI logic drift
Mitigation:
- UI only renders state.
- Navigation decisions made in engine.

Risk: GPS jitter causing false triggers
Mitigation:
- Phase 2 hysteresis/debounce.
- Optional distance gating.

Risk: Duplicate collectors on rebind
Mitigation:
- Cancel the prior bind Job before rebind.

----------------------------------------------------------------------
7) Test plan (non-negotiable)

Unit tests (pure JVM):
- Line crossing intersection (enter and exit scenarios).
- Sector entry based on bisector rules.
- Keyhole entry with cylinder and sector paths.
- Start exit vs turnpoint entry distinction.

Replay validation:
- Use deterministic replay builder for the active task.
- Verify events fire in correct order and with exact timestamps.

Deterministic replay spec (example for 3-waypoint task):
- Start time: 2025-01-01T00:00:00Z (1735689600000)
- Step size: 2000 ms
- Expected events:
  - START at t0 + 2000 ms
  - TURNPOINT at t0 + 6000 ms
  - FINISH at t0 + 10000 ms
Note: start time uses the previous fix time (last in-sector fix), not
interpolated crossing time.

Time-base enforcement tests (must add):
- A unit test that injects GPSData.timeForCalculationsMillis and verifies
  navigation events use that timestamp (not wall time).
- A unit test that fails if System.currentTimeMillis is used for decisions
  or event timestamps in navigation logic.

Review red flags (reject changes):
- Business logic in UI code.
- Shared geometry utilities across task types.
- Wall time used in navigation or scoring logic.
- Duplicate state owners for the same data.
- Racing task logic depending on AAT types.

----------------------------------------------------------------------
8) Definition of done (Phase 1-3)

- Racing transitions match rules in Racing_XCSoar_Navigation.md.
- Navigation decisions are pure and testable.
- No UI-side business logic.
- No "xcsoar" string in Kotlin production code.
- No Racing/AAT cross imports.
- Auto-advance gated by user arming.

----------------------------------------------------------------------
9) Rollout strategy

- Feature flag: enable new navigation engine only when stable.
- Keep manual advance intact throughout.
- Release in increments: Phase 1 -> Phase 2 -> Phase 3.

----------------------------------------------------------------------
10) Open questions (to answer before Phase 2)

- Where should TaskNavigationController live (map package vs tasks package)?
- Should TaskAdvanceState stay generic or be duplicated for Racing?
- How should we represent "task invalidated" in UI (for skipped TP)?

Decisions (2026-01-15):
- TaskNavigationController will live in tasks/ (router only, no geometry).
- Racing will use a Racing-specific AdvanceState to preserve separation.

----------------------------------------------------------------------
10A) Phase 2 Genius-grade criteria (must hold)

- Exactly one owner for navigation state (StateStore only).
- GPS -> RacingNavigationFix conversion happens in one place only.
- TaskNavigationController accepts Flow<RacingNavigationFix> (no Android types).
- Auto-advance gated by RacingAdvanceState and disarmed by manual advance.
- Feature flag can disable auto-advance without code changes.
- Controller bind returns a Job and rebind cancels the prior collector.
- Integration tests cover state transitions and timestamp usage.

----------------------------------------------------------------------
11) Time-base enforcement rule (hard rule)

All navigation logic must use GPSData.timeForCalculationsMillis for
timestamps and transition decisions. Wall time is forbidden for any
navigation decision or event timestamp. This is enforced by tests.

----------------------------------------------------------------------
12) Deterministic replay spec (initial table)

Provide a small, explicit table for replay verification. Example format:

| Replay sample timestamp | Position (lat, lon) | Expected event |
|-------------------------|--------------------|----------------|
| 000100 ms               | 37.123, -122.456   | None           |
| 000200 ms               | 37.124, -122.457   | Start exit     |
| 000350 ms               | 37.130, -122.460   | TP1 entry      |
| 000500 ms               | 37.140, -122.470   | Finish entry   |

This table becomes the acceptance contract for replay-based validation.

Fixture used in tests and debug replay:
- feature/map/src/test/resources/replay/racing-task-basic.igc
- app/src/main/assets/replay/racing-task-basic.igc

Expected sequence for racing-task-basic.igc (relative to first B record):
| T+0000 ms | Start exit |
| T+4000 ms | TP1 entry  |
| T+6000 ms | Finish entry |

----------------------------------------------------------------------
13) Navigation state machine (explicit)

States:
- PENDING_START
- STARTED
- IN_PROGRESS
- FINISHED
- INVALIDATED

Transitions (high level):
- PENDING_START -> STARTED on start exit transition.
- STARTED -> IN_PROGRESS after start completes and leg 1 is active.
- IN_PROGRESS -> FINISHED on finish entry transition.
- Any state -> INVALIDATED on invalid order or manual invalidation.

Notes:
- Start line uses line sector exit; start cylinder/sector uses exit.
- Turnpoints advance on entry when state is STARTED or IN_PROGRESS.

----------------------------------------------------------------------
14) Red flags (review rejection)

Reject changes if any are true:
- Business logic lives in Compose UI.
- Shared utilities or models appear across task types.
- Wall time is used for navigation decisions or timestamps.
- Navigation state is owned by more than one layer.
- State machines are implied rather than explicit in docs.

