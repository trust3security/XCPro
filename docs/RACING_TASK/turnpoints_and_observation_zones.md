# Turn Points (TP) and Observation Zones

## Turn Point observation zone (SC3 Annex A 2025, 7.6.1)

FAI rule:

- A Turn Point is a waypoint between two legs.
- The TP Observation Zone is a vertical cylinder centered on the TP.
- For a Racing Task the cylinder radius is 500 m.

## How a TP is achieved (SC3 Annex A 2025, 7.6.4)

FAI rule:

A competitor is credited with achieving a TP if either:

1. a valid GNSS fix is inside the observation zone, or
2. the straight line between two consecutive valid fixes intersects the
   observation zone

This matters because a low sample rate can skip over the boundary.

## Near-miss tolerance (SC3 Annex A 2025, 7.6.5)

FAI rule, 2025 behavior:

- If the pilot fails to enter the observation zone, but the log shows a fix
  within 500 m of the zone, the scorer chooses whichever yields the higher
  score:
  - give credit with a penalty, or
  - give no credit and no penalty

Historical note:

- The 2013 Annex A version awarded credit with penalty when within 500 m of the
  zone.

App guidance:

- always compute and display:
  - `tpAchievedStrict` (entered or intersected)
  - `tpNearMiss500m` (closest fix within 500 m of the boundary)
- if you are not scoring, show a possible-TP warning but do not auto-advance
- if you are scoring, implement the 2025 choose-better-outcome logic
- for exact live progression, do not auto-advance a newly active leg from the
  first inside fix alone when the leg became active while already inside the OZ
- require evidence observed within the active-leg window

---

# Implementation notes (geometry)

## Cylinder inclusion

Given a fix `P` and TP center `C`:

- compute geodesic distance `d(P, C)`
- the fix is inside when `d <= radius`

## Segment intersects cylinder

Given two consecutive fixes `A` and `B`:

- if either endpoint is inside the cylinder, you already have achievement
- otherwise check whether segment `AB` comes within `radius` of the center

App guidance for practical implementation:

For radii like 500 m, a local tangent-plane approximation is usually
sufficient:

1. convert nearby lat/lon to local ENU meters
2. compute distance from the center to the segment
3. intersect when distance is `<= radius`

See `validation_algorithms.md` for pseudocode.

## Time of achievement

FAI uses interpolated-to-nearest-second timing for start and finish. For a TP,
the rule is based on a valid inside fix or a segment intersection, but an app
usually still wants an estimated credited timestamp.

App guidance:

- use linear interpolation in time between fixes `A` and `B` to estimate the
  boundary crossing time for authoritative progression
