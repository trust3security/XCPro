
# TASK.md - Autonomous Execution Task (HAWK)

## Objective
Implement a HAWK-like variometer path that runs in real time on phone sensors,
kept separate from existing TE calculations, and tuned for fast response with
low false lift spikes.

## Scope (must hold)
- Real-time only. No IGC replay or replay IMU.
- Sensors: barometer + earth-frame vertical accel (with reliability) + GNSS optional for validation.
- No raw gyro dependency in v1.

## Key behavior requirements
- Baro-clocked, baro-gated fusion: step only on new baro samples; accel cannot create lift without baro support.
- Aggressive baro QC: outlier rejection and innovation gating to suppress pressure transients.
- Adaptive accel trust: reduce accel influence when variance increases (gust/handling).
- QNH decoupled: vario physics uses pressure altitude or QNE; QNH affects display only.

## Engine outputs
- v_raw (instantaneous), v_audio (smoothed), confidence, debug metrics (innovation, accel variance).

## Integration requirements
- Integrate under VarioServiceManager (foreground service lifecycle).
- Feature flag HAWK_ENABLED plus tunable config surface.
- Do not change existing TE calculations or outputs; HAWK path must be removable.
- Fallbacks: accel unreliable -> baro only; baro missing -> hold output and decay confidence (no IMU-only stepping).

## Acceptance criteria (minimum)
- On ground: v_audio near zero; no spikes above +/- 0.3 m/s (tunable).
- Handling/rotation: no false lift spikes without baro support.
- In flight: fewer false lift spikes than baseline and comparable or better thermal entry response.
- Logging available for baro, accel, variance, innovations, and outputs.

## Authoritative Plan (single plan)
- docs/HAWK/Agent-Execution-Contract-HAWK.md

## Constraints
- docs/RULES/ARCHITECTURE.md
- docs/RULES/CODING_RULES.md
- docs/RULES/PIPELINE.md
- docs/RULES/KNOWN_DEVIATIONS.md

## Execution Rules
- Follow docs/HAWK/AGENT_RELEASE.md without exception.
- Do not ask questions unless execution is impossible.
- Preserve behavior parity unless explicitly allowed.
- Keep HAWK fully separate from existing TE calculations.


## Feature: Add Parallel "HAWK Vario" Flight Data Card (Display-Only v1)

### Goal
Add a new Flight Data card under:
**Flight Data â†’ Card Categories â†’ Variometers â†’ HAWK Vario**
that displays **HAWK vario (smoothed)** and a **status/confidence row** in real time, in parallel to the existing Levo vario.

This is a UI + plumbing task only. It must not change current pilot-facing behavior other than showing the new card when enabled.

---

### Non-Negotiable Constraints
1. **Levo remains primary**:
   - Levo continues to drive all existing audio output, primary vario readouts, and any flight logic.
2. **HAWK is display-only in v1**:
   - The HAWK card must not change audio source, netto, STF, wind logic, or existing vario publishing.
3. **No mixing**:
   - Do not blend Levo+HAWK values. The HAWK card displays HAWK only.
4. **Safe defaults**:
   - If HAWK data is not available, card must display placeholder values and statuses (no crashes).
5. **Do not widen sensor contracts**:
   - Do not add raw gyro streams or replay IMU. Use existing earth-frame vertical acceleration + baro inputs already available.

---

### UI Requirements (Exact)
Create a new card: **"HAWK Vario"** in Variometers category.

#### Layout
- **Title**: `HAWK Vario`
- **Center (primary)**: Large text showing **HAWK Vario (smoothed)** formatted like `+2.3 m/s`
- **Bottom row**: One-line status text:
  - `ACCEL OK` or `ACCEL UNREL`
  - `BARO OK` or `BARO DEG`
  - `CONF HIGH` / `CONF MED` / `CONF LOW` / `CONF --`

#### Status Rules
- `ACCEL OK` if:
  - accel reliability flag is true AND accel sample freshness is within threshold AND accel variance below threshold
- `BARO OK` if:
  - baro samples are arriving recently AND baro rate stable AND baro innovation is not being rejected excessively
- Confidence mapping:
  - HIGH: accelOk && baroOk
  - MED: baroOk && !accelOk
  - LOW: !baroOk
  - UNKNOWN/--: no data yet

---

### Data Model (Exact)
Add a new Kotlin UI state type with exactly these fields and behavior:

- Create `HawkVarioUiState.kt` in the appropriate UI/state package
- Type: `data class HawkVarioUiState(...)` and `enum class HawkConfidence`

Required fields:
- `varioSmoothedMps: Float?`
- `varioRawMps: Float?` (optional to display; required to carry/debug)
- `accelOk: Boolean`
- `baroOk: Boolean`
- `confidence: HawkConfidence`

Optional debug fields (include if easy):
- `accelVariance: Float?`
- `baroInnovationMps: Float?`
- `baroHz: Float?`
- `lastUpdateElapsedRealtimeMs: Long?`

Required helper methods/properties:
- `formatCenterValue(): String` returns `--.- m/s` when null; else `+%.1f m/s`
- `accelStatusText`, `baroStatusText`, `confText` as specified above

---

### Plumbing Requirements (Exact)
Publish a `StateFlow<HawkVarioUiState>` (or equivalent) to the UI layer that drives Flight Data cards.

- Source: HAWK engine outputs (smoothed vario + raw vario + debug) and status inputs (accelOk/baroOk/confidence).
- If HAWK engine is not running or not producing values:
  - publish default `HawkVarioUiState()` (null vario, statuses false, confidence UNKNOWN)
  - do not throw; do not crash

---

### Feature Flag / Visibility
Add a preference/flag:
- `SHOW_HAWK_CARD` (default OFF) OR show card always but mark it clearly as Beta.
Either is acceptable, but it must be deterministic and testable.

---

### Files Allowed to Change
You MAY add or modify only what is required to:
1) publish `HawkVarioUiState`
2) register and render the new card
3) read the `SHOW_HAWK_CARD` preference/flag

Do NOT change unrelated components.

Expected touch points (names may vary by repo):
- Flight Data aggregation (where RealTimeFlightData/card model is constructed)
- dfcards registration/render pipeline for Variometers category
- preference repository for show/hide toggle
- minimal wiring inside the service/lifecycle manager if required to obtain HAWK outputs

---

### Acceptance Criteria (Must Pass)
1. App builds successfully.
2. With `SHOW_HAWK_CARD=OFF`, the UI is unchanged from current behavior.
3. With `SHOW_HAWK_CARD=ON`, a new `HAWK Vario` card appears in Variometers category.
4. Card updates in real time during flight.
5. Levo audio and all existing vario outputs behave exactly as before (no regressions).
6. When HAWK data is missing/unavailable, card shows `--.- m/s` and `CONF --` without crashes.

---

### Verification Steps
- Run: `./gradlew clean testDebugUnitTest lintDebug assembleDebug`
- Manual:
  - Toggle `SHOW_HAWK_CARD` ON â†’ card visible & updating
  - Toggle OFF â†’ card disappears, no other changes
  - Confirm audio source remains Levo

---

### Out of Scope (Explicitly Not in This Task)
- Making HAWK primary vario
- Driving audio from HAWK
- Any TEK changes or speed-to-height compensation changes
- Adding raw gyro pipeline
- Any replay/IGC work

## Stop Condition
STOP only when all phases are complete and all checks pass.

