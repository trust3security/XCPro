# CHANGE_PLAN_FIRST_LAUNCH_DEFAULT_PROFILE_PICKER_2026-04-03

## 0) Metadata

- Title: First-launch aircraft picker for canonical default profile
- Owner: Codex
- Date: 2026-04-03
- Issue/PR: N/A
- Status: Complete

## 1) Scope

- Problem statement: clean installs silently created `default-profile` as `PARAGLIDER`, so the first profile did not reflect the pilot's aircraft and public backup artifacts were written before the user made a choice.
- Why now: profile bootstrap is already the app entry gate, so first-launch profile provisioning must be explicit before more profile-scoped runtime state accumulates.
- In scope:
  - stop auto-provisioning on empty valid storage
  - add first-launch aircraft picker UI
  - create canonical `default-profile` from the selected aircraft type
  - keep `Load Profile File` available on first launch
  - suppress managed backup sync until a real active profile exists
  - add repository and compose regression coverage
- Out of scope:
  - changing later manual profile-creation flows
  - changing import/export bundle formats
  - changing degraded recovery fallback policy beyond keeping it separate from clean-install setup
- User-visible impact: first app start now asks for `Sailplane`, `Paraglider`, or `Hang Glider` before entering the app.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| First-launch completion mutation | `ProfileRepository` | `completeFirstLaunch(...)` | UI-created ad hoc default profile state |
| First-launch required flag | `ProfileViewModel` | `ProfileUiState.isFirstLaunchSetupRequired` | Composable-local startup policy |
| Canonical default profile record | `ProfileRepository` | `profiles` + `activeProfileId` | bootstrap-time UI shadow copy |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `profiles` on empty valid storage | `ProfileRepository` | hydration + repository mutations | `ProfileUseCase` -> `ProfileViewModel` | stored snapshot | `ProfileStorage` | import, recovery, create | wall clock for metadata only | bootstrap + backup tests |
| `isFirstLaunchSetupRequired` | `ProfileViewModel` | none, derived only | `ProfileSelectionContent` | hydrated + empty profiles + no bootstrap error | none | clears after successful first launch or import/recovery | none | compose tests |
| canonical first-launch profile metadata | `ProfileRepositoryMutationCoordinator` | `completeFirstLaunch` | repository snapshot flows | selected aircraft type + injected `Clock` | `ProfileStorage` | delete blocked for canonical default | injected wall clock | identity/time tests |

### 2.2 Dependency Direction

`UI -> domain -> data` is preserved.

- Modules/files touched: `feature:profile`, `app` tests, docs.
- Boundary risk: startup UI could have started owning profile creation policy; this was kept in repository/use-case owners instead.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `app/src/test/java/com/trust3/xcpro/profiles/ProfileRepositoryBootstrapRecoveryTest.kt` | already covered bootstrap and degraded profile startup behavior | repository bootstrap harness and diagnostics assertions | add explicit clean-install empty-state expectations |
| `app/src/test/java/com/trust3/xcpro/profiles/ProfileSelectionContentRecoveryTest.kt` | existing compose startup-state coverage | recovery-card UI test structure | add first-launch-specific picker and scroll behavior |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Empty-valid-storage default provisioning | bootstrap helper | no owner on clean install until user acts | clean install must be explicit | bootstrap tests |
| First-launch default creation entrypoint | none | `ProfileRepository.completeFirstLaunch(...)` | canonical creation must stay in data owner | mutation + identity tests |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepository.kt` | Existing | repository API and backup-sync gating | repository owns persistence-side profile mutations | UI must not own bootstrap/write policy | No |
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileRepositoryMutationCoordinator.kt` | Existing | atomic canonical default creation | mutation coordinator already owns profile write transactions | keeps repository facade narrow | No |
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/ProfileViewModel.kt` | Existing | derived first-launch UI state + intent forwarding | ViewModel already shapes profile startup UI state | repository should not expose UI-only flags | No |
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/ui/ProfileFirstLaunchSetupCard.kt` | New | first-launch aircraft picker rendering only | dedicated UI owner keeps setup card isolated | avoids adding mixed logic to generic content file | No |
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/ui/ProfileSelectionContent.kt` | Existing | startup-state routing between loading, first-launch, recovery, and list screens | central startup rendering switch already lives here | avoids duplicate state branching | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `ProfileRepository.completeFirstLaunch(aircraftType)` | `feature:profile` | `ProfileUseCase` | public within module boundary | canonical first-launch creation path | long-lived API |
| `ProfileUseCase.completeFirstLaunch(aircraftType)` | `feature:profile` | `ProfileViewModel` | public within module boundary | keep UI on use-case seam | long-lived API |
| `ProfileUiState.isFirstLaunchSetupRequired` | `feature:profile` | profile-selection UI | public state field | explicit first-launch routing | long-lived UI contract |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| first-launch profile `createdAt` / `lastUsed` | Wall | persisted profile metadata already uses injected wall clock |

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules: none added; change is startup/profile bootstrap only.

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| empty valid storage | User Action | `ProfileViewModel` + `ProfileSelectionContent` | first-launch aircraft picker | user selects aircraft or imports bundle | compose + bootstrap tests |
| parse/read/bootstrap failure without fallback | Recoverable | `ProfileRepositoryHydrationCoordinator` | recovery card, no silent profile creation | recover default or import bundle | recovery tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| canonical first-launch profile | `ProfileRepositoryMutationCoordinator` | fixed `default-profile` + injected `Clock` | Yes | repository owns canonical profile persistence and active-profile commit |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| clean install silently creates wrong aircraft profile | profile bootstrap default policy | unit test | `ProfileRepositoryBootstrapRecoveryTest` |
| backups written before first profile exists | repository persistence ownership | unit test | `ProfileRepositoryBackupSyncTest` |
| UI bypasses repository and creates profile locally | layering | unit test | `ProfileRepositoryMutationTest` + `ProfileRepositoryIdentityTimeOwnershipTest` |
| first-launch and recovery states overlap | startup UI ownership | compose test | `ProfileSelectionContentFirstLaunchTest` + `ProfileSelectionContentRecoveryTest` |

## 3) Data Flow (Before -> After)

Before:

`storage empty -> hydration bootstrap inserts PARAGLIDER default -> active profile set -> backup sync writes public bundle/index`

After:

`storage empty -> hydration leaves profiles empty -> ProfileViewModel derives first-launch-required -> first-launch UI asks aircraft type -> repository completeFirstLaunch creates canonical default + active profile -> backup sync resumes`

## 4) Implementation Phases

### Phase 1

- Goal: stop silent clean-install provisioning and add repository completion API.
- Files changed:
  - `ProfileRepositoryBootstrapHelpers.kt`
  - `ProfileRepositoryMutationCoordinator.kt`
  - `ProfileRepository.kt`
  - `ProfileUseCase.kt`
- Tests added/updated:
  - repository bootstrap, mutation, identity, delete-cascade, and backup-sync tests
- Exit criteria:
  - empty valid storage stays empty
  - first-launch completion creates canonical default atomically
  - backup sync does not run before first profile exists

### Phase 2

- Goal: add first-launch aircraft picker and separate it from degraded recovery UI.
- Files changed:
  - `ProfileViewModel.kt`
  - `ProfileSelectionScreen.kt`
  - `ProfileSelectionContent.kt`
  - `ProfileFirstLaunchSetupCard.kt`
- Tests added/updated:
  - `ProfileSelectionContentFirstLaunchTest`
  - `ProfileSelectionContentRecoveryTest`
- Exit criteria:
  - clean install shows aircraft picker
  - recovery state does not show first-launch picker
  - `Load Profile File` remains available on first launch

## 5) Test Plan

- Unit tests:
  - `ProfileRepositoryBootstrapRecoveryTest`
  - `ProfileRepositoryBackupSyncTest`
  - `ProfileRepositoryMutationTest`
  - `ProfileRepositoryIdentityTimeOwnershipTest`
  - `ProfileRepositoryDeleteCascadeTest`
- Compose tests:
  - `ProfileSelectionContentFirstLaunchTest`
  - `ProfileSelectionContentRecoveryTest`
- Verification notes:
  - focused profile test slice passed locally
  - repo-wide `testDebugUnitTest` may still be affected by unrelated deleted example files under `docs/PROFILES/examples`
