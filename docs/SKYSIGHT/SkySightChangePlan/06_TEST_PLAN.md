# Test plan

## Unit tests (required)
- WeatherOverlayUseCase:
  - selecting parameter updates downstream request state
  - time selection boundaries (no wall clock calls; use injected Clock)
  - replay-sync mapping (if implemented)

- WeatherAuthRepository:
  - token save/load
  - expiry handling
  - logout clears token

- WeatherProviderAdapter (data layer):
  - Retrofit request construction (headers present)
  - error mapping to domain error types
  - never logs secrets (can be checked by ensuring no logging interceptor in release)

Use MockWebServer to simulate API responses.

## ViewModel tests (required)
- Intent -> UI state transitions for:
  - login flow success/fail
  - overlay enable + parameter change
  - point query success/fail

## Integration tests (nice-to-have)
- End-to-end happy path with MockWebServer and the Map overlay controller in a JVM-friendly way (if possible).
- If MapLibre makes it hard, keep integration tests focused on repository + use-case + ViewModel.

## Determinism gates
Even though this is a network feature, ensure:
- No wall-time calls in domain/usecases without injected Clock.
- Replay mode behavior is stable for identical replay input when replay-sync is enabled.

## Manual QA checklist (device)
- Login works, logout works.
- Overlay toggles on/off.
- Parameter change updates imagery.
- Time slider updates imagery.
- Opacity adjusts smoothly.
- Style reload (changing base style / re-entering map screen) re-applies overlay.
- Long-press shows correct value and updates with time changes.
- Offline/no-network shows a clear error state and does not crash.

