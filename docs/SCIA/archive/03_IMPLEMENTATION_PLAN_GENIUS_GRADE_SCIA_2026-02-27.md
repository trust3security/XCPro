# Implementation Plan: Genius-Grade SCIA Stability And UX

Date
- 2026-02-27

Status
- Proposed

Owner
- XCPro Team

Goal
- Eliminate perceived SCIA toggle pause and harden SCIA runtime for release-grade reliability while preserving MVVM + UDF + SSOT boundaries.

Target quality
- Architecture cleanliness: >= 9.0/10
- Maintainability/change safety: >= 8.5/10
- Release readiness: >= 8.5/10
- "Genius-grade" bar: >= 9.0/10 with full verification evidence

Non-goals
- No redesign of OGN protocol ingest.
- No change to task or replay architecture.
- No migration away from DataStore SSOT for preferences.

## 0) Architecture Declarations (Pre-Implementation)

SSOT ownership:
- SCIA enabled preference: `OgnTrafficPreferencesRepository` (`showSciaEnabledFlow`).
- OGN overlay enabled preference: `OgnTrafficPreferencesRepository` (`enabledFlow`).
- Trail segment history: `OgnGliderTrailRepository` (`segments` state flow).
- Selected trail aircraft keys: `OgnTrailSelectionPreferencesRepository`.
- Runtime render cache/state: `MapOverlayManager` and `OgnGliderTrailOverlay` (UI/runtime only, not SSOT).

Dependency direction:
- UI -> ViewModel -> UseCase -> Repository.
- Runtime map renderer consumes already-derived state, does not own domain policy.

Time base:
- No new domain time math planned in this workstream.
- Existing repository clocks remain monotonic for trail retention.

## Phase 1: Move Trail Selection Filtering Out Of Compose Binding

Problem addressed
- Large trail lists are filtered in Compose binding path (`MapScreenBindings.kt`), adding main-thread work and jank risk.

Change
- Shift selected-aircraft trail filtering to non-UI pipeline stage (ViewModel/use-case/runtime layer), keeping Composables render-only.

Implementation sketch
- Replace `remember(...){ rawOgnGliderTrailSegments.filter { ... } }` heavy path in UI binding with pre-filtered flow/state.
- Keep filtering deterministic and key-normalized exactly once per update.
- Consolidate trail-selection flow collection to a single UI boundary point and pass derived state downward.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenBindings.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
- optionally `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt`

Acceptance criteria
- No large-list filtering in Compose binding path.
- Rendered trail output remains behaviorally identical for same inputs.

Rollback safety
- Keep previous filter logic available in tests for behavioral parity validation.

## Phase 2: Add Explicit Pending Toggle UX (No SSOT Duplication)

Problem addressed
- SCIA switch feedback waits for persistence roundtrip and can look stalled.

Change
- Introduce transient "pending mutation" UX state for SCIA/OGN toggle actions.
- Keep DataStore as single truth for actual enabled value.
- Add in-flight mutation serialization/coalescing so rapid taps do not queue redundant toggle writes.

Implementation sketch
- Emit pending-start/pending-end effect/state around toggle mutation.
- Disable rapid repeat taps while pending.
- On failure, clear pending and show existing error toast.
- Guard toggle mutation entry with a lightweight mutex/token so only one SCIA/OGN toggle mutation runs at a time.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenViewModel.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`

Acceptance criteria
- Immediate visual acknowledgement on tap.
- No duplicated authoritative toggle state.
- Rapid repeated taps do not queue redundant conflicting writes.

Rollback safety
- Pure UI transient state; no persisted schema changes.

## Phase 3: Consolidate SCIA+OGN Preference Mutation Into Single Write

Problem addressed
- SCIA enable from OGN-off path currently performs two sequential writes.

Change
- Add repository/use-case API to atomically set related OGN preference keys in one `dataStore.edit` transaction for user-toggle path.

Implementation sketch
- Introduce a single mutation method in OGN preferences repository:
  - set OGN enabled and SCIA enabled together when needed.
- Update coordinator toggle logic to use consolidated method.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/ogn/OgnTrafficPreferencesRepository.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenUseCases.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapScreenTrafficCoordinator.kt`

Acceptance criteria
- User-toggle SCIA-on path persists in one transaction.
- Existing behavior (auto-enable OGN) is preserved.

Rollback safety
- Keep old methods during migration, then remove after tests pass.

## Phase 4: Move Trail Feature Construction Off Main/UI Path

Problem addressed
- First SCIA render can build many features synchronously.

Change
- Build SCIA `FeatureCollection` on background dispatcher.
- Keep map source mutation on map-safe thread.

Implementation sketch
- Split overlay render into build phase and apply phase.
- Add stale-job cancellation to avoid queue buildup.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/OgnGliderTrailOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`

Acceptance criteria
- Heavy feature construction removed from UI coroutine path.
- Failure handling remains non-fatal.

Rollback safety
- Temporary fallback switch to synchronous path during rollout if needed.

## Phase 5: User-Triggered Immediate Render And First-Enable Burst Control

Problem addressed
- Display mode throttle can delay first visible repaint on SCIA enable.
- First-enable dense history can still burst expensive work.

Change
- Ensure one-shot immediate render on SCIA enable transition.
- Add warm-start subset policy (for example 2k-4k newest) before convergence to full cap.
- Reduce large-list compare/key pressure in render scheduling path and avoid duplicate startup pushes where possible.

Implementation sketch
- Detect SCIA off->on transition and invoke trail update with `forceImmediate = true`.
- Apply deterministic newest-first warm-start and converge on next cadence.
- Replace expensive list-as-key/equality hot paths with cheap render signature/version strategy where safe.
- De-duplicate map-ready and first compose-effect trail push to avoid redundant heavy comparisons.

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenRootEffects.kt`
- optionally `feature/map/src/main/java/com/trust3/xcpro/map/OgnGliderTrailOverlay.kt`

Acceptance criteria
- No perceptible first-enable render stall attributable to cadence delay.
- Warm-start reduces first-enable burst cost.
- Reduced CPU overhead from repeated O(n) list comparisons in heavy sessions.

Rollback safety
- Keep behavior behind isolated SCIA branch.

## Phase 6: User Clarity Improvements For Empty Selection

Problem addressed
- SCIA can be ON while zero aircraft are selected.

Change
- Add explicit guidance when SCIA is ON and selected-aircraft set is empty.

Implementation sketch
- Detect `showSciaEnabled && selectedAircraftKeys.isEmpty()` in presentation state.
- Render clear action text ("select aircraft to show trails").

Likely files
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContent.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapBottomSheetTabs.kt`

Acceptance criteria
- Reduced confusion for "SCIA enabled but no trails visible".

Rollback safety
- UI-only change.

## Phase 7: Regression Hardening And Verification

Change
- Add/extend tests for:
  - non-UI filtering behavior,
  - pending UX state,
  - rapid-tap mutation coalescing/serialization behavior,
  - single-write preference mutation,
  - immediate-render/warm-start policy,
  - startup persisted-selection load behavior (avoid visible empty-trail transient where practical),
  - duplicate map-ready/effect push de-dup behavior,
  - background feature build correctness.

Likely test files
- `feature/map/src/test/java/com/trust3/xcpro/map/MapScreenViewModelTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/OgnGliderTrailOverlayRenderPolicyTest.kt`
- new focused tests in `feature/map/src/test/java/com/trust3/xcpro/map/`

Required verification commands
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Windows fallback for test result lock issues
- `test-safe.bat :feature:map:testDebugUnitTest`

Acceptance criteria
- All required gates pass.
- No architecture drift.
- Measurable SCIA responsiveness improvement in dense scenarios.

## Delivery Strategy

Recommended PR sequence:
1. Phase 1 + tests
2. Phase 2 + tests
3. Phase 3 + tests
4. Phase 4 + tests
5. Phase 5 + tests
6. Phase 6 + tests
7. Phase 7 validation + docs finalization

Each phase should include:
- implementation summary
- changed files
- tests added/updated
- command outputs summary
- updated quality rescore

## Risk Register

Risk: introducing race conditions between background build and map style changes.
- Mitigation: generation token and stale-job cancellation in overlay manager.

Risk: pending UI state drifts from persisted state.
- Mitigation: pending is transient only; persisted flow remains authoritative and clears pending.

Risk: consolidated preference mutation changes behavior unexpectedly.
- Mitigation: parity tests for existing SCIA/OGN toggle semantics before removing old path.

Risk: extra complexity in runtime rendering logic.
- Mitigation: isolate helper functions, keep strict single responsibility per class.

Risk: replay or unrelated overlays regress.
- Mitigation: keep changes scoped to SCIA render path and execute full map module tests.
