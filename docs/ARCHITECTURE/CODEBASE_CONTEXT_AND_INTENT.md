> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# CODEBASE_CONTEXT_AND_INTENT.md

## Purpose

This document is the **bridge** between:
- XC Pro's current variometer/wind/airspeed/netto implementation, and
- The new phone-only **Wind + TAS/IAS proxy + TE gating + Netto tiering + Confidence** upgrades.

It exists so AI/dev tools (Codex) can:
- Understand what already works and must be preserved
- Understand the physical constraints (phone sensors)
- Implement the new specs without ad-hoc behavior
- Preserve determinism, timebase rules, and SSOT architecture

This file is normative for intent. If code changes conflict with this intent, the change is wrong even if it compiles.

Must comply with:
- `ARCHITECTURE.md` (timebase + SSOT + DI) 
- `CODING_RULES.md` (enforcement + tests + ASCII hygiene) 
- Contributor workflow in `CONTRIBUTING.md` 

Related new docs:
- `PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md`
- `NETTO_SETTINGS_S100_PARITY.md`
- `CONFIDENCE_MODEL_SPEC.md`
- `GOLDEN_REPLAY_TEST_PLAN.md`

---

## Quick Glossary (Vendor-neutral)

- **Brutto vario**: measured vertical speed (what the glider is doing).
- **TE vario**: total-energy compensated vario (removes speed-change effects).
- **Netto**: airmass vertical velocity estimate = TE vario minus glider sink at current airspeed.
- **Wind vector**: airmass motion relative to ground.
- **TAS proxy**: computed TAS estimate based on GNSS ground vector and estimated wind.
- **IAS proxy**: derived from TAS proxy using density ratio (informational only).
- **Tier A/B/C**: quality bands for airspeed and netto. A is best possible on phone; C is invalid.

---

## What XC Pro already does well (must preserve)

### 1) Layering and determinism

XC Pro is explicitly built for:
- MVVM + UDF
- SSOT repositories
- Pure domain logic (use cases)
- Deterministic replay / simulator mode

**Do not** move business math into UI.
**Do not** introduce wall-clock dependencies in domain/fusion logic. 

### 2) Vario pipeline separation

Current design already separates:
- domain computation (`CalculateFlightMetricsUseCase`) from
- UI display smoothing (`DisplayVarioSmoother` style behavior)

This separation is correct. Keep it.

### 3) Wind-to-TAS math (vector correctness)

Existing TAS/IAS estimation math uses:
- wind vector defined as "wind TO" (airmass velocity), and computes:
  `air = ground - wind_to`, then TAS as magnitude.

This vector convention is correct and must remain unchanged.

### 4) TE gating concept (partially correct today)

The current code tends to compute TE only when it believes it has a meaningful airspeed.
This is directionally correct and must become stricter with the new specs.

---

## What XC Pro cannot do (physics constraints)

This section is not a TODO list; it is a hard constraint model.

1) A phone cannot measure real IAS.
- No pitot/static pressure differential sensor exists.
- Any IAS is an **IAS proxy** derived from an estimated TAS.

2) A phone cannot directly observe TAS.
- TAS proxy exists only via:
  `TAS_proxy = |ground_vector - wind_vector|`
- Therefore TAS quality depends on wind quality.

3) Turns destroy instantaneous observability.
- During circling, GNSS track/speed are noisy and changing.
- Attempting to estimate wind or TAS "live" through turns produces flicker and bias.

Therefore:
- Wind must be learned only in approved conditions (phone-only circling method).
- Wind/TAS must be held across turns and between circles.

4) Wrong numbers are worse than missing numbers.
- A confident but wrong netto/airspeed destroys pilot trust.
- The system must prefer degraded/invalid outputs over fabricated precision.

---

## Why these upgrades are required (pilot-facing problems)

Even if current code "works", pilots will observe:

1) **Netto twitchiness**
- Phone sensor noise + weak airspeed observability causes netto to feel jumpy.
- S100 feels stable because it has real IAS sensors and pneumatic damping.

2) **TE artifacts**
- If TE uses ground speed or low-quality TAS proxy, stick-thermal artifacts appear.
- This feels like false lift/sink.

3) **Wind and TAS instability in real gliding**
- Gliders turn a lot (especially after launch).
- Without "hold through turns", TAS and netto oscillate.

4) **Confidence flicker destroys trust**
- A/B/C mode must not flicker quickly.
- Pilots prefer a stable "degraded" marker over rapid switching.

---

## What is changing (intent overview)

These upgrades do not rewrite the architecture. They add explicit signal quality modeling.

### A) Explicit confidence model (new backbone)

We introduce explicit confidence signals for:
- baro/vario
- GNSS
- wind
- airspeed (derived)
- netto (derived)

See: `CONFIDENCE_MODEL_SPEC.md`.

Intent:
- Every downstream decision (TE on/off, tier A/B/C, display validity) is driven by explicit confidence.

### B) Wind is slow and persistent

Wind becomes:
- updated only via circling method (phone-only)
- stored persistently (order of hour)
- decays in confidence, not to zero

Intent:
- Wind does not vanish unexpectedly.
- Wind does not change during glide.

### C) TAS/IAS are proxies with strict validity gating

- TAS proxy exists only when wind confidence is sufficient.
- IAS proxy exists only when TAS proxy is valid.

Intent:
- Never substitute ground speed as "TAS" when wind is unknown.
- Never display IAS proxy unless TAS proxy is valid.

### D) TE gating becomes strict

- TE is computed only when TAS proxy is valid (tier A/B conditions if allowed).
- Otherwise TE is disabled and pressure/baro vario remains.

Intent:
- Remove TE false artifacts and preserve pilot trust.

### E) Netto becomes tiered and honest

Netto display behavior becomes:
- Tier A: S100-like (best possible on phone)
- Tier B: degraded (held/stale wind; heavy smoothing; marked degraded)
- Tier C: invalid/hidden

Intent:
- Never present "good netto" without good airspeed confidence.

### F) Golden replay regression gates

We add deterministic replay gates and anti-flicker stability tests.
See: `GOLDEN_REPLAY_TEST_PLAN.md`.

Intent:
- Future refactors cannot silently degrade trust.

---

## Where changes belong (architecture mapping)

**Repository (SSOT)**
- wind persistence
- wind age/staleness computed using injected time base
- expose `Flow<WindState?>`

**Domain (UseCase)**
- confidence computation
- tiering logic
- TAS/IAS proxy gating + holding
- TE gating
- netto tiering + averaging semantics

**UI**
- render values and quality labels
- optional visual easing
- must not compute or store authoritative wind/airspeed/netto state

No cross-layer violations are allowed. 

---

## What Codex must NOT do (common failure modes)

1) Do not invent straight-flight wind estimation (EKF) using derived TAS.
- This becomes self-referential and wrong on phone sensors.

2) Do not "fix" invalid outputs by substituting ground speed.
- This creates confident but wrong netto/TE.

3) Do not push math into UI for convenience.
- Breaks determinism and SSOT.

4) Do not add new global mutable singletons.
- DI only.

5) Do not use wall clock in domain logic.
- Live uses monotonic; replay uses IGC timestamps. 

6) Do not add magic numbers in scattered locations.
- Thresholds must be centralized and documented.

---

## How this improves the app (measurable outcomes)

After implementation, expected outcomes:

1) **Pilot trust**
- TAS/IAS/netto are shown only when valid.
- When degraded, it is visible and stable.

2) **Reduced flicker**
- Tier A/B/C changes are slow, not rapid.
- TE enable/disable has minimum dwell times.

3) **Better TE feel**
- No TE false lift from speed noise.
- TE comes on only when conditions support it.

4) **Netto feels instrument-like**
- 10s average default produces S100-like decision stability.
- 5s option remains for "race feel" only when tier A.

5) **Regression resistance**
- Golden replay suite catches drift/flicker/invalid logic instantly.

---

## Minimal handoff instructions to Codex

Codex should:
1) Read: `ARCHITECTURE.md` then `CODING_RULES.md` then this file.  
2) Execute `PHASED_IMPLEMENTATION_WIND_TAS_IAS_NETTO.md` phases in order.
3) Add tests at each phase.
4) Ensure `KNOWN_DEVIATIONS.md` remains empty. 
5) Run the required Gradle checks after each phase.

If blocked by missing code context:
- add a short `docs/WIND_PIPELINE_MAP.md` per Phase 0 and continue.

---

## End State

XC Pro will:
- Deliver the best possible wind/TAS/IAS/netto behavior possible on phone sensors
- Never pretend to be a certified instrument
- Behave like an avionics product: stable, honest, and regression-resistant



