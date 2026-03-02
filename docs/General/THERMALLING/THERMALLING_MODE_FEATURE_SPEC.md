# THERMALLING_MODE_FEATURE_SPEC

Date: 2026-03-02
Status: Draft
Owner: XCPro Team

## 1) Goal

Add configurable thermalling automation so XCPro can:

- Detect current-user thermalling from existing `isCircling` signal.
- Optionally switch to Thermal flight mode automatically.
- Optionally apply a thermalling zoom automatically.
- Restore previous mode/zoom after circling stops (with configurable delay).

This must be user-configurable and optional.

## 2) Baseline (Current Behavior)

Current code supports:

- Thermal/circling detection (`isCircling`, `currentThermalValid`) in sensor pipeline.
- Manual flight mode switching (`CRUISE`, `THERMAL`, `FINAL_GLIDE`) via map UI controls.
- Manual map zoom changes.

Current code does not support:

- Timer-based auto switch to Thermal mode.
- Timer-based auto zoom for thermalling.
- Auto restore of pre-thermal mode/zoom on thermal exit.

## 3) User Stories

1. As a pilot, when circling starts, I want XCPro to switch into Thermal mode after `N` seconds.
2. As a pilot, when circling stops, I want XCPro to return to my previous mode after `M` seconds.
3. As a pilot, I want thermalling zoom to change automatically on entry.
4. As a pilot, if I manually adjust zoom during thermal, I want XCPro to respect that in the active thermal session.
5. As a pilot, if Thermal screen is not available, I still want zoom-only thermalling behavior on my current screen.
6. As a pilot, I want this feature disabled by default and fully configurable.

## 4) Proposed Behavior

Thermalling automation is driven by a state machine using existing `isCircling`.

Definitions:

- `enter timer`: delay before thermalling mode/zoom activates after circling begins.
- `exit timer`: delay before restoring previous mode/zoom after circling ends.
- `pre-thermal snapshot`: mode + zoom captured once when entering active thermalling state.

### 4.1 Activation

When `isCircling = true` continuously for `enterDelaySeconds`:

- Capture `pre-thermal snapshot`:
  - previous mode (`CRUISE` or `FINAL_GLIDE` or other active mode)
  - previous zoom
- If configured, switch mode to `THERMAL` (only if Thermal mode is available/visible).
- If configured, apply thermalling zoom.

### 4.2 Active Thermalling

While active:

- User can manually change zoom.
- Session keeps current zoom as the active thermal zoom for that session.
- No repeated recapture of pre-thermal snapshot.

### 4.3 Deactivation

When `isCircling = false` continuously for `exitDelaySeconds`:

- If configured, restore previous mode from snapshot.
- If configured, restore previous zoom from snapshot.
- Clear thermal session state.

### 4.4 Mode Availability Fallback

If `THERMAL` mode is hidden in current profile:

- Do not force mode switch.
- If `zoomOnlyFallbackWhenThermalHidden` is enabled, still apply thermalling zoom behavior.

### 4.5 Replay Policy (v1)

For initial release:

- Disable thermalling auto mode/zoom while replay is active.
- Keep replay behavior unchanged and deterministic.

## 5) Settings Model (Draft)

All settings are user-configurable from a dedicated Thermalling settings screen (same category level as Hotspots).

| Key | Type | Default | Range / Values | Meaning |
|---|---|---|---|---|
| `enabled` | Boolean | `false` | `true/false` | Master toggle for thermalling automation. |
| `switchToThermalMode` | Boolean | `true` | `true/false` | Auto-switch flight mode to `THERMAL` on enter. |
| `zoomOnlyFallbackWhenThermalHidden` | Boolean | `true` | `true/false` | Apply thermalling zoom even if Thermal mode is unavailable. |
| `enterDelaySeconds` | Int | `5` | `0..30` | Continuous circling time required before activation. |
| `exitDelaySeconds` | Int | `8` | `0..30` | Continuous non-circling time required before restore. |
| `applyZoomOnEnter` | Boolean | `true` | `true/false` | Apply thermalling zoom when entering active thermalling state. |
| `thermalZoomLevel` | Float | `13.0` | map zoom clamp (`7.0..20.0`) | Target zoom used on thermal enter. |
| `rememberManualThermalZoomInSession` | Boolean | `true` | `true/false` | Keep pilot-adjusted zoom during active thermal session. |
| `restorePreviousModeOnExit` | Boolean | `true` | `true/false` | Restore pre-thermal mode after exit delay. |
| `restorePreviousZoomOnExit` | Boolean | `true` | `true/false` | Restore pre-thermal zoom after exit delay. |

## 6) State Machine (Draft)

States:

- `IDLE`
- `ENTER_PENDING`
- `ACTIVE`
- `EXIT_PENDING`

Transitions:

- `IDLE -> ENTER_PENDING` when `isCircling` becomes true.
- `ENTER_PENDING -> ACTIVE` when circling duration >= `enterDelaySeconds`.
- `ENTER_PENDING -> IDLE` when circling breaks before threshold.
- `ACTIVE -> EXIT_PENDING` when `isCircling` becomes false.
- `EXIT_PENDING -> IDLE` when non-circling duration >= `exitDelaySeconds` (restore actions applied).
- `EXIT_PENDING -> ACTIVE` when circling resumes before threshold.

## 7) Non-Functional Requirements

- Must preserve MVVM/UDF/SSOT boundaries.
- No business logic in Composables.
- Use explicit time source for timers in logic classes.
- No hidden global mutable state.
- No regressions for manual mode switching.
- No replay behavior change in v1.

## 8) Acceptance Criteria

1. With feature enabled and enter delay `2s`, sustained circling for >=2s switches to Thermal mode and thermal zoom.
2. With exit delay `10s`, brief straight segments <10s do not restore mode/zoom.
3. On thermal exit >= delay, previous mode and zoom restore as configured.
4. If Thermal mode unavailable, zoom-only fallback works when enabled.
5. Manual mode switching still works with feature disabled.
6. Replay sessions do not auto-switch/auto-zoom in v1.
