# Validation algorithms (pseudocode)

This file gives implementable logic for an Android app that tracks progress through a Racing Task.

It assumes:
- A GPS track is a list of fixes with time (seconds), lat/lon, and optional altitude.
- Distances are computed geodesically (or locally approximated for small radii).
- “Valid fix” filtering (e.g., removing obvious GNSS jumps) is handled upstream.

## High-level task state machine

A Racing Task is completed when (SC3 Annex A 2025, 7.9.1):
- the competitor has a **valid Start** (possibly with penalty),
- has achieved all Turn Points in the correct order (possibly with penalties),
- and has a **valid Finish** (possibly with penalty).

### State machine
```
PRE_START
  -> STARTED (on valid start)
STARTED
  -> TP_i_ACHIEVED (for each TP in order)
TP_n_ACHIEVED
  -> FINISHED (on valid finish)
```

## Geometry helper functions

### 1) Distance (meters)
Use a WGS‑84 geodesic library if available. If not, Haversine is acceptable for <~1000 km.

```
distanceMeters(lat1, lon1, lat2, lon2) -> double
```

### 2) Cylinder inclusion
```
isInsideCylinder(fix, center, radiusM):
  return distanceMeters(fix.lat, fix.lon, center.lat, center.lon) <= radiusM
```

### 3) Segment intersects cylinder (local ENU approximation)
For small radii (500 m) you can treat a neighborhood around the center as planar.

```
segmentIntersectsCylinder(A, B, center, radiusM):
  # convert A,B,center to local ENU coordinates (meters) using center as origin
  a = toENU(A, origin=center)
  b = toENU(B, origin=center)
  c = (0,0)

  # compute closest distance from point c to segment ab
  d = distancePointToSegment(c, a, b)
  return d <= radiusM
```

### 4) Start ring “leave circle” detection
```
detectLeaveCircle(prevFix, fix, center, radiusM):
  insidePrev = isInsideCylinder(prevFix, center, radiusM)
  insideNow  = isInsideCylinder(fix, center, radiusM)
  return insidePrev && !insideNow
```

### 5) Finish ring “enter circle” detection
```
detectEnterCircle(prevFix, fix, center, radiusM):
  insidePrev = isInsideCylinder(prevFix, center, radiusM)
  insideNow  = isInsideCylinder(fix, center, radiusM)
  return !insidePrev && insideNow
```

### 6) Line crossing with direction

Represent a finite line segment by two endpoints `L1` and `L2` and a “valid crossing direction” vector `dir` (unit vector in local ENU).

```
segmentCrossesLine(prevFix, fix, L1, L2):
  # planar line segment intersection in ENU coordinates
  return segmentsIntersect(prevFix, fix, L1, L2)

crossingDirectionOk(prevFix, fix, dir):
  v = toENU(fix, origin=prevFix)  # movement vector
  return dot(v, dir) > 0
```

**App guidance:** Create `dir` from:
- Start: bearing from Start Point to first TP (or to first Assigned Area center).
- Finish: bearing specified by contest; often bearing from last point to finish.

### 7) Interpolated event time (nearest second)
If A at time tA and B at time tB cross a boundary at fraction `f` along segment, event time is:

```
t = tA + f * (tB - tA)
return roundToNearestSecond(t)
```

Compute `f` from the geometry (line intersection or circle boundary intersection). If you can’t compute `f`, use the time of the first fix inside / first fix after crossing as a conservative approximation.

## Start detection

Inputs:
- `startGateOpenTime`
- start geometry (`LINE` or `RING` or `CYLINDER`)
- (optional) tolerance rules: fix within 500 m of start zone

Algorithm:
1. Ignore fixes with timestamp < startGateOpenTime.
2. For each consecutive fix pair (A,B), detect start condition:
   - Line: segment crosses start line AND direction OK.
   - Ring: leave circle (inside→outside).
   - Cylinder start (PEV): requires PEV events, typically not available in a generic GPS log; treat as unsupported unless you have recorder events.
3. If found, record `startTime` (interpolated) and `startFixIndex`.
4. If not found, check tolerance:
   - find a fix within 500 m of the start line/circle AFTER gate open; mark as “tolerance start”.

## Turn point detection (for each TP in order)

For TP_i with center C and radius R=500m:
1. Starting from the last achieved index, scan fixes:
   - if isInsideCylinder(fix,C,R) => achieved at fix time
   - else if segmentIntersectsCylinder(prevFix, fix, C, R) => achieved with interpolated time
2. If never achieved, compute near-miss:
   - compute minimum distance to boundary; if a fix is within 500 m outside boundary, flag `nearMiss500m`.

## Finish detection

Similar to start:
- Finish ring: detect enter circle (outside→inside).
- Finish line: segment crosses line AND direction OK.
- Apply closing time if provided; if finish is closed, treat as “outlanded at last fix before close”.

## Multiple starts

FAI scoring for line start allows multiple valid starts; best scoring may be used (SC3 Annex A 2025, 7.4.3.6). For a pilot-navigation app:

**App guidance:**
- Default behavior: use the **first** valid start after the gate opens for live navigation.
- Provide UI: “Select another start time” if multiple starts are detected.
- When exporting to scoring, keep all detected starts with timestamps so scoring software can choose.

