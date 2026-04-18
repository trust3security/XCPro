# Production-Grade Phased IP: OGN Target Ring and Direct Line

Date
- 2026-03-06

Owner
- XCPro Team

Status
- Draft implementation plan after focused code pass.

## 0) Purpose

Deliver a production-grade OGN Target feature where a pilot can target a tapped OGN glider from the details sheet, then see:
- a target ring around that glider marker
- a direct ownship-to-target line on the map

This plan supersedes the initial TARGET draft by closing lifecycle, SSOT, suppression, and map-runtime hardening gaps found in the focused code pass.

## 1) Focused pass findings integrated into this IP

1. Suppression/target drift gap
- Existing ownship suppression is authoritative in `OgnTrafficRepositoryRuntime` (`suppressedTargetIds`).
- Target state must auto-clear if the selected key becomes suppressed (same pattern as SCIA trail selection pruning).

2. Layer-order and lifecycle gap
- New target ring/line layers must participate in `initialize()`, `cleanup()`, and `bringToFront()` paths.
- Style reload and map detach paths must recreate/clear target visuals without orphan layers.

3. Tap hit-test gap
- If ring renders above icons, `findTargetAt(...)` must include the ring layer or marker taps can fail on the ring itself.

4. File-budget risk gap
- `MapScreenViewModel.kt` is `317` lines with `<= 350` cap.
- `OgnTrafficOverlay.kt` is `433` lines (near default 500 budget).
- `MapOverlayManagerRuntime.kt` is already large (`564` lines); new behavior should live in OGN delegate/new classes, not this file.

5. Session policy gap
- SCIA is reset on process start (`SciaStartupResetter`).
- Target persistence policy must be explicit (recommended: session-local reset on startup to avoid stale cross-flight targets).

6. Overlay-throttle gap
- OGN render cadence is controlled by `MapOverlayManagerRuntimeOgnDelegate` display mode.
- Target line/ring updates must follow the same throttle semantics to avoid jitter/perf regression.

7. Target-toggle render propagation gap
- `MapScreenOverlayEffects` currently updates OGN traffic render only from target list/altitude/unit dependencies.
- `MapOverlayManagerRuntimeOgnDelegate.updateTrafficTargets(...)` exits early when those inputs are unchanged.
- If target selection changes while traffic list is unchanged, ring visuals can fail to update unless target state is a first-class overlay input.

8. Overlay front-order signature gap
- `MapOverlayManagerRuntime.captureOverlayFrontOrderSignature()` currently tracks blue + OGN traffic + ADS-B overlays only.
- New target overlays must be included in signature accounting or front-order throttling can skip required reorder work.

## 2) Architecture contract before implementation

### 2.1 SSOT ownership

| Data | Authoritative owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| Target enabled/key | `OgnTrafficPreferencesRepository` | `Flow<Boolean>`, `Flow<String?>` | Compose local target state authority |
| Resolved target object | `MapScreenViewModel` (derived from SSOT key + live `ognTargets`) | `StateFlow<OgnTrafficTarget?>` | map-runtime lookup used as business authority |
| Target ring/line map objects | `MapOverlayManagerRuntimeOgnDelegate` + overlay classes | runtime-only state | ViewModel MapLibre objects |

### 2.2 Dependency direction

Must remain:
- `UI -> domain/use-case -> data`

Rules:
- Compose emits intent only.
- ViewModel uses use-cases only.
- Repository remains owner of persisted target state.
- Map runtime classes own MapLibre layers/sources only.

### 2.2A Boundary moves

| Responsibility | Old owner | New owner | Why | Validation |
|---|---|---|---|---|
| Target selection persistence | none | `OgnTrafficPreferencesRepository` | single SSOT + process-safe state | repository tests |
| Target ring/line map rendering | none | `MapOverlayManagerRuntimeOgnDelegate` + dedicated overlays | keep MapLibre ownership in UI runtime | overlay lifecycle tests |

### 2.2B Bypass removal plan

| Bypass callsite | Current bypass | Planned replacement | Phase |
|---|---|---|---|
| Details-sheet target toggle wiring | risk of direct repository/UI mutation | route only via `MapScreenViewModel` -> `MapScreenTrafficCoordinator` -> use-case | Phase 3 |

### 2.3 Time base

| Value | Time base | Why |
|---|---|---|
| Target freshness/visibility gating | Monotonic (`lastSeenMillis`) | consistent with OGN stale policy |
| Render throttle cadence | Monotonic | existing OGN display mode contract |
| Startup reset policy | Wall/startup lifecycle only | product/session behavior, not fusion logic |

### 2.4 Replay determinism

- Replay pipeline is unchanged.
- OGN target behavior is live OGN overlay logic only.

### 2.5 Boundary adapter check

- Persistence change stays in OGN preferences repository.
- No Android/framework APIs added to domain logic.
- No new global mutable singleton state.

## 3) Product decisions (explicit)

1. Single active target.
- Enabling target for one aircraft replaces previous target.

2. Toggle visibility.
- Target toggle is shown only for glider-class OGN markers (aircraft type code `1`) to match requirement wording.

3. Session scope (recommended).
- Target state persists during session but resets on app process start, aligned with current SCIA startup reset behavior.

4. Unresolved target behavior.
- Keep selected key in SSOT; render nothing when key is unresolved or stale.
- Do not fabricate fallback coordinates.

## 4) Technical design

### 4.1 Preferences and use-case

Add in `OgnTrafficPreferencesRepository`:
- `KEY_OGN_TARGET_ENABLED`
- `KEY_OGN_TARGET_AIRCRAFT_KEY`

Add APIs:
- `targetEnabledFlow`
- `targetAircraftKeyFlow` (normalized)
- `setTargetSelection(enabled, aircraftKey)`
- `clearTargetSelection()`

Rules:
- persist target enabled+key atomically in one datastore edit per mutation.
- normalize and dedupe keys using existing OGN key normalization helpers.

Expose in `OgnTrafficUseCase`:
- flows + suspend mutation methods

### 4.2 ViewModel/coordinator

`MapScreenViewModel`:
- `ognTargetEnabled: StateFlow<Boolean>`
- `ognTargetAircraftKey: StateFlow<String?>`
- `ognResolvedTarget: StateFlow<OgnTrafficTarget?>`
- `ognTargetSelectionLookup: StateFlow<OgnSelectionLookup>` (or equivalent derived helper)

`MapScreenTrafficCoordinator`:
- `onSetOgnTarget(aircraftKey, enabled)` mutation path with existing error-to-toast contract
- mutation coalescing key (for rapid toggles)
- auto-clear targeted key when suppressed by ownship filter stream
- resolve target key via alias-aware lookup (`buildOgnSelectionLookup`) rather than raw string equality

### 4.3 Details sheet wiring

`OgnMarkerDetailsSheet`:
- add second switch row: `Target this aircraft`
- inputs:
  - `isTargeted`
  - `onTargetChanged(Boolean)`
  - optional `targetToggleEnabled` (for non-glider markers)

`MapScreenContentRuntimeSections`:
- compute current marker target-selected state from target key lookup
- dispatch toggle intent through ViewModel/coordinator only

### 4.4 Target ring overlay

Preferred implementation:
- add dedicated `OgnTargetRingOverlay` (new) using a single-point `GeoJsonSource` + `CircleLayer`.
- avoid embedding ring state inside `OgnTrafficOverlay` to keep file budget safe and decouple from traffic list rerenders.
- ring radius derives from current icon size to keep visual proportion.
- ring input is explicit: `enabled + resolvedTarget + iconSizePx`.

Required lifecycle updates:
- `initialize()` creates ring source/layer.
- `cleanup()` removes ring source/layer.
- `bringToFront()` reorders ring layer relative to OGN icons/labels.
- tap resolution path includes ring layer so tapping the ring still selects the aircraft.

### 4.5 Direct line overlay

Add `OgnTargetLineOverlay`:
- source/layer IDs for single line
- render one `LineString(ownship, target)`
- clear on invalid/unresolved state

Wire via `MapOverlayManagerRuntimeOgnDelegate`:
- cache latest ownship + resolved target + enabled
- render under OGN display-mode throttle
- style-reload recreation and map-detach cleanup
- update both ring+line when target selection changes even if OGN traffic list is unchanged

Pass new inputs from UI runtime:
- ownship location (`MapLocationUiModel`) from existing bindings
- resolved target lat/lon from ViewModel state

### 4.6 Startup reset integration (if session-local policy chosen)

Update startup reset path:
- extend `SciaStartupResetter` (or rename to broader traffic startup resetter)
- clear target enabled/key on process start

## 5) Phased implementation

### Phase 0: Baseline and guard setup

Goal
- lock behavior and document impacted SLO IDs.

Work
- finalize this IP + test plan docs.
- add TODO-free file checklist and line-budget guard plan.

Exit gate
- no architecture drift in planned boundaries.

### Phase 1: SSOT target state in preferences/use-case

Files
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt`
- startup reset file(s) if session-local

Tests
- `OgnTrafficPreferencesRepositoryTest` new target cases.

Exit gate
- target enabled/key flows are persisted and normalized.

### Phase 2: ViewModel and coordinator policy

Files
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelStateBuilders.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModelRuntimeWiring.kt`

Tests
- `MapScreenViewModelTrafficSelectionTest` target state + cross-selection behavior
- coordinator policy tests for suppression clear + mutation coalescing
- add alias-resolution tests for persisted target keys vs canonical live keys

Exit gate
- target state is VM-observable and suppression-safe.

### Phase 3: Details sheet + intent wiring

Files
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnMarkerDetailsSheet.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntimeSections.kt`
- binding/scaffold files only if strictly required

Tests
- `OgnMarkerDetailsSheetTest` toggle semantics
- compose or unit tests for details-to-intent wiring

Exit gate
- user can target/untarget from OGN details sheet without direct repository/UI coupling.

### Phase 4: OGN ring and target line overlays

Files
- `feature/map/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt` (new)
- `feature/map/src/main/java/com/trust3/xcpro/map/OgnTargetLineOverlay.kt` (new)
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenState.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt` (front-order signature + lifecycle wiring)
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeStatus.kt` (target overlay status)
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRoot.kt`

Tests
- extend `MapOverlayManagerOgnLifecycleTest`
- add `OgnTargetLineOverlay` policy tests
- add `OgnTargetRingOverlay` lifecycle/hit-test tests
- add test proving target toggle redraw works when traffic list is unchanged

Exit gate
- ring + line render correctly across style reload and map detach.

### Phase 5: Hardening and docs sync

Work
- update pipeline doc OGN section with new target flow.
- update `docs/OGN/OGN.md` behavior section.
- update overlay status debug output to include target overlay status.

Exit gate
- docs and runtime behavior are synchronized.

### Phase 6: Verification and SLO evidence

Required commands
- `python scripts/arch_gate.py`
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

When available
- `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
- `./gradlew connectedDebugAndroidTest --no-parallel`

SLO evidence
- `MS-UX-01`, `MS-UX-03`, `MS-UX-04`, `MS-ENG-01` minimum

Exit gate
- all impacted mandatory SLOs pass or approved time-boxed deviation is recorded.

## 6) Explicit anti-regression checklist

- No business logic added to Compose.
- No new MapLibre object ownership in ViewModel.
- No stale ring/line layers after style change.
- Ring layer included in hit-testing query path.
- Target clears on ownship suppression updates.
- Target toggle redraw works even when OGN target list is unchanged.
- Front-order signature accounts for target overlays.
- File-size caps remain compliant (`MapScreenViewModel <= 350`).

## 7) Release acceptance criteria

- Feature behavior matches section 0 contract.
- No architecture rule violations.
- Required tests and verification commands pass.
- MapScreen SLO evidence attached for impacted IDs.
- Documentation updated in same change set.
