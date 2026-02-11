
# Refactor Plan: SnailTrailManager

Owner: agent (Codex)
Date: 2026-01-27

Goal
- Move trail business logic out of UI (SnailTrailManager).
- Keep trail rendering behavior unchanged.
- Make time base explicit and testable.

Scope
- Files under `feature/map/src/main/java/com/example/xcpro/map/trail/`
- MapScreen observers/ViewModel wiring for trail updates.
- MapScreenRuntimeEffects/manager rendering wiring.

Non-goals
- Reworking flight card UI or RealTimeFlightData usage outside trail.
- Changing trail styling, palette, or map overlay visuals.

SSOT Flow (Trail)
CompleteFlightData + WindState + FlyingState + ReplaySession
  -> TrailProcessor (SSOT for trail points)
    -> StateFlow<TrailUpdateResult>
      -> SnailTrailManager (renderer only)
        -> MapLibre overlay layers

Time Base Rules (explicit)
- Replay: use IGC timestamps as simulation clock (TrailTimeBase.REPLAY_IGC).
- Live: use monotonic GPS time when available; otherwise use wall time
  (TrailTimeBase.LIVE_MONOTONIC or LIVE_WALL).
- No mixing time bases within a single decision path.

Phases
1) Baseline
   - Document current behavior and reset conditions.
   - Create tests to lock vario/circling/wind/replay interpolation logic.
2) Core logic extraction
   - Add usecases: ResolveTrailVarioUseCase, ResolveCirclingUseCase.
   - Add TrailWindSmoother and ReplayTrailInterpolator.
3) Integration
   - Add TrailProcessor (owns TrailStore and replay state).
   - Expose TrailUpdateResult from MapScreenViewModel via StateFlow.
   - Update SnailTrailManager to render only.
4) Hardening
   - Verify reset behavior and time-base handling.
   - Run required checks and adjust tests.

Behavior parity checklist
- Same trail point thinning and store limits.
- Same replay interpolation cadence and backstep reset thresholds.
- Same wind smoothing behavior for replay.
- Same rendering gating based on zoom/settings/sample changes.

Tests to add
- ResolveTrailVarioUseCase selection order (replay and live).
- ResolveCirclingUseCase replay fallback (track-based circling).
- ReplayTrailInterpolator: timestamp adjustment, interpolation count, reset triggers.
- TrailWindSmoother: smoothing response over time.

Assumptions
- Trail time base for live uses GPS monotonic time when available.
- Trail time base for replay uses IGC timestamps from CompleteFlightData.timestamp.
- RealTimeFlightData remains for card/UI use outside trail.


