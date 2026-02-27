# SCIA Crash And Pause Investigation

Date
- 2026-02-27

Status
- Investigated on current branch state.

## 1) Questions Answered

Q1: What is displayed when SCIA is enabled?
- OGN glider trail/wake lines are rendered for selected aircraft only.
- OGN traffic is force-enabled if required.
- If no aircraft are selected, no SCIA lines are rendered.

Q2: Can SCIA when on cause app crash?
- Current implementation has hardening to reduce crash risk from SCIA-related preference and overlay failures.
- Residual crash risk from this path is now low, but UI jank/pause risk remains.

Q3: Why does SCIA toggle look paused?
- Most likely due to persisted-toggle roundtrip plus heavy first trail render work, not an infinite loop.

## 2) Evidence Summary

Preference hardening:
- SCIA and related toggles are wrapped in guarded mutation path with toast fallback:
  - `MapScreenTrafficCoordinator.kt:345-353`
  - `MapScreenTrafficCoordinator.kt:247-256`

Auto-enable behavior:
- SCIA enforces OGN overlay on if needed:
  - `MapScreenTrafficCoordinator.kt:193-202`
  - `MapScreenTrafficCoordinator.kt:254`

Render hardening:
- Manager catches trail render failures:
  - `MapOverlayManager.kt:375-383`
- Overlay catches per-render failures and attempts source clear:
  - `OgnGliderTrailOverlay.kt:58-90`

Render load boundaries:
- Repository can hold up to 24,000 segments:
  - `OgnGliderTrailRepository.kt:333`
- Overlay render cap trims to newest 12,000:
  - `OgnGliderTrailOverlay.kt:149,153`

Display mode latency:
- `BALANCED` and `BATTERY` intentionally delay render cadence:
  - `OgnDisplayUpdateMode.kt:17-25`

## 3) Why Pause Can Happen (Root Cause)

Likely path during toggle-on:
1. User toggles SCIA.
2. Coordinator persists state through DataStore-backed preference write.
3. UI waits for flow update that confirms new persisted value.
4. Render pipeline receives potentially large trail list and builds many GeoJSON features.

The feature-build section in `OgnGliderTrailOverlay.render(...)` is synchronous in current implementation (`OgnGliderTrailOverlay.kt:64-86`), so large first renders can briefly stall responsiveness.

Important: no loop was found in SCIA toggle wiring.

Additional missed contributors (found in second code pass):
- Main-thread list filtering in Compose binding:
  - `MapScreenBindings.kt:94-107` filters raw trail segments by selected keys in UI composition path.
  - With large segment lists this can add jank before render handoff.
- Dual preference writes on SCIA enable when OGN is off:
  - `MapScreenTrafficCoordinator.kt:254` then `:256` performs two sequential writes (`setOverlayEnabled(true)` then `setShowSciaEnabled(true)`), increasing latency window.
- Display mode throttle can delay first user-visible SCIA render:
  - render throttle uses `lastRenderMonoMs` initialized at overlay init (`MapOverlayManager.kt:289-291`);
  - SCIA enable updates call `updateOgnGliderTrailSegments(..., forceImmediate = false)` (`MapOverlayManager.kt:343,349`),
    so `BALANCED/BATTERY` may intentionally defer first trail repaint.
- Historical segment retention raises first-enable cost:
  - when streaming disabled, repository clears samples but keeps existing segments (`OgnGliderTrailRepository.kt:97-99`),
    allowing large retained history to be rendered on next enable.

Additional contributors (found in third code pass):
- Rapid-tap mutation fan-out:
  - toggles launch independent preference mutations (`MapScreenTrafficCoordinator.kt:331-337`) with no in-flight coalescing.
  - repeated taps can queue duplicate writes and extend perceived lag.
- Per-aircraft enable path triggers multi-source writes:
  - marker-sheet enable writes trail selection (`MapScreenContent.kt:733`) and then may trigger SCIA/OGN toggle writes (`MapScreenContent.kt:738-741`).
  - temporary write-order skew can show SCIA enabled with no visible trails for a short interval.
- Large-list equality and key-compare overhead:
  - manager compares full segment lists before scheduling (`MapOverlayManager.kt:344-345`).
  - Compose effect keys on full trail list (`MapScreenRootEffects.kt:84,103`).
  - these are O(n) operations and become significant with large lists.
- Startup/style path duplicate trail pushes:
  - map-ready path forces immediate trail update (`MapScreenScaffoldInputs.kt:207-210`),
  - then compose overlay effects push again (`MapScreenRootEffects.kt:103-104`),
  - causing extra large-list compare/work on heavy sessions.
- Initial selected-aircraft state starts empty:
  - `OgnTrailSelectionViewModel` uses `initialValue = emptySet()` (`OgnTrailSelectionViewModel.kt:21`),
  - while persisted keys load asynchronously; this can briefly suppress trails on startup.
- Duplicate trail-selection collection in UI layer:
  - `OgnTrailSelectionViewModel` is collected in both `MapScreenBindings.kt` and `MapScreenContent.kt`,
  - increasing redundant state reads/recompositions on heavy traffic updates.

Additional contributors (found in fourth code pass):
- Startup callback duplication on map readiness:
  - `MapViewHost` calls `onMapReady` once before map initialization and again after initialization
    (`MapScreenSections.kt:221-225`, `:240-244`).
  - `onMapReady` forces immediate SCIA trail update pushes (`MapScreenScaffoldInputs.kt:207-210`),
    so startup can do redundant heavy trail handoff work.
- Per-segment key-match helper overhead:
  - `selectionSetContainsOgnKey(...)` rebuilds a normalized selected-key set on every invocation
    (`OgnAddressing.kt:84-85`).
  - current SCIA segment filter path calls this for each segment in Compose (`MapScreenBindings.kt:97-109`),
    amplifying CPU cost as segment count grows.

## 4) Current Risk Register

Risk: SCIA toggle feels laggy on dense traffic history.
- Severity: Medium
- Likelihood: Medium to High
- Cause: main-path render work, Compose-side filtering cost, persistence roundtrip, and possible display-mode throttle delay.

Risk: SCIA-related crash from render exception.
- Severity: High (if occurs)
- Likelihood: Low after hardening
- Mitigation in place: manager and overlay try/catch guards.

Risk: silent user confusion when SCIA is on but no trails visible.
- Severity: Low to Medium
- Likelihood: Medium
- Cause: empty selected-aircraft set yields empty rendered list.

## 5) Test Evidence (Current)

Behavior tests:
- `MapScreenViewModelTest.showOgnScia_defaultsToDisabled` (`MapScreenViewModelTest.kt:668`)
- `MapScreenViewModelTest.onToggleOgnScia_enablingForcesOgnTrafficOn` (`MapScreenViewModelTest.kt:675`)
- `MapScreenViewModelTest.onToggleOgnTraffic_ignoredWhileSciaIsEnabled` (`MapScreenViewModelTest.kt:705`)

Render cap tests:
- `OgnGliderTrailOverlayRenderPolicyTest` (`OgnGliderTrailOverlayRenderPolicyTest.kt`)

Verification status from latest run in this session:
- `./gradlew enforceRules`: passed
- Scoped SCIA-related map tests: passed
- Full repo `testDebugUnitTest`: not completed in this session (interrupted by user)

## 6) Scorecard (Requested)

Architecture cleanliness
- Score: 8.1 / 10
- Reason: layering is mostly clean and SSOT boundaries are preserved.

Maintainability/change safety
- Score: 7.3 / 10
- Reason: hardening improved safety, but responsiveness path still concentrates complexity in render runtime.

Release code grade
- Score: 6.9 / 10 (B-)
- Reason: good safety direction, but unresolved UX pause risk and incomplete full verification run in this session.

"Genius dev/programmer" likely score now
- Score: 6.5 to 7.0 / 10
- Reason: a top-tier bar expects off-main heavy render prep, explicit pending UX state, and full gate evidence.

## 7) Recommended Next Moves

Priority 1
- Move SCIA trail feature construction off main/UI path.

Priority 2
- Move selected-aircraft filtering out of Compose binding path and into non-UI pipeline stage.

Priority 3
- Add explicit pending state for SCIA toggle so switch feedback is immediate and deterministic.

Priority 4
- Add single-write SCIA+OGN preference mutation API for user-toggle path to reduce latency and transient inconsistency.

Priority 5
- Force immediate one-shot trail render for explicit user SCIA enable action (even when display mode is throttled).

Priority 6
- Add mutation in-flight serialization/coalescing for SCIA/OGN toggles to prevent duplicate queued writes under rapid taps.

Priority 7
- Reduce O(n) large-list comparison pressure in SCIA render path (list keying/equality strategy).

Priority 8
- Complete full verification gates after implementation:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
