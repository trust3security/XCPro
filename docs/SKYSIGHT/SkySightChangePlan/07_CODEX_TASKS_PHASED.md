# Codex task breakdown (Phase 0..4)

This file is written to match the repo's mandatory phased execution model.

## Phase 0 - Baseline
Goal: lock current behavior and prepare scaffolding without changing runtime behavior.

Tasks:
- Add plan docs to repo (this folder or equivalent).
- Identify existing overlay architecture entry points:
  - Map overlay stack UI
  - Map overlay manager/style reload handling
  - Existing traffic overlays (ADS-B/OGN) as reference
- Add a minimal regression test placeholder (even if it only asserts that the new repositories default to "disabled" and do nothing).

Gate:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

## Phase 1 - Pure logic implementation
Goal: implement provider-neutral domain models + use-cases with fakes.

Tasks:
- Create domain models (WeatherParameterId, ForecastTime, etc).
- Define a domain port interface: WeatherProviderPort
  - listParameters()
  - getTileTemplate(parameter, time)
  - getLegend(parameter)
  - getPointValue(parameter, time, lat, lon)
- Implement WeatherOverlayUseCase that:
  - combines preferences flows into a single "OverlayRequest" model
  - requests tile template when enabled and inputs are valid
  - exposes OverlayDataState as Flow/StateFlow
- Unit test the use-case with a fake provider port.

Gate:
- deterministic unit tests pass (no Android imports)

## Phase 2 - Repository / SSOT wiring
Goal: wire SSOT repositories and the data adapter, but keep UI changes minimal.

Tasks:
- Implement WeatherOverlayPreferencesRepository (DataStore or SharedPreferences wrapper).
- Implement WeatherAuthRepository with secure storage.
- Implement WeatherProviderAdapter (SkySight) in data layer:
  - Retrofit service for /api/auth and forecast endpoints
  - OkHttp config for headers and caching
- Bind everything via Hilt modules (no manual construction).

Gate:
- no duplicated state; repositories expose Flow/StateFlow only

## Phase 3 - ViewModel + UI wiring
Goal: make it work in the app.

Tasks:
- Add WeatherOverlayViewModel:
  - consumes stable domain-facing seams only
  - use cases or focused owner/port seams are valid
  - exposes WeatherOverlayUiState (enabled, parameter list, selected time, opacity, legend, point value, error states)
- Update MapScreenRoot / MapOverlayStack to include the forecast overlay runtime controller.
- Implement ForecastRasterOverlayController (UI/runtime):
  - adds/removes MapLibre raster source/layer
  - updates opacity
  - responds to style reload via MapOverlayManager
- Add UI controls (toggle, parameter picker, time slider, opacity slider).
- Add long-press handler + point query sheet.

Gate:
- end-to-end works in debug on device

## Phase 4 - Hardening
Goal: production quality and rule compliance.

Tasks:
- Threading: ensure network is off Main.
- Ensure no secrets are logged.
- Add rate limiting / debouncing for time slider changes (avoid spamming network).
- Add clear error UI states.
- Add documentation updates:
  - Update PIPELINE.md if the map overlay flow changes
  - Update ARCHITECTURE.md only if rules evolve (avoid exceptions)
- Run required checks and report results.

Gate:
- all required checks pass; no architecture drift

