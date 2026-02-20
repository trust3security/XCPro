# Finish procedure and parameters

## Finish definitions (SC3 Annex A 2025, 7.8.1)

- Finish Point: midpoint of the Finish Line, or center of the Finish Ring.
- Finish Time: time the glider first crosses the Finish Line or enters the Finish Ring, interpolated to nearest second.
- Only the **first finish** is valid.

## Finish geometry (SC3 Annex A 2025, 7.8.2)

Organisers choose one finish geometry for the contest (declared in Local Procedures):

### Finish Ring
- Circle around Finish Point.
- Radius is specified; **minimum 3 km**.
- A minimum altitude (MSL) is imposed for crossing the ring; below-minimum crossings are penalised.

### Finish Line
- Finite line at the elevation of the contest site and clearly identifiable on the ground.
- Should be placed so sailplanes can safely land beyond it.
- A minimum altitude (MSL) should be imposed for crossing; below-minimum crossings are penalised (except straight-in landings).

Finish ring is described as the preferred procedure because it lets pilots slow down and focus on landing/traffic.

**App guidance:** task editor fields for finish:
- `finishType`: `RING` or `LINE`
- `finishRingRadiusMeters` (required if RING; validate >= 3000)
- `finishLineLengthMeters` and `finishLineBearingDeg` (required if LINE)
- `finishMinAltitudeMeters` + `altitudeReference` (optional, but common)
- `finishDirectionBearingDeg` (required for LINE; used to validate correct-direction crossing)

## Steering point recommendation (SC3 Annex A 2025, 7.8.2 commentary)

Organisers are encouraged to use a Steering Point to align sailplanes with the desired finish direction.

**App guidance:** treat as an optional waypoint before Finish.

## Validity of finishes (SC3 Annex A 2025, 7.8.3)

A finish is valid if:
- the log shows the glider crossed the Finish Line in the direction specified on the task sheet, OR
- the log shows the glider entered the Finish Ring.

After finishing, the glider must land without delay.

Special case:
- A sailplane landing within the contest site boundary without crossing the Finish Line is deemed to have finished.
- Finish Time is set to “time the glider stopped moving + 5 minutes”.

## Finish closing (SC3 Annex A 2025, 7.8.4)

- Finish line/ring normally closes at contest sunset or when all competitors are accounted for.
- Other closure conditions must be described in Local Procedures.
- If still on task after finish closes, competitor is considered outlanded at the last valid GNSS fix immediately before closing time.

