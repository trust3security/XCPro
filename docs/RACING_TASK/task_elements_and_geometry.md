# Task elements and zone geometry

This file focuses on the **geometric** information needed for navigation and validation.

## Waypoints

A waypoint is a named point with:
- latitude (deg, WGS‑84)
- longitude (deg, WGS‑84)
- (optional) elevation (meters)
- (optional) code / ICAO-style ID

In SC3 Annex A, “Turn Point” and “Finish Point” are defined relative to the geometry of the zone.

## Start

### 2025: start types (SC3 Annex A 2025, 7.4.1)

The 2025 Annex A describes two start types:
- **Line Start** (7.4.3): cross a finite line in the direction of the first TP / first Assigned Area.
- **Cylinder Start** (7.4.4): a waiver-only start method using PEV inside a cylinder.

### 2013: start options include Start Ring (SC3 Annex A 2013, 7.4.1–7.4.2)

The 2013 Annex A explicitly defines:
- **Start Line**: finite line, perpendicular to course to first TP.
- **Start Ring**: a circle around the contest site/release areas; a start occurs when the pilot **leaves** the ring.

**App guidance:** Support *both* Line Start and Start Ring/Circle for compatibility with older tasks and different contest practices.

---

## Start geometry types for the app

### Start Line

A **line segment** defined by:
- `startPoint` (midpoint of line, lat/lon)
- `lineLengthMeters`
- `courseBearingDeg` (bearing from start point toward first TP or first AA center)
- `lineBearingDeg = (courseBearingDeg + 90) mod 360` (line runs perpendicular to course)
- Endpoints computed from `startPoint`, `lineBearingDeg`, `lineLengthMeters/2`.

**Crossing direction**
- Valid start requires crossing the line **in the direction specified on the Task Sheet** (SC3 Annex A 2025, 7.4.3.3).
- In most RTs that direction is “toward the first TP.”

### Start Ring / Start Circle (legacy but common)

A **circle** defined by:
- `center` (lat/lon)
- `radiusMeters`
- Start occurs when the track transitions from **inside → outside** the circle.

**FAI 2013 guidance:** radius should be sufficient to enclose contest site and release areas (2013 7.4.2b).

### Start Cylinder (PEV/Cylinder Start, SC3 Annex A 2025, 7.4.4)

A **circle/cylinder** (vertical, unlimited) defined by:
- `center` (Start Point)
- `radiusMeters` (FAI note: should not be less than 10 km)
- plus Cylinder Start specific parameters (see `start_procedure.md`)

---

## Turn Points (TP)

**FAI rule (SC3 Annex A 2025, 7.6.1):**
- A Turn Point observation zone is a **vertical cylinder** centered on the TP.
- For a Racing Task, the cylinder **radius is 500 m**.

So each TP is:
- waypoint lat/lon
- observation zone radius = 500 m (default, fixed for FAI RT)

---

## Finish

### Finish Point and Finish Time (SC3 Annex A 2025, 7.8.1)

- Finish Point is the midpoint of the Finish Line or the center of the Finish Ring.
- Finish Time is when the glider first crosses the Finish Line or enters the Finish Ring (interpolated to nearest second). Only the first finish counts.

### Finish geometry options (SC3 Annex A 2025, 7.8.2)

- **Finish Ring**
  - circle radius specified by organisers; **minimum 3 km**
  - competitor finishes when entering the ring
  - minimum altitude may be imposed for crossing the ring (penalty if below)

- **Finish Line**
  - finite line at airfield elevation
  - competitor finishes when crossing line **in the specified direction**
  - minimum altitude should be imposed for crossing the line (penalty if below)

**App guidance:** For a task builder, treat Finish Ring / Line as first-class options and store:
- type
- radius or length
- minimum altitude (optional)
- direction (for finish line)

---

## Steering Point

SC3 Annex A 2025 notes that organisers may use a **Steering Point** to align the last leg (6.2.3 and 7.8.2 commentary).

**App guidance:**
- Model a Steering Point as an **optional additional waypoint** inserted before Finish.
- Default its observation zone to the standard TP cylinder (500 m) unless the contest defines otherwise.

