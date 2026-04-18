# Card_Preferences_Profile_Contributor_Extraction_Plan_2026-03-14.md

## Purpose

Finish the profile-settings boundary refactor for the last major section:
`CARD_PREFERENCES`.

This plan exists because card profile import/export is still implemented inside
`feature:profile`, while the runtime card state owner remains
`dfcards-library`. The current tests also do not lock card payload/restore
behavior deeply enough, and the shared profile contract placement is currently
blocked by a module edge from `core/common` to `dfcards-library`.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/AGENT.md`
6. `docs/refactor/Profile_Settings_Contributor_Boundary_Refactor_Plan_2026-03-13.md`

## 0) Metadata

- Title: Card Preferences Profile Contributor Extraction
- Owner: XCPro Team
- Date: 2026-03-14
- Issue/PR: TBD
- Status: In Progress
- Active seam lock:
  - `docs/refactor/Profile_Card_Boundary_Seam_Lock_2026-03-14.md`

## 0A) Current Status Snapshot

- Phase 2 complete:
  - `core/common -> :dfcards-library` dependency removed
  - shared orientation contract now depends on
    `OrientationFlightDataSnapshot`, not `RealTimeFlightData`
  - shared units types needed below the profile contributor contract now live
    in `core/common`
  - required verification passed:
    - `./gradlew enforceRules --no-configuration-cache`
    - `./gradlew testDebugUnitTest --no-configuration-cache`
    - `./gradlew assembleDebug --no-configuration-cache`
- Next planned phase:
  - Phase 4 guardrails only if new profile/card boundary drift appears
- Card contributor extraction complete:
  - `CARD_PREFERENCES` now captures/applies through an owner-owned contributor
    in `dfcards-library`
  - `AppProfileSettingsSnapshotProvider` and
    `AppProfileSettingsRestoreApplier` are back to orchestration-only roles
  - focused contributor tests now live with the card owner
- Phase C UI cleanup complete:
  - production UI no longer constructs `CardPreferences(context)`
  - `ProfileQuickActions.kt` now requires host-provided available modes
  - `CardLibraryModal.kt` now requires host-provided saved-template state and
    persistence callbacks

## 1) Scope

- Problem statement:
  - `CARD_PREFERENCES` is still implemented directly in:
    - `feature/profile/src/main/java/com/trust3/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
    - `feature/profile/src/main/java/com/trust3/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
  - The payload DTO still lives in:
    - `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
  - Current tests prove that the card section is present in bundle export/import,
    but they do not deeply lock card payload shape or restore side effects.
  - A clean owner-owned contributor in `dfcards-library` is currently blocked by
    `core/common` depending on `:dfcards-library`.
- Why now:
  - This is the last high-value settings ownership seam left in the profile
    refactor.
  - The remaining giant profile tests are now mostly carrying card complexity.
- In scope:
  - Add payload-level regression tests for `CARD_PREFERENCES`.
  - Reduce `AppProfileSettingsRestoreApplierTest` /
    `AppProfileSettingsSnapshotProviderTest` to orchestration-focused coverage.
  - Remove the `core/common -> dfcards-library` blocker needed for an
    owner-owned card contributor.
  - Move card capture/apply behind a dedicated contributor owned by the card
    module.
- Out of scope:
  - Redesign of card UI or template UX.
  - Card subsystem internal cleanup beyond what is needed for safe contributor
    extraction.
  - Broad `dfcards-library` architecture cleanup unrelated to profile import/export.
- User-visible impact:
  - None intended.
  - Existing profile bundle format and restore semantics must remain compatible.

## 2) Current Findings

### 2.1 Missed High-Value Issue: Card Payload Coverage Gap

Current tests only prove card-section presence, not card-section behavior:

- `feature/profile/src/test/java/com/trust3/xcpro/profiles/AppProfileSettingsSnapshotProviderTest.kt`
- `feature/profile/src/test/java/com/trust3/xcpro/profiles/AppProfileSettingsRestoreApplierTest.kt`
- `app/src/test/java/com/trust3/xcpro/profiles/ProfileRepositoryBundleTest.kt`

The current test suite does **not** explicitly lock:

- `templates`
- `profileTemplateCards`
- `profileFlightModeTemplates`
- `profileFlightModeVisibilities`
- `profileCardPositions`
- `cardsAcrossPortrait`
- `cardsAnchorPortrait`
- `lastActiveTemplate`
- `varioSmoothingAlpha`

### 2.2 Missed Medium-High Issue: Test Ownership Drift

`AppProfileSettingsRestoreApplierTest.kt` and
`AppProfileSettingsSnapshotProviderTest.kt` are now acting as large
mixed-contributor integration harnesses instead of small orchestration tests.

Examples:

- `feature/profile/src/test/java/com/trust3/xcpro/profiles/AppProfileSettingsRestoreApplierTest.kt:88`
- `feature/profile/src/test/java/com/trust3/xcpro/profiles/AppProfileSettingsSnapshotProviderTest.kt:82`

### 2.3 Missed Structural Blocker: Contract Module Is Not Below Card Owner

The current shared contract placement prevents an owner-owned card contributor:

- `core/common/build.gradle.kts:28`
  - `implementation(project(":dfcards-library"))`
- `core/common/src/main/java/com/trust3/xcpro/common/orientation/OrientationContracts.kt:3`
  - imports `com.example.dfcards.RealTimeFlightData`
- `core/common/src/main/java/com/trust3/xcpro/common/orientation/OrientationContracts.kt:61`
  - `updateFromFlightData(flightData: RealTimeFlightData)`

That means `dfcards-library` cannot depend on the profile contributor contract
currently housed in `core/common` without creating a cycle.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Card templates and card layout persistence | `CardPreferences` | repository APIs / flows | copied card persistence state inside `feature:profile` |
| Card profile bundle section payload | card contributor owned by `dfcards-library` | contributor interface + private DTOs | profile-local DTO copy kept as separate owner |
| Profile bundle orchestration and restore result aggregation | `feature:profile` orchestrators | `ProfileSettingsSnapshot` / `ProfileSettingsRestoreResult` | alternate card bundle entrypoint bypassing orchestrator |
| Orientation runtime contract | `core/common` orientation contract | neutral contract types only | direct `dfcards-library` model dependency in shared contract |

### 3.2 Dependency Direction

Target flow:

`ProfileRepository -> profile orchestrator -> card contributor -> CardPreferences`

Required boundary correction before that is legal:

`core/common` must no longer depend on `:dfcards-library`.

Files likely touched:

- `core/common/build.gradle.kts`
- `core/common/src/main/java/com/trust3/xcpro/common/orientation/OrientationContracts.kt`
- `feature/map/**` orientation callsites if contract changes
- `dfcards-library/**`
- `feature/profile/**`

### 3.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Card payload DTOs for profile export/import | `feature:profile` | `dfcards-library` contributor private DTOs | card subsystem owns card section semantics | contributor tests + bundle regression tests |
| Card section capture/apply | `AppProfileSettings*` switchboards | card contributor | eliminate last major profile-owned subsystem logic | provider/applier orchestration tests + card contributor tests |
| Orientation flight-data dependency in shared contract | `core/common` importing `RealTimeFlightData` | neutral shared contract or lower-level neutral model | remove cycle blocking owner-owned contributor | compile/build graph validation |

### 3.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `AppProfileSettingsSnapshotProvider` card capture branch | card payload built directly in profile orchestrator | `CardProfileSettingsContributor.captureSection(...)` | 3 |
| `AppProfileSettingsRestoreApplier` card apply branch | card payload parsed/applied directly in profile orchestrator | `CardProfileSettingsContributor.applySection(...)` | 3 |
| shared profile contract below card owner | blocked by `core/common -> dfcards-library` | remove that dependency edge first | 2 |

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| `FlightTemplate.createdAt` | Wall | persistence metadata only |
| template/profile layout `last_updated` values inside `CardPreferences` | Wall | persistence metadata only |

Rules:

- This refactor must preserve these values exactly.
- No new time comparisons should be introduced.

## 4) Implementation Phases

### Phase 0: Baseline Lock for Card Section

- Goal:
  - Add missing regression coverage for card payload content and restore side effects.
- Files to change:
  - `feature/profile/src/test/java/com/trust3/xcpro/profiles/AppProfileSettingsSnapshotProviderTest.kt`
  - `feature/profile/src/test/java/com/trust3/xcpro/profiles/AppProfileSettingsRestoreApplierTest.kt`
  - `app/src/test/java/com/trust3/xcpro/profiles/ProfileRepositoryBundleTest.kt`
- Tests to add/update:
  - assert serialized card payload fields explicitly
  - assert restore calls for:
    - `saveAllTemplates`
    - `saveProfileTemplateCards`
    - `saveProfileFlightModeTemplate`
    - `saveProfileFlightModeVisibility`
    - `saveProfileCardPositions`
    - `saveLastActiveTemplate`
    - `saveVarioSmoothingAlpha`
    - `setCardsAcrossPortrait`
    - `setCardsAnchorPortrait`
- Exit criteria:
  - card payload shape and restore semantics are test-locked

### Phase 1: Test Responsibility Split

- Goal:
  - Reduce giant profile tests to orchestration behavior only.
- Files to change:
  - `feature/profile/src/test/java/com/trust3/xcpro/profiles/AppProfileSettingsRestoreApplierTest.kt`
  - `feature/profile/src/test/java/com/trust3/xcpro/profiles/AppProfileSettingsSnapshotProviderTest.kt`
  - new focused card bundle tests under `feature/profile` and/or `dfcards-library`
- Tests to add/update:
  - keep orchestration-only tests for:
    - canonical section order
    - continue-on-failure behavior
    - missing/filtered section behavior
  - move card-specific assertions into dedicated card contributor contract tests
- Exit criteria:
  - orchestration tests are small
  - card semantics are tested where card ownership lives

### Phase 2: Contract Placement Unblock

- Goal:
  - Remove the `core/common -> dfcards-library` dependency that blocks the
    owner-owned card contributor.
- Files to change:
  - `core/common/build.gradle.kts`
  - `core/common/src/main/java/com/trust3/xcpro/common/orientation/OrientationContracts.kt`
  - orientation consumers in `feature/map/**` as needed
- Expected change:
  - replace `RealTimeFlightData` in the shared orientation contract with a
    neutral contract/model that does not require `dfcards-library`
- Exit criteria:
  - `core/common` no longer depends on `:dfcards-library`
  - `dfcards-library` can depend on the shared profile contributor contract
    without a cycle

Phase 2 result:

- completed on 2026-03-14
- shared orientation dependency was replaced by
  `OrientationFlightDataSnapshot`
- shared units classes used below the contributor contract were moved into
  `core/common`

### Phase 3: Card Contributor Extraction

- Goal:
  - Move card capture/apply out of `feature:profile` and into the card owner module.
- Files to change:
  - new card contributor and DI binding under `dfcards-library`
  - `feature/profile/src/main/java/com/trust3/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
  - `feature/profile/src/main/java/com/trust3/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
  - `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
  - `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileSettingsContributorSupport.kt`
- Exit criteria:
  - `CARD_PREFERENCES` no longer has custom capture/apply branches in the
    profile switchboards
  - card DTOs no longer live in `feature:profile`
  - bundle behavior stays compatible

Phase 3 result:

- completed on 2026-03-14
- owner-owned contributor added under
  `dfcards-library/src/main/java/com/example/dfcards/profiles/`
- focused tests added under
  `dfcards-library/src/test/java/com/example/dfcards/profiles/`

### Phase 4: Guardrails and Cleanup

- Goal:
  - Prevent card logic drifting back into the switchboards.
- Files to change:
  - `scripts/ci/enforce_rules.ps1`
  - architecture docs if needed
- Exit criteria:
  - CI blocks direct card-owner implementation knowledge from returning to
    `AppProfileSettingsSnapshotProvider` / `AppProfileSettingsRestoreApplier`

## 5) Test Plan

- Unit tests:
  - card payload capture shape
  - card restore side effects
  - contributor-level roundtrip behavior
- Regression tests:
  - bundle export/import still contains `CARD_PREFERENCES`
  - legacy default-profile alias mapping still works
- Degraded/failure-mode tests:
  - one card payload restore failure is surfaced correctly
  - invalid anchor enum falls back safely
  - unmapped imported profile IDs are ignored as before

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| card payload drift during extraction | High | add Phase 0 payload assertions first | XCPro Team |
| contract cycle persists | High | Phase 2 boundary unblock before contributor move | XCPro Team |
| test split becomes churn-only | Medium | keep orchestration tests small but do not duplicate contributor assertions | XCPro Team |
| deeper card subsystem cleanup expands scope | Medium | keep internal `CardPreferences` cleanup out of scope for this IP | XCPro Team |

## 7) Acceptance Gates

- `CARD_PREFERENCES` payload and restore behavior are regression-locked
- `AppProfileSettingsRestoreApplierTest.kt` and
  `AppProfileSettingsSnapshotProviderTest.kt` are reduced to orchestration scope
- `core/common` no longer depends on `:dfcards-library`
- `CARD_PREFERENCES` contributor is owner-owned
- no new architecture deviations are introduced

## 8) Rollback Plan

- Revert independently:
  - card test additions
  - contract-placement unblock
  - card contributor extraction
  - CI guardrails
- Recovery steps:
  1. Revert the last phase only.
  2. Keep baseline regression tests.
  3. Re-run required checks.
  4. Resume with narrower scope if needed.
