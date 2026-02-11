
# HAWK_DESIGN_NOTE.md

Purpose: capture the exact architecture choices for the HAWK parallel variometer
before code changes. This is not a plan; it is a short decision record to keep
implementation consistent.

Status: Draft (confirm before implementation)
Date: 2026-02-04

## 1) Scope and non-goals

Scope (v1):
- Real-time only on phone sensors (baro + IMU + GNSS optional for validation).
- No IGC replay or replay IMU.
- No raw gyro dependency.

Non-goals (v1):
- Do not alter existing TE pipeline, outputs, or audio when HAWK is disabled.
- No attempt at true HAWK 3D wind or TAS.

## 2) Placement in the pipeline (parallel and removable)

HAWK is a separate, parallel pipeline that consumes the same sensor inputs but
produces its own output stream. It does not modify TE calculations or outputs.

Data flow (high level):
Sensors -> HawkSensorAdapter -> HawkVarioEngine -> HawkRepository (SSOT)
        -> HawkUseCase -> HawkViewModel -> UI/Audio (feature-flag gated)

Existing TE pipeline remains unchanged and is the default.

## 3) Inputs, outputs, and timebase

Inputs:
- Barometer pressure + monotonic timestamp (SensorEvent.timestamp).
- Earth-frame vertical acceleration + monotonic timestamp + reliability flag.
- Optional GNSS vertical speed for validation only (not as primary vario).

Timebase rules:
- Baro ticks are the clock. Update only on new baro samples.
- Use monotonic time for dt and staleness.
- QNH is display-only; physics uses pressure altitude/QNE.

Outputs (HawkOutput):
- v_raw (instantaneous vertical speed).
- v_audio (smoothed vertical speed for audio).
- confidence (0..1).
- qc/debug metrics (innovation, accel variance, gating flags).

## 4) Core algorithm choices

- Baro-gated fusion: never step the vertical state without a baro sample.
- Accel is a helper input only; it cannot create lift without baro support.
- Adaptive accel trust: use a rolling RMS/variance window (300-700 ms) to
  increase noise when acceleration is chattery.
- Aggressive baro QC: median/robust filtering and innovation gating to reject
  pressure transients.

Filter model (v1):
- 2-state (h, v) filter with accel as input during prediction and baro altitude
  as measurement at each baro tick.

Output smoothing:
- 1-pole low-pass to produce v_audio with small deadband/hysteresis.

## 5) Fallback behavior

- Accel unreliable or stale: baro-only behavior.
- Baro missing: hold output and decay confidence (no IMU-only stepping).

## 6) Feature flag and config

- HAWK_ENABLED flag controls all HAWK output wiring (default off).
- HawkConfig (tunable constants): accelTrustBase, accelTrustGain,
  baroNoiseBase, baroOutlierGate, audioLiftTau, audioSinkTau, deadband,
  clamps for v/a.

## 7) Integration constraints

- HAWK runs under VarioServiceManager (foreground service lifecycle).
- HAWK has its own repository as SSOT. UI and ViewModel depend on use-cases only.
- No direct UI access to repositories or sensors.
- No changes to TE by default.

## 8) Testing scope (minimum)

- Unit tests for QC filters, adaptive accel trust, and 2-state filter behavior.
- Use-case tests for repository updates and confidence gating.
- ViewModel tests for enable/disable (if UI wiring added).

## 9) Acceptance targets (minimum)

- On ground: v_audio near 0, no spikes beyond +/-0.3 m/s (tunable).
- Handling/rotation: no false lift spikes without baro support.
- In flight: fewer false lift spikes than baseline and comparable thermal entry.

## 10) Decision summary

- Parallel HAWK pipeline, fully removable.
- Baro-gated fusion with adaptive accel trust and baro QC.
- Real-time only, no replay/gyro in v1.
- QNH decoupled from physics channel.

