# TC30s - XCSoar (phone sensors) vs XCPro

This note captures the concrete reasons TC30s behaves differently between XCSoar
(Android phone sensors only) and XCPro, so future agents do not need to re-trace
the code paths.

Scope
- XCSoar: Android phone sensors only (no TE tube, no pitot, no external vario).
- XCPro: current repository state.
- Focus: TC30s (30-second rolling average of climb/vario).

Definition (common intent)
- TC30s is a rolling 30-second average of a "brutto" vertical speed.
- It is not the "current thermal average" (TC Avg) or "total thermal avg" (T Avg).

------------------------------------------------------------------------------
XCSoar (phone sensors) TC30s pipeline

1) Sensors
   - Android pressure sensor is read in `android/src/NonGPSSensors.java`.
   - Listener is registered at `SensorManager.SENSOR_DELAY_NORMAL`.

2) Java -> native bridge
   - `android/src/NativeSensorListener.java`
   - `src/Android/NativeSensorListener.cpp`

3) Pressure -> vario (non-comp)
   - `src/Device/SmartDeviceSensors.cpp`
     - `SelfTimingKalmanFilter1d` smooths pressure.
     - `ComputeNoncompVario()` in `src/Device/SmartDeviceSensors.hpp`
       converts dP/dt to vertical speed.
     - `basic.ProvideNoncompVario(...)` sets non-comp vario.

4) Brutto vario selection
   - `src/Computer/BasicComputer.cpp`
     - `ComputeGPSVario()` prefers pressure altitude (if available),
       otherwise baro altitude, otherwise GPS altitude.
     - `ComputeBruttoVario()` chooses TE vario if present, else GPS/pressure
       derived vario. With phone sensors only, this is effectively
       pressure-derived vario.

5) TC30s averaging
   - `src/Computer/AverageVarioComputer.cpp`
     - Uses `WindowFilter<30>` to keep 30 samples.
     - Adds one sample per elapsed second (rounded).
     - **Time base for averaging is `basic.time` (GPS time).**
       It advances only when GPS time updates.

6) Display
   - `src/InfoBoxes/Content/Thermal.cpp` -> `UpdateInfoBoxThermal30s()`
     uses `CommonInterface::Calculated().average`.

XCSoar key behavior implications
- TC30s advances only when GPS time advances (typically 1 Hz).
- If GPS updates are slow or irregular, TC30s will appear sluggish or stepwise.
- Pressure vario is heavily smoothed by `SelfTimingKalmanFilter1d`.

Relevant files (XCSoar)
- `android/src/NonGPSSensors.java`
- `android/src/InternalGPS.java`
- `src/Device/SmartDeviceSensors.cpp`
- `src/Device/SmartDeviceSensors.hpp`
- `src/Computer/BasicComputer.cpp`
- `src/Computer/AverageVarioComputer.cpp`
- `src/InfoBoxes/Content/Thermal.cpp`

------------------------------------------------------------------------------
XCPro TC30s pipeline

1) Sensors
   - Baro + accel read in `feature/map/src/main/java/com/example/xcpro/sensors/SensorRegistry.kt`.
   - Baro sensor registered at `SensorManager.SENSOR_DELAY_GAME` (faster).

2) High-speed baro loop (50 Hz)
   - `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
   - Uses monotonic timestamps where available.

3) Pressure smoothing and vario
   - `feature/map/src/main/java/com/example/xcpro/sensors/PressureKalmanFilter.kt`
   - `dfcards-library/src/main/java/com/example/dfcards/filters/KalmanFilter.kt`
     (`AdvancedBarometricFilter`)
   - `ModernVarioResult.verticalSpeed` is the primary baro vario.

4) Sensor snapshot + brutto selection
   - `feature/map/src/main/java/com/example/xcpro/sensors/domain/SensorFrontEnd.kt`
   - TE vario is used when available; otherwise pressure vario or GPS vario.

5) TC30s averaging
   - `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
     picks `avgVarioSample` (TE -> pressure vario -> pressure altitude vario
     -> GPS vario -> brutto).
   - `feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt`
     owns the 30-second rolling window.
   - `feature/map/src/main/java/com/example/xcpro/sensors/WindowFill.kt`
     adds one sample per elapsed second.
   - **Time base for averaging is the baro loop time
     (`currentTime` from baro monotonic or baro timestamp).**

6) Display
   - `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
   - `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`
     shows TC30s on card `thermal_avg`.

XCPro key behavior implications
- TC30s advances on baro time (50 Hz loop), not GPS time.
- Non-finite samples are treated as 0.0 to keep the window moving
  (`FusionBlackboard.updateAveragesAndDisplay()`).
- Window resets on time going backwards or circling state toggles.

Relevant files (XCPro)
- `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/domain/FusionBlackboard.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/WindowFill.kt`
- `feature/map/src/main/java/com/example/xcpro/sensors/PressureKalmanFilter.kt`
- `dfcards-library/src/main/java/com/example/dfcards/filters/KalmanFilter.kt`
- `dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt`

------------------------------------------------------------------------------
Why XCPro TC30s looks "faster" than XCSoar (phone sensors)

1) Different time base and gating
- XCSoar TC30s uses GPS time (`basic.time`) and updates only when GPS time
  advances. On Android this is usually 1 Hz (`android/src/InternalGPS.java`).
- XCPro TC30s uses baro monotonic time from the high-speed baro loop, so it
  advances continuously.

2) Different sensor rate
- XCSoar pressure sensor is read at `SENSOR_DELAY_NORMAL` (slower).
- XCPro uses `SENSOR_DELAY_GAME` for baro and a 50 Hz fusion loop.

3) Different smoothing/filtering
- XCSoar: `SelfTimingKalmanFilter1d` on pressure, then direct dP/dt to vario.
- XCPro: `PressureKalmanFilter` + `AdvancedBarometricFilter` + vario suite.
  This can yield a more responsive sample stream.

4) Window reset and invalid sample handling
- XCPro: non-finite samples become 0.0 (keeps window moving).
- XCPro: window resets when circling state toggles (may cause jumps).
- XCSoar: window advances only when `DeltaTime` gets a positive dt from GPS time.

Net effect
- XCSoar TC30s tends to feel slower and more stable because it is
  GPS-time gated and more heavily smoothed for phone sensors.
- XCPro TC30s feels more responsive because it is baro-time gated and updates
  with higher cadence and different filtering.

------------------------------------------------------------------------------
Debug hooks

XCPro
- Enable thermal logs in `FlightDataConstants.LOG_THERMAL_METRICS` and read:
  `Thermal metrics: TC30=...` in `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`.

XCSoar
- TC30s is the InfoBox "Thermal climb, last 30 s" -> `UpdateInfoBoxThermal30s()`
  in `src/InfoBoxes/Content/Thermal.cpp`.

------------------------------------------------------------------------------
If we want XCPro TC30s to behave more like XCSoar (phone sensors)

Options to consider
- Gate TC30s update time to GPS time (use `gpsTimestampMillis` instead of
  baro time for `FusionBlackboard.updateAveragesAndDisplay()`).
- Reduce baro cadence contribution (e.g., update TC30s only when a new GPS
  fix arrives).
- Change `addSamplesForElapsedSeconds()` to floor instead of round seconds,
  matching XCSoar's behavior more closely.
- Avoid treating non-finite samples as 0.0 for TC30s, so dropouts pause the
  window instead of pulling the average.
- Revisit circling toggle resets if they cause frequent TC30s jumps.

Note: These are behavioral alignment ideas, not changes made.

