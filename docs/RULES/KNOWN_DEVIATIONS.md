# KNOWN_DEVIATIONS.md

Audit date: 2026-02-04

This file lists known deviations from ARCHITECTURE.md and CODING_RULES.md.
Each entry must include an issue ID, owner, and expiry date.

## Current deviations

None.

## Verification

Last verified: 2026-02-04
- Commands: ./gradlew enforceRules, ./gradlew testDebugUnitTest, ./gradlew lintDebug, ./gradlew assembleDebug

## Resolved deviations

1) MapScreenViewModel uses platform APIs/managers instead of use-cases
- Rule: ViewModels depend on use-cases only; no platform APIs or I/O in ViewModels.
- Issue: RULES-20260204-01
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: MapScreenViewModel now depends on use-case wrappers; Context/WaypointLoader moved to MapWaypointsUseCase; Log removed.

2) TaskSheetViewModel holds UI map handle and constructs dependencies
- Rule: ViewModels must not reference UI types; dependencies must be injected; use-cases only.
- Issue: RULES-20260204-02
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: MapLibreMap removed from VM; TaskSheetUseCase and TaskSheetCoordinatorUseCase injected.

3) IgcReplayViewModel depends on controller and platform logging
- Rule: ViewModels depend on use-cases only; no platform APIs in ViewModels.
- Issue: RULES-20260204-03
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: IgcReplayUseCase added and injected; Log removed from VM.

4) FlightDataViewModel constructs Clock/use-case internally
- Rule: Dependencies must be injected; ViewModels should not construct use-cases or clocks.
- Issue: RULES-20260204-04
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: FlightCardsUseCaseFactory injected; VM no longer constructs clock/use-case.

5) TaskFilesUseCase uses wall time directly
- Rule: Domain/use-case logic must use injected Clock; no Date/System time direct calls.
- Issue: RULES-20260204-05
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: TaskFilesUseCase now uses injected Clock for timestamp formatting.

6) UI imports sensor/data models directly
- Rule: UI code never imports data repositories or sensor/data models directly.
- Issue: RULES-20260204-06
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: Map UI now uses UI-safe models mapped in ViewModel.

7) Un-gated println logging in production paths
- Rule: No logs in tight loops; logs must not be required for correctness; avoid logging location data in release builds.
- Issue: RULES-20260204-07
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: println removed from production sources.

8) Global mutable feature flags via Kotlin object singletons
- Rule: No hidden singletons holding mutable state.
- Issue: RULES-20260204-08
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: MapFeatureFlags and TaskFeatureFlags are injected @Singleton classes.

9) Default wall-time usage in replay parser without injected clock
- Rule: Domain logic should use injected time sources; avoid implicit wall time.
- Issue: RULES-20260204-09
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: IgcParser injected with Clock; default date derived from injected time.

10) AAT map editing uses SystemClock directly
- Rule: Domain/use-case logic must use injected Clock; no SystemClock/System.currentTimeMillis direct calls.
- Issue: RULES-20260204-10
- Owner: XCPro Team
- Resolved: 2026-02-04
- Notes: AAT edit state, tap timestamps, and overlays now use injected Clock.

11) Timebase usage in domain/fusion
- Rule: injected clock only; no System.currentTimeMillis in domain or fusion.
- Resolved: 2026-01-27
- Notes: Removed direct system time usage; injected Clock or explicit timestamps.

12) DI: pipeline constructed inside manager
- Rule: core pipeline components must be injected.
- Resolved: 2026-01-27
- Notes: Sensor fusion pipeline provided via Hilt; managers no longer construct it directly.

13) ViewModel purity
- Rule: ViewModels must not touch SharedPreferences or UI types.
- Resolved: 2026-01-27
- Notes: SharedPreferences moved to repositories/use-cases; Compose types removed from ViewModels; tests updated.

14) Compose lifecycle collection
- Rule: use collectAsStateWithLifecycle for UI state.
- Resolved: 2026-01-27
- Notes: Replaced collectAsState with collectAsStateWithLifecycle in UI layers.

15) Vendor string policy
- Rule: no "xcsoar"/"XCSoar" literals in production Kotlin source.
- Resolved: 2026-01-27
- Notes: Vendor literals removed/renamed in production Kotlin.

16) ASCII hygiene
- Rule: no non-ASCII characters in production Kotlin source.
- Resolved: 2026-01-27
- Notes: Non-ASCII characters replaced with ASCII equivalents in production Kotlin.
