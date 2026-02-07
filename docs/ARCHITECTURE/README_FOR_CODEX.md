> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# README_FOR_CODEX.md

## Purpose

This file tells Codex **exactly how to read this repository** and **in what order** to implement changes.
It exists to prevent partial understanding, ad-hoc fixes, or accidental violations of intent.

This is not a spec.  
This is the **execution contract**.

---

## Mandatory Reading Order (Do Not Skip)

Codex MUST read the following files **in this exact order** before making any changes:

1. **ARCHITECTURE.md**  
   - Non-negotiable system invariants  
   - Timebase rules, SSOT, DI, determinism

2. **CODING_RULES.md**  
   - Day-to-day enforcement rules  
   - What is forbidden even if code works"

3. **CODEBASE_CONTEXT_AND_INTENT.md**  
   - What the current XC Pro code already does correctly  
   - Physical constraints of phone sensors  
   - Why the upcoming changes exist  
   - What must NOT be changed

4. **GENIUS_PHONE_SENSORS_WIND_TAS_IAS_NETTO_SPEC.md**  
   - Authoritative physics + behavior spec  
   - Wind, TAS/IAS proxy, TE, Netto, tiering  
   - If code conflicts with this document, the code is wrong

5. **CONFIDENCE_MODEL_SPEC.md**  
   - Explicit confidence signals and decay rules  
   - Backbone for validity and tiering decisions



---

## Execution Rules (Hard)

Codex MUST:

- Follow the phases **in order**
- Add tests before or with behavior changes
- Keep `KNOWN_DEVIATIONS.md` empty
- Use injected clocks only (no wall time)
- Preserve SSOT ownership and layering
- Avoid magic numbers; centralize thresholds
- Prefer invalid/degraded output over fabricated precision

Codex MUST NOT:

- Invent new physics
- Substitute ground speed for TAS
- Estimate wind outside the circling method
- Push business logic into UI
- Introduce global mutable state
- Add Android framework types to domain code

---

## How to Proceed if Blocked

If Codex encounters missing context:

1. Create a **small, factual** doc:
   - `docs/WIND_PIPELINE_MAP.md`
   - Or similar flow map
2. Continue implementation using the specs
3. Do not guess or invent behavior

If a rule seems unclear:
- Stop
- Document the ambiguity
- Ask for clarification

---

## Definition of Done

A phase is complete only when:

- All unit tests pass
- Deterministic replay gates pass
- `./gradlew enforceRules` passes
- No new deviations are added
- Behavior matches the specs exactly

---

## Final Reminder

This project prioritizes:

> **Honesty over cleverness**  
> **Stability over noise**  
> **Trust over features**

If uncertain, do less -- not more.


