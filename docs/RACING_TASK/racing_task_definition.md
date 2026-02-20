# Racing Task (RT) definition and required data

## What is a Racing Task?

**FAI rule (SC3 Annex A 2025, 6.2.1 & 6.3.1):**
A Racing Task is a speed task over a course with **two or more designated Turn Points**, and a **finish at the contest site**. The task is defined by specifying:
- the **Start**
- the **Turn Points** (in order)
- the **Finish**

**FAI rule (6.3.1):** Organisers should avoid acute angles between consecutive legs; a minimum of **45°** between consecutive legs is recommended.

## Minimum structure

An RT MUST contain:

1. Start (line or cylinder-style start, depending on contest rules)
2. At least **two** Turn Points (TP1..TPn, n ≥ 2)
3. Finish (ring or line)

Optionally:

- **Steering Point** (SC3 Annex A 2025, 6.2.3 & 7.8.2 commentary): a point used to align the last leg with a runway/landing direction. Treat it as an additional waypoint on the last leg.

## Parameters the task builder should capture

Even when a pilot only needs navigation, competitions define a “Task Sheet” with additional parameters. The app should support storing these values when available.

Recommended top-level RT parameters:

- `taskType`: `"RT"`
- `taskName`, `taskDate`, `contestSite` (metadata)
- `start`:
  - geometry type (`LINE`, `RING`, `CYLINDER`)
  - geometry parameters (line length, circle radius, etc.)
  - direction / course reference (where required)
  - start gate opening time and (optional) closing time
  - (optional) PEV start parameters (wait time + window) if used in the event
- `turnpoints[]`:
  - waypoint coordinates (WGS‑84 lat/lon)
  - observation zone radius (FAI RT default 500 m)
- `finish`:
  - geometry type (`RING` or `LINE`)
  - ring radius (FAI min 3 km) or line length
  - (optional) minimum finish altitude constraint
  - direction (for finish line)
  - (optional) finish closing time
- `qnh` / altitude reference notes (altitudes may be expressed as MSL or QNH in the rules)

