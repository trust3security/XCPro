# Ownship Snail Trail: System Map And Current Behavior

Date
- 2026-02-27

Purpose
- Document the current end-to-end ownship snail-trail pipeline.
- Provide exact behavior in live and replay modes.
- Provide a stable reference before implementing further smoothing changes.

## 1. End-to-End Data Flow

Primary flow:
- `CompleteFlightData + WindState + FlyingState + ReplayMode`
- `MapScreenObservers` builds trail update input.
- `TrailProcessor` owns trail domain state and produces `TrailUpdateResult`.
- `MapScreenRuntimeEffects` forwards updates to `SnailTrailManager`.
- `SnailTrailManager` triggers `SnailTrailOverlay` rendering.
- `SnailTrailOverlay` writes MapLibre sources/layers.

Key files:
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenObservers.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/domain/TrailProcessor.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRuntimeEffects.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailOverlay.kt`

## 2. Ownship Icon And Layering

The pilot blue triangle is MapLibre symbol-layer based:
- `BlueLocationOverlay` with source `aircraft-location-source`
- layer id `aircraft-location-layer`

Layer ordering:
- Snail trail line/tail/dot are placed below the blue triangle anchor layer.
- `SnailTrailManager.initialize()` re-brings blue overlay to front.

Key files:
- `feature/map/src/main/java/com/trust3/xcpro/map/BlueLocationOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/SailplaneIconBitmapFactory.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailManager.kt`

## 3. Thermal And Circling Semantics

Circling source:
- Live: `ResolveCirclingUseCase` returns `data.isCircling`.
- Replay: returns true when any of:
  - `data.isCircling`
  - `data.currentThermalValid`
  - `data.thermalAverageValid`
  - fallback `CirclingDetector` from track over time.

Thermal-specific trail behavior:
- Wind drift compensation is applied only when:
  - `settings.windDriftEnabled == true`
  - `isCircling == true`
- No separate "thermal style" override exists for ownship trail.

Key files:
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/domain/ResolveCirclingUseCase.kt`
- `feature/map/src/main/java/com/trust3/xcpro/sensors/CirclingDetector.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailMath.kt`

## 4. Sampling, Timing, And Storage

Live:
- Store throttle: `minDeltaMillis = 2_000` ms.
- Time base: monotonic GPS time if available, otherwise wall time.

Replay:
- Store throttle: `minDeltaMillis = 0` ms.
- Time base: IGC timestamp.
- Interpolation: inserts points at `250` ms cadence when needed.
- Replay wind is smoothed (`TrailWindSmoother`).

Store behavior:
- Capacity 1024 points, with thinning when full.
- New point `driftFactor` is altitude-derived sigmoid.

Visible display-pose trail note (2026-04-22):
- The visible ownship trail is display-pose geometry when
  `MapFeatureFlags.useDisplayPoseSnailTrail` is enabled.
- Raw `TrailStore` remains source/raw state, but raw line/dot/tail drawing is
  hidden by `SnailTrailManager`.
- A transient display connector may render from the latest accepted display
  point to the current aircraft pose; that connector point is not stored.

Key files:
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/domain/TrailProcessor.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/TrailStore.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/domain/ReplayTrailInterpolator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/domain/TrailWindSmoother.kt`

## 5. Rendering Plan Rules

Planner behavior:
- Filters by trail-length time window.
- Applies drift transform in `SnailTrailMath.filterPoints`.
- Applies zoom-based distance filter.
- Live uses `LIVE_DISTANCE_FACTOR = 3.0`.
- Replay uses `REPLAY_DISTANCE_FACTOR = 1.0` and caps min distance to 30 m.

Segment generation:
- Vario/altitude color index from palette.
- Dots/lines selected by `TrailType` and vario sign.

Key files:
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailRenderPlanner.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailMath.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailSegmentBuilder.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailPalette.kt`

## 6. UI Settings Path

User path:
- `Nav Drawer -> Look & Feel -> Snail Trail -> Behavior -> Wind drift`

Persistence path:
- `LookAndFeelScreen -> SnailTrailSettingsViewModel -> MapTrailSettingsUseCase -> MapTrailPreferences`

Key files:
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/LookAndFeelSheets.kt`
- `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/SnailTrailSettingsViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/MapTrailPreferences.kt`

## 7. Known Current Constraint

Live trail rendering currently updates primarily on new stored samples.
- With 2-second live sampling, circles in thermals can appear polygonal.
- Drift compensation then moves historical points in discrete time steps.

This is the baseline condition addressed by the implementation plan in:
- `03_IMPLEMENTATION_PLAN_THERMAL_SMOOTHING_2026-02-27.md`
