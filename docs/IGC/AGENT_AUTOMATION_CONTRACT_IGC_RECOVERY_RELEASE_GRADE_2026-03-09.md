# AGENT_AUTOMATION_CONTRACT_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md

## Purpose

Define the execution contract for full-agent automation of the active IGC
recovery release-grade plan, end-to-end, across phases `0 -> 6`.

Primary plan anchor:

- `docs/IGC/CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`

Companion review anchor:

- `docs/IGC/REVIEW_IGC_RECOVERY_FOCUSED_CODE_PASS_2026-03-09.md`

This contract is task-level and subordinate to:

- `AGENTS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`

## 0) Automation Scope

- Coverage:
  - full execution of recovery release-grade phases `0 -> 6`
  - code, tests, docs, evidence, and score updates required by the plan
- Explicitly in scope:
  - recovery bootstrap extraction
  - structured recovery metadata
  - kill-point matrix closure
  - MediaStore instrumentation proof
  - typed recovery diagnostics
  - recovery-slice diff hygiene
- Explicitly out of scope:
  - retention implementation
  - share redaction/privacy implementation
  - unrelated IGC files UI work
- Mode:
  - autonomous execution without per-phase user approval unless blocked
- Output:
  - production code
  - tests
  - docs
  - evidence pack updates
  - plan score updates

## 1) Required Read Order Before Execution

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/IGC/CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`
10. `docs/IGC/CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`
11. `docs/IGC/REVIEW_IGC_RECOVERY_FOCUSED_CODE_PASS_2026-03-09.md`

If variometer/replay pipeline behavior is touched while implementing recovery,
also read:

1. `docs/LEVO/levo.md`
2. `docs/LEVO/levo-replay.md`

## 2) Workspace Preconditions

Before autonomous execution starts:

1. Run `git status --short`.
2. Record the allowed pre-existing dirty file set in the execution log.
3. Stop if unexpected unrelated changes appear after execution begins.
4. Never revert unrelated user changes.

Hard rule:

- If the branch is already dirty, automation may proceed only with an explicit
  allowed-file list captured before Phase 0 implementation begins.

Recommended execution log target:

- `docs/IGC/EXECUTION_LOG_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`

## 3) Non-Negotiable Recovery Rules

- Preserve `UI -> domain/use-case -> data`.
- Keep SSOT ownership explicit:
  - snapshot store owns persisted in-flight session state
  - recovery metadata store owns authoritative recovery identity by `sessionId`
  - flight log repository owns staged bytes and finalized publish path
- No hidden global mutable state.
- No direct `System.currentTimeMillis()` in domain/recovery orchestration.
- No heavy recovery I/O on `Main`.
- Replay behavior must remain deterministic and unchanged.
- `Recording` restart continuity must not be regressed while hardening
  `Finalizing` recovery.
- Any unavoidable rule exception must be recorded in
  `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue, owner, expiry.

## 4) Mandatory Per-Phase Automation Loop

For every phase `N`, run this exact loop:

1. Run `git status --short`.
2. Re-read phase `N` goal, tests, exit criteria, and hard-fail conditions from
   the recovery plan.
3. Record current assumptions in the execution log:
   - SSOT ownership deltas
   - dependency direction impact
   - time-base declarations
   - boundary adapters touched
4. Implement only phase-`N` scoped code/tests/docs.
5. Run the mandatory phase build gate.
6. Run phase-targeted tests.
7. Update the recovery plan and execution log with:
   - files touched
   - commands run
   - pass/fail
   - score update
   - residual risks
8. Advance to phase `N+1` only if the phase gate is green or an explicit
   unrelated blocker is recorded with evidence.

No phase skipping is allowed.

## 5) Phase Build Gate Policy

### 5.1 Mandatory Basic Build Gate After Each Code Phase

Use this minimum build gate after each code-bearing phase:

```bash
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

If app wiring, DI, manifest, or instrumentation scope changes in the phase,
also run:

```bash
./gradlew :app:assembleDebug
```

### 5.2 Retry Policy

If the phase build gate fails:

1. fix phase-owned issues and rerun the same build gate,
2. retry with `--no-configuration-cache` if failure indicates tooling/cache
   instability,
3. if still red, classify blocker as:
   - phase-caused, or
   - unrelated/pre-existing
4. stop auto-advance after 3 failed attempts.

## 6) Required Final Verification

At major milestones and final close, run:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

KSP stability fallback when needed:

```bash
./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" <tasks>
```

## 7) Phase Contract

### Phase 0 - Contract Freeze and Baseline Audit

Scope:

- confirm the recovery plan is the active execution anchor
- create/update execution log
- freeze recovery-only scope
- record current known gaps and current score

Required files:

- `docs/IGC/CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`
- this contract
- execution log

Required checks:

- no build required if docs-only
- if code is touched during audit, run the phase build gate

Exit criteria:

- active plan, contract, and execution log are aligned
- allowed dirty-file set is recorded
- starting score and blocker list are documented

### Phase 1 - Recovery Bootstrap Owner Extraction

Scope:

- extract `IgcRecoveryBootstrapUseCase`
- remove repository recovery I/O from
  `IgcRecordingUseCase.restoreOrCreateStateMachine()`
- introduce explicit bootstrap outcomes:
  - `Recovered`
  - `TerminalFailure`
  - `ResumeExisting`
  - `Unsupported`
- ensure `Recording` snapshots with no recoverable staged/finalized artifacts
  resume rather than clear

Primary files:

- `feature/map/src/main/java/com/example/xcpro/igc/usecase/IgcRecordingUseCase.kt`
- new `feature/map/src/main/java/com/example/xcpro/igc/usecase/IgcRecoveryBootstrapUseCase.kt`
- DI wiring files as needed
- `docs/ARCHITECTURE/PIPELINE.md` if startup ownership wording changes

Required focused checks:

```bash
./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.usecase.*RecoveryBootstrap*"
./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.usecase.IgcRecordingUseCaseTest"
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

Exit criteria:

- constructor/restore path no longer owns recovery I/O
- `Recording -> ResumeExisting` is proven
- outcome taxonomy no longer overloads `NoRecoveryWork`

### Phase 2 - Structured Recovery Metadata Authority

Scope:

- add authoritative recovery metadata store keyed by `sessionId`
- persist wall-start/date identity outside staged bytes
- make staged-byte parsing fallback only
- support both `HFDTEDATE:` and `HFDTE`
- prepare deterministic orphan pending-row cleanup even when staged metadata is
  unreadable

Primary files:

- new recovery metadata contract/store under `feature/igc/.../data/`
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcRecordingRuntimeActionSink.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcFlightLogRepository.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcSessionStateSnapshotStore.kt` if needed

Required focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.data.*Recovery*"
./gradlew :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.data.*Metadata*"
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

Exit criteria:

- structured metadata is primary authority
- fallback wall-start/date path is real, not dead
- short-form `HFDTE` is covered by tests
- duplicate finalized matches are no longer silently accepted

### Phase 3 - Kill-Point Matrix Closure

Scope:

- add explicit `K1..K7` tests
- add duplicate finalized export invariant tests
- add production-realistic `Recording` restart continuity tests

Primary files:

- `feature/igc/src/test/java/.../IgcFlightLogRepositoryRecoveryKillPointTest.kt`
- `feature/map/src/test/java/.../IgcRecoveryBootstrapUseCaseTest.kt`
- `feature/map/src/test/java/.../IgcRecordingUseCaseTest.kt`

Required focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.data.*Recovery*"
./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.usecase.*Recovery*"
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

Exit criteria:

- `K1..K7` are explicitly green
- duplicate-session guard behavior is proven
- mid-flight restart continuity is proven with realistic semantics

### Phase 4 - Device/MediaStore Instrumentation Proof

Scope:

- prove restart recovery against real Android storage behavior
- verify pending-row deletion and no duplicate finalized output after rerun

Primary files:

- `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcRecoveryRestartInstrumentedTest.kt`
- supporting Android test utilities/fakes

Required focused checks:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

If full multi-module instrumentation is feasible:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

Exit criteria:

- at least one end-to-end restart recovery instrumentation path is green
- pending-row cleanup is demonstrated with real resolver behavior

### Phase 5 - Typed Diagnostics and Operator Evidence

Scope:

- make recovery outcomes observable as typed diagnostics
- preserve source provenance of failures
- populate the evidence pack required by the recovery plan

Primary files:

- recovery diagnostics contract/use-case output
- `docs/IGC/phase6_evidence/phase6_recovery_kill_matrix.md`
- `docs/IGC/phase6_evidence/phase6_gates.md`
- `docs/IGC/phase6_evidence/phase6_manual_checklist.md`
- `docs/ARCHITECTURE/PIPELINE.md` if operator-visible flow changes

Required focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.*Diagnostic*"
./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.usecase.*Recovery*"
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

Exit criteria:

- recovery outcomes are typed and observable
- exception provenance is preserved
- evidence pack is current

### Phase 6 - Diff Hygiene and Release Gate

Scope:

- isolate or remove unrelated retention/share scaffolding
- make recovery diff reviewable and release-safe
- run full gates and final rescore

Primary files:

- recovery-scope code and docs touched during phases `0 -> 5`
- final plan and execution log updates

Required focused checks:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

Exit criteria:

- only recovery-slice artifacts remain in scope, or extra scaffolding is
  explicitly owned and documented
- final score is `>= 95/100`
- no hard-fail condition remains open

## 8) Score Publication Contract

Use the recovery plan rubric exactly:

- Architecture and ownership clarity: 25
- Deterministic recovery semantics: 25
- Automated kill-point and restart proof: 30
- Operational diagnostics and docs/evidence: 20

After each phase, update:

- total score `/100`
- impacted rubric categories only
- evidence basis:
  - files changed
  - tests added/updated
  - commands run
  - remaining risks

Hard rule:

- never publish `>= 95/100` while any hard-fail condition in the recovery plan
  remains unresolved.

## 9) Evidence Contract

For every phase, record:

- date
- phase number and status
- files changed with paths
- tests added/updated
- commands run
- PASS/FAIL per command
- first failing path(s) if any command fails
- updated score
- residual risks
- next action pack

Minimum required evidence references:

- `scripts/arch_gate.py` status
- time abstraction reference paths:
  - `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
  - `app/src/main/java/com/example/xcpro/di/TimeModule.kt`

Recommended evidence destinations:

- recovery plan
- execution log
- `docs/IGC/phase6_evidence/*`

## 10) Blocker Policy

Automation must stop and publish blocker evidence when any is true:

- architecture or coding-rule conflict cannot be safely resolved in-phase
- phase build gate remains red after retry policy is exhausted
- instrumentation/device resources are unavailable for required proof
- dirty worktree ambiguity makes ownership unsafe
- unknown external changes invalidate deterministic execution assumptions

Blocker record must include:

- failing command
- first failing file or test
- classification:
  - phase-caused
  - unrelated/pre-existing
  - environment-blocked
- proposed next action pack

## 11) Documentation Sync Rules

If pipeline wiring changes, update:

- `docs/ARCHITECTURE/PIPELINE.md`

If architecture/rules/policies change, update:

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`

If the recovery plan scope or score contract changes, update:

- `docs/IGC/CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`
- this contract

If a known rule is intentionally violated, update:

- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

## 12) Completion Criteria

Automation may declare the recovery plan complete only when:

1. phases `0 -> 6` are completed in order or explicitly blocked with evidence,
2. required final verification is green or environment-blocked with explicit
   proof,
3. final recovery score is `>= 95/100`,
4. `Recording` restart continuity and `Finalizing` recovery are both proven,
5. duplicate-session guard and pending-row cleanup are proven,
6. final quality rescore from `docs/ARCHITECTURE/AGENT.md` is published,
7. plan, contract, and evidence pack are synchronized.

## 13) Practical Advice for the Runner

Use this contract to automate the whole active recovery plan, not the broader
retention/privacy workstream.

Recommended start order:

1. create execution log,
2. record allowed dirty-file set,
3. run Phase 0,
4. execute Phase 1 only until green,
5. continue phase-by-phase without mixing retention/share scaffolding.

Best operating mode:

- one dedicated recovery branch
- one commit per phase or small phase slice
- no broad "implement Phase 6" attempts

Reason:

- this work crosses `feature:map`, `feature:igc`, MediaStore behavior, docs,
  and instrumentation
- reliability comes from strict phase gates, not from one large diff
