# Scope and milestones

## Primary user value
Show SkySight forecast layers on top of XC Pro maps so pilots can plan and (optionally) analyze flights with forecast context.

## In-scope (MVP)
M0 - Account linking + authentication
- UI to enter credentials (or a token) and verify login.
- Secure token/session storage owned by a repository.

M1 - Map overlay rendering (raster tiles)
- Toggle forecast overlay on/off.
- Parameter selection (e.g., thermal strength, cloudbase, winds, etc. - whatever the API exposes).
- Time-of-day selection (forecast timestamp) with a slider or stepper.
- Opacity control (0..1), matching SkySight's "colour transparency" concept.

M2 - Point query ("what is the value here?")
- Long-press on the map triggers a point query for the active parameter/time.
- Display a small sheet/callout with the numeric value + units + "valid time".

M3 - Basic legend support
- Display a legend/scale for the current parameter (if API provides it).
- Units toggle (metric/imperial) if supported.

## Optional follow-ons (not required for MVP)
M4 - Replay/time sync mode
- When IGC replay is running, optionally sync the forecast time to replay time (useful for analysis).

M5 - Route forecast / cross sections / skew-T / windgrams
- These are large features (charts, route editing, server-side compute). Only implement if the API explicitly supports them and the product wants it.

## Explicit non-goals (for initial integration)
- Re-implementing the full SkySight web UI.
- Scraping the web app or reverse engineering private endpoints without permission.
- Offline bulk download of tiles/forecasts (unless SkySight explicitly allows it).

