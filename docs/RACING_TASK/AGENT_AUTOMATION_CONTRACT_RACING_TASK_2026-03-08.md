# AGENT_AUTOMATION_CONTRACT_RACING_TASK_2026-03-08.md

## Purpose

Define the execution contract for full-agent automation of Racing Task phases 1..10,
including a mandatory basic build gate after each phase.

This contract is subordinate to:

- `AGENTS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/RACING_TASK/CHANGE_PLAN_RACING_TASK_PRODUCTION_GRADE_PHASED_IP_2026-03-07.md`

## 0) Automation Scope

- Coverage: Racing Task phased IP phases 1..10.
- Mode: autonomous execution without per-phase user confirmation.
- Output: code, tests, docs, and phase score updates in the phased IP.
- Mandatory post-phase gate: basic build command must run after each phase.

## 1) Required Read Order Before Execution

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/RACING_TASK/CHANGE_PLAN_RACING_TASK_PRODUCTION_GRADE_PHASED_IP_2026-03-07.md`

If variometer/replay behavior is touched, also read:

1. `docs/LEVO/levo.md`
2. `docs/LEVO/levo-replay.md`

## 2) Core Execution Contract

For each phase N:

1. Implement only phase-N scoped items from the phased IP.
2. Add or update tests that directly prove phase-N acceptance semantics.
3. Run mandatory basic build gate:
   - `./gradlew :feature:map:assembleDebug`
4. Run phase-targeted tests.
5. Update phased IP with:
   - changes summary
   - files touched
   - commands and pass/fail
   - score update for impacted area(s)
6. Proceed to phase N+1 only if phase-N gate is green or explicitly marked blocked with evidence.

No phase skipping is allowed.

### 2A) Autonomous Run Loop (Mandatory)

For each phase N, run this exact loop:

1. Pre-check workspace state (`git status --short`) and stop if unexpected unrelated changes appear.
2. Implement phase-N code/tests/docs scope.
3. Run phase-N basic build gate and phase-targeted tests.
4. Update phase score and evidence in the phased IP.
5. If gate is green, advance immediately to phase N+1.

Do not wait for additional user confirmation between phases unless blocked.

## 3) Basic Build Gate (Mandatory After Each Phase)

Required command after each phase:

```bash
./gradlew :feature:map:assembleDebug
```

If this fails:

1. auto-fix issues caused by phase changes,
2. re-run the same build command,
3. if still failing and failure is branch-external/unrelated, mark phase as blocked with evidence and stop auto-advance.

`compileDebugKotlin` may be run for diagnostics but does not replace the required phase build gate.

### 3A) Build Retry Policy

If phase build gate fails, use this retry sequence:

1. fix phase-caused code issues and retry `:feature:map:assembleDebug`,
2. retry with `--no-configuration-cache` if failure indicates cache/tooling instability,
3. if still red, classify blocker as phase-caused or unrelated and stop auto-advance.

Maximum retries before blocker classification: 3.

## 4) Full Verification Gates

At phase boundaries (minimum):

```bash
./gradlew :feature:map:assembleDebug
./gradlew :feature:map:testDebugUnitTest --tests "<phase-relevant-tests>"
```

At major milestones and final close:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

### 4A) Phase-to-Area Mapping and Minimum Test Focus

Phase scoring and tests must map as follows:

| Phase | Primary Area | Minimum Test Focus |
|---|---|---|
| 1 | Area 2 | canonical model ownership, adapters, hydrate path |
| 2 | Area 1 | structure/profile validator + manager/engine integration |
| 3 | Area 3 | start procedure matrix + timestamp normalization |
| 4 | Area 4 | turnpoint/OZ crossing, near-miss, ordering |
| 5 | Area 5 | finish policy matrix and outcomes |
| 6 | Area 6 | boundary evidence/timing hardening |
| 7 | Area 7 | UI editor/typed command validation |
| 8 | Area 8 | serializer/import-export v2 migration fidelity |
| 9 | Area 9 | replay parity/preconditions + lifecycle idempotency |
| 10 | Area 10 | failure-mode matrix + CI drift guards |

Every phase must run:

- `./gradlew :feature:map:assembleDebug`
- at least one targeted unit-test command that exercises the phase focus
  for the mapped area.

## 5) Scoring Contract

Use the existing 100-point rubric from the phased IP:

- Spec coverage and behavior parity: 40
- Automated test coverage depth: 30
- Determinism/timebase and architecture compliance: 20
- Operational hardening and docs sync: 10

After each phase, rescore only impacted areas and append to the phased IP.

### 5A) Score Publication Rules

Each phase score update must include:

- phase number and mapped area number,
- total score `/100`,
- 40/30/20/10 rubric breakdown,
- explicit note whether score is full-branch or scoped slice.

## 6) Blocker Policy

Automation must stop and publish blocker evidence when any is true:

- architecture or coding-rule conflict cannot be resolved safely in-phase,
- required build gate remains red after auto-fix loop,
- failure is environment-dependent (no device/emulator, toolchain outage),
- unknown external changes invalidate deterministic execution assumptions.

Blocker entry must include:

- failing command,
- first failing file/test path(s),
- classification: phase-caused vs unrelated,
- proposed next action pack.

If full-branch gates fail for unrelated reasons but phase gate is green:

1. record the unrelated failure with evidence,
2. continue automation for next phase,
3. keep final completion blocked until full release gates are resolved or
   explicitly environment-blocked.

## 7) Safety and Rollback

- No destructive git operations.
- No reverting unrelated user changes.
- Preserve MVVM + UDF + SSOT boundaries.
- Use injected time sources in domain/fusion paths.
- Keep replay deterministic.

If a phase introduces regressions:

1. rollback only that phase slice,
2. restore prior green build state,
3. re-implement with a narrower change set.

### 7A) Documentation Sync Rules

If pipeline wiring changes, update:

- `docs/ARCHITECTURE/PIPELINE.md`

If architecture/rules/policies change, update:

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`

If a known rule is intentionally violated, add a time-boxed entry to:

- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

## 8) Evidence Format Per Phase

Each phase update in the phased IP must include:

- phase ID and date
- summary of behavior changes
- absolute file paths changed
- command list and PASS/FAIL
- score and score breakdown
- residual risks and linked pending pack IDs

## 9) Completion Criteria

Automation may declare completion only when:

1. Areas 1..10 are each `>= 95/100`.
2. Phase build gate (`:feature:map:assembleDebug`) has passed after every phase.
3. Final required gates are green or blocked with explicit environment evidence.
4. No unapproved RT-scope deviation remains open.
5. Final quality rescore from `docs/ARCHITECTURE/AGENT.md` is published.
6. Phase-by-phase evidence is present in the phased IP.

## 10) Practical Guarantee Statement

This contract enables full autonomous implementation flow and enforces
basic build-after-phase behavior.

It does not guarantee unconditional completion in all environments; completion
remains contingent on resolvable code issues, available toolchain/device
resources, and non-conflicting external repository state.

## 11) Runner Entrypoint

Primary automation runner:

- `scripts/ci/racing_phase_runner.ps1`

Example usage:

```bash
powershell -ExecutionPolicy Bypass -File scripts/ci/racing_phase_runner.ps1 -FromPhase 8 -ToPhase 10
```

Optional strict/extended flags:

```bash
powershell -ExecutionPolicy Bypass -File scripts/ci/racing_phase_runner.ps1 -FromPhase 1 -ToPhase 10 -RunArchGatePerPhase -RunFinalGatesAtEnd
```
