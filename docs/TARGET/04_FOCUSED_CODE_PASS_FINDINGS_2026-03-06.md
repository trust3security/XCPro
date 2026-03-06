# Focused Code Pass Findings and Corrections

Date
- 2026-03-06

Scope
- OGN target feature touchpoints only (details sheet, OGN preferences/use-case/viewmodel, map runtime overlays, lifecycle, tests).

Purpose
- Record what the first TARGET draft missed and how the production-grade phased IP corrects it.

## 1) What was missed in the first draft

1. Suppression-aware target clearing
- Missed: ownship suppression path (`suppressedTargetIds`) was not included in target-state policy.
- Risk: user can target an ownship-suppressed key and hold stale/invalid target state.
- Correction: coordinator/use-case must observe suppression flow and clear target when matched.

2. Ring layer hit-testing and ordering
- Missed: adding a visual ring can break marker taps if query layers do not include ring layer.
- Risk: taps on ring fail to open details sheet.
- Correction: include ring layer in hit-test query and layer-order lifecycle methods.

3. Style reload and map detach lifecycle for new overlay
- Missed: direct-line overlay lifecycle was not fully integrated into style recreation and detach cleanup paths.
- Risk: orphan layers, duplicate source IDs, stale render jobs.
- Correction: wire line overlay through OGN runtime delegate with initialize/cleanup/recreate coverage.

4. File-budget and growth constraints
- Missed: near-cap files were not accounted for in implementation slicing.
- Risk: `enforceRules` failures or hotspot growth.
- Correction: keep new logic in dedicated helper/new overlay classes; minimal additions in capped files.

5. Startup/session policy
- Missed: no explicit persistence policy for target key across process restart.
- Risk: stale target from prior session with no active traffic context.
- Correction: choose and document policy explicitly (recommended: session-local reset, aligned with SCIA).

6. OGN display-mode cadence integration
- Missed: target line/ring cadence was not explicitly tied to OGN display update mode throttling.
- Risk: uneven redraw cadence and p95 frame regressions.
- Correction: target visuals render under existing OGN delegate throttle contract.

7. Glider-only requirement enforcement
- Missed: requirement says OGN glider traffic, but first draft did not enforce glider-only toggle semantics.
- Risk: target toggle appears on non-glider traffic.
- Correction: show/enable target toggle only for glider-class markers.

## 2) Code evidence reviewed in focused pass

Primary evidence files
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntime.kt`
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeDomainPolicies.kt`
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
- `app/src/main/java/com/example/xcpro/SciaStartupResetter.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerOgnLifecycleTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTrafficSelectionTest.kt`

## 3) Production-grade corrections now reflected in the IP

- Suppression-aware target clearing is now a required phase gate.
- Ring hit-testing and lifecycle ordering are explicit implementation tasks.
- Direct-line overlay lifecycle and throttling are explicit delegate responsibilities.
- Session policy decision and startup reset integration are explicit tasks.
- File-budget constraints are explicitly called out in phase planning.
- SLO validation and rollback triggers are defined per impacted behavior.

## 4) Final output of this focused pass

- Updated production-grade phased implementation plan:
  - `docs/TARGET/02_IMPLEMENTATION_PLAN_OGN_TARGET_2026-03-06.md`
- Updated production-grade validation plan:
  - `docs/TARGET/03_TEST_VALIDATION_PLAN_OGN_TARGET_2026-03-06.md`

These two docs should be treated as the canonical implementation baseline for coding the feature.
