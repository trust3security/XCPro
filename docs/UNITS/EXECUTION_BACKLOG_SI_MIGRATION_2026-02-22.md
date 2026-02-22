# Execution Backlog: SI Migration

Date: 2026-02-22
Status: Updated after Re-pass #8

## Priority Legend
- P0: correctness bug risk
- P1: high-value consistency
- P2: cleanup/hardening

## Backlog

1. P0 - Fix `AATPathOptimizerSupport` km-vs-meter mismatch (`OPTIMIZATION_TOLERANCE_METERS` and path distance unit).
2. P0 - Fix `AATPathOptimizer` target distance unit consistency (`targetDistanceMeters` math vs km path functions).
3. P0 - Fix `AATFlightPathValidator` start/finish checks (km distance compared to meter thresholds).
4. P0 - Fix `AATTaskQuickValidationEngine` distance checks (km compared to meter thresholds), including `validateStart` (`AATTaskQuickValidationEngine.kt:174`) and `validateFinish` (`AATTaskQuickValidationEngine.kt:202`).
5. P0 - Fix `AATTaskSafetyValidator` double conversion (`distance / 1000` on km result).
6. P0 - Fix `CircleAreaCalculator` and `SectorAreaCalculator` distance/radius unit mismatch.
7. P1 - Fix `AATTaskPerformanceSupport` assignment to `AATAreaAchievement.distanceFromCenter` (meters field).
8. P1 - Normalize AAT manager/coordinator internal distance contracts to meters.
9. P1 - Normalize Racing manager/coordinator internal distance contracts to meters.
10. P1 - Rename ambiguous task distance APIs with explicit unit suffixes.
11. P1 - Add fixture-based tests for AAT validation/path optimization boundary behavior, including explicit `AATTaskQuickValidationEngine` start/finish threshold cases.
12. P1 - Add fixture-based tests for racing/aat segment and total distance invariants.
13. P1 - Add boundary adapter tests for ADS-B/OGN/replay conversions.
14. P1 - Fix replay movement snapshot contract: `MovementSnapshot.distanceMeters` must store distance in meters (not speed in m/s) in `ReplayRuntimeInterpolator`, and add heading-gating regression tests for `ReplayHeadingResolver`.
15. P1 - Fix distance-circles output boundary to use `UnitsPreferences`/`UnitsFormatter` (remove hard-coded `km`/`m` labels in `DistanceCirclesCanvas`).
16. P1 - Fix task UI distance outputs (`TaskStatsSection`, minimized indicator, racing selector distance text) to use selected distance units instead of hard-coded `km`.
17. P2 - Decide polar model storage contract (keep km/h as boundary data or migrate to SI storage).
18. P2 - Remove transitional km wrappers after caller migration.
19. P2 - Add static checks to block new internal non-SI math in domain paths and hard-coded production distance-unit labels.
20. P2 - Final doc sync and compliance sign-off.

## Suggested PR Split
1. PR-1: AAT P0 correctness fixes + tests.
2. PR-2: Task manager/coordinator SI contract normalization.
3. PR-3: Racing SI normalization + tests.
4. PR-4: Boundary adapter hardening + polar contract decision.
5. PR-5: Cleanup/deprecation removal + compliance sign-off.
