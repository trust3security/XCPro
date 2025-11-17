# Engineering Units Contract (XCPro)

This file is the single source of truth describing which **SI base units** every calculation layer must use. Anything outside the UI or persistence boundary *must* stick to these units. When in doubt, convert into these values as data enters XCPro, keep them untouched through all math, and only format/convert when rendering or accepting user input.

## Sensor & Domain Models

| Field / Concept | Canonical Type | Base Unit | Source Files |
|-----------------|----------------|-----------|--------------|
| GPS latitude / longitude | `Double` | degrees (WGS84) | `feature/map/src/main/java/com/example/xcpro/sensors/SensorData.kt` |
| GPS altitude (`GPSData.altitude`, `RealTimeFlightData.gpsAltitude`) | `Double` | meters (MSL) | same |
| Barometric altitude (`CompleteFlightData.baroAltitude`, `RealTimeFlightData.baroAltitude`) | `Double` | meters | same |
| AGL (`CompleteFlightData.agl`, `RealTimeFlightData.agl`) | `Double` | meters above local terrain | same |
| Vertical speed (`CompleteFlightData.verticalSpeed`, `RealTimeFlightData.verticalSpeed`, `displayVario`, `vario*`) | `Double` | meters per second | same |
| Ground speed (`GPSData.speed`, `RealTimeFlightData.groundSpeed`) | `Double` | meters per second (convert from knots/kmh before storing) | same |
| True / Indicated airspeed (`CompleteFlightData.trueAirspeed`, `.indicatedAirspeed`, `RealTimeFlightData.trueAirspeed`, `.indicatedAirspeed`) | `Double` | meters per second | `PolarCalculator.kt`, `SensorData.kt` |
| Wind speed (`CompleteFlightData.windSpeed`, `RealTimeFlightData.windSpeed`) | `Float/Double` | meters per second | `SensorData.kt` |
| Wind direction / heading / track | `Double` | degrees [0,360) | `SensorData.kt`, `CompleteFlightData.kt` |
| Thermal averages / netto / brutto | `Float/Double` | meters per second | `CompleteFlightData.kt`, `RealTimeFlightData.kt` |
| Distance / glide ratio inputs (`currentLD`, range computations) | `Float/Double` | meters for distance, unitless for L/D | `CompleteFlightData.kt` |
| Pressure (`BaroData.pressureHPa`, `CompleteFlightData.qnh`, `RealTimeFlightData.currentPressureHPa`) | `Double` | hectopascals | `SensorData.kt`, `MapScreenUtils.kt` |
| Temperature (future fields) | `Double` | Celsius | `UnitsPreferences.kt` (existing converters) |

## Persistence & Configuration

| Artifact | Field | Stored Unit |
|----------|-------|-------------|
| `GliderConfigModels.kt` (pilot weight, ballast, bugs, drain time) | All numeric entries | kg, kg, percent, minutes |
| Polars (WinPilot / Three-point) | Speed, sink | **Input format varies** (km/h & m/s). **Convert to SI (m/s) immediately after parsing**. |
| `UnitsRepository` DataStore | Unit selections only (strings) | N/A (no physical measurements stored here) |
| Task files / replay logs (future) | All physical values | meters, meters/sec, Pascals |

## UI Boundaries

*Formatting:*  
Use `UnitsFormatter` + wrappers (`AltitudeM`, `SpeedMs`, `VerticalSpeedMs`, etc.) to convert SI to the user’s preference when displaying values in cards, overlays, nav drawer, toasts, etc. (`dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`, `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayWidgets.kt`, `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/UnitsSettings.kt`).

*Parsing / Input:*  
Whenever the UI accepts values in user-selected units (e.g., QNH in inHg, altitude in feet, ballast volumes), convert to SI immediately using `UnitsPreferences.*.toSi(...)` before persisting or passing downstream (`feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt`, `feature/map/src/main/java/com/example/xcpro/screens/navdrawer/PolarCards.kt`).

## Requirements for New Code

1. **SI-only calculations.** Every new domain calculator, repository, or data class must accept and emit SI units even if a sensor or input widget provides something else.
2. **Conversions at the boundary.** The only permitted conversion sites are:
   - Data ingestion (sensor/device/NMEA → SI)
   - User input parsing (UI form → SI)
   - Rendering (SI → user-selected format via `UnitsFormatter`)
3. **Tests for contracts.** Whenever a new measurement type is introduced, add:
   - A table entry above
   - Unit tests that fail if conversions are skipped
4. **Documentation updates.** Keep this file in sync with any new fields or modules touching physical quantities.

Following this contract ensures XCPro mirrors XCSoar’s proven flow: *normalize once, compute everywhere in SI, format on the way out*. That keeps math stable, prevents double conversions, and makes unit preferences purely a presentation concern.
