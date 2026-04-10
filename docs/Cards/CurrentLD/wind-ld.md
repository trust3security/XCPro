# Wind And Current L/D Release-Grade Brief

This file records the implemented release-grade rule for wind and visible
Current L/D.

## Current product decision

XCPro now uses wind in the visible `ld_curr` card, but not by patching wind
into the old raw ground helper.

Implemented rule:

- the visible pilot-facing `ld_curr` card reads the fused upstream metric
  `pilotCurrentLD/pilotCurrentLDValid`
- raw `currentLD/currentLDValid` still remain the old over-ground metric
- raw `currentLDAir/currentLDAirValid` still remain the air-data metric used
  by `ld_vario`

## Wind rule

Wind is used when it is trustworthy.

Trustworthy wind means:

- vector available
- `isAvailable == true`
- not stale
- confidence above the live wind-entry threshold

When wind is trustworthy:

- project the wind vector along the glide direction
- use that projected component in the rolling fused estimator

When wind is not trustworthy:

- do not invalidate the metric
- use `windAlong = 0`

That zero-wind fallback is mandatory and implemented.

## Direction rule

Wind only matters after projection onto a direction.

Direction priority is:

1. active target/course bearing from `WaypointNavigationRepository`
2. smoothed recent straight-flight track
3. freeze the last valid glide direction during turn/thermal/climb

If wind is valid but there is no reliable direction:

- degrade to zero-wind behavior
- do not invalidate the metric

## Circling / thermal rule

The fused metric must not recompute from circling geometry.

Implemented behavior:

- circling, turning, and climbing samples do not enter the rolling estimator
- the last valid straight-flight value is held briefly
- while that held value is shown, the visible `ld_curr` subtitle should read
  `THERMAL`
- if the non-gliding state lasts past the timeout, the card shows no data and
  the visible subtitle should still read `THERMAL`
- when straight flight resumes, the window refills from fresh eligible samples

## Why this is release-grade

This avoids the two bad extremes:

- bad option 1:
  - keep showing nonsense from circle geometry
- bad option 2:
  - kill Current L/D whenever wind or TE is imperfect

Instead XCPro now does:

- wind-aware when wind is good
- zero-wind graceful degradation when wind is weak/missing
- no circling contamination
- active-polar support only through the authoritative polar seam

## What wind does not do

Wind still does not:

- change the raw `currentLD` helper formula
- directly change `ld_vario`
- override active-polar ownership
- create a `currentVsPolar` metric

Wind is one input to the fused visible pilot Current L/D. It is not a reason to
collapse wind, polar, and setup ownership into one mixed helper.
