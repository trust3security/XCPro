# Turn Points (TP) and Observation Zones

## Turn Point observation zone (SC3 Annex A 2025, 7.6.1)

**FAI rule:**
- A Turn Point is a waypoint between two legs.
- The TP Observation Zone is a **vertical cylinder** centered on the TP.
- For a Racing Task the cylinder **radius is 500 m**.

## How a TP is achieved (SC3 Annex A 2025, 7.6.4)

**FAI rule:**
A competitor is credited with achieving a TP (or Assigned Area) if either:
1. A valid GNSS fix is **inside** the observation zone, OR
2. The straight line between two consecutive valid fixes **intersects** the observation zone.

This is important for apps because a low sample rate can “skip over” the boundary.

## “Near miss” tolerance (SC3 Annex A 2025, 7.6.5)

**FAI rule (2025 behavior):**
If the pilot fails to enter the observation zone, but the log shows a fix within **500 m of the zone**, the scorer chooses whichever yields the higher score:
- give credit with a penalty, OR
- give no credit and no penalty.

**Historical note (2013 Annex A, 7.5.5):**
The 2013 rule version awarded credit (with penalty) when within 500 m of the zone.

**App guidance:**
- Always compute and display:
  - `tpAchievedStrict` (entered/intersected)
  - `tpNearMiss500m` (closest fix within 500 m of boundary)
- If you’re not scoring, you can show “Possible TP (near miss)” but not auto-advance.
- If you *are* scoring, implement the 2025 “choose better outcome” logic.

---

# Implementation notes (geometry)

## Cylinder inclusion

Given a fix `P` and TP center `C`:
- compute geodesic distance `d(P,C)`
- inside zone if `d ≤ radius`.

## Segment intersects cylinder

Given two consecutive fixes `A` and `B`:
- if either endpoint is inside the cylinder, you already have achievement.
- else check if the great-circle segment AB comes within `radius` of the center.

**App guidance for practical implementation:**
For radii like 500 m, a local tangent-plane approximation is usually sufficient:
1. Convert lat/lon near the TP to local ENU meters.
2. Compute distance from center to segment.
3. Intersects if distance ≤ radius.

(See `validation_algorithms.md` for concrete pseudocode.)

## Time of achievement

FAI uses “interpolated to nearest second” for start/finish; for TP achievement it references “valid fix inside zone” or segment intersection. For an app, you typically want an estimated timestamp for:
- when the segment intersects the boundary
- or the time of the first fix inside

**App guidance:** Use linear interpolation in time between fixes A and B to estimate the boundary crossing time.

