# racingtask.md

**Purpose**: Single source of truth for how XCPro creates and runs Racing tasks, how the UI maps to the Racing task model, and how that model aligns with FAI Racing task rules.

**Scope**: Racing task creation, geometry, distance calculation, and validation. This doc does NOT cover AAT or DHT tasks.

---

## 1) What we are trying to do (product + UX)

### User flow (current)
1. Navigation Drawer -> Add Task
2. Bottom sheet opens (Manage / Rules / Files / ...)
3. Manage tab (Racing) lets user enter:
   - Start waypoint
   - One or more turnpoints (ordered)
   - Finish waypoint
4. For each point, user can select the Racing point type (start/turn/finish) and set its parameters (gate width, keyhole params, etc.).

### Desired behavior
- Racing task is a fixed, ordered course: START -> TP1 -> TP2 -> ... -> FINISH.
- The pilot must enter each observation zone in order (no flexible areas).
- Distance is computed using the optimal FAI-compliant path that touches the observation zone boundary (not just center-to-center).
- Map display and distance calculation must match.

---

## 2) Current Racing task model in XCPro

**Core types**:
- `RacingWaypoint` (role = START / TURNPOINT / FINISH)
- `RacingStartPointType` = Start Line | Start Cylinder | FAI Start Sector
- `RacingTurnPointType` = Cylinder | FAI Quadrant | Keyhole
- `RacingFinishPointType` = Finish Cylinder | Finish Line

**Default sizes (standardized)**
- Start: 10 km
- Finish: 3 km
- Turnpoint cylinder: 0.5 km
- Keyhole: inner cylinder 0.5 km, outer sector radius 10 km
- FAI Quadrant: finite radius (default 10 km, XCSoar parity)

Source of defaults: `RacingWaypoint.createWithStandardizedDefaults()`

---

## 3) How the geometry is computed

XCPro uses specialized calculators for each zone type:
- `CylinderCalculator`
- `FAIQuadrantCalculator`
- `KeyholeCalculator`
- `StartLineCalculator`

`RacingTaskCalculator.findOptimalFAIPath()` builds a path that touches each zone boundary in the optimal place (FAI style).
This path is used for:
- Total task distance
- Course line display
- Distance-to-next-entry computations

**Important invariant**: visual course line and distance computation must use the same geometry/entry points.

---

## 4) FAI Racing task compliance: what is covered vs missing

### What we already align with
- Fixed, ordered waypoints (Racing task model enforces sequence).
- Supported FAI observation zone shapes:
  - Cylinder
  - 90-degree sector (FAI Quadrant / FAI Start Sector)
  - Keyhole (sector + cylinder)
- Default sizes match typical FAI competition defaults.
- Distance uses optimal boundary touch points instead of center-to-center.

### What is NOT fully enforced yet
These are required for strict FAI compliance but are not fully validated or enforced today:
- Start/finish rules (speed limits, height limits, start gate time window).
- Validation of sector orientation (FAI sector must be oriented by bisector of inbound/outbound legs).
- Minimum distance / separation between turnpoints.
- Task shape rules (e.g., FAI triangle requirements, min leg percentages).
- Confirmation that the selected observation zone parameters are legal per competition rules.

### Bottom line
The UI flow (start + turnpoints + finish) is necessary but not sufficient for full FAI compliance. We need explicit validation and rule enforcement inside `RacingTaskValidator` (or a new FAI compliance layer) to guarantee FAI-legal tasks.

---

## 5) What to add for full compliance (planned)

### Validation rules to implement
- Minimum number of points and required roles
- Turnpoint separation >= FAI minimums
- Start/finish gates (open time, height/speed limits)
- Correct sector orientation (FAI quadrant bisector rules)
- FAI triangle shape checks when applicable

### Navigation / scoring behavior
- Task is valid only if turnpoints are entered in order
- Mark skipped turnpoints and invalidate task
- Use entry/exit transitions to detect start/finish per rule settings

---

## 6) References in code

- Task model: `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingTask.kt`
- Waypoint defaults: `feature/map/src/main/java/com/example/xcpro/tasks/racing/models/RacingWaypoint.kt`
- Distance & path: `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskCalculator.kt`
- Validation: `feature/map/src/main/java/com/example/xcpro/tasks/racing/RacingTaskValidator.kt`
- UI selectors: `feature/map/src/main/java/com/example/xcpro/tasks/racing/ui/`
- Task creation flow: `feature/map/src/main/java/com/example/xcpro/screens/task/`

---

## 7) Current answer to: "Does this comply with FAI Racing task rules?"

**Partially.**
We have the correct task structure and observation zone types, and we compute distance using FAI-style boundary touch points. However, we do not yet enforce all FAI start/finish constraints, sector orientation validation, or minimum separation rules. Full compliance requires those validation checks to be implemented.

---

## 8) XCSoar-style navigation behavior (what we will implement now)

XCSoar uses transition-based task logic. We will mirror that:

- **Start**: task starts on **exit** from the start observation zone.
  - For a start line, this is an **exit from the line sector**:
    - previous fix inside line sector, current fix outside
    - transition constraint: both fixes inside the start circle (line length / 2)
  - For a start cylinder/sector, this is **inside -> outside** transition.
  - Start time is the **previous fix time** (last in-sector fix).
- **Turnpoints**: advance on **entry** into the observation zone.
- **Finish**: task completes on **entry** into the finish observation zone, after all prior points are achieved.
- **Transitions use last GPS vs current GPS**, not just distance-to-center.

This is the minimal behavior we need for: "start when crossing start line, then advance on each turnpoint entry".

Details: `docs/02_Tasks/Racing_XCSoar_Navigation.md`

---

## 9) Long-term implementation strategy (months of work)

This is a multi-phase plan that keeps UX improving while the core stays correct.

### Phase 0: Guardrails and instrumentation (1-2 weeks)
- Add a dedicated `racing/navigation/` package (no AAT imports).
- Centralize transition logic in `RacingNavigationManager`.
- Add logging + debug overlays (show inside/outside, last/now, active index).
- Keep feature-flagged switches to avoid regressions.

### Phase 1: Core transitions
- Implement **line crossing** for start lines.
- Implement **enter detection** for cylinder/sector/keyhole with TaskContext.
- Wire auto-advance to `TaskAdvanceState` (AUTO + ARMED).
- Emit navigation events: `TaskStarted`, `TurnpointReached`, `TaskFinished`.

### Phase 2: Stability (anti-jitter)
- Hysteresis: require N consecutive inside samples for entry.
- Debounce: avoid flip-flop near boundary.
- Distance gating: reduce checks when far from next OZ.

### Phase 3: UX + safety polish
- UI status bar: active TP, distance, completion state.
- Optional audio/haptic cues for entry/finish.
- Manual override with confirmation (skip invalidates task).

### Phase 4: Advanced compliance (later)
- Start/finish limits (time/height/speed).
- FAI triangle rules, min separation, class constraints.
- Validation reporting integrated into UI.
