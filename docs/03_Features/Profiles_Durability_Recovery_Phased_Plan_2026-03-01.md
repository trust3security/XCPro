# Profiles Durability And Recovery Phased Plan (2026-03-01)

Status: In progress
Owner: XCPro Team

## 1) Problem Statement

Profile data is sandbox-scoped by Android package identity. When users switch app package/variant
(`release` vs `debug` or changed `applicationId`), stored profiles appear missing.

Current gaps that reduce recovery confidence:

1. `Export All` path could produce empty backups.
2. Profile settings import action did not apply imported profiles.
3. Startup observability for "why profile screen is shown" is limited.
4. No dedicated test coverage for backup/import durability paths.

## 2) Architecture Contract

SSOT owners remain unchanged:

1. `ProfileRepository` owns profile list + active profile.
2. UI remains intent/render only.
3. Persistence remains in `ProfileStorage` (`profile_preferences` DataStore).

No dependency direction change:

`UI -> domain/use-case -> data`

## 3) Time Base Declaration

1. Profile timestamps (`createdAt`, `lastUsed`) use wall time only.
2. No monotonic/replay timing logic is introduced in this plan.

## 4) Phases

### Phase 0 - Correctness Hotfixes (Done)

Goal: close immediate durability breakages.

Changes:

1. Ensure `Export All` uses actual profile list and fails fast on empty export requests.
2. Wire profile settings import to actual profile creation.
3. Add startup log line when profile selection is required (package + hydration context).
4. Add unit tests for export/import durability path.

Exit criteria:

1. Export all includes all current profiles.
2. Import from settings creates profiles.
3. Test coverage exists for empty export and round-trip import.

### Phase 1 - Deterministic Bulk Import

Goal: remove per-profile fire-and-forget import races.

Implementation status (2026-03-01):

1. Implemented:
   - repository batch import API (`importProfiles`) with serialized mutation path
   - use-case + ViewModel import intent wiring
   - all three UI import entrypoints switched from per-item loops to one intent
   - import dialog policy control for `keepCurrentActive`
   - richer import feedback with skipped/failure preview text
   - import/export fidelity improvement for preserved preferences + metadata
   - unit tests for active-profile preservation/switching, deterministic name-collision suffixing,
     single-write behavior, and invalid-entry handling
   - strict no-op behavior for all-invalid imports (no persistence write, no active-profile mutation)
   - locale-stable collision normalization (`lowercase(Locale.ROOT)`) in import merge path
   - dead duplicate-ID failure contract removed (`DUPLICATE_ID` variant deleted)
   - centralized import-feedback formatter shared by all three import entrypoints
   - additional tests for:
     - all-invalid no-op write/active-state invariance
     - `preserveImportedPreferences = false` defaulting behavior
     - duplicate-ID regeneration behavior
     - formatter consistency

2. Remaining to harden:
   - optional UI policy control for name-collision policy (currently default only)
   - instrumentation coverage for import completion timing and messaging paths

Phase 1 post-implementation repass findings (2026-03-01, latest pass):

1. Closed: all-invalid import no longer persists or mutates active selection.
   Repository now returns structured skipped/failure result and exits before any write/commit.

2. Closed: duplicate-ID contract was reconciled by removing unreachable `DUPLICATE_ID`
   reason and keeping deterministic ID regeneration behavior.

3. Closed: import feedback formatting is centralized in shared formatter:
   - `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileImportFeedbackFormatter.kt`
   Consumers:
   - `ProfileSelectionScreen`
   - `ProfileSettingsScreen`
   - drawer `ProfilesScreen`

4. Closed: name-collision normalization now uses locale-stable `Locale.ROOT` mapping.

5. Remaining coverage gap:
   - instrumentation coverage for import completion timing and UI messaging paths.
   - optional UI controls for name-collision policy remain deferred.

Historical pre-implementation findings (already addressed in Phase 1 implementation):

1. Import is still executed as per-item UI loops (`viewModel.createProfile(...)` called repeatedly),
   so completion is nondeterministic and success feedback can be shown before persistence finishes.
2. `ProfileViewModel.createProfile(...)` auto-selects each created profile, so bulk import can
   unintentionally churn active profile and end with an order-dependent active selection.
3. Import fidelity is currently incomplete:
   - parsed `UserProfile.preferences` is dropped by UI import paths that map into
     `ProfileCreationRequest(name/type/model/description)` only.
   - imported metadata fields (`createdAt`, `lastUsed`) are also discarded in create flow.
4. `ProfileCreationRequest.copyFromProfile` is modeled but unused in repository create path,
   so there is no authoritative copy/import contract for profile settings.
5. Import naming policy is inconsistent by entrypoint (`"(Imported)"` suffix in one screen,
   original name in other screens).
6. Import logic is duplicated across three entrypoints:
   - `ProfileSelectionScreen`
   - `ProfileSettingsScreen`
   - drawer `ProfilesScreen`
   This duplication increases behavior drift risk.
7. No structured import result contract exists (per-item failure reasons are not surfaced).
8. No dedicated tests assert active-profile preservation, settings fidelity, or "no early success"
   behavior across bulk import.

Planned changes (updated after repass):

1. Add a dedicated `importProfiles(...)` use-case/repository path with serialized batch behavior.
2. Add explicit import request/options contract:
   - `keepCurrentActive: Boolean = true`
   - `nameCollisionPolicy: KEEP_BOTH_SUFFIX` (initial default)
   - `preserveImportedPreferences: Boolean = true` (default)
3. Return structured import report:
   - `requestedCount`
   - `importedCount`
   - `skippedCount`
   - `failures: List<ImportFailure>`
   - `activeProfileBefore`
   - `activeProfileAfter`
4. Move all batch import mutation sequencing into repository mutex path:
   - one deterministic merge pass
   - one persistence commit for final merged state
5. Keep prior active profile unless user explicitly selects "activate imported profile".
6. Replace all per-item import loops in UI screens with one ViewModel import intent.
7. Harmonize naming behavior across all import entrypoints and remove screen-specific suffix drift.
8. Preserve imported profile preferences/settings in repository import mapping (no lossy create mapping).
9. Avoid write amplification from create+select chaining during import.

Phase 1 implementation slices:

1. Domain/data contract slice
   - Add `ProfileImportRequest`, `ProfileImportResult`, and failure reason model.
   - Add `ProfileImportPolicy` (active-profile and name-collision settings).
   - Add repository `importProfiles(request)` with deterministic merge + validation.

2. Repository import execution slice
   - Validate/normalize all imported items before mutation.
   - Preserve imported `preferences` by default.
   - Resolve collisions once using policy and return per-item outcomes.
   - Persist merged state once and commit repository state once.

3. ViewModel/UI intent slice
   - Add `ProfileViewModel.importProfiles(...)`.
   - Use one loading lifecycle for whole import.
   - Replace import loops in all three UI entrypoints with one intent call.
   - Emit completion summary only after repository result is returned.

4. Active-profile safety slice
   - Capture pre-import active profile ID.
   - Reconcile post-import active profile according to import policy.
   - Ensure no implicit active-profile churn during import.

5. Test slice
   - Unit: preserves active profile when `keepCurrentActive=true`.
   - Unit: `keepCurrentActive=false` activates expected imported profile by deterministic policy.
   - Unit: imported preferences are retained after import.
   - Unit: all-invalid import is no-op (no persistence write and no active-state mutation).
   - Unit: deterministic naming collision policy.
   - Unit: partial-failure report correctness and reachable failure-reason contract.
   - Unit: `preserveImportedPreferences=false` applies default preferences/timestamps policy.
   - Unit: single persistence commit for batch import path.
   - UI/integration: no success toast/message before import completion.
   - UI/integration: import feedback text parity across all three import entrypoints.

Exit criteria:

1. One-shot import operation with deterministic completion signal exists in repository/use-case.
2. UI triggers exactly one import intent per import action.
3. Active profile is stable by policy after import.
4. Completion summary includes counts + failure reasons.
5. Imported profile preferences are preserved (no lossy mapping).
6. Import path does not emit premature success feedback.
7. All-invalid import input is a strict no-op for storage and active selection.
8. Import failure contract has no dead/unreachable reason variants.
9. New tests lock determinism, settings fidelity, and active-profile preservation behavior.

### Phase 2 - Recovery Assistant UX

Goal: make profile recovery self-explanatory when sandbox mismatch happens.

Planned changes:

1. Add "Why profiles can be missing" help sheet in empty profile state.
2. Add guided CTA sequence: `Import backup` -> `Profile restored` -> `Continue`.
3. Surface current package/variant and explain scope.

Exit criteria:

1. Users can recover without guessing debug/release/package behavior.
2. Empty-state abandonment rate reduced in QA/manual checks.

### Phase 3 - Release Observability

Goal: improve confidence and triage speed.

Planned changes:

1. Structured events/log tags for:
   - `profile_bootstrap_empty`
   - `profile_import_started`
   - `profile_import_completed`
   - `profile_import_failed`
2. Capture package name, build type, profile counts, and bootstrap error class.

Exit criteria:

1. Field reports can distinguish true data loss vs sandbox switch in minutes.
2. Logs include enough context to reproduce import failures.

### Phase 4 - E2E And Instrumentation Coverage

Goal: release-grade confidence for recovery and first-run transitions.

Planned tests:

1. Compose/instrumentation test: empty-state shows import CTA.
2. Integration test: import JSON -> profiles created -> active profile remains stable.
3. Regression test: export-all contains all profiles.

Exit criteria:

1. Recovery flows covered in CI instrumentation suite.
2. No untested critical path in profile restore UX.

## 5) Risks And Mitigations

1. Risk: import duplicates profile names.
   Mitigation: add optional name de-duplication policy in Phase 1.

2. Risk: users confuse backup/import with cloud sync.
   Mitigation: explicit "local backup file" copy in Phase 2 UX.

3. Risk: logging could become noisy.
   Mitigation: use bounded info-level events only on transition points.

4. Risk: large import payload causes long main-thread UI lock perception.
   Mitigation: run import merge/persist on IO and keep UI progress state explicit.

5. Risk: report model drift across screens.
   Mitigation: single shared `ProfileImportResult` contract consumed by all entrypoints.

## 6) Required Verification Per Phase

1. `python scripts/arch_gate.py`
2. `./gradlew enforceRules`
3. `./gradlew testDebugUnitTest`
4. `./gradlew assembleDebug`

Latest verification evidence (2026-03-01):

1. `python scripts/arch_gate.py` -> PASS
2. `./gradlew enforceRules` -> PASS
3. `./gradlew testDebugUnitTest` -> PASS
4. `./gradlew assembleDebug` -> PASS
