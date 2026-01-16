# XCSoar Snail Trail - Investigation and Implementation Notes for XCPro

This document is based on XCSoar source at:
C:\Users\Asus\AndroidStudioProjects\XCSoar

It documents where the "trail length" UI lives and how the snail trail is
recorded, filtered, and rendered so the same behavior can be implemented
in XCPro.

---

## A) Direct answer: trail length UI + mapping

Trail length settings in the map display panel:
- UI list and labels: `src/Dialogs/Settings/Panels/SymbolsConfigPanel.cpp:71-100`
- UI wiring (AddEnum/AddBoolean): `src/Dialogs/Settings/Panels/SymbolsConfigPanel.cpp:144-165`
- When length is OFF, drift/type/width rows are hidden:
  `src/Dialogs/Settings/Panels/SymbolsConfigPanel.cpp:44-62`

Trail length mapping to time window:
- `src/MapWindow/GlueMapWindowOverlays.cpp:381-401`
  - LONG -> now - 1 hour (`std::chrono::hours{1}`)
  - SHORT -> now - 10 minutes (`std::chrono::minutes{10}`)
  - FULL -> no limit (`TimeStamp{}`)
  - OFF -> no rendering (early return)

Input event toggle for trail length:
- `src/Input/InputEventsSettings.cpp:43-92`
  - supports toggle, off, long, short, full, show

---

## B) Trail settings model and defaults

Trail settings definition:
- `src/MapSettings.hpp:71-95`
  - `wind_drift_enabled`
  - `scaling_enabled`
  - `type` enum
  - `length` enum

Defaults:
- `src/MapSettings.cpp:14-19`
  - wind_drift_enabled = true
  - scaling_enabled = true
  - type = VARIO_1
  - length = LONG

Persistence keys:
- `src/Profile/MapProfile.cpp:120-155` (Load for TrailSettings)
- `src/Profile/Keys.hpp` defines keys: SnailTrail, SnailType, SnailWidthScale, TrailDrift

---

## C) Trace capture (when points are recorded)

TraceComputer only records points if all of this is true:
- time available
- location available
- Nav altitude available
- calculated.flight.flying == true

Source:
- `src/Computer/TraceComputer.cpp:52-60`

TracePoint fields used by the trail renderer:
- location
- time (seconds)
- nav altitude (`basic.nav_altitude`)
- netto vario (`basic.netto_vario`)
- drift_factor (see drift section)

Source:
- `src/Engine/Trace/Point.cpp:12-17`
- `src/Engine/Trace/Point.hpp:20-117`

---

## D) Trace storage and thinning

Full trace store sizing:
- `full_trace_size = 1024`
- `full_trace_no_thin_time = 2 minutes`

Source:
- `src/Computer/TraceComputer.cpp:9-18`

Sampling rules:
- Minimum spacing between points is 2 seconds.
- If time goes backwards by more than 3 minutes, the trace is cleared.
- If time goes backwards by less than 3 minutes, points later than
  (time - 10 seconds) are removed.

Source:
- `src/Engine/Trace/Trace.cpp:196-223`

Thinning rules:
- When size reaches max_size, the trace is thinned to opt_size
  (opt_size = 3/4 of max_size).
- Thinning uses a Douglas-Peucker style metric, ranking points by
  distance error, then time error, then age.
- Points within the last no_thin_time window are protected first.

Source:
- `src/Engine/Trace/Trace.hpp:49-154`
- `src/Engine/Trace/Trace.cpp:17,306-334`

Filtered extraction for rendering:
- `Trace::GetPoints` supports min_time and min_distance filtering.

Source:
- `src/Engine/Trace/Trace.cpp:415-443`

---

## E) Rendering pipeline (TrailRenderer)

Spatial filtering at render time:
- `TrailRenderer::LoadTrace` requests a filtered trace with a min
  separation of 3 screen pixels.

Source:
- `src/Renderer/TrailRenderer.cpp:28-35`

Bounds culling:
- Points outside `projection.GetScreenBounds().Scale(4)` are skipped
  and line continuity is broken.

Source:
- `src/Renderer/TrailRenderer.cpp:123-139`

Width scaling on zoom:
- Scaled widths are used only when map scale <= 6000.

Source:
- `src/Renderer/TrailRenderer.cpp:120-121`

Final segment:
- The renderer draws a final line from the last trace point to the
  current aircraft position.

Source:
- `src/Renderer/TrailRenderer.cpp:180`

---

## F) Color ramps and widths

Color steps:
- `TrailLook::NUMSNAILCOLORS = 15`

Source:
- `src/Look/TrailLook.hpp:9`

Color ramps:
- Vario #1: 0=C4801E, 100=A0A0A0, 200=1EF173
- Vario #2: 0=0000FF, 99=00FFFF, 100=FFFF00, 200=FF0000
- Vario E-ink: 0=000000, 200=808080
- Altitude: 0=FF0000, 50=FFFF00, 100=00FF00, 150=00FFFF, 200=0000FF

Source:
- `src/Look/TrailLook.cpp:13-60`

Width scaling:
- Base width is `ScalePenWidth(2)`.
- If scaling_enabled, climb-side widths increase up to
  `ScalePenWidth(16)` as color index increases.

Source:
- `src/Look/TrailLook.cpp:88-110`

---

## G) Vario and altitude ranges

Vario min/max defaults and clamps:
- Default min/max: -2.0 / 0.75
- Clamp: min >= -5.0, max <= 7.5

Source:
- `src/Renderer/TrailRenderer.cpp:76-85`

Altitude min/max defaults:
- Default min/max: 500 / 1000 (meters)
- Then expanded by actual trace min/max

Source:
- `src/Renderer/TrailRenderer.cpp:68-74`

---

## H) Dot vs line behavior (trail types)

Rules in `TrailRenderer::Draw`:
- ALTITUDE: line segments only
- VARIO_1 / VARIO_2: line segments only
- VARIO_1_DOTS / VARIO_2_DOTS:
  - sink (vario < 0): dots only
  - climb (vario >= 0): lines
- VARIO_DOTS_AND_LINES / VARIO_EINK:
  - sink: dots only
  - climb: dots + lines

Dots are drawn at the midpoint between consecutive points.

Source:
- `src/Renderer/TrailRenderer.cpp:140-170`

---

## I) Wind drift logic

Enablement:
- Enabled only when trail drift is ON and the aircraft is circling.
- Disabled if location or wind are not available.

Source:
- `src/MapWindow/GlueMapWindowOverlays.cpp:401`
- `src/Renderer/TrailRenderer.cpp:100-114`

Drift vector:
- `traildrift = basic.location - FindLatitudeLongitude(basic.location,
  wind.bearing, wind.norm)`
- wind.norm is used as the distance parameter, effectively a 1-second
  drift vector when wind is in m/s.

Source:
- `src/Renderer/TrailRenderer.cpp:108-113`

Per-point drift:
- Each point uses `CalculateDrift(now)` which multiplies elapsed
  seconds by `(drift_factor / 256)`.

Source:
- `src/Engine/Trace/Point.hpp:113-119`

Drift factor:
- `drift_factor = Sigmoid(altitude / 100) * 256`

Source:
- `src/Engine/Trace/Point.cpp:17`
- `src/Math/Util.hpp:70-74`

---

## J) Implementation checklist for XCPro

Data inputs:
- GPS location (lat/lon)
- UTC time (seconds)
- Nav altitude (baro if configured, else GPS)
- Netto vario (or equivalent air mass vertical speed)
- Wind speed + direction
- Flying flag
- Circling flag
- Map scale (for width scaling and distance filter)

Trace store:
- Max points: 1024
- Min time spacing: 2 seconds
- Protect latest 2 minutes from thinning
- Thinning using distance + time metric (Douglas-Peucker style)
- Time warp handling (clear if >3 minutes backward)

Rendering:
- Trail length: Short=10 min, Long=60 min, Full=no limit
- XCPro addition: Medium=30 min (XCSoar does not ship a medium preset)
- Downsample by 3px minimum distance
- Cull outside screen bounds scaled by 4x
- Draw last segment from last point to aircraft position

Colors and width:
- 15 discrete color steps
- Same ramps as XCSoar
- Vario min/max defaults and clamps
- Altitude min/max defaults then expand
- Width scaling only when map scale <= 6000

Dots and lines:
- Match per-type rules from `TrailRenderer::Draw`
- Draw dots at segment midpoints

Drift:
- Only in circling mode when wind+location available
- Use Sigmoid-based drift factor per point
- Apply parametric drift based on elapsed seconds

---

If you want, I can now:
- map these requirements directly to XCPro classes,
- draft Kotlin data models and a MapLibre trail layer,
- or add the settings UI and persistence in XCPro.
