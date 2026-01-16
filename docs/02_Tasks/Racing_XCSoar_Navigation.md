# Racing_XCSoar_Navigation.md

**Purpose**: Define how XCPro will implement Racing task navigation using the same transition-driven model as XCSoar.

**Scope**: Start/turn/finish transitions, auto-advance, navigation state, and UI event pipeline. This doc excludes AAT logic.

---

## 1) XCSoar reference model (what we are copying)

XCSoar pipeline:
- GPS update -> TaskManager::Update() -> OrderedTask::Update() -> CheckTransitions()
- Start: scored on **exit** from start OZ (line/cylinder/sector)
- Turnpoints: **entry** into OZ advances to next point
- Finish: **entry** into finish OZ completes task
- SmartTaskAdvance handles auto/manual arming

Relevant XCSoar files:
- `src/Engine/Task/Ordered/OrderedTask.cpp` (CheckTransitions)
- `src/Engine/Task/Ordered/SmartTaskAdvance.cpp` (advance logic)
- `src/Engine/Task/Ordered/Points/StartPoint.cpp` (start = exit)
- `src/Engine/Task/Ordered/Points/FinishPoint.cpp` (finish = entry)

---

## 2) XCPro mapping (target architecture)

### New package (Racing-only)
```
feature/map/src/main/java/com/example/xcpro/tasks/racing/navigation/
  RacingNavigationEngine.kt
  RacingZoneDetector.kt
  RacingNavigationState.kt
  RacingNavigationEvent.kt
  RacingTransitionDetector.kt
```

### Existing modules used
- `RacingTaskManager` (task data, persistence)
- `RacingTaskCalculator` (geometry touch points)
- `turnpoints/*Calculator.kt` (OZ checks with context)
- `TaskAdvanceState` (auto/manual arming)

### Ownership boundaries
- Navigation state lives in Racing-only package.
- TaskManagerCoordinator only consumes Racing navigation state/events.
- No AAT imports in Racing navigation.

---

## 3) Transition rules (the core logic)

### Start
- **Start line**: task starts on **exit** from the line sector.
  - previous fix inside, current fix outside
  - transition constraint: both fixes inside the start circle (line length / 2)
  - start time is the **previous fix time** (last in-sector fix)
- **Start cylinder / sector**: task starts on **exit** (inside -> outside),
  start time = previous fix time.

### Turnpoints
- Entering the OZ advances to the next turnpoint.

### Finish
- Entering the finish OZ completes the task (only after all previous points).

### All transitions
- Use **last GPS vs current GPS**.
- Do not use single-point distance checks for transitions.

---

## 4) Observation zone checks (context-aware)

We must evaluate OZ membership using TaskContext (previous + next waypoint).

### Cylinder
- inside if distance(center, pos) <= radius

### FAI Quadrant
- 90-degree infinite sector
- oriented by the bisector of inbound/outbound legs
- requires previous + next waypoint

### Keyhole
- inside if (distance <= innerRadius) OR (distance <= outerRadius AND angle within sector)
- sector orientation same as FAI quadrant (bisector-based)

### Start line
- line sector centered on start waypoint
- oriented perpendicular to outgoing leg (start -> next)
- inside if within start circle and within the line sector angles
- exit transition is inside -> outside with the transition constraint

---

## 5) Anti-jitter / stability rules

- **Hysteresis**: require N consecutive inside samples (e.g., 3) to confirm entry.
- **Debounce**: require N consecutive outside samples before treating as exited.
- **Distance gating**: reduce checks when far from OZ (e.g., > 30 km).
- **Speed gating**: ignore impossible jumps (GPS spikes).

---

## 6) Navigation state + events

### State
```
currentLegIndex
taskStarted
taskFinished
turnpointStatus[] (PENDING/ACTIVE/COMPLETED/SKIPPED)
lastGpsPosition
insideActiveZone
```

### Events
- TaskStarted
- TurnpointReached(index)
- TaskFinished
- TaskInvalidated(reason)

These events drive UI updates and optional audio/haptic cues.

---

## 7) Pseudocode (core loop)

```
onGpsUpdate(now, last):
  if taskFinished: return

  active = currentLeg
  activeWp = waypoints[active]

  insideNow = zoneDetector.isInside(now, activeWp, context)
  insideLast = zoneDetector.isInside(last, activeWp, context)

  if !taskStarted:
     if active is START:
        if insideLast && !insideNow && transitionConstraint(last, now):
           taskStarted = true
           mark START completed
           advance to next leg
     return

  if active is TURNPOINT or FINISH:
     if !insideLast && insideNow:
        mark completed
        if active is FINISH:
           taskFinished = true
        else:
           advance to next leg
```

---

## 8) Tests (non-negotiable)

- Start line exit (prev inside, now outside) with transition constraint
- Cylinder entry/exit transitions
- FAI quadrant orientation + entry detection
- Keyhole cylinder vs sector entry
- Hysteresis: GPS jitter near boundary

---

## 9) Integration plan (incremental)

1) Implement RacingNavigationManager + ZoneDetector (feature-flagged)
2) Feed GPS updates into navigation manager
3) Wire navigation state into UI (top bar, current TP)
4) Add manual advance and confirmations
5) Add audio/haptic events

---

## 10) Deliverable definition for Phase 1 (Core navigation)

- Start triggers on line crossing or OZ exit
- Turnpoints advance on entry
- Finish triggers on entry
- UI shows current TP, distance, completion

---
