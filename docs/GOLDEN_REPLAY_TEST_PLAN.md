# GOLDEN_REPLAY_TEST_PLAN.md

## Purpose

Create a **golden replay regression suite** that makes XC Pro changes safe.

This plan enforces:
- Determinism (same input -> same output)
- Stability (no flicker or tier oscillation)
- Correct gating (TE/netto only when valid)

Required by `CODING_RULES.md` determinism gates. 

---

## Golden Flight Set

Create `feature/map/src/test/resources/igc/golden/` containing a small curated set of IGC files.
Each file must have a short README explaining what it exercises.

Recommended minimum set (5 files):
1. **Launch + immediate turning** (many turns early)
2. **Long steady glide** (stable cruise)
3. **Thermalling with drift** (multiple circles, varying wind)
4. **Weak conditions** (low speeds, noisy segments)
5. **Dropouts / gaps** (GNSS or baro gaps)

If you cannot add real IGCs to the repo, add synthetic/replayed fixtures that mimic these patterns.

---

## What to Record (Deterministic Metrics)

During replay, capture a compact per-tick record of:

- timestamps (replay clock)
- wind vector (if present)
- wind confidence score/grade
- TAS/IAS validity flags + tier
- TE enabled flag
- netto value (if present)
- netto tier + confidence
- vario source and confidence

Persist this record in memory for the test; do not write files.

---

## Determinism Gate

For each golden IGC:
1. Run replay end-to-end twice with identical inputs.
2. Assert the two output streams are identical:
   - same number of samples
   - same timestamps
   - identical values for all recorded fields

This must pass on CI.

---

## Stability Gates (Anti-Flicker)

Add additional assertions:

### A) Tier Oscillation Gate
- Airspeed tier must not toggle A<->C repeatedly in short intervals.
- Define max toggles per minute; exceed -> fail.

### B) TE Flicker Gate
- TE must not switch on/off rapidly.
- Define minimum on/off dwell time (seconds).

### C) Wind Freeze Gate
- Wind must not change during non-circling segments.
- If wind changes during glide, fail (phone-only model).

### D) Netto Honesty Gate
- Netto must not claim Tier A unless airspeed confidence >= MEDIUM.
- Netto must be hidden/invalid when tier C.

---

## Test Harness Design

Constraints:
- No Android framework
- Replay clock is the IGC timebase
- Use injected fake clock/test dispatcher per architecture rules 

Implementation approach:
- Build a JVM-only replay runner that feeds samples through:
  - ReplaySensorSource -> engine -> repository -> use case
- Record outputs from the domain use case (or repository output) at each tick.

---

## Performance Gate (Optional but recommended)

Ensure golden replay tests run quickly:
- Limit max ticks by decimation if necessary
- But preserve turn/glide structure

Set a test timeout threshold.

---

## Acceptance Criteria

A PR touching wind/TAS/TE/netto is mergeable only if:
- golden determinism tests pass
- stability gates pass
- enforceRules passes 
- no new deviations added 

---

## Notes

Golden replay tests are your safety net.
They are how you ship "genius" without regressions.


