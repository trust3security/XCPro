# Start procedure and parameters

This file focuses on what an app must store to model start rules and what it
must detect to validate a start.

## Start gate / opening and closing (SC3 Annex A 2025, 7.4.2)

FAI rule, paraphrased:

- Start gate opening time is announced by radio.
- Start is normally opened 30 minutes after a launch has been offered to the
  last sailplane in the class being launched.
- The Sporting Director may reduce this time if a fair start opportunity exists
  for all pilots; the minimum is 20 minutes.
- Start gate normally closes at contest sunset, when all competitors are
  accounted for, or when the day is cancelled.

App guidance. Store:

- `startGateOpenTimeLocal` (required)
- `startGateCloseTimeLocal` (optional)
- `timezone`
- optional metadata such as `releaseAreaCenter` or `launchStatus`

## Pre-start altitude procedure (SC3 Annex A 2025, 7.4.2.2)

FAI rule:

- Organisers may impose a pre-start altitude (MSL).
- After the start gate is open and before the competitor's valid start, the
  track must contain at least one fix below the specified pre-start altitude.
- This is a pre-start procedure and does not itself limit the actual start
  altitude.

App guidance:

- `preStartAltitudeMeters`
- `altitudeReference` (`MSL` or `QNH`)
- a visible altitude-compliance indicator

## Validity of starts and tolerance (SC3 Annex A 2025, 7.4.2.3)

FAI rule:

- If there is no proof of a valid start, the start may still be validated if
  the flight log shows a valid fix within 500 m of the Start Line or Start
  Cylinder after opening.
- In that case the start time is taken from that fix and a distance-based
  penalty applies.
- If no such event is detected, there is no valid start.

App guidance:

- implement both strict validation and 500 m tolerance detection
- mark tolerance starts as `VALID_WITH_PENALTY_POSSIBLE`
- do not present a tolerance start as a normal clean start
- do not silently auto-advance a live task from a tolerance-only start

---

# Line Start (SC3 Annex A 2025, 7.4.3)

## Geometry and definitions (7.4.3.1)

- Start Line: finite length line, perpendicular to the course to the first TP
  or first assigned area center
- Start Point: midpoint of the Start Line
- Start Time: time the competitor crosses the Start Line, interpolated to the
  nearest second

## Start options (7.4.3.2)

### Normal Start

No additional parameters.

### PEV Start

Two parameters must be on the task sheet:

- `pevWaitTimeMinutes` in {5, 6, 7, 8, 9, 10}
- `pevStartWindowMinutes` in {5, 6, 7, 8, 9, 10}

## Validity (7.4.3.3)

A line start is valid when the log shows the glider crossed the Start Line in
the specified direction after the start gate opened.

App guidance:

- for exact live behavior, do not infer a clean line start from a single
  boundary fix when the task becomes active while the glider is already on or
  over the line
- require observed post-activation crossing evidence for a clean start

## PEV start procedure (7.4.3.4)

FAI rule, paraphrased:

- The pilot presses a PEV in the primary flight recorder before crossing.
- This creates a penalty-free interval that:
  - begins `pevWaitTimeMinutes` after the press
  - ends `pevStartWindowMinutes` after it begins
- PEV can be pressed up to 3 times per launch.
- Each press closes the previous window and restarts the wait timer.
- Multiple PEVs within 30 seconds are treated as one at the first time.
- Pressing PEV before the start gate opens is allowed.
- Failure to record PEV or starting outside the interval is penalized.

App guidance:

- even if XCPro cannot read PEV events directly, keep these fields in the task
  model so tasks can be imported, exported, and displayed correctly

## Energy control at start (7.4.3.5)

FAI rule, paraphrased:

- Organisers may set a Maximum Start Altitude and a Maximum Start Groundspeed.
- Groundspeed is determined from the straight-line distance between the fixes
  nearest to 8 seconds before and after the start, divided by elapsed time.
- A no-wind value should be at least 170 kph.

App guidance:

- store these as optional task fields
- if you have suitable GNSS data, compute a start-speed warning

## Multiple starts (7.4.3.6)

FAI rule, paraphrased:

- If multiple valid starts exist, the competitor can be scored using the start
  that yields the best score.
- A start after a properly completed task is not valid.
- Only the first task completion each day may be claimed.

---

# Cylinder Start (waiver-only) (SC3 Annex A 2025, 7.4.4)

Annex A 2025 states the Cylinder Start will not be used unless approved by
waiver from the IGC Bureau.

## Key parameters (7.4.4.1)

- Start Cylinder radius is specified on the task sheet and should not be less
  than 10 km
- Start Time is the latest PEV within the Start Cylinder; if there is no PEV,
  it is the time of the latest exit of the cylinder
- Minimum PEV interval is 10 minutes

Other Cylinder Start fields that matter for scoring and penalties:

- maximum allowable groundspeed at the start
- maximum loss of height without penalty

App guidance:

- implement storage and UI for these fields
- only enable Cylinder Start mode when the competition actually uses it
