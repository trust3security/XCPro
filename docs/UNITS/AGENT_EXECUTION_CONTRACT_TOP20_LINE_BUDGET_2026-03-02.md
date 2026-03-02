# AGENT_EXECUTION_CONTRACT_TOP20_LINE_BUDGET_2026-03-02.md

Date: 2026-03-02
Owner: XCPro Team / Codex
Status: Active
Primary plan: `docs/ARCHITECTURE/CHANGE_PLAN_TOP20_KOTLIN_LINE_BUDGET_500_2026-03-02.md`

Use with:
- `AGENTS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`

---

# 0) Contract

This is the autonomous execution contract for the top-20 Kotlin hotspot refactor program (`<= 500` lines/file compliance).

## 0.1 Authority
- Execute end-to-end without pause prompts unless blocked.
- Keep architecture correctness over diff size.

## 0.2 Non-negotiables
- Preserve MVVM + UDF + SSOT.
- Preserve dependency direction (`UI -> domain -> data`).
- Keep replay deterministic.
- No forbidden time API usage in domain/fusion/replay.
- All scoped files must reach budget (`<= 500`, with stricter per-file targets from the change plan).

## 0.3 Done Criteria
Work is done only when:
- all phases pass gates,
- all scoped files are compliant,
- verification table is complete,
- repass x5 evidence is complete,
- each phase quality score is `>= 94/100`.

---

# 1) Scope

Target set: the same 20 files listed in
`docs/ARCHITECTURE/CHANGE_PLAN_TOP20_KOTLIN_LINE_BUDGET_500_2026-03-02.md`.

Execution model:
- Phase 0: policy + gate setup
- Phase 1: repository/domain runtime decomposition
- Phase 2: UI/screen decomposition
- Phase 3: test decomposition
- Phase 4: compliant-file guard hold
- Phase 5: hardening + closeout

---

# 2) Required Verification

Minimum:
- `python scripts/arch_gate.py`
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When relevant:
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`

Release/CI parity:
- `./gradlew connectedDebugAndroidTest --no-parallel`

## 2.1 Evidence Table (fill while executing)
| Command | Purpose | Result | Duration | Notes |
|---|---|---|---|---|
| `python scripts/arch_gate.py` | static architecture gate | | | |
| `./gradlew enforceRules` | rule + line-budget gate | | | |
| `./gradlew testDebugUnitTest` | JVM regression suite | | | |
| `./gradlew assembleDebug` | build integrity | | | |
| `./gradlew :app:connectedDebugAndroidTest ...` | app instrumentation | | | |
| `./gradlew connectedDebugAndroidTest --no-parallel` | full instrumentation parity | | | |

---

# 3) Repass x5 Protocol (Mandatory)

Run for each phase slice and final closeout:
1. Structural pass (line/function split map).
2. Architecture pass (SSOT/dependency/boundary purity).
3. Timebase/determinism pass.
4. Test pass (scenario parity + stability).
5. Quality pass (scorecard + residual risk + evidence).

---

# 4) Quality Gates

Each phase must score `>= 94/100` on:
- architecture cleanliness,
- determinism/timebase safety,
- test confidence,
- maintainability outcome,
- evidence completeness.

Any phase below threshold is a hard fail and must be reworked.

---

# 5) Drift Audit Checklist

- [ ] No business logic moved into UI.
- [ ] No dependency direction violations.
- [ ] No forbidden time API in domain/fusion/replay.
- [ ] No new global mutable singleton state.
- [ ] No raw manager/controller escape hatches.
- [ ] Replay determinism preserved.
- [ ] No unresolved rule violations (or deviation documented with issue/owner/expiry).

---

# 6) Output Format

At end of each phase:
- What changed
- Files touched
- Tests added/updated
- Verification results
- Risks/mitigations
- Phase score

At final closeout:
- Done checklist
- Completed evidence table
- Final quality scorecards
- PR-ready summary

