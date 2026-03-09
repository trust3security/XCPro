# REVIEW_IGC_RECOVERY_FOCUSED_CODE_PASS_2026-03-09.md

## Purpose

Capture the focused code-pass findings for the current IGC recovery slice and
tie them to the release-grade recovery plan.

Related plan:

- `docs/IGC/CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`

Scope of this review:

- startup recovery only
- restart/bootstrap ownership
- staged/finalized artifact handling
- recovery metadata and typed failure semantics

Out of scope:

- retention
- share redaction/privacy
- general IGC files UI

## Current Slice Assessment

- Architecture cleanliness: `4.5/5`
- Maintainability/change safety: `4.5/5`
- Test confidence on risky paths: `4.5/5`
- Release readiness for recovery slice: `4.5/5`

Reason the score is not `5/5`:

- the current slice is solid for finalize-time restart recovery
- it is not yet release-grade for active-flight restart continuity,
  authoritative metadata, and full invariant proof

## Findings

### F1 High: Short-form `HFDTE` is missed

- `IgcFlightLogRepository.parseStagedRecoveryMetadata()` only looks up
  `HFDTEDATE:`
- `parseSessionHeaderDate(...)` accepts both `HFDTEDATE:` and `HFDTE`
- impact: valid staged files can be misclassified as corrupt

### F2 High: Bootstrap outcome contract is overloaded

- `IgcRecordingUseCase.attemptStartupRecovery()` uses
  `IgcRecoveryResult.NoRecoveryWork` for more than one meaning
- impact: bootstrap cannot cleanly distinguish:
  - unsupported repository
  - resume existing live snapshot
  - terminal recovery did not run

### F3 High: Pending-row cleanup depends on parsed staged metadata

- cleanup keys for pending MediaStore rows are derived from parsed metadata
- impact: corrupt staged metadata prevents deterministic orphan cleanup

### F4 Medium: Startup exceptions lose provenance

- thrown bootstrap exceptions are collapsed into
  `PENDING_ROW_WRITE_FAILED`
- impact: diagnostics are weaker than the Phase 6 contract

### F5 Medium: Finalized detection is still heuristic

- existing finalized entries are inferred from filename prefix
- impact: duplicate guard is not yet authoritative across restart boundaries

### F6 High: Duplicate-session guard is not enforced

- the contract exposes `DUPLICATE_SESSION_GUARD`
- current lookup returns the first matching finalized file instead of failing
- impact: duplicate finalized exports can be silently masked

### F7 High: Mid-flight restart continuity is still missing

- startup recovery currently treats both `Recording` and `Finalizing`
  snapshots as recovery candidates
- staging bytes are only written during finalize/export
- impact: a real mid-flight crash can clear a valid `Recording` snapshot
  instead of resuming it

### F8 High: `fallbackSessionStartWallTimeMs` is effectively dead

- snapshot persistence does not store wall-start authority
- bootstrap currently passes `0L`
- impact: fallback UTC-date resolution is not a real safety path

### F9 Medium: One bootstrap test is unrealistic

- a current test allows a `Recording` snapshot to recover directly to
  `Recovered(...)`
- impact: test confidence is overstated for a path production cannot produce
  with current staging semantics

## What Was Missed

The key miss was scope interpretation.

The implemented slice correctly hardened this case:

1. finalization has started
2. a staged/finalized artifact path exists
3. the app dies during publish
4. restart recovery cleans up or republishes deterministically

The slice did not yet solve this different case:

1. flight is still actively recording
2. no staged export exists yet
3. the app dies
4. restart should resume the live snapshot, not terminally recover it

That distinction now needs to be explicit in code and in tests.

## Required Plan Changes

The release-grade recovery plan must require:

1. explicit bootstrap outcomes:
   - `Recovered`
   - `TerminalFailure`
   - `ResumeExisting`
   - `Unsupported`
2. `Recording` restart continuity to be proven separately from finalize
   recovery
3. structured recovery metadata keyed by `sessionId`
4. authoritative wall-start/date identity
5. duplicate finalized-match guard as a typed invariant failure
6. production-realistic restart tests

These requirements are now captured in:

- `docs/IGC/CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md`

## Recommended Execution Order

1. Extract a dedicated `IgcRecoveryBootstrapUseCase`.
2. Split `Recording -> ResumeExisting` from `Finalizing -> terminal recovery`.
3. Add authoritative recovery metadata persistence.
4. Close the `K1..K7` matrix with realistic restart tests.
5. Add device-level MediaStore proof.

## Acceptance Reminder

Recovery should not be called release-grade until all of the following are
true:

- no constructor-side recovery I/O remains
- `Recording` restart continuity is preserved
- staged metadata is no longer the sole authority
- duplicate finalized matches fail deterministically
- `K1..K7` is explicitly green
