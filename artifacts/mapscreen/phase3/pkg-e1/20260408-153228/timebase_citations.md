# Timebase Citations

Required citations:

1. `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
2. `app/src/main/java/com/example/xcpro/di/TimeModule.kt`

Package focus:

- `pkg-f1`: lifecycle and cadence-owner proof remains runtime-scoped; replay/live display-frame ownership stays explicit and timebase-safe.
- `pkg-d1`: ADS-B visual smoothing and OGN selection/allocation hot paths use monotonic and deterministic runtime contracts.
- `pkg-g1`: replay selection projection and dense SCIA trail rendering paths remain semantic and avoid wall-time coupling.
- `pkg-w1`: weather rain cache identity and frame transitions remain visual-layer behavior with no fusion/replay timebase coupling.
- `pkg-e1`: mixed-load cadence governance remains visual/runtime scoped; replay determinism checks use runtime cadence ownership tests with no wall-time coupling.
