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
5) UI reads it as `audioNeedleVario` and draws purple needle  
   `feature/map/src/main/java/com/example/xcpro/map/FlightDataManager.kt`  
   `feature/variometer/src/main/java/com/example/ui1/UIVariometer.kt`

## What It Is / Is Not
**Is**
- The exact vario value *selected* for audio (TE when valid, otherwise raw).
- A fast visual comparison against the blue (main) and red (fast) needles.

**Is Not**
- The audio engine’s **output** (beep vs silence, frequency, duty cycle).
- A deadbanded or clamped signal (audio engine applies those internally).

## Audio Output Behavior (Separate Path)
- Audio mapping uses deadband + thresholds and clamps to ±5 m/s.  
  `feature/map/src/main/java/com/example/xcpro/audio/VarioFrequencyMapper.kt`
- Default deadband: **-0.3 to +0.1 m/s**  
- Default lift threshold: **0.1 m/s**  
- Default sink silence threshold: **0.0 m/s**

This means the purple needle can move even when the audio is silent.

## UI Appearance
- Purple needle is drawn as `averageNeedleValue` in `UIVariometer`.
- Color: `#7C3AED` (purple), width 4dp.

## If You Want Purple To Match Audio Output
Options:
1) Apply the **same deadband + thresholds** before drawing the purple needle.
2) Drive the needle from **audio mode** (SILENCE/BEEPING/CONTINUOUS).
3) Clamp purple needle to the same **±5 m/s** range used by audio mapping.

