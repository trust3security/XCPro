# Aircraft Declutter Implementation

## Summary

Pass 2 was implemented with **Path B**.

Path B keeps authoritative aircraft coordinates unchanged and performs declutter as a display-only runtime step:

1. project the currently rendered targets into screen space,
2. compute deterministic screen-space offsets for crowded groups,
3. unproject those displaced screen positions back into temporary display coordinates,
4. emit those display coordinates into the existing runtime GeoJSON features.

This fits the current XCPro stack better than layer-expression offsets because:

- the traffic overlays already own render-time feature generation,
- hit-testing already happens against those rendered traffic layers,
- labels and icons continue to move together because they share one feature geometry,
- the solution stays inside `feature:traffic` and does not leak screen-layout state into repositories, ViewModels, or Compose.

## Ownership

- Repositories and ViewModels remain authoritative for real aircraft state.
- `feature:traffic` overlay/runtime code owns declutter as a display-only concern.
- `feature:map` and `feature:map-runtime` only forward camera invalidation signals; they do not compute offsets or store display layout state.

## Algorithm

The shared declutter engine lives in:

- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficScreenDeclutterEngine.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficScreenDeclutterRuntimeSupport.kt`

Current behavior:

1. Visible traffic is converted into `TrafficProjectionSeed` values with:
   - stable key
   - projected collision size
   - priority rank
   - optional `pinAtOrigin`
2. The runtime projects those seeds with `MapLibreMap.projection.toScreenLocation(...)`.
3. The engine builds collision groups with a grid bucket + disjoint-set pass.
4. Each crowded group keeps one anchor at the original projected position:
   - selected OGN target wins if pinned,
   - otherwise the existing overlay ordering/priority rank wins.
5. Remaining targets get stable radial offsets using:
   - previous offset reuse when still valid,
   - deterministic angle preference,
   - bounded ring search with no randomness.
6. Offset strength fades with zoom using per-overlay zoom bands.
7. Displaced screen points are converted back to temporary display coordinates with `fromScreenLocation(...)`.
8. GeoJSON feature builders use those display coordinates instead of mutating repository lat/lon.

For OGN specifically, the selected target is pinned at origin so the existing selected ring/line/badge overlays remain aligned with the displayed aircraft icon.

## Changed Files

Shared declutter runtime:

- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficScreenDeclutterEngine.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficScreenDeclutterRuntimeSupport.kt`

Overlay integration and zoom-strength policy:

- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficViewportDeclutterPolicy.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficViewportDeclutterPolicy.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbGeoJsonMapper.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlayFeatureProjection.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/AdsbTrafficOverlaySupport.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/TrafficOverlayRuntimeState.kt`

Runtime delegate/camera invalidation wiring:

- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayInteractionCadencePolicy.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnHelpers.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficHelpers.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegate.kt`
- `feature/map-runtime/src/main/java/com/example/xcpro/map/MapOverlayManagerRuntime.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapInitializer.kt`

Tests:

- `feature/traffic/src/test/java/com/example/xcpro/map/TrafficScreenDeclutterEngineTest.kt`
- `feature/traffic/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeOgnDelegateViewportZoomTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerRuntimeTrafficDelegateTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapInitializerOgnViewportZoomTest.kt`
- `feature/map/src/test/java/com/example/xcpro/map/MapOverlayManagerOgnLifecycleTest.kt`

Docs:

- `docs/ARCHITECTURE/PIPELINE.md`
- `docs/aircraft-declutter-implementation.md`

## Camera Invalidation

Declutter depends on current screen projection, so target updates alone are not sufficient.

The runtime now invalidates traffic projection from the map shell at three points:

- `MapInitializer.setupInitialPosition(...)`
  - forces one immediate projection refresh after initial camera placement and zoom propagation.
- `MapInitializer.setupListeners(...)` camera move callback
  - calls `invalidateTrafficProjection(forceImmediate = false)`.
  - delegates reuse the interaction-aware cadence path and throttle projection-only rerenders to `TRAFFIC_PROJECTION_INVALIDATION_MIN_RENDER_INTERVAL_MS` (`120 ms`).
- `MapInitializer.setupListeners(...)` camera idle callback
  - updates viewport zoom first,
  - then forces an immediate projection refresh so the final settled layout matches the current camera exactly.

Delegate notes:

- OGN and ADS-B projection invalidation is fanned out by `MapOverlayManagerRuntime.invalidateTrafficProjection(...)`.
- Both delegates now track `pendingDueMonoMs` so an outstanding deferred rerender can be replaced by an earlier one when needed.
- OGN also invalidates projection immediately when selected-target pinning changes and there are live OGN targets on screen.

## Verification Run

Focused tests run first:

- `.\gradlew :feature:traffic:testDebugUnitTest :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.TrafficScreenDeclutterEngineTest" --tests "com.example.xcpro.map.MapOverlayManagerRuntimeOgnDelegateViewportZoomTest" --tests "com.example.xcpro.map.MapOverlayManagerRuntimeTrafficDelegateTest" --tests "com.example.xcpro.map.MapInitializerOgnViewportZoomTest"`
- `.\gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.MapOverlayManagerOgnLifecycleTest"`

Merge-ready repo gate:

- `scripts/qa/run_change_verification.bat -Profile pr-ready`

That profile completed successfully and covered:

- `enforceRules`
- reliable root `testDebugUnitTest`
- `assembleDebug`

Deferred:

- `connectedDebugAndroidTest`
- map SLO evidence capture under `scripts/qa/*`

Those were not run in this task.

## Manual QA Checklist

- Two nearby OGN aircraft at low zoom: confirm both remain tappable and visibly separated.
- Five or more crowded OGN aircraft: confirm stable spacing while panning and when zooming in/out repeatedly.
- Two nearby ADS-B aircraft with motion smoothing active: confirm icons separate without obvious wobble.
- Selected OGN target inside a crowded group: confirm the selected aircraft stays pinned at truth and ring/line/badge stay aligned.
- Mixed pan/rotate interaction: confirm layout refreshes during movement and snaps cleanly on camera idle.
- Zoom in past the strength fade band: confirm aircraft converge back toward their normal projected positions.

## Known Limitations

- Declutter is currently computed per overlay type. OGN and ADS-B do not resolve collisions against each other in one combined layout pass.
- There are no leader/tether lines back to true position for displaced aircraft.
- Labels move with the displaced feature geometry; this pass does not add an independent label-only declutter strategy.
- Projection rerenders during active gestures are intentionally throttled, so the display can lag camera movement by up to the configured cadence window.
- This pass does not add new map-performance evidence capture; SLO proof still needs a dedicated evidence run if this change is being prepared for PR/release sign-off.
