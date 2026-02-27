# System design (XCPro compliant)

## Architectural constraints
- MVVM + UDF + SSOT.
- Dependency flow: UI -> domain/use-cases -> data.
- MapLibre runtime types stay in map runtime controllers/managers.
- ViewModels do not perform direct network/persistence I/O.

## Implemented component graph

UI (MapScreen, SkySight tab)
  -> `ForecastOverlayViewModel`
    -> forecast use-cases (`ForecastOverlayUseCases.kt`)
      -> `ForecastOverlayRepository` (overlay UI state composition, query state)
      -> `ForecastPreferencesRepository` (persistent SSOT for settings)
      -> SkySight ports via `SkySightForecastProviderAdapter`

Map runtime path:
  -> `MapScreenContent`
    -> `MapOverlayManager`
      -> `ForecastRasterOverlay` (forecast + wind layers)
      -> `SkySightSatelliteOverlay` (imagery/radar/lightning layers)
      -> `WeatherRainOverlay` (independent rain stack)

## SSOT ownership

1) Forecast + SkySight settings SSOT:
- `ForecastPreferencesRepository`
- Includes:
  - forecast enable/time/region/selected parameters,
  - wind overlay and display settings,
  - SkySight satellite settings:
    - enabled
    - imagery/radar/lightning toggles
    - animate toggle
    - history frames

2) Overlay derived runtime state SSOT:
- `ForecastOverlayRepository`
- Exposes `ForecastOverlayUiState` to ViewModel/UI.

3) Runtime map object ownership:
- `MapScreenState` + `MapOverlayManager`
- No ViewModel ownership of MapLibre runtime objects.

## Time semantics
- Forecast time and satellite reference time are driven from selected forecast time (`selectedTimeUtcMs`) and auto-time behavior.
- Satellite frame stepping uses 10-minute buckets and bounded history frame count.

## Error handling
- Overlay repository exposes warning/fatal states into UI state.
- Map runtime overlay apply failures are logged and do not crash UI state pipeline.
