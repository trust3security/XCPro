# Thermal Trail Angular/Jerky Rendering: Root Cause Analysis

Date
- 2026-02-27

Owner
- XCPro Team / future map-trail contributors

Problem Statement
- During thermalling, ownship snail trail appears angular and jerky to the pilot.

Severity
- Medium to High UX impact in a core navigation scenario.
- High frequency in real use (thermal flight).

## 1. Observed Behavior

When circling in thermal:
- Trail path looks polygonal instead of smooth arcs.
- Drifted trail can visibly "jump" between updates.
- Blue ownship icon appears smoother than trail itself, amplifying perceived mismatch.

## 2. Direct Causes (Ranked)

1) Live trail point cadence is too coarse (primary)
- Live trail storage accepts a new sample only every 2 seconds.
- This turns circular motion into sparse straight segments.

Evidence:
- `TrailProcessor` live store: `TrailStore(minDeltaMillis = LIVE_MIN_DELTA_MS)`
- `LIVE_MIN_DELTA_MS = 2_000L`
- `TrailStore.addSample`: drops samples when `dt < minDeltaMillis`.

2) Full trail render is gated by `sampleAdded` (primary)
- `SnailTrailManager` re-renders full geometry mostly only when new sample arrives.
- In live mode that means coarse update steps.

Evidence:
- Render condition includes `update.sampleAdded`.
- No dedicated per-frame full geometry update path for live mode.

3) Thermal drift transform uses elapsed time and applies in render steps (primary)
- Drift uses `driftSeconds = (currentTime - pointTime)` while circling.
- With coarse update cadence this creates visible geometric jumps.

Evidence:
- `applyDrift = settings.windDriftEnabled && isCircling`
- Render coordinate shift from drift vector and drift scale.

4) Live path has no interpolation equivalent to replay (secondary but important)
- Replay inserts intermediate points at 250 ms.
- Live path has no equivalent expansion.

Evidence:
- Replay-only `ReplayTrailInterpolator` with `DEFAULT_STEP_MS = 250L`.

5) Live wind used for drift is unsmoothed (secondary)
- Replay wind passes through `TrailWindSmoother`.
- Live wind uses direct wind sample, increasing drift jitter when wind estimate fluctuates.

Evidence:
- `TrailProcessor` applies `replayWindSmoother` only for replay branch.

6) Additional live distance thinning can remove points (secondary)
- Planner uses `LIVE_DISTANCE_FACTOR = 3.0` (vs replay 1.0).
- At some zoom levels this increases angular feel.

Evidence:
- `SnailTrailRenderPlanner` min-distance computation.

## 3. Why It Is Most Visible In Thermal

Circling state turns on drift compensation.
- Without circling, drift transform is off and jerk is less obvious.
- In thermal, circle geometry + drift jumps make artifacts obvious.

Also:
- Pilot expectation in thermal is smooth rings and coherent centering cues.
- Any stepwise artifact is cognitively expensive while centering the climb.

## 4. Mismatch With Ownship Icon Motion

Ownship icon path:
- Display pose is updated per display frame loop.
- Camera/icon can feel smooth.

Trail path:
- Geometry refresh is tied to coarse sample acceptance in live mode.

Result:
- Smooth icon over coarse trail creates "icon is fluid, trail is stuttering" perception.

## 5. Non-Causes / Lower-Likelihood Contributors

- Palette selection (`TrailType`) is not the root cause.
- Layer ordering (below blue icon) is correct and intentional.
- Time-base switching logic is not the thermal-specific jerk driver.

## 6. Repro Recipe

1. Enable snail trail.
2. Keep `Wind drift` enabled.
3. Enter sustained circling (thermal).
4. Observe trail arc corners and drift jumps.

Optional controls:
- Disable `Wind drift`: jumps reduce notably.
- Replay same segment: smoother due to replay interpolation path.

## 7. Conclusion

The issue is primarily a live-cadence + render-gating + drift-transform interaction:
- coarse live sampling
- coarse geometry refresh
- drift applied at each refresh step

Fixing any one part helps; fixing all three gives stable thermal visuals.

