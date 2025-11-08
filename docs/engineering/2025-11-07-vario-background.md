# 2025-11-07 – Background Vario Service & Polar-Aware Netto

## Summary
- Introduced `VarioForegroundService` + `VarioServiceManager` so the sensor/TE pipeline stays alive (50 Hz vario + audio) when XC Pro is in the background.
- Added a shared `FlightDataRepository` for SSOT `CompleteFlightData` snapshots; UI layers observe this instead of talking to the calculator.
- Wired the pilot’s configured glider polar (water ballast, bugs, reference weight, 3-point fits) into `FlightCalculationHelpers.calculateNetto`.

## Foreground Service Details
- Service entry point: `com.example.xcpro.service.VarioForegroundService`.
  - Starts once on app launch (also safe to start from widgets/background actions).
- Runs as `foregroundServiceType="location"` since we only access location sensors; audio output doesn't require the microphone FGS type (which would demand extra permissions).
  - Hosts `VarioServiceManager`, which owns `UnifiedSensorManager`, `FlightDataCalculator`, and `VarioAudioEngine`.
  - On every service creation (including Android restarts) we call `VarioServiceManager.start()`, ensuring sensors + audio resume immediately even if the UI isn’t active.
- `LocationManager`, `MapScreen`, HUD overlays, and nav-drawer modules observe `FlightDataRepository.flightData` and call manager APIs instead of starting/stopping sensors directly.
- If the user force-stops vario (future UX), call `VarioServiceManager.stop()`; otherwise the service keeps the SSOT flows warm.

## Polar-Aware Netto
- New `StillAirSinkProvider` (`PolarStillAirSinkProvider`) listens to `GliderRepository.selectedModel/config`.
- `FlightCalculationHelpers.calculateNetto` now subtracts sink from the provider; the legacy hard-coded bucket curve is only used when no polar/config exists (logged via `AI-NOTE`).
- `PolarCalculator.sinkMs()` already applies ballast + bugs; no extra math needed downstream.

## Testing & Follow-ups
- TODO: add instrumentation test covering background telemetry continuity.
- TODO: add UX affordance (notification action + nav-drawer copy) so pilots can pause the background service.
