# AGENT CONTRACT - PROFILE PHASES 0 TO 6 (COLLABORATIVE)

## Purpose

Define a production-grade collaborative execution contract so an agent can implement
profile Phases `0 -> 6` with explicit user help at gated checkpoints.

Primary plan anchor:
- `docs/PROFILES/CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07.md`

## 0) Collaboration Model

- Execution mode: agent-led implementation with user checkpoint approvals.
- Agent responsibilities:
  - prepare phase deltas, code, tests, and evidence,
  - run checks and publish pass/fail status,
  - propose options where policy choices are needed.
- User responsibilities:
  - approve phase scope decisions at checkpoints,
  - select rollout policy where multiple valid production options exist,
  - approve promotion from one phase to the next.
- Promotion rule:
  - no phase promotion without explicit user approval recorded in execution log.

## 1) Mandatory Inputs (Read Before Code Changes)

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

## 2) Non-Negotiable Rules

- Preserve `UI -> domain/use-case -> data`.
- Keep settings/profile state SSOT in repositories.
- No hidden mutable global state.
- No direct wall/system time in domain/fusion logic.
- Keep import/export deterministic.
- Record any unavoidable rule exception in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue, owner, expiry.

## 3) User Checkpoint Matrix

### Phase 0 Checkpoint (Contract Freeze)

User confirms:
- Tier A vs Tier B include/exclude matrix.
- target quality bar (`>=95`) per phase.
- release evidence expectations.

### Phase 1 Checkpoint (Identity + Startup)

User confirms:
- canonical default policy (`default-profile` only),
- startup null-active recovery behavior UX copy and action flow.

### Phase 2 Checkpoint (Storage + Backup Safety)

User confirms:
- managed backup ownership/cleanup policy,
- parse-failure degraded mode behavior.

### Phase 3 Checkpoint (Snapshot Coverage)

User confirms:
- final Tier A settings coverage list,
- explicit scope for profile metadata vs runtime SSOT.

### Phase 4 Checkpoint (Restore + Import/Export)

User confirms:
- import conflict default policy (`replace` vs `import-as-new`),
- one-file bundle-first UX and compatibility behavior.

### Phase 5 Checkpoint (Runtime Profile Scope)

User confirms:
- repository migration list for profile-scoped keys,
- delete-cascade ownership for supported stores.

### Phase 6 Checkpoint (UX/Ops + Release)

User confirms:
- operational diagnostics/reporting expectations,
- release signoff evidence package completeness.

## 4) Phase Contract (0 To 6)

### Phase 0 - Baseline and Contract Freeze
- Outcome:
  - current-state gap map and explicit acceptance gates.
- Agent deliverables:
  - baseline findings + risk map + initial scoring.
- User help required:
  - approve scope and acceptance gates.
- Gate:
  - phase scope and quality target approved in log.

### Phase 1 - Canonical Identity and Startup Hardening
- Outcome:
  - canonical profile identity enforced in runtime paths.
- Agent deliverables:
  - identity migration changes + startup invariant tests.
- User help required:
  - approve startup recovery UX behavior.
- Gate:
  - tests pass, user approves startup behavior.

### Phase 2 - Bundle Schema and Storage Engine
- Outcome:
  - deterministic/atomic backup pipeline with safe ownership cleanup.
- Agent deliverables:
  - schema/storage hardening + failure-mode tests.
- User help required:
  - approve cleanup ownership policy.
- Gate:
  - storage safety tests pass, user approval logged.

### Phase 3 - Snapshot Adapter Export Coverage
- Outcome:
  - Tier A settings exported/restored via section contract.
- Agent deliverables:
  - section coverage implementation + tests.
- User help required:
  - approve final Tier A matrix.
- Gate:
  - section coverage tests pass, user approval logged.

### Phase 4 - Restore Pipeline and Import/Export Unification
- Outcome:
  - one deterministic import/export contract with compatibility.
- Agent deliverables:
  - unified restore orchestration + round-trip tests.
- User help required:
  - approve conflict default policy.
- Gate:
  - round-trip tests pass, user approval logged.

### Phase 5 - Runtime Profile-Scoping Migration
- Outcome:
  - targeted settings are profile-scoped in runtime and delete cascade.
- Agent deliverables:
  - migration implementation + isolation tests.
- User help required:
  - approve migration/deletion ownership list.
- Gate:
  - cross-profile isolation tests pass, user approval logged.

### Phase 6 - UX, Ops, and Documentation Hardening
- Outcome:
  - production-grade recovery UX and diagnostics evidence.
- Agent deliverables:
  - mutation feedback UX + diagnostics + release checklist evidence.
- User help required:
  - approve release evidence package for promotion.
- Gate:
  - release checklist complete and user signoff logged.

## 5) Verification Policy

Required for non-trivial phase deltas:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

KSP lock fallback (local stability workaround):

```bash
./gradlew --no-daemon --no-configuration-cache "-Pksp.incremental=false" <tasks>
```

## 6) Evidence Contract (Per Phase)

Must record in execution log:
- files changed,
- tests added/updated,
- commands run and pass/fail,
- unresolved risks,
- user decision and approval record,
- phase score and rationale.

Recommended log target:
- `docs/PROFILES/EXECUTION_LOG_PROFILE_PHASES_0_6_COLLAB_2026-03-08.md`

## 7) Start Protocol

On kickoff:
1. create execution log,
2. record baseline state and current phase scores,
3. mark Phase 0 status as `in_progress`,
4. publish Phase 0 checkpoint questions for user approval,
5. proceed after user answers are logged.

## 8) Completion Contract

Execution is complete only when:
1. all phases `0..6` are either completed or explicitly deferred with approved rationale,
2. required checks are run and status is documented,
3. final scorecard is updated,
4. user signs off final release evidence status.
