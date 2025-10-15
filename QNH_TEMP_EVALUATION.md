# QNH Drift & Temperature Compensation - Implementation Evaluation

**Created:** 2025-10-13
**Context:** Gliding flights 10am-7pm (max 9 hours, typical 3-5 hours)
**Question:** Are these issues worth implementing vs SRTM QNH calibration fix?

---

## 📊 Research Summary

### 1. QNH Drift During Gliding Flights

#### Atmospheric Pressure Behavior

**Diurnal (Daily) Pressure Cycle:**
- Semidiurnal component: ±1.4 hPa amplitude (12-hour cycle)
- Diurnal component: ±0.7 hPa amplitude (24-hour cycle)
- Pressure minimums: **4am and 4pm** Local Standard Time
- Pressure maximums: **10am and 10pm** Local Standard Time

**Typical Gliding Day (10am-7pm):**
```
10am: Pressure at MAXIMUM (high point)
  ↓
4pm: Pressure at MINIMUM (low point) - drop ~1-2 hPa
  ↓
7pm: Pressure rising again - rise ~0.5 hPa
```

**Weather System Pressure Changes:**
- Stable weather: 0.5-2 hPa per hour
- Normal weather: 2-3 hPa per hour
- Active weather front: 3-7 hPa per hour
- Severe storms: 10+ hPa per 3 hours

#### Real-World Error Calculation

**Conversion:** 1 hPa = ~8 meters altitude at sea level (27 feet)

**Scenario 1: Perfect Weather (Rare)**
```
Flight duration: 5 hours (10am-3pm)
Pressure change: 1 hPa (diurnal only)
QNH drift: 1 hPa
Altitude error: 1 × 8m = 8m ✅ Negligible
```

**Scenario 2: Normal Soaring Day (Common)**
```
Flight duration: 5 hours (10am-3pm)
Pressure change: 2-3 hPa (diurnal + slight weather trend)
QNH drift: 2-3 hPa
Altitude error: 2-3 × 8m = 16-24m ⚠️ Moderate
```

**Scenario 3: Active Weather Day (Occasional)**
```
Flight duration: 5 hours
Pressure change: 5-10 hPa (weather front approaching)
QNH drift: 5-10 hPa
Altitude error: 5-10 × 8m = 40-80m ❌ Significant
```

**Scenario 4: Long XC Flight (Max Duration)**
```
Flight duration: 9 hours (10am-7pm)
Pressure change: 3-5 hPa (full diurnal + weather)
QNH drift: 3-5 hPa
Altitude error: 3-5 × 8m = 24-40m ❌ Significant
```

#### Impact Assessment

**For Gliding Safety:**
- Airspace boundaries: Typically ±300m buffers → 24-40m error is **acceptable**
- Terrain clearance: Use AGL, not ASL → **not affected**
- Final glide: GPS altitude used for scoring → **not affected**
- Other aircraft separation: Visual flight rules → **not affected**

**For Competition:**
- Start height limit: Checked by GPS logger → **not affected**
- Turnpoint photos: GPS altitude logged → **not affected**
- Airspace violations: 300m vertical buffers → **unlikely to cause issues**

**For Display:**
- Pilot expects stable altitude readings
- 40m drift over 5 hours = 8m/hour = **barely noticeable**
- Our 20% GPS sensor fusion already corrects this somewhat

---

### 2. Temperature Compensation Effects

#### Aviation Standard Temperature Effects

**ICAO Standard Atmosphere:**
- Sea level: 15°C (59°F)
- Temperature lapse rate: -2°C per 1000 feet (-6.5°C per 1000m)

**Temperature Error Formula:**
```
Altitude error = 4 feet per °C deviation per 1000 feet altitude
              = ~1.2 meters per °C deviation per 300m altitude
```

#### Real-World Temperature Scenarios

**Hot Summer Soaring Day:**
```
Altitude: 1500m (typical thermal height)
ISA temperature at 1500m: 15°C - (6.5°C × 1.5) = 5.25°C
Actual temperature: 25°C (hot day)
Deviation: +19.75°C (warmer than ISA)

Temperature error: (1500m / 300m) × 19.75°C × 1.2m
                 = 5 × 19.75 × 1.2
                 = 118m ❌ HUGE ERROR!

Direction: Altimeter reads LOW (actual altitude is HIGHER)
```

**Cold Winter/Mountain Flight:**
```
Altitude: 2000m (ridge soaring Alps)
ISA temperature at 2000m: 15°C - (6.5°C × 2) = 2°C
Actual temperature: -10°C (cold day)
Deviation: -12°C (colder than ISA)

Temperature error: (2000m / 300m) × 12°C × 1.2m
                 = 6.67 × 12 × 1.2
                 = 96m ❌ HUGE ERROR!

Direction: Altimeter reads HIGH (actual altitude is LOWER)
```

#### The Critical Question: Phone Temp vs Ambient Temp

**Our Current Implementation:**
```kotlin
// CalcBaroAltitude.kt line 147
private fun applyTemperatureCompensation(pressure: Double, temperatureCelsius: Double): Double {
    val tempRatio = (temperatureCelsius + 273.15) / (ISA_TEMPERATURE + 273.15)
    return pressure * tempRatio
}

// Uses: this.temperatureCelsius = ISA_TEMPERATURE (15°C by default)
```

**Status: We're using ISA standard temperature (15°C), NOT phone temperature!**

This is actually **CORRECT** - we're not applying wrong temperature compensation.

**If we wanted to add temperature compensation, we'd need:**

Option 1: **Phone Temperature Sensor**
- Problem: Measures phone temp, not ambient air
- Phone in cockpit: +10-20°C warmer than ambient (sun/electronics)
- Error from wrong temp: 10-20°C × 1.2m × (alt/300m) = 40-80m at 1500m
- **Conclusion: WORSE than not compensating at all!**

Option 2: **Weather API (METAR/TAF)**
- Fetch actual temperature from nearest weather station
- Latency: 30-60 minute updates (METAR frequency)
- Accuracy: ±2-5°C (station vs actual location)
- Error: 2-5°C × 1.2m × (alt/300m) = 8-20m at 1500m
- **Conclusion: Marginal improvement, adds complexity**

Option 3: **No Compensation (Current Approach)**
- Assume ISA standard temperature (15°C at sea level)
- Error depends on actual temperature deviation
- Hot day: -40m to -120m (reads low)
- Cold day: +40m to +120m (reads high)
- **Conclusion: Simple, but has systematic error**

---

## 🎯 Decision Matrix

### QNH Drift Monitoring

**Effort to Implement:**
- Monitor GPS/baro difference: 2 hours
- Alert pilot when difference > threshold: 1 hour
- Manual recalibration button: 2 hours
- **Total: 5 hours**

**Benefit:**
- Catches 5-10 hPa drift (40-80m error)
- Alerts pilot to weather changes
- Provides peace of mind

**Frequency of Issue:**
- Normal days (2-3 hPa drift): Every flight
- Active weather (5-10 hPa): ~10-20% of flights
- Pilot would notice drift anyway (GPS shows different)

**Recommendation:** ⚠️ **MEDIUM PRIORITY**
- Not critical for safety (airspace margins large)
- Nice to have for peace of mind
- Implement after SRTM QNH fix
- Low effort, moderate benefit

---

### Temperature Compensation

**Effort to Implement:**

Option A: Phone temperature (EASY but WRONG)
- Read phone temp sensor: 1 hour
- Apply compensation: 1 hour
- **Total: 2 hours**
- **Result: MAKES THINGS WORSE** ❌

Option B: Weather API temperature (MEDIUM effort)
- Fetch METAR temperature: 3 hours
- Cache and update: 2 hours
- Apply compensation: 1 hour
- **Total: 6 hours**
- **Result: Marginal 8-20m improvement**

Option C: No compensation (CURRENT)
- Effort: 0 hours ✅
- **Result: Systematic error 40-120m depending on temp**

**Frequency of Issue:**
- Hot summer days (+20°C deviation): -120m error (reads low)
- Cold winter days (-15°C deviation): +90m error (reads high)
- Moderate days (±5°C deviation): ±30m error
- **Most flights: 30-60m systematic error**

**Recommendation:** ⚠️ **LOW-MEDIUM PRIORITY**
- Phone temp sensor: **DO NOT USE** (makes things worse)
- Weather API: Moderate effort, marginal benefit
- Current approach: Acceptable for gliding
- Implement only if fetching weather data anyway (for other features)

---

## 🏆 Final Recommendations

### Priority Ranking

**1. SRTM-Based QNH Calibration (CRITICAL - DO THIS FIRST)**
- Effort: 4-6 hours
- Benefit: Fixes 80m → 20m error (4x improvement)
- Impact: Eliminates negative altitude bug
- **STATUS: MUST IMPLEMENT IMMEDIATELY**

**2. Adaptive Sensor Fusion (HIGH)**
- Effort: 1-2 hours
- Benefit: Better stability with varying GPS quality
- Impact: Reduces GPS jump artifacts
- **STATUS: Implement after #1**

**3. QNH Drift Monitoring (MEDIUM)**
- Effort: 5 hours
- Benefit: Catches weather changes, pilot peace of mind
- Impact: Prevents 40-80m errors on active weather days
- **STATUS: Implement after #1 and #2**
- **JUSTIFICATION:** Not safety-critical, but good practice

**4. Temperature Compensation via Weather API (LOW)**
- Effort: 6 hours
- Benefit: 20-30m error reduction (marginal)
- Impact: Systematic error correction
- **STATUS: Optional, only if adding weather features anyway**
- **JUSTIFICATION:** Effort vs benefit ratio poor
- **ALTERNATIVE:** Accept ISA standard temperature error

**5. Temperature Compensation via Phone Sensor (DO NOT IMPLEMENT)**
- Effort: 2 hours
- Benefit: NEGATIVE (makes things worse!)
- Impact: Wrong temperature → wrong compensation
- **STATUS: REJECTED**

---

## 📊 Error Budget Comparison

### Current System (After SRTM QNH Fix)
```
QNH calibration error:     ±8 hPa    → ±64m     (SRTM-based)
Barometric sensor noise:   ±0.5 hPa  → ±4m      (hardware limit)
Temperature (no comp):     ±5-15°C   → ±30-90m  (systematic)
QNH drift (5hr flight):    ±3 hPa    → ±24m     (weather)
GPS fusion correction:     -          → -10m     (drift prevention)
─────────────────────────────────────────────────────────────
TOTAL ERROR (typical):     ±30-60m   ✅ Acceptable for gliding
TOTAL ERROR (hot day):     ±90m      ⚠️ Noticeable but safe
```

### With QNH Monitoring Added
```
QNH calibration error:     ±8 hPa    → ±64m     (SRTM-based)
Barometric sensor noise:   ±0.5 hPa  → ±4m      (hardware limit)
Temperature (no comp):     ±5-15°C   → ±30-90m  (systematic)
QNH drift (5hr flight):    ±1 hPa    → ±8m      (pilot alerted & recals)
GPS fusion correction:     -          → -10m     (drift prevention)
─────────────────────────────────────────────────────────────
TOTAL ERROR (typical):     ±20-40m   ✅ Better
TOTAL ERROR (hot day):     ±80m      ⚠️ Still noticeable
```

### With Weather API Temperature Compensation
```
QNH calibration error:     ±8 hPa    → ±64m     (SRTM-based)
Barometric sensor noise:   ±0.5 hPa  → ±4m      (hardware limit)
Temperature (METAR):       ±2-5°C    → ±12-30m  (improved!)
QNH drift (5hr flight):    ±1 hPa    → ±8m      (monitored)
GPS fusion correction:     -          → -10m     (drift prevention)
─────────────────────────────────────────────────────────────
TOTAL ERROR (typical):     ±20-30m   ✅ Good
TOTAL ERROR (hot day):     ±40m      ✅ Much better
```

---

## ✅ Conclusion

### QNH Drift Monitoring: **WORTH IT** (Medium Priority)
- ✅ Catches weather changes (40-80m errors)
- ✅ Low effort (5 hours)
- ✅ Good aviation practice
- ✅ Pilots expect QNH updates
- ⚠️ Not safety-critical (implement after SRTM fix)

**Recommendation:** Implement, but not urgent.

---

### Temperature Compensation: **NOT WORTH IT** (Low Priority)

**Phone Temperature:** ❌ **DO NOT USE**
- Makes things WORSE (40-80m wrong direction)
- Phone temp ≠ ambient air temp

**Weather API Temperature:** ⚠️ **MAYBE**
- Only if adding weather features anyway
- Marginal benefit (20-30m improvement)
- 6 hours effort for small gain
- Accept ISA standard error for now

**Recommendation:** Skip unless you're already fetching weather data for other features (like cloud base, wind forecast, etc.).

---

## 🚀 Implementation Order

**Phase 1 (This Week - CRITICAL):**
1. SRTM-based QNH calibration (4-6 hours) ← **DO THIS FIRST**
2. Adaptive sensor fusion (1-2 hours)

**Phase 2 (Next Month - Nice to Have):**
3. QNH drift monitoring (5 hours)
4. Manual QNH UI (4 hours)

**Phase 3 (Future - Optional):**
5. Temperature compensation via weather API (6 hours)
   - Only if implementing weather features anyway
   - E.g., cloud base forecast, wind analysis, METAR display

**Never Implement:**
- ❌ Phone temperature sensor compensation (makes things worse)
- ❌ Accelerometer altitude integration (drift too severe)
- ❌ GNSS raw measurements (too complex, no benefit)

---

## 📚 References

- NOAA: Atmospheric Tides and Diurnal Pressure Variation
- FAA AIM 7.2: Barometric Altimeter Errors and Setting Procedures
- FAA AIM 7.3: Cold Temperature Barometric Altimeter Errors
- SKYbrary: Altimeter Temperature Error Correction
- University of Wyoming: Diurnal Pressure Variation Studies

---

**Summary:** QNH drift monitoring is worth implementing (medium priority). Temperature compensation is NOT worth the effort unless you're adding weather features anyway. Focus on SRTM QNH calibration first (critical fix).
