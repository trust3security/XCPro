# PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md

## Scope

Implement the phone-only **Wind / TAS proxy / IAS proxy / TE gating / Netto tiering** plan exactly as defined in:
- `docs/GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md` (authoritative spec)
- Must remain compliant with `ARCHITECTURE.md` and `CODING_RULES.md` (timebase, SSOT, DI, testability). ^filecite^turn5file1^ ^filecite^turn5file2^

This plan is **phased** so Codex can execute it end-to-end with tests, without ad-hoc choices.

---

## Preflight (must run before and after each phase)

Run locally:
- `./gradlew enforceRules` ^filecite^turn5file2^
- `./gradlew testDebugUnitTest`
- `./gradlew lintDebug`
- `./gradlew assembleDebug`

If replay tests exist, also run the deterministic replay gate required by CODING_RULES.md. ^filecite^turn5file2^

---

## Phase 0 -- Repo reconnaissance (no behavior changes)

### Goal
Map the current ownership and call sites so later changes are surgical.

### Tasks
1. Identify current wind state model + storage:
   - Locate `WindState`, `WindVector`, store/repository, and how it reaches `CalculateFlightMetricsUseCase`.
2. Identify current airspeed estimation path:
   - `WindEstimator.fromWind(...)` already computes TAS and IAS proxy.
3. Identify where wind estimation updates happen (circling wind):
   - Find the component that produces circling wind measurements and persists them.

### Output artifacts (required)
- `docs/WIND_PIPELINE_MAP.md` (short, factual flow map)
- List of touch points" (files + responsibilities)

### Tests
No new tests yet.

---

## Phase 1 -- Add explicit quality/validity models (domain-only)

### Goal
Make truthiness" explicit and enforceable downstream.

### New domain models (pure Kotlin, no Android)
Create:
- `AirspeedQuality.kt`
  - `enum class AirspeedTier { A, B, C }`
  - `data class AirspeedValidity(val tasValid: Boolean, val iasValid: Boolean, val tier: AirspeedTier, val reason: String)`
- `WindQuality.kt`
  - `data class WindQuality(val score: Double, val ageSeconds: Double, val isStale: Boolean, val reason: String)`

Rules:
- Quality/validity must be derived from inputs only (deterministic).
- No wall clock calls; time values are passed in (monotonic/replay-safe). ^filecite^turn5file1^

### Integrate into `FlightMetricsResult`
Extend `FlightMetricsResult` to include:
- `windQualityScore: Double`
- `windAgeSeconds: Double`
- `tasValid: Boolean`
- `iasValid: Boolean`
- `airspeedTier: String` (ASCII only)
- `airspeedReason: String` (ASCII only)

### Tests (JVM)
Add tests that validate tiering logic:
- Wind score below threshold -> TAS invalid -> IAS invalid -> tier C
- Wind score high + age low -> TAS valid -> IAS valid -> tier A

---

## Phase 2 -- Wind persistence and staleness (repository + domain contract)

### Goal
Wind must persist for long durations and degrade gracefully, never drop to zero" unexpectedly.

### Repository responsibilities (SSOT)
Implement or update the Wind repository/store so it:
- Stores last good wind vector + timestamp + altitude + quality score
- Computes age using injected monotonic clock in live mode; replay timestamp in replay mode
- Exposes wind as `Flow<WindState?>` (SSOT)

Timebase rules:
- Live: monotonic time for age/deltas
- Replay: IGC time
- Wall time only for UI labels (never for validity math). ^filecite^turn5file1^

### Domain contract
`CalculateFlightMetricsUseCase` must receive wind as an already-formed `WindState` (or null).
The use case must never fetch from repositories directly (DI and layering). ^filecite^turn5file1^

### Tests
- Wind persists across simulated time progression
- Wind becomes stale after threshold but is not erased
- Replay time regressions reset/handle safely (if applicable)

---

## Phase 3 -- Freeze wind learning during glide; hold wind through turns

### Goal
Stop trying to learn" wind in straight flight (phone-only).
Update wind only via circling method. Hold wind during circling and between circles.

### Implementation
Locate wind-learning component and enforce:
- Only update wind when circling and full-circle criteria met
- During glide: freeze updates (no EKF, no straight-flight fitting)

### Tests
- Feed sequences: glide -> circle -> glide
- Assert wind updates only during circle
- Assert wind remains constant through glide

---

## Phase 4 -- TAS proxy gating and hold-through-turns

### Goal
TAS proxy exists only when wind is valid. Hold TAS during circling.

### Implementation
In the domain path where TAS is computed (currently `WindEstimator.fromWind`):
- Continue computing TAS from `ground - wind_to` only when wind is present
- Add validity gating:
  - `tasValid = windQuality.score >= MIN_SCORE && windAgeSeconds <= MAX_AGE && tasProxy >= MIN_TAS`
- Add hold behavior:
  - If circling: do not recompute TAS from noisy ground changes; hold last valid TAS proxy
  - If wind invalid: TAS invalid (do not substitute ground speed)

Note:
- IAS proxy must exist only if TAS valid.

### Tests
- Circling + noisy ground speed -> TAS stays stable (held)
- Wind invalid -> TAS invalid -> IAS invalid
- Wind stale -> tier B -> TAS held and marked degraded

---

## Phase 5 -- TE gating (no TE without TAS)

### Goal
TE is computed only when TAS is valid and meaningful.

### Implementation
Update TE logic in `CalculateFlightMetricsUseCase` so:
- TE vario is computed only if TAS valid (not merely has a speed")
- Never run TE on ground-speed fallback

### Tests
- TAS invalid -> TE null -> pressure/baro vario path used
- TAS valid -> TE computed and used

---

## Phase 6 -- Netto tiering + S100-style settings surface

### Goal
Netto behavior matches pilot expectations:
- Tier A: S100-like"
- Tier B: degraded (heavy smoothing)
- Tier C: invalid/hidden

### Implementation
- Add netto tier selection driven by airspeed tier:
  - Tier A: normal netto smoothing, selectable averaging (5s/10s)
  - Tier B: force slower netto averaging (>= 10s) and mark degraded
  - Tier C: netto invalid (do not display, or display placeholder)

Important:
- Do not use ground speed for polar sink as if it were TAS.
  - If you choose to compute a fallback netto", it must be labeled Tier B and smoothed heavily.

### Tests
- Tier C hides netto
- Tier B shows degraded netto and never reports valid"
- Tier A behaves with selected averaging window

---

## Phase 7 -- UI indicators (non-authoritative)

### Goal
Make signal quality visible without polluting SSOT.

### UI Rules
- UI only renders:
  - TAS/IAS values
  - Tier badge (A/B/C)
  - Degraded" / Invalid" indicators
- No UI-driven math, no writing smoothed values back. ^filecite^turn5file1^

### Tests
- ViewModel state renders correct tier + label
- No data-layer imports in UI

---

## Phase 8 -- Deterministic replay gate

### Goal
Prove the full pipeline is deterministic across replays.

### Add/extend tests
- Replay the same IGC twice
- Assert identical:
  - wind state sequence
  - TAS/IAS validity sequence
  - Netto tier sequence
  - TE enable/disable sequence

Required by CODING_RULES.md. ^filecite^turn5file2^

---

## Acceptance Criteria (Release Grade)

A change is complete only if:
- All phases implemented with tests
- `KNOWN_DEVIATIONS.md` remains empty (no rule breaks) ^filecite^turn5file4^
- No wall time usage in domain/fusion
- TAS/IAS never pretend-valid"
- Netto tiering is consistent and visible to pilot

---

## Notes for Codex (hard constraints)

- No ad-hoc constants: all thresholds must be declared centrally and documented.
- No new singletons: DI only.
- No Android types in domain.
- ASCII only in production Kotlin.

