# XCPro V1 / HAWK Integration Notes

## Core Platform
- XCPro V1 lives under `app/src/main/java/com/example/xcpro/xcprov1`.
- Three main layers:
  - `filters/` – JS1-C aerodynamic EKF, wind model, state definitions.
  - `bluetooth/` – Garmin GLO 2 SPP/NMEA connector + parser.
  - `audio/` – XCPro V1 audio engine (anticipatory tones, sink warning).
  - `ui/` + `viewmodel/` – HAWK dashboard Compose screen + state.
  - `service/` – `XcproV1Controller` wiring sensors, EKF, audio, GPS fusion.

## Sensor & Controller Integration
- `LocationManager` instantiates `XcproV1Controller`, Garmin GLO manager, and attaches the external GPS flow.
- `ServiceLocator.locationManager` exposes the live instance outside the map.
- Controller fuses handset and optional Garmin GPS frames, publishes `FlightDataV1Snapshot`, and drives the XCPro V1 audio engine (toggle via controller).

## UI Entry Points
- **Dedicated dashboard route**: `app/src/main/java/com/example/xcpro/navdrawer/DrawerMenuSections.kt` adds “XCPro HAWK” under Settings → General (indent level 2), alongside “Vario Audio”.
- **Map overlay**: `MapScreenSections.kt` renders a compact HAWK gauge/wind ribbon when flight mode equals the new `FlightMode.HAWK`.
- **Flight Mode enums** updated across the codebase (`FlightMode`, `FlightModeSelection`, `FlightDataManager`, templates) to include the HAWK option.

## Build Verification
- `./gradlew assembleDebug` succeeds with current layout (warnings only about deprecated Material icons).
- Install via `./gradlew installDebug` after uninstalling old debug builds to ensure the new nav drawer entries appear.

## Outstanding UX Work
- Existing `HawkDashboardScreen` currently mirrors a simplified dual-needle UI; further polish will refine the layout to match the real LXNAV HAWK instrument (colors, scale, peripheral data blocks). 

