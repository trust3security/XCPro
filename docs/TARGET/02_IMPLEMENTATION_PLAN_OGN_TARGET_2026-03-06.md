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

Expose in `OgnTrafficUseCase`:
- flows + suspend mutation methods

### 4.2 ViewModel/coordinator

`MapScreenViewModel`:
- `ognTargetEnabled: StateFlow<Boolean>`
- `ognTargetAircraftKey: StateFlow<String?>`
- `ognResolvedTarget: StateFlow<OgnTrafficTarget?>`

`MapScreenTrafficCoordinator`:
- `onSetOgnTarget(aircraftKey, enabled)` mutation path with existing error-to-toast contract
- mutation coalescing key (for rapid toggles)
- auto-clear targeted key when suppressed by ownship filter stream

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

### 4.4 Marker ring rendering

Preferred implementation:
- add `CircleLayer` target ring in `OgnTrafficOverlay` (no new bitmap resource required)
- feature property: `is_target` (0/1)
- ring layer filtered to `is_target == 1`
- ring radius derives from current icon size to keep visual proportion

Required lifecycle updates:
- `initialize()` creates ring layer
- `cleanup()` removes ring layer
- `bringToFront()` re-adds ring layer in correct order
- `findTargetAt(...)` queries ring layer too

### 4.5 Direct line overlay

Add `OgnTargetLineOverlay`:
- source/layer IDs for single line
- render one `LineString(ownship, target)`
- clear on invalid/unresolved state

Wire via `MapOverlayManagerRuntimeOgnDelegate`:
- cache latest ownship + resolved target + enabled
- render under OGN display-mode throttle
- style-reload recreation and map-detach cleanup

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
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenUseCases.kt`
- startup reset file(s) if session-local

Tests
- `OgnTrafficPreferencesRepositoryTest` new target cases.

Exit gate
- target enabled/key flows are persisted and normalized.

### Phase 2: ViewModel and coordinator policy

Files
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelStateBuilders.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenTrafficCoordinator.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModelRuntimeWiring.kt`

Tests
- `MapScreenViewModelTrafficSelectionTest` target state + cross-selection behavior
- coordinator policy tests for suppression clear + mutation coalescing

Exit gate
- target state is VM-observable and suppression-safe.

### Phase 3: Details sheet + intent wiring

Files
- `feature/map/src/main/java/com/example/xcpro/ogn/OgnMarkerDetailsSheet.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContentRuntimeSections.kt`
- binding/scaffold files only if strictly required

Tests
- `OgnMarkerDetailsSheetTest` toggle semantics
- compose or unit tests for details-to-intent wiring

Exit gate
- user can target/untarget from OGN details sheet without direct repository/UI coupling.

### Phase 4: OGN ring and target line overlays

Files
- `feature/map/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/OgnTargetLineOverlay.kt` (new)
- `feature/map/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenState.kt`
- minimal facade updates in `MapOverlayManagerRuntime.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`

Tests
- extend `MapOverlayManagerOgnLifecycleTest`
- add `OgnTargetLineOverlay` policy tests
- add OGN ring layer/hit-test tests

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
- File-size caps remain compliant (`MapScreenViewModel <= 350`).

## 7) Release acceptance criteria

- Feature behavior matches section 0 contract.
- No architecture rule violations.
- Required tests and verification commands pass.
- MapScreen SLO evidence attached for impacted IDs.
- Documentation updated in same change set.
