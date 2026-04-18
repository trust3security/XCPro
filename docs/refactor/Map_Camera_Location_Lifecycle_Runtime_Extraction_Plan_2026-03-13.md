# Map Camera Location Lifecycle Runtime Extraction Plan

## 0) Metadata

- Title: Extract camera/location/lifecycle runtime cluster into `:feature:map-runtime`
- Owner: Codex
- Date: 2026-03-13
- Issue/PR: TBD
- Status: Complete
- Execution contract:
  - `docs/refactor/Map_Camera_Location_Lifecycle_Runtime_Extraction_Agent_Contract_2026-03-13.md`
- Progress note:
  - 2026-03-13: Phase A implemented and verified:
    - added explicit shell-facing runtime ports in `:feature:map-runtime`:
      - `MapCameraRuntimePort`
      - `MapLocationRuntimePort`
      - `MapLifecycleRuntimePort`
      - `MapLocationPermissionRequester`
    - concrete owners in `feature:map` now implement those ports
    - direct shell consumers (`MapCameraEffects`, `MapComposeEffects`, `MapScreenRuntimeEffects`, `MapGestureSetup`, `LocationPermissionUi`, `MapTaskIntegration`, `OverlayActions`, `MapScreenRootEffects`, `MapLifecycleEffects`, `MapScreenRootHelpers`) now depend on the ports rather than the concrete owners
    - broad scaffold/content-host fan-out is intentionally deferred to Phase B
    - verification result:
      - `:feature:map-runtime:compileDebugKotlin` passed
      - `:feature:map:compileDebugKotlin` passed
      - `assembleDebug` passed
      - `enforceRules` only reports the pre-existing `MapScreenViewModel.kt` line-budget failure
      - `testDebugUnitTest` remains blocked by pre-existing unit-test compile failures in `GlideTargetRepositoryTest.kt` and `MapScreenViewModelTestRuntime.kt`
  - 2026-03-13: Phase B seam pass tightened the real shell fan-out boundary:
    - the active concrete fan-out is narrower than the original outline
    - actual blocker files are:
      - `MapScreenManagers.kt`
      - `MapScreenScaffoldInputModel.kt`
      - `MapScreenScaffoldInputs.kt`
      - `MapScreenScaffoldContentHost.kt`
      - `MapScreenContentRuntime.kt`
      - `MapOverlayStack.kt`
      - `MapScreenSections.kt`
      - `MapScreenRoot.kt`
    - `MapCameraEffects.kt`, `MapComposeEffects.kt`, `MapScreenRuntimeEffects.kt`, `MapGestureSetup.kt`, `LocationPermissionUi.kt`, `MapTaskIntegration.kt`, `OverlayActions.kt`, `MapScreenRootEffects.kt`, and `MapScreenRootHelpers.kt` are already port-based after Phase A and no longer drive Phase B
    - the remaining concrete blocker is the shell-local render-frame binding path in `MapViewHost`, which still calls `LocationManager.bindRenderFrameListener(...)` directly
    - Phase B therefore needs one shell-local `MapView` binding bridge in `feature:map`, not another runtime-module contract
  - 2026-03-13: Phase B implemented and verified:
    - `MapScreenManagers` now exposes camera/location/lifecycle through narrowed ports instead of concrete owner fields
    - the scaffold/content path now carries `MapCameraRuntimePort`, `MapLocationRuntimePort`, and a shell-local `MapLocationRenderFrameBinder`
    - `MapOverlayStack` and `MapViewHost` no longer depend on the concrete `LocationManager` or `MapCameraManager` types
    - verification result:
      - `:feature:map-runtime:compileDebugKotlin` passed
      - `:feature:map:compileDebugKotlin` passed
      - `assembleDebug` passed
      - `enforceRules` only reports the pre-existing `MapScreenViewModel.kt` line-budget failure
      - `testDebugUnitTest` remains blocked by pre-existing unit-test compile failures in `GlideTargetRepositoryTest.kt` and `MapScreenViewModelTestRuntime.kt`
  - 2026-03-13: Phase C seam pass corrected the camera owner move shape:
    - the Phase A/B shell-port work already removed the broad shell call-site blocker for `MapCameraManager`
    - the remaining camera blocker is now local to `MapCameraManager.kt` itself:
      - direct `MapScreenState` ownership
      - direct `MapLibreMap` access
      - direct `MapView` size access for zoom clamping and AAT edit zoom
    - the earlier helper-graph outline overstated the immediate Phase C payload:
      - `MapCameraManager.kt` does not currently depend on `MapCameraControllerProvider.kt`
      - `MapCameraManager.kt` does not currently depend on `MapViewSizeProvider.kt`
      - `MapCameraManager.kt` does not currently depend on `MapTrackingCameraController.kt`
      - `MapCameraManager.kt` does not currently depend on `MapCameraUpdateGateAdapter.kt`
    - `MapScreenManagers.kt` is the only production construction site for `MapCameraManager`, so the move remains low-churn once its direct shell-handle ownership is isolated behind a narrow camera surface port
    - there is still no concrete owner-parity test for `MapCameraManager`; current coverage remains limited to the pure `resolveCameraBearingUpdate(...)` helper
  - 2026-03-13: Phase C implemented and verified:
    - added a shell-local `MapCameraSurfaceAdapter` in `feature:map` and a runtime-side `MapCameraSurfacePort` in `:feature:map-runtime`
    - moved `MapCameraManager` into `:feature:map-runtime`
    - moved `MapZoomConstraints` into `:feature:map-runtime` because the moved camera owner now depends on it and the shell still consumes it through the same package-stable contract
    - `MapScreenManagers` remains the shell construction site and now passes the surface adapter into the moved owner
    - moved `MapCameraManagerBearingUpdateTest` into `:feature:map-runtime`
    - verification result:
      - `:feature:map-runtime:compileDebugKotlin` passed
      - `:feature:map:compileDebugKotlin` passed
      - `assembleDebug` passed
      - `enforceRules` only reports the pre-existing `MapScreenViewModel.kt` line-budget failure after rule ownership was updated for the new camera-owner path
      - `testDebugUnitTest` remains blocked by pre-existing unit-test compile failures in `GlideTargetRepositoryTest.kt` and `MapScreenViewModelTestRuntime.kt`
    - warm edit timing signal:
      - tiny edit in moved `MapCameraManager.kt` -> `:feature:map-runtime:compileDebugKotlin` in about `15.9s`
      - tiny edit in retained shell `MapScreenManagers.kt` -> `:feature:map:compileDebugKotlin` in about `55.9s`
  - 2026-03-13: Phase D seam pass tightened the real location blocker set:
    - the broad shell fan-out work is already done for location; `MapScreenRoot`, `MapScreenRuntimeEffects`, `MapComposeEffects`, `LocationPermissionUi`, `MapGestureSetup`, `MapOverlayStack`, and the scaffold/content path now depend on `MapLocationRuntimePort` plus the shell-local `MapLocationRenderFrameBinder`
    - the remaining blocker is now local to `LocationManager.kt` itself:
      - direct `Context`
      - direct `MapScreenState`
      - concrete `LocationSensorsController`
      - concrete `DisplayPoseRenderCoordinator`
      - concrete `RenderFrameSync`
      - concrete `MapPositionController` / interaction controller graph
    - `MapLifecycleManager` remains a downstream blocker for later phases because it still depends on the concrete `LocationManager`
  - 2026-03-13: Phase D implemented and verified:
    - moved `LocationManager` and the runtime-owned display-pose/location pipeline into `:feature:map-runtime`
    - added shell-local adapters for runtime-owned camera/location surfaces:
      - `MapLocationOverlayAdapter`
      - `MapDisplayPoseSurfaceAdapter`
      - `MapLibreCameraControllerProvider`
      - `MapScreenSizeProvider`
      - `MapCameraUpdateGateAdapter`
    - moved the runtime-owned display-pose helper cluster into `:feature:map-runtime`:
      - `DisplayClock`
      - `DisplayPoseCoordinator`
      - `DisplayPoseFrameLogger`
      - `DisplayPosePipeline`
      - `DisplayPoseRenderCoordinator`
      - `DisplayPoseSmoother`
      - `DisplayPoseSelector`
      - `DisplayPoseAdaptiveSmoothing`
      - `LocationFeedAdapter`
      - `MapPositionController`
      - `MapTrackingCameraController`
      - `MapUserInteractionController`
      - `MapShiftBiasResetter`
      - `MapCameraPolicy`
      - `IconHeadingSmoother`
    - verification result:
      - `:feature:map-runtime:compileDebugKotlin` passed
      - `:feature:map:compileDebugKotlin` passed
      - `assembleDebug` passed
      - `:feature:map-runtime:testDebugUnitTest` passed
      - `enforceRules` only reports the pre-existing `MapScreenViewModel.kt` line-budget failure after updating rule ownership for the moved `LocationManager`
      - `testDebugUnitTest` remains blocked by the pre-existing `GlideTargetRepositoryTest.kt` and `MapScreenViewModelTestRuntime.kt` compile failures
  - 2026-03-13: Phase E implemented and verified:
    - moved `MapLifecycleManager` into `:feature:map-runtime`
    - added shell-local lifecycle bridges:
      - `MapLifecycleSurfaceAdapter`
      - `MapOrientationRuntimePort` implementation in `MapOrientationManager`
      - `MapRenderFrameCleanupPort` implementation in `MapLocationRenderFrameBinderAdapter`
    - kept `MapLifecycleEffects.kt` shell-owned in `feature:map`
    - verification result:
      - `:feature:map-runtime:compileDebugKotlin` passed
      - `:feature:map:compileDebugKotlin` passed
      - `:feature:map-runtime:testDebugUnitTest` passed
      - `enforceRules` only reports the pre-existing `MapScreenViewModel.kt` line-budget failure
      - `testDebugUnitTest` remains blocked by the pre-existing `GlideTargetRepositoryTest.kt` and `MapScreenViewModelTestRuntime.kt` compile failures
  - 2026-03-13: Phase F completed and the stop rule is triggered:
    - warm no-change baselines:
      - `:feature:map-runtime:compileDebugKotlin` about `1.8s`
      - `:feature:map:compileDebugKotlin` about `2.2s`
    - warm one-line edit timings in moved runtime owners:
      - `MapCameraManager.kt` about `18.5s`
      - `LocationManager.kt` about `16.8s`
      - `MapLifecycleManager.kt` about `16.6s`
    - warm one-line edit timings in retained shell files:
      - `MapScreenManagers.kt` about `55.6s`
      - `MapScreenScaffoldInputs.kt` about `48.6s`
    - conclusion:
      - the camera/location/lifecycle owner move produced the intended compile-scope win
      - moved-owner edits now compile in `:feature:map-runtime` instead of paying the retained `:feature:map` shell cost
      - further structural work under this dedicated plan should stop unless a fresh seam pass proves another broad module-boundary win
    - verification note:
      - `assembleDebug` is currently blocked by an unrelated app compile issue in `AppNavGraph.kt` (`MyAbout` unresolved)
      - `AboutKt.class` is present in `feature:map` tmp classes but missing from the packaged library jar, so this is classified as an existing packaging/classpath issue outside this extraction scope

## 1) Scope

- Problem statement:
  - After the forecast, weather, tasks, WeGlide, and overlay-runtime moves, the retained `feature:map` shell is still the dominant incremental compile hotspot for normal development.
  - Recent warm shell-edit timings remain in the same heavy band (`~44s` to `~50s`) across unrelated shell files, which indicates the next meaningful win must come from a broad module-boundary move rather than another helper extraction.
  - The largest remaining runtime-heavy cluster still owned by `feature:map` is:
    - `MapCameraManager.kt`
    - `LocationManager.kt`
    - `MapLifecycleManager.kt`
- Why now:
  - The overlay-runtime C1/C2/C3 work proved the boundary strategy works:
    - a small edit in moved runtime code compiled in `:feature:map-runtime` in about `12.3s`
    - a comparable retained shell edit in `feature:map` still took about `43.7s`
  - That is strong evidence that the next broad compile-speed win comes from moving more real runtime ownership out of `feature:map`.
- In scope:
  - Dedicated production-grade extraction plan for the camera/location/lifecycle runtime cluster.
  - Shell fan-out narrowing around:
    - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenManagers.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputModel.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/MapCameraEffects.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`
  - Runtime-cluster extraction for:
    - `feature/map/src/main/java/com/trust3/xcpro/map/MapCameraManager.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/LocationManager.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleEffects.kt`
- Out of scope:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapRuntimeController.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapTaskScreenManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt` as an owner move in the first cycle
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` line-budget work
  - Unrelated repo-health failures in app/map tests
- User-visible impact:
  - No intended product behavior change.
  - This plan exists to reduce compile scope and preserve current camera/location/lifecycle behavior.

### 1.1 Critical Shell Fan-Out Boundary

These files are part of the problem, not incidental cleanup:

| File | Why it matters | Planned role |
|---|---|---|
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenManagers.kt` | Shell currently constructs the concrete runtime managers directly | Keep as shell `remember...` wrapper; narrow what it constructs and exposes |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt` | Fans concrete manager types through the UI shell | Narrow to shell-safe runtime handles/ports |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputModel.kt` | Data carrier for concrete manager types in the scaffold path | Narrow to shell-safe runtime handles/ports |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldContentHost.kt` | Thin shell bridge still forwards concrete manager types into the live content path | Keep shell-owned; switch it to narrowed shell-safe handles during shell fan-out narrowing |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt` | Live content host still threads `LocationManager` and `MapCameraManager` through the main map body | Narrow to shell-safe runtime handles/ports before owner moves |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRoot.kt` | Shell anchor still sets profile state on the concrete `LocationManager` and creates the concrete permission launcher | Keep shell-owned; switch it to shell-safe location/lifecycle handles during shell fan-out narrowing |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootHelpers.kt` | Still feeds concrete `MapCameraManager` into `MapCameraEffects` through `rememberMapRuntimeController(...)` | Keep shell-owned; switch it to the narrowed camera-facing contract during shell fan-out narrowing |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapCameraEffects.kt` | Direct Compose dependency on `MapCameraManager` | Keep shell-owned; consume a narrower camera-facing contract |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt` | Direct shell dependency on `LocationManager` and `MapLifecycleManager` | Keep shell-owned; consume shell bridges or narrow runtime ports |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/effects/MapComposeEffects.kt` | Direct Compose/runtime dependency on `LocationManager` for permissions, live/replay updates, display-pose cadence, and frame dispatch | Keep shell-owned; move to a narrow location-facing runtime port rather than the concrete class |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRuntimeEffects.kt` | Direct shell/runtime dependency on `LocationManager` for display-pose snapshots, frame listeners, and snail-trail updates | Keep shell-owned; consume a narrow display-pose/runtime port instead of the concrete manager |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapGestureSetup.kt` | Gesture shell calls directly into `LocationManager` and `MapCameraManager` for user interaction and AAT edit camera control | Keep shell-owned; consume narrow gesture-facing camera/location ports |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/LocationPermissionUi.kt` | Permission launcher callback is wired directly to `LocationManager` | Keep shell-owned; consume a permission-facing location port rather than the concrete manager |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapTaskIntegration.kt` | AAT edit flow calls `MapCameraManager.restoreAATCameraPosition()` directly | Keep shell-owned; consume a narrow AAT camera action port |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/OverlayActions.kt` | Shell wrapper passes concrete `MapCameraManager` into the AAT FAB path | Keep shell-owned; forward only the narrowed camera action port |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt` | Live shell host still carries `MapInitializer`, `LocationManager`, and `MapCameraManager` through the rendered map stack | Retain as shell consumer; do not treat as an incidental follow-up |
| `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSections.kt` | `MapMainLayers` / `MapViewHost` still thread `MapInitializer` and `LocationManager` through the live map host | Retain as shell consumer; keep owner move separate from map-host wiring |
| `feature/map/src/main/java/com/trust3/xcpro/map/MapInitializer.kt` | Still co-constructed beside the runtime cluster | Explicitly out of scope for first owner-move cycle; shell must remain coherent without moving it |

### 1.1A Method-Level Shell Surface

These are the concrete methods/fields the shell currently uses and therefore the exact port surface the plan must replace.

| Owner | Current shell usage | Live call sites |
|---|---|---|
| `MapCameraManager` | `targetZoom`, `targetLatLng`, `isTrackingLocation`, `showReturnButton`, `updateBearing(...)`, `applyAnimatedZoom(...)` | `MapCameraEffects.kt` |
| `MapCameraManager` | `zoomToAATAreaForEdit(...)`, `restoreAATCameraPosition()` | `MapGestureSetup.kt`, `MapTaskIntegration.kt`, `OverlayActions.kt` |
| `LocationManager` | `checkAndRequestLocationPermissions(...)`, `onLocationPermissionsResult(...)` | `MapComposeEffects.kt`, `LocationPermissionUi.kt` |
| `LocationManager` | `updateLocationFromGPS(...)`, `updateLocationFromFlightData(...)`, `updateOrientation(...)`, `setReplaySpeedMultiplier(...)`, `onDisplayFrame()` | `MapComposeEffects.kt` |
| `LocationManager` | `getDisplayPoseLocation()`, `getDisplayPoseTimestampMs()`, `getDisplayPoseSnapshot()`, `setDisplayPoseFrameListener(...)` | `MapScreenRuntimeEffects.kt` |
| `LocationManager` | `bindRenderFrameListener(...)`, `unbindRenderFrameListener()` | `MapScreenSections.kt`, `MapLifecycleManager.kt` |
| `LocationManager` | `showReturnButton()`, `handleUserInteraction(...)` | `MapGestureSetup.kt` |
| `LocationManager` | `recenterOnCurrentLocation()`, `returnToSavedLocation()` | `MapScreenContentRuntime.kt` via action-button layer |
| `LocationManager` | `restartSensorsIfNeeded()`, `stopLocationTracking()`, `isGpsEnabled()` | `MapLifecycleManager.kt`, `MapLifecycleEffects.kt` |
| `LocationManager` | `setActiveProfileId(...)` | `MapScreenRoot.kt` |
| `MapLifecycleManager` | `handleLifecycleEvent(...)`, `syncCurrentOwnerState(...)`, `cleanup()` | `MapLifecycleEffects.kt`, `MapScreenRootEffects.kt`, `MapScreenScaffoldInputs.kt` |

### 1.2 Exact Current Blockers

These are the concrete blockers the next implementation phases must remove:

- `MapCameraManager.kt`
  - still owns direct `MapScreenState` access and `MapLibreMap` camera operations
  - still exposes shell-facing behavior used directly by:
    - `MapCameraEffects.kt`
    - `MapGestureSetup.kt`
    - `MapTaskIntegration.kt`
    - `OverlayActions.kt`
  - the remaining owner-move blocker is now local:
    - `moveTo(...)`, `handleDoubleTapZoom(...)`, `updateBearing(...)`, `applyAnimatedZoom(...)`, `animateToTarget(...)`, `zoomToAATAreaForEdit(...)`, and `restoreAATCameraPosition()` still read concrete `mapState.mapLibreMap`
    - `clampZoom(...)` and `zoomToAATAreaForEdit(...)` still read concrete `mapState.mapView`
    - those concrete shell handles must stay shell-owned, so Phase C needs a narrow camera-surface bridge instead of moving `MapScreenState`
  - the broader camera helper graph remains relevant to later camera/runtime work, but it is not the immediate blocker for moving `MapCameraManager.kt` itself
- `LocationManager.kt`
  - still owns `Context`, `MapScreenState`, permission flow entrypoints, render-frame binding, live/replay location ingestion, and display-pose dispatch
  - still owns `LocationSensorsController`, so permission/start-stop semantics are not yet separated from the concrete runtime owner
  - still has direct shell consumers in:
    - `MapComposeEffects.kt`
    - `MapScreenRuntimeEffects.kt`
    - `MapScreenRootEffects.kt`
    - `MapGestureSetup.kt`
    - `LocationPermissionUi.kt`
    - `MapOverlayStack.kt`
    - `MapScreenSections.kt`
  - `MapScreenSections.kt` binds render-frame listeners directly in `MapViewHost`, so the live map host still knows about the concrete location owner
  - `MapScreenRoot.kt` calls `setActiveProfileId(...)` directly on `LocationManager`, so profile-scoped orientation preferences are still coupled to the concrete owner
  - still depends on shell-backed helper graph pieces:
    - `LocationSensorsController.kt` owns permission request, permission-result handling, and start/stop/restart behavior around `ActivityResultLauncher` and `Context`
    - `MapUserInteractionController.kt` still depends on `MapCameraControllerProvider` and owns recenter/return-button behavior that the shell triggers directly
    - `DisplayPoseRenderCoordinator.kt` still depends directly on `MapScreenState`, `MapStateReader`, `MapTrackingCameraController`, and `MapPositionController`
    - `MapPositionController.kt` still mutates shell-owned overlays via `MapScreenState.blueLocationOverlay`
    - `RenderFrameSync.kt` still owns concrete `MapView` render-frame binding
- `MapLifecycleManager.kt`
  - still owns direct `MapScreenState` map-view lifecycle mutation and overlay cleanup
  - still depends on concrete `LocationManager`
  - still has direct shell consumers in:
    - `MapScreenRootEffects.kt`
    - `MapLifecycleEffects.kt`
    - `MapScreenScaffoldInputs.kt`
  - `MapLifecycleEffects.LocationCleanupEffect(...)` still depends directly on the concrete `LocationManager`
- `MapScreenSections.kt`
  - `MapViewHost` still calls `locationManager.bindRenderFrameListener(...)` on both `factory` and `update`, so render-frame binding is an active shell concern that must be isolated before or during the location owner move
- `MapScreenScaffoldContentHost.kt` and `MapScreenContentRuntime.kt`
  - still forward concrete `LocationManager` and `MapCameraManager` through the live shell/content path
  - that means narrowing only `MapScreenManagers` and `MapScreenScaffoldInputs` is not enough; the content-host path must also stop carrying the concrete moved owners
- `MapScreenRoot.kt` and `MapScreenRootHelpers.kt`
  - `MapScreenRoot.kt` still calls `locationManager.setActiveProfileId(...)` directly and creates the permission launcher from the concrete `LocationManager`
  - `MapScreenRootHelpers.kt` still passes the concrete `MapCameraManager` into `MapCameraEffects.AllCameraEffects(...)`
  - that means the shell anchor itself is part of Phase B, not just the scaffold model
- `MapScreenManagers.kt`
  - remains the shell construction hub for:
    - `MapCameraManager`
    - `LocationManager`
    - `MapLifecycleManager`
    - `MapInitializer`
    - `MapOverlayManager`
    - `SnailTrailManager`
  - that means moving the runtime cluster without first narrowing construction and exposure would still leave broad concrete fan-out in `feature:map`
- `MapScreenScaffoldInputs.kt` and `MapScreenScaffoldInputModel.kt`
  - still carry concrete manager types through the shell path
  - a runtime owner move without narrowing these signatures would reduce ownership but still leave the shell tightly coupled to the moved implementations
  - these are not the only shell fan-out carriers; the live content-host path also still forwards the concrete managers
- Existing test coverage is still skewed toward shell-owned or helper-only behavior:
  - `MapCameraManagerBearingUpdateTest.kt` only covers the pure bearing resolver helper, not owner-move parity for the concrete manager
  - `MapLifecycleManagerResumeSyncTest.kt` and the other lifecycle tests instantiate the concrete `MapLifecycleManager` type with a mocked `LocationManager`
  - `MapLifecycleManagerWeatherCleanupTest.kt` and `MapLifecycleManagerScaleBarCleanupTest.kt` assert direct cleanup against `MapScreenState` overlay/controller handles, which means lifecycle cleanup semantics are still tightly tied to the concrete owner
  - there is no existing direct shell-port parity coverage yet for:
    - permission launcher wiring
    - AAT edit camera restore path
    - display-pose frame listener wiring in `MapScreenRuntimeEffects.kt`
    - render-frame binding in `MapViewHost`
    - content-host wiring proving `MapScreenScaffoldContentHost` / `MapScreenContentRuntime` no longer depend on the moved concrete owners

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Camera target / zoom / return snapshot | `MapStateStore` via `MapStateReader` + `MapStateActions` | `StateFlow` + action methods | manager-local target/zoom mirrors |
| Current user location stored for UI/runtime | `MapStateStore` | `currentUserLocation` through `MapStateReader` | shell-local duplicate location state |
| Display pose frame output | runtime location/display-pose pipeline | `DisplayPoseSnapshot` | duplicate shell pose caches |
| MapView / MapLibre concrete handles | `MapScreenState` shell owner | shell bridge only | runtime-owned copies or secondary holders |
| Lifecycle readiness state for the current map view | runtime `MapLifecycleManager` + shell bridge | shell lifecycle callbacks and narrow runtime API | duplicate shell lifecycle flags outside the lifecycle owner |
| Orientation lifecycle state | `MapOrientationManager` | injected collaborator | duplicate runtime shadow state |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI shell -> runtime contracts -> runtime implementation -> core/leaf modules`

- Modules/files touched:
  - `feature:map`
  - `feature:map-runtime`
  - active refactor plans under `docs/refactor`
- Any boundary risk:
  - `:feature:map-runtime` must not depend back on `feature:map`
  - Compose/UI shell types must not become runtime public contracts
  - `MapView`, `MapLibreMap`, and `MapScreenState` must remain shell-owned or be accessed only via explicit narrow ports

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Camera positioning / zoom / bearing runtime logic | `feature:map` | `:feature:map-runtime` | heavy runtime owner still inflates shell compile cost | module compile + camera parity tests |
| Live/replay location display-pose pipeline | `feature:map` | `:feature:map-runtime` | broad runtime cluster with high fan-out through shell | module compile + replay/location tests |
| Runtime lifecycle handling for map resources | `feature:map` | `:feature:map-runtime` | should live with runtime owners, not with Compose shell effects | module compile + lifecycle parity tests |
| Compose lifecycle bridge | `feature:map` | `feature:map` (retained) | shell-only lifecycle observer/effect wiring | shell compile + UI bridge tests |
| Shell manager fan-out | broad direct concrete-manager wiring in `feature:map` | narrowed shell wrapper over runtime ports | reduce concrete runtime exposure in shell signatures | shell compile + facade tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapCameraEffects.kt` | direct `MapCameraManager` dependency in Compose shell | shell-safe camera port/handle | Phase A |
| `MapScreenRootEffects.kt` | direct `LocationManager` / `MapLifecycleManager` dependency in shell effects | shell bridges over runtime ports | Phase B |
| `MapScreenScaffoldInputs.kt` | fans concrete managers through UI shell | narrowed shell-facing runtime handles | Phase B |
| `MapScreenScaffoldInputModel.kt` | carries concrete manager types in scaffold model | narrowed shell-facing runtime handles | Phase B |
| `MapScreenScaffoldContentHost.kt` | forwards concrete managers into the live content path | narrowed shell-facing runtime handles | Phase B |
| `MapScreenContentRuntime.kt` | carries concrete managers through the main map body | narrowed shell-facing runtime handles | Phase B |
| `MapScreenManagers.kt` | direct construction exposure of concrete runtime managers | shell wrapper over explicit runtime owner bundle | Phase B |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Camera update throttling / animation gating | Monotonic | frame/update cadence must stay stable and wall-time independent |
| Display pose timestamps | Replay or Monotonic, depending on source | existing display-pose pipeline already distinguishes live vs replay cadence |
| Replay heading/fix providers | Replay | same-input replay determinism must be preserved |
| Lifecycle event sequencing | Lifecycle state, not wall time | lifecycle transitions remain driven by owner state rather than time comparisons |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - `Main`: `MapView` / `MapLibreMap` interaction and lifecycle dispatch
  - Existing background ownership stays unchanged; this plan does not introduce new background dispatchers
- Primary cadence/gating sensor:
  - render-frame cadence + location/sensor updates
- Hot-path latency budget:
  - no additional frame delay beyond the current shell behavior
  - target is parity, not new animation behavior

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - unchanged from current behavior
  - extraction must not introduce wall-time or shell-only behavior into replay handling

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Shell/runtime reverse dependency | `ARCHITECTURE.md` dependency direction | review + module compile | `:feature:map-runtime:compileDebugKotlin`, `:feature:map:compileDebugKotlin` |
| Concrete `MapView`/Compose leak into runtime public surface | `CODING_RULES.md` architecture boundaries | review + targeted tests | this plan + shell bridge tests |
| Replay drift in moved location/display-pose runtime | replay determinism rules | targeted unit tests | planned runtime location/replay tests |
| Lifecycle cleanup drift | map/lifecycle hardening rules | targeted unit tests + manual smoke | planned lifecycle parity tests |
| Camera behavior drift | map/camera behavior parity | targeted unit tests | planned camera parity tests |

### 2.7 Visual UX SLO Contract (Mandatory for map/overlay/replay interaction changes)

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Camera tracking / recenter behavior remains unchanged | `MS-ENG-CAM-01` | current debug behavior | no regression | camera parity tests + manual smoke | Phase C |
| Live/replay display-pose rendering remains unchanged | `MS-ENG-LOC-01` | current replay/live behavior | no regression | location/replay tests | Phase D |
| Map lifecycle pause/resume/destroy cleanup remains unchanged | `MS-ENG-LIFECYCLE-01` | current lifecycle behavior | no regression | lifecycle parity tests + manual smoke | Phase E |

## 3) Data Flow (Before -> After)

Before:

```text
MapScreenRoot
  -> rememberMapScreenManagers(...)
    -> constructs MapCameraManager / LocationManager / MapLifecycleManager directly
  -> rememberMapScreenScaffoldInputs(...)
    -> fans concrete managers through scaffold inputs
  -> MapCameraEffects / MapScreenRootEffects
    -> consume concrete runtime managers directly
```

After:

```text
MapScreenRoot
  -> rememberMapScreenManagers(...) [shell wrapper retained]
    -> obtains narrowed runtime-facing handles from :feature:map-runtime
  -> rememberMapScreenScaffoldInputs(...)
    -> carries shell-safe runtime handles/ports only
  -> MapCameraEffects / MapScreenRootEffects
    -> consume shell bridges or narrow runtime contracts
  -> :feature:map-runtime
    -> owns camera/location/lifecycle concrete implementation
```

## 4) Implementation Phases

### Phase A - Runtime Contract Extraction

- Status:
  - Complete on 2026-03-13

- Goal:
  - extract or confirm the narrow runtime-facing contracts needed for camera/location/lifecycle ownership without moving concrete implementations yet
- Files to change:
  - new or updated runtime-facing contracts in `feature:map-runtime`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapCameraManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/LocationManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt`
- Tests to add/update:
  - targeted contract tests for camera/location/lifecycle runtime APIs
- Exit criteria:
  - no new runtime public contract depends directly on Compose shell types
  - any remaining `MapScreenState` / `MapView` / `MapLibreMap` touchpoints are explicitly documented and localized
  - explicit shell-facing ports exist for the live consumers in:
    - `MapCameraEffects.kt`
    - `MapComposeEffects.kt`
    - `MapScreenRuntimeEffects.kt`
    - `MapGestureSetup.kt`
    - `LocationPermissionUi.kt`
    - `MapTaskIntegration.kt`
    - `OverlayActions.kt`
    - `MapScreenRootEffects.kt`
    - `MapScreenScaffoldContentHost.kt`
    - `MapScreenContentRuntime.kt`

### Phase B - Shell Fan-Out Boundary Narrowing

- Status:
  - Complete on 2026-03-13

- Goal:
  - narrow the shell signatures so the moved runtime cluster is not still dragged through the scaffold/effects path as concrete manager types
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenManagers.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldInputModel.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffoldContentHost.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenSections.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRoot.kt`
  - one new shell-local render-frame binding bridge in `feature:map`
  - keep `MapLifecycleEffects.kt` shell-owned
- Tests to add/update:
  - shell bridge tests proving scaffold/effects no longer require concrete runtime implementations
  - explicit shell-port tests for:
    - render-frame binding wiring in `MapViewHost`
    - content-host wiring proving the live `MapScreenContentRuntime` path no longer needs the concrete moved owners
- Exit criteria:
  - scaffold/content paths no longer fan concrete `MapCameraManager` / `LocationManager` / `MapLifecycleManager` types through the shell
  - `rememberMapScreenManagers(...)` remains shell-owned but no longer exposes those owners as concrete aggregate fields
  - `MapScreenScaffoldContentHost` / `MapScreenContentRuntime` no longer act as a second concrete-manager fan-out path
  - `MapOverlayStack.kt` and `MapViewHost` in `MapScreenSections.kt` consume only narrowed shell-safe handles where the moved cluster is involved

### Phase C - Camera Runtime Owner Move

- Status:
  - Complete on 2026-03-13

- Goal:
  - narrow `MapCameraManager.kt` off direct shell handle ownership, then move it into `:feature:map-runtime`
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapCameraManager.kt`
  - one narrow camera-surface bridge for shell-owned `MapLibreMap` / `MapView` access
  - any narrow camera-facing contract files from Phase A/B
- Tests to add/update:
  - camera parity tests
  - AAT edit camera restore path test coverage
- Exit criteria:
  - `MapCameraManager` no longer owns `MapScreenState` directly
  - `MapCameraManager` no longer reads `MapView` / `MapLibreMap` through `MapScreenState`
  - `MapCameraManager` compiles and tests in `:feature:map-runtime`
  - shell camera effects depend only on the narrowed camera-facing contract
  - shell gesture/edit paths no longer depend on the concrete moved camera owner

### Phase D - Location / Display-Pose Runtime Owner Move

- Status:
  - Complete on 2026-03-13

- Goal:
  - move `LocationManager.kt` and its runtime-owned display-pose pipeline into `:feature:map-runtime`
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/LocationManager.kt`
  - directly-owned runtime pipeline collaborators proven clean by seam review, likely including:
    - `feature/map/src/main/java/com/trust3/xcpro/map/LocationSensorsController.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/MapUserInteractionController.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/DisplayPoseRenderCoordinator.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/MapPositionController.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/RenderFrameSync.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/DisplayPoseCoordinator.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/DisplayPosePipeline.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/map/LocationFeedAdapter.kt`
    - plus any required shell-bridge ports for `MapCameraControllerProvider.kt`, `MapViewSizeProvider.kt`, `MapCameraUpdateGate.kt`, and `MapTrackingCameraController.kt`
- Tests to add/update:
  - replay/location parity tests
  - display-pose output tests
  - permission callback and sensor-start bridge tests
- Exit criteria:
  - `LocationManager` compiles and tests in `:feature:map-runtime`
  - shell no longer owns the runtime location/display-pose implementation
  - shell compose/effects paths consume only narrowed location-facing ports
  - moved location/display-pose owner no longer depends directly on shell-owned `ActivityResultLauncher`, `MapView`, or `MapScreenState`

### Phase E - Lifecycle Runtime Owner Move

- Status:
  - Complete on 2026-03-13

- Goal:
  - move `MapLifecycleManager.kt` runtime class into `:feature:map-runtime`
  - keep `MapLifecycleEffects.kt` shell-owned
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleEffects.kt`
- Tests to add/update:
  - lifecycle parity tests
- Exit criteria:
  - lifecycle runtime owner compiles in `:feature:map-runtime`
  - Compose lifecycle observer/effect remains shell-owned in `feature:map`
  - lifecycle runtime no longer depends on a shell-owned concrete `LocationManager`
  - lifecycle runtime no longer mutates `MapScreenState` directly for `MapView` lifecycle dispatch or overlay cleanup; those shell surfaces are isolated behind explicit bridges

### Phase F - Remeasure and Stop Rule

- Status:
  - Complete on 2026-03-13

- Goal:
  - verify the move produced a real compile-scope win before any further structural work
- Files to change:
  - plan docs only if a follow-up is justified
- Tests to add/update:
  - none
- Exit criteria:
  - one-line edits in moved runtime files compile in `:feature:map-runtime`
  - moved-cluster edits no longer require `:feature:map:compileDebugKotlin`
  - if broad shell compile is still dominated by another concrete cluster, stop and re-evaluate instead of guessing the next phase

## 5) Test Plan

- Unit tests:
  - add targeted camera parity coverage
  - add targeted location/display-pose parity coverage
  - add targeted lifecycle parity coverage
  - add shell bridge tests for narrowed scaffold/effects paths
- Replay/regression tests:
  - rerun replay/location paths affected by `LocationManager`
- UI/instrumentation tests (if needed):
  - manual smoke or instrumentation for lifecycle resume/pause if unit coverage is insufficient
- Degraded/failure-mode tests:
  - location permission flow
  - replay-active sensor restart gating
  - map cleanup on destroy
- Boundary tests for removed bypasses:
  - scaffold/effects use shell-safe runtime handles instead of concrete moved managers

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Targeted compile checks:

```bash
./gradlew :feature:map-runtime:compileDebugKotlin
./gradlew :feature:map:compileDebugKotlin
```

Warm-edit measurement checks:

```bash
./gradlew :feature:map-runtime:compileDebugKotlin
./gradlew :feature:map:compileDebugKotlin
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| `MapView` / `MapLibreMap` leaks into runtime public contracts | High | keep shell bridges explicit and review every runtime-facing API in Phase A | Codex |
| Shell/runtime back-edge through `MapScreenState` or shell-only helpers | High | localize `MapScreenState` access and keep shell-only wrappers in `feature:map` | Codex |
| Lifecycle regressions on pause/resume/destroy | High | keep `MapLifecycleEffects.kt` shell-owned and add lifecycle parity tests before moving runtime class | Codex |
| Replay or sensor behavior drift during `LocationManager` move | High | preserve current time bases and add replay/location parity tests | Codex |
| Permission/start-stop behavior regresses during `LocationManager` move | High | keep permission launcher shell-owned and add explicit permission-bridge tests before moving the owner | Codex |
| AAT edit-mode camera restore path breaks during `MapCameraManager` move | Medium | add explicit shell-port coverage for `restoreAATCameraPosition()` wiring before the owner move | Codex |
| Display-pose/snail-trail shell updates drift during `LocationManager` move | High | add explicit `MapScreenRuntimeEffects` bridge tests before moving the owner | Codex |
| Shell fan-out remains broad even after an owner move | High | Phase B is mandatory; do not move the owners until `MapScreenManagers`, scaffold inputs, and effect call sites are narrowed | Codex |
| Shell signature width does not materially shrink | Medium | Phase B is mandatory before class moves; stop if it does not remove concrete manager fan-out | Codex |
| `MapInitializer.kt` becomes an implicit blocker | Medium | keep it explicitly out of scope for the first cycle and document shell construction coherence | Codex |
| Existing unrelated repo failures obscure signal | Medium | treat existing blockers as baseline and reject any new failures introduced by this work | Codex |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- `:feature:map-runtime` does not depend back on `feature:map`
- Shell fan-out files no longer expose moved concrete managers where the phase says they should not
- Moved runtime files compile in `:feature:map-runtime`
- Required verification commands are rerun for each implementation phase
- No new failures are introduced beyond the current known unrelated blockers:
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt` line-budget gate
  - existing unrelated test failures outside this extraction scope

## 8) Rollback Plan

- What can be reverted independently:
  - Phase A contract extraction
  - Phase B shell fan-out narrowing
  - each owner move phase (`C`, `D`, `E`) independently
- Recovery steps if regression is detected:
  - revert only the most recent phase
  - keep shell bridges/contracts if they remain architecture-safe
  - rerun `:feature:map-runtime:compileDebugKotlin`, `:feature:map:compileDebugKotlin`, `testDebugUnitTest`, and `assembleDebug`
