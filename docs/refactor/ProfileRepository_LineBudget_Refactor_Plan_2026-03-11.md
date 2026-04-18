# ProfileRepository line-budget refactor plan (target < 380 lines)

## 0) Metadata

- Title: ProfileRepository line-budget refactor plan (target < 380 lines)
- Owner: XCPro Team
- Date: 2026-03-11
- Issue/PR: TBD
- Status: Draft

## 1) Scope

- Problem statement:
  - `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt` is currently 889 lines and concentrates bootstrap, hydration, import, bundle, persistence, and backup-sync orchestration in one file.
- Why now:
  - Reduce review risk and regression surface.
  - Bring hotspot maintainability in line with production-grade refactor policy.
- In scope:
  - Split `ProfileRepository.kt` into focused collaborators while preserving behavior and public API.
  - Keep repository as SSOT owner and orchestration facade.
  - Add/adjust tests only where needed to lock existing behavior.
- Out of scope:
  - No behavior/product changes.
  - No data format/schema migration.
  - No module boundary changes.
  - No pipeline rewiring outside profile flow.
  - No Gradle config changes.
- User-visible impact:
  - None expected.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Profiles list | `ProfileRepository` (`_profiles`) | `StateFlow<List<UserProfile>>` | ViewModel/UI mirrors of authoritative profile list |
| Active profile | `ProfileRepository` (`_activeProfile`) | `StateFlow<UserProfile?>` | Secondary active-profile owners in use case/UI |
| Bootstrap completion/error | `ProfileRepository` | `StateFlow<Boolean>` / `StateFlow<String?>` | Duplicate bootstrap/error state outside repository |
| Persisted profile snapshot | `ProfileStorage` adapter (via repository) | read/write API | Alternate persistence path bypassing repository |
| Managed backup snapshot | `ProfileBackupSink` adapter (triggered by repository) | `syncSnapshot(...)` side effect | Independent backup writers outside repository flow |

### 2.2 Dependency Direction

Confirmed flow remains:

`UI -> ProfileViewModel -> ProfileUseCase -> ProfileRepository -> ProfileStorage/ProfileBackupSink/ProfileSettings* adapters`

- Modules/files touched:
  - `feature/profile/src/main/java/com/trust3/xcpro/profiles/*` (new helper files + repository trim)
  - `app/src/test/java/com/trust3/xcpro/profiles/*` (test lock/updates)
- Boundary risk:
  - Helper extraction must not move business logic into UI/ViewModel.
  - Repository remains the only mutation entrypoint.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Hydration parse/sanitize/default resolution | `ProfileRepository` | `ProfileHydrationPolicy` + `ProfileBootstrapProvisioner` | Reduce monolith complexity; keep pure policy isolated | Existing bootstrap/read-failure tests |
| Profile import orchestration | `ProfileRepository` | `ProfileProfilesImportCoordinator` | Isolate collision/active-selection/import rules | Existing import tests |
| Bundle import/export orchestration | `ProfileRepository` | `ProfileBundleImportCoordinator` / `ProfileBundleExportCoordinator` | Isolate bundle path and restore-scope decisions | Existing bundle tests |
| Persistence + backup sync plumbing | `ProfileRepository` | `ProfileStateWriter` + `ProfileBackupSyncOrchestrator` | Make side effects explicit and testable | Existing backup/atomic-write tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Internal large-method direct logic in `ProfileRepository` | Monolithic in-class policy execution | Delegate to focused collaborators; keep repository entrypoint authority | 1-3 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Profile repository logic | N/A | This refactor introduces no time-based domain logic and no direct system-time calls. |

Explicitly forbidden comparisons remain unchanged:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Repository mutation remains serialized by existing `mutationMutex`.
  - Backup sync remains async via existing injected/internal scope behavior.
- Primary cadence/gating sensor:
  - N/A (profile data/persistence path).
- Hot-path latency budget:
  - Not real-time critical; prioritize determinism and atomicity.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No new randomness introduced
- Replay/live divergence rules: N/A for profile repository flow

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Behavior drift in bootstrap fallback | ARCHITECTURE SSOT + determinism | Unit test | `ProfileRepositoryTest` bootstrap/read-failure cases |
| Import semantics drift | CODING_RULES repository/use-case boundaries | Unit test | `ProfileRepositoryTest` import cases |
| Bundle restore semantics drift | CODING_RULES SSOT/state ownership | Unit test | `ProfileRepositoryBundleTest` |
| Backup sync regression | CODING_RULES repository authority | Unit test | `ProfileRepositoryBackupSyncTest` |
| Deletion cascade regression | CODING_RULES repository side-effect ownership | Unit test | `ProfileRepositoryDeleteCascadeTest` |
| File-size regression | CODING_RULES line budget policy | `enforceRules` static gate and review | `scripts/ci/enforce_rules.ps1` (if touched), PR review checklist |

### 2.7 Visual UX SLO Contract

Not applicable. This change does not touch map/overlay/replay interaction runtime behavior.

## 3) Data Flow (Before -> After)

Before:

`ProfileViewModel -> ProfileUseCase -> ProfileRepository (monolith) -> ProfileStorage/ProfileBackupSink/ProfileSettings*`

After:

`ProfileViewModel -> ProfileUseCase -> ProfileRepository (facade/orchestrator) -> [HydrationPolicy + ImportCoordinators + StateWriter + BackupSyncOrchestrator] -> ProfileStorage/ProfileBackupSink/ProfileSettings*`

SSOT ownership remains in repository state flows; helpers are collaborators, not state owners.

## 4) Implementation Phases

### Phase 0 - Baseline lock

- Goal:
  - Freeze current behavior with existing tests; add characterization tests only if gaps are found.
- Files to change:
  - `app/src/test/java/com/trust3/xcpro/profiles/ProfileRepositoryTest.kt` (only if gap found)
  - Existing profile repository test files (only if gap found)
- Tests to add/update:
  - Bootstrap parse-failure and fallback edge coverage if missing.
  - Import strict/lenient restore edge coverage if missing.
- Exit criteria:
  - Baseline test behavior locked and green.

### Phase 1 - Extract hydration/bootstrap policy

- Goal:
  - Remove parse/sanitize/default provisioning and active-profile resolution policy from `ProfileRepository.kt`.
- Files to change:
  - `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt`
  - New helper files (`ProfileHydrationPolicy.kt`, `ProfileBootstrapProvisioner.kt`)
- Tests to add/update:
  - Reuse existing bootstrap/read-failure tests; add only if behavior seam requires.
- Exit criteria:
  - Hydration behavior unchanged; repository line count reduced.

### Phase 2 - Extract import and bundle workflows

- Goal:
  - Remove large import and bundle workflows from repository class.
- Files to change:
  - `ProfileRepository.kt`
  - New helper files (`ProfileProfilesImportCoordinator.kt`, `ProfileBundleImportCoordinator.kt`, optional `ProfileBundleExportCoordinator.kt`)
  - Reuse existing `ProfileRepositoryImportHelpers.kt`
- Tests to add/update:
  - Existing import and bundle tests remain authoritative.
- Exit criteria:
  - Import/bundle behavior unchanged; repository line count reduced further.

### Phase 3 - Extract persistence and backup side-effect orchestration

- Goal:
  - Isolate write and backup sync concerns while preserving atomic mutation semantics.
- Files to change:
  - `ProfileRepository.kt`
  - New helper files (`ProfileStateWriter.kt`, `ProfileBackupSyncOrchestrator.kt`)
- Tests to add/update:
  - Existing backup sync and atomic-write tests.
- Exit criteria:
  - Persist/backup behavior unchanged; repository primarily orchestration facade.

### Phase 4 - Compliance closeout

- Goal:
  - Final trim to `< 380` lines and close documentation and gate evidence.
- Files to change:
  - `ProfileRepository.kt`
  - Optional: `scripts/ci/enforce_rules.ps1` for explicit per-file cap (no Gradle config changes)
  - Optional: `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` only if temporary exception is explicitly approved
- Tests to add/update:
  - None beyond regression lock unless needed.
- Exit criteria:
  - `ProfileRepository.kt` `< 380` lines.
  - Required checks pass.
  - No Gradle config changes.

## 5) Test Plan

- Unit tests:
  - Keep profile repository test suites as primary contract lock.
- Replay/regression tests:
  - N/A (profile flow).
- UI/instrumentation tests (if needed):
  - Not required for this refactor.
- Degraded/failure-mode tests:
  - Parse/read failure, invalid import entries, strict restore failure cases.
- Boundary tests for removed bypasses:
  - Repository still sole mutation entrypoint; validate through existing API-level tests.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Constraint:
- No Gradle config changes in this workstream.

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Hidden behavior drift during extraction | High | Phase 0 characterization lock + existing broad profile tests | XCPro Team |
| SSOT leakage into helpers | Medium | Keep state mutation authority in repository only | XCPro Team |
| Line target miss (`< 380`) | Medium | Explicit phase budget and final closeout pass | XCPro Team |
| Over-abstraction | Medium | Prefer focused package-private helpers over new layers/modules | XCPro Team |
| Build slowdown concerns | Low | No new modules/dependencies/Gradle config edits | XCPro Team |

## 7) Acceptance Gates

- No violations of `ARCHITECTURE.md` or `CODING_RULES.md`.
- No duplicate SSOT ownership.
- Repository remains mutation authority.
- Public behavior unchanged for profile bootstrap/import/export/update/delete flows.
- `ProfileRepository.kt` is `< 380` lines.
- No Gradle configuration changes.
- Required checks pass:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`

## 8) Rollback Plan

- What can be reverted independently:
  - Each phase can be reverted by removing helper delegation introduced in that phase.
- Recovery steps if regression is detected:
  - Revert latest phase only.
  - Re-run profile repository test suites.
  - Reapply extraction with narrower seam.

## 9) Plan Quality Score

- Score: 94/100
- Evidence basis:
  - Phase-gated structure aligned with repository architecture contracts.
  - Existing strong test coverage across bootstrap/import/bundle/backup/delete paths.
  - Explicit SSOT and dependency-direction preservation.
  - Explicit "no Gradle config changes" constraint.
- Remaining risk:
  - Final line-count landing may require one extra split boundary adjustment in Phase 4.
