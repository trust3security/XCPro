# RainViewer Permission Note

Date recorded: 2026-02-20

Permission source/contact:
- User-provided project approval in XCPro planning session.
- Provider artifact tracker:
  - `docs/RAINVIEWER/evidence/RAINVIEWER_PROVIDER_PERMISSION_ARTIFACT.md`
  - Current status: documented project-level approval record.

Allowed scope:
- Personal XCPro usage for rain radar overlays.

Restrictions and compliance reminders:
- Keep direct tile access only (no proxy mirroring).
- Respect public API limits and stability constraints.
- Keep visible source attribution with clickable source link.
- Revalidate scope before enabling any commercial/public redistribution flow.

Runtime attribution implementation:
- Map raster attribution payload:
  - `feature/map/src/main/java/com/example/xcpro/map/WeatherRainOverlay.kt`
  - value source: `feature/map/src/main/java/com/example/xcpro/weather/rain/WeatherRainAttribution.kt`
- In-app fallback clickable source link:
  - `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/WeatherSettingsScreen.kt`
  - URL constant: `https://www.rainviewer.com`

Manual verification checklist (release gate):
- [ ] Rain overlay enabled on map: attribution text is visible on map chrome.
- [ ] Attribution link is reachable:
  - via map attribution control, or
  - via Weather settings "Open radar source link" action.
- [ ] Link target opens provider source page at `https://www.rainviewer.com`.
- [ ] Attribution remains visible/reachable after map style change and overlay reapply.
