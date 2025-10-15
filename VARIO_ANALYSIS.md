# Variometer Calculation - Analysis & Improvement Plan

**Date:** 2025-10-11
**Status:** 🚧 Analysis Complete - Improvements Pending
**Priority:** 🔴 HIGH - Critical for gliding safety and competition accuracy

---

## 📋 Table of Contents

1. [Current Implementation Analysis](#current-implementation-analysis)
2. [Aviation Standards & Best Practices](#aviation-standards--best-practices)
3. [Problems Identified](#problems-identified)
4. [Proposed Improvements](#proposed-improvements)
5. [Implementation Plan](#implementation-plan)
6. [Testing & Validation](#testing--validation)

---

## 🔍 Current Implementation Analysis

### Architecture

```
Barometer Sensor (20Hz)
    ↓
BarometricAltitudeCalculator (converts pressure → altitude)
    ↓
AdvancedBarometricFilter (3-stage filtering)
    ├─ Stage 1: OutlierFilter (remove spikes)
    ├─ Stage 2: KalmanFilter (altitude + vertical speed estimation)
    └─ Stage 3: ExponentialSmoothing (display smoothing)
    ↓
Vertical Speed Output
```

### Current Kalman Filter Implementation

**File:** `KalmanFilter.kt` (lines 10-173)

**State Vector:**
- `stateVector[0]` = altitude (m)
- `stateVector[1]` = vertical velocity (m/s)

**Tuning Parameters:**
- Process noise: 0.04-0.12 (adaptive)
- Measurement noise: 1.0-2.5 (adaptive)
- Deadband: 0.05 m/s (10 fpm)

**Adaptive Behavior:**
- Thermal flying (|v| > 1.0 m/s): More responsive
- Cruising (|v| < 0.5 m/s): More stable
- Normal: Balanced

###Current Flow

```kotlin
// FlightDataCalculator.kt:150-151
val baroAltitude = filteredBaro?.displayAltitude ?: gps.altitude
val verticalSpeed = filteredBaro?.verticalSpeed ?: 0.0
```

---

## ✈️ Aviation Standards & Best Practices

### Variometer Types (FAI Standards)

#### 1. **Simple Variometer** (What we currently have)
- Measures altitude change via barometer
- **Problem**: Shows "stick thermals" (false lift from pilot control inputs)
- **Use**: Beginners only

#### 2. **Total Energy (TE) Variometer** ⭐ REQUIRED
- Compensates for airspeed changes
- Formula: `TE_reading = Δh + Δ(v²/2g) / Δt`
- **Benefit**: No more stick thermals
- **Use**: All competition flying

#### 3. **Netto Variometer** ⭐⭐ COMPETITION STANDARD
- TE variometer + glider polar compensation
- Shows ONLY air mass movement (zero in still air)
- Formula: `Netto = TE - polar_sink_rate(speed)`
- **Benefit**: Critical for final glide calculations
- **Use**: Competition required, cross-country

#### 4. **Super Netto / Relative Netto**
- Netto + MacCready compensation
- **Use**: Advanced competition tactics

### Key Research Findings

#### From AOPA & ILEC Research:
> "A poor compensation cannot be improved by additional damping. The faster the vario, the clearer the errors of compensation will show."

#### From Aviation Stack Exchange:
> "Total energy systems solve the stick thermal problem by compensating for the speed of the aircraft."

#### From Signal Processing Research:
> "With only an altitude sensor, the filter must have significant lag to process noisy data."

#### From Sensor Fusion Studies:
> "Kalman filters can estimate altitude and climbrate by fusing altitude and vertical acceleration data for lag-and-overshoot-free output."

---

## ❌ Problems Identified

### 🔴 CRITICAL: Missing Total Energy Compensation

**Problem:**
```kotlin
// Current: Only barometric altitude used
val verticalSpeed = filteredBaro?.verticalSpeed
```

**Impact:**
- False "lift" when pilot pulls back (converting kinetic → potential energy)
- False "sink" when pilot pushes forward (converting potential → kinetic energy)
- **Result**: Unusable for competition flying, misleading for pilots

**Example Scenario:**
1. Pilot flying at 80 km/h (22 m/s)
2. Pilot pulls back, slows to 60 km/h (17 m/s)
3. Glider gains 10m altitude but loses speed
4. **Current vario**: Shows +2 m/s climb (WRONG!)
5. **TE vario**: Shows 0 m/s (CORRECT - no lift, just energy conversion)

### 🟠 HIGH PRIORITY: No Netto Compensation

**Problem:** Vario doesn't account for glider's natural sink rate

**Impact:**
- Can't determine if air mass is rising or sinking
- Final glide calculations impossible
- Competition scoring inaccurate

**Example:**
- Glider sinking at 0.8 m/s at cruising speed
- Air mass rising at 0.5 m/s
- **Current vario**: Shows -0.8 m/s (pilot thinks it's sink!)
- **Netto vario**: Shows +0.5 m/s (pilot knows it's weak lift)

### 🟡 MEDIUM: Lag in Response

**Problem:** Only using barometric altitude (no accelerometer fusion)

**Impact:**
- ~1-2 second delay before vario responds
- Pilot misses thermal core centering opportunities
- Competitive disadvantage

**Research finding:**
> "A barometric vario based on pressure alone will be delayed, always, since altitude changes must occur before they can be detected."

### 🟡 MEDIUM: Deadband Too Conservative

**Current:** 0.05 m/s (10 fpm) deadband

**Problem:**
- Pilots can't detect weak lift (0.2-0.5 m/s)
- Misses thermals in weak conditions

**Recommended:** 0.02-0.03 m/s (4-6 fpm) for gliding

### 🟢 GOOD: Kalman Filter Implementation

**What's working:**
- ✅ Proper 2-state Kalman filter (altitude + velocity)
- ✅ Adaptive parameters based on flight conditions
- ✅ 3-stage filtering (outlier → Kalman → smoothing)
- ✅ Confidence calculation
- ✅ Innovation-based quality assessment

---

## 🎯 Proposed Improvements

### Priority 1: Add Total Energy Compensation

**Algorithm:**

```kotlin
fun calculateTotalEnergy(
    baroVerticalSpeed: Double,    // m/s from Kalman filter
    currentAirspeed: Double,       // m/s (IAS or TAS)
    previousAirspeed: Double,      // m/s
    deltaTime: Double              // seconds
): Double {
    // Constants
    val g = 9.81  // m/s² (gravity)

    // Calculate kinetic energy change
    val deltaKineticEnergy = (currentAirspeed * currentAirspeed -
                              previousAirspeed * previousAirspeed) / (2 * g)

    // TE vertical speed = baro V/S + change in kinetic energy per unit time
    val teVerticalSpeed = baroVerticalSpeed + (deltaKineticEnergy / deltaTime)

    return teVerticalSpeed
}
```

**Implementation Notes:**
- Use GPS speed as approximation for airspeed (phones don't have pitot tubes)
- GPS speed ≈ TAS (True Airspeed) at gliding altitudes
- Accuracy: ±0.5 m/s (acceptable for recreational flying)

### Priority 2: Add Netto Compensation

**Algorithm:**

```kotlin
fun calculateNetto(
    teVerticalSpeed: Double,      // m/s from TE calculation
    currentAirspeed: Double,      // m/s (GPS speed)
    polarCurve: GliderPolar       // Glider performance data
): Double {
    // Get glider's natural sink rate at current speed
    val glidersinkRate = polarCurve.getSinkRate(currentAirspeed)

    // Netto = TE minus glider sink
    val nettoVerticalSpeed = teVerticalSpeed - gliderSinkRate

    return nettoVerticalSpeed
}
```

**Glider Polar Curve:**
```kotlin
data class GliderPolar(
    val speeds: List<Double>,      // km/h
    val sinkRates: List<Double>    // m/s (negative for sink)
) {
    fun getSinkRate(speedKmh: Double): Double {
        // Linear interpolation between points
        // ... implementation
    }
}

// Example: Standard Club Class glider
val clubClassPolar = GliderPolar(
    speeds = listOf(60.0, 80.0, 100.0, 120.0, 140.0),
    sinkRates = listOf(-1.0, -0.8, -0.9, -1.2, -1.8)
)
```

### Priority 3: Reduce Deadband

```kotlin
// Current (line 88 in KalmanFilter.kt)
if (abs(filteredVelocity) < 0.05) {  // 0.05 m/s = 10 fpm
    filteredVelocity = 0.0
}

// ✅ IMPROVED
if (abs(filteredVelocity) < 0.02) {  // 0.02 m/s = 4 fpm (gliding standard)
    filteredVelocity = 0.0
}
```

**Rationale:** Glider pilots need to detect weak lift (0.2-0.5 m/s) to stay airborne

### Priority 4: Accelerometer Fusion (FUTURE)

**Benefits:**
- Reduce lag from ~1-2 seconds to ~0.3-0.5 seconds
- Earlier thermal detection
- Smoother response

**Implementation:**
```kotlin
// 3-state Kalman filter: [altitude, velocity, acceleration]
// Fuse barometer (altitude) + accelerometer (vertical acceleration)
```

**Complexity:** High (requires careful tuning)
**Recommendation:** Phase 2 improvement after TE/Netto working

### Priority 5: Configurable Damping Modes

Allow pilots to choose vario response:

```kotlin
enum class VarioMode {
    FAST,      // 0.5s damping (thermal centering)
    NORMAL,    // 1.0s damping (default)
    SLOW       // 2.0s damping (cruising, less jumpy)
}
```

**Implementation:**
- Adjust Kalman filter's `processNoise` and `measurementNoise`
- Store user preference in settings

---

## 📐 Implementation Plan

### Phase 1: Total Energy Compensation (CRITICAL)

**Files to Modify:**
1. `FlightDataCalculator.kt`
   - Add `calculateTotalEnergy()` method
   - Track previous GPS speed
   - Replace simple V/S with TE V/S

2. `SensorData.kt`
   - Add `teVerticalSpeed` field to `CompleteFlightData`

3. `CardDefinitions.kt`
   - Update vario card to display TE V/S
   - Add "TE" indicator

**Estimated Time:** 2-3 hours
**Testing:** Compare with external vario (if available)

### Phase 2: Netto Compensation (HIGH PRIORITY)

**Files to Modify:**
1. Create `GliderPolar.kt`
   - Define polar curve data structure
   - Interpolation algorithm
   - Pre-defined polars for common gliders

2. `FlightDataCalculator.kt`
   - Add `calculateNetto()` method
   - Load user's glider polar from settings

3. `CardDefinitions.kt`
   - Add Netto vario card type
   - Update existing vario card to show mode

**Estimated Time:** 3-4 hours
**Testing:** Netto should read ~0 in still air during flight

### Phase 3: Deadband & Damping (MEDIUM)

**Files to Modify:**
1. `KalmanFilter.kt`
   - Reduce deadband from 0.05 to 0.02 m/s
   - Add configurable damping modes

2. Add settings UI for vario mode selection

**Estimated Time:** 1-2 hours
**Testing:** Verify weak lift detection (0.2-0.5 m/s)

### Phase 4: Accelerometer Fusion (FUTURE)

**Complexity:** High
**Benefit:** Reduced lag
**Recommendation:** Defer until Phase 1-3 validated

---

## 🧪 Testing & Validation

### Test Scenarios

#### Test 1: Stick Thermal Detection (TE Validation)
**Setup:**
1. Level flight at constant altitude (no actual lift)
2. Pilot pulls back sharply (convert speed → altitude)

**Expected Results:**
- ❌ **Current vario**: Shows false lift
- ✅ **TE vario**: Shows zero (no air mass movement)

#### Test 2: Netto in Still Air
**Setup:**
1. Fly straight at cruising speed (e.g., 90 km/h)
2. No thermals (still air)

**Expected Results:**
- ❌ **Simple vario**: Shows -0.8 m/s (glider sink)
- ✅ **Netto vario**: Shows 0 m/s (air mass not moving)

#### Test 3: Weak Thermal Detection
**Setup:**
1. Fly through weak thermal (0.3 m/s lift)

**Expected Results:**
- ❌ **Current deadband (0.05)**: Might miss thermal
- ✅ **Improved deadband (0.02)**: Detects thermal

#### Test 4: Response Time (Future with Accel)
**Setup:**
1. Enter strong thermal (2 m/s lift)
2. Measure time to detect

**Expected Results:**
- ❌ **Baro only**: 1-2 second delay
- ✅ **Baro + Accel**: 0.3-0.5 second delay

### Validation Metrics

| Metric | Current | Target | Method |
|--------|---------|--------|--------|
| **Stick thermal error** | High | Zero | Pitch maneuver test |
| **Netto accuracy** | N/A | ±0.1 m/s | Still air cruise test |
| **Weak lift detection** | >0.05 m/s | >0.02 m/s | Threshold test |
| **Response lag** | 1-2s | 1-2s (Phase 1-3), 0.5s (Phase 4) | Step input test |
| **Noise level** | <0.02 m/s | <0.02 m/s | Stationary ground test |

---

## 📚 References

### Aviation Standards
- FAI Sporting Code Section 3 (Gliding)
- OSTIV Variometer Specifications
- AOPA: "How it works: Total energy variometer"
- ILEC GmbH: "Total Energy Compensation in Practice" (Brozel, 2002)

### Technical Resources
- Wikipedia: Variometer
- Aviation Stack Exchange: Energy Variometer discussions
- Signal Processing Stack Exchange: Kalman filter for barometric vario
- GitHub: har-in-air/Kalmanfilter_altimeter_vario

### Research Papers
- "Measurement data fusion with cascaded Kalman and complementary filter in the flight parameter indicator for hang-glider and paraglider" (ScienceDirect, 2018)
- Niklas Löfgren: "Optimal Variometer" (niklaslofgren.net/gliding)

---

## ✅ Success Criteria

### Phase 1 (TE) Complete When:
- [ ] TE calculation implemented
- [ ] Stick thermal test passes (no false lift)
- [ ] Display shows "TE" mode indicator
- [ ] Code reviewed and documented

### Phase 2 (Netto) Complete When:
- [ ] Netto calculation implemented
- [ ] Glider polar database created
- [ ] Still air test passes (netto ≈ 0)
- [ ] User can select glider type in settings

### Phase 3 (Damping) Complete When:
- [ ] Deadband reduced to 0.02 m/s
- [ ] Weak lift detection verified (0.2-0.5 m/s)
- [ ] Damping modes selectable
- [ ] Settings persist across app restarts

---

## 🚀 Quick Start (For Next Session)

1. **Read this document** completely
2. **Implement Phase 1** (Total Energy)
3. **Test** with stick thermal scenario
4. **Deploy** and validate with real flight (if possible)
5. **Iterate** based on pilot feedback

---

**Status:** Ready for implementation
**Next Action:** Implement Phase 1 (Total Energy Compensation)
**Owner:** Development team
**Priority:** 🔴 Critical for competition accuracy
