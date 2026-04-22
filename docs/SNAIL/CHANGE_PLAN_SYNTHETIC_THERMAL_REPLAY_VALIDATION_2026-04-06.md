# Synthetic Thermal Replay Validation Plan

## Status

- Superseded.
- The MapScreen THN/THR FABs and their synthetic replay launch path have been
  removed.
- The active debug replay entry points are now SIM/SIM2/SIM3 and TASK replay.

## Current Contract

- Do not reintroduce a dedicated synthetic thermal map FAB or replay builder
  without a new change plan.
- Snail-trail and live smoothness validation should use controlled replay
  captures, live device captures, and the existing replay pipeline.
- Replay determinism remains owned by the replay controller and existing replay
  tests; UI must not own replay points or replay session state.
