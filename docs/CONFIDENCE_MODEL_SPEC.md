# CONFIDENCE_MODEL_SPEC.md

## Purpose

Define a **single, explicit, deterministic confidence model** for XC Pro's flight pipeline.
Confidence is used to prevent "confidently wrong" outputs and to stabilize behavior on phone sensors.

This spec is normative and must remain compliant with:
- `ARCHITECTURE.md` (timebase, SSOT, DI, determinism) 
- `CODING_RULES.md` (no wall time in domain, tests, ASCII hygiene) 

---

## Core Principles

1. **Confidence is data**: a first-class signal that flows through the same pipeline as metrics.
2. **No implicit validity**: every derived quantity must have explicit validity + confidence.
3. **Deterministic**: confidence updates must depend only on inputs + provided timestamps.
4. **Prefer invalid over wrong**: when uncertain, reduce confidence, degrade tier, or hide.

---

## Timebase Rules (Hard)

- Live confidence math uses **monotonic time**.
- Replay confidence math uses **replay timestamps** (IGC clock).
- Domain/fusion code must not call any system clocks. 

---

## Confidence Domains

XC Pro must maintain *separate* confidence signals:

### 1) Baro/Vario Confidence
Indicates whether barometric vertical speed is trustworthy.

### 2) GNSS Confidence
Indicates whether ground speed/track is trustworthy.

### 3) Wind Confidence
Indicates whether stored wind is trustworthy.

### 4) Airspeed Confidence (derived)
Indicates whether TAS/IAS proxies are trustworthy.

### 5) Netto Confidence (derived)
Indicates whether netto is trustworthy.

Confidence signals are not interchangeable; combine explicitly.

---

## Data Model (Domain)

Create pure-Kotlin domain models (ASCII-only production Kotlin):

- `data class Confidence(val score: Double, val reason: String)`
  - `score` in [0.0, 1.0]
  - `reason` is ASCII and short (<= 80 chars)
- `enum class ConfidenceGrade { HIGH, MEDIUM, LOW, INVALID }`
- `data class ConfidenceSnapshot(...)`
  - holds baro, gnss, wind, airspeed, netto confidence + grades

Mapping:
- HIGH: score >= 0.85
- MEDIUM: score >= 0.65
- LOW: score >= 0.35
- INVALID: score < 0.35 (or explicit invalid inputs)

---

## Baro/Vario Confidence

### Inputs
- baro sample cadence (delta time)
- spike detection (pressure jump)
- "disturbance" detection (unphysical pressure artifacts)
- device warm-up window (first N seconds after start)

### Deterministic rules
- If dt is too large (dropout): reduce confidence proportionally.
- If pressure spike exceeds threshold: mark INVALID for that sample and decay confidence.
- Warm-up: ramp confidence from LOW -> MEDIUM over configured seconds.

### Required behavior
- Baro confidence must never instantly jump from INVALID to HIGH without a ramp.

---

## GNSS Confidence

### Inputs
- fix age (elapsed time since last GNSS update)
- speed magnitude (low speeds have poor track)
- bearing stability (rapidly changing track at low speed is noise)
- optional: reported accuracy if available (but must be optional)

### Rules
- If fix age > threshold: INVALID
- If speed < minimum moving speed: reduce confidence (track unreliable)
- If bearing is NaN or unstable: reduce confidence

---

## Wind Confidence

### Source constraints
Phone-only wind updates are allowed only from circling method (full circle).

### Inputs
- angular coverage achieved
- speed stability during the circle
- age since last update
- number of independent measurements stored
- altitude consistency weighting

### Rules
- New wind measurement produces an initial score based on coverage/stability.
- Wind confidence decays with age, slowly (order of hour).
- Wind confidence never becomes "zero wind"; it becomes stale/low-confidence.

---

## Airspeed Confidence (TAS/IAS proxies)

### Derived from
- GNSS confidence
- Wind confidence

### Rules
- TAS valid only if wind confidence >= MEDIUM and wind age <= max age.
- IAS valid only if TAS valid.
- Airspeed confidence = min(gnss.score, wind.score) with additional gating
  for speed thresholds.

---

## Netto Confidence

### Derived from
- Baro confidence (or TE confidence if TE is used)
- Airspeed confidence
- Polar availability validity

### Rules
- Tier A (instrument-grade phone) requires:
  - baro/TE confidence >= MEDIUM
  - airspeed confidence >= MEDIUM
- Tier B requires:
  - baro confidence >= LOW
  - wind confidence >= LOW (stale permitted)
- Tier C when either:
  - wind confidence INVALID
  - baro confidence INVALID
  - speed below flying threshold

Netto confidence must never be HIGH when airspeed confidence is LOW.

---

## Persistence and Smoothing of Confidence

Confidence must be *stable* (no flicker):

- Use exponential smoothing on confidence scores (domain-owned).
- Confidence score changes must have max slope per second (optional but recommended).
- Reasons should reflect dominant failing signal (e.g. "wind stale", "gnss fix old").

---

## Output Contract

Extend `FlightMetricsResult` (or a dedicated domain output model) to include:
- `baroConfidenceScore`, `gnssConfidenceScore`, `windConfidenceScore`
- `airspeedConfidenceScore`, `nettoConfidenceScore`
- Grade labels: `baroConfidenceGrade`, etc (ASCII strings)

---

## Testing Requirements (JVM)

Must add unit tests for:
- dropout reduces baro confidence
- pressure spike produces INVALID and decays
- low speed reduces GNSS confidence
- wind confidence decays slowly with age
- airspeed confidence never exceeds wind/gnss min
- netto tiering aligns with confidence

No Android framework.

---

## Centralized Thresholds (No Ad-Hoc)

All thresholds must live in one place, e.g.:
- `FlightMetricsThresholds.kt` (domain constants)
- Document each constant in KDoc with rationale and units.

Forbidden:
- magic numbers in use cases
- per-callsite thresholds

---

## Notes

The confidence model is the backbone of pilot trust.
If uncertain:
- reduce confidence
- degrade tier
- hide outputs

Never lie.


