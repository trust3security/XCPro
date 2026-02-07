> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# Feature: <FEATURE_NAME>

## 1. Purpose
One clear paragraph.

- What problem does this feature solve for the pilot or system?
- Why does it exist *now*?
- What does success look like?

Explicit non-goals:
- What this feature does NOT attempt to do.

---

## 2. User-Visible Behaviour
Describe exactly what the user sees and/or hears.

- UI surfaces affected:
- Audio effects (if any):
- Mode-dependent behaviour (CRUISE / CLIMB / FINAL / REPLAY):
- Live vs replay differences:

Rules:
- No implementation details
- No class or file names

---

## 3. Architectural Impact

### 3.1 SSOT Ownership
Declare authoritative ownership explicitly.

- Raw inputs owner:
- Derived values owner:
- UI state owner:

Statement (mandatory):
> This data has exactly one authoritative owner. No other layer may cache or re-derive it.

---

### 3.2 Data Flow
Describe the data flow using existing pipeline terminology.

```
<Source>
  -> <Repository>
    -> <UseCase>
      -> <ViewModel>
        -> UI
```

Indicate:
- [ ] Extends existing flow
- [ ] Adds new parallel flow
- [ ] Reuses existing SSOT

---

### 3.3 Time Base
Select exactly one primary time base:

- [ ] Monotonic (live fusion)
- [ ] Replay clock (IGC)
- [ ] Wall time (UI/output only)

Rules:
- Which timestamps are used?
- What is forbidden?
- What happens if timestamps are missing or invalid?

---

## 4. Cadence & Performance

Declare explicitly:

- Update cadence (Hz or ms):
- Gating sensor (baro / GPS / other):
- Worst-case latency budget:
- Audio coupling (yes/no, where):

State clearly if this feature is:
- Baro-gated
- GPS-gated
- UI-only smoothing

---

## 5. Failure & Degradation Modes

Define behaviour when conditions degrade.

- Missing sensor:
- Stale data:
- Low confidence:
- Replay data malformed:

For each, state:
- UI behaviour
- Audio behaviour
- Logging expectations (debug-only / never)

---

## 6. Replay & Determinism

Replay guarantees:

- Deterministic output: Yes / No
- Same input produces same output: Yes / No
- Randomness allowed: No (or must be seeded)

Required tests:
- Replay A vs Replay B equality
- Live vs replay divergence rules

---

## 7. Testing Plan

Mandatory coverage:

- Unit tests (list UseCases):
- Replay tests:
- Regression risks:

Statement (mandatory):
> This feature becomes incorrect if these tests are not present.

---

## 8. Files & Modules Touched

Expected changes:

- New files:
- Modified files:

Explicitly forbidden:
- Files or modules that must NOT be touched

---

## 9. Open Questions / Deferred Decisions

List anything unresolved.

Rule:
- If this section is non-empty, implementation MUST NOT begin.

---

## 10. Approval

- Spec author:
- Date:
- Approved by:


