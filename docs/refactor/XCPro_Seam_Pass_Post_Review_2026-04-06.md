# XCPro Seam Pass Post-Review 2026-04-06

## Summary

The four-phase narrow seam pass completed green.

Result:
- Phase 1: Pass
- Phase 2: Pass
- Phase 3: Pass
- Phase 4: Pass

Final repo gate:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

passed after the final Phase 4 shape.

No new deviation entry was required for this pass.

## What Landed

### Phase 1: `:core:flight` extraction

- Added `:core:flight` as the owner of shared runtime-neutral flight contracts
  and math.
- Removed the `feature:flight-runtime -> :dfcards-library` ownership inversion.
- Rewired `dfcards-library`, `feature:flight-runtime`, `feature:map`,
  `feature:map-runtime`, and `feature:livefollow` to the new owner.

Impact score: `8/10`

### Phase 2: `feature:map-runtime` seam cleanup

- Replaced replay ownship ingestion through `RealTimeFlightData` with
  runtime-owned `ReplayLocationFrame`.
- Removed runtime-facing `FlightModeSelection` leakage from map-runtime
  contracts.
- Kept card/UI mode conversion in the map shell.
- Removed duplicate map flight-mode runtime state.

Impact score: `7/10`

### Phase 3: explicit production wiring for IGC and vario seams

- Removed production fallback construction from `IgcRecordingUseCase`.
- Removed the remaining main-source no-op diagnostics path from
  `IgcRecoveryBootstrapUseCase`.
- Removed default mandatory no-op/nullable collaborators from
  `VarioServiceManager`.
- Moved convenience fallback wiring into test support.

Impact score: `6/10`

### Phase 4: Gradle/build-config cleanup

- Added the `build-logic` included build and `xcpro.secret-properties` plugin.
- Centralized secret-property loading for `app`, `feature:forecast`,
  `feature:livefollow`, and `feature:weglide`.
- Removed duplicate OpenSky BuildConfig ownership from `feature:map`.
- Kept OpenSky BuildConfig ownership singular in `app`.

Impact score: `4/10`

## Post-Phase Drift Review

### Ownership clarity

- `:core:flight` is now the clear owner of shared non-UI flight contracts and
  pure math used across modules.
- `feature:map-runtime` no longer owns or exposes card/UI enum or replay DTO
  seams.
- OpenSky BuildConfig ownership is singular in `app`.

Score after pass: `8/10`

### Boundary direction

- The worst runtime-to-UI dependency inversion was removed.
- `feature:map-runtime` no longer depends on `:dfcards-library`.
- Production construction paths for IGC/vario seams no longer hide mandatory
  behavior through defaults.

Score after pass: `8/10`

### Lifetime explicitness

- Improved for constructor fallback policy, but not fully solved repo-wide.
- `IgcRecordingUseCase` and `VarioServiceManager` still own internal scopes.
- The broader runtime-scope ownership standardization phase was not part of
  this seam pass.

Score after pass: `5/10`

### API surface discipline

- Runtime seams are narrower and more intentional.
- Test convenience now lives in test support instead of production-reachable
  public constructors.
- Build scripts no longer duplicate the same secret-loading helpers.

Score after pass: `7/10`

### Replay safety

- Replay-specific map ingestion now uses a runtime-owned DTO.
- No new wall-time reads or non-deterministic replay behavior were introduced.

Score after pass: `8/10`

### Documentation sync

- ADRs were added for `:core:flight`, map-runtime seam tightening, explicit
  IGC/vario production wiring, and build secret/config ownership.
- `PIPELINE.md` was updated where runtime ownership changed materially.

Score after pass: `8/10`

## Overall Success Rating

Overall rating for this seam pass: `8/10`

Why not higher:
- the most damaging ownership leaks are fixed
- the repo is green
- the pass stayed narrow and did not introduce new deviations
- but broader runtime-scope ownership and remaining map-runtime hub pressure
  still need follow-on work

## Remaining Misses and Recommended Next Work

### 1. Runtime scope ownership standardization

Priority: `High`

Why:
- several authoritative runtime owners still create or hide long-lived scopes
- this is the next most likely architecture drift vector after constructor
  fallback cleanup

Suggested targets:
- `feature/tasks/.../TaskManagerCoordinator.kt`
- `feature/map/.../di/SensorFusionModule.kt`
- `feature/livefollow/.../di/LiveFollowModule.kt`
- `feature/map/.../MapOrientationManager.kt`
- audit `VarioServiceManager` under the same rule

Expected improvement: `7/10`

### 2. Task snapshot seam enforcement

Priority: `High`

Why:
- `taskSnapshotFlow` should become the only cross-feature task read seam
- this reduces future duplicate task authority and map/task bypass drift

Expected improvement: `7/10`

### 3. Further `feature:map-runtime` contraction with compile proof

Priority: `Medium`

Why:
- the card/UI leakage is gone, but `feature:map-runtime` still carries some
  real higher-level dependencies through replay/session and profile-owned
  runtime types
- further narrowing should happen only by extracting or rewiring those concrete
  types, not by cosmetic dependency deletion

Expected improvement: `6/10`

### 4. Line-budget and concentration follow-up

Priority: `Medium`

Why:
- several large owners remain and will become harder to reason about over time
- this is lower priority than ownership and runtime-lifetime correction

Expected improvement: `5/10`

## Recommendation

Do not spend the next slice on more generic cleanup.

The best next implementation track is:

1. runtime scope ownership standardization
2. task snapshot seam enforcement
3. compile-proof map-runtime contraction beyond the `dfcards` seam

This keeps the next work focused on real architecture leverage instead of
cosmetic churn.
