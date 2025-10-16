# Variometer System Analysis

Date: 2025-10-12  
Status: Updated after 50 Hz refactor  
Priority: High – safety critical flight instrument

---

## Table of Contents

1. [Architecture Snapshot](#architecture-snapshot)  
2. [Implementation Details](#implementation-details)  
3. [Energy Compensation & Derived Varios](#energy-compensation--derived-varios)  
4. [Diagnostics & Telemetry](#diagnostics--telemetry)  
5. [Findings & Open Risks](#findings--open-risks)  
6. [Recommended Next Steps](#recommended-next-steps)  
7. [Validation Checklist](#validation-checklist)  
8. [References](#references)

---

## Architecture Snapshot

- **Split sensor loops**: `app/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt:135` runs a 50 Hz baro+IMU loop for instantaneous vertical speed, while GPS/compass stay on a slower 10 Hz track loop.
- **Stateful filters**: The high-speed loop feeds five `IVarioCalculator` implementations (`app/src/main/java/com/example/xcpro/vario`) plus the shared `Modern3StateKalmanFilter`.
- **Total Energy (TE) pipeline**: TE compensation is applied in the slow loop before publishing `CompleteFlightData` and driving audio (`FlightDataCalculator.kt:359`).
- **Audio integration**: TE vertical speed is streamed into `VarioAudioEngine` for zero‑lag beeps (`app/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt:101`).
- **Cached GPS**: The vario loop reuses last-known GPS metrics so accelerometer fusion can run between GNSS updates.

---

## Implementation Details

### High-speed vario loop

- `updateVarioFilter` (`FlightDataCalculator.kt:188`) differentiates baro altitude every ~20 ms and, when the IMU is trustworthy (`speed ≥ 1 m/s`, acceleration within ±12 m/s²), fuses accelerometer data.
- When fusion toggles on/off, all filters reset to avoid stale bias (`FlightDataCalculator.kt:214`).
- If IMU cannot be trusted, the code falls back to `AdvancedBarometricFilter` (legacy 2‑state smoother) and keeps the loop alive.

### Modern 3-state Kalman filter

- `Modern3StateKalmanFilter` (`dfcards-library/src/main/java/com/example/dfcards/filters/Modern3StateKalmanFilter.kt:26`) maintains altitude, velocity, and acceleration states.
- Measurement noise has been tightened to `R_altitude = 0.5 m`, `R_accel = 0.3 m/s²`, scaled by motion state (`Modern3StateKalmanFilter.kt:92`).
- Process noise adapts to detected maneuver intensity, yielding faster reaction in thermals while damping cruise noise (`Modern3StateKalmanFilter.kt:241`).
- Innovation clamping prevents pressure spikes from blowing up the estimate and forces a soft reset if three spikes arrive consecutively (`Modern3StateKalmanFilter.kt:146`).
- Deadband is now 0.02 m/s (4 fpm) to retain weak-lift sensitivity (`Modern3StateKalmanFilter.kt:198`).

### Supporting vario calculators

- `OptimizedKalmanVario` (`app/src/main/java/com/example/xcpro/vario/OptimizedKalmanVario.kt:22`) is a thin wrapper around the modern filter so the UI can compare “optimized” vs “legacy”.
- `LegacyKalmanVario` (`app/src/main/java/com/example/xcpro/vario/LegacyKalmanVario.kt:20`) preserves the old `R_altitude = 2 m` behavior for regression checks.
- `RawBaroVario`, `GPSVario`, and `ComplementaryVario` (`app/src/main/java/com/example/xcpro/vario`) provide baseline, slow reference, and fast complementary alternatives respectively. The complementary filter is tuned with 0.92/0.08 blending and high-pass bias removal (`dfcards-library/src/main/java/com/example/dfcards/filters/ComplementaryVarioFilter.kt:29`).

### Audio path

- `VarioAudioEngine` consumes the TE vertical speed to drive professional tone mapping (XCTracer profile and hysteresis) and runs continuously once `initialize()` succeeds.
- Audio updates run inside the 50 Hz loop, so any regression in vario latency is immediately audible.

---

## Energy Compensation & Derived Varios

- **Total Energy (TE)**: `FlightCalculationHelpers.calculateTotalEnergy` (`app/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt:249`) adds the kinetic energy term `(v²_current - v²_previous) / (2g Δt)` to raw vertical speed. This output replaces `verticalSpeed` in published flight data (`FlightDataCalculator.kt:359`).
- **Netto**: `calculateNetto` (`FlightCalculationHelpers.kt:292`) adds the polar sink-rate lookup based on ground speed; results flow into `CompleteFlightData.netto` (`FlightDataCalculator.kt:396`).
- **Thermal averaging / L/D**: Helpers maintain sliding windows for thermal strength, wind, and glide ratio, reusing TE outputs to avoid stick thermals in statistics (`FlightCalculationHelpers.kt` overall).
- **Side-by-side metrics**: The slow loop exposes optimized, legacy, raw, GPS, and complementary vertical speeds for UI diagnostics (`FlightDataCalculator.kt:401`).

---

## Diagnostics & Telemetry

- `VarioFilterDiagnosticsCollector` tracks innovation variance, sample rate, and sensor health (`dfcards-library/src/main/java/com/example/dfcards/filters/VarioFilterDiagnostics.kt:55`). Stats are available via `Modern3StateKalmanFilter.getDiagnosticStats()` but are not yet surfaced in UI or logs.
- High-speed loop has periodic debug output placeholders intended to log TE vs optimized values (`FlightDataCalculator.kt:304`), though string interpolation is currently missing (see risks below).
- Audio engine keeps counters and mode state in `StateFlow`s for potential UI bindings (`VarioAudioEngine.kt:52`).

---

## Findings & Open Risks

1. **Ground speed proxy for TE / Netto** – Both TE and Netto use GPS ground speed; in strong winds this diverges from true airspeed, so TE may under- or over-compensate (`FlightCalculationHelpers.kt:249`, `:292`). Consider integrating a TAS estimate.
2. **First-sample TE spike** – `previousGPSSpeed` starts at 0.0, so the first TE update after takeoff can produce a spurious climb/descent until one GPS cycle completes. A guard that seeds `previousGPSSpeed` with the current reading would remove the transient.
3. **Logging placeholders** – Diagnostic logs in `updateVarioFilter` and the 50 Hz status message omit variable values (`FlightDataCalculator.kt:218`, `:304`). This makes field debugging harder; string interpolation needs to be wired back in.
4. **Complementary filter tuning** – Coefficients are fixed (0.92/0.08, bias time constant 5 s). We lack runtime calibration or dynamic weighting, so noise behavior in turbulence is unknown.
5. **Diagnostics not exposed** – We now collect rich Kalman stats but nothing consumes them. Surfacing them in `CompleteFlightData` or a dev overlay would help validate noise assumptions and the spike clamp resets.
6. **IMU trust gate** – Fusion toggles purely on ground speed and accelerometer magnitude (`FlightDataCalculator.kt:206`). If the IMU delivers stale reliability flags we could thrash between modes; adding hysteresis or minimum dwell time would stabilize mode switching.
7. **Audio error handling** – Audio engine initialization happens on launch, but failures only log a warning. We should expose status upstream so pilots know if tones are muted.

---

## Recommended Next Steps

1. Seed `previousGPSSpeed` on first GPS update and add optional exponential smoothing to the kinetic energy term to curb GPS jitter.
2. Feed `diagnosticsCollector.getStats()` into telemetry logs and surface key metrics (sample rate, innovation RMS) on the developer HUD.
3. Restore formatted values in `Log.d` calls for IMU fusion transitions and 50 Hz snapshots.
4. Experiment with dynamic complementary weights (e.g., blend ratio driven by innovation variance) and compare against Kalman output in recorded flights.
5. Evaluate TAS estimation options (GPS + wind estimate, or IMU integration) so TE and Netto stay accurate in headwinds/tailwinds.
6. Add automated tests around `calculateTotalEnergy` and `calculateNetto` to guard against regressions in the energy math.

---

## Validation Checklist

- [ ] Stationary ground test: noise < 0.02 m/s for optimized Kalman, no audio beeps.  
- [ ] Stick-thermal maneuver: TE vertical speed near zero while raw vario spikes.  
- [ ] Weak lift (0.2 m/s) detection within 0.5 s by optimized Kalman and complementary filters.  
- [ ] Strong sink alarm: audio engine triggers sink profile below −2 m/s.  
- [ ] GPS dropout simulation: high-speed loop continues smoothly with cached data, TE output recovers without spikes.  
- [ ] Crosswind flight: compare TE vs external vario to quantify wind-induced error.

---

## References

- `app/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt` – sensor fusion orchestrator.  
- `app/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt` – TE, Netto, wind, L/D helpers.  
- `dfcards-library/src/main/java/com/example/dfcards/filters/Modern3StateKalmanFilter.kt` – primary Kalman implementation.  
- `dfcards-library/src/main/java/com/example/dfcards/filters/ComplementaryVarioFilter.kt` – zero‑lag complementary filter.  
- `app/src/main/java/com/example/xcpro/audio/VarioAudioEngine.kt` – audio feedback path.  
- `app/src/main/java/com/example/xcpro/vario` – comparison vario calculators (raw, legacy, GPS, complementary).

