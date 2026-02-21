# Task creation UI spec (Android)

This file is not a rulebook; it’s a product spec to support implementing a task editor consistent with FAI Annex A Racing Tasks.

## Core screens

### 1) Task metadata
- Task name
- Date (local)
- Timezone
- Contest site (optional: name, lat/lon, elevation)

### 2) Waypoint selection & ordering
User selects:
- Start waypoint
- Turn Points (minimum 2)
- Finish waypoint
- Optional Steering Point (inserted before finish)

Features:
- Search by name/code
- Map picking
- Reorder TP list (drag)
- “Reverse task” (swap order) — optional

Validation:
- Require ≥2 TPs for RT.
- Warn if consecutive leg angle < 45° (recommended in FAI rules, but not mandatory).

### 3) Start configuration
Fields (show based on start type):

**Start type**
- Line (default)
- Ring/Circle (compat/legacy mode)
- Cylinder Start (advanced; hide behind a toggle)

**Common**
- Start gate open time (required)
- Start gate close time (optional)
- Pre-start altitude (optional; plus MSL/QNH selector)

**Line start**
- Line length (meters)
- “Auto-orient to first TP” toggle (default ON):
  - bearing computed from start point → first TP
  - line perpendicular to that bearing
- Crossing direction:
  - default: toward first TP
  - allow manual override if contest provides a different direction
- Start option:
  - Normal
  - PEV Start (if enabled): wait time and window (5–10 min each)

**Ring start**
- Ring radius (meters)

**Cylinder Start (rare)**
- Cylinder radius (meters, validate ≥ 10,000)
- Maximum groundspeed (optional)
- Maximum loss of height (optional)

### 4) Turn Points configuration
For each TP:
- Show name + coordinates
- Zone radius:
  - default and locked to 500 m for FAI RT
  - allow override if app supports non-FAI tasks

### 5) Finish configuration
Finish type:
- Ring (default, preferred by FAI)
- Line

Ring:
- Radius (validate ≥ 3000 m)
- Minimum finish altitude (optional) + altitude reference

Line:
- Line length
- Line bearing (auto-perpendicular to last leg, optional)
- Crossing direction (auto from last point to finish point, optional)
- Minimum finish altitude (optional)

### 6) Review screen
Show:
- Map with zones (line, circles)
- Leg distances + total task distance
- Bearings per leg
- Export/share options

## Live-flight guidance features (optional but useful)
- “Distance to next zone boundary” (not to center)
- “On correct side of start line?” indicator
- “TP achieved” auto-advance (strict entry) with near-miss warning
- Finish countdown with min-altitude warning

