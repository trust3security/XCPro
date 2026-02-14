# Data model and storage

## Domain models (provider-neutral)
Keep these in domain/model (no Android types):

- WeatherParameterId (opaque string)
- WeatherParameterMeta
  - id, displayNameKey (NOT vendor string), unit, category, supportsPointQuery, etc
- ForecastTime (epochMsUtc)
- TileTemplate
  - urlTemplate (string), minZoom, maxZoom, tileSizePx, attribution
- Legend
  - unit, stops: List<LegendStop(value, colorHex, label)>
- PointValue
  - value, unit, validTime

## Preferences (SSOT)
Create a preferences repository that owns:
- overlayEnabled: Boolean
- selectedParameterId: String?
- selectedForecastTime: Long? (epochMsUtc)
- opacity: Float (0..1)
- units: enum Metric/Imperial (if supported)

Expose all as StateFlow.

Storage choice:
- If the app already uses DataStore, prefer it.
- Otherwise use SharedPreferences wrapper owned by the repository.

## Auth storage (SSOT)
Auth repository owns:
- token/session
- expiry
- lastLoginError

Storage choice:
- EncryptedSharedPreferences (AndroidX Security) for tokens, OR
- DataStore + encryption (if already present)

## Caching policy
Safe baseline:
- Cache only metadata (parameter list, legends, tile templates) with short TTL.
- Do NOT cache bulk tiles unless SkySight explicitly permits it.
- Use OkHttp cache for HTTP caching if the server provides Cache-Control headers.

Make caching policy configurable in data layer (so it can be tightened without UI changes).

