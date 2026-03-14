# Profile/Card Boundary Seam Lock

## 0) Metadata

- Title: Seam lock for the profile/card boundary
- Owner: Codex
- Date: 2026-03-14
- Status: Complete
- Parent plans:
  - `docs/refactor/Profile_Settings_Contributor_Boundary_Refactor_Plan_2026-03-13.md`
  - `docs/refactor/Card_Preferences_Profile_Contributor_Extraction_Plan_2026-03-14.md`

## 0A) Current Execution Status

- Phase A complete:
  - `core/common` no longer depends on `:dfcards-library`
  - the shared orientation contract now uses
    `OrientationFlightDataSnapshot`
  - shared units types used by `core/common` moved under
    `core/common/src/main/java/com/example/xcpro/common/units/`
  - orientation consumers in `feature:map` were updated without changing
    orientation behavior
- Verification completed:
  - `./gradlew :core:common:compileDebugKotlin --no-configuration-cache`
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.MapOrientationManagerTest" --no-configuration-cache`
  - `./gradlew :feature:map:compileDebugKotlin --no-configuration-cache "-Pksp.incremental=false"`
  - `./gradlew enforceRules --no-configuration-cache`
  - `./gradlew testDebugUnitTest --no-configuration-cache`
  - `./gradlew assembleDebug --no-configuration-cache`
- Phase B complete:
  - `CARD_PREFERENCES` capture/apply now lives in
    `dfcards-library/src/main/java/com/example/dfcards/profiles/CardProfileSettingsContributor.kt`
  - `feature:profile` orchestrators no longer own card payload DTOs or card
    import/export semantics
  - card contributor multibinding now lives in
    `dfcards-library/src/main/java/com/example/dfcards/profiles/CardProfileSettingsBindingsModule.kt`
  - focused contributor and orchestration tests passed
- Phase C complete:
  - production UI no longer constructs `CardPreferences(context)` directly
  - `FlightModeIndicator(...)` now consumes host-provided available modes
    instead of reading `CardPreferences` inside the composable
  - `CardLibraryModal(...)` now consumes owner-provided saved-template state
    and persistence callbacks instead of constructing `CardPreferences`
  - the only remaining `CardPreferences(context)` callsite is the
    `app` androidTest hydration harness

## 1) Purpose

Lock the exact next ownership cut before any profile/card implementation work.

This pass is intentionally focused on the remaining boundary leak, not on broad
card-system cleanup and not on forecast/weather runtime.

## 2) Decision Summary

The next ownership target should be the profile/card boundary.

The seam has three linked problems:

1. `feature:profile` still owns `CARD_PREFERENCES` capture/apply and DTO shape.
2. `core/common` still depends on `:dfcards-library` through the shared
   orientation contract, which blocks a clean owner-owned card contributor.
3. UI still constructs `CardPreferences` directly in places that should consume
   an injected owner or a narrow host-provided contract.

Professional recommendation:

- first batch: unblock the shared contract edge and move `CARD_PREFERENCES`
  behind an owner-owned contributor
- second batch only if needed: clean up the remaining direct UI
  `CardPreferences` constructions
- do not start forecast/weather runtime until this boundary is locked and
  reviewed

## 3) Reference Pattern Check

Reference files reviewed:

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/weather/src/main/java/com/example/xcpro/weather/rain/WeatherOverlayProfileSettingsContributor.kt` | owner-owned settings contributor already migrated out of `feature:profile` | feature-owned capture/apply contributor with private payload DTO | card contributor will live in `dfcards-library`, not a `feature:*` module |
| `feature/weather/src/main/java/com/example/xcpro/weather/rain/WeatherProfileSettingsBindingsModule.kt` | current contributor multibinding pattern | `@IntoSet` binding for capture/apply owners | same pattern, different owner module |
| `app/src/main/java/com/example/xcpro/di/AppModule.kt` | repo already has the canonical DI provider for `CardPreferences` | injected singleton owner from app graph | UI cleanup should reuse this provider instead of constructing `CardPreferences(context)` |

No new discovery pattern is needed.

## 4) Exact Boundary Findings

### 4.1 Card Bundle Logic Still Lives in `feature:profile`

Current owner leak:

- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
  - still captures `CARD_PREFERENCES` directly via `CardPreferences`
- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
  - still parses and applies `CARD_PREFERENCES` directly via `CardPreferences`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
  - still owns the card payload DTOs

Implication:

- `feature:profile` is still the implementation owner of card import/export
  semantics even though runtime card persistence is owned by `CardPreferences`
  in `dfcards-library`

### 4.2 Shared Contract Placement Is Still Blocked by `core/common`

Current blocker:

- `core/common/build.gradle.kts`
  - `implementation(project(":dfcards-library"))`
- `core/common/src/main/java/com/example/xcpro/common/orientation/OrientationContracts.kt`
  - imports `com.example.dfcards.RealTimeFlightData`
  - `OrientationController.updateFromFlightData(...)` depends on that type

Practical impact:

- `dfcards-library` cannot depend on the shared profile contributor contract in
  `core/common` without creating a cycle
- the profile/card seam cannot be made owner-owned until this edge is removed

The good news:

- current orientation consumers in `feature:map` only use a narrow subset of
  flight fields, so a neutral shared snapshot contract is sufficient

### 4.3 UI Construction Leak (Closed in Phase C)

Direct constructions that were present before Phase C:

- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileQuickActions.kt`
  - `FlightModeIndicator(...)` creates `CardPreferences(context)` inside a
    composable and reads DataStore directly
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardLibraryModal.kt`
  - creates `CardPreferences(context)` inside owner UI for template auto-save

Why this matters:

- `CardPreferences` is the persistence-facing owner for card settings
- UI should not construct persistence wrappers directly when a DI-provided owner
  already exists

Phase C result:

- production UI now consumes host-provided contracts instead of constructing
  persistence wrappers directly
- no production `CardPreferences(context)` callsites remain in
  `feature:profile` or `dfcards-library`

## 5) SSOT and Boundary Decisions

| Data / Responsibility | Authoritative Owner | Exposed As | Forbidden Duplicate |
|---|---|---|---|
| Card templates, profile template cards, flight-mode templates, visibilities, positions, portrait anchor/count, smoothing | `CardPreferences` in `dfcards-library` | repository APIs / flows | profile-local mirrors or ad hoc UI caches of persistence state |
| Profile bundle orchestration and result aggregation | `feature:profile` orchestrators | `ProfileSettingsSnapshot` / `ProfileSettingsRestoreResult` | alternate owner-side bundle entrypoints |
| Card bundle capture/apply semantics | owner-owned card contributor in `dfcards-library` | contributor capture/apply APIs | direct `CARD_PREFERENCES` branches in `AppProfileSettings*` |
| Orientation shared contract | `core/common` | neutral contract types only | `core/common` importing `RealTimeFlightData` |

Dependency direction after the seam:

`feature:profile orchestrator -> contributor contract in core/common -> owner-side card contributor -> CardPreferences`

## 6) Exact First Implementation Cut

### Phase A: Shared Contract Unblock

Goal:

- remove `core/common -> :dfcards-library`
- replace the orientation contract dependency on `RealTimeFlightData` with a
  neutral shared snapshot

Files to modify:

- `core/common/build.gradle.kts`
- `core/common/src/main/java/com/example/xcpro/common/orientation/OrientationContracts.kt`
- `feature/map/src/main/java/com/example/xcpro/OrientationSensorSource.kt`
- `feature/map/src/main/java/com/example/xcpro/OrientationDataSource.kt`
- `feature/map/src/main/java/com/example/xcpro/MapOrientationManager.kt`
- orientation-related tests under `feature/map/src/test/java/com/example/xcpro/`

Locked rule:

- the neutral contract should carry only the fields orientation actually uses
- do not move card logic into `core/common`
- do not redesign orientation behavior in this batch

### Phase B: Owner-Owned Card Contributor Extraction

Goal:

- move `CARD_PREFERENCES` capture/apply out of `feature:profile`

Files to add:

- `dfcards-library/src/main/java/com/example/dfcards/profiles/CardProfileSettingsContributor.kt`
- `dfcards-library/src/main/java/com/example/dfcards/profiles/CardProfileSettingsBindingsModule.kt`
- focused contributor tests under `dfcards-library/src/test/java/com/example/dfcards/profiles/`

Files to modify:

- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProvider.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplier.kt`
- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileSettingsSectionSnapshots.kt`
- `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsSnapshotProviderTest.kt`
- `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierTest.kt`
- `app/src/test/java/com/example/xcpro/profiles/ProfileRepositoryBundleTest.kt`

Locked rule:

- keep `feature:profile` as the orchestrator of bundle order and restore result
  aggregation
- keep card payload DTOs private to the owner contributor when practical
- preserve current payload semantics and alias handling exactly

### Phase C: UI Construction Cleanup

Goal:

- remove the remaining direct `CardPreferences(context)` constructions from UI

Planned files:

- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileQuickActions.kt`
- `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardLibraryModal.kt`
- the owning host/viewmodel files needed to pass a DI-owned `CardPreferences`
  instance or a narrower contract down into UI

Locked rule:

- do not hide the owner behind a global singleton or service locator
- do not widen the contributor extraction PR with this UI cleanup unless the
  compile graph demands it

Phase C result:

- completed on 2026-03-14
- `ProfileQuickActions.kt` now renders from host-provided mode data only
- `CardLibraryModal.kt` now renders from host-provided saved-template state and
  emits persistence intents upward
- no new service locator or global singleton was introduced

## 7) Explicit Non-Targets

Do not mix this seam with:

- forecast/weather runtime extraction
- broad card UI redesign
- card catalog cleanup
- map-shell work
- unrelated profile import/export sections that are already migrated

## 8) Verification Targets

Required after the first implementation batch:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Focused slice checks:

```bash
./gradlew :feature:profile:testDebugUnitTest --tests "com.example.xcpro.profiles.AppProfileSettingsSnapshotProviderTest" --tests "com.example.xcpro.profiles.AppProfileSettingsRestoreApplierTest" --tests "com.example.xcpro.profiles.ProfileSettingsContributorRegistryTest"
./gradlew :app:testDebugUnitTest --tests "com.example.xcpro.profiles.ProfileRepositoryBundleTest" --tests "com.example.xcpro.profiles.ProfileRepositoryBackupSyncTest"
./gradlew :dfcards-library:testDebugUnitTest
```

## 9) Next Recommendation

This seam is complete.

Next work should be either:

1. close-out/guardrail follow-up if new profile/card drift appears, or
2. move to the next ownership target, with forecast/weather runtime still
   remaining a valid later candidate
