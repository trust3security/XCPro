# Synthetic Thermal Replay Validation Plan

## Status

- Superseded.
- The MapScreen THN/THR FABs and their synthetic replay launch path have been
  removed.
- The legacy MapScreen replay FAB entry points have also been removed.

## Current Contract

- Do not reintroduce a dedicated synthetic thermal map FAB or replay builder
  without a new change plan.
- Snail-trail and live smoothness validation should use controlled replay
  captures, live device captures, and the existing replay pipeline without
  MapScreen debug FAB launchers.
- Replay determinism remains owned by the replay controller and existing replay
  tests; UI must not own replay points or replay session state.
