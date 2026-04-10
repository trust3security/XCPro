# SkySight 94+ Phased Implementation Plan (IP)

Date: 2026-03-04
Owner: XCPro Team
Status: Updated after comprehensive repass #8 (SkySight slice)
Scope: Raise every SkySight score category to >=94 (hard gate) and overall slice to >=95

## 0A) 2026-03-04 Comprehensive Repass Refresh (Authoritative)

This section is the active baseline for execution sequencing and score tracking.
Older baselines and phase estimates below are kept as historical context.

Latest baseline (2026-03-04):
- Architecture boundary compliance: 92
- Runtime correctness (forecast + satellite): 84
- Reliability/resilience: 86
- Auth and credential robustness: 66
- Network/API contract resilience: 78
- UI responsiveness risk: 88
- Test coverage on risky paths: 85
- Docs/tooling reliability: 84
- Overall SkySight slice: 83

Newly confirmed gaps from repass #8:
1. Password trimming corrupts valid credentials on save/load/UI pre-submit path.
   - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt`
   - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`
2. SkySight URL contract validates hosts but does not enforce `https` scheme.
   - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightHttpContract.kt`
3. Forecast vector-fill legend/color continuity bug:
   - Existing fill layer keeps stale color expression when tile changes and legend fetch is absent.
   - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlayRuntime.kt`
4. Source-layer fallback false-positive risk:
   - Fallback decision based on a single camera-center rendered-feature query.
   - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlayRuntime.kt`
5. Satellite z-order anchor gap for wind BARB layers:
   - Satellite anchor list omits `forecast-*-wind-barb-layer-*`.
   - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`
6. Dual-rain arbitration gap:
   - SkySight non-wind rain (`accrain`) and RainViewer can both be enabled without explicit composition policy.
   - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/ui/MapWeatherOverlayEffects.kt`
7. Missing direct tests for `ForecastOverlayRuntimeEffects` transition policy (`loading` guard vs clear/apply sequencing).
   - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
8. Contract drift risk:
   - Host/origin policy duplicated between request contract and MapLibre network configurator.
   - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightHttpContract.kt`
   - `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt`

Phased execution refresh (production-grade hard path):
- Phase A (Security/Auth gate): remove password trimming, enforce HTTPS contract, add focused auth/credential regression tests.
  - Expected lift: Auth 66 -> 93, Network 78 -> 90, Reliability 86 -> 90.
- Phase B (Runtime correctness gate): fix legend continuity behavior, harden source-layer fallback probe strategy, add BARB z-order anchors, define single-rain authority policy.
  - Expected lift: Runtime 84 -> 95, Reliability 90 -> 94, UI 88 -> 94.
- Phase C (Integration and transition tests gate): add `ForecastOverlayRuntimeEffects` transition tests, add z-order regression tests (ARROW/BARB), add policy drift tests between SkySight HTTP and MapLibre network config.
  - Expected lift: Tests 85 -> 95, Docs/tooling 84 -> 94, Architecture 92 -> 95.
- Phase D (Release evidence gate): run required gates and publish consolidated rescore where every category is >=94 in one pass.
  - Target consolidated score: all categories >=94, overall >=95.

## 0B) 2026-03-04 Closure Update (Comprehensive Repass #9)

Implemented in this pass:
- Dual-rain runtime arbitration completed and test-locked:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntime.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/SkySightUiMessagePolicy.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffectsPolicyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/ui/SkySightUiMessagePolicyTest.kt`
- Auth verification transient retry/backoff added (429/5xx/retryable-network), cancellation-safe:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastAuthRepository.kt`
  - `feature/map/src/test/java/com/example/xcpro/forecast/ForecastAuthRepositoryTest.kt`
- Provider request execution hardened with fail-closed API-key requirement for key-required SkySight hosts:
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightRequestExecutor.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightHttpContract.kt`
  - `feature/map/src/test/java/com/example/xcpro/forecast/SkySightHttpContractTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/forecast/SkySightForecastProviderAdapterNetworkContractTest.kt`
- MapLibre SkySight client policy made explicit with bounded timeout contract:
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt`
- Architecture/doc sync updated for runtime ownership and rain-arbitration behavior:
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/TABS/2Tab.md`

Verification evidence (this pass):
- `python scripts/arch_gate.py` -> PASS
- `./gradlew enforceRules --console=plain` -> PASS
- `./test-safe.bat testDebugUnitTest` -> PASS
- `./gradlew assembleDebug --console=plain` -> PASS
- Focused SkySight suites (auth/network/runtime policy/temporal/z-order) -> PASS

Updated scores (/100):
- Architecture boundary compliance: 95
- Runtime correctness (forecast + satellite): 95
- Reliability/resilience: 95
- Auth and credential robustness: 94
- Network/API contract resilience: 94
- UI responsiveness risk: 94
- Test coverage on risky paths: 94
- Docs/tooling reliability: 94
- Overall SkySight slice: 95

Production-grade gate status:
- All required score categories are now >=94 in one evidence-backed pass.
- Remaining residual risk is primarily optional connected/instrumentation coverage depth, not unit/runtime contract correctness.

Read first (required):
1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`

## 0) Baseline and Goal

Baseline (current):
- Architecture boundary compliance: 88
- Runtime correctness (forecast + satellite): 82
- Reliability/resilience: 79
- Auth and credential robustness: 68
- Network/API contract resilience: 77
- UI responsiveness risk: 81
- Test coverage on risky paths: 82
- Docs/tooling reliability: 74
- Overall SkySight slice: 81

Goal (hard gate: every category must be >=94):
- Architecture boundary compliance: 95
- Runtime correctness (forecast + satellite): 95
- Reliability/resilience: 95
- Auth and credential robustness: 95
- Network/API contract resilience: 95
- UI responsiveness risk: 95
- Test coverage on risky paths: 95
- Docs/tooling reliability: 95
- Overall SkySight slice: 95

Production-ready gate:
- Do not mark SkySight production-ready until every category is >=94 in the same evidence pass.
- Stretch target remains 95 for all categories.

First phase where each category reaches >=94 (planned):
- Architecture boundary compliance: Phase 4
- Runtime correctness (forecast + satellite): Phase 4
- Reliability/resilience: Phase 4
- Auth and credential robustness: Phase 6
- Network/API contract resilience: Phase 5
- UI responsiveness risk: Phase 4
- Test coverage on risky paths: Phase 4
- Docs/tooling reliability: Phase 6
- Overall SkySight slice: Phase 6

## 1) Code Repass Findings (Actionable)

1. Critical credential exposure gap (newly confirmed):
- `SKYSIGHT_API_KEY` is injected into `BuildConfig` for both debug and release.
- Files:
  - `feature/map/build.gradle.kts`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastAuthRepository.kt`
- Risk: static extraction from APK; does not meet production-grade credential-hardening bar.

2. Credential hardening gap:
- `ForecastCredentialsRepository` silently falls back to plaintext storage.
- File:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt`
- Risk: security downgrade remains auto-enabled without explicit user consent policy.

3. Network cancellation responsiveness gap:
- SkySight auth/provider network calls use blocking `execute()` directly and are not cancellation-aware.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastAuthRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
- Risk: in-flight requests can outlive cancelled UI jobs and degrade responsiveness/reliability under poor networks.

4. Network contract gap:
- SkySight forecast/auth currently use unqualified shared `OkHttpClient` policy.
- No SkySight-specific retry/backoff/timeout/host-policy contract.
- File area:
  - `feature/map/src/main/java/com/example/xcpro/di/`
- Risk: contract drift and hidden coupling with unrelated network clients.

5. Privacy/error-surface gap (newly confirmed):
- Provider HTTP error strings include full URLs; point-query URL embeds user latitude/longitude.
- File:
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
- Risk: sensitive coordinates may appear in user-facing error surface or crash reports.

6. Runtime churn gap:
- Satellite overlay is re-applied on minute-level time changes while enabled, and apply currently rebuilds sources/layers each time.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt` (satellite effect keyed by `selectedTimeUtcMs`)
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRuntime.kt` (satellite-only `selectedTimeUtcMs = nowUtcMs`)
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRuntimeSelectionSupport.kt` (minute ticker cadence)
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt` (config equality uses raw `referenceTimeUtcMs`)
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt` (full remove/rebuild in render path)
- Risk: avoidable layer churn, flicker risk, and unnecessary CPU/memory work.

7. Runtime state consistency gap:
- OGN satellite-contrast icon state is toggled before satellite apply success is known.
- On apply failure, no explicit rollback to previous icon-contrast state is performed.
- File:
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
- Risk: map can present contrast icon mode that does not match effective satellite overlay state after failure.

8. Runtime rollback/test gap:
- SkySight apply path reports errors but still lacks explicit "last known good config" rollback behavior tests.

9. Message normalization contract gap:
- Forecast runtime warning aggregation joins with spaces, while SkySight UI policy splits by `|`.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/SkySightUiMessagePolicy.kt`
- Risk: warning dedupe/normalization can miss duplicates across channels.

10. SkySight control UX gap (newly confirmed):
- Non-wind parameter chips are disabled when non-wind overlay is off.
- File:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheetControls.kt`
- Risk: users cannot preselect next parameter (e.g., Rain) before enabling overlay; avoidable friction and support churn.

11. Auth coverage gap:
- `ForecastAuthRepository` has no dedicated unit test class.
- Risk: auth failure handling/regression not protected.

12. Test isolation gap:
- Forecast preference test setup does not reset SkySight satellite preference keys before each test.
- Files:
  - `feature/map/src/test/java/com/example/xcpro/forecast/ForecastOverlayRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/forecast/ForecastPreferencesRepositoryTest.kt`
- Risk: order-dependent test leakage can mask regressions or cause flaky results.

13. SkySight UI interaction coverage gap:
- No focused Compose tests currently lock SkySight control behavior in `ForecastOverlayControlsContent` (satellite toggles/history slider/error presentation).
- File area:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheetControls.kt`
- Risk: user-facing regressions can slip through despite lower-layer unit tests.

14. MapLibre network client policy explicitness gap:
- `SkySightMapLibreNetworkConfigurator` installs a global MapLibre OkHttp client with interceptor only, without explicit timeout/retry policy contract coverage.
- File:
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt`
- Risk: policy drift and hard-to-audit behavior under degraded networks.

15. Tooling/docs confidence gap:
- Connected instrumentation gates for this slice remain optional/pending.
- Multiple SkySight plan docs exist; status drift risk remains.
- archived `docs/03_Features/archive/2026-04-doc-pass/SkySight_Weather.md`
  documents non-existent components.

16. Forecast apply exception-isolation gap (newly confirmed):
- Forecast apply/reapply calls are not wrapped in failure isolation (`runCatching`/error-state channel) unlike satellite/weather paths.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
- Risk: runtime map/style layer exceptions from forecast overlay can escape and destabilize map UI path.

17. Forecast refresh continuity gap (newly confirmed):
- UI effect clears forecast overlays whenever both active tile specs are temporarily null; during refresh windows this can produce blank/flicker behavior.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRuntime.kt`
- Risk: avoidable visual discontinuity and user perception of unstable overlay behavior.

18. Implementation-plan path drift gap (newly found in repass #7):
- Several findings/tasks still reference pre-split files (`MapScreenContent.kt`, `MapOverlayManager.kt`, `ForecastOverlayBottomSheet.kt`) while active code now lives in split runtime/control files.
- File:
  - `docs/refactor/SkySight_94plus_Phased_IP_2026-03-02.md`
- Risk: fixes/tests can be implemented against wrong targets, lowering execution reliability.

19. Satellite cadence integration coverage gap (newly confirmed):
- Existing temporal tests lock frame order math, but no integration-level test locks minute-tick -> runtime-effect -> manager apply cadence dedupe behavior.
- Files:
  - `feature/map/src/test/java/com/example/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt` (unit-only)
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
- Risk: future regressions can reintroduce high-frequency layer rebuild churn without failing tests.

## 2) Architecture Contract for This IP

SSOT ownership:
- Forecast/satellite prefs: `ForecastPreferencesRepository`.
- Credentials + storage mode: `ForecastCredentialsRepository`.
- Overlay resolved state: `ForecastOverlayRepository`.
- Runtime apply status/errors: `MapOverlayManagerRuntime`.

Dependency direction:
- UI -> use-cases -> repositories/ports.
- No MapLibre/UI type leakage into use-cases/domain.

Time base:
- Forecast/satellite provider time: wall UTC.
- Overlay animation: UI wall-time stepping only.
- No replay-time coupling introduced.

## 3) Phase Plan (94+ by Category)

### Phase 0 - Baseline Lock (No Behavior Change)

Work:
- Add missing regression tests before behavior changes:
  - `ForecastAuthRepositoryTest` (missing credentials, API key, http success/error, network error, cancellation).
  - `MapOverlayManager` SkySight last-good/clear behavior tests.
  - `MapOverlayManager` forecast apply failure isolation tests.
  - forecast refresh continuity tests (no blanking on transient loading windows).
  - Satellite config dedupe and no-op reapply tests.
  - minute-tick satellite cadence dedupe integration tests (runtime-effects -> manager no-op when effective bucket unchanged).
  - OGN contrast-icon rollback tests on satellite apply failure.
  - warning-message normalization tests for manager + UI message policy contract.
  - preference test fixture hard-reset for satellite keys/history-frame settings and selected region.
  - path-split sanity checks in plan/doc references (prevent targeting stale file names).

Files:
- `feature/map/src/test/java/com/example/xcpro/forecast/ForecastAuthRepositoryTest.kt` (new)
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerSkySightSatelliteErrorTest.kt` (extend)
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerForecastWarningTest.kt` (extend with failure-isolation cases)
- `feature/map/src/test/java/com/example/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt` (extend)
- `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffectsTest.kt` (new, cadence + continuity contracts)
- `feature/map/src/test/java/com/example/xcpro/map/ui/SkySightUiMessagePolicyTest.kt` (extend)
- `feature/map/src/test/java/com/example/xcpro/forecast/ForecastOverlayRepositoryTest.kt` (test-fixture reset update)
- `feature/map/src/test/java/com/example/xcpro/forecast/ForecastPreferencesRepositoryTest.kt` (test-fixture reset update)
- `docs/refactor/SkySight_94plus_Phased_IP_2026-03-02.md` (path-reference cleanup)

Expected score after phase:
- Architecture 88
- Runtime 83
- Reliability 81
- Auth 70
- Network 79
- UI 81
- Tests 86
- Docs/tooling 78
- Overall 83

### Phase 1 - Auth and Credential Security Closure

Work:
- Remove `SKYSIGHT_API_KEY` from release `BuildConfig` exposure path and move to secure runtime retrieval/contracted backend path.
- Replace silent plaintext fallback with explicit policy:
  - default fail-closed for secure storage unavailability,
  - optional user-approved fallback mode flag (explicit consent),
  - clear status propagation to settings UI.
- Make credential writes deterministic (no asynchronous race semantics).
- Add credential migration path tests and clear-user-feedback states.

Files:
- `feature/map/build.gradle.kts`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialStorageMode.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastAuthRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`
- tests under `feature/map/src/test/java/com/example/xcpro/forecast/` and `.../screens/navdrawer/`

Expected score after phase:
- Architecture 89
- Runtime 85
- Reliability 84
- Auth 82
- Network 80
- UI 83
- Tests 88
- Docs/tooling 81
- Overall 86

### Phase 2 - SkySight Network Contract Hardening

Work:
- Introduce dedicated `@SkySightHttpClient`:
  - explicit timeouts,
  - bounded retry/backoff policy for transient network errors,
  - request tagging and user-agent policy.
- Make SkySight network calls cancellation-aware (call cancellation on coroutine cancel).
- Sanitize provider/auth surfaced error messages (no full URL, no embedded coordinates).
- Keep MapLibre host/header policy explicit and tested.
- Add deterministic tests for host allowlist and header overwrite semantics.
- Add deterministic auth retry policy tests (`retryable` vs `non-retryable` paths).

Files:
- `feature/map/src/main/java/com/example/xcpro/di/` (new SkySight network module/qualifier)
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastAuthRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt`
- `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt`
- `feature/map/src/test/java/com/example/xcpro/map/SkySightMapLibreNetworkConfiguratorTest.kt`
- `feature/map/src/test/java/com/example/xcpro/forecast/SkySightForecastProviderAdapterTest.kt` (extend)

Expected score after phase:
- Architecture 90
- Runtime 88
- Reliability 88
- Auth 87
- Network 88
- UI 84
- Tests 90
- Docs/tooling 83
- Overall 88

### Phase 3 - Runtime Correctness and Resilience Closure

Work:
- Add step-aligned config dedupe in satellite runtime to avoid unnecessary source/layer rebuilds (compare effective stepped bucket, not raw wall time).
- Add "last-known-good" runtime config rollback behavior for apply failures (including OGN contrast icon-state rollback).
- Add forecast apply failure isolation in `MapOverlayManager` (mirror satellite/weather guarded apply behavior).
- Preserve forecast visual continuity during transient loading/null-tile windows (no unnecessary clear/flicker).
- Harden warning/error channel precedence for repeated mixed-failure states.
- Add explicit render invariants:
  - frame order remains `1,2,3,4,5,6,1,2,...`,
  - ownship/traffic overlays always top-priority.

Files:
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
- `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRuntime.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRuntimeSelectionSupport.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerSkySightSatelliteErrorTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerForecastWarningTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt`
- `feature/map/src/test/java/com/example/xcpro/forecast/ForecastOverlayRepositoryTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffectsTest.kt`

Expected score after phase:
- Architecture 92
- Runtime 92
- Reliability 92
- Auth 89
- Network 90
- UI 90
- Tests 92
- Docs/tooling 85
- Overall 91

### Phase 4 - UI Performance and Interaction Stability

Work:
- Reduce unnecessary recomposition/runtime calls from SkySight controls.
- Ensure slider/toggle interactions apply only on stable commit points.
- Ensure satellite apply effect does not re-trigger when effective frame bucket is unchanged.
- Allow non-wind parameter preselection even when non-wind overlay is currently disabled.
- Add focused UI tests for bottom-sheet control interactions and visual error/warning state.

Files:
- `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheetControls.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeEffects.kt`
- `feature/map/src/test/java/com/example/xcpro/map/ui/` (new/extended SkySight UI tests)
- `feature/map/src/androidTest/java/com/example/xcpro/map/ui/` (new targeted integration tests)

Expected score after phase:
- Architecture 94
- Runtime 94
- Reliability 94
- Auth 90
- Network 92
- UI 94
- Tests 94
- Docs/tooling 87
- Overall 93

### Phase 5 - Full Test Confidence Lift

Work:
- Add risk-path integration test matrix:
  - auth/network failure matrix,
  - style reload + overlay state replay,
  - satellite-only mode with no forecast fetch.
- Add deterministic flake-safe harness coverage for SkySight slice.

Files:
- `feature/map/src/test/java/com/example/xcpro/forecast/` (extended)
- `feature/map/src/test/java/com/example/xcpro/map/` (extended)
- `feature/map/src/androidTest/java/com/example/xcpro/map/` (extended)

Expected score after phase:
- Architecture 94
- Runtime 95
- Reliability 95
- Auth 93
- Network 94
- UI 94
- Tests 95
- Docs/tooling 92
- Overall 94

### Phase 6 - Docs, Runbooks, and Gate Automation Closure

Work:
- Consolidate SkySight docs to one canonical IP and one status source.
- Update `PIPELINE.md` and `docs/TABS/2Tab.md` with final runtime/auth/network contract truth.
- Retire or archive stale SkySight docs that reference non-existent components
  (`docs/03_Features/archive/2026-04-doc-pass/SkySight_Weather.md`).
- Add/refresh operational runbook for SkySight evidence capture and failure triage.
- Enforce required SkySight verification block in preflight flow.

Files:
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/TABS/2Tab.md`
- `docs/refactor/SkySight_Production_Grade_Phased_IP_2026-03-02.md`
- `docs/refactor/SkySight_94plus_Phased_IP_2026-03-02.md` (this file status updates)
- `scripts/integrations/capture_skysight_evidence.ps1`
- `preflight.bat` and/or gate scripts as needed

Expected score after phase:
- Architecture 95
- Runtime 95
- Reliability 95
- Auth 95
- Network 95
- UI 95
- Tests 95
- Docs/tooling 95
- Overall 95

Phase 6 exit criteria (mandatory):
- All nine score categories are >=94 in one consolidated quality rescore.
- Verification evidence captured for:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`
  - `./gradlew :feature:map:testDebugUnitTest`
  - `./gradlew assembleDebug`
  - `./gradlew testDebugUnitTest`
  - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
  - `./gradlew connectedDebugAndroidTest --no-parallel`

## 4) Net Score Increase Plan (Baseline -> Target)

- Architecture boundary compliance: `88 -> 95` (+7)
- Runtime correctness (forecast + satellite): `82 -> 95` (+13)
- Reliability/resilience: `79 -> 95` (+16)
- Auth and credential robustness: `68 -> 95` (+27)
- Network/API contract resilience: `77 -> 95` (+18)
- UI responsiveness risk: `81 -> 95` (+14)
- Test coverage on risky paths: `82 -> 95` (+13)
- Docs/tooling reliability: `74 -> 95` (+21)
- Overall SkySight slice: `81 -> 95` (+14)

## 5) Required Verification Gates Per Phase

Minimum per phase:
```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew :feature:map:testDebugUnitTest
./gradlew assembleDebug
```

Release validation phases (5-6):
```bash
./gradlew testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Acceptance Criteria

1. Every score category is >94 with evidence.
2. No new architecture deviation entries needed.
3. Replay determinism remains unchanged.
4. SkySight auth/network runtime has deterministic fallback/error semantics.
5. SkySight overlay runtime preserves ordering and top-layer visibility invariants.
6. Documentation and scripts align with implemented runtime behavior.
