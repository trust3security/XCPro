# UI and MapLibre integration

## UX: what the pilot sees (MVP)
- A "Forecast overlay" entry in the map overlay drawer/stack.
- Toggle: on/off
- Dropdown/list: parameter
- Time control: slider or stepper for local time (bounded by API availability)
- Opacity slider
- Optional legend panel for current parameter
- Long-press on map: show value at that point (if supported)

## Map layer implementation (raster tiles)
Create a UI/runtime overlay component that:
- On enable: adds a RasterSource + RasterLayer to the MapLibre style.
- On disable: removes layer + source.
- On parameter/time change: updates the source tile URL and triggers a refresh (remove + re-add is acceptable if MapLibre cannot update in place).
- On opacity change: updates RasterLayer rasterOpacity.

Naming:
- Keep names generic in production: "Forecast", "Weather overlay".
- Avoid vendor names in UI strings.

## Layer ordering
Decide a stable z-order relative to:
- base map
- terrain/contours
- airspace
- task overlays
- traffic overlays

Typical: forecast raster under airspace boundaries and under task lines, so those remain readable.

## Cross-overlay readability (SkySight satellite <-> OGN traffic)

When SkySight satellite overlays are active, map readability for OGN glider icons degrades on
dark/complex backgrounds. Current runtime contract adds a map-only coupling:

- Source of truth for this coupling: `MapOverlayManager` runtime state.
- Condition:
  - `satellite overlay enabled` AND
  - at least one satellite layer active (`imagery` OR `radar` OR `lightning`).
- Effect:
  - OGN glider icon id resolves to a white-contrast variant.
- Non-glider OGN icons are unchanged.

Important boundary:
- This is a UI/runtime rendering concern only.
- No domain/repository/business policy is added.
- No ViewModel dependency on MapLibre or render internals.

Refresh policy:
- One-shot immediate refresh on contrast mode transition.
- Existing markers are refreshed once when satellite contrast mode changes.
- After transition refresh, normal OGN update cadence resumes.

## Gesture: point query
Implement long-press (2s on mobile) to match SkySight behavior:
- Convert screen point to lat/lon.
- Call ViewModel intent: `onForecastPointSelected(lat, lon)`.
- ViewModel calls a use-case that triggers a point query.
- UI displays results in a small sheet/callout.

Do NOT store point selection state in multiple places:
- If you need "current selected point", make it part of ViewModel UI state.

## Replay integration (optional)
Add setting: "Sync forecast time to replay time"
- When enabled and replay is active, derive ForecastTime from replay clock.
- When disabled, preserve manual time selection.
