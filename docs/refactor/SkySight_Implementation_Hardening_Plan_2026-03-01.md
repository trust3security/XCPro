# SkySight Implementation Hardening Plan 2026-03-01

## 0) Metadata

- Title: SkySight Forecast and Satellite Hardening
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: SKYSIGHT-20260301-01
- Status: Draft

## 1) Scope

- Problem statement:
  - Settings auth check and runtime overlay fetches are not contract-coupled, so users can read "Authentication succeeded" while runtime overlay requests still fail.
  - Forecast vector source-layer candidate modeling exists but runtime does not apply candidate fallback, increasing blank-overlay risk under contract drift.
  - SkySight satellite/network contract behavior lacks focused regression tests for header policy and frame/render logic.
  - Credential persistence currently allows plaintext fallback when encrypted storage creation fails.
  - Satellite history frame cap was previously 3 and limited motion readability for cloud/radar progression.
  - Dual non-wind overlay UX (primary + secondary toggle) is overly complex and error-prone for users.
  - SkySight evidence/documentation path is drifting (`docs/integrations/skysight/evidence` references are present while evidence assets are currently missing from repo state).
- Why now:
  - These are correctness and operability risks in a weather-safety feature area.
  - Review findings are concrete and reproducible in current code.
- In scope:
  - Forecast auth UX/runtime semantics alignment.
  - Forecast vector source-layer fallback behavior.
  - Satellite/network contract hardening tests.
  - Credential storage hardening behavior.
  - Evidence/doc/script sync for SkySight contract maintenance.
  - Satellite history frame cap expansion with deterministic playback contract coverage.
  - Non-wind overlay UX simplification to a single active non-wind overlay model.
- Out of scope:
  - Full provider authentication/session redesign for all endpoints.
  - New forecast parameters or major visual redesign.
  - Replay/fusion pipeline behavior changes.
- User-visible impact:
  - Clearer and more honest SkySight auth status in settings.
  - Reduced blank-overlay incidents for valid data.
  - More stable satellite/forecast behavior across API/header changes.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Forecast overlay runtime selection and payload state | `ForecastOverlayRepository` | `Flow<ForecastOverlayUiState>` | UI-local or overlay-manager policy copies |
| SkySight settings auth-check status | `ForecastSettingsViewModel` state | `authConfirmation`, `authReturnCode`, explicit availability text | UI-computed availability claims disconnected from repository/runtime state |
| Source-layer fallback candidate order | `SkySightForecastProviderAdapter` contract + runtime resolver in `ForecastRasterOverlay` | `ForecastTileSpec.sourceLayerCandidates` | Ad-hoc map-runtime default strings not derived from spec |
| Satellite runtime overlay config | `MapOverlayManager` | runtime owner fields + `SkySightSatelliteRuntimeConfig` | independent unmanaged overlay state holders |
| SkySight credentials | `ForecastCredentialsRepository` | `loadCredentials()/saveCredentials()` | duplicate storage copies in UI or alternate stores |
| Evidence artifacts and capture process | `scripts/integrations/capture_skysight_evidence.ps1` + docs owner | documented artifact set under one canonical path | multiple incompatible evidence locations without canonical reference |

### 2.2 Dependency Direction

Dependency flow remains:

`UI -> domain/use-cases -> data`

- Modules/files touched:
  - `feature/map/src/main/java/com/example/xcpro/forecast/*`
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettings*`
  - `feature/map/src/test/java/com/example/xcpro/forecast/*`
  - `feature/map/src/test/java/com/example/xcpro/map/*` and `.../ui/*` where relevant
  - `docs/SKYSIGHT/*` and `docs/refactor/*` for documentation sync
  - `scripts/integrations/capture_skysight_evidence.ps1`
- Boundary risk:
  - Avoid moving map/runtime responsibilities into ViewModel/use-cases.
  - Avoid introducing direct network/session handling into UI layer.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| "Auth succeeded" user interpretation | Settings UI string only | Explicit multi-signal settings state (auth result + runtime availability caveat) | Prevent false confidence and improve operational clarity | `ForecastSettingsViewModel` and settings UI tests |
| Vector source-layer fallback resolution | single selected source-layer at runtime | runtime fallback strategy using ordered candidates from tile spec | Reduce blank overlays when provider layer naming varies | new `ForecastRasterOverlay` tests + adapter/repository tests |
| Credentials fallback policy when encryption fails | `ForecastCredentialsRepository` silent plaintext fallback | explicit fail-closed or explicit degraded-mode policy with user-visible warning | Avoid silent credential security downgrade | `ForecastCredentialsRepository` tests + settings messaging tests |
| SkySight header policy regression detection | implicit runtime behavior only | focused tests around `SkySightMapLibreNetworkConfigurator` host/header behavior | Guard against accidental host/header regressions | dedicated network-config tests |
| Evidence path ownership | implicit/fragmented docs references | one canonical evidence path + script/doc sync in same change set | Keep contract refresh workflow reliable | script smoke + doc grep checks |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `ForecastRasterOverlay.resolveSourceLayer` | Picks one source-layer and ignores candidate fallback in runtime behavior | Candidate-aware source-layer fallback resolution strategy in renderer | Phase 2 |
| `ForecastSettingsScreen` auth messaging | `verifyCredentials()` success text interpreted as operational availability signal | Explicit wording and state model separating auth-check from runtime availability | Phase 1 |
| `ForecastCredentialsRepository.createEncryptedPrefsOrFallback` | Silent fallback to plaintext `SharedPreferences` | Explicit secure-storage policy with deterministic failure/reporting path | Phase 1 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Forecast slot selection and follow offset | Wall | Forecast contract is regional wall-time based |
| Satellite frame bucket selection (`date=YYYY/MM/DD/HHmm`) | Wall | API contract uses wall UTC bucket formatting |
| Overlay animation frame stepping | Wall UI/runtime timer | Visual runtime effect only |
| Auth status timestamps/logging (if added) | Wall | UX/status reporting only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - Network and JSON parse: `Dispatchers.IO` (`@IoDispatcher`).
  - Map style mutation/runtime overlay apply: main/UI thread.
- Primary cadence/gating:
  - Existing minute-level ticker for forecast auto-time.
  - Satellite animation cadence remains fixed in overlay runtime.
- Hot-path latency budget:
  - Parameter/time switch should remain under ~1s in typical network conditions.
  - Source-layer fallback lookup must remain O(1)-ish per update (bounded candidate list).

### 2.5 Replay Determinism

- Deterministic for same input: Yes.
- Randomness used: No.
- Replay/live divergence rules:
  - No replay-domain state or time-source behavior will be modified.
  - Changes are confined to forecast/satellite overlay and settings surfaces.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| False operational confidence from auth UI | Honest outputs, SSOT/UDF clarity | Unit/UI test + review | new `ForecastSettingsViewModel` tests |
| Blank forecast overlays from source-layer mismatch | Data-contract correctness | Unit tests | new `ForecastRasterOverlay` tests + existing forecast tests |
| Header policy regression breaks tiles | Boundary adapter/network contract correctness | Unit test + live probe script | new `SkySightMapLibreNetworkConfigurator` tests |
| Silent plaintext credential fallback | Security/compliance hardening | Unit test + review | new `ForecastCredentialsRepository` tests |
| Evidence workflow drift | Documentation/process correctness | script smoke + docs review | `capture_skysight_evidence.ps1` + docs references |

## 3) Data Flow (Before -> After)

Before:

`Settings UI -> ForecastAuthRepository.verifySavedCredentials() -> "Authentication succeeded" text`  
`Map runtime -> ForecastOverlayRepository -> SkySightForecastProviderAdapter/MapLibre requests`  
`(no explicit coupling between auth check outcome and runtime availability messaging)`

After:

`Settings UI -> ForecastSettingsViewModel explicit status model -> auth-check result + runtime-availability caveat`  
`Map runtime unchanged for ownership, but forecast renderer uses source-layer fallback candidates deterministically`  
`Capture script/docs use one canonical evidence path and validation checklist`

## 4) Implementation Phases

### Phase 0 - Baseline and Defect Locks

- Goal:
  - Lock current behavior and add failing tests for targeted defects.
- Files to change:
  - Tests only.
- Tests to add/update:
  - Add regression test proving source-layer candidate fallback is currently not applied.
  - Add settings/auth test proving success message can be shown independently of runtime availability semantics.
  - Add credential repository tests for encryption failure behavior.
- Exit criteria:
  - Reproducible failing tests exist for each targeted improvement track.

### Phase 1 - Auth Semantics and Credential Hardening

- Goal:
  - Make settings auth state honest and harden credential storage behavior.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastCredentialsRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/ForecastSettingsScreen.kt`
- Tests to add/update:
  - New tests for settings status mapping and auth-message correctness.
  - New tests for credential-storage fallback policy.
- Exit criteria:
  - Settings auth text cannot be interpreted as guaranteed runtime-data availability.
  - Credential fallback policy is explicit and covered by tests.

### Phase 2 - Forecast Source-Layer Fallback Hardening

- Goal:
  - Apply `sourceLayerCandidates` deterministically at runtime to reduce blank overlays.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ForecastRasterOverlay.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/SkySightForecastProviderAdapter.kt` (if contract metadata adjustments are needed)
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt` (only if fallback status surfacing is needed)
- Tests to add/update:
  - New `ForecastRasterOverlay` tests for candidate fallback and branch switching.
  - Extend `SkySightForecastProviderAdapterTest` for candidate ordering contract.
  - Extend `ForecastOverlayRepositoryTest` if warning/fallback state semantics change.
- Exit criteria:
  - Runtime uses ordered source-layer candidates without regressions for wind/fill branches.

### Phase 3 - Satellite/Network Contract Regression Coverage

- Goal:
  - Add targeted tests for SkySight network configurator and satellite overlay runtime behavior.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightMapLibreNetworkConfigurator.kt` (if testability seams needed)
  - `feature/map/src/main/java/com/example/xcpro/map/SkySightSatelliteOverlay.kt` (only if bug fixes are required)
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt` (only if state transitions need correction)
- Tests to add/update:
  - New tests for host allowlist and `Origin` header injection behavior.
  - New tests for satellite frame selection, history window clamp, and clear/reapply behavior.
  - Update existing hotspot anchor test coverage as needed.
- Exit criteria:
  - Header policy and satellite runtime behavior are regression-protected by focused tests.

### Phase 4 - Evidence and Documentation Sync

- Goal:
  - Remove evidence-path drift and make contract refresh workflow deterministic.
- Files to change:
  - `scripts/integrations/capture_skysight_evidence.ps1`
  - `docs/SKYSIGHT/SkySightChangePlan/*.md` references to evidence path
  - `docs/refactor/SkySight_Wind_Overlay_Refactor_Plan_2026-02-15.md` references
  - Optionally add/create canonical evidence directory scaffolding
- Tests to add/update:
  - Script smoke validation (artifact presence list).
  - Doc-reference consistency check (path grep).
- Exit criteria:
  - Script output path and docs references are consistent and verifiable.

### Phase 5 - Hardening, Verification, and Release Readiness

- Goal:
  - Run full required checks and complete architecture self-audit.
- Files to change:
  - Any final fixes from previous phase verification.
  - Plan status update section in this doc.
- Tests to add/update:
  - Final pass for all new/updated tests in this plan.
- Exit criteria:
  - Required commands pass.
  - No architecture drift introduced.
  - Quality rescore added with evidence.

### Phase 6 - Product UX Follow-up (History + Single Non-Wind Overlay)

- Goal:
  - Increase satellite history capacity while preserving directional animation clarity.
  - Replace secondary non-wind overlay toggle with one canonical non-wind overlay selection path.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastSettings.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/ForecastOverlayBottomSheet.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastPreferencesRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayUseCases.kt`
  - `feature/map/src/main/java/com/example/xcpro/forecast/ForecastOverlayViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManager.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapBottomSheetTabs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Tests to add/update:
  - Satellite animation order test explicitly verifying forward loop `1..N..1`.
  - Clamp/selection tests for increased frame max with default preserved.
  - Repository/UI tests ensuring only one non-wind overlay parameter can be active.
  - Migration tests mapping legacy secondary settings to the new single-selection model.
  - Regression test that non-wind renderable data is not dropped by primary/wind-only short-circuit conditions.
  - Regression test that wind hard-failure is surfaced once (error) without duplicate warning channel text.
  - Regression test that satellite-only mode does not require forecast catalog/time-slot fetch work every tick.
- Exit criteria:
  - History frame slider supports requested expanded range (max `6`, default `3`).
  - Animated playback order remains forward oldest->newest->oldest with no reverse perception.
  - Secondary non-wind overlay toggle removed from UI and state model.
  - Single non-wind overlay selection is deterministic and SSOT-driven.
  - Satellite-only path no longer performs avoidable forecast metadata polling.
  - Architecture docs reflect current history-frame range and selector model.

Recommended implementation shape:
- Replace `secondaryPrimaryOverlayEnabled` + `selectedSecondaryPrimaryParameterId` with one `selectedNonWindParameterId`.
- Keep wind controls independent (`selectedWindParameterId`, `windOverlayEnabled`) so wind/non-wind remain orthogonal.
- In bottom sheet, present one non-wind picker section only (chips/list); selecting one parameter always replaces the previous one.
- Add migration mapping:
  - if legacy primary exists, keep it;
  - else if only legacy secondary exists, promote secondary to primary;
  - otherwise fall back to default non-wind parameter.

## 5) Test Plan

- Unit tests:
  - `SkySightForecastProviderAdapterTest` (candidate contract and API mapping invariants).
  - `ForecastOverlayRepositoryTest` (fallback/warning semantics if changed).
  - New `ForecastRasterOverlayTest` (source-layer candidate fallback, branch cleanup).
  - New `ForecastCredentialsRepositoryTest` (secure fallback policy).
  - New `ForecastSettingsViewModelTest` (auth-status clarity and message mapping).
  - New `SkySightMapLibreNetworkConfiguratorTest` (header and host behavior).
  - New `SkySightSatelliteOverlayTest` (frame/time/animation config behavior where feasible).
- Replay/regression tests:
  - Confirm no replay behavior changes in existing replay test suites.
- UI/instrumentation tests (if needed):
  - Optional compose/UI state tests for settings auth status copy and SkySight tab warning surfaces.
- Degraded/failure-mode tests:
  - Auth endpoint failure classes (missing credentials, missing API key, HTTP/network failure) map to unambiguous user status.
  - Source-layer primary miss with valid fallback candidate still renders.
  - Satellite overlay disable/no-layer-enabled path clears runtime overlay.
  - Wind tile hard-failure emits one actionable error surface (no duplicate warning echo).
  - Secondary non-wind data is not hidden by primary/wind short-circuit guards.
  - Satellite-only usage remains functional when forecast metadata path is unavailable.
- Boundary tests for removed bypasses:
  - No silent plaintext fallback path without explicit status handling.
  - Runtime source-layer resolution does not ignore candidate list.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Auth messaging change causes user confusion during transition | Medium | Keep copy explicit and test all result mappings | XCPro Team |
| Source-layer fallback introduces wrong-layer render | High | Contract-driven candidate order + runtime tests | XCPro Team |
| Credential hardening blocks legacy devices unexpectedly | Medium | Explicit degraded-mode handling and clear user feedback | XCPro Team |
| Network configurator tests are brittle due static hooks | Medium | Add minimal seam and isolate test scope to policy logic | XCPro Team |
| Evidence path migration breaks existing scripts/docs | Medium | Single canonical path and grep-based consistency checks | XCPro Team |
| Additional tests increase build time | Low | Keep tests focused and deterministic; avoid heavy integration in unit suite | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling remains explicit and non-mixed.
- Replay behavior remains deterministic.
- Auth status messaging is explicit about scope (auth-check vs runtime data availability).
- Source-layer fallback candidates are used deterministically at runtime with tests.
- Credential storage behavior is explicit and tested.
- `KNOWN_DEVIATIONS.md` unchanged unless explicit approval is provided.
- Required verification commands pass.

## 8) Rollback Plan

- What can be reverted independently:
  - Settings/auth messaging and ViewModel status mapping.
  - Source-layer fallback runtime changes.
  - Credential hardening policy changes.
  - Network configurator/satellite tests and testability seams.
  - Evidence/doc path sync changes.
- Recovery steps if regression is detected:
  - Revert only the failing phase commit.
  - Re-run targeted SkySight tests, then required checks.
  - Keep baseline regression tests from Phase 0 to prevent reintroduction.

## 9) Initial Research Snapshot (2026-03-01)

- Live probe performed during review:
  - `edge.skysight.io` tile sample responded `400` without `Origin`, `200` with `Origin`.
  - tested `static2.skysight.io` legend sample responded `200` with and without `Origin`.
  - tested `satellite.skysight.io` sample tile responded `200` with and without `Origin`.
- Interpretation:
  - Edge tile requests require or strongly depend on origin policy and must remain protected by network configurator behavior.
  - Legend and satellite host behavior still requires contract monitoring but appears less strict in sampled requests.
