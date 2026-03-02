# IAS/TAS Deep Code Pass (XCPro, Live/Flying Only)

Date: 2026-02-27
Scope: live/flying IAS/TAS path only.

Release scope decision (confirmed): no external airspeed hardware integration for this release.

## Executive Summary

- Runtime IAS/TAS are represented as SI (`m/s`) in `AirspeedSample`.
- Live fusion selects airspeed in this order:
  1. fresh valid external sample (`SENSOR`),
  2. wind-derived estimate (`WIND`),
  3. GPS ground-speed fallback (`GPS`).
- `tasValid` does not mean "dedicated TAS sensor present"; it means an energy-eligible non-GPS estimate is active (`SENSOR`/`WIND`).
- TE compensation is hard-gated by source eligibility and thresholds (`speed > 5 m/s`, `previous speed > 5 m/s`, `dt > 0.05 s`).
- External ingest entry exists (`ExternalAirspeedRepository.updateAirspeed(...)`) but there is currently no production callsite feeding it.
- Operational note: this project currently has no live external airspeed hardware/device integration, so `SENSOR` airspeed is effectively unavailable in normal use.
- External sample freshness is checked against the fusion clock (`currentTimeMillis`), which is monotonic sensor time in live loops; wall-time samples can be rejected as stale/invalid-age.
- Wind pipeline receives airspeed in inputs, but current production wind solve path is circling/store-driven and does not consume `input.airspeed`.
- The 10s airspeed hold only applies when no estimate is available; in moving flight, GPS fallback usually makes estimate non-null, so hold is commonly bypassed.
- Wind "available" for UI (`quality > 0 && !stale`) is looser than wind eligibility for TAS/TE (`confidence >= 0.1`), so UI can show wind while TAS/TE has already fallen back.
- `WindEkfUseCase` exists and is tested, but is not wired into production wind fusion flow.
- Legacy netto speed-cache logic shares one timestamp for IAS and ground speed; this can keep stale IAS "fresh" when only ground speed updates.

## Canonical Models and Units

- Core airspeed sample model:
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindInputs.kt:20`
  - fields: `trueMs`, `indicatedMs`, `timestampMillis`, `clockMillis`, `valid`.
- Selected airspeed model in metrics:
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedModels.kt:3`
- Source labels + TE eligibility:
  - `EXTERNAL -> "SENSOR"` (`energyHeightEligible = true`)
  - `WIND_VECTOR -> "WIND"` (`energyHeightEligible = true`)
  - `GPS_GROUND -> "GPS"` (`energyHeightEligible = false`)
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/AirspeedModels.kt:9`

## Live Pipeline (End to End)

### 1) Live Source Wiring

- DI binds live airspeed source to `ExternalAirspeedRepository`:
  - `feature/map/src/main/java/com/example/xcpro/di/WindSensorModule.kt:57`
- Fusion factory chooses the live airspeed source for live mode:
  - `feature/map/src/main/java/com/example/xcpro/sensors/SensorFusionRepositoryFactory.kt:35`
- Engine collects selected `airspeedFlow` and caches latest sample:
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngine.kt:142`

### 2) Metrics Request Feed

- Cached live sample is forwarded on both emit paths:
  - baro-driven emit: `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:195`
  - GPS fallback emit: `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:307`
- `FlightDataEmitter` passes this into `FlightMetricsRequest.externalAirspeedSample`:
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt:85`

### 3) External Sample Acceptance

`CalculateFlightMetricsUseCase.resolveExternalAirspeed(...)` accepts only when:

- sample exists and `valid == true`,
- sample is fresh (`<= 3000 ms`) using `clockMillis` first, else `timestampMillis`,
- `trueMs` finite and `> 0.1`,
- `indicatedMs` finite and `> 0.1`; otherwise uses `trueMs` as IAS fallback.

Code:
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:393`
- freshness check: `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:410`
- constants: `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:458`

Timebase contract detail (live):

- Freshness compares `sample.clockMillis` first (then `sample.timestampMillis`) against `FlightMetricsRequest.currentTimeMillis`.
- In live loops, `currentTimeMillis` is fed from sensor calc-time (`monotonicTimestampMillis` when present, else wall-time timestamp).
- If an external producer sends wall-time-only values in `clockMillis` while fusion runs in monotonic timebase, `age` becomes negative and the sample is rejected.
- Practical producer contract for live mode: publish `clockMillis` in the same monotonic domain used by GPS/baro ingest; keep `timestampMillis` for wall-time metadata/logging.

Code:
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:410`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt:79`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:20`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:33`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:247`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:249`
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorData.kt:28`
- `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt:86`

### 4) Wind-Derived TAS/IAS (Live)

`WindEstimator.fromWind(...)`:

- requires finite GPS speed and bearing, plus non-null wind vector,
- computes air vector as `ground - wind_to`,
- `TAS = hypot(airEast, airNorth)`, must be `> 0.1`,
- computes IAS from TAS using QNH-aware density ratio.

Code:
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/WindEstimator.kt:15`
- density formula: `feature/map/src/main/java/com/example/xcpro/sensors/domain/WindEstimator.kt:40`

Wind eligibility used by metrics:

- requires `windState.isAvailable == true` and `confidence >= 0.1`.
- `windState.isAvailable` means `vector != null && quality > 0 && !stale`.
- This differs from UI-facing "wind valid" semantics that are often tied to `isAvailable` only; TAS/TE wind usage is stricter and can drop earlier when confidence decays.

Code:
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:94`
- `feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindState.kt:14`
- confidence decay / stale horizon: `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:362`

### 5) Final Selection Order

In `CalculateFlightMetricsUseCase.execute(...)`:

1. external sample,
2. wind-derived sample,
3. GPS fallback (only if GPS speed finite and `> 0.5 m/s`, with IAS=TAS=GPS).

Code:
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:97`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:117`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:451`

### 6) Hold and Validity Semantics

- Last valid airspeed estimate is held for `10_000 ms`.
- Hold is resolved in `SensorFrontEnd` through `FusionBlackboard.resolveAirspeedHold(...)`.
- If no active/held estimate, IAS/TAS output falls to `0.0` and source defaults to `GPS`.
- Hold is only consulted when incoming `airspeedEstimate` is `null`; because source selection usually emits GPS fallback above `0.5 m/s`, hold is often bypassed during normal moving flight.

Code:
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt:59`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt:58`
- constant: `feature/map/src/main/java/com/example/xcpro/sensors/domain/FlightMetricsConstants.kt:22`
- selection/fallback path: `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:108`

`tasValid` rule:
- `tasValid = activeEstimate != null && source.energyHeightEligible`
- therefore `GPS` source yields `tasValid = false`.
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt:62`

## How Live IAS/TAS Is Used

### TE / Energy Height

- TE vario is enabled only if:
  - TE toggle is on,
  - chosen source is energy-eligible (`SENSOR` or `WIND`),
  - current and previous speed > `5.0 m/s`,
  - `dt > 0.05 s`.
- TE altitude kinetic term is only applied for energy-eligible source.

Code:
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:120`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt:67`

### Netto and STF

- Legacy netto path uses indicated airspeed first, then hold/fallback behavior:
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt:363`
- Levo netto requires wind+polar+flying+straight conditions, sink lookup from IAS, and may scale smoothing using TAS:
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/LevoNettoCalculator.kt:49`
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/LevoNettoCalculator.kt:104`
- STF computes IAS target and IAS delta vs current IAS:
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/SpeedToFlyCalculator.kt:22`

### Flying State

- Flight-state path consumes the live airspeed source in live mode.
- Detector input uses `trueAirspeedMs = airspeed?.trueMs` with `airspeedReal = valid && finite`.
- No explicit sample age/freshness gate is applied in `FlightStateRepository` before detector call.

Code:
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:60`
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:100`
- detector logic: `feature/map/src/main/java/com/example/xcpro/sensors/domain/FlyingStateDetector.kt:38`

## UI / Card Exposure

- Domain output maps into SSOT with:
  - `trueAirspeed`, `indicatedAirspeed`, `airspeedSource`, `tasValid`
  - `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt:64`
- SSOT maps to `RealTimeFlightData`:
  - `feature/map/src/main/java/com/example/xcpro/MapScreenUtils.kt:135`

Card formatting behavior:

- IAS card uses `indicatedAirspeed`, TAS card uses `trueAirspeed`.
- Subtitle is `EST` when `tasValid=true`, `GPS` when `tasValid=false`.
- Card subtitle does not expose `SENSOR` vs `WIND` source distinction.
- `cardFlightDataFlow` buckets vario/altitude/wind/LD fields, but IAS/TAS fields are not bucketed in `toDisplayBucket`, so IAS/TAS propagate at source precision while labels still depend only on `tasValid`.

Code:
- `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:135`
- `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:146`
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt:207`
- `feature/map/src/main/java/com/example/xcpro/map/FlightDataManagerSupport.kt:21`

## Additional Live Details Found In This Intensive Pass

1. Wind confidence decay is source-sensitive:
- Auto wind (`CIRCLING`/`EKF`) decays with half-life `7 minutes` from `lastCirclingClockMillis`.
- Manual/external wind uses base quality directly (no decay).
- `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:362`

2. Wind stale threshold is long (`1 hour`):
- `STALE_MS = 3_600_000L`.
- `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:377`

3. Wind override default quality is `6`, but confidence normalization clamps with `MAX_MEASUREMENT_QUALITY=5`:
- practical result: override confidence saturates at `1.0`.
- `feature/map/src/main/java/com/example/xcpro/weather/wind/model/WindOverride.kt:10`
- `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:364`

4. Wind candidate precedence is:
- auto when newer than manual,
- else external,
- else manual,
- else auto.
- `feature/map/src/main/java/com/example/xcpro/weather/wind/domain/WindSelectionUseCase.kt:23`

5. Production wind fusion receives `input.airspeed`, but current processing path does not read it.
- `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepository.kt:57`

6. Wind override write APIs exist but have no production callsites outside their repository:
- `setManualWind(...)`, `updateExternalWind(...)`, `updateExternalWindVector(...)` are defined in `WindOverrideRepository`.
- Search over `feature/map/src/main/java` shows no callsites beyond the repository itself.
- `feature/map/src/main/java/com/example/xcpro/weather/wind/data/WindOverrideRepository.kt:67`

## Current Gaps / Risks (Live)

1. External live ingest is unbound in production
- `ExternalAirspeedRepository.updateAirspeed(...)` exists but no production callsite was found.
- There is currently no integrated external airspeed device path in this codebase.
- file: `feature/map/src/main/java/com/example/xcpro/weather/wind/data/ExternalAirspeedRepository.kt:16`

2. External sample timebase mismatch can silently disable `SENSOR` airspeed
- Freshness gating compares sample times to fusion `currentTimeMillis` (typically monotonic in live loops).
- Producers emitting wall-time in `clockMillis` may fail freshness checks (`age < 0`) and be rejected.
- files:
  - `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt:410`
  - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt:249`

3. Wind EKF is present but not integrated into production wind fusion path
- `WindEkfUseCase` exists with tests but no production wiring found.
- files:
  - `feature/map/src/main/java/com/example/xcpro/weather/wind/domain/WindEkfUseCase.kt:12`
  - `feature/map/src/test/java/com/example/xcpro/weather/wind/WindEkfUseCaseTest.kt:10`

4. Flight-state path does not apply explicit airspeed freshness gating
- It validates only `valid + finite` and forwards directly to detector.
- file: `feature/map/src/main/java/com/example/xcpro/sensors/FlightStateRepository.kt:100`

5. UI subtitle granularity is limited
- IAS/TAS cards only show `EST` vs `GPS`; they do not distinguish `SENSOR` from `WIND`.
- file: `dfcards-library/src/main/java/com/example/dfcards/CardFormatSpec.kt:139`

6. Naming drift in legacy netto helper
- `lastValidTAS` variable stores IAS candidate logic.
- file: `feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt:372`

7. Shared IAS/ground-speed timestamp can keep stale IAS active in netto path
- `calculateNetto(...)` uses one `lastSpeedTimestamp` for both IAS and ground-speed caches.
- Fresh ground-speed updates can extend IAS recency window (`recentTas`) even when IAS itself has not refreshed.
- This can bias sink-polar speed selection in edge/dropout conditions.
- file: `feature/map/src/main/java/com/example/xcpro/sensors/FlightCalculationHelpers.kt:363`

## Live Test Coverage Snapshot

Covered:
- airspeed source priority + stale/fresh logic (`SENSOR`/`WIND`/`GPS`):
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCaseTest.kt:358`
- airspeed hold behavior and expiry:
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/SensorFrontEndTest.kt:87`
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/FusionBlackboardTest.kt:70`
- wind-based IAS/TAS estimation and QNH effect:
  - `feature/map/src/test/java/com/example/xcpro/sensors/domain/WindEstimatorTest.kt:12`
- wind confidence half-life behavior:
  - `feature/map/src/test/java/com/example/xcpro/weather/wind/data/WindSensorFusionRepositoryTest.kt:27`
- `tasValid` and IAS/TAS mapping to card data model:
  - `feature/map/src/test/java/com/example/xcpro/ConvertToRealTimeFlightDataTest.kt:81`
  - `dfcards-library/src/test/java/com/example/dfcards/CardDataFormatterTest.kt:147`

Observed missing/weak coverage for live behavior:
- no dedicated production-path test for a real live external producer feeding `ExternalAirspeedRepository`.
- no explicit test for mixed clock domains in external freshness (e.g., monotonic `currentTimeMillis` with wall-time `clockMillis`).
- no explicit test for clock-vs-timestamp fallback edge cases (`clockMillis` invalid but `timestampMillis` valid, and vice versa).
- helper in `CalculateFlightMetricsUseCaseTest` sets `timestampMillis = clockMillis`, so timebase-mismatch regressions are currently under-tested.
- no dedicated `FlightStateRepository` test that validates stale-vs-fresh airspeed gating behavior (there is currently no freshness gate there).

## Practical Live Interpretation

- If a live external producer is eventually wired, `SENSOR` airspeed becomes first-priority and enables TE/netto/STF paths immediately when fresh.
- In current production live code, practical airspeed is usually:
  - `WIND` when wind state is available and confident enough,
  - otherwise `GPS` fallback.
- Given there is no external device integration today, treat `SENSOR` as non-operational until a real producer is wired.
- When fallback is `GPS`, `tasValid=false`, TE kinetic compensation is blocked by source eligibility, and cards label IAS/TAS as `GPS`.

## Release-Scoped Recommendation (No External Hardware)

Keep external-airspeed wiring work out of this release and prioritize correctness hardening in existing live paths:

1. Split legacy netto speed timestamps into separate IAS and ground-speed recency clocks in `FlightCalculationHelpers.calculateNetto(...)`.
2. Add a focused unit test proving stale IAS cannot stay "recent" via fresh ground-speed updates.
3. Keep `SENSOR` path documented as inactive for this release (no producer callsites).
