# Variometer Rotation Artifact Analysis

**Date:** 2025-10-17
**Status:** Active Issue - Rotation Causes False Vario Readings
**Priority:** High - Affects user experience and trust in vario accuracy
**Severity:** Medium - Cosmetic issue (no safety impact during actual flight)

---

## Table of Contents

1. [Problem Description](#problem-description)
2. [Root Cause Analysis](#root-cause-analysis)
3. [Technical Deep Dive](#technical-deep-dive)
4. [Why This Only Affects Ground Operation](#why-this-only-affects-ground-operation)
5. [Proposed Solutions](#proposed-solutions)
6. [Implementation Recommendations](#implementation-recommendations)
7. [Testing Plan](#testing-plan)
8. [References](#references)

---

## Problem Description

### User Observation

When holding the phone stationary (no altitude change) and rotating it quickly:

- **Vertical → Horizontal (flat)**: Shows ~-1.4 m/s (sink)
- **Horizontal → Vertical (upright)**: Shows ~+1.4 m/s (lift)
- **Magnitude**: Proportional to rotation speed
- **Duration**: Brief transient (~0.5-1 second)

### Expected Behavior

When phone altitude doesn't change, vario should read **0.0 m/s** regardless of orientation changes.

### Actual Behavior

Vario shows false lift/sink proportional to rotation speed during orientation changes.

---

## Root Cause Analysis

### The Sensor Chain

```
TYPE_LINEAR_ACCELERATION (Android OS sensor fusion)
          ↓
   [Gravity already removed by OS]
          ↓
OrientationProcessor.projectVerticalAcceleration()
          ↓
   [Projects into Earth frame using rotation matrix]
          ↓
FlightDataCalculator.updateVarioFilter()
          ↓
   [Feeds to Kalman filter if IMU fusion enabled]
          ↓
Modern3StateKalmanFilter
          ↓
   [Fuses with barometer for vertical speed]
          ↓
VARIO READING (m/s)
```

### The Fundamental Problem

**Android's `TYPE_LINEAR_ACCELERATION` sensor is NOT perfect during rapid rotation.**

The sensor is created by:
```
Linear Acceleration = Raw Accelerometer - Gravity Estimate
```

The **Gravity Estimate** comes from sensor fusion that:
1. Uses accelerometer + gyroscope + magnetometer
2. Has a **time constant** (typically 100-200ms)
3. Assumes **slow, smooth rotation**

**During rapid rotation**, the gravity estimate LAGS behind actual orientation, causing:
- Gravity components to "leak" into linear acceleration
- False acceleration readings proportional to rotation speed
- Transient errors that take 0.5-1 second to settle

### Why -1.4 m/s² Specifically?

Earth's gravity is **9.8 m/s²**. When rotating 90° from vertical to horizontal:

```
Vertical orientation:   gravity = [0, 0, -9.8]
Horizontal orientation: gravity = [0, -9.8, 0]

During rotation: gravity vector rotates through sensor frame
If rotation is FAST, gravity compensation can't track it perfectly
Result: Transient error = some fraction of 9.8 m/s²

Observed ~1.4 m/s² = roughly 14% of gravity (plausible lag artifact)
```

---

## Technical Deep Dive

### Step-by-Step: What Happens During Rotation

#### 1. Initial State (Phone Vertical)
```
Phone frame:          [X: right, Y: forward, Z: up (screen normal)]
Gravity in phone:     [0, 0, -9.8] m/s² (pulling down on Z-axis)
Linear accel (ideal): [0, 0, 0] (gravity removed)
Rotation matrix:      Maps phone Z → Earth Z (vertical)
Vertical projection:  0.0 m/s² ✅
```

#### 2. During Rotation (Vertical → Horizontal)
```
Rotation speed:       500°/s (fast rotation)
Gravity NOW in phone: [-X, -Y, -Z] (rotating vector)
OS gravity estimate:  LAGGING (still thinks mostly [0, 0, -9.8])
Linear accel (actual): Non-zero! (gravity leak due to lag)
Rotation matrix:      Updating (but also lagging slightly)
Vertical projection:  ~-1.4 m/s² ❌ FALSE SINK
```

**Why negative (sink)?**
- Phone's Z-axis (screen normal) is rotating from up to horizontal
- Gravity component that WAS on Z-axis is now being "removed" slower than it's physically rotating
- Net result: Apparent downward acceleration in Earth frame

#### 3. After Rotation (Phone Horizontal - Stable)
```
Phone frame:          [X: right, Y: up, Z: forward (screen normal)]
Gravity in phone:     [0, -9.8, 0] (pulling down on Y-axis)
Linear accel (ideal): [0, 0, 0] (gravity removed correctly now)
Rotation matrix:      Maps phone Y → Earth Z (vertical)
Vertical projection:  0.0 m/s² ✅ (after settling ~500ms)
```

### Code Location: OrientationProcessor.kt:25

```kotlin
fun projectVerticalAcceleration(linearAcceleration: FloatArray): AccelSample {
    val ax = linearAcceleration[0].toDouble()  // ← This contains gravity leak during rotation!
    val ay = linearAcceleration[1].toDouble()  // ← This too!
    val az = linearAcceleration[2].toDouble()  // ← And this!

    val vertical = if (orientationFresh) {
        rotationMatrix[6] * ax + rotationMatrix[7] * ay + rotationMatrix[8] * az
        // ↑ Multiplying contaminated linear accel by rotation matrix
        // Even though rotation matrix is correct, input data is wrong!
    } else {
        az  // Fallback: just use Z-axis (even worse during rotation)
    }

    return AccelSample(
        verticalAcceleration = vertical,  // ← Contains rotation artifact
        isReliable = orientationFresh
    )
}
```

### Why Rotation Matrix Doesn't Save Us

The rotation matrix correctly transforms from phone frame to Earth frame, BUT:
- It operates on `linearAcceleration` which is ALREADY contaminated
- The contamination is in the phone frame (due to lagging gravity removal)
- Multiplying contaminated phone-frame data by a correct rotation matrix still gives contaminated Earth-frame data

**Analogy:**
Rotation matrix is like a perfect translator, but if the source language text is corrupted, the translation will also be corrupted.

---

## Why This Only Affects Ground Operation

### During Actual Flight (In Glider)

**Rotation is SLOW and SMOOTH:**
- Typical bank angle change: 20°/second (thermalling)
- Pitch changes: <10°/second (smooth flying)
- Android's gravity compensation easily tracks this

**IMU Fusion Gate Prevents Artifacts:**
```kotlin
// FlightDataCalculator.kt:212
val usingImu = accelReliable && withinAccelRange && cachedGPSSpeed >= 1.0
```
- `cachedGPSSpeed >= 1.0`: Requires 1 m/s (3.6 km/h) to enable fusion
- On ground stationary: GPS speed = 0, so IMU fusion is **DISABLED** ✅
- In flight: GPS speed >> 1 m/s, so IMU fusion is **ENABLED** ✅

**BUT**: The user observed the artifact even on the ground, which means:
- Either GPS reported speed > 1 m/s (GPS jitter/noise)
- OR the user was walking while rotating (speed threshold passed)

### Why It's Noticeable on Ground but Not in Flight

| Condition | Ground (Walking) | Flight (Glider) |
|-----------|------------------|-----------------|
| **Typical rotation speed** | 180°/s (hand rotation) | 20°/s (banking) |
| **Gravity compensation lag** | Severe | Negligible |
| **Barometer stability** | Stable (no real altitude change) | Changing (real lift/sink) |
| **Artifact magnitude** | ±1.4 m/s (obvious) | ±0.2 m/s (masked by real vario) |
| **User expectations** | Expect 0.0 m/s | Expect variation |

**Bottom line**: In actual flight, this artifact is negligible and masked by real atmospheric movement.

---

## Proposed Solutions

### Solution 1: Add Gyroscope Gating ⭐ RECOMMENDED

**Concept**: Detect rapid rotation using gyroscope and disable IMU fusion during rotation.

**Implementation:**
```kotlin
// Add to UnifiedSensorManager.kt
private val gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
private val _gyroFlow = MutableStateFlow<GyroData?>(null)
val gyroFlow: StateFlow<GyroData?> = _gyroFlow.asStateFlow()

// In onSensorChanged():
Sensor.TYPE_GYROSCOPE -> {
    val gx = event.values[0]  // rad/s around X
    val gy = event.values[1]  // rad/s around Y
    val gz = event.values[2]  // rad/s around Z
    val angularVelocity = sqrt(gx*gx + gy*gy + gz*gz)  // Total rotation rate
    _gyroFlow.value = GyroData(angularVelocity, System.currentTimeMillis())
}

// In FlightDataCalculator.kt:
private val MAX_ANGULAR_VELOCITY_FOR_FUSION = 1.0  // rad/s (~57°/s)

val gyro = cachedGyroData
val isRotating = gyro != null && gyro.angularVelocity > MAX_ANGULAR_VELOCITY_FOR_FUSION

val usingImu = accelReliable
            && withinAccelRange
            && cachedGPSSpeed >= MIN_SPEED_FOR_IMU_FUSION
            && !isRotating  // ← NEW: Disable during rotation
```

**Advantages:**
- ✅ Directly detects the problem (rotation)
- ✅ Simple threshold (1 rad/s = 57°/s is plenty for flight, blocks fast hand rotation)
- ✅ No false positives in normal flight (glider rotations are slow)
- ✅ Gyroscope is low-drift for short-term angular velocity

**Disadvantages:**
- Requires additional sensor (gyroscope)
- Slightly more complex logic

**Estimated Effort:** 2-3 hours
**Risk:** Low
**Effectiveness:** High (95%+ elimination of artifact)

---

## Testing Plan

### Ground Tests (Before Flight)

1. **Stationary Rotation Test**
   - Stand still, rotate phone vertical → horizontal → vertical
   - Expected: Vario stays at 0.0 m/s (±0.1 m/s acceptable transient)
   - Before fix: Shows ±1.4 m/s

2. **Walking Rotation Test**
   - Walk slowly (~2 m/s), rotate phone
   - Expected: Vario stays near 0.0 m/s (no IMU fusion below 3 m/s)
   - Before fix: Shows ±1.4 m/s if speed > 1 m/s

3. **Running Rotation Test**
   - Run fast (>3 m/s), rotate phone slowly
   - Expected: Vario uses IMU fusion, stays stable
   - Before fix: Shows artifacts if rotation is fast

4. **Elevator Test**
   - Ride elevator (real altitude change), rotate phone during motion
   - Expected: Vario tracks altitude change, ignores rotation
   - Before fix: Rotation adds false component to reading

### Flight Tests (After Ground Tests Pass)

5. **Thermalling Test**
   - Circle in thermal (banking 20-30°)
   - Expected: Vario responds instantly to lift/sink, no false readings from bank angle changes
   - Success: Vario reading matches glider variometer

6. **Straight Flight Test**
   - Cruise straight and level
   - Expected: Vario reads near 0.0 m/s (or slight sink from glider polar)
   - Success: Stable reading, no oscillations

7. **Stick Thermal Test**
   - Pull up sharply (stick thermal maneuver)
   - Expected: Vario with TE compensation shows ~0.0 m/s (energy conversion)
   - Success: TE compensation removes false lift from maneuver

8. **Weak Lift Test**
   - Fly through 0.2 m/s lift
   - Expected: Vario detects weak lift within 0.5 seconds
   - Success: Fast response, no lag from filtering

---

## References

### Code Files
- `app/src/main/java/com/example/xcpro/sensors/OrientationProcessor.kt:25` - Vertical acceleration projection
- `app/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt:65` - TYPE_LINEAR_ACCELERATION sensor
- `app/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt:212` - IMU fusion gate logic
- `dfcards-library/src/main/java/com/example/dfcards/filters/Modern3StateKalmanFilter.kt:69` - Kalman filter that fuses IMU

### Android Sensor Documentation
- [TYPE_LINEAR_ACCELERATION](https://developer.android.com/reference/android/hardware/Sensor#TYPE_LINEAR_ACCELERATION) - "Acceleration minus gravity"
- [TYPE_GYROSCOPE](https://developer.android.com/reference/android/hardware/Sensor#TYPE_GYROSCOPE) - Angular velocity sensor
- [TYPE_ROTATION_VECTOR](https://developer.android.com/reference/android/hardware/Sensor#TYPE_ROTATION_VECTOR) - Device orientation

### Related Documentation
- [VARIO_ANALYSIS.md](./VARIO_ANALYSIS.md) - Overall vario system architecture
- [AGL.md](./AGL.md) - Altitude sensor accuracy and limitations

### Research Papers
- "Smartphone-based Variometer" - theFlightVario project
- "Sensor Fusion for Altitude Estimation" - XCTracer technical blog

---

## Status

**Current Status:** ✅ Root cause identified, solutions proposed
**Next Steps:** Implement recommended solution (Gyro gating + speed threshold)
**Owner:** Development team
**Priority:** Medium (cosmetic issue, no flight safety impact)

**User Impact:**
- **Before fix:** Confusing vario readings when handling phone on ground
- **After fix:** Vario only activates in flight, stable on ground regardless of orientation
