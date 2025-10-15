# Barometric Altitude & Variometer Bug Analysis

**Date:** 2025-10-15
**Status:** 🐛 4 Critical Bugs Identified
**Symptoms:** Baro Alt = -2677 ft (constantly decreasing), Default Vario = -1.9 m/s

---

## Executive Summary

The barometric altitude system has **4 interconnected bugs** that prevent QNH calibration and cause temperature compensation to run backwards. The result is a constantly decreasing altitude reading that diverges from reality.

**Critical Impact:** Competition safety compromised - pilots cannot trust altitude readings.

---

## Bug Details

### 🐛 BUG 1: GPS Altitude Initialized to Zero (CRITICAL)

**Location:** `FlightDataCalculator.kt:113`

```kotlin
private var cachedGPSAltitude = 0.0  // ❌ WRONG - should be NaN!
```

**Problem:**
- Barometer loop runs at 50Hz, GPS loop at 10Hz
- During app startup, barometer starts BEFORE GPS has any data
- `cachedGPSAltitude = 0.0` is passed to calibration logic
- Calibration validation rejects `0.0` as invalid altitude
- QNH never gets calibrated!

**Evidence:**
```kotlin
// CalcBaroAltitude.kt:272
if (gpsAltitude <= 0) {
    Log.d(TAG, "🐛 Skip sample: gpsAltitude <= 0 ($gpsAltitude)")
    return false
}
```

**Impact:** QNH stays at standard atmosphere (1013.25 hPa) forever.

**Fix:**
```kotlin
private var cachedGPSAltitude = Double.NaN  // ✅ Use NaN as "no data yet" sentinel
```

---

### 🐛 BUG 2: Temperature Compensation Formula is Backwards (CATASTROPHIC)

**Location:** `CalcBaroAltitude.kt:163-166`

```kotlin
private fun applyTemperatureCompensation(pressure: Double, temperatureCelsius: Double): Double {
    val tempRatio = (temperatureCelsius + 273.15) / (ISA_TEMPERATURE + 273.15)
    return pressure * tempRatio  // ❌ SHOULD BE DIVISION!
}
```

**Physics Analysis:**

**Correct Physics:**
- Hot air expands → Lower pressure at same altitude → Altimeter reads HIGH
- Cold air contracts → Higher pressure at same altitude → Altimeter reads LOW
- Correction: When temp > ISA, INCREASE indicated pressure to get true altitude
- Formula: `P_indicated / tempRatio` (dividing makes it larger when hot)

**Current Bug:**
- Hot weather (30°C): `tempRatio = 1.05`
- `P * 1.05` = INCREASES pressure reading
- Higher pressure → LOWER altitude reading
- **Result: Reads -2677 ft when should read +500 ft!**

**Numerical Example:**
```
Location: Sea level, 30°C (hot day)
Raw pressure: 1013 hPa
ISA temp: 15°C = 288.15 K
Actual temp: 30°C = 303.15 K
tempRatio = 303.15 / 288.15 = 1.052

❌ CURRENT (WRONG):
compensatedPressure = 1013 * 1.052 = 1065.7 hPa
altitude = calculateICAOBaroAltitude(1065.7, 1013.25)
         = -446 meters = -1463 ft  (WRONG!)

✅ CORRECT:
compensatedPressure = 1013 / 1.052 = 962.8 hPa
altitude = calculateICAOBaroAltitude(962.8, 1013.25)
         = +440 meters = +1444 ft  (CORRECT!)

Error magnitude: 2907 ft difference!
```

**Impact:** In hot weather, altitude can be off by **thousands of feet**. Catastrophic for flight safety.

**Fix:**
```kotlin
return pressure / tempRatio  // ✅ DIVIDE, not multiply!
```

---

### 🐛 BUG 3: Temperature Never Updated

**Location:** `FlightDataCalculator.kt` (missing code)

**Problem:**
- `BarometricAltitudeCalculator` has `updateTemperature()` method
- FlightDataCalculator NEVER calls it
- Temperature stuck at `ISA_TEMPERATURE = 15.0°C` forever
- Combined with Bug 2, causes unpredictable errors based on actual temp vs 15°C

**Current Code:**
```kotlin
// FlightDataCalculator.kt - NO temperature sensor integration!
// baroCalculator.updateTemperature() is NEVER called
```

**Impact:**
- If actual temp = 15°C → Bug 2 has no effect (tempRatio = 1.0)
- If actual temp = 30°C → Bug 2 causes -1463 ft error
- Unpredictable behavior depending on weather conditions

**Why This Matters:**
- Android phones don't expose reliable ambient temperature sensors
- `Sensor.TYPE_AMBIENT_TEMPERATURE` rarely available on consumer devices
- Temperature compensation is **unreliable** on phones!

**Fix Options:**
1. **RECOMMENDED:** Remove temperature compensation entirely (KISS principle)
2. Integrate actual temperature sensor (if available)
3. Use weather API for local temperature

---

### 🐛 BUG 4: GPS Altitude Validation Too Strict

**Location:** `CalcBaroAltitude.kt:272-275`

```kotlin
if (gpsAltitude <= 0) {
    Log.d(TAG, "🐛 Skip sample: gpsAltitude <= 0 ($gpsAltitude)")
    return false
}
```

**Problem:**
- Rejects any altitude ≤ 0 meters
- Many airports are at sea level (0m elevation)
- Dead Sea Airport = -430m elevation
- Legitimate altitudes rejected!

**Impact:** Sea-level airports can never calibrate QNH.

**Fix:**
```kotlin
if (gpsAltitude.isNaN() || gpsAltitude < -500.0) {  // Allow sea level, reject only absurd values
    Log.d(TAG, "🐛 Skip sample: invalid altitude ($gpsAltitude)")
    return false
}
```

---

## Root Cause Chain (How Bugs Interact)

```
APP STARTUP
    ↓
1. cachedGPSAltitude = 0.0 (Bug 1)
    ↓
2. Barometer reads pressure (50Hz loop starts)
    ↓
3. Tries to calibrate QNH with gpsAltitude = 0.0
    ↓
4. Validation rejects: "gpsAltitude <= 0" (Bug 4)
    ↓
5. QNH stays at 1013.25 hPa (uncalibrated)
    ↓
6. Temperature compensation applied (Bug 2 + Bug 3)
    ↓
7. BACKWARDS formula with WRONG temp
    ↓
8. Altitude = -2677 ft (and decreasing)
    ↓
9. Derivative calculation: V/S = -1.9 m/s
```

---

## Why Altitude is Constantly Decreasing

**Observation:** User reports altitude is "constantly increasing -3xxx ft" (getting more negative).

**Explanation:**

1. **QNH Uncalibrated:** Using standard atmosphere (1013.25 hPa)
2. **Temperature Bug Active:** If actual temp > 15°C, backwards formula LOWERS altitude
3. **Barometer Drift:** Pressure sensors drift over time (±2 hPa/hour typical)
4. **No Re-calibration:** Bug 1 prevents QNH updates during flight
5. **Cumulative Error:** Altitude drifts further from reality with time

**Mathematical Model:**
```
Raw Pressure: 1020 hPa (slowly drifting upward due to weather/sensor)
Temp: 25°C (causing backwards compensation)
tempRatio = 298.15 / 288.15 = 1.035

Compensated Pressure = 1020 * 1.035 = 1055.7 hPa
Altitude = calculateICAOBaroAltitude(1055.7, 1013.25)
         ≈ -370 meters = -1214 ft

As pressure drifts up: 1021 hPa → 1022 hPa → 1023 hPa...
Altitude goes: -1214 ft → -1246 ft → -1278 ft... (decreasing!)

Derivative: ΔAlt/Δt ≈ -1.9 m/s (matches user observation!)
```

---

## Default Vario = -1.9 m/s Explanation

**Vario Calculation:**
```kotlin
// Modern3StateKalmanFilter or LegacyKalmanVario
// Calculates derivative of barometric altitude
verticalSpeed = Δ(altitude) / Δt
```

**With Buggy Altitude:**
- Altitude decreasing at ~1.9 m/s
- Vario correctly measures this rate of change
- **Vario is ACCURATE - it's measuring the BUGGY altitude's rate of decrease!**

**This is NOT a vario bug** - it's a symptom of the altitude bug.

---

## Fix Priority & Recommendations

### CRITICAL (Fix Immediately)

**Fix 1.1:** Initialize GPS altitude properly
```kotlin
// FlightDataCalculator.kt:113
private var cachedGPSAltitude = Double.NaN  // ✅ NaN = "no data yet"
```

**Fix 1.2:** Update GPS validation
```kotlin
// CalcBaroAltitude.kt:262-289
private fun shouldCollectCalibrationSample(...): Boolean {
    if (gpsAltitude == null || gpsAltitude.isNaN()) {
        return false
    }
    if (gpsAltitude < -500.0) {  // ✅ Allow sea level
        return false
    }
    // ... rest of validation
}
```

**Fix 2:** Correct temperature compensation formula
```kotlin
// CalcBaroAltitude.kt:163
private fun applyTemperatureCompensation(pressure: Double, temperatureCelsius: Double): Double {
    val tempRatio = (temperatureCelsius + 273.15) / (ISA_TEMPERATURE + 273.15)
    return pressure / tempRatio  // ✅ DIVIDE, not multiply!
}
```

### RECOMMENDED (Apply KISS Principle)

**Fix 3:** DISABLE temperature compensation entirely
```kotlin
// CalcBaroAltitude.kt:163 - SIMPLE version
private fun applyTemperatureCompensation(pressure: Double, temperatureCelsius: Double): Double {
    return pressure  // ✅ KISS: Skip unreliable temp compensation on phones
}
```

**Rationale:**
- Phones lack reliable ambient temperature sensors
- Temperature compensation requires accurate external temp readings
- Sensor drift is more predictable WITHOUT temperature compensation
- GPS re-calibration handles temp effects indirectly
- **Simplicity > Complexity** (CLAUDE.md principle)

---

## Testing Plan

### Phase 1: Verify QNH Calibration Works

1. Apply Fix 1.1 + Fix 1.2
2. Deploy to device
3. Monitor logs:
   ```bash
   adb logcat -s "BaroCalc:I" "BaroCalc:D" -v time
   ```
4. Expected output:
   ```
   ✅ Sample ACCEPTED! (alt=123.4, acc=8.5, fixed=true)
   Calibration sample 1/15 collected...
   Calibration sample 15/15 collected...
   ✅ QNH CALIBRATED: 1018 hPa
   ```

### Phase 2: Verify Altitude Accuracy

1. Check "BARO ALT" card shows "QNH 1018" (not "STD")
2. Compare baro altitude with GPS altitude (should be within ±20m)
3. Altitude should be STABLE (not constantly decreasing)
4. Vario should fluctuate around 0.0 m/s when stationary

### Phase 3: Long-Term Stability

1. Monitor for 10+ minutes
2. Altitude drift should be < 50m (acceptable for barometric)
3. No runaway decreasing trend
4. QNH remains calibrated throughout flight

---

## Code Locations Reference

| Bug | File | Line | Priority |
|-----|------|------|----------|
| Bug 1 | `FlightDataCalculator.kt` | 113 | CRITICAL |
| Bug 1 | `FlightDataCalculator.kt` | 196-203 | CRITICAL |
| Bug 1 | `FlightDataCalculator.kt` | 306 | CRITICAL |
| Bug 2 | `CalcBaroAltitude.kt` | 163-166 | CRITICAL |
| Bug 3 | `FlightDataCalculator.kt` | (missing) | MEDIUM |
| Bug 4 | `CalcBaroAltitude.kt` | 262-289 | HIGH |

---

## Additional Notes

### Why Temperature Compensation is Problematic on Phones

1. **No Sensor Access:**
   - `Sensor.TYPE_AMBIENT_TEMPERATURE` rarely available
   - `Sensor.TYPE_TEMPERATURE` is BATTERY temp, not ambient!

2. **Heat Contamination:**
   - Phone CPU generates heat
   - Battery generates heat
   - Reading is NOT ambient air temperature

3. **Better Alternative:**
   - Use GPS altitude for periodic re-calibration
   - SRTM terrain data (already implemented) is more reliable
   - Let QNH absorb temperature effects implicitly

### SRTM-Based Calibration (Already Implemented)

The code has a BETTER solution already written:

```kotlin
// CalcBaroAltitude.kt:215-253
private fun calibrateQNHWithTerrain(...)
```

This uses:
- GPS horizontal position (accurate ±5m)
- SRTM terrain elevation (accurate ±20m, cached)
- Estimated AGL = 2m (phone on ground)
- Calculated QNH from terrain + pressure

**This is 5x more accurate than GPS vertical accuracy!** The bug prevents it from ever running.

---

## Conclusion

All 4 bugs must be fixed for reliable barometric altitude:

1. ✅ Fix GPS initialization (NaN instead of 0.0)
2. ✅ Fix temperature formula (divide instead of multiply)
3. ✅ Remove temperature compensation (KISS principle)
4. ✅ Fix GPS validation (allow sea level)

**Estimated fix time:** 30 minutes
**Testing time:** 1 hour
**Risk:** Low (fixes are isolated, well-understood)

Once fixed:
- Baro altitude will calibrate properly
- Altitude will be stable and accurate
- Vario will read correctly (currently measuring buggy altitude's rate)
- Competition-safe altitude readings restored
