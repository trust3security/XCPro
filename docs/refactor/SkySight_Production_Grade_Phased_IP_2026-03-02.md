# SkySight Production-Grade Phased Implementation Plan (IP)

Date: 2026-03-02
Owner: XCPro Team
Status: In progress (Phase 2 production-ready + Phase 3 anchor cleanup update; required local gates passed on 2026-03-02)
Scope: SkySight forecast + satellite overlays, settings/auth hardening, z-order correctness, release-grade validation

Read first (required order):
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`

## 0) Objective

Raise SkySight slice quality from "implemented and usable" to "production-grade and release-defensible":
- deterministic runtime behavior under style reload and overlay toggles,
- explicit error semantics,
- hardened auth/credential behavior,
- stable cross-overlay z-order,
- fully documented and test-gated contracts.

## 1) Current Scorecard (Out of 100)

| Category | Current | Target | Gap |
|---|---:|---:|---:|
| Architecture boundary compliance | 90 | 95 | 5 |
| Runtime correctness (forecast + satellite) | 89 | 95 | 6 |
| Reliability/resilience | 87 | 94 | 7 |
| Auth and credential robustness | 73 | 93 | 20 |
| Network/API contract resilience | 80 | 93 | 13 |
| UI responsiveness risk | 82 | 92 | 10 |
| Test coverage on risky paths | 88 | 94 | 6 |
| Docs/tooling reliability | 81 | 94 | 13 |

Overall SkySight slice score: **92/100**  
Release target score: **94/100**

## 2) Open Findings to Close

1. Optional connected instrumentation gates remain pending for this change set.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Forecast/satellite user prefs | `ForecastPreferencesRepository` | `preferencesFlow` and dedicated flows | UI-local mutable preference mirrors |
| Forecast overlay resolved state | `ForecastOverlayRepository` | `Flow<ForecastOverlayUiState>` | Runtime policy-only copies as source of truth |
| Runtime map apply status/warnings | `MapOverlayManager` (runtime owner) | `StateFlow` runtime status | ViewModel/UI ad-hoc status state |
| Credentials | `ForecastCredentialsRepository` | repository API | duplicated storage backends without explicit policy |

### 3.2 Dependency Direction

Must remain:
`UI -> domain/use-cases -> data`

No MapLibre types in ViewModels/use-cases.

### 3.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| Forecast slot selection | Wall UTC | Provider contract |
| Satellite frame bucket (`YYYY/MM/DD/HHmm`) | Wall UTC | Provider contract |
| Animation stepping | Wall/UI timer | Visual-only runtime behavior |
| Replay domain timing | Replay | Must remain unchanged |

Forbidden:
- Monotonic vs wall comparisons
- Replay vs wall comparisons

## 4) Phased Plan

### Phase 0 - Baseline Lock and Contract Freeze

Goal:
- Lock current behavior and prevent regression while hardening.

Work:
- Refresh focused baseline tests for:
  - source-layer fallback behavior,
  - satellite frame order (`1,2,3,4,5,6,1,2,...`),
  - z-order priority (rain/satellite/forecast/blue overlay),
  - single non-wind selector behavior.

Files:
- `feature/map/src/test/java/com/trust3/xcpro/map/ForecastRasterOverlaySourceLayerFallbackTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/WeatherRainOverlayPolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/HotspotsOverlayPolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerWeatherRainTest.kt`

Exit criteria:
- Baseline tests pass and represent current intended behavior.

Phase score target: **86/100**

### Phase 1 - Auth and Credential Hardening

Goal:
- Remove silent security downgrade and move credential I/O off UI-facing synchronous API shape.

Work:
- Add explicit credential backend status (`encrypted`, `fallback-plaintext`) and surface as settings state.
- Convert credential load/save APIs to suspend/IO-dispatched path.
- Keep verify flow exception-safe and cancellation-safe.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastCredentialsRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettingsUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`

Tests:
- new/updated `ForecastCredentialsRepositoryTest`
- new/updated `ForecastSettingsViewModelTest`

Exit criteria:
- No silent credential fallback.
- No sync credential I/O path from UI events.
- Clear user-visible state when secure storage is unavailable.

Phase score target: **89/100**

### Phase 2 - Runtime Error Semantics Unification

Goal:
- Make warning/error state authoritative and non-duplicative.

Work:
- Normalize forecast/satellite runtime warnings and errors into one contract consumed by UI.
- Keep `CancellationException` propagation intact in repository/runtime paths.
- Ensure fatal/warning channels cannot duplicate the same message.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`

Tests:
- `ForecastOverlayRepositoryTest`
- `MapOverlayManagerSkySightSatelliteErrorTest`
- `MapOverlayManagerForecastWarningTest`

Exit criteria:
- One clear warning/error presentation path.
- Deterministic fatal vs warning behavior across primary/wind/satellite failures.

Phase score target: **91/100**

### Phase 3 - Z-Order Policy Consolidation

Goal:
- Make cross-overlay ordering explicit, minimal, and drift-resistant.

Work:
- Remove stale/dead anchor IDs (for removed secondary non-wind layers).
- Consolidate anchor policy constants and assert ownship/critical overlays remain visible.
- Re-verify thermal/rain/satellite/forecast/wind interaction order.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/map/SkySightSatelliteOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ForecastRasterOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`

Tests:
- `HotspotsOverlayPolicyTest`
- `WeatherRainOverlayPolicyTest`
- `MapOverlayManagerWeatherRainTest`

Exit criteria:
- No stale anchor references.
- Stable z-order with predictable ownship/top-layer behavior.

Phase score target: **92/100**

### Phase 4 - API Contract and Network Policy Hardening

Goal:
- Lock MapLibre host/header policy and SkySight integration contract via tests.

Work:
- Add focused policy tests for `SkySightMapLibreNetworkConfigurator` allowlist + `Origin` header behavior.
- Harden/validate evidence capture script assumptions against runtime contract.

Files:
- `feature/map/src/main/java/com/trust3/xcpro/map/SkySightMapLibreNetworkConfigurator.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/*NetworkConfigurator*Test.kt` (new)
- `scripts/integrations/capture_skysight_evidence.ps1`

Exit criteria:
- Header policy covered by deterministic tests.
- Evidence tooling contract aligned with runtime assumptions.

Phase score target: **93/100**

### Phase 5 - Docs and Pipeline Sync

Goal:
- Remove contract drift from docs used by reviewers/agents.

Work:
- Update SkySight contract docs to current truth:
  - history frames `1-6` (default `3`),
  - single non-wind selector model,
  - current z-order policy summary.
- Keep `PIPELINE.md` and tab docs consistent.

Files:
- `docs/ARCHITECTURE/PIPELINE.md` (if any additional drift remains)
- `docs/TABS/2Tab.md`
- `docs/refactor/SkySight_Comprehensive_Code_Pass_and_Phased_Implementation_Plan_2026-03-01.md` (status consolidation)

Exit criteria:
- No contradictory SkySight behavior statements across architecture docs.

Phase score target: **94/100**

### Phase 6 - Final Verification and Release Gate

Goal:
- Prove production readiness with mandatory checks and evidence-based rescore.

Required checks:
```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:
```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

Exit criteria:
- All required checks pass.
- No new architecture deviations introduced.
- Final SkySight score >= **94/100** with residual risks documented.

Phase score target: **95/100**

## 8) Phase 2 Production-Ready Update (2026-03-02)

Implemented:
- Repository warning/error dedupe: warning text is now filtered against fatal error text.
  - `feature/map/src/main/java/com/trust3/xcpro/forecast/ForecastOverlayRepository.kt`
- Overlay-manager stale runtime issue fix:
  - forecast runtime warning clears deterministically when map is unavailable or forecast is disabled.
  - satellite runtime error clears deterministically when satellite overlay is disabled while map is unavailable.
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
- UI message unification policy:
  - centralized warning/error merge + dedupe contract in `resolveSkySightUiMessages(...)`.
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/SkySightUiMessagePolicy.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
- Satellite-only reference-time policy made explicit and test-locked:
  - `ForecastOverlayRepository` satellite-only state keeps `selectedTimeUtcMs = nowWallMs`
    with no catalog/time-slot fetch.
  - `SkySightSatelliteOverlay` null reference resolves to near-live stepped bucket.
  - docs updated in `docs/ARCHITECTURE/PIPELINE.md`.

Tests added/updated:
- `feature/map/src/test/java/com/trust3/xcpro/forecast/ForecastOverlayRepositoryTest.kt`
  - `primaryDisabled_withWindTileAndLegendFailure_dedupesWarningAgainstError`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerForecastWarningTest.kt`
  - `setForecastOverlay_whenMapUnavailable_clearsStaleRuntimeWarning`
  - `setForecastOverlay_disabled_clearsWarningEvenWhenMapUnavailable`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapOverlayManagerSkySightSatelliteErrorTest.kt`
  - `setSkySightSatelliteOverlay_disabled_clearsErrorWhenMapUnavailable`
- `feature/map/src/test/java/com/trust3/xcpro/map/ui/SkySightUiMessagePolicyTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/forecast/ForecastOverlayRepositoryTest.kt`
  - `satelliteOnly_doesNotResolveForecastSelection` now also asserts selected-time reference source.
- `feature/map/src/test/java/com/trust3/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt`
  - `resolveBaseFrameEpochSec_withoutReference_usesNearLiveSteppedTime`

Verification:
- `./test-safe.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.forecast.ForecastOverlayRepositoryTest" --tests "com.trust3.xcpro.map.MapOverlayManagerForecastWarningTest" --tests "com.trust3.xcpro.map.MapOverlayManagerSkySightSatelliteErrorTest" --tests "com.trust3.xcpro.map.ui.SkySightUiMessagePolicyTest"` -> PASS
- `./gradlew enforceRules --no-daemon --no-configuration-cache` -> PASS
- `python scripts/arch_gate.py` -> PASS
- `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` -> PASS
- `./gradlew assembleDebug --no-daemon --no-configuration-cache` -> PASS

Phase 2 quality score after this update: **92/100**

## 9) Phase 3 Z-Order Drift Cleanup Update (2026-03-02)

Implemented:
- Removed stale legacy secondary forecast anchor namespace from weather overlay ordering policy.
  - `feature/map/src/main/java/com/trust3/xcpro/map/WeatherRainOverlay.kt`
- Added regression assertions to ensure removed secondary anchors are not queried in weather overlay policy path.
  - `feature/map/src/test/java/com/trust3/xcpro/map/HotspotsOverlayPolicyTest.kt`

Verification:
- `./test-safe.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.HotspotsOverlayPolicyTest" --tests "com.trust3.xcpro.map.WeatherRainOverlayPolicyTest"` -> PASS
- `python scripts/arch_gate.py` -> PASS
- `./gradlew enforceRules --no-daemon --no-configuration-cache` -> PASS
- `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` -> PASS
- `./gradlew assembleDebug --no-daemon --no-configuration-cache` -> PASS

Phase 3 quality score after this update: **93/100**

## 5) Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| Credential hardening affects legacy devices | High | explicit degraded-mode UX + tests for fallback path |
| Error semantics refactor regresses user messaging | Medium | lock warning/error tests before refactor |
| Z-order cleanup hides a layer unexpectedly | High | keep policy tests + manual map sanity pass |
| Network policy tests become brittle | Medium | isolate host/header policy with minimal seams |
| Docs drift reappears | Medium | enforce doc sync in final gate checklist |

## 6) Acceptance Gates

1. No violations of `ARCHITECTURE.md` and `CODING_RULES.md`.
2. SSOT ownership remains singular and explicit.
3. Time-base handling remains explicit and non-mixed.
4. Replay behavior remains deterministic and unchanged.
5. No manager/controller bypass reintroduced in UI layer.
6. Required verification commands pass.
7. `KNOWN_DEVIATIONS.md` updated only if explicitly approved.

## 7) Recommended Execution Order

1. Phase 0
2. Phase 1
3. Phase 2
4. Phase 3
5. Phase 4
6. Phase 5
7. Phase 6

This order keeps the highest-risk correctness/security work ahead of docs-only cleanup and final release gating.
