
# CardDataFormatter Phase 0 Inventory

Date: 2026-01-29
Scope: Discovery and alignment for CardDataFormatter refactor.

## Sources reviewed
- dfcards-library/src/main/java/com/example/dfcards/CardDataFormatter.kt
- dfcards-library/src/main/java/com/example/dfcards/CardLibraryCatalog.kt
- dfcards-library/src/main/java/com/example/dfcards/CardLibrary*Catalog.kt
- dfcards-library/src/main/java/com/example/dfcards/FlightTemplates.kt
- dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepository.kt
- dfcards-library/src/main/java/com/example/dfcards/dfcards/CardStateRepositoryUpdates.kt
- feature/map/src/main/java/com/trust3/xcpro/MapScreenUtils.kt
- feature/map/src/main/java/com/trust3/xcpro/map/MapScreenObservers.kt (wrapped by FlightDataUiAdapter)
- feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataEmitter.kt
- feature/map/src/main/java/com/trust3/xcpro/sensors/FlightDataCalculatorEngineLoops.kt
- feature/map/src/main/java/com/trust3/xcpro/sensors/SensorRegistry.kt
- feature/map/src/main/java/com/trust3/xcpro/sensors/SensorData.kt

## Card ID inventory (catalog vs formatter vs templates)
All CardDefinition ids in the card catalog aggregation are handled in CardDataFormatter (39 of 39).
Default templates cover 18 unique cards; the rest are only available via the card library.

Legend:
- Templates: Cruise, Thermal, Final, Cross, Comp
- Notes: Fast update means in FAST_UPDATE_CARD_IDS. Clock means updated by independent timer.

| CardId | Category | Default templates | Formatter | Notes |
| --- | --- | --- | --- | --- |
| gps_alt | Essential | Final, Cross | Yes | Uses baroAltitude when QNH calibrated; fallback to gpsAltitude |
| baro_alt | Essential | - | Yes | Requires baroAltitude > 0 else NO BARO |
| agl | Essential | - | Yes | Uses NaN check; shows QNH in secondary |
| vario | Essential | - | Yes | Fast update; uses primaryVarioValue and varioValid/source |
| ias | Essential | - | Yes | Fast update; label uses tasValid (EST vs GPS) |
| tas | Essential | - | Yes | Label uses tasValid (EST vs GPS) |
| ground_speed | Essential | Final, Cross | Yes | Fast update |
| vario_optimized | Vario | - | Yes | Fast update |
| vario_legacy | Vario | - | Yes | Fast update |
| vario_raw | Vario | - | Yes | Fast update |
| vario_gps | Vario | - | Yes | Fast update |
| vario_complementary | Vario | - | Yes | Fast update |
| real_igc_vario | Vario | - | Yes | Replay-only; shows NO IGC if null |
| track | Navigation | Cruise, Cross | Yes | Uses groundSpeed > 2 kt gate |
| wpt_dist | Navigation | Cross | Yes | Placeholder only (NO WPT) |
| wpt_brg | Navigation | Cross | Yes | Placeholder only (NO WPT) |
| final_gld | Navigation | Final, Cross, Comp | Yes | Placeholder only (NO WPT) |
| wpt_eta | Navigation | Comp | Yes | Placeholder only (NO WPT) |
| thermal_avg | Performance | Thermal | Yes | Primary TC30; secondary current thermal |
| thermal_tc_avg | Performance | Thermal, Cross | Yes | Highlight color logic in CardStateRepositoryUpdates |
| thermal_t_avg | Performance | Thermal, Cross | Yes | Rejects abs(sample) <= 0.1 |
| thermal_tc_gain | Performance | Thermal | Yes | Uses thermalGainValid |
| netto | Performance | - | Yes | Uses displayNetto; label NO POLAR when invalid |
| netto_avg30 | Performance | Thermal | Yes | Uses nettoAverage30s |
| ld_curr | Performance | Cross, Comp | Yes | Requires currentLD > 1 |
| mc_speed | Performance | Comp | Yes | Placeholder only (NO MC) |
| wind_spd | Time/Weather | - | Yes | Needs windQuality > 0 and windSpeed > 0.5 |
| wind_dir | Time/Weather | - | Yes | Needs windQuality > 0 and windSpeed > 0.5 |
| wind_arrow | Time/Weather | - | Yes | Uses headingValid to render relative arrow |
| local_time | Time/Weather | - | Yes | Clock update in CardStateRepositoryUpdates |
| flight_time | Time/Weather | Comp | Yes | From FlightDataUiAdapter/MapScreenObservers elapsed time |
| task_spd | Competition | Comp | Yes | Placeholder only (NO TASK) |
| task_dist | Competition | Comp | Yes | Placeholder only (NO TASK) |
| start_alt | Competition | Comp | Yes | Placeholder only (NO START) |
| g_force | Advanced | - | Yes | Placeholder only (NO ACCEL) |
| flarm | Advanced | - | Yes | Placeholder only (NO FLARM) |
| qnh | Advanced | - | Yes | Requires currentPressureHPa > 0 else NO BARO |
| satelites | Advanced | - | Yes | Typo in id is consistent across catalog and formatter |
| gps_accuracy | Advanced | - | Yes | Quality buckets based on accuracy meters |

Additional ID lists:
- CardStateRepository essentialCardIds: gps_alt, baro_alt, agl, vario, ias, ground_speed (tas excluded).
- FAST_UPDATE_CARD_IDS: vario, vario_optimized, vario_legacy, vario_raw, vario_gps, vario_complementary, ground_speed, ias.
- Highlight color logic: thermal_avg and thermal_tc_avg.
- local_time is skipped in updateCardsWithLiveData and updated by a 1s wall clock timer.

## Timebase notes
Findings from the sensor and mapping pipeline:
- SensorRegistry sets GPSData.timestamp to Location.time (wall time) and monotonicTimestampMillis to elapsedRealtime.
- FlightDataCalculatorEngine uses monotonic timestamps for live calculations when available; in replay it uses sensor timestamps as the simulation clock.
- FlightDataEmitter sets CompleteFlightData.timestamp to outputTimestampMillis:
  - Live: wall time
  - Replay: IGC time
- FlightDataUiAdapter/MapScreenObservers chooses sampleClockMillis = gps.timestamp if present, else CompleteFlightData.timestamp.
- convertToRealTimeFlightData sets:
  - RealTimeFlightData.timestamp = CompleteFlightData.timestamp
  - RealTimeFlightData.lastUpdateTime = sampleClockMillis
- CardDataFormatter local_time uses lastUpdateTime if > 0 else timestamp.
- CardStateRepositoryUpdates overrides local_time using clock.nowWallMs() every second.

Implications:
- local_time is wall-clock driven in UI regardless of replay. CardDataFormatter local_time path may only matter for initial hydration or previews.
- flight_time uses elapsed based on sampleClockMillis (GPS timestamp or CompleteFlightData timestamp), which is wall time for live and IGC time for replay.

Open questions to resolve before refactor:
- Should RealTimeFlightData include an explicit wallTimeMillis field for UI time display to avoid ambiguity?
- Should CardDataFormatter handle local_time at all if it is always updated by CardStateRepositoryUpdates?

## Phase 0 output checklist
- Card ID inventory and default template coverage recorded (this doc).
- Timebase semantics traced to source files (this doc).
- Candidate mismatches and placeholder-only cards identified (this doc).

