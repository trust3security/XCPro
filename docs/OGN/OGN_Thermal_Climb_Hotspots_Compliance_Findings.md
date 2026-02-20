# OGN Thermal Hotspots Compliance Findings

## 0) Metadata

- Date: 2026-02-18
- Reviewer: Codex
- Scope: plan-level compliance audit for `docs/OGN/OGN_Thermal_Climb_Hotspots_Implementation_Plan.md`
- Rule set:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
  - `docs/ARCHITECTURE/CONTRIBUTING.md`
  - `docs/ARCHITECTURE/AGENT.md`
  - `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`

## 1) Pass 1 Findings (Strict Audit)

| ID | Severity | Finding | Why it was non-compliant | Action taken |
|---|---|---|---|---|
| F1 | High | Missing enforcement coverage map | Plan did not map risks to enforceRules/tests/review guards | Added `10.1 Enforcement coverage map` |
| F2 | High | Missing explicit boundary ownership moves | Responsibility migration was implicit | Added `3.3 Boundary ownership moves` |
| F3 | High | Missing bypass-prevention table | Direct-call boundary risks were not explicitly blocked | Added `3.4 Bypass removal and prevention` |
| F4 | Medium | SSOT table lacked forbidden-duplicates column | Duplicate ownership risk not explicitly documented | Expanded `3.1 SSOT Ownership` table |
| F5 | Medium | Threading/dispatcher ownership not declared | No explicit `Main/Default/IO` intent for new paths | Added `4.2 Threading and cadence` |
| F6 | Medium | Replay determinism declaration missing | AGENT gate expects explicit statement even when scoped out | Added `4.3 Replay determinism declaration` |
| F7 | Medium | Hidden-vs-detect behavior ambiguous | Could cause inconsistent state when `Show Thermals` is off | Added explicit detection semantics in `7.1` |
| F8 | Medium | Lifecycle collection contract not explicit | `collectAsStateWithLifecycle` policy needed explicit mention | Added `7.5 Lifecycle and collection contract` |

## 2) Pass 2 Findings (Re-pass After Fixes)

| ID | Severity | Finding | Why it mattered | Action taken |
|---|---|---|---|---|
| F9 | Medium | Boundary adapter check not explicit | AGENT pre-implementation gate 1.5 expects explicit justification | Added `3.5 Boundary adapter check` |
| F10 | Low | Before/after flow not clearly separated | CHANGE_PLAN template expects explicit before/after flow | Added `3.6 Before and after data flow` |

## 3) Pass 3 Findings (Current Re-pass)

| ID | Severity | Finding | Why it mattered | Action taken |
|---|---|---|---|---|
| F11 | Medium | Scope block not template-structured | `CHANGE_PLAN_TEMPLATE` expects explicit problem/why/in-scope/out-of-scope/user-impact fields | Added `1.1 Scope contract` |
| F12 | Medium | Time base declaration was bullet-only | `AGENT` and `CHANGE_PLAN_TEMPLATE` require explicit per-value time-base declaration | Added `4.1` time-base table with rules |
| F13 | Medium | Phase section lacked explicit goal/tests/exit criteria per phase | Template requires these fields per phase | Reworked all phase sections under `8)` |
| F14 | Low | Risk table missing owner column | Template requires risk owner accountability | Added owner column in `10)` |
| F15 | Low | Acceptance gates missing explicit replay-unchanged and deviations gate | Required acceptance gate clarity for scoped-out replay and deviations process | Added items 9 and 10 in `11)` |

## 4) Final Re-pass Result

After pass 3 updates, no remaining major compliance gaps were found in the plan.

Residual implementation-time watch items:

1. Keep all thermal detection math out of ViewModel/UI during implementation.
2. Keep runtime ownership exclusively in `MapOverlayManager`.
3. Ensure new Compose flow collections remain lifecycle-aware.
4. Enforce fake-clock unit tests for all hysteresis/time-window logic.
5. Keep `PIPELINE.md` updated in the same PR when thermal wiring lands.

## 5) Files Updated by This Audit

1. `docs/OGN/OGN_Thermal_Climb_Hotspots_Implementation_Plan.md`
2. `docs/OGN/OGN_Thermal_Climb_Hotspots_Compliance_Findings.md`
