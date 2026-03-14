# Profile Identity/Time Ownership Standardization Phased IP

## 0) Metadata

- Title: Make profile identity and wall-time ownership explicit without broad churn
- Owner: XCPro Team
- Date: 2026-03-14
- Issue/PR: TBD
- Status: Complete
- Execution rules:
  - This is an ownership-and-determinism track, not a style-cleanup track.
  - Reuse the existing `core/time` `Clock` seam; do not introduce new direct `TimeBridge` usage while executing this plan.
  - Keep the ID seam slice-local to profiles unless a broader repo need is proven; do not create a speculative repo-wide UUID framework.
  - Land one owner boundary at a time with behavior parity and rollback.
  - Do not mix this plan with logging cleanup, canonical atmospheric math consolidation, or unrelated profile settings refactors.
- Progress:
  - Phase 0 complete: seam inventory recorded and target ownership contract defined.
  - Phase 1 complete: profile repository wiring now owns an injected `Clock` plus a slice-local `ProfileIdGenerator`, and the creation/bootstrap/import owners consume those seams without removing model defaults yet.
  - Phase 2 complete: `UserProfile` no longer generates `id`, `createdAt`, or `lastUsed`, and the remaining profile create/import/bootstrap fixtures now pass deterministic metadata explicitly.
  - Phase 3 complete: export, bundle, and backup owners now stamp explicit wall time once and carry it through JSON metadata, suggested filenames, and managed bundle payloads.
  - Phase 4 complete: no scoped profile/export model or helper still hides ID/time defaults, and the temporary deviation is closed.

## 1) Scope

- Problem statement:
  - The profile/export slice still hides ID and wall-time creation inside persistent models and helper defaults instead of showing those decisions at owner call sites.
  - The most concrete current examples are:
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileModels.kt`
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBundleCodec.kt`
    - `feature/profile/src/main/java/com/example/xcpro/profiles/AircraftProfileFileNames.kt`
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryMutationCoordinator.kt`
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryImportCoordinator.kt`
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryImportHelpers.kt`
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryBundleCoordinator.kt`
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBackupSink.kt`
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
  - The result is weak ownership visibility:
    - reviewers cannot see who owns ID/timestamp policy from creation/import/export call sites
    - tests rely on hidden randomness/time behavior unless they override every path deliberately
    - export JSON metadata and suggested export filename timing are decided in different places
- Why now:
  - Runtime ownership cleanup is complete; this is the next lowest-churn architecture hardening move.
  - The profile slice is self-contained and already has strong test coverage for create/import/export behavior.
  - The repo already provides a `Clock` abstraction, so this change can reuse an existing seam instead of inventing new infrastructure for time.
- In scope:
  - Profile creation metadata (`id`, `createdAt`, `lastUsed`)
  - Import-time ID regeneration and metadata reset policy
  - Bundle/export timestamps such as `exportedAtWallMs`
  - Suggested export filename timestamp ownership
  - Backup/bundle metadata ownership where timestamps become part of persisted/exported payloads
- Out of scope:
  - Repo-wide UUID abstraction beyond the profile slice
  - Generic wall-time cleanup in unrelated UI or Android I/O code
  - Logging, runtime ownership, or formula-consolidation work
  - User-facing behavior changes beyond deterministic metadata ownership
- User-visible impact:
  - None intended
  - Export timestamps and suggested file names may become more consistently aligned once one owner stamps both

## 2) Seam Pass Findings

### 2.1 Concrete Current Drift

| Seam | Current drift | Why it matters |
|---|---|---|
| `UserProfile` defaults | model constructor generates `id`, `createdAt`, and `lastUsed` | hides identity/time policy inside a data model |
| `ProfileRepositoryMutationCoordinator` | new profile creation relies on `UserProfile` defaults | reviewers cannot see profile creation metadata ownership |
| `ProfileRepositoryBootstrapHelpers` | default profile builder uses `TimeBridge` directly | default-profile time policy is not injected or owner-seamed |
| `ProfileRepositoryImportHelpers` + `ProfileRepositoryImportCoordinator` | import ID regeneration uses `UUID.randomUUID()` and import-reset metadata uses `TimeBridge` directly | import identity/time policy is split across helpers and hidden globals |
| `ProfileBundleDocument` defaults | bundle export timestamp is created inside the document type | exported payload metadata is not owner-supplied |
| `AircraftProfileFileNames` | filename helper silently pulls wall time by default | formatter owns time lookup instead of consuming explicit input |
| `ProfileRepositoryBundleCoordinator` + `ProfileExportImport` | bundle JSON and suggested filename time are chosen in different layers | export metadata can drift and ownership is unclear |
| `ProfileBackupSink` | backup payload creation already accepts explicit `generatedAtWallMs`, but bundle export inside the sink still relies on `ProfileBundleDocument` default timestamp | owner-seamed backup timestamping is partially explicit, partially hidden |

### 2.2 Existing Seams To Reuse

- `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
  - existing injected wall-time seam
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBackupSink.kt`
  - already uses explicit `generatedAtWallMs` for backup payload documents; that is the correct owner shape to extend to bundle export metadata

## 3) Target Identity/Time Ownership Standard

### 3.1 Ownership Contract

| Concern | Authoritative owner | Exposed as | Forbidden pattern |
|---|---|---|---|
| New profile ID | repository/coordinator/factory-level owner using a profile ID seam | explicit `String` passed into `UserProfile` | `UserProfile` constructor generating its own ID |
| New profile timestamps | repository/coordinator/factory-level owner using injected `Clock` | explicit `Long` values passed into `UserProfile` | `UserProfile` constructor generating wall time |
| Import replacement/regenerated ID | import owner using the same profile ID seam | explicit imported/copied `id` | raw `UUID.randomUUID()` inside helpers |
| Import metadata reset (`createdAt`, `lastUsed`) | import owner using injected `Clock` and explicit policy | explicit values in `copy(...)` | helper-local `TimeBridge.nowWallMs()` calls with no owner seam |
| Bundle export time | bundle/export owner | explicit `exportedAtWallMs` in `ProfileBundleDocument` | document default generating export time |
| Suggested export filename timestamp | same export owner that decides export metadata, or an explicitly documented caller | explicit `nowWallMs` argument | filename formatter silently reading wall time |
| Backup generated time | backup sink as file/persistence owner using injected `Clock` | explicit payload arguments | mixed direct/default time creation inside payload models |

Required rules:
- Persistent and exported model types are data-only; they do not mint IDs or wall time.
- Formatting helpers may format time, but they do not fetch it silently.
- Owner code may decide policy; models and formatters may not.
- Use the existing `Clock` seam for wall time.
- Introduce one small profile-slice ID seam instead of repeated raw `UUID.randomUUID()`.

### 3.2 Time Base Declaration

| Value | Time base | Why |
|---|---|---|
| `UserProfile.createdAt` | Wall | user/profile metadata is real-world clock time |
| `UserProfile.lastUsed` | Wall | profile usage history is wall-clock metadata |
| `ProfileBundleDocument.exportedAtWallMs` | Wall | export metadata is user-facing real-world time |
| backup `generatedAtWallMs` | Wall | persisted backup metadata is real-world file snapshot time |
| suggested export filename date | Wall | filename is a human-facing export timestamp |

Explicitly out of scope for this plan:
- monotonic time
- replay time
- generic app-wide wall-time refactors unrelated to profile/export metadata

### 3.3 Reference Pattern

Reuse these patterns:
- Explicit owner-supplied timestamps already used by `ProfileBackupDocument` and `ProfileSettingsBackupDocument`
- Existing injected `Clock` interface in `core/time`

Intentional deviation from current code:
- `ProfileBundleDocument`, `UserProfile`, and `AircraftProfileFileNames` will move away from convenience defaults because those defaults currently hide ownership decisions.

## 4) Architecture Contract

### 4.1 SSOT Ownership

| Data / responsibility | Owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| profile creation metadata | profile mutation/bootstrap owner | explicit constructor args | duplicate hidden defaults in `UserProfile` |
| import ID regeneration policy | import coordinator/helper seam | explicit generated ID | ad hoc `UUID.randomUUID()` across import helpers |
| import metadata reset policy | import coordinator | explicit copy values | scattered `TimeBridge.nowWallMs()` calls in import helpers |
| export timestamp | bundle/export owner | explicit field in bundle/export payload | UI and repository independently stamping different times |
| backup snapshot timestamp | backup sink owner | explicit payload args | hidden backup/bundle timestamp defaults |

### 4.2 Dependency Direction

Required flow:

`UI -> ViewModel/use-case -> repository/coordinator/factory -> model/formatter`

Boundary rules:
- UI may not become the hidden owner of profile identity policy.
- Data models may not own current-time or ID generation.
- Utility helpers may not become pseudo-repositories for time or identity.

### 4.3 Enforcement Coverage

| Risk | Guard type | File/test |
|---|---|---|
| hidden ID/time defaults remain in model types | targeted grep + tests + review | `ProfileModels.kt`, `ProfileBundleCodec.kt`, `AircraftProfileFileNames.kt` |
| create/import paths still hide identity policy | repository tests | `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTest.kt` |
| export timestamp and filename drift | bundle/export tests | `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBundleTest.kt`, `app/src/test/java/com/example/xcpro/profiles/ProfileExportImportTest.kt`, `app/src/test/java/com/example/xcpro/profiles/AircraftProfileFileNamesTest.kt` |
| backup payload metadata remains mixed implicit/explicit | backup contract tests | `feature/profile/src/test/java/com/example/xcpro/profiles/ProfileBackupSinkContractTest.kt` |

## 5) Implementation Phases

### Phase 0 - Contract lock and seam baseline

- Goal:
  - Freeze the identity/time ownership target and confirm the scoped owner seams before code changes begin.
- Files to change:
  - plan/deviation docs only
- Tests to add/update:
  - none
- Exit criteria:
  - target ownership contract is documented
  - scoped files and owner seams are listed
  - the work remains separate from unrelated profile settings churn

### Phase 1 - Introduce explicit identity/time owner seams

- Goal:
  - Add the smallest explicit seams needed so owner code can create IDs and wall-time metadata without relying on model/helper defaults.
- Files to change:
  - new slice-local profile ID seam, for example:
    - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileIdGenerator.kt`
  - profile repository wiring files needed to inject or pass:
    - existing `Clock`
    - the new ID seam
- Tests to add/update:
  - seam-level unit tests for deterministic ID/time injection where practical
  - repository harness setup changes required by later phases
- Exit criteria:
  - one explicit profile-slice ID seam exists
  - owner code can obtain wall time through injected `Clock`
  - no new direct `UUID.randomUUID()` or `TimeBridge.nowWallMs()` is introduced in scoped model/helper files
- Completion note:
  - Completed 2026-03-14 by adding `ProfileIdGenerator`, threading `Clock` and the ID seam through `ProfileRepository`, and routing creation/bootstrap/import owner paths through those seams while keeping model defaults for later removal.

### Phase 2 - Make creation, bootstrap, and import ownership explicit

- Goal:
  - Move profile identity/time decisions out of `UserProfile` defaults and into the mutation/bootstrap/import owners.
- Files to change:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileModels.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryMutationCoordinator.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryBootstrapHelpers.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryImportCoordinator.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryImportHelpers.kt`
  - any narrow repository wiring files required by constructor changes
- Tests to add/update:
  - `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryTest.kt`
  - targeted tests for:
    - new profile creation metadata
    - copied profile metadata rules
    - default-profile bootstrap metadata
    - deterministic import ID regeneration and metadata reset rules
- Exit criteria:
  - `UserProfile` creation/import call sites provide `id`, `createdAt`, and `lastUsed` explicitly
  - import ID regeneration is centralized behind the new ID seam
  - bootstrap/default profile creation no longer uses hidden model defaults
- Completion note:
  - Completed 2026-03-14 by removing the `UserProfile` constructor defaults for `id`, `createdAt`, and `lastUsed`, while keeping the profile repository owner paths explicit and migrating the affected profile tests/fixtures to deterministic metadata values.

### Phase 3 - Make export, bundle, and backup metadata ownership explicit

- Goal:
  - Move export and backup timestamp decisions to one explicit owner path and stop relying on document/helper defaults.
- Files to change:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBundleCodec.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepositoryBundleCoordinator.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBackupSink.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/AircraftProfileFileNames.kt`
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
  - narrow repository/use-case/ViewModel files if export result shape must carry explicit metadata
- Tests to add/update:
  - `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBundleTest.kt`
  - `app/src/test/java/com/example/xcpro/profiles/ProfileExportImportTest.kt`
  - `app/src/test/java/com/example/xcpro/profiles/AircraftProfileFileNamesTest.kt`
  - `feature/profile/src/test/java/com/example/xcpro/profiles/ProfileBackupSinkContractTest.kt`
- Exit criteria:
  - `ProfileBundleDocument` receives explicit `exportedAtWallMs`
  - `AircraftProfileFileNames` requires explicit timestamp input
  - bundle JSON and suggested filename timestamp are owned by one explicit path or a deliberately documented caller boundary
  - backup bundle metadata no longer depends on hidden timestamp defaults
- Completion note:
  - Completed 2026-03-14 by introducing `ProfileBundleExportArtifact`, moving export timestamp and suggested filename ownership into `ProfileRepositoryBundleCoordinator`, requiring explicit `exportedAtWallMs` in `ProfileBundleDocument`, removing implicit wall-time lookup from `AircraftProfileFileNames`, and reusing explicit owner-stamped timestamps inside `ProfileBackupSink`.

### Phase 4 - Remove hidden defaults and close the deviation

- Goal:
  - Finish the migration by deleting convenience defaults that hide identity/time policy and close the temporary deviation.
- Files to change:
  - all scoped production/test files still depending on removed defaults
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
- Tests to add/update:
  - regression updates from prior phases only
- Exit criteria:
  - no hidden ID/time defaults remain in:
    - `ProfileModels.kt`
    - `ProfileBundleCodec.kt`
    - `AircraftProfileFileNames.kt`
  - scoped tests pass with explicit owner-supplied metadata
  - the deviation entry for this track is removed
- Completion note:
  - Completed 2026-03-14 after the final seam sweep confirmed that the scoped profile/export files no longer mint IDs or wall time implicitly; the remaining `ProfileIdGenerator` UUID call is now the deliberate slice-local owner seam rather than hidden model/helper behavior.

## 6) Acceptance Criteria

- No persistent or exported profile model silently creates ID or wall-time metadata.
- Reviewers can identify profile identity/time ownership from creation/import/export call sites.
- Import ID regeneration and metadata reset behavior remains deterministic under test.
- Exported bundle metadata and suggested filename timestamp follow one explicit owner path.
- No new direct `UUID.randomUUID()` or `TimeBridge.nowWallMs()` usage is introduced in scoped model/helper files.

## 7) Verification Plan

- Required commands before closing the track:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- Targeted test slices to run during the phases:
  - `./gradlew app:testDebugUnitTest --tests "com.example.xcpro.profiles.ProfileRepositoryTest"`
  - `./gradlew app:testDebugUnitTest --tests "com.example.xcpro.profiles.ProfileRepositoryBundleTest"`
  - `./gradlew app:testDebugUnitTest --tests "com.example.xcpro.profiles.ProfileExportImportTest"`
  - `./gradlew :feature:profile:testDebugUnitTest --tests "com.example.xcpro.profiles.ProfileBackupSinkContractTest"`
