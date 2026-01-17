# Racing Task Boundary Crossing - Implementation Plan (XCSoar-Aligned)

## Purpose
Provide a single-source-of-truth (SSOT) for racing task boundary crossing that:
- Matches XCSoar transition rules (enter/exit using consecutive fixes + line constraints).
- Produces exact boundary touch points and timestamps for competition-grade scoring.
- Keeps replay, UI, and navigation logic perfectly aligned.
- Avoids duplicate geometry or ad-hoc fixes.

## Scope
Racing tasks only. No cross-contamination with AAT/DHT modules.

## Key Findings From XCSoar (Authoritative Behavior)
1) Inside test is strict geometry (no epsilon):
   - Cylinder: distance <= radius.
   - Sector: distance <= radius AND bearing within sector.
2) Transition uses consecutive fixes:
   - Enter: now inside AND last outside.
   - Exit: now outside AND last inside.
3) Line zones add a constraint:
   - For line start/finish, both fixes must be inside the line’s semicircle.
4) Start/finish add constraints:
   - Start: exit transition + time/speed/height rules.
   - Finish: enter transition + height rules.

## Problem In XCPro Today
Replay uses waypoint centers as “inside points” for cylinders/keyholes, so the
triangle keeps flying to the center after the nav engine already fires the
“arrived” event at the boundary. This creates UI/logic drift.

## SSOT Strategy
Introduce a single racing-module component responsible for:
- Detecting transitions (enter/exit).
- Computing the boundary crossing point and crossing timestamp.
- Generating guaranteed inside/outside anchor points.

This component is used by:
- Racing navigation engine.
- Replay log builder.
- UI distance-to-entry calculations.

## Proposed Components
### 1) RacingBoundaryCrossingPlanner (new)
Responsibility: Given two fixes and a waypoint (with prev/next context),
returns transition info and boundary anchors.

Inputs:
- previous fix, current fix (lat/lon/time)
- waypoint + role/type (start/turn/finish)
- previous/next waypoint (for sector orientation)

Outputs:
- transition type: ENTER / EXIT / NONE
- crossingPoint (lat/lon)
- crossingTimeMillis (interpolated)
- insideAnchor (lat/lon)
- outsideAnchor (lat/lon)

### 2) RacingBoundaryEpsilonPolicy (new)
Responsible for guard-band behavior, independent of XCSoar logic.

Default:
- epsilonMeters = max(30m, 2 * gpsAccuracy)  (configurable)

Rules:
- inside: distance <= radius - epsilon
- outside: distance >= radius + epsilon

Note: XCSoar is strict, but XCPro should guard against phone GPS noise.

## Algorithms (By Zone)
### Cylinder (Turn/Finish/Start Cylinder)
1) Compute intersection of segment (prev->curr) with circle boundary.
2) Validate enter/exit using inside/outside checks with epsilon.
3) Inside anchor: boundary point moved inward by epsilon.
4) Outside anchor: boundary point moved outward by epsilon.

### Line Start/Finish
1) Require both fixes inside the line’s semicircle (XCSoar constraint).
2) Check segment-line intersection within line endpoints.
3) Determine enter/exit based on inside/outside relative to line sector.
4) Anchors: offset along line normal by epsilon (in/out).

### Sector (FAI or custom)
1) Compute sector orientation using prev/next waypoints.
2) Check boundary crossing on arc or radial boundary.
3) Use epsilon to generate in/out anchors.

### Keyhole
1) If inside inner cylinder, treat like cylinder.
2) Else treat like sector arc (outer radius + angular range).
3) Anchors follow the same epsilon rules as cylinder/sector.

## Navigation Engine Integration
Replace current insidePrevious/insideNow logic with:
- planner output + transition rules
- start uses EXIT, finish uses ENTER
- transitions use crossingTimeMillis for event timestamps

## Replay Integration
RacingReplayLogBuilder should use planner output:
- For each waypoint, build anchors as:
  - Start: inside -> crossing -> outside
  - Turnpoint: outside -> crossing -> inside
  - Finish: outside -> crossing -> inside
- Ensures replay triangle turns at boundary, not at center.

## UI Integration
Distance-to-entry should use the same crossing planner:
- Distance measured to crossingPoint (or the nearest boundary point).
- Eliminates mismatch between top bar distance and map marker.

## Tests (Non-Negotiable)
1) Boundary crossing tests per zone type:
   - Cylinder enter/exit at correct epsilon distance.
   - Line crossing only when both fixes in semicircle.
   - Sector crossing with correct orientation.
2) Replay tests:
   - Generated replay path triggers events at crossing points.
   - Visual turn at boundary (not center).
3) Regression test:
   - Change cylinder radius -> crossing moves accordingly.

## Rollout Phases
### Phase 1: Planner + Cylinder
- Implement planner for cylinders first.
- Wire into navigation engine + replay builder + distance UI.

### Phase 2: Line + Sector + Keyhole
- Add line and sector crossing with XCSoar constraint.
- Add keyhole logic (inner cylinder + outer sector).

### Phase 3: Validation + Tests
- Add cross-zone replay validation tests.
- Add guard-band unit tests.

## Red Flags (Must Fail Review)
- Any new geometry logic outside the racing module.
- Replay paths that use waypoint centers for boundary crossings.
- UI distances computed without the planner.
- Using wall clock time instead of GPS time.
- Constructing the planner/epsilon policy directly in UI (must be injected via racing DI).

## Expected Outcomes
- Task transitions happen exactly when the glider crosses the boundary.
- Replay and live display the same boundary crossing.
- Competition-grade determinism with minimal future rewrites.
