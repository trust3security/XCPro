# Implementation Plan: Background/Foreground Blue Icon Desync

Date
- 2026-02-27

Status
- Implemented (Phases 1-3)

Owner
- XCPro Team

Problem
- After switching to another app and returning, ownship snail trail can show current position while blue triangle icon remains at an older position.

Goal
- Guarantee blue icon and ownship trail stay position-consistent across app background/foreground transitions.

Scope
- Ownship blue icon (`BlueLocationOverlay`) and ownship trail synchronization only.
- Map lifecycle resume path and display-pose render path.
- No UI redesign and no behavior change for OGN/ADS-B overlays.

Non-goals
- No changes to OGN glider trail pipeline.
- No changes to task map rendering.
- No changes to replay determinism semantics.

Architecture Constraints
- Keep trail rendering in UI/runtime layer.
- Keep business decisions out of Compose UI.
- Keep explicit live/replay time-base rules.
- Avoid hidden global mutable state.

## Root-Cause Hypothesis

1. Blue icon update can early-return when MapLibre style/source/layer is unavailable after resume.
2. Blue icon update is currently coupled behind camera update result in display render coordinator.
3. Trail updates are driven by separate data flow and may recover sooner than icon layer readiness.

## Phase 1: Decouple Icon Update From Camera Update Result

Change
- Ensure `positionController.updateOverlay(...)` is not blocked by camera update early-return.

Implementation target
- `feature/map/src/main/java/com/trust3/xcpro/map/DisplayPoseRenderCoordinator.kt`

Acceptance criteria
- Blue icon update attempts occur every display frame with valid pose, even if camera controller is unavailable.
- No camera behavior regressions.

## Phase 2: Blue Overlay Self-Heal On Missing Runtime Objects

Change
- In `BlueLocationOverlay.updateLocation(...)`, when style/source/layer is missing, attempt bounded re-init/rebind before returning.

Implementation targets
- `feature/map/src/main/java/com/trust3/xcpro/map/BlueLocationOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapOverlayManager.kt` (if needed for central re-init path)

Acceptance criteria
- Returning to app no longer leaves icon stuck due to missing layer/source.
- Re-init path is idempotent and safe.

## Phase 3: Forced One-Shot Sync On Resume

Change
- On lifecycle resume, force one immediate icon sync from latest display pose snapshot.

Implementation targets
- `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/LocationManager.kt` or render coordinator helper

Acceptance criteria
- First visible frame after resume has icon aligned with current ownship pose.
- No extra oscillation on resume.

## Phase 4: Synchronization Guardrails And Logging

Change
- Add debug-only guard logging when icon/trail delta exceeds threshold after resume window.
- Add bounded suppression to avoid log spam.

Implementation targets
- `feature/map/src/main/java/com/trust3/xcpro/map/trail/SnailTrailManager.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/MapPositionController.kt`

Acceptance criteria
- Debug diagnostics provide clear evidence if desync reappears.
- No release-build overhead.

## Phase 5: Regression Tests

Unit tests
- `DisplayPoseRenderCoordinator` test: icon update still called when camera update path returns null.
- `BlueLocationOverlay` test/fake-style harness: missing source/layer triggers self-heal branch.

Integration test (if feasible)
- Simulate `ON_STOP -> ON_RESUME` and verify icon position catches up to latest pose.

Acceptance criteria
- New tests fail on old behavior, pass with fix.

## Validation Gates

Required commands
- `./gradlew enforceRules`
- `./gradlew :feature:map:testDebugUnitTest`
- `./gradlew assembleDebug`

Manual verification script
1. Start live flight simulation or real GPS feed.
2. Confirm icon and trail aligned.
3. Send app to background for 10-30 seconds.
4. Return to app.
5. Confirm icon immediately matches current trail head/ownship position.
6. Repeat while changing map style once to validate style rebind stability.

## Rollout Strategy

1. Merge Phase 1 and 2 first (core correctness).
2. Merge Phase 3 next (resume hardening).
3. Add Phase 4 and 5 for long-term regression protection.

## Implementation Update (2026-02-27)

Completed in code:
- Phase 1: decoupled icon overlay updates from camera update null returns.
  - `feature/map/src/main/java/com/trust3/xcpro/map/DisplayPoseRenderCoordinator.kt`
- Phase 2: added bounded blue overlay self-heal for missing image/source/layer runtime objects.
  - `feature/map/src/main/java/com/trust3/xcpro/map/BlueLocationOverlay.kt`
- Phase 3: added one-shot display-frame sync on lifecycle resume.
  - `feature/map/src/main/java/com/trust3/xcpro/map/MapLifecycleManager.kt`

Added regression coverage:
- `feature/map/src/test/java/com/trust3/xcpro/map/DisplayPoseRenderCoordinatorTest.kt`
- `feature/map/src/test/java/com/trust3/xcpro/map/MapLifecycleManagerResumeSyncTest.kt`

Rollback plan
- Keep changes localized to icon/render lifecycle path.
- If issues occur, revert phase-by-phase in reverse order.

## Risks

Risk
- Over-aggressive re-init could cause duplicate layer operations.

Mitigation
- Check layer/source existence before add/remove.
- Keep operations idempotent and guarded by style readiness.

Risk
- Resume sync could conflict with ongoing replay frame updates.

Mitigation
- Gate one-shot sync by active mode and last pose timestamp validity.
