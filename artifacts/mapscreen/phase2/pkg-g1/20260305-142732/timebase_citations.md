# Timebase Citations

Required citations:

1. core/time/src/main/java/com/example/xcpro/core/time/Clock.kt
2. pp/src/main/java/com/example/xcpro/di/TimeModule.kt

Hotspot bindings in this package:

- ADS-B visual smoothing and overlay animation paths use monotonic time (TimeBridge.nowMonoMs) and keep sample-time transitions monotonic.
- Replay observer projection changes are semantic (selection != null) and avoid wall-time dependence.
- No domain/fusion timebase rule changes were introduced.
