# KNOWN_DEVIATIONS.md

Audit date: 2026-01-27

This file lists known deviations from ARCHITECTURE.md and CODING_RULES.md.
Each entry must include an issue ID, owner, and expiry date.

## Current deviations

1) MapScreenViewModel uses platform APIs/managers instead of use-cases
- Rule: ViewModels depend on use-cases only; no platform APIs or I/O in ViewModels.
- Issue: RULES-20260204-01
- Owner: XCPro Team
- Expiry: 2026-03-04
- Notes: Uses Context + WaypointLoader for file I/O; depends on TaskManagerCoordinator,
  VarioServiceManager, LevoVarioPreferencesRepository, CardPreferences, and MapOrientationManagerFactory.

2) TaskSheetViewModel holds UI map handle and constructs dependencies
- Rule: ViewModels must not reference UI types; dependencies must be injected; use-cases only.
- Issue: RULES-20260204-02
- Owner: XCPro Team
- Expiry: 2026-03-04
- Notes: Holds MapLibreMap; depends on TaskManagerCoordinator; constructs TaskSheetUseCase.

3) IgcReplayViewModel depends on controller and platform logging
- Rule: ViewModels depend on use-cases only; no platform APIs in ViewModels.
- Issue: RULES-20260204-03
- Owner: XCPro Team
- Expiry: 2026-03-04
- Notes: Injects IgcReplayController directly and logs via android.util.Log.

4) FlightDataViewModel constructs Clock/use-case internally
- Rule: Dependencies must be injected; ViewModels should not construct use-cases or clocks.
- Issue: RULES-20260204-04
- Owner: XCPro Team
- Expiry: 2026-03-04
- Notes: Uses DefaultClockProvider and constructs FlightCardsUseCase when no override provided.

5) TaskFilesUseCase uses wall time directly
- Rule: Domain/use-case logic must use injected Clock; no Date/System time direct calls.
- Issue: RULES-20260204-05
- Owner: XCPro Team
- Expiry: 2026-03-04
- Notes: Uses Date() for filename timestamps in TaskFilesUseCase.

6) AAT map editing uses SystemClock directly
- Rule: Domain/use-case logic must use injected Clock; no SystemClock/System.currentTimeMillis direct calls.
- Issue: RULES-20260204-06
- Owner: XCPro Team
- Expiry: 2026-03-04
- Notes: Uses SystemClock.elapsedRealtime() in AAT edit session timing and tap timestamps
  (AATEditModeState, AATMapCoordinateConverter, AATMapInteractionHandler). Needs injected Clock/nowMs.

## Verification

Last verified: 2026-01-28 (unit tests re-run after MapOverlayWidgetGesturesTest moved to Robolectric)
- Commands: ./gradlew testDebugUnitTest (re-run), ./gradlew enforceRules, ./gradlew lintDebug, ./gradlew assembleDebug, powershell -File scripts/ci/enforce_rules.ps1, preflight.bat

## Resolved deviations

1) Timebase usage in domain/fusion
- Rule: injected clock only; no System.currentTimeMillis in domain or fusion.
- Resolved: 2026-01-27
- Notes: Removed direct system time usage; injected Clock or explicit timestamps.

2) DI: pipeline constructed inside manager
- Rule: core pipeline components must be injected.
- Resolved: 2026-01-27
- Notes: Sensor fusion pipeline provided via Hilt; managers no longer construct it directly.

3) ViewModel purity
- Rule: ViewModels must not touch SharedPreferences or UI types.
- Resolved: 2026-01-27
- Notes: SharedPreferences moved to repositories/use-cases; Compose types removed from ViewModels; tests updated.

4) Compose lifecycle collection
- Rule: use collectAsStateWithLifecycle for UI state.
- Resolved: 2026-01-27
- Notes: Replaced collectAsState with collectAsStateWithLifecycle in UI layers.

5) Vendor string policy
- Rule: no "xcsoar"/"XCSoar" literals in production Kotlin source.
- Resolved: 2026-01-27
- Notes: Vendor literals removed/renamed in production Kotlin.

6) ASCII hygiene
- Rule: no non-ASCII characters in production Kotlin source.
- Resolved: 2026-01-27
- Notes: Non-ASCII characters replaced with ASCII equivalents in production Kotlin.
