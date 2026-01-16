# Oversized Files Refactor Plan

Purpose: Split oversized Kotlin files into smaller, testable units while
preserving behavior, SSOT ownership, and time-base rules.

Scope (this plan):
- SnailTrailOverlay.kt (trail rendering)
- MapScreenViewModel.kt (map screen VM)
- IgcReplayController.kt (replay orchestration)

Non-goals:
- No behavioral changes.
- No new UI features.
- No changes to navigation/scoring logic.

References:
- ARCHITECTURE.md (SSOT, time base, MVVM/UDF)
- CODING_RULES.md (no UI logic in Compose, no new state owners)
- mapposition.md (map display flow)

----------------------------------------------------------------------
1) Constraints (hard)

- SSOT stays in repositories; ViewModel owns UI state only.
- Replay time base stays on IGC timestamps; live stays monotonic when present.
- No new shared mutable state outside Flow/StateFlow owners.
- No Android UI types in use cases or domain classes.
- No "xcsoar" string in production Kotlin code.

----------------------------------------------------------------------
2) SnailTrailOverlay split (target < 500 lines)

Current: map-layer rendering + palette + filtering in one file.

Split (implemented):
- SnailTrailOverlay.kt: layer wiring, lifecycle, render orchestration.
- SnailTrailStyle.kt: layer IDs + style property keys.
- SnailTrailModels.kt: RenderPoint, ColorRamp, ScreenBounds.
- SnailTrailPalette.kt: color ramps + expression builder + color helpers.
- SnailTrailFeatureBuilder.kt: line/dot feature builders.
- SnailTrailMath.kt: filtering, ranges, meters-per-pixel, width helpers.

Behavior unchanged; moved code only.

----------------------------------------------------------------------
3) MapScreenViewModel split (target < 500 lines)

Current: UI state + observers + replay + racing handling in one file.

Split strategy (implemented, composition only):
- MapScreenViewModel.kt: state ownership + public API.
- MapScreenReplayCoordinator.kt: replay + racing event handling (no state ownership).
- MapScreenObservers.kt remains for map observers.

All StateFlow owners stay in MapScreenViewModel; helpers update via callbacks.

----------------------------------------------------------------------
4) IgcReplayController split (target < 500 lines)

Current: loading + session state + playback loop in one file.

Split (implemented):
- IgcReplayController.kt: public API + orchestration.
- ReplayPipeline.kt: fusion pipeline setup + forwarder.
- ReplaySessionPrep.kt: log prep + densify helper.
- ReplayFinishRamp.kt: finish ramp helper.

No changes to time-base behavior or replay outputs.

----------------------------------------------------------------------
5) Validation

- Run existing unit tests for replay + racing.
- Smoke replay + racing FABs (ensure unchanged behavior).
- Confirm file sizes under 500 lines.

----------------------------------------------------------------------
6) Definition of done

- Each target file is < 500 lines.
- No functional regressions (tests pass).
- Architecture constraints still satisfied.

Current status:
- SnailTrailOverlay.kt: 402 lines (done).
- MapScreenViewModel.kt: 440 lines (done).
- IgcReplayController.kt: 472 lines (done).
