# Validation algorithms (pseudocode)

This file gives implementable logic for an Android app that tracks progress
through a Racing Task.

It assumes:

- a GPS track is a list of fixes with time, lat/lon, and optional altitude
- distances are computed geodesically or with a suitable local approximation for
  small radii
- "valid fix" filtering is handled upstream

## High-level task state machine

A Racing Task is completed when the competitor has:

- a valid Start
- achieved all Turn Points in the correct order
- a valid Finish

### State machine

```text
PRE_START
  -> STARTED
STARTED
  -> TP_i_ACHIEVED
TP_n_ACHIEVED
  -> FINISHED
```

## Geometry helper functions

### 1) Distance (meters)

Use a WGS-84 geodesic library if available. If not, Haversine is acceptable for
short task geometry calculations.

```text
distanceMeters(lat1, lon1, lat2, lon2) -> double
```

### 2) Cylinder inclusion

```text
isInsideCylinder(fix, center, radiusM):
  return distanceMeters(fix.lat, fix.lon, center.lat, center.lon) <= radiusM
```

### 3) Segment intersects cylinder (local ENU approximation)

For small radii such as 500 m, you can treat the neighborhood as planar.

```text
segmentIntersectsCylinder(A, B, center, radiusM):
  a = toENU(A, origin=center)
  b = toENU(B, origin=center)
  c = (0, 0)
  d = distancePointToSegment(c, a, b)
  return d <= radiusM
```

### 4) Start ring leave-circle detection

```text
detectLeaveCircle(prevFix, fix, center, radiusM):
  insidePrev = isInsideCylinder(prevFix, center, radiusM)
  insideNow  = isInsideCylinder(fix, center, radiusM)
  return insidePrev && !insideNow
```

### 5) Finish ring enter-circle detection

```text
detectEnterCircle(prevFix, fix, center, radiusM):
  insidePrev = isInsideCylinder(prevFix, center, radiusM)
  insideNow  = isInsideCylinder(fix, center, radiusM)
  return !insidePrev && insideNow
```

### 6) Line crossing with direction

Represent a finite line segment by endpoints `L1` and `L2` and a valid crossing
direction vector `dir`.

```text
segmentCrossesLine(prevFix, fix, L1, L2):
  return segmentsIntersect(prevFix, fix, L1, L2)

crossingDirectionOk(prevFix, fix, dir):
  v = toENU(fix, origin=prevFix)
  return dot(v, dir) > 0
```

Create `dir` from:

- Start: bearing from Start Point to the first TP or first assigned area center
- Finish: the contest-specified finish direction

### 7) Interpolated event time (nearest second)

If fix `A` at time `tA` and fix `B` at time `tB` cross a boundary at fraction
`f` along the segment:

```text
t = tA + f * (tB - tA)
return roundToNearestSecond(t)
```

Compute `f` from the geometry whenever the result will be treated as
authoritative task progression. If you cannot compute `f`, treat the fallback
time as advisory UI only, not exact credited boundary evidence.

## Start detection

Inputs:

- `startGateOpenTime`
- start geometry (`LINE`, `RING`, or `CYLINDER`)
- optional 500 m tolerance handling

Algorithm:

1. Ignore fixes with timestamp before `startGateOpenTime`.
2. For each consecutive fix pair `(A, B)`, detect the start condition:
   - Line: segment crosses the start line and direction is correct.
   - Ring: inside-to-outside leave-circle transition.
   - Cylinder start: requires PEV events unless the contest uses a simplified
     compatibility mode.
3. If found, record `startTime` and the credited crossing evidence.
4. If not found, check 500 m tolerance and mark it as a penalized fallback, not
   a clean start.

## Turnpoint detection (for each TP in order)

For TP_i with center `C` and radius `R = 500 m`:

1. Starting from the last achieved index, scan fixes:
   - if `isInsideCylinder(fix, C, R)` then achieved at fix time
   - else if `segmentIntersectsCylinder(prevFix, fix, C, R)` then achieved with
     interpolated time
2. If never achieved, compute near-miss:
   - compute minimum distance to the boundary
   - if a fix is within 500 m outside the boundary, flag `nearMiss500m`

Exact live-navigation note:

- do not auto-advance a leg from a near-miss
- if the leg becomes active while the glider is already inside the OZ, do not
  treat the first inside fix as enough for exact auto-advance unless you have
  active-leg evidence that proves the achievement belongs to the current leg

## Finish detection

Similar to start:

- Finish ring: detect outside-to-inside enter-circle transition
- Finish line: detect line crossing with correct direction
- apply finish close time when present

## Multiple starts

FAI scoring for line start allows multiple valid starts, with the best-scoring
start potentially used for scoring. For a live navigation app:

- preserve all detected start candidates
- keep the credited start outcome explicit
- do not silently reinterpret a tolerance start as a clean start
