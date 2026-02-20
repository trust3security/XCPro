# ADSB Improvement Plan (2026-02-20)

Status: Completed

This plan supersedes the completed dated hardening plan and captures only active, high-value ADS-B improvements from the latest deep code pass.

## 0) Delta from genius re-pass (2026-02-20, second pass)

Newly identified misses added to this plan:

1. Deterministic ordering tie-break is missing in `AdsbTrafficStore` final comparator, which can produce unstable marker ordering for equal distance/age ties.
2. `metadataRevision` increments are read-modify-write and not atomic across concurrent lookup jobs.
3. Token refresh path lacks a single-flight guard, allowing duplicate concurrent token requests under race.
4. Metadata bucket listing parser is regex/XML-string based and lacks dedicated robustness tests for format drift.

## 0B) Implementation progress (updated 2026-02-21 +11:45)

Completed in code and tests:

- [x] P1 Deterministic target ordering tie-break
  - Added deterministic final tie-break by ICAO24 in store comparator.
- [x] P2 Polling-health telemetry visibility hardening
  - Policy state access synchronized and snapshot publication reads policy telemetry atomically.
- [x] P3 Top-level loop recovery guard
  - Added loop-level unexpected-exception recovery path with bounded backoff and snapshot/error updates.
- [x] P4 Recompute churn reduction for high-rate updates
  - Added dedupe guards for unchanged center, ownship origin, and ownship altitude updates.
- [x] P5 Metadata revision atomicity hardening
  - Switched revision increment to atomic `StateFlow.update`.
- [x] P6 Token refresh single-flight dedupe
  - Added mutex-based single-flight guard around token fetch and cache re-check.
- [x] P7 Metadata listing parser robustness
  - Added continuation-token loop guard, page-limit guard, key dedupe, `NextMarker` fallback support, and dedicated client tests.
- [x] P8 Icon classification follow-up
  - Enforced authoritative non-fixed-wing category precedence and expanded helicopter/conflict regression coverage.

Remaining in this plan:

- None.

## 1) Inputs and constraints

- Architecture rules:
  - MVVM + UDF + SSOT preserved
  - dependency direction remains `UI -> domain -> data`
  - no hidden global mutable state
  - domain/fusion logic uses injected clock/time sources
- Current behavior:
  - `UnknownHostException` and `SocketTimeoutException` are normal transient network outcomes for mobile ADS-B pulls and are already classified/recovered by policy.

## 2) Prioritized findings and actions

### P1: Deterministic target ordering tie-break

Problem:
- Target store iteration uses `ConcurrentHashMap` and final sort comparator has no stable tie-break beyond distance/age.

Action:
- Add explicit stable tie-break (for example by ICAO24) at the end of displayed-target comparator.
- Add regression test asserting deterministic order when priority keys are equal.

Target files:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficStore.kt`
- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficStoreTest.kt`

Acceptance:
- Repeated `select(...)` calls with equal-priority targets return stable ordering.
- No display-count or filtering regressions.

### P2: Polling-health telemetry visibility hardening

Problem:
- `AdsbPollingHealthPolicy` telemetry fields can be read from snapshot publication while policy updates happen on polling paths.

Action:
- Make policy read/write ownership single-threaded under repository scope, or publish an immutable telemetry snapshot under synchronization/atomic semantics.

Target files:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbPollingHealthPolicy.kt`
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`

Acceptance:
- No unsynchronized cross-thread policy field reads.
- Existing telemetry snapshot tests remain green.

### P3: Top-level loop recovery guard

Problem:
- Polling loop currently relies on local result handling and `finally`; future regressions outside provider fetch path could terminate loop.

Action:
- Add an outer non-cancellation catch in `runLoop` that:
  - logs once
  - updates error snapshot safely
  - applies bounded wait
  - continues polling while enabled

Target files:
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`

Acceptance:
- New regression test proves polling survives unexpected exception in non-provider branch.

### P4: Reduce avoidable recompute churn from high-rate updates

Problem:
- Center and ownship altitude updates can trigger frequent store reselection work.

Action:
- Add dedupe/throttle strategy for ADS-B center and ownship altitude update paths before reselection.
- Keep output semantics unchanged (no target correctness regressions).

Target files:
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepository.kt`
- `feature/map/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryTest.kt`

Acceptance:
- Repeated same-value updates do not trigger unnecessary reselection.
- Behavior and snapshot correctness remain unchanged in existing tests.

### P5: Metadata revision atomicity hardening

Problem:
- Metadata revision uses read-modify-write increment and can lose increments if concurrent on-demand jobs complete close together.

Action:
- Use atomic `MutableStateFlow.update { it + 1 }` semantics for revision increments.
- Add stress-style unit coverage for concurrent on-demand completion paths.

Target files:
- `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/AircraftMetadataRepositoryImpl.kt`
- `feature/map/src/test/java/com/example/xcpro/adsb/metadata/AircraftMetadataRepositoryImplTest.kt`

Acceptance:
- Revision counter always advances for each successful hydration batch event.
- Enrichment refresh triggers are not dropped under concurrency.

### P6: Token refresh single-flight dedupe

Problem:
- Concurrent callers can trigger duplicate token fetch requests when cache is stale.

Action:
- Introduce single-flight guard around fetch path (mutex or deferred in-flight token request sharing).
- Keep existing auth-state taxonomy unchanged.

Target files:
- `feature/map/src/main/java/com/example/xcpro/adsb/OpenSkyTokenRepository.kt`
- `feature/map/src/test/java/com/example/xcpro/adsb/OpenSkyTokenRepositoryTest.kt`

Acceptance:
- Concurrent stale-token requests produce at most one network token fetch.
- Existing credential-rejection/transient-failure behavior remains unchanged.

### P7: Metadata listing parser robustness

Problem:
- S3 listing parsing uses regex over XML text and has no dedicated parser robustness tests.

Action:
- Replace regex parsing with structured XML parser, or add strict compatibility guardrails + tests covering listing variants.

Target files:
- `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/OpenSkyMetadataClient.kt`
- `feature/map/src/test/java/com/example/xcpro/adsb/metadata/OpenSkyMetadataClientTest.kt`

Acceptance:
- Listing parse remains correct across multiline/spacing/ordering variants.
- Continuation token behavior is validated by unit tests.

### P8: Icon classification follow-up

Problem:
- Helicopter-vs-fixed-wing conflict ordering risk still documented.

Action:
- Apply authoritative precedence for non-fixed-wing categories and add conflict regression tests.

Target files:
- `feature/map/src/main/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapper.kt`
- `feature/map/src/test/java/com/example/xcpro/adsb/ui/AdsbAircraftIconMapperTest.kt`
- `docs/ADS-b/ADSB_CategoryIconMapping.md`

Acceptance:
- Regression tests cover category/class/typecode conflicts and known helicopter prefixes.

## 3) Verification gate

Required commands:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run additionally when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## 4) Documentation sync

- Keep `docs/ADS-b/ADSB.md` as runtime contract.
- Keep `docs/ADS-b/ADSB_AircraftMetadata.md` and `docs/ADS-b/ADSB_CategoryIconMapping.md` for specialized subcontracts.
- Keep this file as the single active ADS-B improvement plan.
