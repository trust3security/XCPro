# SkySight Comprehensive Code Pass and Phased Implementation Plan (2026-03-01)

## 0) Metadata

- Title: SkySight comprehensive code pass, rescoring, and phased hardening plan
- Date: 2026-03-01
- Owner: XCPro team
- Status: In progress (Phase 2 substantially advanced, Phase 4 substantially implemented, and Phase 5 partially implemented on 2026-03-02)
- Scope: SkySight forecast overlays, satellite overlays, settings/auth, credentials, network header policy, evidence capture script
- Architecture sources read first (required order):
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
  - `docs/ARCHITECTURE/CONTRIBUTING.md`
  - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
  - `docs/ARCHITECTURE/AGENT.md`

## 1) Comprehensive Code Pass Findings (Ordered by Severity)

### Critical

1. First-load style timeout can leave SkySight/forecast/weather overlays unapplied until a later state mutation.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt:98`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt:113`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt:116`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt:156`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt:162`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt:222`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenSections.kt:232`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt:200`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt:201`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt:202`
- Risk: cold-start blank overlays on slow style load paths.

2. Forecast source-layer fallback candidates are generated but not effectively attempted at runtime when primary layer mismatches.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt:68`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:494`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:495`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:497`
- Risk: provider source-layer drift can silently produce blank tiles.

### High

3. Satellite animation is built newest-to-oldest, then iterated forward, which can visually reverse storm evolution.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt:103`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt:288`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt:291`
- Risk: misleading temporal motion in animated satellite loops.

4. Satellite reference time is coupled to forecast selected slot and only upper-clamped to "latest available", so stale references can persist.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt:335`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt:344`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:712`
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt:346`
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt:348`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt:281`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt:283`
- Risk: off-hour usage can show unexpectedly old satellite frames.

5. Auth verify flow can remain stuck in "Verifying..." if non-IO exceptions escape the repository.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastAuthRepository.kt:53`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt:113`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt:142`
- Risk: verification spinner dead-end and no recovery message.

6. `ForecastOverlayRepository` catches broad `Throwable` in fetch/query loops and can swallow coroutine cancellation.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:345`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:365`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:386`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:408`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:430`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:451`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:581`
- Risk: stale work can keep running across selection switches and emit stale warning/error state.

7. SkySight-specific primary toggle use case exists and is tested, but active UI wiring still routes through the generic toggle path.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayUseCases.kt:94`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt:19`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt:64`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt:591`
  - `feature/map/src/test/java/com/example/xcpro/forecast/ToggleSkySightPrimaryOverlaySelectionUseCaseTest.kt:21`
- Risk: dead behavior and misleading test confidence around SkySight-specific selection semantics.

### Medium

8. Wind-only tile failures stay warning-only; fatal error state is primary-only.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:519`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:521`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:536`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:538`
- Risk: blank wind overlay with non-fatal UX semantics.

9. Credentials repository silently falls back to plaintext `SharedPreferences` when encrypted init fails.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt:45`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt:58`
- Risk: silent security downgrade.

10. Satellite overlay apply failures are log-only and are not propagated into SSOT overlay status.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt:985`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt:998`
- Risk: user cannot tell "disabled" from "enabled but failed."

11. Settings credential load/save executes on UI event path with synchronous repository calls.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt:83`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt:303`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt:102`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt:105`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsUseCase.kt:47`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsUseCase.kt:50`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt:15`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt:45`
- Risk: potential UI jank on first encrypted prefs initialization and disk I/O.

12. Pre-ready style command buffering stores style intent but drops it on `onMapReady` without replay.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt:20`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt:29`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt:48`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/effects/MapComposeEffects.kt:160`
- Risk: style intent can be lost in race windows where command is emitted before map readiness.

13. `MapOverlayManager.initializeOverlays` is effectively dead in current wiring, while active init path uses different setup flow.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt:267`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt:156`
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt:162`
- Risk: duplicated divergent init paths increase drift and make regressions harder to catch.

### Medium-Low

14. Evidence capture script assumes edge tile success is strictly HTTP `200`.
- Evidence:
  - `scripts/integrations/capture_skysight_evidence.ps1:158`
- Risk: false negatives when provider-valid success semantics include non-200 success for tile probing.

15. Evidence capture script does not hard-gate successful auth status before parsing auth body / region selection fallback.
- Evidence:
  - `scripts/integrations/capture_skysight_evidence.ps1:281`
  - `scripts/integrations/capture_skysight_evidence.ps1:287`
  - `scripts/integrations/capture_skysight_evidence.ps1:303`
  - `scripts/integrations/capture_skysight_evidence.ps1:308`
- Risk: contract evidence can be produced from invalid auth state and silently fallback to default region.

16. Evidence capture slot search includes `20:30`, while runtime slot contract is `06:00..20:00` only.
- Evidence:
  - `scripts/integrations/capture_skysight_evidence.ps1:152`
  - `scripts/integrations/capture_skysight_evidence.ps1:153`
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt:346`
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt:348`
- Risk: script artifacts can drift from app runtime contract.

17. MapLibre SkySight HTTP override is global, one-way, and has no dedicated policy regression tests.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt:19`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt:36`
- Risk: host/header policy drift can silently regress in future refactors.

### Re-pass Misses Found and Closed (2026-03-02)

18. Non-animated satellite mode selected the oldest frame after oldest->newest ordering changes.
- Resolution:
  - `SkySightSatelliteOverlay.resolveInitialFrameIndex(...)` now selects latest frame for non-animated mode and oldest frame for animated mode.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt`

19. Wind-only tile failures were still warning-only and did not surface as fatal error state.
- Resolution:
  - `ForecastOverlayRepository` now includes wind tile hard-failure in `errorMessage` when wind overlay is enabled and no wind tile is renderable.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
  - `feature/map/src/test/java/com/example/xcpro/forecast/ForecastOverlayRepositoryTest.kt`

20. Satellite apply failures were not retried on same config and were not surfaced to map warning/error UI.
- Resolution:
  - `MapOverlayManager` now exposes `skySightSatelliteRuntimeErrorMessage`, retries failed satellite apply on same config, and clears error on successful apply/clear.
  - `MapScreenContent` now merges satellite runtime apply errors into SkySight error presentation.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerSkySightSatelliteErrorTest.kt`

### Re-pass Misses Found (Open, 2026-03-02 late pass)

21. Dual non-wind overlay model is still active end-to-end (state, use-cases, UI, runtime), so the requested single-selector UX is not implemented.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayModels.kt:76`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayUseCases.kt:158`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt:87`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt:220`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt:527`
- Risk: user confusion and higher state/behavior complexity for non-wind overlays.

22. `MapScreenContent` clears forecast overlays when primary and wind are unavailable, even if secondary tile data is present.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt:303`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt:316`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt:317`
- Risk: degraded-but-renderable secondary overlay can be dropped, producing unnecessary blank output.

23. Satellite-only mode remains coupled to forecast catalog/time-slot resolution and minute ticker activity.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:71`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:84`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:606`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:611`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:633`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:637`
- Risk: unnecessary forecast API work and avoidable failure coupling when users only want satellite layers.

24. Wind tile hard-failure text can appear in both warning and fatal error channels at once.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:524`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:535`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt:540`
- Risk: duplicated/conflicting UX messaging during failure states.

25. Architecture docs drift from runtime contract after frame-cap increase to 6.
- Evidence:
  - `docs/ARCHITECTURE/PIPELINE.md:364`
  - `docs/ARCHITECTURE/PIPELINE.md:377`
- Risk: reviewers/agents follow outdated contract text (single selector and frame range mismatch).

26. Newly added SkySight temporal/error tests and refactor plans are still untracked in git in current workspace.
- Evidence:
  - `feature/map/src/test/java/com/example/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerSkySightSatelliteErrorTest.kt`
  - `docs/refactor/SkySight_Comprehensive_Code_Pass_and_Phased_Implementation_Plan_2026-03-01.md`
  - `docs/refactor/SkySight_Implementation_Hardening_Plan_2026-03-01.md`
- Risk: CI/regression protection may not include these changes until tracked/committed.

## 2) Coverage Gaps Found

Current test tree has SkySight adapter/use-case tests, but these gaps remain:
- Missing instrumentation/integration-style test for real source-layer mismatch recovery under live tile loading conditions
  (unit-level async-like fallback timing coverage was added on 2026-03-02).
- Missing dedicated tests for `ForecastCredentialsRepository` encryption-failure policy.
- Missing dedicated tests for `SkySightMapLibreNetworkConfigurator` host allowlist and `Origin` injection.
- Missing UI/runtime integration test for first-load style timeout plus delayed style callback.
- Missing script validation tests for auth status gating and slot-contract alignment in `capture_skysight_evidence.ps1`.
- Missing focused tests for single non-wind selector migration/removal of secondary overlay state.
- Missing regression test that prevents secondary-only renderable data from being cleared by UI short-circuit logic.
- Missing regression test that avoids duplicate warning+error emission for wind hard-failure.
- Missing tests that verify satellite-only usage does not force forecast catalog/time-slot work every tick.
- Missing tests for history-frame clamp behavior at new max (`6`) in preferences/repository layers.

## 3) Scorecard (Each Category out of 100)

| Category | Current | Target | Why current is not higher |
|---|---:|---:|---|
| Architecture boundary compliance | 80 | 92 | Dual non-wind model remains active across SSOT/use-case/UI/runtime paths, and docs drift remains |
| Runtime correctness (forecast + satellite) | 82 | 93 | Secondary-only render path can still be dropped; satellite-only flow still depends on forecast selection/calls |
| Reliability/resilience | 80 | 91 | Satellite apply retry is improved, but satellite-only coupling and duplicate failure messaging remain |
| Auth and credential robustness | 60 | 92 | Verify dead-end fixed, but encrypted-storage fallback policy is still silent/plaintext |
| Network/API contract resilience | 67 | 90 | Tooling contract checks improved; runtime global HTTP override policy tests still missing |
| UI responsiveness risk | 56 | 88 | Credential load/save remains synchronous and satellite-only mode can still trigger unnecessary periodic forecast work |
| Test coverage on risky paths | 78 | 91 | Key newly identified risk paths are still not covered (single-selector migration, secondary-clear regression, wind dedupe, satellite-only decoupling) |
| Docs/tooling reliability | 66 | 88 | PIPELINE contract text is stale for frame range and selector model; new SkySight tests/docs are still untracked |

Overall SkySight readiness score: **76/100**  
Target after all phases: **91/100**

## 4) Phased Implementation Plan

### Phase 0 - Baseline and Safety Net

Goal:
- Freeze current behavior with targeted failing/guard tests before code changes.

Work:
- Add regression tests for findings 1-17 (priority: 1-7 first).
- Capture reproducible first-load style-timeout scenario test.
- Add regression for pre-ready map style command behavior.

Exit criteria:
- Every critical/high finding has at least one deterministic test.

Phase target score: **60/100**

### Phase 1 - First-Load Overlay Apply Reliability

Goal:
- Guarantee overlay apply when style becomes available, including timeout/fallback path.

Primary files:
- `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt` (if needed for effect keying)

Exit criteria:
- No cold-start blank overlay in delayed-style test.
- Reapply is idempotent and does not duplicate layers/sources.
- Pre-ready style command behavior is deterministic and covered by tests.

Phase target score: **68/100**

### Phase 2 - Forecast Source-Layer Fallback Execution

Goal:
- Apply ordered source-layer candidates when primary source layer is missing and remove selection wiring drift.

Primary files:
- `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt` (if contract normalization needed)

Exit criteria:
- Candidate fallback path is deterministic and covered by unit tests.
- SkySight primary toggle path is either correctly wired or intentionally removed with tests/docs updated.

Phase target score: **75/100**

Phase 2 deep-pass status (2026-03-02):
- Substantially implemented (runtime fallback + layer retarget + fallback warning surfacing + selection-path cleanup + expanded tests).
- Current Phase 2 readiness score: **82/100**.

Phase 2 sub-scorecard (out of 100):
- Candidate fallback execution: **84/100**
- Layer retarget correctness (fill/arrow/barb): **86/100**
- Fallback observability and UX surfacing: **79/100**
- Risk-path test coverage: **80/100**
- Selection-path hygiene: **92/100**

Phase 2 implementation outcomes:

1. Added deterministic candidate state and runtime fallback progression for vector fill overlays.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:506`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:523`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:540`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:835`

2. Existing vector layers now retarget `sourceLayer` during updates (fill + wind arrow + wind barb).
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:181`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:306`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt:445`

3. Added dedicated Phase 2 tests for fallback advancement and wind-layer retarget behavior.
- Evidence:
  - `feature/map/src/test/java/com/example/xcpro/map/ForecastRasterOverlaySourceLayerFallbackTest.kt:36`
  - `feature/map/src/test/java/com/example/xcpro/map/ForecastRasterOverlaySourceLayerFallbackTest.kt:107`

4. Added runtime fallback warning signal from renderer and surfaced it through overlay manager + map warning UI.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerForecastWarningTest.kt`

5. Removed dead generic primary-selection wiring path and aligned ViewModel to canonical SkySight toggle use case.
- Evidence:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayUseCases.kt`

6. Expanded fallback behavior tests to cover async-like recovery and fallback exhaustion semantics.
- Evidence:
  - `feature/map/src/test/java/com/example/xcpro/map/ForecastRasterOverlaySourceLayerFallbackTest.kt`

Phase 2 remaining gaps:
- No integration/instrumentation test yet proving fallback behavior against real asynchronous vector tile load timing in a live map/style context.
- Fallback warning is surfaced in map warning composition, but not yet modeled as a first-class `ForecastOverlayUiState` field.

Phase 2 remaining implementation sequence:
- Step 2.4: Add integration-oriented test coverage for asynchronous tile load and real mismatch recovery behavior.
- Step 2.6: Decide whether runtime fallback warning should be promoted into repository-driven `ForecastOverlayUiState` instead of manager-local warning composition.

### Phase 3 - Settings/Auth/Credential Hardening

Goal:
- Remove verify-stuck failure mode, move blocking credential operations off UI, and make security downgrade explicit.

Primary files:
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastAuthRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`

Exit criteria:
- `_authChecking` reset is exception-safe.
- Credential reads/writes are not on UI thread path.
- Fallback security mode is explicit in state/UX and tested.

Phase target score: **82/100**

### Phase 4 - Satellite Temporal Correctness

Goal:
- Fix frame ordering and stale-reference behavior for satellite/radar/lightning overlays.

Primary files:
- `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt` (if decoupling reference-time source)

Exit criteria:
- Animation progresses oldest->newest (or documented contract with matching tests).
- Freshness clamp has both upper and lower bound policy.

Phase target score: **87/100**

Phase 4 implementation status (2026-03-02):
- Substantially implemented.
- Satellite frame sequencing now renders/animates oldest->newest.
- Non-animated satellite mode now anchors on the latest frame (no stale oldest-frame lock).
- Reference-time resolution now applies upper and lower freshness clamps.
- Added dedicated temporal policy tests for frame ordering and clamp behavior.
- Current Phase 4 readiness score: **86/100**.

Phase 4 remaining gaps:
- Map/UI semantics for how manual forecast slot selection should influence satellite reference time are still implicit and need an explicit product-policy contract.
- Satellite history frame cap is now `6` (default `3`), but perf behavior with max-cap animation still needs explicit guard validation.
- Satellite-only mode is still coupled to forecast catalog/time-slot resolution and minute ticker work.

### Phase 5 - Error Surfacing and Wind Fatal Semantics

Goal:
- Align fatal/warning semantics across primary/wind overlays, surface runtime satellite apply errors through SSOT, and preserve cancellation semantics.

Primary files:
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`

Exit criteria:
- UI can distinguish disabled/loading/degraded/failed.
- Wind-only hard failure maps to clear actionable state.
- `CancellationException` is never swallowed in overlay/query flows.

Phase target score: **89/100**

Phase 5 implementation status (2026-03-02):
- Partially implemented.
- Wind-only hard tile failures now surface into `ForecastOverlayUiState.errorMessage`.
- Satellite apply runtime failures now surface via manager flow and map SkySight error composition.
- Satellite apply retry now works for repeated same-config attempts after transient failure.
- Current Phase 5 readiness score: **84/100**.

Phase 5 remaining gaps:
- Runtime satellite apply error is currently manager-local composition, not yet repository-owned SSOT overlay status.
- Forecast runtime warning/error composition is still split between repository state and manager-local runtime flow.
- Wind tile hard-failure text can still appear in both warning and error channels.
- `MapScreenContent` forecast short-circuit still clears overlays when primary and wind are absent, even if secondary is renderable.

Phase 5 follow-up work added (2026-03-02):
- Raise satellite history frame cap from `3` to `6` with default kept at `3` (implemented).
- Keep explicit forward playback contract in tests (`1,2,3,4,5,6,1,2,...`) so cloud/radar motion direction stays readable (implemented).
- Add lightweight render/perf guard checks for higher frame count (layer/source count, update cadence stability).

### Phase 8 - Non-Wind Overlay UX Simplification

Goal:
- Replace dual non-wind overlay model (primary + secondary toggle) with one canonical non-wind overlay selection flow.

Primary files:
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastPreferencesRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayModels.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayUseCases.kt`
- `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`

Recommended model:
- Keep one non-wind overlay rendered at a time (single-active SSOT contract).
- Replace secondary overlay enable/selection controls with:
  - one non-wind parameter picker (chips/list) shown only once in the SkySight section,
  - optional "recent/favorites" quick-switch row (still single-active at runtime),
  - no separate "second non-wind overlay" toggle.

Exit criteria:
- No secondary non-wind overlay toggle in UI.
- Exactly one non-wind overlay parameter is active in repository SSOT at any time.
- Migration preserves existing users by mapping current primary/secondary settings to one selected parameter deterministically.
- Regression tests lock single-overlay selection semantics and UI behavior.
- Forecast render path does not drop renderable non-wind state due primary/wind-only short-circuit assumptions.

### Phase 6 - Network Policy and Evidence Tooling

Goal:
- Lock SkySight host/header behavior in tests and harden evidence capture auth/status/slot handling.

Primary files:
- `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt`
- `scripts/integrations/capture_skysight_evidence.ps1`
- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/SKYSIGHT/*` (as needed for contract notes)

Exit criteria:
- Dedicated configurator policy tests pass.
- Evidence script accepts contract-valid status set and emits clear diagnostics.
- Evidence script fails fast on auth failure and uses slot search aligned with runtime contract.
- Architecture docs reflect runtime contract (history frame range and non-wind selector model) with no SkySight drift.

Phase target score: **91/100**

### Phase 7 - Final Verification and Rescore

Goal:
- Run required gates and publish final scorecard with residual risk log.

Required checks:
- `python scripts/arch_gate.py`
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When relevant:
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
- `./gradlew connectedDebugAndroidTest --no-parallel`

Exit criteria:
- Required checks green.
- Final scorecard reaches or exceeds **91/100** overall.

## 5) Implementation Progress (2026-03-01 and 2026-03-02 passes)

Phase 2 comprehensive code pass update (2026-03-01):
- Completed deep review and implementation of source-layer fallback execution path.
- Added deterministic fallback progression and update-path source-layer retargeting in `ForecastRasterOverlay`.
- Added dedicated Phase 2 tests and verification evidence.

Implemented in code:
- Finding 1 (first-load overlay apply reliability): added explicit reapply calls during overlay setup.
  - `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`
- Finding 6 (cancellation swallowing): rethrow `CancellationException` in all overlay/query catch paths.
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
- Finding 7 (SkySight-specific toggle wiring): ViewModel now uses `ToggleSkySightPrimaryOverlaySelectionUseCase`.
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt`
- Finding 12 (pre-ready style intent drop): queue now replays on `onMapReady`.
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapRuntimeController.kt`
- Finding 15 (auth verify stuck): ViewModel verify flow now has exception-safe `finally` reset and unknown-exception messaging.
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt`
- Findings 14/16 (script status/slot assumptions): evidence script accepts contract-valid tile status and enforces runtime-aligned slot window.
  - `scripts/integrations/capture_skysight_evidence.ps1`
- Finding 15 (tooling auth status gate): evidence script now fails fast on non-2xx auth before parsing body.
  - `scripts/integrations/capture_skysight_evidence.ps1`
- Finding 2 / Phase 2 (source-layer fallback execution): runtime fallback progression and layer retargeting implemented.
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`

Added tests:
- `feature/map/src/test/java/com/example/xcpro/forecast/ForecastOverlayRepositoryTest.kt`
  - `overlayState_propagatesCancellation_whenTileFetchIsCancelled`
  - `queryPointValue_propagatesCancellation_fromValuePort`
- `feature/map/src/test/java/com/example/xcpro/map/ui/MapRuntimeControllerWeatherStyleTest.kt`
  - `applyStyle_beforeMapReady_replaysWhenMapBecomesReady`
- `feature/map/src/test/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModelTest.kt`
  - `verifyCredentials_unexpectedException_setsFailureAndResetsChecking`
- `feature/map/src/test/java/com/example/xcpro/map/ForecastRasterOverlaySourceLayerFallbackTest.kt`
  - `vectorFill_afterConsecutiveMisses_advancesToNextSourceLayerCandidate`
  - `windArrow_existingLayer_updatesSourceLayerOnRender`

Verification evidence:
- `./test-safe.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.screens.navdrawer.ForecastSettingsViewModelTest" --tests "com.example.xcpro.map.ui.MapRuntimeControllerWeatherStyleTest" --tests "com.example.xcpro.forecast.ForecastOverlayRepositoryTest"` -> PASS
- `./test-safe.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.ForecastRasterOverlaySourceLayerFallbackTest" --tests "com.example.xcpro.forecast.SkySightForecastProviderAdapterTest" --tests "com.example.xcpro.map.ui.MapRuntimeControllerWeatherStyleTest" --tests "com.example.xcpro.forecast.ForecastOverlayRepositoryTest" --tests "com.example.xcpro.screens.navdrawer.ForecastSettingsViewModelTest"` -> PASS
- `./gradlew enforceRules` -> PASS
- `./gradlew testDebugUnitTest` -> PASS
- `./gradlew assembleDebug` -> PASS

Phase 2 implementation update (2026-03-02):
- Implemented fallback warning signal path (`ForecastRasterOverlay.runtimeWarningMessage`) and manager-level warning aggregation flow for map UI warning surfacing.
- Removed dead `SelectForecastParameterUseCase` and unused `selectParameter(...)` ViewModel path to keep one canonical SkySight primary selection behavior.
- Added Phase 2 tests for:
  - fallback warning surfacing in `MapOverlayManager`,
  - miss-then-recovery behavior (no premature fallback),
  - fallback exhausted warning semantics.

Verification evidence (2026-03-02):
- `./test-safe.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.ForecastRasterOverlaySourceLayerFallbackTest" --tests "com.example.xcpro.map.MapOverlayManagerForecastWarningTest" --tests "com.example.xcpro.forecast.SkySightForecastProviderAdapterTest" --tests "com.example.xcpro.forecast.ForecastOverlayRepositoryTest" --tests "com.example.xcpro.map.ui.MapRuntimeControllerWeatherStyleTest" --tests "com.example.xcpro.screens.navdrawer.ForecastSettingsViewModelTest"` -> PASS
- `./gradlew enforceRules assembleDebug` -> PASS
- `./gradlew testDebugUnitTest` -> PASS (after profile hydration parser hardening)

Phase 4 implementation update (2026-03-02):
- Implemented satellite temporal correctness fixes:
  - `SkySightSatelliteOverlay.buildFrameEpochs(...)` now returns oldest->newest epoch ordering.
  - `SkySightSatelliteOverlay.resolveBaseFrameEpochSec(...)` now enforces two-sided freshness clamp
    (latest available upper bound + lower bound based on renderable provider history horizon).
  - Defensive frame-count clamping is applied inside the temporal helpers.
- Added dedicated tests:
  - `feature/map/src/test/java/com/example/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt`
    - `buildFrameEpochs_ordersOldestToNewest`
    - `resolveBaseFrameEpochSec_clampsReferenceToLatestAvailableStep`
    - `resolveBaseFrameEpochSec_clampsReferenceToLowerBoundByHistoryWindow`

Comprehensive re-pass update (2026-03-02, follow-up):
- Closed additional missed items:
  - wind-only tile failures now elevate to fatal error state,
  - non-animated satellite now defaults to latest frame,
  - satellite apply failures now surface in runtime error flow and retry on unchanged config.
- Also tightened OGN contrast refresh behavior to avoid unnecessary OGN overlay instantiation when there are no targets and no existing OGN overlay.

Added tests:
- `feature/map/src/test/java/com/example/xcpro/forecast/ForecastOverlayRepositoryTest.kt`
  - `primaryDisabled_withWindOverlayTileFailure_setsFatalErrorMessage`
- `feature/map/src/test/java/com/example/xcpro/map/SkySightSatelliteOverlayTemporalPolicyTest.kt`
  - `resolveInitialFrameIndex_nonAnimatedUsesLatestFrame`
  - `resolveInitialFrameIndex_animatedUsesOldestFrame`
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerSkySightSatelliteErrorTest.kt`
  - `setSkySightSatelliteOverlay_renderFailureSurfacesErrorAndRetriesSameConfig`
  - `clearSkySightSatelliteOverlay_clearsRuntimeError`

Verification evidence (2026-03-02 follow-up):
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.forecast.ForecastOverlayRepositoryTest" --tests "com.example.xcpro.map.SkySightSatelliteOverlayTemporalPolicyTest" --tests "com.example.xcpro.map.MapOverlayManagerSkySightSatelliteErrorTest" --tests "com.example.xcpro.map.MapOverlayManagerForecastWarningTest" --no-daemon --no-configuration-cache` -> PASS
- `./gradlew enforceRules --no-daemon --no-configuration-cache` -> PASS
- `./gradlew assembleDebug --no-daemon --no-configuration-cache` -> PASS

Phase 8 implementation update (2026-03-02):
- Completed single non-wind selector migration across SSOT/use-case/UI/runtime:
  - removed secondary non-wind fields/mutations from forecast preferences, overlay models, use-cases, and ViewModel wiring.
  - removed secondary non-wind fetch/cache/render paths from `ForecastOverlayRepository`.
  - removed secondary runtime overlay ownership/rendering from `MapOverlayManager` and runtime state cleanup.
  - removed secondary SkySight callback plumbing from bottom-sheet/tab wiring.
- Added/updated tests for the single-selector model and updated affected forecast/map tests.
- Updated architecture pipeline docs to reflect the one non-wind plus optional wind contract.

Verification evidence (2026-03-02 Phase 8):
- `./gradlew :feature:map:compileDebugKotlin :feature:map:compileDebugUnitTestKotlin --no-daemon --no-configuration-cache` -> PASS
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.forecast.ForecastPreferencesRepositoryTest" --tests "com.example.xcpro.forecast.ToggleForecastPrimaryOverlaySelectionUseCaseTest" --tests "com.example.xcpro.forecast.ToggleSkySightPrimaryOverlaySelectionUseCaseTest" --tests "com.example.xcpro.forecast.ForecastOverlayRepositoryTest" --tests "com.example.xcpro.map.MapOverlayManagerForecastWarningTest" --tests "com.example.xcpro.map.ui.MapBottomSheetTabsTest" --no-daemon --no-configuration-cache` -> PASS

Cross-module stability update (2026-03-02):
- Fixed a non-SkySight gate blocker by hardening profile hydration parsing so null/invalid list entries do not block valid entries:
  - `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileRepository.kt`
- Validation:
  - `./test-safe.bat :app:testDebugUnitTest --tests "com.example.xcpro.profiles.ProfileRepositoryTest"` -> PASS

Required checks status (latest run, 2026-03-02):
- `./gradlew enforceRules --no-daemon --no-configuration-cache` -> PASS
- `./gradlew assembleDebug --no-daemon --no-configuration-cache` -> PASS
- `./gradlew testDebugUnitTest --no-daemon --no-configuration-cache` -> blocked by Windows file lock on `feature/map/build/test-results/testDebugUnitTest/binary/output.bin` (no assertion failure in targeted suites)

## 6) API Research Snapshot (2026-03-01)

Latest probe outcomes captured in this review cycle:
- `edge.skysight.io` sample tile probe:
  - without `Origin`: HTTP `400`
  - with SkySight `Origin`: HTTP `200` (other probes in this cycle also observed `204` success semantics)
- `static2.skysight.io` legend probe returned HTTP `200` with and without `Origin`.
- `cf.skysight.io` point endpoint probe returned HTTP `200` with valid payload (`Invoke-WebRequest` JSON POST).
- `satellite.skysight.io` sample imagery/radar/lightning probes returned HTTP `200` for tested buckets.

Implication:
- Origin policy remains mandatory.
- Contract tooling should treat valid non-200 success cases carefully where provider behavior indicates acceptance semantics.

## 7) Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Overlay reapply fix introduces duplicate layers | Medium | Idempotence tests around repeated apply/clear cycles |
| Source-layer fallback picks wrong layer on edge cases | High | Deterministic candidate ordering tests and contract fixtures |
| Cancellation swallowed in repository paths causes stale emissions | High | Explicit `CancellationException` rethrow policy + tests |
| Security hardening blocks edge devices | Medium | Explicit degraded mode plus migration-safe UI messaging |
| Satellite order change surprises users | Medium | Document temporal contract and align UI toggle wording |
| Script behavior drifts from provider contract | Medium | Encode auth/status/slot policy and emit diagnostic artifact |
