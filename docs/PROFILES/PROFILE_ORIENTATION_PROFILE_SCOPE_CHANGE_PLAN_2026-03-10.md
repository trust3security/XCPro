# PROFILE_ORIENTATION_PROFILE_SCOPE_CHANGE_PLAN_2026-03-10

## 0) Metadata

- Title: Profile-scoped Orientation settings for aircraft profile files
- Owner: Codex
- Date: 2026-03-10
- Issue/PR: local working change
- Status: In progress

## 1) Scope

- Problem statement:
  - `General -> Orientation` is not included in the portable aircraft profile file.
  - Runtime orientation settings are currently global, so switching aircraft profiles does not switch orientation behavior.
- Why now:
  - Aircraft profiles need to carry all relevant per-aircraft setup, and Orientation is a documented remaining gap.
- In scope:
  - make orientation settings profile-scoped at runtime
  - wire active-profile switching into orientation runtime
  - include orientation settings in profile export/import
  - add repository and bundle tests
- Out of scope:
  - migrate other global General sections in this change
  - `General -> Files` package policy
  - replay/session/history settings
- User-visible impact:
  - each aircraft profile can keep its own Orientation setup
  - importing/exporting an aircraft profile preserves Orientation settings
  - switching aircraft applies the matching Orientation setup

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| active orientation settings for selected aircraft profile | `MapOrientationSettingsRepository` | `StateFlow<MapOrientationSettings>` | UI-local or manager-local persisted mirrors |
| exported/imported orientation snapshot | `ProfileSettingsSnapshot` section `tier_a.orientation_preferences` | bundle JSON section | direct runtime ownership by bundle file |

### 2.2 Dependency Direction

Confirmed flow remains:

`UI -> use case -> repository`

- Modules/files touched:
  - `feature/map`
  - `app`
  - `feature/profile`
  - `docs/PROFILES`
- Boundary risk:
  - keep file import/export at profile bundle adapter layer
  - do not move persistence into ViewModels

### 2.2A Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| profile-specific orientation persistence | global orientation preference keys | `MapOrientationSettingsRepository` with profile-scoped keys | aircraft switching must switch orientation state | repository tests + profile bundle tests |

### 2.2B Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapScreenViewModel.setActiveProfileId(...)` | orientation repo not updated on profile switch | explicitly set orientation active profile id | Phase 2 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| orientation preference persistence | n/a | preference state only |
| orientation engine timing | Monotonic / Wall, unchanged | existing runtime behavior remains unchanged |

Explicitly forbidden comparisons:

- monotonic vs wall
- replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - preference reads/writes stay in repository/store owner paths
  - existing orientation runtime cadence stays unchanged
- Primary cadence/gating sensor:
  - unchanged, orientation sensor flow sampling in `MapOrientationManager`
- Hot-path latency budget:
  - no new work added to sensor math path beyond existing settings-flow updates

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - unchanged
  - this change only affects which persisted orientation settings are selected for the active profile

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| duplicate orientation SSOT | `ARCHITECTURE.md` SSOT | unit test + review | orientation repository tests |
| profile switch does not update orientation runtime | `ARCHITECTURE.md` SSOT/UDF | unit test + review | profile bundle/profile switch tests |
| bundle export/import misses orientation | `CHANGE_PLAN_TEMPLATE.md` regression requirement | unit test | profile repository bundle tests |

## 3) Data Flow (Before -> After)

Before:

`Orientation UI -> OrientationSettingsUseCase -> MapOrientationSettingsRepository (global prefs)`

`Profile switch -> units/glider/variometer active profile update`

After:

`Orientation UI -> OrientationSettingsUseCase -> MapOrientationSettingsRepository (profile-scoped prefs)`

`Profile switch -> units/glider/variometer/orientation active profile update`

`Profile export -> ProfileSettingsSnapshotProvider -> tier_a.orientation_preferences`

`Profile import -> ProfileSettingsRestoreApplier -> MapOrientationSettingsRepository`

## 4) Implementation Phases

### Phase 0

- Goal:
  - document scope and preserve current runtime contracts outside orientation ownership
- Files to change:
  - this plan doc
- Tests to add/update:
  - none
- Exit criteria:
  - plan committed in-repo

### Phase 1

- Goal:
  - refactor orientation repository storage to support profile-scoped keys with legacy fallback
- Files to change:
  - `feature/map/.../MapOrientationSettingsRepository.kt`
  - `feature/map/.../MapOrientationPreferences.kt`
- Tests to add/update:
  - orientation repository tests
- Exit criteria:
  - repository can read/write settings per profile id

### Phase 2

- Goal:
  - wire active profile switching into orientation runtime
- Files to change:
  - `feature/map/.../OrientationSettingsUseCase.kt`
  - `feature/map/.../MapScreenViewModel.kt`
  - related runtime owners if needed
- Tests to add/update:
  - bundle/profile switch tests
- Exit criteria:
  - switching aircraft changes runtime orientation settings selection

### Phase 3

- Goal:
  - add export/import snapshot section for orientation
- Files to change:
  - `app/.../AppProfileSettingsSnapshotProvider.kt`
  - `app/.../AppProfileSettingsRestoreApplier.kt`
  - `app/.../ProfileSettingsSectionSnapshots.kt`
  - `feature/profile/.../ProfileSettingsSnapshot.kt`
  - `docs/PROFILES/...`
- Tests to add/update:
  - snapshot/restore round-trip tests
- Exit criteria:
  - aircraft profile file preserves orientation settings

### Phase 4

- Goal:
  - harden docs and verification
- Files to change:
  - `docs/PROFILES/PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`
  - `docs/PROFILES/PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md`
- Tests to add/update:
  - targeted tests as needed
- Exit criteria:
  - docs match implemented ownership

## 5) Test Plan

- Unit tests:
  - `MapOrientationSettingsRepositoryTest`
  - profile bundle import/export tests
- Replay/regression tests:
  - not needed; replay timing behavior unchanged
- UI/instrumentation tests:
  - none required for this repository-focused slice
- Degraded/failure-mode tests:
  - legacy fallback values still load when profile-scoped keys are absent
- Boundary tests for removed bypasses:
  - switching active profile changes selected orientation settings

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| legacy users lose orientation preferences | incorrect initial settings after upgrade | legacy fallback read path per key | Codex |
| orientation runtime does not refresh after profile switch | wrong aircraft orientation mode remains active | repository emits new settings on active profile change; hook switch path | Codex |
| bundle import applies wrong profile mapping | imported settings land on wrong profile | use imported profile id map like other profile-scoped sections | Codex |

## 7) Acceptance Gates

- no rule violations from architecture docs
- no duplicate SSOT ownership introduced
- orientation is explicitly profile-scoped
- bundle export/import preserves orientation
- profile switching applies orientation settings for the active profile

## 8) Rollback Plan

- Revert orientation section snapshot/restore independently if bundle compatibility issue appears.
- Revert repository active-profile scoping while keeping legacy read compatibility if runtime regression appears.
