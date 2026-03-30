# XCPro OGN reconnect hardening pack

This pack is set up for Codex-style phase execution against `trust3security/XCPro`.

## Files

1. `01_CODEX_MASTER_BRIEF.md`  
   Master prompt for the whole workstream.

2. `02_CHANGE_PLAN_OGN_RECONNECT_HARDENING.md`  
   Filled implementation/change plan aligned to the repo's architecture template.

3. `03_PHASE_0_BASELINE.md`  
   Baseline prep and guardrails. No production behavior change.

4. `04_PHASE_1_UNEXPECTED_STREAM_END.md`  
   Fix invisible clean-EOF retry churn and backoff reset.

5. `05_PHASE_2_OFFLINE_WAIT_SEAM.md`  
   Add explicit offline wait / resume behavior for OGN.

6. `06_PHASE_3_STATE_OWNERSHIP_SERIALIZATION.md`  
   Make OGN state ownership serialized without putting blocking socket I/O on the same writer lane.

7. `07_PHASE_4_TELEMETRY_AND_REGRESSION.md`  
   Harden structured telemetry, UI semantics, and regression coverage.

## Recommended execution order

- Run `01_CODEX_MASTER_BRIEF.md` once to set global constraints.
- Land `02_CHANGE_PLAN_OGN_RECONNECT_HARDENING.md` or keep it as the working plan doc.
- Execute one phase file at a time, in order.
- Do not combine Phase 2+3 blindly; Phase 3 needs care because OGN uses blocking socket reads.

## Why this split exists

The repo's agent contract expects phased execution, explicit SSOT ownership, and documented file ownership before non-trivial refactors. This pack keeps each phase small enough to review, test, and revert independently.

## Verification cadence

Minimum after each phase:
```bash
./gradlew enforceRules
```

Targeted tests after each phase:
```bash
./gradlew :feature:traffic:testDebugUnitTest
```

Merge-ready proof after the last phase:
```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
