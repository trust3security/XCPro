# Kotlin Line-Budget Compliance Phased IP (`RULES-20260306-14` Closeout Record)

## Purpose

Release-grade phased plan that closed the default line-budget deviation scope
for `RULES-20260306-14` without ad hoc churn, behavior drift, or hidden
test-fixture ownership.

This document supersedes the earlier draft scope and is retained as the
completed remediation record. The previously planned map runtime and ADS-B
runtime lanes have already landed, and no files remain in active scope.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` for current active exceptions, if any

## 0) Metadata

- Title: Kotlin line-budget compliance for remaining default-budget exceptions
- Owner: XCPro Team
- Date: 2026-03-06
- Refreshed: 2026-04-14
- Issue/PR: `RULES-20260306-14` remediation lane
- Status: Complete

## 1) Current Scope

As of 2026-03-15 after Phase 4 completion, no files remain in active scope.

| File | Lines | Seam Summary | Recommended Lane |
|---|---:|---|---|
| None | 0 | Remaining scope closed | None |

Why this order:

- The earlier phases closed the oversized test lanes first.
- `CardLibraryCatalog.kt` was the last production-file exception and is now
  split into category-owned files with a thin aggregation owner.

Out of scope:

- Functional feature work
- Behavior changes in profile restore, ADS-B repository policy, wind policy, or
  card definitions
- Expanding `RULES-20260306-14` to unrelated files after closeout

## 2) Seam-Pass Findings

Focused seam/code repass date: 2026-03-15.

### 2.1 Completed Phase 1: `AppProfileSettingsRestoreApplierTest.kt`

- Landed files:
  - `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierTestSupport.kt`
  - `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierOrderingAndFailureTest.kt`
  - `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierProfileScopedSectionsTest.kt`
  - `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierLocalGlobalSectionsTest.kt`
- Removed file:
  - `feature/profile/src/test/java/com/example/xcpro/profiles/AppProfileSettingsRestoreApplierTest.kt`
- Preserved ownership:
  - support owns harness creation only
  - ordering/failure owns canonical order and fail-and-continue semantics
  - profile-scoped sections own mapped-profile and canonical-default routing
  - local/global sections own extracted contributor application

### 2.2 Completed Phase 2: `AdsbTrafficRepositoryFilterAndAuthTest.kt`

- Landed files:
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositorySelectionAndFilterTest.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryAuthAndEnableQueueTest.kt`
- Removed file:
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryFilterAndAuthTest.kt`
- Reused support owner:
  - `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTestRuntime.kt`
- Preserved ownership:
  - `SelectionAndFilterTest` owns cache reuse, ownship reference switching,
    display filters, bbox propagation, altitude filtering, and proximity tiers.
  - `AuthAndEnableQueueTest` owns token fallback and queued mutations before
    enable.

### 2.3 Completed Phase 3: `CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt`

- Landed files:
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseWindFallbackPolicyTestRuntime.kt`
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseWindPolicyCountersTestRuntime.kt`
- Removed file:
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt`
- Reused support owner:
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTestSupportRuntime.kt`
- Preserved ownership:
  - `WindFallbackPolicyTestRuntime` owns TE disable, stale/low-confidence wind,
    grace-period fallback, and dwell/hysteresis behavior.
  - `WindPolicyCountersTestRuntime` owns decision counters, transition counters,
    exactness assertions, and reset accounting.

### 2.4 `CardLibraryCatalog.kt`

- The production file is over budget because it inlines every category catalog.
- The category seams are already explicit in the existing top-level lists:
  - `essentialCards`
  - `varioCards`
  - `navigationCards`
  - `performanceCards`
  - `timeWeatherCards`
  - `competitionCards`
  - `advancedCards`
- The correct production shape is a thin aggregation file plus one file per
  existing category group.

## 3) Architecture Contract

### 3.1 Ownership

- Test support files own fixture/harness creation only.
- Test scenario files own one behavior family each.
- Production aggregation files own composition only, not inline data for all
  categories.

### 3.2 Non-Negotiables

- No behavior changes.
- No policy drift.
- No hidden time/random seams introduced while splitting tests.
- No replacement monoliths: each new file should target `<= 450` lines and must
  remain `<= 500`.
- Keep deterministic ordering assertions where they already exist.

### 3.3 Verification

Every implementation phase must run:

```bash
./gradlew enforceRules --no-configuration-cache
./gradlew testDebugUnitTest --no-configuration-cache
./gradlew assembleDebug --no-configuration-cache
```

Targeted lanes should also run for the touched scope before the full suite.

## 4) Implementation Phases

### Phase 0 - Freeze Remaining Scope

- Goal:
  - Keep `RULES-20260306-14` limited to the scoped files in this lane.
- Exit criteria:
  - The scoped default-budget files match this plan.
  - No new oversized files are added to the issue during this lane.

### Phase 1 - Split `AppProfileSettingsRestoreApplierTest.kt`

- Goal:
  - Remove duplicated fixture ownership first, then split by contributor
    behavior family.
- Planned files:
  - `AppProfileSettingsRestoreApplierTestSupport.kt`
  - `AppProfileSettingsRestoreApplierOrderingAndFailureTest.kt`
  - `AppProfileSettingsRestoreApplierProfileScopedSectionsTest.kt`
  - `AppProfileSettingsRestoreApplierLocalGlobalSectionsTest.kt`
- Ownership:
  - `TestSupport` owns `Harness`, `createHarness()`, registry setup, and shared
    mocks only.
  - `OrderingAndFailureTest` owns canonical order and fail-and-continue
    semantics.
  - `ProfileScopedSectionsTest` owns mapped-profile, canonical-default alias,
    default-layout fallback, and section-routing assertions.
  - `LocalGlobalSectionsTest` owns extracted contributor application for
    non-profile-scoped/global sections.
- Exit criteria:
  - `AppProfileSettingsRestoreApplierTest.kt` is removed.
  - All new files remain under budget.

Status:

- Complete on 2026-03-15.

### Phase 2 - Split `AdsbTrafficRepositoryFilterAndAuthTest.kt`

- Goal:
  - Separate filter/cache behavior from auth/enable-queue behavior.
- Planned files:
  - `AdsbTrafficRepositorySelectionAndFilterTest.kt`
  - `AdsbTrafficRepositoryAuthAndEnableQueueTest.kt`
- Reused support:
  - `AdsbTrafficRepositoryTestRuntime.kt` remains the single shared test-support
    owner for this lane.
- Ownership:
  - `SelectionAndFilterTest` owns cache reuse, center/origin recompute,
    bounding-box propagation, altitude filtering, and proximity tiers.
  - `AuthAndEnableQueueTest` owns token fallback and queued mutations before
    enable.
- Exit criteria:
  - `AdsbTrafficRepositoryFilterAndAuthTest.kt` is removed.
  - Existing deterministic proximity assertions are preserved.
  - No new ADS-B test support file is introduced.

Status:

- Complete on 2026-03-15.

### Phase 3 - Split `CalculateFlightMetricsUseCaseWindPolicyTestRuntime.kt`

- Goal:
  - Separate wind fallback policy behavior from counter/accounting behavior.
- Planned files:
  - `CalculateFlightMetricsUseCaseWindFallbackPolicyTestRuntime.kt`
  - `CalculateFlightMetricsUseCaseWindPolicyCountersTestRuntime.kt`
- Reused support:
  - `CalculateFlightMetricsUseCaseTestSupportRuntime.kt` remains the single
    shared test-support owner for this lane.
- Ownership:
  - `WindFallbackPolicyTestRuntime` owns TE disable, stale/low-confidence wind,
    grace-period fallback, and dwell/hysteresis behavior.
  - `WindPolicyCountersTestRuntime` owns decision counters, transition counters,
    and reset accounting.
- Exit criteria:
  - Original host file is removed.
  - Counter exactness assertions remain unchanged.
  - No new wind-policy test support file is introduced.

Status:

- Complete on 2026-03-15.

### Phase 4 - Split `CardLibraryCatalog.kt`

- Goal:
  - Keep `CardLibraryCatalog.kt` as a thin aggregation owner only.
- Planned files:
  - `CardLibraryEssentialCatalog.kt`
  - `CardLibraryVarioCatalog.kt`
  - `CardLibraryNavigationCatalog.kt`
  - `CardLibraryPerformanceCatalog.kt`
  - `CardLibraryTimeWeatherCatalog.kt`
  - `CardLibraryCompetitionCatalog.kt`
  - `CardLibraryAdvancedCatalog.kt`
- Ownership:
  - Each category file owns only its category definitions.
  - `CardLibraryCatalog.kt` owns aggregation/order only.
- Exit criteria:
  - No card definition drift.
  - Aggregation file is comfortably below budget.

Status:

- Complete on 2026-03-15.

### Phase 5 - Close `RULES-20260306-14`

- Goal:
  - Remove the deviation honestly once the scoped files are gone.
- Exit criteria:
  - No remaining files from this scope exceed the default budget.
  - Active deviation state is narrowed or closed accordingly.

Status:

- Complete on 2026-03-15.

## 5) Reviewer Checklist

- Does each new test file map to a single behavior family?
- Is test support isolated from scenario assertions?
- Were deterministic order/counter assertions preserved exactly?
- Did any split accidentally drop constructor/setup coverage?
- Is `CardLibraryCatalog.kt` now aggregation-only?
- Did `enforceRules`, `testDebugUnitTest`, and `assembleDebug` pass?

## 6) Closeout

`RULES-20260306-14` closeout is complete for the default-budget exception
scope tracked by this plan. There are no remaining global default line-budget
exception paths in `scripts/ci/enforce_rules.ps1`.
