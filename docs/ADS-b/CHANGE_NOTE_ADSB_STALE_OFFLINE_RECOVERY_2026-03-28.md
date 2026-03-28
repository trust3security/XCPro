# CHANGE NOTE - ADS-B Stale Offline Recovery (2026-03-28)

## Problem

Field report:

- ADS-B map status turned red.
- Connectivity later recovered.
- ADS-B stayed red until full app restart.

Observed date for this note: 2026-03-28.

## Root Cause

The ADS-B runtime recovery path trusted the injected `isOnline` flow as the
only source of truth while waiting offline or during retry delays.

That is safe in tests, but in production the Android connectivity callback path
can leave the flow stale-false after a recovery or network handover. When that
happened:

1. `AdsbTrafficRepositoryRuntime.awaitNetworkOnline()` kept waiting.
2. No new ADS-B poll happened.
3. The UI kept projecting the repository's degraded state.
4. App restart re-seeded connectivity from `ConnectivityManager.activeNetwork`,
   so the indicator turned green again.

## Fix Implemented

- Added `currentOnlineState()` to `AdsbNetworkAvailabilityPort`.
- `AndroidAdsbNetworkAvailabilityAdapter` now exposes a fresh connectivity
  snapshot from `ConnectivityManager`.
- ADS-B offline wait and retry-delay logic now re-checks that fresh snapshot
  instead of trusting only the event flow.
- Snapshot network telemetry now uses the same fresh connectivity read.

## Files Changed

- `feature/traffic/src/main/java/com/example/xcpro/adsb/domain/AdsbNetworkAvailabilityPort.kt`
- `feature/traffic/src/main/java/com/example/xcpro/adsb/data/AndroidAdsbNetworkAvailabilityAdapter.kt`
- `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimeNetworkWait.kt`
- `feature/traffic/src/main/java/com/example/xcpro/adsb/AdsbTrafficRepositoryRuntimeSnapshot.kt`
- `feature/traffic/src/test/java/com/example/xcpro/adsb/AdsbTrafficRepositoryConnectivityTest.kt`

## Regression Coverage

Added:

- `staleOfflineFlow_recoversFromFreshNetworkSnapshotWithoutRestart()`

This locks the production-like case where the callback-backed flow remains
false but the fresh network snapshot is already true.

## Verification

Pass:

- `./gradlew.bat :feature:traffic:testDebugUnitTest --tests "com.example.xcpro.adsb.AdsbTrafficRepositoryConnectivityTest"`

Workspace blockers seen during broader verification on 2026-03-28:

- `./gradlew.bat enforceRules`
  - failed for an unrelated existing line-budget violation in
    `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlaySupport.kt`
    (`509` lines, max `500`).
- `./gradlew.bat testDebugUnitTest`
  - blocked by a Windows file-lock cleanup failure under
    `feature/traffic/build/test-results/testDebugUnitTest/binary/output.bin`.
- A parallel root `assembleDebug` attempt also hit a KSP generated-file
  collision, so it was not used as fix evidence.

## User Advice

Before this fix, a full app restart could recover ADS-B because startup forced a
fresh Android connectivity read. After this fix, the ADS-B runtime should
recover on its own once Android reports usable internet again, even if the
callback flow was left stale.
