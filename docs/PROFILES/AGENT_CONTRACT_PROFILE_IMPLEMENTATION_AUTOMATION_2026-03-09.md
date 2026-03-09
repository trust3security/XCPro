# AGENT CONTRACT - PROFILE IMPLEMENTATION AUTOMATION (PHASES 0-6)

## Purpose

Define an autonomous production-grade contract for implementing the current
profile hardening plan end-to-end in XCPro.

This contract targets the remaining gaps identified in the deep code pass:

1. Remove split profile truth (metadata profile model vs runtime settings SSOT).
2. Ensure backup bundle freshness for settings-only changes.
3. Complete missing profile behavior contracts (`copyFromProfile`, update validation, delete UX).
4. Harden import/restore behavior and schema compatibility.
5. Refactor oversized profile files for change safety.

## 0) Execution Authority

- Mode: autonomous.
- User prompts: not required for normal progress.
- Decision rule: if ambiguous, choose architecture-consistent behavior and document rationale.
- Forbidden shortcuts:
  - skipping phase gates,
  - moving business logic into UI,
  - bypassing repository/use-case boundaries,
  - silent partial restore without explicit reporting,
  - destructive git operations.

## 1) Mandatory Read Order

Read before edits:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/PROFILES/PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`
10. `docs/PROFILES/PROFILE_STARTUP_AND_DEFAULT_POLICY.md`
11. `docs/PROFILES/PROFILE_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md`

## 2) Pre-Approved Invariants

- Canonical default profile id remains `default-profile`.
- Legacy aliases (`default`, `__default_profile__`) are migration inputs only.
- Runtime profile settings SSOT remains in dedicated settings repositories.
- Bundle files remain portability artifacts, not runtime SSOT.
- Public managed backup location remains `Download/XCPro/profiles/`.

## 3) Non-Negotiable Architecture Rules

- Preserve `UI -> domain/use-case -> data`.
- Keep profile list and active profile id authoritative in profile storage/repository.
- Keep runtime settings authoritative in their owning repositories.
- ViewModels depend on use-cases only.
- No hidden global mutable state.
- No replay determinism regressions.
- Any unavoidable rule break must be entered in
  `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` with issue, owner, expiry.

## 4) Phase Plan and Exit Gates

Strict order: `0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6`.

Global phase gate (mandatory for every phase):

- Run a basic build and pass before phase close:
  - `./gradlew assembleDebug`
- Do not start the next phase until the current phase basic build is green.

### Phase 0 - Contract Freeze and SSOT Matrix

Implement:

- Document authoritative ownership matrix for all profile-related settings.
- Explicitly classify `UserProfile` fields as identity metadata vs runtime settings.

Exit gate:

- Docs updated with explicit SSOT ownership table.
- No unresolved ownership ambiguity for any Tier-A settings section.

### Phase 1 - Remove Split Profile Truth

Implement:

- Stop editing/persisting misleading non-authoritative settings in profile metadata paths.
- Make profile settings UI read/write real settings repositories (or hide non-authoritative controls).

Exit gate:

- No runtime-affecting setting is editable only through `UserProfile.preferences/polar`.
- Tests cover profile switch reflecting real repository-backed settings.

### Phase 2 - CRUD Contract Completion

Implement:

- Implement `copyFromProfile` behavior during profile creation.
- Add update validation parity with create validation.
- Prevent default-profile delete action in UI before repository rejection.
- Decide and enforce `lastUsed`/`isActive` semantics (update or remove dead fields).

Exit gate:

- `copyFromProfile` works and is tested.
- Update validation tests pass.
- Default delete UX test passes.

### Phase 3 - Backup Freshness and Sync Reliability

Implement:

- Trigger managed backup sync for settings-only changes (debounced/coalesced).
- Preserve latest-wins ordering and non-destructive managed cleanup.

Exit gate:

- Settings-only change updates `*_bundle_latest.json` and `*_profile_settings.json`.
- Existing backup ordering tests still pass.

### Phase 4 - Import/Restore Hardening

Implement:

- Import scope policy: `profiles only`, `profiles + profile-scoped settings`, `full bundle`.
- Optional strict restore mode for fail-fast behavior.
- Keep compatibility for legacy and managed backup artifacts.

Exit gate:

- Scope option tests pass.
- Strict restore behavior tests pass.
- Legacy compatibility tests remain green.

### Phase 5 - Schema and Compatibility Gates

Implement:

- Enforce version compatibility checks for bundle/settings schemas.
- Add migration path for supported old versions and deterministic rejection for unsupported versions.

Exit gate:

- Version compatibility tests pass.
- Unsupported-version import surfaces actionable error.

### Phase 6 - Maintainability and Release Hardening

Implement:

- Split oversized files into focused collaborators:
  - `feature/profile/.../ProfileRepository.kt`
  - `app/.../AppProfileSettingsRestoreApplier.kt`
- Update docs and troubleshooting for final behavior.

Exit gate:

- File-size policy met or explicitly time-boxed deviation recorded.
- End-to-end profile startup/import/export/switch/delete tests pass.

## 5) Required Verification

Minimum per phase (mandatory):

```bash
./gradlew assembleDebug
```

Minimum per non-trivial phase (in addition to basic build):

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

Release/CI path:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Evidence Requirements (Per Phase)

Record in execution log:

- Files changed.
- SSOT ownership statement for changed behavior.
- Tests added/updated.
- Commands run and pass/fail result.
- Risks and mitigations.
- Whether `KNOWN_DEVIATIONS.md` changed.

Recommended log file:

- `docs/PROFILES/EXECUTION_LOG_PROFILE_PHASES_0_6.md`

## 7) Failure and Rollback Protocol

- If gate fails: fix within the same phase and rerun.
- If regression root cause is unclear:
  - revert current phase delta only,
  - keep independent green guard tests where safe,
  - re-implement with smaller slices.
- Never use destructive reset commands.

## 8) Completion Criteria

All must be true:

1. Phases `0..6` complete in order.
2. Required checks pass at final head.
3. No unresolved split-SSOT behavior remains.
4. Backup freshness includes settings-only mutations.
5. Import/restore contract is deterministic and compatibility-tested.
6. Final quality rescore documented with evidence and residual risk.
