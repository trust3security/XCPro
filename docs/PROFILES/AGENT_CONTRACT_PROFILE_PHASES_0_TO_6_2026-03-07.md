# AGENT CONTRACT - PROFILE PHASES 0 TO 6 (AUTONOMOUS)

## Purpose

Define a production-grade autonomous execution contract so an agent can implement
`Phase 0` through `Phase 6` of:

- `docs/PROFILES/CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07.md`

without additional user assistance, while preserving architecture, determinism,
and release safety.

## 0) Authority and Mode

- Execution mode: autonomous.
- User interaction: none required for normal execution.
- Decision rule under ambiguity: choose the most architecture-consistent option,
  implement it, and record it in execution notes.
- Prohibited shortcuts:
  - skipping required gates,
  - bypassing use-case/repository boundaries,
  - introducing temporary hidden globals,
  - shipping partial restore behavior without explicit failure reporting.

## 1) Mandatory Inputs (Read Before Any Code Change)

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/PROFILES/CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07.md`
10. `docs/PROFILES/PROFILE_STARTUP_AND_DEFAULT_POLICY.md`
11. `docs/PROFILES/PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`

## 2) Pre-Approved Decisions (No Additional Approval Needed)

- Canonical default profile ID: `default-profile`.
- Legacy aliases (`default`, `__default_profile__`) are migration inputs only.
- Backup folder contract stays user-accessible at `Download/XCPro/profiles/`.
- Backup cleanup is managed-file only and must preserve user archival files.
- Profile backup/import contract is unified to one bundle path with compatibility
  support for existing user artifacts.
- Secrets and sync checkpoints are excluded from profile bundle payload.

## 3) Non-Negotiable Architecture Rules

- Dependency direction remains `UI -> domain/use-case -> data`.
- Profile/state ownership remains SSOT in repository owners.
- ViewModels use use-cases only, no repository or storage bypass.
- No direct wall/system time in domain/fusion logic.
- Replay behavior must remain deterministic.
- No Compose runtime state in non-UI managers/domain classes.
- Any unavoidable deviation must be entered in
  `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue, owner, expiry.

## 4) Autonomous Execution Protocol

For each phase, the agent must do all of the following before advancing:

1. Declare SSOT ownership changes.
2. Declare time-base impact (if any) and forbidden comparisons.
3. Implement code and tests for the phase scope only.
4. Run required checks.
5. Record evidence (files, tests, results, risks).
6. If gates fail, fix and rerun until green or a deviation is explicitly recorded.

No phase skipping. Order is strict: `0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6`.

## 5) Phase Contract (0 To 6)

### Phase 0 - Baseline and Contract Freeze

- Outcome:
  - Scope matrix finalized (Tier A / Tier B / Exclude).
  - Canonical profile identity and include/exclude policy locked.
- Must deliver:
  - updated profile docs and phase-ready baseline notes.
- Gate:
  - baseline tests/specs for current known gaps are present.

### Phase 1 - Canonical Identity and Startup Hardening

- Outcome:
  - no runtime drift across `default-profile`, `default`, `__default_profile__`.
  - no non-empty-profile startup path with null active profile.
  - startup error/selection paths are actionable (no no-op skip path).
- Must deliver:
  - resolver-based ID normalization across profile/map/dfcards touchpoints.
  - deterministic default-profile presence repair for legacy snapshots.
  - startup selection and fallback behavior tests.
- Gate:
  - identity migration and startup invariant tests pass.

### Phase 2 - Bundle Schema V1 and Storage Engine

- Outcome:
  - versioned bundle schema with deterministic parser/writer.
  - atomic managed writes and latest-wins ordering.
  - cleanup preserves non-managed user files and cross-variant ownership safety.
- Must deliver:
  - unified compatibility parser path.
  - parse-failure safe-mode behavior preventing destructive sync.
  - explicit backup policy alignment (`backup_rules` and extraction rules).
- Gate:
  - schema, atomicity, ordering, ownership, and parse-failure tests pass.

### Phase 3 - Snapshot Adapter Export Coverage

- Outcome:
  - Tier A profile bundle captures full required settings domains.
  - profile visibility/card/layout/glider polar and required settings are exported.
- Must deliver:
  - snapshot adapters for each Tier A owner repository/store.
  - tests per adapter and bundle section presence coverage.
- Gate:
  - Tier A section coverage tests pass with no SSOT duplication.

### Phase 4 - Restore Pipeline and Import/Export Unification

- Outcome:
  - one import/export contract and one deterministic restore orchestrator.
  - legacy user artifacts remain importable via compatibility layer.
- Must deliver:
  - unified restore use-case and result summary contract.
  - removal/deprecation of profile-only bypass path.
  - conflict-policy tests (`replace`, `import as new`, `keep active`).
- Gate:
  - full round-trip restore and compatibility tests pass.

### Phase 5 - Runtime Profile-Scoping Migration

- Outcome:
  - targeted global-key domains are profile-scoped with migration fallback.
  - profile delete performs downstream profile-key cleanup for supported stores.
- Must deliver:
  - migration wiring for scoped repositories.
  - profile switch isolation tests.
  - ACTIVE badge and UI ownership corrections where needed.
- Gate:
  - cross-profile bleed tests and delete-cascade tests pass.
  - if map/widget runtime behavior changes, required MapScreen SLO evidence exists.

### Phase 6 - UX, Ops, and Documentation Hardening

- Outcome:
  - startup/profile error states include deterministic recovery actions.
  - save/delete/import/export outcomes are visible and diagnosable.
  - docs and troubleshooting are production-ready.
- Must deliver:
  - mutation-result-aware UI flows (no optimistic silent success).
  - structured operational diagnostics for profile bundle operations.
  - updated profile documentation and user recovery guidance.
- Gate:
  - instrumentation and failure-mode UX tests pass.
  - no dead-end startup storage error screen.

## 6) Required Checks (Every Phase)

Minimum:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Release/CI verification when phase introduces release-path risk:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 7) Evidence Contract (Per Phase)

Required evidence:

- changed file list (absolute repo paths),
- tests added/updated,
- command list with pass/fail,
- unresolved risks and mitigation,
- explicit statement of whether `KNOWN_DEVIATIONS.md` changed.

Recommended artifact location:

- `docs/PROFILES/EXECUTION_LOG_PROFILE_PHASES_0_6.md`

## 8) Failure Handling and Rollback

- On test/check failure: fix and rerun in the same phase.
- On regression with unclear root cause:
  - revert only the current phase delta,
  - keep independent passing tests/guards when safe,
  - re-implement with narrower change slices.
- Never use destructive cleanup commands to force green status.

## 9) Completion Contract

Implementation is complete only when all are true:

1. Phase `0..6` gates are satisfied.
2. Required checks pass at final head.
3. Acceptance gates in
   `docs/PROFILES/CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07.md`
   are satisfied.
4. Quality rescore is produced per `docs/ARCHITECTURE/AGENT.md` with
   evidence and remaining risks.
5. Final summary includes:
   - what changed,
   - why it is architecture-correct,
   - known residual risk (if any),
   - rollback steps.
