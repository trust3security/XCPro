# MapPosition Adaptive Smoothing + GPS Cadence Plan

Purpose: Improve live marker responsiveness at high speed while keeping SSOT,
time-base rules, and replay determinism intact.

Scope:
- Adaptive display smoothing (UI-only).
- Live GPS cadence gating (sensor layer only).

Non-goals:
- No changes to navigation logic, scoring, or task rules.
- No replay time-base changes.
- No UI redesign or settings UX in this phase.

References:
- ARCHITECTURE.md (SSOT + time base rules)
- CODING_RULES.md (UI-only logic, no business logic in Compose)
- mapposition.md (map display pipeline)

----------------------------------------------------------------------
1) Constraints (hard)

- SSOT remains FlightDataRepository; UI never writes back smoothed values.
- Live time base = monotonic when available; replay time base = IGC time.
- Adaptive smoothing is visual-only and must not affect navigation.
- GPS cadence changes are live-only and must not affect replay.

----------------------------------------------------------------------
2) SSOT ownership diagram (explicit)

Live sensors / Replay
        |
        v
FlightDataRepository (SSOT)
        |
        v
MapScreenViewModel.mapLocation (GPSData?)
        |
        v
LocationManager.updateLocationFromGPS / updateLocationFromFlightData
        |
        v
DisplayPoseSmoother (UI-only, adaptive)
        |
        v
Map overlay + camera (UI)

----------------------------------------------------------------------
3) GPS cadence state machine (explicit)

States:
- SLOW (1 Hz GPS)
- FAST (5 Hz GPS)

Transitions:
- LIVE + isFlying == true  -> FAST
- LIVE + isFlying == false -> SLOW
- REPLAY (any)             -> SLOW (live sensors stopped)

Guardrails:
- No UI decides cadence.
- Only VarioServiceManager drives cadence based on FlightStateSource + activeSource.

----------------------------------------------------------------------
4) Phase plan

Phase 0: Docs + baselines
- This plan doc.
- Update mapposition.md with adaptive smoothing + cadence notes.

Phase 1: Adaptive smoothing (UI-only)
- Add a pure policy to scale smoothing constants by speed + accuracy.
- Wire policy into DisplayPoseSmoother (config remains the base).
- Unit tests for high-speed/low-speed and good/poor accuracy cases.

Phase 2: Live GPS cadence gating
- Add fast/slow intervals in SensorRegistry (dynamic update).
- Add a pure cadence policy (LIVE+flying -> FAST, else SLOW).
- VarioServiceManager observes state and applies cadence on Main thread.
- Unit tests for cadence policy (no Android).

Phase 3: Validation
- Run replay/diagnostic checks to confirm UI arrow alignment.
- Measure visual lag at cruise and verify reduced jump size.

----------------------------------------------------------------------
5) Definition of done

- Live marker lag reduced at high speed without breaking replay.
- GPS cadence switches to FAST when flying and back to SLOW on ground.
- No SSOT violations; no time-base mixing.
- Unit tests cover adaptive smoothing policy + cadence policy.

----------------------------------------------------------------------
6) Red flags (reject changes)

- UI code touching sensors.
- Display smoothing writing into repositories.
- Wall time used in navigation or replay math.
- Duplicate state owners for location or cadence.
