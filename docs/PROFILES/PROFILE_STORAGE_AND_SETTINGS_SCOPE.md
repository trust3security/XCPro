# PROFILE STORAGE AND SETTINGS SCOPE

## Profile Storage (Authoritative)

Profile list and active profile id are stored in DataStore:

- DataStore name: `profile_preferences`
- Keys:
  - `profiles_json`
  - `active_profile_id`

File:

- `feature/profile/src/main/java/com/example/xcpro/profiles/ProfileStorage.kt`

## User-Facing Portable File Policy

- XCPro should present a portable profile export as an "Aircraft Profile" file.
- That file is a portability artifact for backup/import/export.
- It is not runtime SSOT.
- Runtime SSOT remains in the internal repositories and stores listed in this
  document.
- Switching profiles in-app should switch internal profile identity and hydrate
  runtime stores; it should not depend on reading external JSON files on each
  switch.

Reference:

- `docs/PROFILES/PROFILE_FILE_PORTABILITY_STRATEGY_2026-03-10.md`

## SSOT Ownership Matrix (Authoritative)

This matrix is the contract for profile-related state ownership.

| Data | Authoritative Owner | Scoped By Profile ID | Bundle Section |
|---|---|---|---|
| Profile list, profile identity metadata, active profile id | `ProfileStorage` + `ProfileRepository` | n/a (list + active id) | Bundle document root (`profiles`, `activeProfileId`) |
| Card templates/cards/positions/mode visibility | `CardPreferences` | Yes | `tier_a.card_preferences` |
| Flight mgmt last mode | `FlightMgmtPreferencesRepository` | Yes | `tier_a.flight_mgmt_preferences` |
| Look and feel + status bar/card style | `LookAndFeelPreferences` | Yes | `tier_a.look_and_feel_preferences` |
| Theme id + custom colors | `ThemePreferencesRepository` | Yes | `tier_a.theme_preferences` |
| Map widget offsets/sizes | `MapWidgetLayoutRepository` | Yes | `tier_a.map_widget_layout` |
| Variometer offset/size | `VariometerWidgetRepository` | Yes | `tier_a.variometer_widget_layout` |
| Glider model/config/polar inputs | `GliderRepository` | Yes | `tier_a.glider_config` |
| Units | `UnitsRepository` | Yes | `tier_a.units_preferences` |
| Levo vario preferences | `LevoVarioPreferencesRepository` | No (global) | `tier_a.levo_vario_preferences` |
| Thermalling mode prefs | `ThermallingModePreferencesRepository` | No (global) | `tier_a.thermalling_mode_preferences` |
| OGN traffic prefs | `OgnTrafficPreferencesRepository` | No (global) | `tier_a.ogn_traffic_preferences` |
| OGN trail selection | `OgnTrailSelectionPreferencesRepository` | No (global) | `tier_a.ogn_trail_selection_preferences` |
| ADS-B prefs | `AdsbTrafficPreferencesRepository` | No (global) | `tier_a.adsb_traffic_preferences` |
| Weather overlay prefs | `WeatherOverlayPreferencesRepository` | No (global) | `tier_a.weather_overlay_preferences` |
| Forecast prefs | `ForecastPreferencesRepository` | No (global) | `tier_a.forecast_preferences` |
| Manual wind override | `WindOverrideRepository` | No (global) | `tier_a.wind_override_preferences` |

## UserProfile Field Classification

`UserProfile` is identity metadata for selection/import/export behavior.
Runtime settings authority is in dedicated settings repositories listed above.

Identity metadata fields:

- `id`
- `name`
- `aircraftType`
- `aircraftModel`
- `description`
- `createdAt`
- `lastUsed` (selection metadata only)

Non-authoritative compatibility fields (must not be treated as runtime SSOT):

- `preferences`
- `polar`
- `isActive` (active profile authority is `active_profile_id` in `ProfileStorage`)

Runtime edit policy:

- Profile edit UI only mutates identity metadata.
- Repository update path preserves compatibility fields (`preferences`, `polar`, `isActive`) and does not treat them as runtime settings authority.

## Why Settings Can Look "Lost"

Many UI and map settings are namespaced by `profileId` using keys like
`profile_<profileId>_...` in SharedPreferences/DataStore repositories.

Examples:

- `feature/map/.../ThemePreferencesRepository.kt`
- `feature/map/.../LookAndFeelPreferences.kt`
- `feature/map/.../FlightMgmtPreferencesRepository.kt`

If app startup lands on a different profile id (for example after creating a
new profile), those repositories read a different settings namespace.
The old settings still exist but under the previous profile id.

## Current Mitigation

- Empty bootstrap now provisions one stable default profile (`default-profile`).
- Active profile repair is deterministic at bootstrap.
- Bootstrap and profile mutations are serialized.
- Profile snapshot is mirrored to a public backup folder for user copy/USB flows:
  `Download/XCPro/profiles/`.
- Backup layout:
  - Per-profile files: `profile_<sanitized-id>_<hash>.json`
  - Active/index metadata: `profiles_index.json`

This reduces accidental profile-id churn during startup and keeps settings attached
to a stable profile identity by default.

## Recommended Portable File Shape

Recommended product policy:

- single-profile export/import is the primary user flow
- multi-profile export/import is a secondary migration flow
- both should use the same versioned JSON schema
- a single-profile export is just a versioned bundle containing one profile

This keeps one portable file per aircraft setup while avoiding a separate
runtime-vs-export schema split.

## Import Scope and Strict Restore Policy

Bundle import supports explicit scope:

- `PROFILES_ONLY`: import profile identities only, skip settings restore.
- `PROFILE_SCOPED_SETTINGS`: import profiles plus profile-scoped settings sections only.
- `FULL_BUNDLE`: import profiles plus all bundle sections.

Strict mode:

- Optional strict restore fails bundle import result when any selected settings section fails to apply.
- Non-strict restore keeps best-effort section apply behavior and reports failed section IDs.

## Bundle and Schema Compatibility

Supported import versions:

- Bundle document: `1.x`, `2.x` (`1.x` accepted as compatibility migration input).
- Legacy profile export: `1.x`.
- Backup profile document: `1.x`.
- Settings snapshot schema: `1.x`.

Unsupported major versions are rejected with actionable errors.

## App Identity Note

Changing app package identity (`applicationId` or debug suffix) creates a different
Android sandbox and appears as empty profile storage.

Reference:

- `app/build.gradle.kts`
- `docs/ARCHITECTURE/README.md` (Profile sandbox contract)
