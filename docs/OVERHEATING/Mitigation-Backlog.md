# Mitigation Backlog

Purpose:
- Track prioritized heat/battery mitigation work.
- Keep changes measurable and tied to acceptance criteria.

Status legend:
- `proposed`
- `in_progress`
- `validated`
- `deferred`

## P0 (High Impact, Low/Medium Risk)

1) Introduce user-selectable thermal profile for live flight
- Status: `proposed`
- Hypothesis: Reducing update cadence under "battery" profile lowers sustained heat.
- Scope ideas:
  - Raise camera min update interval in battery profile.
  - Lower GPS fast cadence from 200 ms when safe.
  - Cap overlay update cadence aggressively.
- Acceptance:
  - A/B run shows clear battery drain improvement without unsafe UX regressions.

2) Make OGN display mode default non-real-time
- Status: `proposed`
- Hypothesis: Avoiding unthrottled overlay refresh reduces GPU/CPU spikes.
- Current risk evidence:
  - `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnDisplayUpdateMode.kt:13`
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt:379`
- Acceptance:
  - No functional regression in traffic awareness.
  - Lower average frame workload in profiling.

3) Tighten map update work when movement is minimal
- Status: `proposed`
- Hypothesis: Skip unnecessary source/camera updates when deltas are tiny.
- Candidate areas:
  - `BlueLocationOverlay.updateLocation(...)`
  - `MapTrackingCameraController.updateCamera(...)`
- Acceptance:
  - Reduced update count while stationary/steady flight.
  - No visible lag in normal maneuvers.

4) Remove or gate debug logging from hot runtime paths in release
- Status: `proposed`
- Hypothesis: Log pressure can raise CPU and I/O overhead in long sessions.
- Candidate paths:
  - `MapPositionController`
  - variometer widget gesture logs
- Acceptance:
  - Release path shows reduced logging overhead and no lost required diagnostics.

## P1 (Medium Impact, Medium Risk)

1) Cap ADS-B interpolation frame loop rate
- Status: `proposed`
- Hypothesis: 60 fps interpolation is often unnecessary for traffic markers.
- Candidate:
  - Add max frame rate gate in `AdsbTrafficOverlay`.
- Acceptance:
  - Similar visual smoothness with lower CPU/GPU cost.

2) Optimize variometer draw allocations
- Status: `proposed`
- Hypothesis: Reusing `Paint` and text layout objects reduces UI thread churn.
- Candidate:
  - `feature/variometer/src/main/java/com/example/ui1/UIVariometer.kt`
- Acceptance:
  - Lower allocation rate in profiler while rendering widget.

3) Add power-aware policy for weather animations
- Status: `proposed`
- Hypothesis: Slower animation and shorter frame windows reduce overlay cost.
- Candidate:
  - Prefer slower weather animation defaults and shorter history in-flight.
- Acceptance:
  - Reduced frame-time variance when weather overlay enabled.

4) Review card update tiers for thermal mode
- Status: `proposed`
- Hypothesis: Relaxing fast tier cadence reduces steady CPU load.
- Candidate:
  - `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepository.kt`
- Acceptance:
  - Measurable savings with acceptable responsiveness.

## P2 (Strategic, Higher Effort)

1) Build always-on internal telemetry counters
- Status: `proposed`
- Goal:
  - Count per-second updates for camera, marker source writes, overlay renders, and audio updates.
- Benefit:
  - Faster diagnosis without full profiler sessions.

2) Add thermal regression scenario in QA checklist
- Status: `proposed`
- Goal:
  - Standardize overheating validation before release.

3) Adaptive runtime load-shed policy
- Status: `proposed`
- Goal:
  - React to thermal severity by reducing non-critical update rates.
- Constraints:
  - Preserve core flight safety and vario integrity.

## Validation Criteria (For Any Mitigation)

- Must preserve architecture constraints (MVVM/UDF/SSOT and deterministic replay behavior).
- Must include before/after profiling evidence.
- Must document expected and observed user-facing behavior changes.
- Must avoid hidden regressions in map tracking and vario/audio correctness.
