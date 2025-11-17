# Netto 30-Second Average - XCSoar vs XCPro

## XCSoar 7.24 (reference implementation)

Source: `src/Computer/AverageVarioComputer.{hpp,cpp}` in the XCSoar tree.

- Keeps two `WindowFilter<30>` buffers (brutto + netto). Each contains exactly 30 slots.
- Every time `Compute()` runs it:
  1. Uses `DeltaTime` to find how many whole seconds elapsed since the previous call.
  2. If the clock moved backwards or the circling flag toggled, it resets both filters and seeds them with the current instantaneous readings.
  3. For every elapsed second it writes the current netto value into the circular buffer (overwriting oldest entries once full).
  4. The average returned to UI is the straight arithmetic mean of those 30 samples.
- Samples are always written, even if the instantaneous nettos are flagged stale. That keeps the 30-second window filled at all times.

## XCPro (before this patch)

Relevant files: `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt` and `TimedAverageWindow.kt`.

- We used a time-based deque (`TimedAverageWindow`) that drops samples older than 30_000 ms. That gives reasonable smoothing but differs from XCSoar in two crucial ways:
  1. We skipped adding samples whenever `nettoValid` was false. If the sensor feed was interrupted, the deque emptied out and the mean fell all the way to 0.
  2. We never reset the window when circling mode changes or the clock jumps backwards, so cruise samples leaked into thermal averages and vice versa.
- As a result pilots saw "Netto 30 s" stick near zero or lag the real air mass for tens of seconds after a mode change.

## XCPro (current implementation)

Relevant files: `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt` and `FixedSampleAverageWindow.kt`.

- Replaced the 30 s `TimedAverageWindow` with a `FixedSampleAverageWindow(30)` that mirrors XCSoar's `WindowFilter<30>` behaviour (constant slot count, circular overwrite, arithmetic mean).
- `updateAverageWindows()` seeds both brutto and netto buffers whenever the derived thermal/circling state toggles or when the sampling timestamp moves backwards, matching XCSoar's reset semantics.
- Between resets we add exactly one sample per elapsed second (based on `lastBruttoSampleTime` and `lastNettoSampleTime`). Long pauses simply enqueue multiple copies of the most recent instantaneous reading so the 30-slot buffer stays populated.
- When `nettoValid=false`, we feed the window with the last known valid netto instead of clearing it; this keeps `nettoAverage30s` stable through short dropouts, just like XCSoar which always writes the current netto.
- The UI still exposes the 5 s time-weighted `nettoDisplayWindow` for short-term smoothing, but the published `nettoAverage30s` now tracks XCSoar within a few cm/s during mode switches and replays.
