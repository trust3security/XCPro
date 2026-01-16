# Levo Replay Notes

This document captures replay-specific behavior, pitfalls, and debugging
notes. Keep it short, factual, and updated when replay logic changes.

Required reading order:
1) `ARCHITECTURE.md`
2) `CODING_RULES.md`
3) `CONTRIBUTING.md`
4) `docs/LevoVario/levo.md`

------------------------------------------------------------------------------
KEY RULES
------------------------------------------------------------------------------
- Replay uses IGC timestamps as the simulation clock.
- Do not mix replay timestamps with wall clock time.
- Live monotonic timestamps are irrelevant in replay mode.
- If you change cadence or validity windows, update tests and notes.

------------------------------------------------------------------------------
ENTRY POINTS
------------------------------------------------------------------------------
- `IgcReplayController` orchestrates replay start/stop and state.
- `ReplaySensorSource` emits sensor flows.
- `ReplaySampleEmitter` converts IGC points to sensor samples.

------------------------------------------------------------------------------
KNOWN PITFALLS
------------------------------------------------------------------------------
- Avoid regressions in vario validity windows when changing replay cadence.
- Ensure QNH jump behavior matches live vs replay rules (no filter resets in replay).

------------------------------------------------------------------------------
DEBUGGING CHECKLIST
------------------------------------------------------------------------------
- Verify replay timestamp monotonic progression (IGC time).
- Confirm FlightDataRepository Source.REPLAY gating works.
- Compare replay vario with expected IGC climb/sink segments.
