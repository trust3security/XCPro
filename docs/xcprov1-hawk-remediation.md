# XCPro V1 HAWK Variometer Remediation Plan

## Context

Field testing of the XCPro V1 (HAWK-mode) variometer shows two critical failures:

1. Unrealistic climb/airmass numbers (hundreds of m/s).
2. Continuous “lift” audio tone, even in still air.

A code trace pins both symptoms to modelling defects in the JS1C aerodynamic helpers and the Kalman propagation loop. This document captures the root cause analysis, corrective plan, and verification strategy so fixes can be executed consistently across the app and shared to downstream consumers (audio, UI, telemetry exporters).

---

## Root Cause Summary

| Area | File / Lines | Issue | Impact |
| ---- | ------------ | ----- | ------ |
| Sink polar scaling | `app/src/main/java/com/example/xcpro/xcprov1/filters/Js1cAeroModel.kt:35-58` | The fitted cubic polynomial expects the TAS input to be scaled (same units as the polar data, i.e., km/h mapped to 0.1 × v). The implementation feeds raw TAS in m/s, producing sink rates that are 100× too large and negative. | Potential climb, netto, and audio anticipatory term explode, driving the UI and tone generator into steady “max lift”. |
| Altitude propagation | `app/src/main/java/com/example/xcpro/xcprov1/filters/XcproV1KalmanFilter.kt:124-133` | Altitude is integrated using `(climbRate + verticalWind)`. Because `climbRate` already represents the glider’s absolute vertical speed (and GPS updates are applied to that state), adding `verticalWind` double-counts airmass motion. | Filter believes it must keep `climbRate` positive to match the baro measurement, biasing “actual climb” high and preventing sink detection. |

Secondary contributors (to be inspected during implementation):

- TAS floor of 10 m/s (`XcproV1Controller`) may mask float operations once the polar is corrected.
- Sink warning logic (`XcproV1AudioEngine`) ties to `netto` and `verticalSpeed`; confirm thresholds once the dynamics are fixed.

---

## Remediation Plan

### 1. Aerodynamic Model Corrections

1. **Normalise TAS input**  
   - Update `Js1cAeroModel.sinkRate()` to convert TAS from m/s to the unit used when fitting (`km/h` scaled to 0.1).  
   - Store the scaling constant with the coefficients (`POLAR_UNIT_SCALE = 3.6 / 10`).

2. **Return positive sink**  
   - Ensure the polynomial evaluates to positive sink (magnitude) and invert the sign only when combining with climb calculations.
   - Introduce unit tests that assert realistic values: `sinkRate(50 km/h) ≈ 0.5 m/s`, `sinkRate(120 km/h) ≈ 2.0 m/s`.

3. **Circle polar consistency**  
   - Recalculate `circleSink` using the corrected base sink.  
   - Add tests for a 45° bank at thermalling speed to confirm induced-sink ≈ 0.8 m/s.

### 2. Kalman Prediction Fixes

1. **State propagation**  
   - Change altitude integration to use `state.altitude + state.climbRate * dt`.  
   - Keep `verticalWind` separate; it should only influence climb via the measurement updates and the output `potentialClimb`.

2. **Process noise review**  
   - Revisit `qAltitude`, `qClimb`, and `qWind` once numerical ranges stabilise. Large sink changes may require scaling `accelSigma` down.

3. **Unit tests / simulations**  
   - Write JVM tests that simulate level flight with zero acceleration and confirm altitude stays constant.  
   - Add a harness that feeds a manoeuvre (e.g., 2 m/s thermal) and assert convergence of `actualClimb` toward sensor truth.

### 3. Controller and Audio Checks

1. **True airspeed (TAS) handling**  
   - After fixing the polar, re-evaluate the `maxOf(airVectorMagnitude, 10.0)` floor in `XcproV1Controller`. Set a more realistic minimum (e.g., 15 m/s) or derive it from glider polar minima.

2. **Sink warning thresholds**  
   - Verify `computeSinkWarning()` still trips near −1.0 m/s once state values are realistic. Adjust thresholds if necessary.

3. **Confidence weighting**  
   - With cleaner covariance evolution, confirm `computeConfidence()` yields ~0.8 in steady flight and decays during turbulence. If needed, recalibrate `maxTrace`.

### 4. Telemetry & UI Alignment

1. **Dashboard readouts**  
   - After the state fixes, confirm `FlightDataV1Snapshot.actualClimb` and `potentialClimb` align with recorded flights (±0.2 m/s).

2. **Audio telemetry**  
   - Ensure `AudioTelemetry` exposes the new values so on-screen diagnostics match audible cues.

---

## Implementation Checklist

1. Patch `Js1cAeroModel` with unit scaling + new tests.
2. Update `XcproV1KalmanFilter` prediction step, add level-flight regression tests.
3. Re-run instrumented or JVM simulations to sanity check convergence.
4. Confirm audio behaviour using logcat (frequency, duty cycle) in static conditions.
5. Update release notes and user documentation for the HAWK dashboard.

---

## Verification Strategy

| Layer | Test / Tool | Expected Outcome |
| ----- | ------------ | ---------------- |
| Unit | `Js1cAeroModelTest` (new) | Sink rates match published JS1C polar within ±0.1 m/s across 80–160 km/h. |
| Unit | `XcproV1KalmanFilterTest` (new harness) | Level-flight simulation keeps altitude drift < 0.5 m over 60 s; thermal case converges to 2 m/s climb within 5 s. |
| Integration | Replay recorded flight log (if available) | Actual/potential climb traces overlay with LXNAV reference within ±0.3 m/s. |
| Manual | Device flight mode with static sensors (ground test) | Gauge shows ~0 m/s, audio silent; tilting device generates consistent short tone matching measured climb. |
| Manual | In-flight validation (glider) | Pilot feedback confirms dual-needle behaviour mirrors real HAWK, and audio transitions cleanly between sink and lift. |

Artifacts from tests (plots, logs) should be attached to the PR or stored under `docs/validation/`.

---

## Open Questions / Follow-up

- Should we expose wing loading as a user tunable so the polar matches their ballast configuration?  
- Do we need an automated regression replay suite (e.g., using recorded sensor CSVs) to prevent future dynamical regressions?  
- Once the model is corrected, do we re-tune `XcproV1FrequencyMapper` to closer match LXNAV’s tone tables?

---

## References

- LXNAV SxHAWK User Manual Rev.6 (April 2025), sections 5.6 & 7: graphical display, HAWK algorithm behaviour.
- XCPro sensor architecture `UnifiedSensorManager.kt` (baro/gps/IMU fusion entry point).
- Current test artefacts (to be generated post-fix).***
