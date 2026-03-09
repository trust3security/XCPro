# PROFILE_PRODUCTION_GRADE_PHASED_IP_2026-03-08

## Purpose

Define a production-grade phased implementation plan for profile behavior in XCPro, with explicit scoring per phase based on current code state.

Primary target outcomes:

1. One canonical default profile always exists and is recoverable.
2. Profile switch applies profile-specific settings deterministically.
3. Full profile backup/restore is portable, deterministic, and user-manageable.
4. `General -> Units` is profile-scoped and restored per profile.
5. User settings (cards/layouts/glider polar/theme/look-and-feel) are never silently "lost" due to profile-key drift.

## Scoring Model

- Score meaning: current completion against phase exit gate.
- `95-100`: production-ready for that phase.
- `85-94`: strong but has meaningful gaps.
- `<85`: not production-ready for that phase.

Assessment date: `2026-03-08`.

## Architecture Contract (Non-Negotiable)

- Preserve `UI -> domain/use-case -> data`.
- Keep settings SSOT in repositories, never in UI state.
- Keep profile bundle files as portability artifacts, not runtime SSOT.
- Keep deterministic startup/profile resolution.
- Keep Android I/O in data adapters, not in domain/viewmodel.

## Phase Plan and Scores

### Phase 0 - Baseline and Contract Freeze

Goal:
- Lock scope, ownership, include/exclude matrix, and acceptance gates before migration work.

Current evidence:
- `docs/PROFILES/CHANGE_PLAN_PROFILE_FULL_SETTINGS_BUNDLE_2026-03-07.md`
- `docs/PROFILES/PROFILE_STORAGE_AND_SETTINGS_SCOPE.md`

Remaining gaps:
- Tier-A vs Tier-B boundary needs explicit final decision for units (currently still global in runtime).

Exit gate:
- Signed-off scope matrix with explicit ownership and migration rules.

Score: `96/100`

### Phase 1 - Canonical Profile Identity and Startup Hardening

Goal:
- Enforce canonical profile identity and stable startup behavior.

Current evidence:
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileIdResolver.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootStateBindings.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ColorsScreen.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt`

Remaining gaps:
- Continue policing for new ad-hoc profile ID fallbacks in future features.

Exit gate:
- No profile-key writes under legacy default aliases in active code paths.

Score: `95/100`

### Phase 2 - Bundle Schema, Storage Engine, and Managed Backup Safety

Goal:
- Harden managed backup generation with namespace ownership, ordering, and safe replacement semantics.

Current evidence:
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBackupSink.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBundleCodec.kt`
- `feature/profile/src/test/java/com/example/xcpro/profiles/ProfileBackupSinkContractTest.kt`

Remaining gaps:
- Keep validating release/debug coexistence behavior in real-device regression runs.

Exit gate:
- Managed backup sync is deterministic, namespace-safe, and non-destructive to non-managed files.

Score: `95/100`

### Phase 3 - Full Settings Snapshot Coverage

Goal:
- Cover all required profile settings in snapshot/export/restore sections.

Current evidence:
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSnapshot.kt`
- `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
- `app/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
- `app/src/main/java/com/example/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
- `app/src/test/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProviderTest.kt`
- `app/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierTest.kt`

Remaining gaps:
- Maintain regression coverage whenever new Tier A sections are added.

Exit gate:
- Full profile settings matrix includes units and restores units correctly per profile.

Score: `97/100`

### Phase 4 - Import/Export Unification and Compatibility

Goal:
- Use one production import/export contract with compatibility for legacy documents.

Current evidence:
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileExportImport.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileBundleCodec.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- `app/src/test/java/com/example/xcpro/profiles/ProfileExportImportTest.kt`

Remaining gaps:
- Full release-evidence closure remains in Phase 6 scope.

Exit gate:
- Import path is deterministic and user-proof for bundle-first flow, with clear fallback behavior.

Score: `96/100`

### Phase 5 - Runtime Profile-Scoped Settings Migration

Goal:
- Ensure all intended per-profile settings are truly profile-scoped at runtime.

Current evidence:
- Profile scoping exists for several domains (theme/look-and-feel/cards/layout/glider/variometer paths).
- `core/common/src/main/java/com/example/xcpro/common/units/UnitsRepository.kt`
- `core/common/src/main/java/com/example/xcpro/common/units/UnitsSettingsUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/UnitsSettingsViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/UnitsSettings.kt`
- `app/src/main/java/com/example/xcpro/profiles/AppProfileScopedDataCleaner.kt`
- `app/src/test/java/com/example/xcpro/common/units/UnitsRepositoryProfileScopeTest.kt`

Remaining gaps:
- Complete final instrumentation evidence for restart/import/profile-switch permutations (Phase 6 release gate).

Exit gate:
- Switching profile applies the exact units set for that profile and preserves isolation.

Score: `96/100`

### Phase 6 - UX Recovery, Diagnostics, and Release Verification

Goal:
- Make failures recoverable and observable, with production-grade validation gates.

Current evidence:
- Recovery actions in profile selection flow:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ui/ProfileSelectionContent.kt`
- Startup hydration gating:
  - `app/src/main/java/com/example/xcpro/MainActivityScreen.kt`

Remaining gaps:
- Full multi-module `connectedDebugAndroidTest` run remains optional for strict release-signoff closure when schedule permits.

Exit gate:
- Full manual + automated evidence set passes across startup/switch/import/export/recovery paths.

Score: `95/100`

## Priority to Reach 95+ Across All Phases

1. (Optional strict release signoff) Run full `connectedDebugAndroidTest --no-parallel`.

## Recommended Verification Commands

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```
