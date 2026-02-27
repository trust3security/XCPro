# Scope and milestones

## Primary user value
Show SkySight forecast and satellite-derived weather context directly on XCPro maps while preserving map readability and runtime stability.

## Status snapshot (2026-02-24)
Completed in code:
- Forecast overlay provider integration (Stage A + Stage B).
- Multi-overlay behavior for forecast/wind.
- Convergence parameter support.
- SkySight satellite API overlay runtime (imagery/radar/lightning) with SkySight-tab controls.

## In scope (current)
M1 - Forecast overlays
- Enable/disable forecast overlays.
- Select non-wind parameter.
- Select wind parameter + wind display behavior.
- Time selection and auto-time follow.

M2 - Forecast value query
- Long-press map to query selected forecast parameter value.
- Show callout/status.

M3 - Forecast legends and warnings
- Show legends for active overlays.
- Show warning/error state with non-fatal vs fatal distinction.

M4 - SkySight satellite overlays
- SkySight-tab options:
  - satellite overlay master toggle
  - imagery (clouds) toggle
  - radar toggle
  - lightning toggle
  - animation toggle
  - history frames (1-3)
- Keep `Sat View` map-style toggle as separate behavior.

## Explicit non-goals (for this track)
- Re-implement full SkySight web UI.
- Add unsupported backend proxy/gateway by default.
- Add bulk offline tile download behavior without explicit provider permission.
