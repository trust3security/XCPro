
# Racing Replay Live-Cadence Plan (100ms)  

Goal: make racing replay behave like live GPS cadence (per-mille^100ms) **without** violating IGC spec,
and ensure start/finish events align with the displayed glider position.

This is a **design + implementation plan** only. No behavior changes should be made outside
the steps below.

---

## 0) Constraints (non'negotiable)

- **SSOT stays in repositories**. UI/VM must not own navigation truth.
- **Time base:** replay uses IGC timestamps only.
- **No UI logic in domain**. Crossing math belongs in navigation engine.
- **No extra state owners**. ViewModel keeps UI state; helpers only coordinate.
- **IGC spec not modified** for assets unless explicitly agreed.

---

## 1) Current Reality (facts)

- Built'in replay assets (`app/src/main/assets/replay/*.igc`) use **1s B'record cadence**.
- `IgcParser` reads `HHMMSS` only *-> **1s timestamp resolution** in file.
- `IgcReplayMath.densifyPoints(log.points)` inserts **1s** intermediate points (REFERENCE mode).
- `ReplaySampleEmitter.shouldEmitGps()` emits **every point** in REFERENCE, but GPS cadence is still
  limited by the point cadence (1s).

Therefore: **editing the file alone cannot produce 100ms behavior** without parser + math changes.

---

## 2) Solution Overview (genius'grade, minimal risk)

### A) Live'cadence without changing file format
- Keep assets intact (IGC compliant).
- **Densify in replay** to 100ms using replay config.
- **Emit GPS at 100ms** during replay.
- Keep baro cadence high (20ms) for smooth vario.

### B) Visual alignment with navigation events
- **Compute exact crossing time** between previous & current fix for start/finish.
- **Use that crossing timestamp** for event emission and state transitions.
- **Force RAW replay pose** during replay to keep UI aligned with fix timestamps.

This gives you **live'like cadence + exact event timing** without touching IGC spec.

---

## 3) Implementation Plan (phased)

### Phase 1 -- Replay cadence profile (core)

**Add a replay cadence profile**
1) Extend `ReplaySimConfig`:
   - `referenceStepMs` (default **1000ms**)
   - keep `gpsStepMs` (default **1000ms**)
2) Add `IgcReplayController.setReplayCadence(profile)`:
   - Only allowed when status != PLAYING.
   - Updates `simConfig` and resets `ReplaySampleEmitter`.
3) Update session prep:
   - In `prepareReplaySession`, if `mode == REFERENCE`, call
     `IgcReplayMath.densifyPoints(original, stepMs = referenceStepMs, jitterMs = 0, random = sampleEmitter.random)`.
   - Default keeps current behavior (1s).
4) Update `ReplaySampleEmitter.shouldEmitGps`:
   - Use `gpsStepMs` **in both modes** (REFERENCE + REALTIME_SIM).
   - Default `gpsStepMs = 1000ms` preserves existing behavior.
5) Define `ReplayCadenceProfile.LIVE_100MS`:
   - `referenceStepMs = 100`
   - `gpsStepMs = 100`
   - `baroStepMs = 20` (unchanged)

**Apply in racing replay**
6) In `MapScreenReplayCoordinator.onRacingTaskReplay()`:
   - snapshot current replay cadence.
   - apply `LIVE_100MS`.
   - restore after replay end.

**Acceptance**
- No change to IGC assets.
- Replay produces ~10x GPS updates vs current.
- Unit tests pass.

---

### Phase 2 -- Exact crossing timestamp (navigation correctness)

**Add crossing resolver**
1) Create `RacingCrossingResolver` (domain, no UI types).
   - Inputs: previous fix, current fix, active waypoint geometry.
   - Outputs: `Crossing(timestampMillis, lat, lon)` or null.
2) Support:
   - **Start/Finish line**: compute line segment intersection in local tangent plane (meters).
   - **Cylinder**: binary search for radius crossing along fix segment.

**Integrate in RacingNavigationEngine**
3) In `handleStartTransition` and `handleProgressTransition`:
   - When transition is detected, compute crossing time.
   - Use crossing timestamp (fallback to `fix.timestampMillis` if no crossing).
   - Set `lastTransitionTimeMillis` and event timestamp to the crossing time.

**Acceptance**
- START/FINISH events align with geometry crossing, not previous fix.
- Deterministic across replay/live.

---

### Phase 3 -- UI alignment (replay only)

**RAW pose during replay**
1) Ensure `DisplayPoseMode.RAW_REPLAY` is forced while replay active.
   - Use existing `MapFeatureFlags.useRawReplayPose` or a replay'only override.
2) Restore original mode after replay ends.

**Acceptance**
- Glider icon position = same fix timestamps used for event detection.
- No smoothing lag during replay validation.

---

## 4) Tests (must add)

- `ReplayCadenceTest`
  - REFERENCE mode densify at 100ms produces expected timestamps.
  - GPS emits at 100ms when `gpsStepMs = 100`.
- `RacingCrossingResolverTest`
  - Line intersection timestamp between two fixes is correct.
  - Cylinder crossing timestamp is correct within tolerance.
- `RacingReplayValidationTest`
  - START/TURN/FINISH times align with expected crossings at 100ms cadence.

---

## 5) Red Flags (code review blockers)

- Any UI code calculating crossing points.
- Replay using wall time for navigation timestamps.
- New SSOT owners or duplicate state.
- Hardcoded cadence changes without snapshot/restore.
- Changing IGC assets to include milliseconds without parser support.

---

## 6) Definition of Done

- Racing replay emits GPS at 100ms cadence without IGC changes.
- Start/finish events timestamped at exact crossing.
- UI arrow aligns with event timing during replay.
- Tests added and green.



