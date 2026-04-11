# LX S100 Bluetooth DF-Card Wiring Plan

## Metadata

- Date: 2026-04-10
- Status: Draft
- Scope: LX S100 live Bluetooth data to DF-cards and Bluetooth settings metadata

## Goal

When an LX S100 is connected to XCPro, parsed LX data should drive the correct DF-cards and parsed device identity should appear in Bluetooth settings.

Target user-visible outcomes:

- `vario` card shows fresh LX TE vario
- `tas` card shows LX airspeed
- `baro_alt` reuses LX pressure altitude when external data is active
- Bluetooth settings show product, serial, software version, and hardware version

## Current Repo Findings

- `feature/variometer/.../LxSentenceParser.kt` already parses:
  - `LXWP0`
  - `LXWP1`
- `feature/variometer/.../LxExternalRuntimeRepository.kt` already stores:
  - `airspeedKph`
  - `pressureAltitudeM`
  - `totalEnergyVarioMps`
  - `deviceInfo`
- `feature/flight-runtime/.../ExternalInstrumentReadPort.kt` currently exposes only:
  - `pressureAltitudeM`
  - `totalEnergyVarioMps`
- `feature/flight-runtime/.../weather/wind/data/ExternalAirspeedRepository.kt` already exists as the live external airspeed SSOT, but LX does not publish into it yet
- `dfcards-library/.../CardFormatSpec.kt` already renders:
  - `vario`
  - `ias`
  - `tas`
  - `baro_alt`
- `feature/profile/.../BluetoothVarioSettingsUseCase.kt` does not yet expose parsed LX metadata to UI

## Scope Decisions

- Treat the single `LXWP0` airspeed field as `TAS only` for this slice
- Reuse the existing `baro_alt` card instead of introducing a new pressure-altitude card
- Keep `ias` blank for LX TAS-only input
- Include Bluetooth settings metadata UI in v1
- Keep replay behavior unchanged

## Ownership

| State | Owner | Notes |
|---|---|---|
| LX Bluetooth session and parsed runtime values | `LxExternalRuntimeRepository` | authoritative LX runtime owner |
| External pressure altitude and TE vario seam | `ExternalInstrumentReadPort` | keep this seam pressure/TE only |
| Live external airspeed sample | `ExternalAirspeedRepository` | canonical live airspeed SSOT |
| Fused flight metrics | `CalculateFlightMetricsRuntime` -> `FlightDataRepository` | no card-side recomputation |
| Bluetooth settings metadata UI state | `LxBluetoothControlUseCase` | UI-safe derived state |

## Planned Wiring

### 1. Pressure altitude and TE vario

- Keep `LxExternalRuntimeRepository` publishing pressure altitude and TE vario through `ExternalInstrumentReadPort`
- Keep `CalculateFlightMetricsRuntime` authoritative for freshness and fallback
- Add explicit downstream state so cards know when pressure altitude is external

### 2. TAS

- Add a small LX-specific publisher near the LX runtime owner
- Publish LX TAS into `ExternalAirspeedRepository`
- Do not create a second airspeed authority
- Keep TAS-only adaptation in flight-runtime, not in the Bluetooth parser

### 3. IAS behavior

- The repo currently tends to fall back to `IAS = TAS` when IAS is missing
- For LX S100 this is not good enough for UI
- Add an explicit `iasDisplayValid` flag through runtime projections
- Result:
  - flight-runtime may still derive internal IAS when needed for calculations
  - `ias` card remains blank for TAS-only LX input

### 4. Bluetooth settings metadata

- Extend control/use-case state with:
  - product
  - serial
  - software version
  - hardware version
- Keep the screen render-only

## Data Mapping

| Sentence | Parsed value | Destination | UI surface |
|---|---|---|---|
| `LXWP0` | `totalEnergyVarioMps` | `ExternalInstrumentReadPort` | `vario` |
| `LXWP0` | `pressureAltitudeM` | `ExternalInstrumentReadPort` | `baro_alt` |
| `LXWP0` | `airspeedKph` | `ExternalAirspeedRepository` | `tas` |
| `LXWP1` | `product/serial/software/hardware` | control/use-case mapping | Bluetooth settings |

Out of scope for this slice:

- `LXWP2`
- `LXWP3`
- `PLXVF`
- `PLXVS`

Reference:

- `docs/BLUETOOTH/02_LX_S100_SENTENCE_CAPABILITIES_2026-04-10.md`

## File Plan

Likely implementation files:

- `feature/variometer/.../LxExternalRuntimeRepository.kt`
- new LX airspeed publisher file in `feature:variometer`
- `feature/flight-runtime/.../weather/wind/model/WindInputs.kt`
- `feature/flight-runtime/.../CalculateFlightMetricsRuntime.kt`
- `feature/flight-runtime/.../FlightMetricsModels.kt`
- `feature/flight-runtime/.../SensorData.kt`
- `core/flight/.../RealTimeFlightData.kt`
- `feature/flight-runtime/.../FlightDisplayMapper.kt`
- `feature/map/.../MapScreenUtils.kt`
- `dfcards-library/.../CardFormatSpec.kt`
- `feature/variometer/.../LxBluetoothControlState.kt`
- `feature/variometer/.../LxBluetoothControlUseCase.kt`
- `feature/profile/.../BluetoothVarioSettingsUseCase.kt`
- `feature/profile/.../BluetoothVarioSettingsScreen.kt`
- `docs/ARCHITECTURE/PIPELINE.md`

## Test Plan

- `feature:variometer`
  - LX runtime publishes TAS, pressure altitude, TE vario, metadata
  - LX runtime clears values on disconnect/error
- `feature:flight-runtime`
  - LX TAS drives `trueAirspeed`
  - TAS-only LX input keeps `ias` display invalid
  - LX pressure altitude marks external-pressure source
  - replay still ignores live LX data
- `dfcards-library`
  - `vario` renders LX TE path
  - `tas` renders LX-fed TAS
  - `ias` stays blank for TAS-only LX input
  - `baro_alt` switches correctly between phone baro and external pressure altitude
- `feature:profile`
  - settings UI shows parsed metadata when available

## Verification

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Acceptance Gates

- No duplicate SSOT owners introduced
- `ExternalInstrumentReadPort` remains pressure altitude and TE only
- Live LX TAS uses `ExternalAirspeedRepository`
- `ias` does not falsely display LX TAS-only data
- `vario`, `tas`, and `baro_alt` show LX-fed values
- Bluetooth settings show parsed LX identity
- Replay remains deterministic
