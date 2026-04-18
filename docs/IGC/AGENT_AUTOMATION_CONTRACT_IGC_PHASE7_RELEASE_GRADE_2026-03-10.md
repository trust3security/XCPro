# AGENT_AUTOMATION_CONTRACT_IGC_PHASE7_RELEASE_GRADE_2026-03-10.md

## Purpose

Define the autonomous full-finish execution contract for IGC Phase 7,
covering validation, interoperability, diagnostics, code delivery, and
evidence closure end-to-end across `P7-0 -> P7-8`.

Primary plan anchor:

- `docs/IGC/CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`

Historical scope note:

- this contract reflects the original Phase 7 scope at the time it was written
- production app code later gained a follow-on `XCS` / `G` compatibility export
  path
- do not read this contract's "security-signature out of scope" note as meaning
  the current production repo lacks signing
- canonical follow-on note:
  - `docs/IGC/PRODUCTION_COMPATIBILITY_PROFILE_XCS_WEGLIDE_2026-03-10.md`

Companion context anchors:

- `docs/IGC/README.md`
- `docs/IGC/CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`

This contract is task-level and subordinate to:

- `AGENTS.md`
- `docs/ARCHITECTURE/AGENT.md`
- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/PIPELINE.md`

## 0) Automation Scope

- Coverage:
  - full execution of Phase 7 items `P7-0 -> P7-8`
  - code, tests, docs, evidence, and score updates required by the plan
- Explicitly in scope:
  - `IgcLint*` contracts and validator implementation
  - parser/formatter/lint parity enforcement
  - typed finalize/export diagnostics SSOT
  - background finalize failure surfacing
  - round-trip tolerance suite
  - external parser/validator compatibility harness
  - `phase7_evidence/*` creation and synchronization
- Explicitly out of scope:
  - new retention policy work
  - new privacy/redaction product scope beyond Phase 7-required boundary cleanup
  - security-signature (`G`) implementation
  - unrelated map/profile/runtime work
- Mode:
  - autonomous execution without per-phase approval unless blocked
- Output:
  - production code
  - tests
  - docs
  - evidence pack updates
  - plan score updates

## 0.1) Activation and Finish Authority

This contract is the canonical Phase 7 automation entrypoint.

When a user selects or references this contract for Phase 7 execution, the
agent is authorized and required to:

- continue beyond planning/docs work into implementation,
- complete code, test, evidence, and verification work in order,
- avoid stopping after contract creation, log creation, or score discussion,
- stop only for a recorded blocker covered by Section 10,
- refuse any `100/100` claim until Section 12 is satisfied in full.

Hard rule:

- Phase 7 is not considered "started" merely because the contract or execution
  log exists; autonomous execution means driving the workstream through real
  code-bearing steps until complete or formally blocked.

## 0.2) Ownership Map for Phase 7

Authoritative ownership for this automation run:

- `feature/igc`
  - lint contracts and validators
  - formatter/parser/lint parity
  - export/finalize diagnostics SSOT
  - existing-document read boundary
  - files UI state and mapping
  - compatibility harness support code
- `feature/map`
  - runtime orchestration integration only
  - no long-term ownership of lint or diagnostics taxonomy
- `docs/IGC`
  - execution log
  - evidence pack
  - plan/contract sync
- `docs/ARCHITECTURE`
  - only when Phase 7 changes pipeline or rule wording

## 1) Required Read Order Before Execution

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/IGC/README.md`
10. `docs/IGC/CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`
11. `docs/IGC/CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`

If replay/runtime loading behavior is touched while implementing Phase 7,
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
  allowed-file list captured before `P7-0` implementation begins.

Recommended execution log target:

- `docs/IGC/EXECUTION_LOG_IGC_PHASE7_RELEASE_GRADE_2026-03-10.md`

## 3) Non-Negotiable Phase 7 Rules

- Preserve `UI -> domain/use-case -> data`.
- Keep SSOT ownership explicit:
  - lint/compatibility decisions belong to domain/use-case outputs
  - latest export/finalize diagnostics belong to an IGC diagnostics repository
  - raw bytes for finalized/staged IGC documents belong to a `feature/igc`
    data boundary
- Core formatter/writer/repository/parser/UI contracts stay in `feature/igc`.
- `feature/map` is wiring/orchestration only for runtime integration.
- No generic finalize exception-only propagation for typed lint/compatibility
  failures.
- No direct `Context.contentResolver.openInputStream(...)` bypass in UI,
  replay-open helpers, or use-cases for validation-owned document reads.
- Lint must validate raw bytes or unsanitized lines before parser trimming.
- Parser/formatter/lint `I`-record rules must remain parity-locked.
- Replay behavior must remain deterministic and must not bypass finalized-file
  validation policy silently.
- Any unavoidable rule exception must be recorded in
  `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue, owner, expiry.

## 4) Mandatory Per-Step Automation Loop

For every Phase 7 item `P7-N`, run this exact loop:

1. Run `git status --short`.
2. Re-read the corresponding `P7-N` scope and exit criteria from the Phase 7
   section of the plan.
3. Record current assumptions in the execution log:
   - SSOT ownership deltas
   - dependency direction impact
   - time-base declarations
   - boundary adapters touched
4. Implement only `P7-N` scoped code/tests/docs.
5. Run the mandatory step build gate.
6. Run step-targeted tests.
7. Update the plan and execution log with:
   - files touched
   - commands run
   - pass/fail
   - score update
   - residual risks
8. Advance to `P7-(N+1)` only if the step gate is green or an explicit blocker
   is recorded with evidence.

No step skipping is allowed.

Hard rule:

- docs-only progress does not count as Phase 7 completion progress once the
  next code-bearing step is active; the runner must move into code and tests,
  not remain in planning mode indefinitely.

## 5) Step Build Gate Policy

### 5.1 Mandatory Basic Build Gate After Each Code Step

Use this minimum build gate after each code-bearing Phase 7 step:

```bash
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

If app wiring, DI, manifest, or instrumentation scope changes in the step,
also run:

```bash
./gradlew :app:assembleDebug
```

### 5.2 Retry Policy

If the step build gate fails:

1. fix step-owned issues and rerun the same build gate,
2. retry with `--no-configuration-cache` if failure indicates tooling/cache
   instability,
3. if still red, classify blocker as:
   - step-caused, or
   - unrelated/pre-existing
4. stop auto-advance after 3 failed attempts.

## 6) Required Final Verification

Phase 7 must use the plan verification order exactly:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew :feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.igc.*" --tests "com.trust3.xcpro.replay.Igc*"
./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.igc.*"
./gradlew :feature:igc:assembleDebug
./gradlew :feature:map:assembleDebug
./gradlew :feature:igc:connectedDebugAndroidTest --no-parallel
./gradlew :feature:map:connectedDebugAndroidTest --no-parallel
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Hard rule:

- final claim requires the full order above to pass in two consecutive runs.

KSP stability fallback when needed:

```bash
./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" <tasks>
```

## 7) Phase 7 Step Contract

### `P7-0` Preflight and Scope Lock

Scope:

- confirm Phase 5 and user-closed Phase 6 are treated as entry prerequisites
- create/update execution log
- freeze Phase 7 scope and owners
- record current score and open blockers

Required files:

- `docs/IGC/CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`
- this contract
- execution log

Exit criteria:

- active plan, contract, and execution log are aligned
- allowed dirty-file set is recorded
- starting score and blocker list are documented

### `P7-1` Add Contracts and Models

Scope:

- add lint domain contracts, issue taxonomy, and UI-safe message mapping
- add diagnostics SSOT contract
- lock document-read boundary ownership in `feature/igc`

Primary files:

- `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcLintValidator.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcLintIssue.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcLintRuleSet.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/usecase/IgcLintMessageMapper.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcExportDiagnosticsRepository.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcDownloadsRepository.kt`

Focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.igc.*Lint*" --tests "com.trust3.xcpro.igc.*Diagnostic*"
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

Exit criteria:

- lint contracts exist
- diagnostics SSOT contract exists
- existing-document read ownership is explicit

### `P7-2` Implement Lint Validation in Finalize/Export Path

Scope:

- validate assembled file payloads before publish
- return typed lint failures from repository/use-case boundaries
- enforce raw-byte/unsanitized-line validation where required

Primary files:

- `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcExportValidationAdapter.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFileModels.kt`

Focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.igc.data.*" --tests "com.trust3.xcpro.igc.domain.*Lint*"
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

Exit criteria:

- finalize/export path returns typed lint failures
- lint rule coverage exists for each rule and precedence path

### `P7-3` Implement Typed Failure Surfacing

Scope:

- replace generic finalize exception propagation with typed outcomes
- publish background finalize/export diagnostics to SSOT
- unify replay/share/export failure messaging through shared mapping

Primary files:

- `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcRecordingRuntimeActionSink.kt`
- `feature/map/src/main/java/com/trust3/xcpro/vario/VarioServiceManager.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/ui/IgcFilesViewModel.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/screens/replay/IgcFilesScreen.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/screens/replay/IgcFilesShareIntents.kt`

Focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.igc.*Diagnostic*" --tests "com.trust3.xcpro.igc.ui.*"
./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.igc.*"
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

Exit criteria:

- background failures are observable from SSOT
- no screen-local failure taxonomy drift remains
- no typed lint/compatibility failure falls back to generic unknown copy

### `P7-4` Implement Round-Trip Tolerance Suite

Scope:

- add deterministic writer->parser parity tests
- add explicit tolerance matrices
- add parser-vs-lint parity tests for invalid `I` definitions and line order

Primary files:

- `feature/igc/src/test/java/com/trust3/xcpro/igc/data/*`
- `feature/igc/src/test/java/com/trust3/xcpro/replay/*`
- `feature/map/src/test/java/com/trust3/xcpro/igc/usecase/*`

Focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.replay.Igc*" --tests "com.trust3.xcpro.igc.*"
./gradlew :feature:igc:assembleDebug :feature:map:assembleDebug
```

Exit criteria:

- tolerance suite is green
- parity fixtures cover invalid `I` and line-structure failures

### `P7-5` Implement External Compatibility Harness

Scope:

- add reproducible external parser/validator harness
- record parser-specific rejection details
- make local/CI command path explicit

Primary files:

- compatibility runner scripts/docs under `docs/IGC/phase7_evidence/`
- supporting test harness code under `feature/igc` as needed

Focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.replay.Igc*"
./gradlew :feature:igc:assembleDebug
```

Exit criteria:

- at least two independent external validator/parser checks are reproducible
- fixture ID, failing line, and reason are captured

### `P7-6` Docs and Evidence Sync

Scope:

- populate all `phase7_evidence/*`
- update pipeline/docs if runtime or diagnostics wiring changed
- sync plan, contract, and evidence

Primary files:

- `docs/IGC/phase7_evidence/*`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/IGC/README.md`
- phase 7 plan section

Exit criteria:

- all Phase 7 evidence artifacts exist and are current
- docs describe lint taxonomy and diagnostics behavior accurately

### `P7-7` Verification Execution Order

Scope:

- run the exact verification order from Section 6
- capture outputs and failing paths if any gate fails

Exit criteria:

- full verification order is green once
- evidence pack captures commands, timestamps, pass/fail, and updated files

### `P7-8` Final Claim Gate

Scope:

- rerun full verification order
- publish final score and completion decision

Exit criteria:

- full verification order is green in two consecutive runs
- all Phase 7 evidence artifacts are populated with pass results
- final score is exactly `100/100`
- no blocking Phase 7 architecture deviation remains open

## 8) Score Publication Contract

Use the Phase 7 rubric exactly:

- spec coverage/parity: 40
- automated test depth: 30
- determinism/architecture compliance: 20
- operational hardening/docs sync: 10

After each step, update:

- total score `/100`
- impacted rubric categories only
- evidence basis:
  - files changed
  - tests added/updated
  - commands run
  - remaining risks

Hard rule:

- never publish `100/100` while any claim criterion in `P7-8` remains open.

## 9) Evidence Contract

For every step, record:

- date
- step identifier and status
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
  - `core/time/src/main/java/com/trust3/xcpro/core/time/Clock.kt`
  - `app/src/main/java/com/trust3/xcpro/di/TimeModule.kt`

Required evidence destinations:

- `docs/IGC/phase7_evidence/phase7_gates.md`
- `docs/IGC/phase7_evidence/phase7_roundtrip_tolerance_matrix.md`
- `docs/IGC/phase7_evidence/phase7_external_compatibility_matrix.md`
- `docs/IGC/phase7_evidence/phase7_lint_rule_matrix.md`
- `docs/IGC/phase7_evidence/phase7_parser_lint_parity_matrix.md`
- `docs/IGC/phase7_evidence/phase7_error_taxonomy_mapping.md`
- `docs/IGC/phase7_evidence/phase7_manual_checklist.md`

## 10) Blocker Policy

Automation must stop and publish blocker evidence when any is true:

- parser/formatter/lint parity cannot be made coherent within step scope
- external validator harness is unavailable or non-reproducible
- phase build gate remains red after retry policy is exhausted
- connected instrumentation is unavailable for required proof
- dirty worktree ambiguity makes ownership unsafe
- unknown external changes invalidate deterministic execution assumptions

Blocker record must include:

- failing command
- first failing file or test
- classification:
  - step-caused
  - unrelated/pre-existing
  - environment-blocked
- proposed next action pack

## 11) Documentation Sync Rules

If pipeline wiring changes, update:

- `docs/ARCHITECTURE/PIPELINE.md`

If architecture/rules/policies change, update:

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`

If Phase 7 scope or scoring changes, update:

- `docs/IGC/CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`
- this contract

If a known rule is intentionally violated, update:

- `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`

## 12) Completion Criteria

Automation may declare Phase 7 complete only when:

1. steps `P7-0 -> P7-8` are completed in order or explicitly blocked with
   evidence,
2. required final verification is green in two consecutive runs,
3. final Phase 7 score is exactly `100/100`,
4. diagnostics SSOT, lint contracts, and document-read authority are all in
   place,
5. parser/formatter/lint parity is proven,
6. internal and external compatibility suites are green,
7. plan, contract, execution log, and evidence pack are synchronized.

Explicitly insufficient on their own:

- contract creation only,
- execution log creation only,
- plan edits only,
- score discussion without verification,
- partial evidence pack population without green gates.

## 13) Practical Advice for the Runner

Use this contract to automate the whole active Phase 7 workstream, not the
broader Phase 8 security work.

Recommended start order:

1. create execution log,
2. record allowed dirty-file set,
3. run `P7-0`,
4. execute `P7-1` only until green,
5. continue step-by-step without mixing unrelated cleanup work.

Best operating mode:

- one dedicated Phase 7 branch
- one commit per major step or small step slice
- no broad "implement all of Phase 7" attempts in one diff

Required first execution moves after this contract is activated:

1. create or update the execution log,
2. record the explicit user directive that this contract is active,
3. lock the allowed dirty-file set,
4. complete `P7-0`,
5. immediately enter `P7-1` rather than stopping at documentation setup.

Reason:

- this work crosses `feature:igc`, `feature:map`, replay/parser behavior,
  diagnostics SSOT, instrumentation, and external compatibility proof
- reliability comes from strict step gates, not from one large diff
