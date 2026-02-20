# Start procedure and parameters

This file focuses on **what an app must store** to model start rules and what it must detect to validate a start.

## Start gate / opening and closing (SC3 Annex A 2025, 7.4.2)

**FAI rule (paraphrased):**
- Start gate opening time is announced by radio.
- Start is normally opened **30 minutes after** a launch has been offered to the last sailplane in the class being launched.
- The Sporting Director may reduce this time if a fair start opportunity exists for all pilots; the **minimum is 20 minutes**.
- Start gate normally closes at contest sunset, when all competitors are accounted for, or when the day is cancelled (no starts after closing).

**App guidance:** Store:
- `startGateOpenTimeLocal` (required)
- `startGateCloseTimeLocal` (optional; often contest sunset)
- `timezone`
- (optional metadata that may help contest ops) `releaseAreaCenter`, `launchStatus`

## Pre-start altitude procedure (SC3 Annex A 2025, 7.4.2.2)

**FAI rule:**
- Organisers may impose a **pre-start altitude** (MSL).
- After the start gate is open and before the competitor’s valid start, the track must contain at least one fix **below** the specified pre-start altitude (otherwise penalty).
- This is explicitly a **pre-start** procedure and does not restrict the actual start altitude.

**App guidance:** Optional fields:
- `preStartAltitudeMeters` + `altitudeReference` (`MSL` or `QNH`)
- Provide an “altitude compliance” indicator.

## Validity of starts and tolerance (SC3 Annex A 2025, 7.4.2.3)

**FAI rule:**
- If there is no proof of a valid start, the start may still be validated if the flight log shows a valid fix within **500 m** of the Start Line or Start Cylinder after opening.
- In that case the start time is taken from that fix and a penalty (distance-based) applies.
- If no such event is detected, there is no valid start.

**App guidance:**
- Implement both:
  - strict validation (cross/leave/PEV)
  - tolerance detection: fix within 500 m of start zone
- Mark tolerance starts as `VALID_WITH_PENALTY_POSSIBLE`.

---

# Line Start (SC3 Annex A 2025, 7.4.3)

## Geometry and definitions (7.4.3.1)

- Start Line: finite length line, perpendicular to the course to the first TP (or center of first AA).
- Start Point: midpoint of the Start Line.
- Start Time: time the competitor crosses the Start Line, **interpolated to nearest second**.

## Start options (7.4.3.2)

### Normal Start
No additional parameters.

### PEV Start
Two parameters must be on the task sheet:
- `pevWaitTimeMinutes` ∈ {5,6,7,8,9,10}
- `pevStartWindowMinutes` ∈ {5,6,7,8,9,10}

## Validity (7.4.3.3)
A line start is valid when the log shows the glider crossed the Start Line **in the specified direction** after the start gate opened.

## PEV start procedure (7.4.3.4)

**FAI rule (paraphrased):**
- Pilot presses a PEV in the primary flight recorder before crossing.
- This creates a penalty-free interval that:
  - begins `pevWaitTimeMinutes` after pressing PEV
  - ends `pevStartWindowMinutes` after it begins
- PEV can be pressed up to **3 times per launch** (later ones ignored by scorer).
- Each press closes the previous window and restarts the wait timer.
- Multiple PEVs within 30 seconds are treated as one (at the first time).
- Pressing PEV before the start gate opens is allowed.
- Failure to record PEV or starting outside the interval is penalized.

**App guidance:** Even if XCPro cannot read “PEV” events, support these fields so tasks can be imported/exported and displayed.

## Energy control at start (7.4.3.5)

**FAI rule (paraphrased):**
- Organisers may set:
  - Maximum Start Altitude (MSA) per class (announced; included in task sheet).
  - Maximum Start Groundspeed (announced at briefing; on task sheet).
- Groundspeed is determined from the straight-line distance between the fixes nearest to **8 seconds before and after** the start, divided by elapsed time.
- A “no wind” value should be at least 170 kph.

**App guidance:** Store these as optional fields and, if you have GNSS fixes at 1 Hz, you can compute a “start speed” and warn.

## Multiple starts (7.4.3.6)

**FAI rule (paraphrased):**
- If multiple valid starts exist, the competitor can be scored using the start that yields the best score.
- A start after a properly completed task is not valid.
- Only the first task completion each day may be claimed.

---

# Cylinder Start (waiver-only) (SC3 Annex A 2025, 7.4.4)

Important note: Annex A 2025 states the Cylinder Start will not be used unless approved by waiver from the IGC Bureau.

## Key parameters (7.4.4.1)

- Start Cylinder radius is specified on task sheet; **should not be less than 10 km**.
- Start Time: the latest PEV within the Start Cylinder; if no PEV, the time of latest exit of the cylinder.
- Minimum PEV interval: **10 minutes**.

Other Cylinder Start fields matter for scoring/penalties:
- maximum allowable groundspeed at the start
- maximum loss of height (start altitude − finish altitude) without penalty

**App guidance:** Implement storage + UI for these fields, but only enable Cylinder Start mode if the competition uses it.

