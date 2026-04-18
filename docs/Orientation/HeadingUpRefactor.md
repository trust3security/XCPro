
# Heading Up Refactor Plan (Temporary)

This is a temporary refactor plan to address Heading Up jitter without breaking
architecture rules in `ARCHITECTURE.md` and `CODING_RULES.md`.

Status: Implemented on 2026-01-09 (source separation + hysteresis in `OrientationDataSource`).
Keep this file for rationale and rollback guidance.

2026-01-09 follow-up:
- Heading data is carried separately from map bearing in `OrientationData`.
- Icon rotation now matches XCSoar (heading relative to screen angle).
- Device heading is gated by `isFlying`/min-speed and AHRS-first source selection.

## Problem statement

In HEADING_UP, the map bearing jumps even when the phone is stationary.
JITTER logs show two stable, reliable heading sources that disagree
(magnetometer vs rotation vector). The current pipeline mixes them into a single
filtered heading, so the output flips between sources.

## Constraints

- No UI-side logic or state changes (UDF/SSOT rules).
- Keep changes inside sensor/orientation pipeline.
- Do not add new production strings referencing XCSoar.
- Preserve existing orientation modes and settings behavior.
- Keep files ASCII/UTF-8 only.

## Target behavior

- Use **one authoritative heading source at a time**.
- Prefer rotation-vector heading when available and fresh.
- Fall back to magnetometer only if rotation-vector is stale/unavailable.
- Fall back to wind/track only if no reliable heading source exists.
- Avoid rapid source switching (add hysteresis).

## Proposed implementation (minimal-impact)

### 1) Split heading filters by source (OrientationDataSource)

Current: `updateHeading()` mixes compass and attitude into a single filtered heading.

Change:
- Maintain two filtered headings:
  - `filteredCompassHeading`
  - `filteredAttitudeHeading`
- Update each filter only from its own source.
- Track freshness and reliability per source.

### 2) Choose a primary heading before calling HeadingResolver

In `OrientationDataSource.updateOrientationData()`:
- If attitude heading is fresh + reliable -> use it as `primaryHeading`.
- Else if compass heading is fresh + reliable -> use it as `primaryHeading`.
- Else `primaryHeading = null`.

Pass `primaryHeading` into `HeadingResolver` (as the "compass" input or rename the
parameter if you choose to refactor the signature).

### 3) Add source hysteresis (avoid flip-flop)

Keep a small state machine:
- `activeHeadingSource = ATTITUDE | COMPASS | NONE`
- When a new source is available, require it to be stable for
  `SOURCE_SWITCH_STABLE_MS` (e.g., 500 ms) before switching.
- If active source becomes stale/unreliable, drop to the next available source.

### 4) Optional: tilt-compensate magnetometer

If compass remains noisy, compute azimuth from accelerometer + magnetometer
using `SensorManager.getRotationMatrix()` and `getOrientation()`.
This can replace the raw `atan2(y, x)` heading in `SensorRegistry`.

## Files to touch

- `feature/map/src/main/java/com/trust3/xcpro/OrientationDataSource.kt`
  - Split filters, add source selection + hysteresis.
- `feature/map/src/main/java/com/trust3/xcpro/orientation/HeadingResolver.kt`
  - Renamed `compassHeadingDeg` to `primaryHeadingDeg` for clarity.
- `feature/map/src/main/java/com/trust3/xcpro/MapOrientationManager.kt`
  - No logic change; keep JITTER logging for verification.
- `feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt`
  - Optional tilt-compensated heading (if needed).

## Risk assessment

Low risk if the resolver continues to receive a single heading input.
Main risk is unintended behavior in HEADING_UP when the primary source is absent.
Mitigate by keeping the existing wind/track fallbacks intact.

## Validation checklist

1) Stationary table test
   - HEADING_UP should not jump.
   - JITTER logs should be silent or significantly reduced.

2) Slow taxi / low-speed glide
   - Ensure heading remains stable; track fallback should not jitter.

3) In-flight turns
   - Heading should track smoothly without large delays.

4) Regression
   - Track Up unaffected.

## Rollback plan

If behavior regresses, revert the source selection/hysteresis changes in
`OrientationDataSource` and keep only the JITTER logging for future analysis.

