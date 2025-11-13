# Ballast Pill — Map Screen Implementation Spec

Scope: Define the UX, data flow, and controller rules for the Ballast Pill on the Map screen, including Fill/Drain animations, SSOT, and the “tap again to cancel” interaction.

## Goals
- Authentic, satisfying UI: blue column fills in ~20 s; drains in ~5 min.
- SSOT: every surface shows the same ballast number (kg≈L) from `GliderRepository.config.waterBallastKg`.
- Predictable gestures: pill captures taps; Fill/Drain buttons are easy to hit, placed above/below the pill, not over it.
- Simple control: tapping the active action again cancels the operation immediately.

## Data Model (SSOT)
- Source: `GliderRepository.config.waterBallastKg` (Double, kg).
- Capacity: `GliderModel.water.totalLiters` (Int). If present, clamp and time against this value. Avoid the fallback default (192) by populating models.
- Assumption: 1 kg ≈ 1 L (fresh water). If density matters later, add a per-model override.
- Example: JS1‑C 18 m → `totalLiters = 192` (already added to `core/common/.../GliderModels.kt`).

## UI Spec
- Component: `feature/map/.../ballast/BallastPill.kt` renders the pill and animated fill level (+ optional countdown while animating).
- Container: `MapUIWidgets.BallastWidget` (Map overlay) owns:
  - Pill size: 40×120 dp.
  - Buttons: `Fill` above the pill; `Drain` below the pill (centered). Buttons never overlap the pill.
  - Visibility: a single tap on the pill toggles controls on/off; auto-hide after 4 s of inactivity.
  - Do NOT auto-hide just because ratio is 0% or 100%; rely on timeout/toggle only.
  - Z-order: render at `zIndex(12f)` so taps beat map gestures and align with hamburger/flight-mode.
  - Edit mode: while UI edit mode is active, controls are hidden and the widget is draggable; position persisted via `MapUIWidgetManager`.

## Interaction & Commands
- Commands: `BallastCommand.StartFill`, `StartDrain`, `Cancel`, `ImmediateSet(kilograms)`.
- Toggle-to-cancel: If the current mode is Filling and the user taps Fill again, treat as `Cancel`. Likewise, Draining → tap Drain again → `Cancel`.
  - Option A (Controller): In `BallastController.submit`, if `state.value.mode` matches the incoming start command, call `stopAnimation()` instead of starting a new one.
  - Option B (UI): `onCommand` dispatches `Cancel` when the pressed button corresponds to the active mode; otherwise dispatches `StartFill`/`StartDrain`.
- Enabled states: Fill disabled at 100% (can’t exceed max); Drain disabled at 0% (can’t go negative). Buttons remain visible even when disabled.

## Controller Spec (`BallastController`)
- Durations (per full range): Fill 20 s, Drain 5 min. Partial deltas scale linearly by fraction of max.
  - `duration = fullDuration × abs(targetKg − startKg) / maxKg` (guard against zero/NaN).
  - Easing: existing cubic for a smooth visual (OK to keep).
- Tick rate: every ~200 ms compute eased kg and write through `BallastRepositoryAdapter.updateWaterBallast()` → SSOT config.
- Snapshot handling (important): Do NOT cancel your own animation each tick.
  - Replace the current cancel heuristic with either:
    1) “Source-of-truth tagging”: mark writes from the controller and ignore them in the cancel path; or
    2) “Expected path” check: only cancel if observed kg deviates from the expected animation value by a large tolerance (e.g., > 2 kg) indicating an external write.
  - Cancel immediately on `BallastCommand.Cancel` or if mode switches (Fill→Drain, Drain→Fill).
- Completion: when target reached or fraction ≥ 1.0, set exact target, reset animation state.

## Edge Cases
- Model without `water.totalLiters`: still animate, but durations/clamping may feel off; populate capacities for all models.
- Live external edits (e.g., Polar modal numeric entry): allowed; controller should cancel and adopt the new SSOT value.
- 0-capacity gliders: hide buttons; show pill as N/A or 0 with disabled actions.

## QA Checklist
- Tap pill → controls appear and stay visible at 0% and 100%; auto-hide after timeout.
- Tap Fill at 0% → pill reaches 100% in ~20 s; intermediate values visible in Polar modal.
- Tap Drain at 100% → pill reaches 0% in ~5 min (simulate with faster frame rate during tests if needed).
- While filling, tap Fill again → animation stops immediately; timer clears; state returns to Idle.
- While draining, tap Drain again → same immediate stop.
- While animating, open Polar modal: Water Ballast shows the live kg and changes smoothly.
- Map gestures do not trigger when interacting with the pill or buttons; hamburger/flight-mode remain interactive.

## Implementation Tasks (ordered)
1) UI controls behavior
   - MapUIWidgets.BallastWidget: keep controls visible at 0%/100%; place Fill above and Drain below; maintain auto-hide timer; keep toggle-on-tap.
   - Ensure `zIndex(12f)` for the ballast overlay.
2) Toggle-to-cancel
   - Pick Option A (controller) or Option B (UI). Recommendation: Option A (centralized, less UI branching).
3) Controller cancel heuristic
   - Remove “cancel on any change” during animation; adopt tagged-writes or expected-path tolerance.
4) Duration scaling
   - Use per-glider `totalLiters` for max; scale durations by fraction. Keep 20 s (fill) / 300 s (drain) for full-range.
5) Model data hygiene
   - Populate `water.totalLiters` for all supported gliders (JS1‑C 18 m is 192 L; JS1‑C 21 m already set).

## Notes
- SSOT ensures the Polar modal, nav drawer cards, and pill remain in sync without extra wiring.
- We assume kg≈L; if you need temperature/salinity accuracy, add an optional density field.

