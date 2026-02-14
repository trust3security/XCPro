# System design (XC Pro compliant)

## Architectural constraint recap (why design matters)
XC Pro enforces MVVM + UDF + SSOT, DI via Hilt, and strict boundaries:
- UI -> domain/usecases -> data
- No ViewModel persistence/network
- No MapLibre types outside UI/runtime controllers

## Proposed component graph

UI (Compose / Map runtime)
  -> WeatherOverlayViewModel (UI state + intents)
     -> WeatherOverlayUseCase (pure logic + orchestration)
        -> WeatherOverlayRepository (SSOT for overlay configuration + last known data)
           -> WeatherProviderPort (domain interface)
              -> SkySightProviderAdapter (data implementation; Retrofit/OkHttp, etc)

Parallel:
- WeatherAuthRepository (SSOT for auth/session; secure storage)
- WeatherOverlayPreferencesRepository (SSOT for persisted user settings)

## SSOT ownership table

| Data item | SSOT owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| overlay enabled | Preferences repository | StateFlow<Boolean> | ViewModel fields, Compose remember |
| selected parameter | Preferences repository | StateFlow<WeatherParameterId> | duplicated enum in UI |
| selected forecast time | Preferences repository | StateFlow<ForecastTime> | separate time in overlay controller |
| opacity | Preferences repository | StateFlow<Float> | local UI slider state without syncing |
| auth session/token | Auth repository | StateFlow<AuthState> | storing token in ViewModel |
| current tile template | Overlay repository | StateFlow<TileTemplateState> | UI caching URL strings |

## Where MapLibre code lives
Create a UI/runtime controller (e.g., `ForecastRasterOverlayController`) that:
- Accepts a simple, MapLibre-free config data class: tileUrlTemplate, opacity, zOrder, etc.
- Applies/removes sources/layers to the MapLibre style.
- Reacts to style reload events via `MapOverlayManager` (same pattern as other overlays).

The ViewModel never touches MapLibre.

## Time base declaration (must be explicit)
- ForecastTime: user-selected wall-time (UTC or explicit zone). Stored as an absolute instant (epoch ms) + original zone if needed.
- If replay-sync is enabled (optional M4), forecast time is derived from replay clock timestamps (still sent as an absolute instant to the API; do not subtract/compare against wall time).

## Error handling model
Errors are data. Repositories expose failures via domain models:
- AuthState: LoggedOut / LoggingIn / LoggedIn / Error(reason)
- OverlayDataState: Idle / Loading / Ready(tileTemplate) / Error(reason)

UI renders error states (snackbar / inline card), but never decides semantics.

