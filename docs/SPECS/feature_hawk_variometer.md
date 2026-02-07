> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# Feature: HAWK-Style Variometer (Parallel, Real-Time Only)

## 1. Purpose
Provide a HAWK-style variometer output that is highly responsive to real energy changes while rejecting short-term airspeed transients and gusts.

This feature exists to:
- Give the pilot a climb/sink indication that matches high-end instruments (e.g., LXNAV HAWK behavior)
- Prevent false climb indications during rapid speed changes (pushovers, pull-ups, entry to turns)
- Remain deterministic and timebase-correct in live operation

Success criteria:
- Rapid response to real vertical air mass changes
- Minimal false lift spikes during speed-to-height or height-to-speed exchanges
- Stable on-ground output (near zero)

Non-goals:
- This feature does NOT implement audio tone mapping UI
- This feature does NOT replace existing Levo vario outputs
- This feature does NOT introduce pilot-adjustable tuning in the first iteration
- This feature does NOT support IGC replay in v1
- This feature does NOT depend on raw gyro inputs in v1

---

## 2. User-Visible Behavior

- A new HAWK Vario card appears in Flight Data -> Variometers (when enabled).
- The card displays:
  - Primary value: HAWK vario (smoothed) in m/s
  - Status row: ACCEL OK/UNREL, BARO OK/DEG, CONF HIGH/MED/LOW/--
- The value updates smoothly but reacts faster than legacy TE vario.

Modes:
- CRUISE: active
- CLIMB: active
- FINAL GLIDE: active
- REPLAY: HAWK output disabled (display shows placeholders)

Visibility:
- Controlled by SHOW_HAWK_CARD (default OFF) or shown as Beta if enabled by product decision.

---

## 3. Architectural Impact

### 3.1 SSOT Ownership

- Raw inputs:
  - Barometric pressure -> Sensor repository
  - Earth-frame vertical acceleration + reliability -> Sensor repository
  - GNSS optional (validation only, not required for core HAWK)

- Derived values:
  - HAWK vario -> HAWK engine (parallel to existing TE pipeline)

- UI state:
  - HAWK vario display value -> HawkVarioUiState (UI layer)

Statement:
> HAWK vario is derived exactly once in the HAWK engine. No other layer may re-derive or smooth it.

---

### 3.2 Data Flow (Parallel Path)

```
Sensors
  -> SensorRegistry / UnifiedSensorManager
    -> HAWK engine (baro-gated fusion)
      -> HawkVarioRepository / UseCase
        -> MapScreenViewModel
          -> HAWK Vario Card UI
```

This feature:
- [ ] Extends existing TE flight metrics flow
- [x] Adds new parallel flow
- [ ] Introduces new SSOT

---

### 3.3 Time Base

Primary time base:
- [x] Monotonic (live fusion)
- [ ] Replay clock (IGC)
- [ ] Wall time

Rules:
- Baro and IMU timestamps use monotonic sensor time
- Replay is out of scope for v1 and must not drive HAWK
- Wall time is forbidden in HAWK fusion math

---

## 4. Cadence & Performance

- Update cadence: baro-gated (~20-50 Hz depending on device)
- Gating sensor: barometric pressure
- Worst-case latency budget: < 80 ms from baro sample to UI
- Audio coupling: optional (future), not part of MVP

Classification:
- Baro-gated: YES
- Accel-only stepping: NO (accel cannot create lift without baro support)
- GPS-gated: NO
- UI-only smoothing: NO

---

## 5. Fusion Behavior (Required)

- Baro QC: outlier rejection and innovation gating to suppress pressure transients
- Adaptive accel trust: reduce accel influence when variance increases (gust/handling)
- QNH decoupled: vario physics uses pressure altitude or QNE; QNH affects display only

Fallbacks:
- Accel unreliable -> baro-only response
- Baro missing -> hold output and decay confidence (no IMU-only stepping)

---

## 6. Failure & Degradation Modes

- Missing barometer:
  - HAWK vario disabled
  - UI shows placeholder values and LOW or -- confidence

- Missing accelerometer or unreliable accel:
  - Fallback to baro-only
  - Confidence downgraded

- Stale data (beyond freshness window):
  - Output frozen
  - Confidence downgraded

Logging:
- Debug-only
- Never log raw sensor or location data in release builds

---

## 7. Replay & Determinism

- Replay is out of scope for HAWK v1
- If replay is active, HAWK output must be disabled or publish default UI state
- Determinism applies to live fusion with monotonic timestamps

---

## 8. Testing Plan

Required tests:
- Unit tests:
  - Baro QC and innovation gating
  - Adaptive accel trust and fallback behavior
  - Confidence state mapping

Integration checks:
- HAWK starts/stops with VarioServiceManager
- HAWK card updates in real time when enabled
- SHOW_HAWK_CARD OFF leaves UI unchanged

Regression risks:
- Accidental mixing with existing TE outputs
- UI-side smoothing or re-derivation
- Timebase contamination (wall time)

Statement:
> This feature is incorrect if it alters existing TE calculations or audio outputs.

---

## 9. Files & Modules Touched

Expected modifications:
- New HAWK engine package (parallel to TE pipeline)
- VarioServiceManager lifecycle wiring
- HawkVarioUiState and UI plumbing
- FlightData card registration and formatting
- Preference/flag for SHOW_HAWK_CARD

Explicitly forbidden:
- Changing existing TE calculations or outputs
- Direct sensor access from ViewModel or UI
- New singleton state
- Wall-clock time usage in fusion

---

## 10. Approval

- Spec author: XCPro
- Date: 2026-02-05
- Status: Aligned to HAWK task/contract (parallel, real-time only)
- Approved by: Pending

