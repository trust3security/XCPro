# Test And Validation Plan: SCIA

Date
- 2026-02-27

Purpose
- Define repeatable verification for SCIA stability and responsiveness improvements.
- Prevent regressions in architecture boundaries, runtime safety, and map UX.

## 1) Unit Tests To Add Or Extend

Filtering pipeline tests:
- Add tests proving large trail filtering is not executed in Compose binding path.
- Validate filtered output parity after moving filtering to non-UI stage.
- Validate single-collection trail-selection wiring (avoid duplicate UI collectors for same flow).

Toggle behavior tests:
- Extend `MapScreenViewModelTest` to verify pending toggle UX behavior (if introduced).
- Verify failure path still emits user-visible effect and does not crash scope.
- Add parity tests for consolidated single-write SCIA+OGN mutation path.
- Add rapid-tap tests to verify mutation coalescing/serialization (no redundant queued toggles).
- Preserve existing expectations:
  - `showOgnScia_defaultsToDisabled`
  - `onToggleOgnScia_enablingForcesOgnTrafficOn`
  - `onToggleOgnTraffic_ignoredWhileSciaIsEnabled`

Overlay render policy tests:
- Keep and extend `OgnGliderTrailOverlayRenderPolicyTest` for:
  - cap behavior
  - user-triggered immediate render bypass for throttled modes
  - warm-start behavior (if added)
  - deterministic ordering (newest subset)

Overlay manager behavior tests:
- Add focused tests for render scheduling/cancellation on dense updates.
- Verify stale jobs do not apply after map style/state change.
- Add tests for duplicate map-ready/effect push de-dup behavior.
- Add tests for reduced O(n) compare pressure strategy (signature/version path) if implemented.

## 2) Integration/Runtime Validation

SCIA toggle responsiveness:
- Toggle SCIA on/off with high target count and dense segment history.
- Confirm immediate UI acknowledgement (pending state or equivalent).
- Confirm no visible freeze longer than short transient update.

Render correctness:
- Confirm trails appear only when:
  - OGN overlay is enabled
  - SCIA is enabled
  - selected aircraft keys are non-empty
- Confirm no trails render for empty selected-aircraft set.
- Confirm first SCIA enable from OFF state repaints immediately even under `BALANCED`/`BATTERY` mode (if immediate bypass is implemented).

Failure resilience:
- Confirm render exceptions are non-fatal and overlay attempts recovery.
- Confirm preference mutation failures surface user feedback (toast).

## 3) Manual Scenarios

Scenario A: SCIA ON from fully OFF state
- Initial state: OGN OFF, SCIA OFF.
- Action: enable SCIA.
- Expected:
  - OGN auto-enables.
  - SCIA enables.
  - UI remains responsive.

Scenario B: SCIA ON with no selected aircraft
- Initial state: SCIA ON, selection empty.
- Expected:
  - no trail lines rendered.
  - clear UI cue explaining why.

Scenario C: High-density trail history
- Initial state: many segments retained.
- Action: enable SCIA.
- Expected:
  - no crash.
  - acceptable toggle latency.
  - map remains interactive.

Scenario D: Display mode effects
- Test `REAL_TIME`, `BALANCED`, `BATTERY`.
- Expected:
  - render cadence follows mode by design.
  - no incorrect "loop" behavior.
  - explicit user SCIA-enable path still provides immediate visual response.

Scenario E: Rapid repeated taps on SCIA switch
- Action: tap SCIA toggle repeatedly before prior write settles.
- Expected:
  - no crash.
  - final state is deterministic.
  - no prolonged oscillation/pause from queued duplicate writes.

Scenario F: App restart resets SCIA state
- Initial state before restart: SCIA ON and non-empty selected keys.
- Action: restart app process.
- Expected:
  - SCIA toggle is OFF after restart.
  - selected trail-aircraft set is empty after restart.
  - no trails render until user explicitly re-enables SCIA and selects aircraft.

## 4) Performance Checks

Collect at minimum:
- approximate SCIA render time on first enable (dense case)
- frame stability during toggle sequence
- number of rendered segments (respecting cap policy)
- CPU cost trend for render scheduling path under dense-list updates (before vs after optimization)

Target direction (guidance, not hard fail):
- materially lower worst-case first-enable jank versus baseline.

## 5) Required Commands

Core gates:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Focused map loop:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.*Scia*"`
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.OgnGliderTrailOverlayRenderPolicyTest"`
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.MapScreenViewModelTest.onToggleOgnScia_enablingForcesOgnTrafficOn"`

Windows lock-safe fallback:
- `test-safe.bat :feature:map:testDebugUnitTest`

## 6) Exit Criteria

A SCIA improvement change set is complete when:
- required gradle gates pass
- no architecture rule drift is introduced
- SCIA toggle pause is materially reduced in dense scenario
- crash resilience remains intact
- non-UI filtering and single-write mutation paths are covered by tests
- docs in `docs/SCIA/` are updated with final deltas and quality rescore
