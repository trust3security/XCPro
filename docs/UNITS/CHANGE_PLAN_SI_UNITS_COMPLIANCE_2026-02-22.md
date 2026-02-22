# Change Plan: SI Units Compliance

Date: 2026-02-22
Status: Draft for execution
Scope: `feature/map`, `core/common`, `dfcards-library`, task modules

## Problem Statement
The codebase is mixed-mode for units. Core flight pipelines are SI-first, but legacy task/AAT/racing paths still compute and compare values in km/km/h or mixed km-vs-meter semantics. This creates correctness risk and makes maintenance harder.

## Goal
Guarantee that all internal calculations use SI units:
- Distance: meters
- Speed: m/s
- Vertical speed: m/s
- Altitude: meters
- Pressure: hPa

Non-SI units remain only for:
- User display formatting.
- Explicit third-party protocol boundaries.

## Non-Goals
- No UX redesign of unit selector in this change.
- No protocol-level behavior changes to external providers beyond boundary adapters.
- No broad refactor of unrelated features.

## Constraints
- Preserve MVVM + UDF + SSOT layering.
- Keep replay deterministic.
- Keep dependency direction and module boundaries.
- Use explicit conversion boundaries; no hidden global conversion behavior.

## Baseline (Before Changes)
- SI wrappers/converters exist and are strong in common layers.
- Flight/fusion is mostly SI-clean.
- Task/AAT/Racing still contains mixed km/km/h semantics and at least one meter-vs-km mismatch risk.

## Re-pass Critical Defects (2026-02-22)
The seventh deep pass confirmed active correctness bugs that must be fixed first:
1. `AATPathOptimizerSupport` compares km path distances against meter-labeled target/tolerance values.
2. `AATPathOptimizer` computes meter-labeled target distances then compares to km path distances.
3. `AATFlightPathValidator` compares km distances to meter thresholds for start/finish checks.
4. `AATTaskQuickValidationEngine` compares km distances to meter thresholds across area/start/finish checks.
5. `AATTaskSafetyValidator` divides an already-km value by 1000.
6. `CircleAreaCalculator` and `SectorAreaCalculator` compare km distances to meter radii.
7. `AATTaskPerformanceSupport` writes km values into a field documented as meters.
8. `AATDistanceCalculator` publishes km values into `AATTaskDistance` fields documented as meters.
9. `AATDistanceCalculator` clamps a meter target (`expectedSpeed * hours * 1000`) using km min/max distances.
10. `AATTaskDisplayGeometryBuilder` forwards meter radii/line lengths into `AATMathUtils.calculatePointAtBearing` (km API).
11. `SectorAreaGeometrySupport` has wide km-vs-meter contamination in boundary math and point generation.
12. `CircleAreaCalculator` unit contamination extends beyond `isInsideArea` into intersections and boundary computations.
13. Racing optimal start-line crossing path passes `gateWidth` in km into a meter-based geometry API (`TaskManagerCoordinator` -> `RacingTaskManager` -> `RacingGeometryUtils.calculateOptimalLineCrossingPoint`), shrinking line width by 1000x in that flow.
14. AAT start/finish cylinder radius is entered/stored as radius but is divided by 2 in renderer/geometry/validation bridge paths (`AATTaskRenderer`, `AATGeometryGenerator`, `AATValidationBridge`), causing 2x scale error.
15. Replay runtime interpolation assigns `MovementSnapshot.distanceMeters` from `speedMs` (`ReplayRuntimeInterpolator`), and `ReplayHeadingResolver` compares that value against `minDistanceM`, creating a meters-vs-m/s contract violation in heading gating.
16. Re-pass #7 scope delta: `AATTaskQuickValidationEngine.validateFinish` (`AATMathUtils.calculateDistance` at line 202) compares km output against meter contracts/tolerances (`lineLength`/`radius` with `+ 100.0`/`+ 50.0`), and was not explicitly captured in earlier finding line coverage.

## Implementation Phases

### Phase 0: Contract Lock and Guard Rails
1. Freeze target contracts (internal SI only) per module.
2. Add/strengthen naming conventions (`*Meters`, `*Ms`, `*Hpa`, `*C`).
3. Add temporary lint/search checks to detect new non-SI internal calculations.

Definition of done:
- Contract doc approved.
- New checks can fail PRs for obvious regressions.

### Phase 1: Task Domain Contract Normalization
1. Standardize coordinator/domain contracts to meters for distances.
2. Keep legacy km APIs only as transitional wrappers (deprecated).
3. Update callers to consume meter contracts.

Definition of done:
- `TaskSheetCoordinatorUseCase` and core task distance APIs expose SI internally.
- Transitional wrappers are isolated and labeled for removal.

### Phase 2: AAT Legacy Calculator Correction
1. Normalize AAT math utilities and validators to one internal distance unit (meters).
2. Fix mixed comparisons where km values are compared to meter thresholds.
3. Explicitly fix `AATTaskQuickValidationEngine` start/finish threshold checks (`validateStart`, `validateFinish`) to compare meter values against meter contracts.
4. Correct area calculators that pass meter radii into km-based geodesic routines.
5. Add unit tests for each corrected path.

Definition of done:
- No km-vs-meter comparisons remain in AAT internals.
- `AATTaskQuickValidationEngine` start/finish validations are meter-correct and covered by regression tests.
- Validation thresholds behave correctly with known fixtures.

### Phase 3: Racing Legacy Normalization
1. Convert racing internal distance contracts to meters.
2. Keep any km formatting only in UI/presentation layers.
3. Add tests for geometry/distance invariants.

Definition of done:
- Racing manager/coordinator internal distance calculations are SI.
- Output formatting remains unchanged to users.

### Phase 4: Boundary Adapter Hardening
1. ADS-B/OGN/replay/polar boundaries must explicitly convert once at ingress/egress.
2. Add adapter-level tests to prove internal SI values.
3. Remove scattered ad-hoc conversion code where possible.

Definition of done:
- Boundary conversion points are centralized and documented.
- Internal flows remain SI from boundary inward.

### Phase 5: Cleanup and Deprecation Removal
1. Remove deprecated km-based internal methods introduced for transition.
2. Rename any ambiguous symbols.
3. Final compliance sweep and doc update.

Definition of done:
- No transitional km internal APIs remain.
- Compliance status moves to Compliant.

## Acceptance Criteria
- All internal distance calculations are meters end-to-end.
- All internal speed calculations are m/s end-to-end.
- Display unit conversion only happens through unit formatter/converter boundary paths.
- Replay determinism preserved.
- Existing feature behavior preserved except fixed unit bugs.

## Test Strategy
- Unit tests for conversion boundaries.
- Unit tests for task/AAT/racing distance math correctness.
- Regression tests for OGN/ADS-B selection and display distance.
- Required verification commands from repo policy.

## Rollout Strategy
- Ship behind incremental PRs by phase.
- Land high-risk AAT fixes first with tests.
- Keep temporary compatibility wrappers for one release cycle max.

## Exit Criteria
- Final re-pass reports no internal non-SI calculations except documented protocol boundaries.
- Required test gates pass.
- Docs updated and synced.
