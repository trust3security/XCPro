# Production Validation Plan: OGN Target Feature

Date
- 2026-03-06

Purpose
- Define production-grade test coverage and rollout evidence for OGN target ring + direct line behavior.

## 1) Impacted SLO IDs

Mandatory
- `MS-UX-01`: map motion smoothness with overlays
- `MS-UX-03`: marker stability and anti-jump behavior
- `MS-UX-04`: overlay z-order stability and no redundant reorder churn
- `MS-ENG-01`: overlay apply duration p95

Conditional
- `MS-ENG-09` if recomposition pressure increases
- `MS-ENG-10` if cadence ownership changes

## 2) Required verification commands

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Connected tests when available

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 3) Coverage matrix

| Area | Test type | Must prove |
|---|---|---|
| Target preference SSOT | unit | enabled/key persistence, normalization, clear semantics |
| Startup reset policy | unit/integration | target state reset on fresh process start (if session-local policy) |
| ViewModel target resolution | unit | key alias matching resolves canonical target; unresolved returns null |
| Suppression hygiene | unit | target key clears when selected target becomes suppressed ownship |
| Coordinator mutation policy | unit | coalesced writes, overlay auto-enable behavior, error-to-toast path |
| OGN details sheet | unit/compose | target toggle emits intent and glider-only visibility rule |
| OGN ring layer | unit | `is_target` mapping, ring layer lifecycle, bring-to-front ordering |
| Ring hit-testing | unit/integration | taps on ring still resolve aircraft selection |
| Target line overlay | unit | valid endpoint render and invalid/stale clear behavior |
| OGN delegate throttle | unit | target line/ring obey display-update mode cadence |
| Style reload lifecycle | integration-like unit | ring + line reinit after style change, no orphan layers |
| Map detach lifecycle | integration-like unit | pending jobs canceled, no stale renders after detach |

## 4) Concrete tests to add/extend

Preferences and startup
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepositoryTest.kt`
- app startup reset tests for target keys (if reset policy selected).

ViewModel/coordinator
- `feature/map/src/test/java/com/example/xcpro/map/MapScreenViewModelTrafficSelectionTest.kt`
- add cases:
  - target selection persists independent of details-sheet selection
  - suppression-driven clear
  - replace-target behavior

- add/extend coordinator tests (`HotspotsOverlayPolicyTest` or dedicated file)
- add cases:
  - target toggle coalescing
  - target-on auto-enables OGN overlay when required

Details sheet
- `feature/map/src/test/java/com/example/xcpro/ogn/OgnMarkerDetailsSheetTest.kt`
- extend with target toggle output/visibility rules.

Overlay runtime
- extend `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerOgnLifecycleTest.kt`
- add:
  - target line overlay lifecycle
  - ring layer lifecycle
  - style reload and detach coverage

- add new tests:
  - `feature/map/src/test/java/com/example/xcpro/map/OgnTargetLineOverlayTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/OgnTrafficOverlayTargetRingTest.kt`

## 5) Manual scenarios

1. Tap glider marker, enable target, confirm ring + line appear immediately.
2. Tap second glider, enable target, confirm previous target visuals clear.
3. Disable target, confirm ring + line clear.
4. Disable OGN overlay, confirm target visuals clear and no crash.
5. Change map style while target is active, confirm visuals recover exactly once.
6. Pan/zoom rapidly with target active, confirm no flicker/order popping.
7. If ownship suppression identity matches targeted key, confirm target auto-clears.

## 6) Artifact/evidence contract

For impacted SLO IDs attach:
- baseline and post-change metrics
- artifact path under `artifacts/mapscreen/<phase>/<package-id>/<timestamp>/`
- pass/fail summary by SLO ID

## 7) Rollback triggers

Rollback candidate if any occur:
- mandatory `MS-UX-*` threshold miss
- `MS-ENG-01` miss on target-enabled scenarios
- ring/line orphan layers after style reload/detach
- hit-testing regression when tapping target ring
- architecture gate failures (`arch_gate.py` or `enforceRules`)
