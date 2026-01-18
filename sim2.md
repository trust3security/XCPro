# SIM2 parity progress (XCPro vs XCSoar)

## Goal
Make SIM2 replay display behave like live/realtime rendering so the aircraft (blue arrow) and trail feel smooth, stable, and visually aligned in a thermal.

## What XCSoar does (key behaviors)
- Renders the trail every render pass using the current projection and current aircraft position.
- Draws the final segment from the last trail point to the current aircraft position each frame.
- Does not bail out of trail render if the current segment is too short; it just skips that segment.
- Camera + aircraft are rendered in the same render pass (projection updated, then aircraft/trail drawn).
- Aircraft position is computed in screen coordinates each frame and used for both the trail end and aircraft draw.
- Aircraft icon is drawn centered on its origin (no extra screen offset).

Relevant XCSoar files (local checkout):
- C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Renderer\TrailRenderer.cpp
  - `TrailRenderer::Draw()` draws the full trace and then `canvas.DrawLine(last_point, pos)`.
- C:\Users\Asus\AndroidStudioProjects\XCSoar\src\MapWindow\MapWindowRender.cpp
  - Computes `aircraft_pos = render_projection.GeoToScreen(basic.location)` and uses it for trail + aircraft.
- C:\Users\Asus\AndroidStudioProjects\XCSoar\src\MapWindow\MapWindowTrail.cpp
  - `RenderTrail()` passes `aircraft_pos` into `TrailRenderer::Draw(...)`.
- C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Renderer\AircraftRenderer.cpp
  - Aircraft polygons are centered around (0,0); no screen offset is applied.
- C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Renderer\TrackLineRenderer.cpp
  - Uses screen angle and current track for track line rendering.

## XCPro current SIM2 pipeline (after changes)
- SIM2 uses IGC replay with CATMULL_ROM_RUNTIME interpolation.
- Display pose is derived per-frame using `DisplayClock` + `DisplayPoseSmoother` for smooth motion.
- Replay runtime pose (position + bearing) can be queried each frame for UI alignment.
- Trail now updates every frame during replay using the *display pose* time/location.
- Map camera and aircraft overlay update on the render frame when `MapFeatureFlags.useRenderFrameSync` is enabled.

Key files:
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`
- `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailManager.kt`
- `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/IgcReplayController.kt`
- `feature/map/src/main/java/com/example/xcpro/replay/ReplayRuntimeInterpolator.kt`

## Changes applied in XCPro (progress)
1) **Render-frame sync (camera + aircraft + trail in same frame)**
   - `MapFeatureFlags.useRenderFrameSync = true` for SIM2.
   - `LocationManager` binds to MapLibre render frame and updates pose in-frame.
   - `MapLifecycleManager` binds/unbinds render frame listener.

2) **Runtime replay pose for SIM2**
   - New `ReplayDisplayPose` (lat/lon/time/bearing/speed) from `IgcReplayController`.
   - `LocationManager` uses runtime pose for position + bearing during SIM2.

3) **Trail aligned to display pose**
   - `SnailTrailManager.updateFromFlightData(...)` accepts display pose location/time.
   - Trail render uses display pose time so trail ends at the same timestamp as the arrow.
   - Per-frame trail updates for replay via `snailTrailManager.updateDisplayPose(...)`.

4) **Fix: trail render should not bail out on short line-to-current**
   - In `SnailTrailOverlay.render()` removed early return when the line-to-current is clipped.
   - This matches XCSoar: trail still renders even if the current segment is too short.

5) **SIM2 parity flags**
   - In `MapScreenReplayCoordinator.onVarioDemoReplaySimLive()`:
     - `MapFeatureFlags.forceReplayTrackHeading = true`
     - `MapFeatureFlags.maxTrackBearingStepDeg = 360.0` (no clamp)
     - `MapFeatureFlags.useIconHeadingSmoothing = false`
     - `MapFeatureFlags.useRuntimeReplayHeading = true`
     - `MapFeatureFlags.useRenderFrameSync = true`
    - Replay cadence 1000ms, GPS accuracy 1m, runtime Catmull-Rom interpolation.

6) **Arrow/trail alignment to match XCSoar**
   - `BlueLocationOverlay` icon offset set to `(0f, 0f)` so the arrow is drawn at the true geo position.
   - `ICON_CLEARANCE_FRACTION` set to `0f` so the trail reaches the arrow center.
   - Added a **tail-anchor** for the line-to-current segment using track direction + icon size so the trail appears to emerge from the back of the arrow.

7) **Projection-aware meters-per-pixel (SIM2)**
   - Trail spacing + tail offset now use MapLibre projection meters-per-pixel with `pixelRatio` correction.
   - Fallback keeps the old WebMercator formula if projection data is unavailable.

8) **Always include newest trail point**
   - Distance filtering no longer drops the most recent point; it is appended back if needed.
   - Prevents a long “tail‑lag” segment from the arrow to an older filtered point.

## Known data quirks
- The SIM2 IGC asset has a timestamp gap at the end:
  - `app/src/main/assets/replay/vario-demo-0-10-0-60s.igc` jumps from `B120059` to `B120100`.
  - This is a 1-second gap; interpolation should hide it, but it can still influence the last frame if the UI is using raw timestamps.

## Current hypothesis for the user-visible issues
- The ?pause then big jump? was caused by trail rendering aborting when the current segment was too short. This is now fixed.
- Remaining jerkiness may still be influenced by:
  - camera bearing update gating (`CAMERA_BEARING_EPS_DEG` in `LocationManager`),
  - smoothing profile tuning (DisplayPoseSmoother),
  - replay cadence (1 Hz) vs UI render cadence.
- Remaining trail/arrow misalignment is likely from **screen-space icon offset + clearance clip**:
  - `BlueLocationOverlay` uses `iconOffset(arrayOf(0f, -6f))`, which shifts the arrow in screen space.
  - `SnailTrailOverlay` clips the line-to-current by `ICON_CLEARANCE_FRACTION = 0.35`, which can leave a visible gap.
  - XCSoar does neither; it draws the trail directly to the aircraft position.

## What to verify on device
1) Run SIM2 and zoom in.
2) Confirm the trail keeps updating continuously (no 5s pause + jump).
3) Observe if the arrow + trail alignment stays stable in a thermal.

## Next adjustments (if needed)
- Reduce `CAMERA_BEARING_EPS_DEG` for SIM2 (e.g., 0.5?1.0 deg) to avoid bearing stutter.
- Re-enable heading smoothing for SIM2 only (if live uses it) to match perceived live behavior.
- Consider forcing `DisplaySmoothingProfile` to the live profile while SIM2 runs.
- Arrow/trail alignment options to match XCSoar:
  - Remove or zero `iconOffset` in `BlueLocationOverlay` so the arrow is drawn at the real geo position.
  - Reduce or disable `ICON_CLEARANCE_FRACTION` so the trail reaches the arrow center.
  - (Advanced) compute a tail-anchor point from track + zoom and use that for the line-to-current so it appears to come from the back of the arrow.

## Additional XCSoar parity ideas (1-4)
1) **Atomic update of aircraft + trail in one render pass**
   - XCSoar computes `aircraft_pos` once and uses it for both trail end + aircraft draw in the same render call.
   - XCPro currently updates the trail GeoJSON and the aircraft GeoJSON in separate sources/layers.
   - **Copy idea:** merge aircraft + trail updates so a single update call pushes both to MapLibre in the same frame.

2) **Tail-anchor line-to-current segment**
   - XCSoar uses a centered polygon; the line-to-current visually appears from the tail because of the icon shape.
   - Our arrow geometry can make the line look “in front” unless we anchor to the tail.
   - **Copy idea:** compute a tail anchor point from the current track + icon size and draw the line to that point instead of the center.

3) **Projection-aware trail thinning**
   - XCSoar thins trail points based on a pixel distance (`projection.DistancePixelsToMeters(3)`).
   - XCPro uses a meters-per-pixel heuristic; replay distance factor is 1.0.
   - **Copy idea:** use an explicit ~3px min distance for replay trail filtering for more stable density.

4) **Ensure SIM2 uses runtime-interpolated bearing everywhere**
   - XCSoar replay uses Catmull-Rom interpolation for both position and bearing (bearing from interpolated positions at t±0.05s).
   - XCPro already has runtime interpolation, but we should **enforce** it for: arrow rotation, tail anchor, and line-to-current.

## Implementation plan (detailed)
**Recommendation:** do **one change at a time** (SIM2‑only flags) and validate on device after each step.

### Phase 0 — Baseline + instrumentation (no behavior change)
- Add temporary debug logs to capture **screen‑space** deltas per frame:
  - Map camera bearing, icon rotation, trail tail anchor position (px + meters).
  - Use a single “frame id” from `LocationManager.onRenderFrame()` to correlate camera+overlay+trail updates.
- Confirm on S22 Ultra the effective `pixelRatio`, `projection.getMetersPerPixelAtLatitude()`, and current zoom.
- Validate that SIM2 is using runtime pose for **location + bearing + trail anchor**.

### Phase 1 — Fix pixel‑to‑meter conversion parity (high‑impact on S22 Ultra)
**Goal:** make trail spacing + tail offset consistent with XCSoar’s projection‑based distance.
1) Replace `SnailTrailMath.metersPerPixelAtLatitude(...)` with MapLibre projection:
   - Use `map.projection.getMetersPerPixelAtLatitude(lat)` and divide by `mapView.pixelRatio` (if needed).
   - Apply this for:
     - replay min distance filter,
     - tail‑anchor meters,
     - scaled line widths.
2) Update any callers that currently depend on manual WebMercator math.
3) Validate zoom levels in SIM2: trail spacing should remain stable across zoom.

**Files:**  
- `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailMath.kt`  
- `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailOverlay.kt`

### Phase 2 — Final segment + bounds parity
**Goal:** eliminate last‑segment dropouts and edge culling discrepancies.
1) Remove or SIM2‑gate `MIN_CURRENT_SEGMENT_METERS` so the last segment always renders.
2) Align bounds behavior with XCSoar:
   - Replace fixed `ScreenBounds` window with a symmetric **4x screen** scale (XCSoar `Scale(4)`).
   - Keep this SIM2‑only to avoid surprising live users.

**Files:**  
- `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailOverlay.kt`  
- `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailModels.kt`

### Phase 3 — Replay heading parity
**Goal:** match XCSoar’s heading derivation from track + wind when compass isn’t present.
1) If SIM2 has wind available, run `HeadingResolver` with:
   - `track`, `groundSpeed`, `windFrom`, `windSpeed`, and `isFlying`.
2) Use that heading for icon rotation (and for camera when `HEADING_UP`).
3) Fallback to track only when wind isn’t available.

**Files:**  
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`  
- `feature/map/src/main/java/com/example/xcpro/orientation/HeadingResolver.kt`  
- (data source for wind if needed)

### Phase 4 — Circling camera bias (thermal center parity)
**Goal:** match XCSoar’s “center between aircraft + thermal estimate” when circling.
1) When circling + thermal estimate valid:
   - Offset camera target toward thermal estimate, limited by map scale (XCSoar behavior).
2) Keep aircraft overlay position unchanged.
3) Gate by SIM2 flag to avoid changing live behavior.

**Files:**  
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`  
- (thermal estimate source in XCPro if available)

### Phase 5 — Atomic update of aircraft + trail (render‑frame parity)
**Goal:** ensure both update inside the same render frame for zero “camera then overlay” lag.
1) Build a “render frame state” object (pose + heading + map bearing + trail tail anchor).
2) Push aircraft + trail GeoJSON in a single call (same frame id).
3) Ensure MapLibre layer order is stable (trail below aircraft).

**Files:**  
- `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`  
- `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailManager.kt`  
- `feature/map/src/main/java/com/example/xcpro/map/BlueLocationOverlay.kt`

### Phase 6 — Optional: map‑shift bias parity (cruise mode)
**Goal:** match XCSoar’s track/target bias in `UpdateProjection()`.
1) Add a track/target‑biased offset (when not circling) to padding logic.
2) Smooth with existing offset history.
3) Gate under SIM2 flag first.

**Files:**  
- `feature/map/src/main/java/com/example/xcpro/map/LocationManager.kt`  
- `feature/map/src/main/java/com/example/xcpro/map/helpers/GliderPaddingHelper.kt`

### Validation after each phase
- Arrow + trail alignment: trail should “come from the tail,” no visible gap.
- Rotation smoothness in thermals (no 5° stepping, no delayed camera rotation).
- Consistent trail density at multiple zoom levels.
- No regressions in **live** mode.

## Additional differences found (XCSoar vs XCPro)
1) **Trace data thinning algorithm**
   - XCSoar: `Trace` uses an online Douglas–Peuker style thinning with distance+time ranking, plus a “no-thin” window.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Engine\Trace\Trace.hpp`
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Computer\TraceComputer.cpp`
   - XCPro: `TrailStore` uses simple time gating + thinning (minDeltaMillis, maxSize).
     - `feature/map/src/main/java/com/example/xcpro/map/trail/TrailStore.kt`

2) **Trail points only recorded when flying**
   - XCSoar: `TraceComputer::Update()` only records points when `calculated.flight.flying` and nav altitude available.
   - XCPro: Replay always records; live uses `isFlying` gate in `SnailTrailManager`.

3) **Screen-angle orientation updated every frame**
   - XCSoar: `GlueMapWindow::UpdateScreenAngle()` sets screen angle each frame from track/heading/wind/target.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\MapWindow\GlueMapWindowDisplayMode.cpp`
   - XCPro: camera bearing update is gated by `CAMERA_BEARING_EPS_DEG` + time threshold.

4) **Map follow / screen-origin smoothing**
   - XCSoar: `UpdateProjection()` uses `glider_screen_position`, map shift bias (track/target), and offset history smoothing.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\MapWindow\GlueMapWindowDisplayMode.cpp`
   - XCPro: padding smoothing uses `MapPositionController` but no bias vector (track/target) for cruise mode.

5) **Replay bearing derived from interpolated positions**
   - XCSoar: `CatmullRomInterpolator::GetVector()` uses interpolated positions at t±0.05s.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Replay\CatmullRomInterpolator.hpp`
   - XCPro: runtime interpolator uses 50ms window (similar), but SIM2 must enforce it for all map pose usage.

6) **Render ordering (trail before aircraft)**
   - XCSoar: Render trail, then draw aircraft last to guarantee visual precedence.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\MapWindow\MapWindowRender.cpp`
   - XCPro: uses MapLibre layers; overlay ordering must be enforced via layer order (done) but source update timing can still differ.

7) **Pixel-to-meter conversion for trail spacing / tail offset**
   - XCSoar: uses `projection.DistancePixelsToMeters(3)` tied to its active projection.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Renderer\TrailRenderer.cpp`
   - XCPro: uses a hardcoded WebMercator formula (`METERS_PER_PIXEL_EQUATOR`) and does **not** account for MapLibre `pixelRatio`.
     - `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailMath.kt`
   - On high-DPI devices (e.g., S22 Ultra), this can over/under-thin and distort tail offset in meters.

8) **Thermal-center bias when circling**
   - XCSoar: if circling and thermal estimate is valid, centers the map between aircraft and thermal estimate.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\MapWindow\GlueMapWindowDisplayMode.cpp`
   - XCPro: no thermal-center bias; camera simply tracks aircraft with padding.

9) **Replay update cadence**
   - XCSoar: IGC replay with Catmull-Rom schedules timer at 1 Hz (`Replay::OnTimer()`).
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Replay\Replay.cpp`
   - XCPro SIM2: uses per-render-frame interpolation; higher update rate than XCSoar (not parity, but smoother).

10) **Replay heading source**
   - XCSoar: `BasicComputer::ComputeHeading()` derives heading from track + wind when compass is missing.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Computer\BasicComputer.cpp`
   - XCPro SIM2: `forceReplayTrackHeading` forces track for icon rotation; wind-corrected heading is not used.

11) **Orientation modes available**
   - XCSoar supports `TARGET_UP` and `WIND_UP` in addition to `NORTH_UP`/`TRACK_UP`/`HEADING_UP`.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\MapWindow\GlueMapWindowDisplayMode.cpp`
   - XCPro only exposes `NORTH_UP`, `TRACK_UP`, `HEADING_UP`.
     - `core/common/src/main/java/com/example/xcpro/common/orientation/OrientationContracts.kt`

12) **Minimum segment guard to “current position”**
   - XCSoar always draws `canvas.DrawLine(last_point, pos)` with no explicit distance guard.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Renderer\TrailRenderer.cpp`
   - XCPro skips drawing the final segment when it’s shorter than `MIN_CURRENT_SEGMENT_METERS` (0.5m).
     - `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailOverlay.kt`
   - This can hide the last segment when zoomed in or during very low movement.

13) **Trail bounds culling window**
   - XCSoar: uses `projection.GetScreenBounds().Scale(4)` (4x screen) as the visibility window.
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\Renderer\TrailRenderer.cpp`
   - XCPro: uses a fixed `-1.5x..2.5x` screen box in `ScreenBounds`.
     - `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailModels.kt`
   - Slightly different bounds can affect which segments are drawn near edges.

14) **Trail length presets**
   - XCSoar: `SHORT=10m`, `LONG=60m`, `FULL=all` (no “medium” length).
     - `C:\Users\Asus\AndroidStudioProjects\XCSoar\src\MapWindow\GlueMapWindowOverlays.cpp`
   - XCPro: `SHORT=10m`, `MEDIUM=30m`, `LONG=60m`, `FULL=all`.
     - `feature/map/src/main/java/com/example/xcpro/map/trail/SnailTrailMath.kt`

## Parity checklist vs XCSoar
- [x] Trail rendered every frame
- [x] Final segment drawn to current aircraft position
- [x] No render abort on short segment
- [x] Camera + aircraft + trail update in same render frame
- [x] Runtime interpolation for replay pose
- [ ] Bearing update gating tuned to match live

## Notes
- This file is a living status summary; update as we validate on device.

## Log capture + analysis tooling
Use the helper script to capture SIM2 logcat data and quantify trail/arrow lag.

Capture (60s):
```
python tools/sim2_log_tool.py capture --seconds 60 --out logs/sim2_logcat.txt --launch
```

Analyze:
```
python tools/sim2_log_tool.py analyze --log logs/sim2_logcat.txt --out logs/sim2_analysis.json --csv logs/sim2_analysis.csv
```

The analysis reports:
- gap between trail updates,
- segment length distribution,
- lag (time + meters) between overlay location and trail tail anchor.
