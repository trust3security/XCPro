> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# Purple Needle (Audio Input Needle)

## Summary
- The purple needle is a visual of the **audio input vario**, not the sound output.
- It shows the same vertical-speed value that is **fed into the audio engine**.
- It does **not** reflect audio deadband, beep/silence mode, or frequency mapping.

## Data Flow (Source of Truth)
1) Sensor fusion computes vario + TE vario
   `feature/map/src/main/java/com/example/xcpro/sensors/domain/CalculateFlightMetricsUseCase.kt`
2) Audio input selection chooses **TE vario if valid**, else **raw vario**
   `feature/map/src/main/java/com/example/xcpro/audio/VarioAudioController.kt`
3) Selected value is stored as `latestAudioVario`
   `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataCalculatorEngineLoops.kt`
4) That value is emitted into flight data and mapped to `audioVario`
   `feature/map/src/main/java/com/example/xcpro/sensors/FlightDataEmitter.kt`
   `feature/map/src/main/java/com/example/xcpro/flightdata/FlightDisplayMapper.kt`
5) UI reads it as `audioNeedleVario` and draws the purple needle
   `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`
   `feature/variometer/src/main/java/com/example/ui1/UIVariometer.kt`

## Why It Rarely Diverges (Phone Sensors)
- TE vario is only computed when airspeed is **not** GPS ground speed, and when speed and dt
  meet minimum thresholds. On phone-only setups, airspeed typically falls back to GPS ground,
  so TE vario is **null** most of the time.
  - See TE gating in `CalculateFlightMetricsUseCase` (airspeed source check + `TE_MIN_SPEED_MS`
    and `TE_MIN_DT_SECONDS`).
- When TE vario is null, the audio input falls back to **raw/brutto vario**, which is also the
  target for the blue and red needles. That makes the purple needle track the others closely.
- The red/blue needles differ only by their response constants:
  - `NEEDLE_T95_SECONDS = 0.6` (blue)
  - `FAST_NEEDLE_T95_SECONDS = 0.4` (red)
  This difference is subtle, and both are UI-throttled to ~30 Hz in `FlightDataManager`.

## What It Is / Is Not
**Is**
- The exact vario value *selected* for audio (TE when valid, otherwise raw).
- A fast visual comparison against the blue (main) and red (fast) needles.

**Is Not**
- The audio engine's **output** (beep vs silence, frequency, duty cycle).
- A deadbanded or clamped signal (audio engine applies those internally).

## Audio Output Behavior (Separate Path)
- Audio mapping uses deadband + thresholds and clamps to +/-5 m/s.
  `feature/map/src/main/java/com/example/xcpro/audio/VarioFrequencyMapper.kt`
- Default deadband: -0.3 to +0.1 m/s
- Default lift threshold: +0.1 m/s
- Default sink silence threshold: 0.0 m/s

This means the purple needle can move even when the audio is silent.

## UI Appearance
- Purple needle is drawn as `averageNeedleValue` in `UIVariometer`.
- Color: `#7C3AED` (purple), width 4dp.

## If You Want Purple To Diverge More Often
Options:
1) Ensure TE vario is valid more often (airspeed sensor or reliable wind estimation).
2) Drive the purple needle from a different input (ex: netto vario or a filtered signal).
3) Apply the same audio deadband + clamp to the needle so it visually matches output behavior.

