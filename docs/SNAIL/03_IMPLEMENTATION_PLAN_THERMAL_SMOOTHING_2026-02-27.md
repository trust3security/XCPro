# Implementation Plan: Thermal Smoothing For Ownship Snail Trail

Date
- 2026-02-27

Status
- Updated 2026-04-22: live/Condor display-pose trail-body phase selected
  before any further raw sampling or provider changes.
- Updated 2026-04-22, superseded 2026-04-23: replay display-pose trail
  and transient display connector slice added.
- Updated 2026-04-23: display-pose trail-body ownership was rejected after
  XCSoar comparison. Raw `TrailStore` drawing is restored as the visible trail
  body; display pose only refreshes the transient tail connector.

Owner
- XCPro Team

Goal
- Remove angular/jerky trail behavior in live thermalling while preserving architecture boundaries and replay determinism.

Non-Goals
- No redesign of OGN glider-trail pipeline.
- No breaking changes to trail settings UI model.
- No changes to task or flight-card business logic.

Architecture Guardrails
- Keep domain decisions in trail domain/use-case classes.
- Keep map rendering in `SnailTrailManager` / `SnailTrailOverlay`.
- Keep explicit time-base behavior for live/replay.
- Do not introduce hidden global mutable state.

## Phase 1: Adaptive Live Sampling In Circling

Change
- Replace fixed live `2_000 ms` minimum with adaptive thresholds:
  - cruise: `2_000 ms` (existing behavior)
  - circling: target `400-500 ms` minimum

Implementation sketch
- Add adaptive sample policy in trail domain layer.
- Use `isCircling` in update path to choose live sample interval.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/domain/TrailProcessor.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/TrailStore.kt` (or wrapper policy)

Acceptance criteria
- In circling mode, stored live points are >= 2x denser than current.
- Cruise mode storage remains unchanged.
- No replay behavior change.

Rollback safety
- Keep old fixed threshold behind a local constant toggle during rollout.

## Phase 2: Live Display-Pose Tail Refresh Path

Change
- Allow live mode to refresh tail segment per display frame (or at small bounded cadence), similar to replay tail updates.
- 2026-04-23 correction: keep this phase tail-only. Do not add a recent
  display-only trail body because that creates a second visual trail-history
  owner.

Implementation sketch
- Extend `SnailTrailManager.updateDisplayPose()` to optionally support live display pose.
- Keep raw `TrailStore` and full historical trail rendering authoritative.
- Refresh only the existing raw tail connector from the latest raw trail point
  to the display pose.
- Do not append display-pose frames into trail history.

Likely files
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/trail/SnailTrailManager.kt`
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/trail/SnailTrailOverlay.kt`

Acceptance criteria
- Tail endpoint tracks icon smoothly between stored samples.
- Raw trail history, replay, IGC, and navigation data remain unchanged.
- CPU/memory overhead remains within acceptable map budget.

Rollback safety
- Revert `SnailTrailManager.updateDisplayPose()` tail refresh behavior.

## Phase 2A: Replay Display-Pose Trail And Connector

Status
- Superseded 2026-04-23. Do not implement as written.

Change
- Superseded. Do not hide raw `TrailStore` drawing.
- Superseded. Do not store display-pose trail history.
- Current replacement: render the raw trail body and refresh only the raw tail
  connector from the latest raw trail point to display pose.

Implementation sketch
- `SnailTrailDisplayStore` ownership was removed because display-pose history is
  a duplicate trail body owner.
- `SnailTrailManager` owns display-pose gating and orchestration only.
- `SnailTrailOverlay` writes only raw line/dot/tail MapLibre sources for ownship
  trail rendering.

Acceptance criteria
- Raw trail body paints for live, replay, and `FULL` length.
- Display pose does not create stored trail points.
- Connector updates remain transient through the raw tail source.

## Phase 3: Live Wind Smoothing For Drift

Change
- Apply a lightweight smoothing step to live drift wind vector.

Implementation sketch
- Reuse `TrailWindSmoother` strategy with live-appropriate tau.
- Keep replay smoothing unchanged.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/domain/TrailProcessor.kt`
- optionally new config constants in same file.

Acceptance criteria
- Reduced visible drift jitter with noisy wind estimates.
- No drift when wind invalid/stale.

Rollback safety
- Keep smoothing tau configurable via constant.

## Phase 4: Circling-Aware Distance Filter Relaxation

Change
- Reduce or bypass additional distance thinning while circling in live mode.

Implementation sketch
- In planner, when `isCircling == true`, lower live distance factor.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailRenderPlanner.kt`

Acceptance criteria
- Circle arcs retain shape at medium/high zoom.
- Non-circling behavior remains similar to current baseline.

Rollback safety
- Bound change to circling branch only.

## Phase 5: Optional Geometry Smoothing (Only If Needed)

Change
- If phases 1-4 are insufficient, add bounded visual-only geometry smoothing.

Implementation sketch
- Post-process render points with simple polyline smoothing (windowed, deterministic).
- Keep raw store unchanged.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailMath.kt`
- or new helper in trail package.

Acceptance criteria
- Visible corner reduction without lagging tail anchor.
- No self-intersection artifacts in tight circles.

## Delivery Sequence

Recommended PR sequence:
1. Phase 1 + tests
2. Phase 2 + tests
3. Phase 3 + tests
4. Phase 4 + tests
5. Phase 5 only if still needed

Each phase:
- update this plan status
- run verification gates
- document measured impact

## Risk Register

Risk: Too many points increase render cost.
- Mitigation: bound point count, maintain thinning, measure frame time.

Risk: Tail and body desync artifacts.
- Mitigation: keep single source of display pose timestamp.

Risk: Drift change alters pilot interpretation.
- Mitigation: keep UI toggle semantics unchanged; verify against known flights.

Risk: Replay determinism regression.
- Mitigation: no replay path modification unless explicit and tested.
