# ADS-B Icon Correctness - Release-Grade IP (5/5)

Status: In Progress  
Owner: XCPro Team  
Target date: 2026-03-09  
Scope: `feature/traffic`, `feature/map`, `docs/ADS-b`

## 0) Current Finding and Evidence

### 0.1 What the app does now

Icon identity is resolved through this path:

1. Raw ADS-B state + metadata merge into `AdsbTrafficUiModel`.
2. `AdsbAircraftClassResolver` computes aircraft class (`classForAircraft`).
3. `AdsbAircraftIconMapper` maps class to `AdsbAircraftIcon` and semantic style IDs.
4. `AdsbGeoJsonMapper` sets `PROP_ICON_ID` and emergency suffix.
5. `MapOverlayManagerRuntimeTrafficDelegate` may override with sticky projection (`ADSB_ICON_STYLE_UNKNOWN_DEFAULT` or `ADSB_ICON_STYLE_UNKNOWN_LEGACY`).
6. `AdsbTrafficOverlay` style registration adds `styleImageId` bitmaps via `ensureAdsbTrafficOverlayStyleImages`.

### 0.2 Primary source files

- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/domain/AdsbAircraftClassResolver.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIconMapper.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIcon.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/AdsbGeoJsonMapper.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/AdsbStickyIconProjectionCache.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlayStyleImages.kt`

### 0.3 Current architecture contract

- Dependency direction: UI -> domain -> data.
- Timebase for metadata/render projection: monotonic.
- Replay determinism: stable sorting in metadata lookup buckets and deterministic sticky cache overwrite logic.

## 1) Why this matters

The app previously fixed the unknown render default semantics but had one hard gap:

- `ADSB_ICON_STYLE_UNKNOWN_LEGACY` registration reused the medium-plane drawable, so rollback did not actually show the legacy question-mark visual.
- This weakens rollback fidelity and violates expected icon-forensics when users enable rollback.

All icon-correctness work must keep this truth:

- Unknown semantic state (`AdsbAircraftIcon.Unknown`) remains unchanged in data/details.
- Visual rendering can default to medium-plane for rollout safety but must rollback correctly to legacy visual when flag is off.

## 2) Phase Plan (Release-Grade 5/5)

### Phase 0 - Baseline Lock and Evidence

Objective:
- Establish exact pass/fail contract for current icon path and telemetry.

Status:
- Completed.

In scope:
- Keep existing tests green:
  - `feature/traffic/src/test/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIconMapperTest.kt`
  - `feature/traffic/src/test/java/com/trust3/xcpro/adsb/ui/AdsbAircraftIconTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/AdsbGeoJsonMapperTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/AdsbStickyIconProjectionCacheTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/AdsbIconTelemetryTrackerTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeTrafficDelegateTest.kt`

Acceptance:
- No baseline regressions in unknown semantic mapping and unknown telemetry counters.

### Phase 1 - Metadata Signal Fast-Path Hardening

Objective:
- Prioritize on-demand metadata correction opportunities that are most likely to unblock icon correctness.

Status:
- Completed.

In scope:
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/metadata/domain/AdsbMetadataEnrichmentUseCase.kt`
  - Confirm `prioritizedLookupOrder(...)` ordering:
    - ambiguous/non-fixed-wing targets first,
    - metadata-hinted second,
    - regular third.
- `feature/traffic/src/test/java/com/trust3/xcpro/adsb/metadata/AdsbMetadataEnrichmentUseCaseTest.kt`
  - Keep/extend ordering tests:
    - `targetsWithMetadata_prioritizesTargetsWithExistingMetadataHintsAboveRegularTargets`
    - `targetsWithMetadata_prioritizesMetadataHintsForRegularCategoryTargets`

Acceptance:
- Metadata-suspect/unknown-category aircraft are not delayed behind regular targets when batch is capped.

### Phase 2 - Rollback Visual Fidelity Closure (Miss Fix)

Objective:
- Ensure legacy rollback truly renders the legacy asset.

Status:
- Completed.

In scope:
- `feature/map/src/main/java/com/trust3/xcpro/map/AdsbTrafficOverlayStyleImages.kt`
  - Replace legacy registration source to `R.drawable.ic_adsb_unknown` for `ADSB_ICON_STYLE_UNKNOWN_LEGACY`.
- `feature/map/src/test/java/com/trust3/xcpro/map/AdsbTrafficOverlayStyleImagesTest.kt` (new)
  - Add regression test proving:
    - `ADSB_ICON_STYLE_UNKNOWN_LEGACY` uses `ic_adsb_unknown`.
    - default unknown still uses `AdsbAircraftIcon.Unknown.resId`.

Acceptance:
- Rollback state is visually distinguishable from default unknown.
- No icon-path regressions in known classes.

### Phase 3 - Focused Regression and Coverage

Objective:
- Validate targeted correctness and rollout surfaces under unit test pressure.

Status:
- Completed.

In scope:
- Run focused unit suites:
  - `./gradlew :feature:traffic:testDebugUnitTest --tests com.trust3.xcpro.adsb.metadata.AdsbMetadataEnrichmentUseCaseTest`
  - `./gradlew :feature:traffic:testDebugUnitTest --tests com.trust3.xcpro.adsb.ui.AdsbAircraftIconMapperTest`
  - `./gradlew :feature:traffic:testDebugUnitTest --tests com.trust3.xcpro.adsb.ui.AdsbAircraftIconTest`
  - `./gradlew :feature:map:testDebugUnitTest --tests com.trust3.xcpro.map.AdsbGeoJsonMapperTest`
  - `./gradlew :feature:map:testDebugUnitTest --tests com.trust3.xcpro.map.MapOverlayManagerRuntimeTrafficDelegateTest`
  - `./gradlew :feature:map:testDebugUnitTest --tests com.trust3.xcpro.map.AdsbTrafficOverlayStyleImagesTest`
- Verify counters/logging behavior in map delegate tests.

Acceptance:
- No regressions across touched test groups.

### Phase 4 - Release Gate (5/5 completion)

Objective:
- Deliver release-safe verification and explicit residual-risk register.

Status:
- Pending.

Execution:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- Optional:
  - `./gradlew connectedDebugAndroidTest`

Acceptance:
- All commands pass or risks logged with owners.
- Residual risks documented in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` only when unavoidable.

## 3) Focused Gap Register (current)

1. Legacy unknown visual rollback source was wrong.
   - Symptom: `ADSB_ICON_STYLE_UNKNOWN_LEGACY` currently receives medium-plane bitmap.
   - Fix: wire legacy style ID to `ic_adsb_unknown`.
   - Priority: High.
   - Owner: map/overlay.
   - Status: Completed.

2. Regression coverage gap for legacy style source.
   - Symptom: no direct test verifies legacy registration source.
   - Fix: add `AdsbTrafficOverlayStyleImagesTest`.
   - Priority: High.
   - Status: Completed.

## 4) Residual risks after this pass

1. Correctness still depends on metadata freshness and on-demand lookup cadence; stale rows can delay final icon correction.
2. Unknown fallback still depends on upstream metadata availability and transport quality.
3. Future drawable/resource-id changes must update this plan + regression tests simultaneously.

## 5) Scoring (working)

- Phase 0: Completed, 4.6/5
- Phase 1: Completed, 4.7/5
- Phase 2: Completed, 4.9/5
- Phase 3: Completed, 4.8/5
- Phase 4: Pending

Plan score target: release-grade 5/5 after all phase gates and evidence are recorded.
