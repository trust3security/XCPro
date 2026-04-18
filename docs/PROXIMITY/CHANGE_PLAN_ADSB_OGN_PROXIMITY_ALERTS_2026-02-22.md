# CHANGE_PLAN_ADSB_OGN_PROXIMITY_ALERTS_2026-02-22.md

## 0) Metadata

- Title: ADS-B ownship proximity color policy hardening (phone/glider ownship only)
- Owner: Codex
- Date: 2026-02-22
- Clarified requirement date: 2026-02-23
- Issue/PR: TBD
- Status: Complete (Phase 0-2 implemented; required verification passed on 2026-02-23; optional connected tests remain device-dependent)

Note:
- File name is retained for continuity/history.
- Active behavior target is ownship-to-ADS-B only.

## 1) Scope

- Problem statement:
  - Requirement is explicitly ownship-relative ADS-B proximity using current phone/glider position.
  - Do not compute ADS-B proximity against OGN traffic.
- Why now:
  - Previous draft targeted ADS-B-to-OGN behavior; this has been explicitly rejected.
- In scope:
  - Keep ownship-distance based ADS-B icon color policy.
  - Keep emergency override semantics.
  - Keep neutral fallback when ownship reference is unavailable.
  - Add tests/docs to prevent future drift into OGN-coupled proximity logic.
- Out of scope:
  - Any ADS-B-to-OGN evaluator or alert policy.
  - OGN-based color tiers or advisories.
  - ADS-B provider polling/network contract changes.
  - OGN protocol/repository changes.
- User-visible impact:
  - ADS-B icon colors remain based on ownship-to-ADS-B proximity only.

### 1.1 Active Proximity Policy (SI)

All thresholds are SI meters internally.

- `GREEN`:
  - ownship-to-ADS-B distance `> 5,000 m`
- `AMBER`:
  - ownship-to-ADS-B distance `> 2,000 m` and `<= 5,000 m`
- `RED`:
  - ownship-to-ADS-B distance `<= 2,000 m`
- `EMERGENCY`:
  - existing emergency collision-risk flag remains highest priority.

When ownship reference is unavailable:
- color falls back to neutral.
- emergency collision-risk classification is disabled.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Raw ADS-B targets | `AdsbTrafficRepository` | `StateFlow<List<AdsbTrafficUiModel>>` | UI-owned business-policy mirrors |
| Ownship reference for ADS-B (origin/altitude) | `AdsbTrafficRepository` runtime inputs | Internal repository state + derived UI model fields | Separate VM/UI-owned ownship-distance authorities |
| Derived ownship-to-ADS-B distance/bearing + emergency flag | `AdsbTrafficStore` via repository selection | `AdsbTrafficUiModel` fields | OGN-coupled proximity overlays for ADS-B coloring |
| Icon color mapping from ADS-B model fields | `AdsbProximityColorPolicy` + `AdsbGeoJsonMapper` | MapLibre expression + GeoJSON properties | UI-side alternative tier policy |

### 2.2 Dependency Direction

Confirmed flow remains:

`UI -> ViewModel -> use-case -> repository`

- Modules/files touched:
  - `feature/map` ADS-B policy tests/mappers/ViewModel tests.
  - `docs/ADS-b/ADSB.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Boundary risk:
  - Accidental introduction of ADS-B-to-OGN policy in VM/UI layer.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Proximity policy source-of-truth | Prior draft proposed ADS-B-to-OGN evaluator | Keep existing ownship-distance policy owner (`AdsbTrafficStore` + `AdsbProximityColorPolicy`) | Requirement is ownship-only | Tests + docs alignment |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| N/A | No ADS-B-to-OGN path should exist | Keep explicit prohibition in docs/tests | Phase 0-2 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| ADS-B target freshness (`ageSec`) | Monotonic-derived | Derived from monotonic receive timestamps |
| Ownship origin update cadence | Monotonic/live sensor-driven | Stable live reference feed |
| UI toast display time | Wall/UI runtime | Rendering only |

Explicitly forbidden comparisons:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Keep heavy repository selection math off main (existing repository path).
- Primary cadence/gating sensor:
  - ADS-B repository poll/update cadence + ownship updates.
- Hot-path latency budget:
  - Keep existing ADS-B selection/render budget; no new ADS-B x OGN pairwise path.

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No new randomness introduced by this plan.
- Replay/live divergence rules:
  - unchanged; this plan does not add replay/task/scoring coupling.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| OGN coupling accidentally reintroduced into ADS-B color policy | `ARCHITECTURE.md` dependency + SSOT rules | Review + tests | `AdsbProximityColorPolicyTest`, `MapScreenViewModelTest` |
| Threshold drift from `2 km/5 km` ownship policy | SI rules in architecture/coding rules | Unit tests | `AdsbProximityColorPolicyTest` |
| Ownship-missing fallback regression | ADS-B contract correctness | Unit tests | `AdsbGeoJsonMapperTest`, policy tests |
| Selected details semantic confusion | UI correctness/docs | UI text review + docs update | `AdsbMarkerDetailsSheet.kt`, `docs/ADS-b/ADSB.md` |

## 3) Data Flow (Before -> After)

Before:

`AdsbTrafficRepository.targets -> metadata merge -> AdsbTrafficOverlay -> ownship-distance color expression`

`OgnTrafficRepository.targets -> OgnTrafficOverlay (independent path)`

After:

`No architectural flow change.`

`AdsbTrafficRepository.targets -> metadata merge -> AdsbTrafficOverlay -> ownship-distance color expression`

`OgnTrafficRepository.targets -> OgnTrafficOverlay (independent path)`

## 4) Implementation Phases

### Phase 0 - De-scope and contract reset

- Goal:
  - Remove ADS-B-to-OGN implementation intent from this plan and lock ownship-only requirement.
- Files to change:
  - `docs/PROXIMITY/CHANGE_PLAN_ADSB_OGN_PROXIMITY_ALERTS_2026-02-22.md`
- Tests to add/update:
  - None.
- Exit criteria:
  - Plan no longer requires `AdsbOgn*` classes or OGN-coupled proximity behavior.

### Phase 1 - Policy test hardening

- Goal:
  - Harden tests around current ownship-distance policy and fallback semantics.
- Files to change:
  - `feature/map/src/test/java/com/trust3/xcpro/map/AdsbProximityColorPolicyTest.kt`
  - `feature/map/src/test/java/com/trust3/xcpro/map/AdsbGeoJsonMapperTest.kt`
- Tests to add/update:
  - Boundary assertions around `2,000 m` and `5,000 m`.
  - Neutral fallback when ownship reference is unavailable.
  - Emergency override precedence.
- Exit criteria:
  - Tests explicitly protect ownship-only behavior.

### Phase 2 - ViewModel/details semantics and docs alignment

- Goal:
  - Ensure ViewModel/details/docs clearly communicate ownship-relative semantics.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` (if selected-target source semantics need clarification)
  - `feature/map/src/main/java/com/trust3/xcpro/adsb/AdsbMarkerDetailsSheet.kt` (optional label clarification)
  - `docs/ADS-b/ADSB.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Tests to add/update:
  - `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt` ownship-source semantic checks.
- Exit criteria:
  - No OGN dependency in ADS-B proximity policy path.
  - Docs consistently describe ownship-only behavior.

## 5) Test Plan

- Unit tests:
  - `5,001 m -> green`
  - `5,000 m -> amber`
  - `2,001 m -> amber`
  - `2,000 m -> red`
  - emergency override precedence
  - ownship reference missing -> neutral
- Regression tests:
  - VM path remains independent of OGN traffic for ADS-B color semantics.
- UI/instrumentation tests (if needed):
  - Optional details-sheet text regression if label changes.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Future drift back to ADS-B-to-OGN logic | Medium | Keep ownship-only tests and docs explicit | XCPro Team |
| Ambiguous details text (`Distance`) misread as OGN-relative | Low | Clarify label/docs as ownship-relative | XCPro Team |
| Plan/doc not tracked in git | Medium | Ensure `docs/PROXIMITY/` is committed | XCPro Team |

## 7) Acceptance Gates

- No architecture/coding-rule violations.
- ADS-B proximity policy remains ownship-relative only.
- No `AdsbOgn*` evaluator/policy classes introduced for this feature scope.
- `docs/ADS-b/ADSB.md` and `docs/ARCHITECTURE/PIPELINE.md` aligned with ownship-only behavior.
- Required verification commands pass.

## 8) Rollback Plan

- What can be reverted independently:
  - Any test/doc hardening changes.
- Recovery steps if regression is detected:
  1. Revert policy/docs/tests to last known stable ownship-distance baseline.
  2. Re-run `enforceRules`, `testDebugUnitTest`, `assembleDebug`.
  3. Keep ADS-B semantics explicitly ownship-relative in docs.

## 9) Superseded Content Note

- Prior versions of this file included ADS-B-to-OGN proximity evaluation phases.
- That direction is intentionally superseded by clarified requirement:
  - `phone/glider ownship position -> ADS-B proximity only`.

## 10) Execution and Verification Snapshot (2026-02-23)

- Implemented:
  - Phase 1 test hardening for ownship-distance thresholds/fallback semantics.
  - Phase 2 ViewModel/details semantics + docs alignment for ownship-only ADS-B proximity.
- Required verification:
  - `./gradlew enforceRules` -> PASS
  - `./gradlew testDebugUnitTest` -> PASS
  - `./gradlew assembleDebug` -> PASS
- Optional follow-up (when device/emulator is available):
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
