# CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md

## Purpose

Define a production-grade phased implementation plan to raise the current IGC
recovery slice from strong local hardening to release-grade recovery behavior
with explicit ownership, deterministic restart handling, and spec-locked proof.

Primary target outcomes:

1. Recovery orchestration has one explicit owner and no constructor-side I/O.
2. Recovery metadata is authoritative, structured, and not inferred from staged
   log bytes alone.
3. Restart behavior is deterministic for the full kill-point matrix (`K1..K7`).
4. Pending MediaStore rows and staged temp files are always cleaned on terminal
   recovery outcomes.
5. Recovery diagnostics are observable and auditable without leaking file I/O
   concerns into UI or ViewModel layers.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/IGC/CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`

## 0) Metadata

- Title: IGC Recovery Release Grade Plan
- Owner: XCPro Team
- Date: 2026-03-09
- Issue/PR: IGC-P6-RECOVERY-RELEASE-GRADE
- Status: Signed Off
- Depends on:
  - current recovery slice implementation in:
    - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/igc/usecase/IgcRecordingUseCase.kt`
  - Phase 6 recovery contract in:
    - `docs/IGC/CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`

Execution update (2026-03-10 / Recovery slice signed off):

- Status:
  - Phase 0 complete
  - Phase 1 complete
  - Phase 2 complete
  - Phase 3 complete
  - Phase 4 complete
  - Phase 5 complete
  - Recovery slice sign-off recorded
- Delivered in Phase 1:
  - dedicated `IgcRecoveryBootstrapUseCase`
  - explicit bootstrap outcome taxonomy
  - `Recording -> ResumeExisting`
  - `Finalizing -> repository terminal recovery`
- Delivered in Phase 2:
  - dedicated `IgcRecoveryMetadataStore`
  - runtime sink persistence for start and first-valid-fix recovery metadata
  - structured metadata used as primary recovery authority
  - duplicate finalized-match guard promoted to typed failure
  - pending-row cleanup no longer depends on staged-byte metadata parse success
  - short-form `HFDTE` parsing fixed
  - dead `fallbackSessionStartWallTimeMs` removed from the recovery contract
- Delivered in Phase 3:
  - explicit `K2..K6` repository kill-point tests
  - explicit `K1` bootstrap/use-case restart failure tests
  - explicit `K7` restart-after-snapshot-clear test
  - named restart coverage proving `Recording -> ResumeExisting`
- Delivered in Phase 4:
  - real MediaStore restart instrumentation running on a connected Android device
  - pending-row cleanup proven against actual resolver behavior
  - rerun proof showing no duplicate finalized output after successful recovery
- Delivered in Phase 5:
  - feature-level `IgcRecoveryDiagnosticsReporter` contract
  - app-level diagnostics capture with `Clock`-backed wall timestamps
  - typed bootstrap outcome mapping for `resume`, `recovered`, `unsupported`,
    repository failure, and exception-originated terminal failure
  - Phase 6 evidence pack populated for kill matrix, gates, and manual signoff
- Interim score after Phase 5:
  - Architecture and ownership clarity: `24/25`
  - Deterministic recovery semantics: `24/25`
  - Automated kill-point and restart proof: `30/30`
  - Operational diagnostics and docs/evidence: `20/20`
  - Total: `98/100`
- Remaining blockers to release-grade:
  - none for the recovery release-grade slice
- Sign-off note:
  - user sign-off recorded on 2026-03-10 for the recovery release-grade
    sub-slice only
  - this sign-off does not apply to the broader
    `CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`
    Phase 6 workstream

Focused code pass update (2026-03-09):

- F1 High:
  - `IgcFlightLogRepository.parseStagedRecoveryMetadata()` currently searches
    only `HFDTEDATE:` lines even though `parseSessionHeaderDate(...)` accepts
    both `HFDTEDATE:` and short-form `HFDTE`.
  - Impact: short-form DTE headers will be misclassified as corrupt recovery
    metadata.
- F2 High:
  - `IgcRecordingUseCase.attemptStartupRecovery()` currently overloads
    `IgcRecoveryResult.NoRecoveryWork` for two different meanings:
    unsupported repository and intentional snapshot resume.
  - Impact: bootstrap intent is ambiguous and the recovery outcome contract is
    too weak for release-grade ownership.
- F3 High:
  - corrupt-metadata recovery paths cannot deterministically clean orphan
    pending MediaStore rows because cleanup keys are derived from parsed
    metadata.
  - Impact: pending-row cleanup proof is incomplete until metadata authority is
    decoupled from staged-byte parsing.
- F4 Medium:
  - thrown startup recovery exceptions are flattened to
    `PENDING_ROW_WRITE_FAILED`.
  - Impact: typed diagnostics lose provenance and operators cannot distinguish
    bootstrap failures from repository write failures.
- F5 Medium:
  - finalized-entry detection is still inferred from filename prefix rather
    than authoritative session metadata keyed by `sessionId`.
  - Impact: duplicate-session guard is strong but still heuristic across
    restart boundaries.
- F6 High:
  - the recovery contract exposes `DUPLICATE_SESSION_GUARD`, but
    `findExistingFinalizedEntryForSession(...)` currently sorts matching files
    and returns the first hit rather than detecting multiple finalized matches.
  - Impact: duplicate finalized exports can be silently masked instead of being
    surfaced as a typed recovery invariant breach.
- F7 High:
  - startup recovery currently sends both `Recording` and `Finalizing`
    snapshots through terminal recovery, but staging bytes are only written
    inside `finalizeSession(...)`.
  - Impact: mid-flight process death can clear a valid `Recording` snapshot
    even though there is no finalized/staged artifact set to recover yet.
- F8 High:
  - `fallbackSessionStartWallTimeMs` is effectively dead today:
    `IgcSessionStateMachine.Snapshot` does not persist wall-start authority and
    `IgcRecordingUseCase` always passes `0L`.
  - Impact: repository fallback UTC-date resolution is not real protection and
    recovery still lacks an authoritative session-start source outside staged
    bytes.
- F9 Medium:
  - current bootstrap tests model an unrealistic happy path by allowing a
    `Recording` snapshot to return `Recovered(...)` from a fake repository even
    though production code does not create staging bytes before finalize.
  - Impact: test confidence is overstated on the most important restart
    boundary.

## 1) Scope

- Problem statement:
  - Current recovery behavior is functional and verified locally, but the
    orchestration still mixes bootstrap, state restore, and recovery I/O in one
    path, and the proof does not yet satisfy the full release-grade kill-point
    matrix.
- Why now:
  - The current slice is good enough for development hardening, but not strong
    enough to claim Phase 6 recovery is fully production-grade.
- In scope:
  - Recovery ownership and bootstrap refactor.
  - Structured recovery metadata persistence.
  - Full kill-point unit/instrumentation coverage.
  - Typed recovery diagnostics and evidence artifacts.
  - Recovery-slice diff hygiene.
- Out of scope:
  - Retention policy implementation.
  - Privacy/redaction share copy implementation.
  - IGC files UI controls outside recovery diagnostics.
- User-visible impact:
  - No intended UX redesign.
  - Better restart safety.
  - Better failure observability after interrupted finalization.

## 2) Score Contract

Score meaning:

- `95-100`: release-grade for recovery scope.
- `85-94`: strong but missing required release proof.
- `<85`: not production-ready.

Current recovery-slice assessment:

- Architecture cleanliness: `4.5/5`
- Maintainability/change safety: `4.5/5`
- Test confidence on risky paths: `4.5/5`
- Release readiness for recovery slice: `4.5/5`

Release-grade score formula:

- Architecture and ownership clarity: 25
- Deterministic recovery semantics: 25
- Automated kill-point and restart proof: 30
- Operational diagnostics and docs/evidence: 20

Required minimum:

- Total `>= 95/100`
- No category below `23/25` or `27/30` for tests
- No unresolved recovery-slice architecture deviation

Hard fail conditions:

- Recovery I/O is still initiated implicitly from `IgcRecordingUseCase`
  constructor/restore path without a dedicated bootstrap owner.
- Recovery bootstrap still uses one result (`NoRecoveryWork`) for both
  unsupported-repository and resume-existing-snapshot outcomes.
- Recovery metadata is still derived only by parsing staged `.igc.tmp` bytes.
- Recovery metadata handling still fails short-form `HFDTE` header parsing.
- Multiple finalized matches for one recovered session can still collapse to a
  silent first-match selection instead of `DUPLICATE_SESSION_GUARD`.
- `Recording` snapshots without finalized/staged recovery material can still be
  terminally cleared instead of resumed.
- Recovery fallback still depends on a wall-start input that is never actually
  persisted or supplied at bootstrap.
- Kill-point coverage does not explicitly prove `K1..K7`.
- Pending rows can survive terminal recovery.
- Recovery result is not observable as a typed diagnostic outcome.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| persisted in-flight session snapshot | `IgcSessionStateSnapshotStore` | snapshot load/store API | ad-hoc runtime mirrors |
| recovery metadata per session | dedicated recovery metadata store | typed metadata record by `sessionId` | inferred header/B-record reconstruction as authority |
| staged IGC temp bytes | `IgcFlightLogRepository` data adapter | file-private storage only | UI/use-case temp file writes |
| finalized IGC archive entry | `IgcDownloadsRepository` / MediaStore | `StateFlow<List<IgcLogEntry>>` | raw file scans in UI |
| startup recovery outcome | dedicated recovery coordinator/use-case | typed result/event/log | swallowed bootstrap side effects |

### 3.2 Dependency Direction

Required direction remains:

`UI -> domain/use-case -> data`

Modules/files expected to change:

- `feature/map/src/main/java/com/trust3/xcpro/igc/usecase/IgcRecordingUseCase.kt`
- new recovery bootstrap owner under `feature/map/.../igc/usecase/` or
  `feature/igc/.../usecase/`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt`
- `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcSessionStateSnapshotStore.kt`
- new recovery metadata adapter under `feature/igc/.../data/`

Boundary risk:

- bootstrap code drifting into manager/controller glue
- use-case directly owning file and MediaStore cleanup policy
- diagnostics leaking Android/data concerns upward

### 3.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| startup recovery trigger | `IgcRecordingUseCase.restoreOrCreateStateMachine()` | dedicated recovery bootstrap coordinator/use-case | remove constructor-side I/O and isolate bootstrap semantics | coordinator unit tests |
| recovery metadata authority | staged file parser | typed metadata store | eliminate inferred authority and collision ambiguity | metadata contract tests |
| recovery outcome publication | implicit side effect | typed recovery result sink/state/log | improve auditability and failure handling | use-case tests |

### 3.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `IgcRecordingUseCase.restoreOrCreateStateMachine()` | snapshot restore path calls repository recovery directly | dedicated recovery bootstrap call before machine restore | P1 |
| `IgcFlightLogRepository.parseStagedRecoveryMetadata()` | parse staged bytes as authoritative metadata | staged bytes as fallback only after structured metadata lookup | P2 |

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| session snapshot lifecycle | monotonic phase state + persisted IDs | state machine contract |
| recovery metadata UTC date/time | wall/UTC | IGC file naming and publish identity |
| cleanup timeout/wait windows | monotonic where runtime-gated | deterministic runtime behavior |
| file modified timestamps | wall | MediaStore/file metadata only |

Forbidden:

- monotonic vs wall comparisons
- replay vs wall comparisons
- direct `System.currentTimeMillis()` in domain/recovery orchestration

### 3.4 Threading and Cadence

- Bootstrap recovery coordinator dispatcher: `IO` for storage work,
  `Default` or caller-owned for orchestration.
- No heavy recovery I/O on `Main`.
- Startup recovery must complete before live recording state is restored.

### 3.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - replay path remains unchanged
  - recovery applies only to live persisted snapshots and finalized staging

### 3.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| constructor-side bootstrap I/O remains | ARCHITECTURE + AGENT ownership clarity | unit test + review | recovery bootstrap tests |
| metadata authority still inferred from staged bytes | SSOT ownership | unit test + review | metadata store tests |
| pending rows survive restart | operational hardening | instrumentation test | recovery restart instrumentation |
| stale snapshot restores after terminal recovery | deterministic lifecycle | unit test | recovery bootstrap tests |
| hidden recovery failure | observability/diagnostics | unit test + docs evidence | recovery diagnostics tests |

## 4) Data Flow (Before -> After)

Current slice:

`SnapshotStore -> IgcRecordingUseCase.restoreOrCreateStateMachine() -> IgcFlightLogRepository.recoverSession() -> state machine reset/restore`

Target release-grade flow:

`SnapshotStore + RecoveryMetadataStore -> IgcRecoveryBootstrapUseCase -> IgcFlightLogRepository cleanup/publish -> typed recovery outcome -> state machine restore/new machine`

Rules:

- bootstrap recovery runs before live state-machine restoration
- bootstrap owner decides whether to:
  - recover and clear snapshot
  - fail terminally and clear snapshot
  - retain snapshot for safe non-terminal resume
- data adapters never push directly into UI

## 5) Phased Implementation Plan

### Phase 0 - Contract Freeze and Current-State Audit

Goal:

- Freeze recovery-slice scope, ownership, and scoring criteria.

Files to change:

- this plan
- `docs/IGC/README.md`
- optionally `docs/ARCHITECTURE/PIPELINE.md` if terminology is clarified

Tests to add/update:

- none required

Exit criteria:

- plan accepted as the active recovery hardening IP
- explicit owner chosen for recovery bootstrap and metadata store

### Phase 1 - Recovery Bootstrap Owner Extraction

Goal:

- Remove recovery I/O from `IgcRecordingUseCase` restore logic and place it in a
  dedicated bootstrap coordinator/use-case.

Files to change:

- `feature/map/src/main/java/com/trust3/xcpro/igc/usecase/IgcRecordingUseCase.kt`
- new `IgcRecoveryBootstrapUseCase.kt`
- DI bindings where required

Tests to add/update:

- `IgcRecoveryBootstrapUseCaseTest`
- update `IgcRecordingUseCaseTest`
- add explicit bootstrap outcome tests for:
  - `Recovered`
  - `TerminalFailure`
  - `ResumeExisting`
  - `Unsupported`

Exit criteria:

- `IgcRecordingUseCase` only restores/creates machine state
- bootstrap recovery can be invoked and tested independently
- bootstrap outcome taxonomy no longer overloads `NoRecoveryWork`
- `Recording` snapshots with no recoverable staged/finalized artifact path are
  classified as `ResumeExisting`, not terminal recovery failure

### Phase 2 - Structured Recovery Metadata Authority

Goal:

- Persist authoritative recovery metadata by `sessionId` rather than deriving it
  only from staged bytes.

Files to change:

- new metadata contract/store under `feature/igc/.../data/`
- `IgcRecordingRuntimeActionSink.kt`
- `IgcFlightLogRepository.kt`

Tests to add/update:

- metadata round-trip tests
- collision/date authority tests
- staged-byte fallback tests
- dual-format DTE parsing tests (`HFDTEDATE:` and `HFDTE`)
- session-start wall-time authority tests
- corrupt-metadata pending-row cleanup tests
- duplicate finalized-match guard tests
- authoritative finalized-entry lookup tests keyed by session metadata, not
  filename prefix only

Exit criteria:

- structured metadata is primary authority
- staged byte parsing is fallback only
- direct ambiguity around UTC date/session identity is removed
- recovery has authoritative wall-start/date identity even when no staged bytes
  are available or staged metadata is unreadable
- orphan pending-row cleanup no longer depends on successful staged-byte parse
- duplicate finalized matches are classified as typed recovery failures, not
  silently accepted

### Phase 3 - Kill-Point Matrix Closure (`K1..K7`)

Goal:

- Add explicit, named tests for every Phase 6 recovery kill point.

Files to change:

- `feature/igc/src/test/java/.../IgcFlightLogRepositoryRecoveryKillPointTest.kt`
- `feature/map/src/test/java/.../IgcRecoveryBootstrapUseCaseTest.kt`
- `feature/map/src/test/java/.../IgcRecordingUseCaseTest.kt`

Tests to add/update:

- `K1` crash after snapshot persist, before staged write
- `K2` crash after staged write, before MediaStore insert
- `K3` crash after row insert with pending row
- `K4` crash mid-byte copy
- `K5` crash after byte copy, before `IS_PENDING=0`
- `K6` crash after publish, before snapshot clear
- `K7` crash after snapshot clear
- duplicate finalized export invariant test for one `sessionId`
- recording-snapshot restart test proving safe resume when no staged/finalized
  recovery artifact exists

Exit criteria:

- full matrix is green with typed expected outcomes
- no duplicate final file for one `sessionId`
- mid-flight restart continuity is explicitly proven and does not depend on
  fake repository behavior

### Phase 4 - Device/MediaStore Instrumentation Proof

Goal:

- Prove pending-row cleanup and restart behavior against actual Android storage
  behavior, not just mocks.

Files to change:

- `feature/map/src/androidTest/java/com/trust3/xcpro/igc/IgcRecoveryRestartInstrumentedTest.kt`
- supporting test fakes/utilities if needed

Tests to add/update:

- startup recovery with staged file present
- pending row deletion on restart
- no finalized duplicate after recovery rerun

Exit criteria:

- at least one end-to-end restart recovery instrumentation path is green
- pending row cleanup is demonstrated with real resolver behavior

### Phase 5 - Typed Diagnostics and Operator Evidence

Goal:

- Make recovery outcomes observable and produce evidence artifacts required for
  release signoff.

Files to change:

- recovery diagnostics contract/use-case output
- `docs/IGC/phase6_evidence/phase6_recovery_kill_matrix.md`
- `docs/IGC/phase6_evidence/phase6_gates.md`
- `docs/IGC/phase6_evidence/phase6_manual_checklist.md`

Tests to add/update:

- diagnostic mapping tests
- non-fatal failure reporting tests
- exception provenance mapping tests for bootstrap-owner vs repository failure

Exit criteria:

- terminal recovery outcomes are typed and logged/observable
- evidence pack is populated and current
- diagnostics preserve source/provenance of failure classification

### Phase 6 - Diff Hygiene and Release Gate

Goal:

- Finish the recovery workstream with a clean scope and full verification.

Files to change:

- remove or separate unrelated retention/share scaffolding from recovery branch
- final doc index references

Tests to add/update:

- none new; verification only

Exit criteria:

- working tree contains only recovery-slice artifacts or separately tracked
  scaffolding with explicit ownership
- verification sequence passes

## 6) Test Plan

- Unit tests:
  - bootstrap owner tests
  - metadata store contract tests
  - repository recovery kill-point matrix
  - duplicate-guard and cleanup tests
- Replay/regression tests:
  - confirm replay paths are unaffected
- UI/instrumentation tests:
  - restart recovery with MediaStore pending-row cleanup
- Degraded/failure-mode tests:
  - corrupt metadata
  - missing staging file
  - name collision exhaustion
  - publish write failure
- Boundary tests for bypass removal:
  - `IgcRecordingUseCase` no longer performs repository recovery in restore path
  - `Recording` restart tests use production-realistic staging semantics

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Recovery-focused checks:

```bash
./gradlew :feature:igc:testDebugUnitTest --tests "com.trust3.xcpro.igc.data.*Recovery*"
./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.igc.usecase.*Recovery*"
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| recovery bootstrap refactor changes startup ordering | session restore regression | add explicit before/after startup tests | XCPro Team |
| metadata store drifts from staged bytes | wrong publish identity | use structured metadata as authority and staged bytes as validation/fallback only | XCPro Team |
| recording snapshots are terminally cleared on restart | flight continuity regression | split bootstrap outcomes into resume vs terminal recovery and prove with tests | XCPro Team |
| instrumentation flakiness | weak release proof | keep unit matrix exhaustive and instrumentation focused to storage invariants | XCPro Team |
| unrelated Phase 6 WIP muddies recovery validation | review and release confusion | isolate or remove non-recovery scaffolding before signoff | XCPro Team |

## 8) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Recovery bootstrap owner is explicit and independently testable
- Time base handling is explicit in code and tests
- `Recording` snapshot restart continuity is preserved when finalize recovery is
  not applicable
- Full `K1..K7` recovery matrix is green
- Pending rows and staged files are cleaned on terminal recovery outcomes
- Recovery diagnostics are typed and observable
- `KNOWN_DEVIATIONS.md` remains unchanged unless explicitly approved
- duplicate finalized exports for one recovered session are surfaced as
  `DUPLICATE_SESSION_GUARD`

## 9) Rollback Plan

- What can be reverted independently:
  - recovery bootstrap extraction
  - metadata store authority change
  - diagnostics publication layer
  - instrumentation/docs additions
- Recovery steps if regression is detected:
  - revert bootstrap owner change first
  - preserve existing repository idempotency and cleanup fixes
  - rerun recovery-focused unit suite before further rollout

## 10) Advisement

Recommended execution order:

1. Phase 1 first. It gives the biggest maintainability lift with the smallest
   behavior risk.
2. Phase 2 second. Structured metadata removes the current biggest ambiguity.
3. Phase 3 before any broader Phase 6 claims. Without `K1..K7`, release-grade
   recovery is still not proven.
4. Phase 4 and 5 only after the unit matrix is stable.
5. Keep retention/privacy out of this workstream until recovery is closed.

Fastest path from current `4.5/5` to `5/5`:

- extract recovery bootstrap owner
- add structured metadata authority
- close the kill-point matrix
- prove one real MediaStore restart path on device/emulator
- clean unrelated WIP from the branch
