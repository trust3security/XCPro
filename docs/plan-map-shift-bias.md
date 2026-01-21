# Map Shift Bias (Look-Ahead) Implementation Plan

Goal: add XCSoar-style forward bias for the glider marker so map movement feels smoother and gives look-ahead. The bias should be directional (track or target), smoothed, and visual-only.

This plan is written to satisfy:
- CODING_RULES.md (explicit, testable, no UI logic)
- ARCHITECTURE.md (SSOT, time base, UDF)

## 1) Scope

In scope:
- Directional screen offset that shifts the map center forward along track or target.
- Smoothing of that offset to reduce jitter.
- Configurable bias mode and strength (initially hidden or a settings knob).
- No changes to SSOT (FlightDataRepository remains authoritative).

Out of scope:
- Changing navigation logic or aircraft state.
- New sensors or wind estimation logic.
- Map rendering changes outside camera/overlay positioning.

## 2) Constraints and Principles

- Visual-only: do not write back smoothed positions to SSOT.
- Time base: live uses monotonic; replay uses replay timestamps.
- No UI business logic: math lives in a pure class, called from runtime controllers.
- MapLibre types remain in UI/runtime layer (no MapLibre in domain).
- No "xcsoar" string in production Kotlin code.

## 3) Data Flow (SSOT Ownership)

- Sensors -> FlightDataRepository (SSOT)
- Map display pose -> LocationManager (visual-only)
- Bias offset -> MapShiftBiasCalculator (pure)
- Camera update -> MapPositionController -> MapLibre map
  
Orientation context:
- Active map orientation mode comes from MapOrientationManager, which
  resolves Cruise / Final Glide vs Thermal / Circling preferences.
  See `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/OrientationSettingsScreen.kt`.

## 4) Desired Behavior

Bias should:
- Be active only in NORTH_UP (Track Up + Heading Up disable directional bias).
- Use TRACK bearing only (TARGET bias out of scope for XCPro).
- Fade out or freeze when speed is below threshold or bearing invalid.
- Be smoothed over time (moving average).
- Combine with existing glider screen percent (vertical placement).
  
Per-mode rule:
- If the active orientation (Cruise / Final Glide or Thermal / Circling) is
  TRACK_UP or HEADING_UP, directional bias is disabled for that mode.
- XCSoar disables directional bias while circling (DisplayMode::CIRCLING)
  regardless of orientation. For parity, XCPro should disable bias in
  Thermal/Circling mode and reset the bias smoother.
  
Design decision (XCPro UX continuity):
- Keep applying the existing vertical glider position offset even when
  directional bias is disabled. This matches current XCPro behavior,
  even though XCSoar centers the glider when bias is off in NORTH_UP.

Speed gate:
- XCSoar uses ground speed > 8 m/s for TRACK bias.
- XCPro should use a dedicated bias gate (default 8 m/s) rather than the
  heading min-speed threshold (2 m/s), to avoid noisy low-speed bias.

States:
- OFF: no directional bias.
- ACTIVE_TRACK: bias along track bearing.
- ACTIVE_TARGET: bias along target bearing.
- HOLD: temporarily keep last bias when input is invalid or speed below threshold.

Transitions:
- OFF -> ACTIVE_* when bias enabled and inputs valid.
- ACTIVE_* -> HOLD when inputs invalid.
- HOLD -> ACTIVE_* when inputs valid again.
- Any -> OFF when bias disabled.

## 5) Proposed API and Types

New types in `feature/map` (pure Kotlin, no Android):
- `enum class MapShiftBiasMode { NONE, TRACK, TARGET }`
- `data class MapShiftBiasConfig(...)`
- `data class ScreenOffset(val dxPx: Float, val dyPx: Float)`
- `class MapShiftBiasCalculator(...)`

Inputs (calculator):
- `trackDeg`, `targetDeg?`, `mapBearingDeg`
- `speedMs`, `minSpeedMs`
- `screenWidthPx`, `screenHeightPx`
- `gliderScreenPercent` (existing preference)
- `biasStrength` (0..1)

Output:
- `ScreenOffset(dxPx, dyPx)`

## 6) Math (Direction + Magnitude)

Reference direction:
- Use reciprocal bearing relative to screen:
  - `theta = (bearingDeg + 180) - mapBearingDeg`
- Screen coords: +x to right, +y down
  - dx = sin(theta) * magnitude
  - dy = -cos(theta) * magnitude

Magnitude:
- Let `positionFactor = (50 - gliderScreenPercent) / 100`
- Use independent scaling for width/height (XCSoar uses both):
  - `dx = sin(theta) * screenWidth * positionFactor`
  - `dy = -cos(theta) * screenHeight * positionFactor`
- Apply `biasStrength` and clamp to safe max (e.g., 0.35 * min dimension)

Notes:
- If in TRACK_UP/HEADING_UP, do not apply directional bias.
- Continue to apply the existing vertical glider position offset.
- XCSoar limits `glider_screen_position` to 10..50 and disables bias at 50.
  XCPro now aligns to 10..50, so a >50 case is no longer possible.
- XCSoar's NORTH_UP/WIND_UP path only applies glider offset when bias is enabled.
   XCPro currently applies vertical offset always; decide whether to keep
   existing behavior (recommended for UX continuity) or match XCSoar strictly.
- If speed < minSpeed or bearing invalid -> HOLD last valid offset or fade to 0.

## 7) Integration Points

Primary integration in map runtime layer:
- `LocationManager` computes display pose per frame.
- Add bias calculation in `LocationManager` or `MapPositionController` (preferred).
- Apply offset using map padding (left/right + top/bottom) or camera target shift.
- Apply bias only when camera tracking is active (XCSoar `IsNearSelf()` equivalent).
- Apply bias only when FlightModeSelection != THERMAL (XCSoar circling parity).

Preferred approach:
- Use dynamic padding (no geo math).
- Convert offset -> padding:
  - If dx > 0: increase left padding
  - If dx < 0: increase right padding
  - If dy > 0: increase top padding
  - If dy < 0: increase bottom padding
- Add this on top of existing glider padding.

Fallback approach:
- If padding is insufficient, compute new camera target by projecting screen offset
  to geo coordinates and shifting camera target accordingly.

## 8) Phases

Phase 0: Decisions and acceptance criteria
- Confirm bias thresholds and UI exposure.
- Confirm NORTH_UP-only directional bias for Cruise/Final Glide and Thermal.
- Write acceptance criteria (visual checks + tests).

Phase 1: Core config and calculator (pure)
- Implement `MapShiftBiasCalculator` and data types.
- Add unit tests (no Android):
  - NORTH_UP + TRACK bias math
  - Invalid bearing -> HOLD/zero
  - Speed below threshold -> HOLD/zero
  - Glider screen percent effect

Phase 2: Wire into runtime pipeline
- Add bias config source (preferences or feature flags).
- Integrate into `MapPositionController.updateCamera` or `LocationManager`.
- Use existing offset history to smooth directional offset.
- Ensure time base usage is display-only.

Phase 3: Replay + edge cases
- Verify replay path (render-frame sync) still behaves.
- Add replay tests if available (or harness logging for manual QA).

Phase 4: Optional UI controls
- Add settings panel entries in "General -> Orientation" (if desired).
- Ensure Cruise / Final Glide and Thermal / Circling modes remain the only
  orientation modes (North/Track/Heading).
- Keep defaults conservative and reversible.

Phase 5: Docs and regression safety
- Update `mapposition.md` with bias flow.
- Add a short note in `ARCHITECTURE_DECISIONS.md` if needed.

## 9) Tests

Unit tests (JVM):
- Calculator math and clamping.
- State transitions (OFF/ACTIVE/HOLD).
- Consistency across orientation modes.

Optional replay tests:
- Verify no drift on steady track.
- Verify stable bias during constant speed.

## 10) Risks and Mitigations

Risk: Over-biasing makes map feel "detached".
- Mitigation: conservative default strength + clamp.

Risk: Jitter when speed is low or track is noisy.
- Mitigation: speed gate + HOLD state + smoothing window.

Risk: Left/right padding causes label collisions.
- Mitigation: cap horizontal offset; keep text layers above.

## 11) Acceptance Criteria

- In straight flight, glider appears ahead of center in NORTH_UP without jitter.
- When speed drops below threshold, bias fades or holds smoothly (no snapping).
- Replay behaves the same as live (no time-base misuse).
- No SSOT changes; only visual display is affected.

## 11A) Decision Matrix (XCPro Behavior)

Legend:
- Bias = directional track bias (left/right + forward).
- Vertical offset = existing glider screen percent offset.

| Orientation Mode | Flight Mode | Tracking Self | Glider % | Bias Enabled | Behavior |
|---|---|---|---|---|---|
| NORTH_UP | Cruise/Final Glide | Yes | 10..49 | Yes | Apply directional bias + vertical offset |
| NORTH_UP | Cruise/Final Glide | Yes | 50 | Yes | Disable directional bias; keep vertical offset |
| NORTH_UP | Thermal/Circling | Yes | any | Yes | Disable directional bias; keep vertical offset; reset bias smoother |
| TRACK_UP | any | Yes | any | Yes | Disable directional bias; keep vertical offset |
| HEADING_UP | any | Yes | any | Yes | Disable directional bias; keep vertical offset |
| any | any | No (panning/return) | any | Yes | Disable directional bias; keep vertical offset |
| any | any | any | any | Bias OFF | Disable directional bias; keep vertical offset |

## 13) XCSoar Reference Specifics (Validated)

These are the exact details from XCSoar that inform parity:
- Bias is only offered when orientation is NORTH_UP or WIND_UP.
  - XCPro only supports NORTH_UP/TRACK_UP/HEADING_UP, so apply bias
    only when the active mode is NORTH_UP.
- `glider_screen_position` is percent from bottom. Default is 20.
  - Settings range in UI: 10..50 (step 5).
- Directional bias is applied only if:
  - `glider_screen_position != 50`, and
  - `map_shift_bias != NONE`.
  - Therefore, setting `glider_screen_position = 50` (center) disables
    directional bias even if `map_shift_bias` is TRACK/TARGET.
- TRACK bias requires:
  - `basic.track_available && basic.ground_speed_available`,
  - `basic.ground_speed > 8 m/s`.
- TARGET bias requires `current_leg.solution_remaining.IsDefined()`,
  but XCPro will not implement TARGET bias in this scope.
- Direction uses the reciprocal of bearing (track/target), and then subtracts screen angle:
  - `angle = bearing.Reciprocal() - screen_angle`.
- Offset is smoothed with an average of the last 30 samples.
  - Offset history is reset when entering circling mode.
- In circling mode, the screen origin is centered and bias is bypassed.
  Additionally, if a thermal estimate is valid, XCSoar blends the map
  center toward the estimated thermal location instead of using the
  raw fix. This is another reason to disable directional bias during
  Thermal/Circling in XCPro to avoid conflicting offsets.
- When orientation is not NORTH_UP/WIND_UP, XCSoar uses a fixed vertical
  glider screen position without directional bias.
- In NORTH_UP/WIND_UP, XCSoar does not apply glider offset unless bias is enabled.
- Location updates are gated: `SetLocationLazy` updates only when the
  new fix moves more than 0.5 px in screen space.

## 12) Ownership

- Map runtime: `LocationManager` and `MapPositionController`
- Config: `MapOrientationPreferences` (or feature flags)
- Tests: `feature/map/src/test/...`
- Docs: `mapposition.md`, this plan
