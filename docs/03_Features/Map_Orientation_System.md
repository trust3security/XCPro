# Map Orientation System - Technical Reference

**Last Updated:** 2026-01-04
**Status:** ✅ Production
**Critical:** Read this BEFORE modifying orientation code

---

## 📋 Table of Contents

- [Overview](#overview)
- [The Three Orientation Modes](#the-three-orientation-modes)
- [Data Sources and Requirements](#data-sources-and-requirements)
- [Rotation Behavior](#rotation-behavior)
- [Aircraft Icon Behavior](#aircraft-icon-behavior)
- [Camera and Centering](#camera-and-centering)
- [UI Elements](#ui-elements)
- [Speed Thresholds and Fallback Logic](#speed-thresholds-and-fallback-logic)
- [Common Mistakes - DO NOT DO THESE](#common-mistakes---do-not-do-these)
- [Performance Considerations](#performance-considerations)
- [Testing Requirements](#testing-requirements)

---

## Overview

The map orientation system controls how the map rotates relative to the aircraft icon. The aircraft icon is **ALWAYS FIXED** at screen center (or 65% down), and the **MAP ROTATES** beneath it.

**Core Principle**: The pilot is stationary at screen center. The world moves and rotates around them.

**Implementation Files**:
- `MapOrientationManager.kt` - Main controller
- `OrientationDataSource.kt` - Sensor data collection
- `CompassWidget.kt` - Visual indicator
- `MapOrientationPreferences.kt` - Persistence

---

## The Three Orientation Modes

### 1. NORTH_UP (Traditional Map View)

**What It Does**:
- Map stays fixed with North pointing up
- Traditional paper map orientation
- No rotation applied to map

**Data Source**:
- None required (0° rotation always)

**When To Use**:
- Ground operations
- Route planning
- When compass heading not critical

**Compass Widget**:
- Hidden by default in this mode
- Can be shown for mode switching

**Rotation Value**: `0.0°` (constant)

---

### 2. TRACK_UP (GPS Direction)

**What It Does**:
- Map rotates so GPS track direction points up
- Aircraft icon always "flies up the screen"
- Map shows where you're moving, not where you're pointing

**Data Source**:
- GPS track bearing from `RealTimeFlightData.track`
- Requires GPS fix and movement

**When To Use**:
- Active flight navigation
- Following a course/route
- When you want to see "where I'm going"

**Compass Widget**:
- Visible and rotating
- Shows "T" mode indicator
- Needle points to magnetic North

**Rotation Value**: `-GPS_TRACK` (map counter-rotates to GPS bearing)

**Critical Speed Threshold**: `2.0 knots` (3.7 km/h)
- Below 2kts: Holds last valid bearing (prevents spinning when stationary)
- Above 2kts: Uses live GPS track

---

### 3. HEADING_UP (Compass Direction)

**What It Does**:
- Map rotates so device's magnetic heading points up
- Aircraft icon points in direction device is facing
- Map shows where you're pointing, not where you're moving

**Data Source**:
- Magnetometer (compass) heading from sensor fusion
- Falls back to GPS track if magnetometer unavailable

**When To Use**:
- Thermal soaring (pointing ≠ moving direction)
- Circling/orbiting
- When you want to see "where I'm pointing"

**Compass Widget**:
- Visible and rotating
- Shows "H" mode indicator
- Needle points to magnetic North

**Rotation Value**: `-MAGNETIC_HEADING` (map counter-rotates to compass bearing)

**Sensors Required**:
- Magnetometer (TYPE_MAGNETIC_FIELD)
- Accelerometer (TYPE_ACCELEROMETER)

**Fallback Logic**:
1. Use magnetometer heading if available and valid
2. Else use GPS track if speed > 2kts
3. Else hold last valid bearing

---

### 4. WIND_UP (Wind-Aligned Map)

**What It Does**:
- Map rotates so the active wind vector points up (upwind = top of screen)
- Aircraft icon still shows actual track, so you see drift relative to wind immediately

**Data Source**:
- Wind direction/speed solved by `WindSensorFusionRepository` via `WindState`
  (direction stored as "from")
- Falls back to last-known value if wind confidence drops

**When To Use**:
- Final glides in strong drift
- Planning AAT cylinder entries: quickly see crab angle vs. airflow
- Training to read wind corrections without interpreting separate overlays

**Compass Widget**:
- Shows "W" badge when active
- Source badge displays `W` or `LK` when falling back to last-known bearing

**Rotation Value**: `-(windDirectionFrom + 180°)` (convert FROM -> TO vector, then counter-rotate)

**Fallback Logic**:
1. Use live wind vector when speed ≥ 0.5 m/s
2. Else hold last valid wind bearing (compass badge switches to `LK`)

---

## Data Sources and Requirements

### NORTH_UP Data Requirements
```kotlin
// No data sources required
bearing = 0.0  // Always
```

### TRACK_UP Data Requirements
```kotlin
// Requires GPS data from RealTimeFlightData
track: Double           // GPS track angle (0-360°)
groundSpeed: Double     // Speed in knots
isGPSFixed: Boolean     // GPS has valid fix

// Validation
isValid = isGPSFixed && groundSpeed >= 2.0
```

### HEADING_UP Data Requirements
```kotlin
// Primary: Magnetometer + Accelerometer
magneticHeading: Double     // From sensor fusion (0-360°)
hasValidHeading: Boolean    // Sensors working correctly

// Fallback: GPS track
track: Double               // GPS track angle
groundSpeed: Double         // Speed in knots
isGPSFixed: Boolean        // GPS has valid fix

// Validation
isValid = hasValidHeading || (isGPSFixed && groundSpeed >= 2.0)
```

---

## Rotation Behavior

### Map Rotation (What Moves)

**All modes rotate the MAP, never the aircraft icon:**

```kotlin
// MapScreen.kt - Camera bearing rotation
val cameraBearing = when (orientation.mode) {
    MapOrientationMode.NORTH_UP -> 0.0
    MapOrientationMode.TRACK_UP -> -orientation.bearing  // Counter-rotate to track
    MapOrientationMode.HEADING_UP -> -orientation.bearing  // Counter-rotate to heading
}

// Apply to camera
mapView.mapLibreMap?.cameraState?.let { camera ->
    newCameraPosition = CameraPosition(
        target = gpsPosition,
        bearing = cameraBearing,  // ← This rotates the MAP
        zoom = camera.zoom
    )
}
```

**Why Negative Bearing?**
- GPS says "heading 90° East"
- To make East point "up" on screen, rotate map -90° (counter-clockwise)
- Aircraft icon stays pointing up visually

### Update Frequencies

**MapOrientationManager**:
- Sample rate: `66ms` (~15Hz)
- Prevents excessive updates

**OrientationDataSource**:
- GPS updates: Continuous via FlightDataManager
- Magnetometer: `50ms` (~20Hz)
- Accelerometer: `50ms` (~20Hz)

**CompassWidget Animation**:
- NORTH_UP: `0ms` (instant, no animation)
- TRACK_UP/HEADING_UP: `300ms` smooth animation

### Smoothing Filters

**Magnetometer Low-Pass Filter**:
```kotlin
// OrientationDataSource.kt
ALPHA = 0.8f  // Filter constant
gravity[i] = ALPHA * gravity[i] + (1 - ALPHA) * newValue[i]
geomagnetic[i] = ALPHA * geomagnetic[i] + (1 - ALPHA) * newValue[i]
```

**Bearing Transition Smoothing**:
```kotlin
// Handles 360°/0° boundary crossing
fun smoothBearingTransition(oldBearing, newBearing):
    diff = handleBoundary(newBearing - oldBearing)  // -180 to +180
    smoothedDiff = diff * 0.3  // 30% weight to new value
    return (oldBearing + smoothedDiff + 360) % 360
```

---

## Aircraft Icon Behavior

### CRITICAL: Icon is NOT a Map Element

**The aircraft icon MUST be:**
- ✅ Fixed at screen coordinates (center or 65% down)
- ✅ Drawn as Compose overlay, NOT MapLibre SymbolLayer
- ✅ Only rotation changes based on mode
- ❌ NEVER positioned by lat/lng on map
- ❌ NEVER moves around screen

**Current Implementation Issue**:
- Icon positioned at camera center via GeoJSON
- Causes flashing/flickering on map updates
- See "How a Gliding App Should Work" in CLAUDE.md

**Icon Rotation** (Different from Map Rotation):
```kotlin
// Icon can rotate independently of map
// For drift indication or aircraft attitude
iconRotation = when (mode) {
    NORTH_UP -> track  // Show GPS direction
    TRACK_UP -> 0.0    // Always points up
    HEADING_UP -> track - heading  // Show drift angle
}
```

---

## Camera and Centering

### Camera Position Updates

**All modes center camera on GPS position:**

```kotlin
// MapScreen.kt - LocationManager effect
LaunchedEffect(gpsPosition, orientation.bearing, orientation.mode) {
    // Update EVERY mode, not just specific ones
    mapView.mapLibreMap?.moveCamera(  // ← moveCamera, NOT animateCamera
        CameraUpdateFactory.newCameraPosition(
            CameraPosition(
                target = gpsPosition,       // ← GPS position
                bearing = cameraBearing,     // ← Mode-specific rotation
                zoom = currentZoom           // ← User-controlled
            )
        )
    )
}
```

**Update Frequency**: `100ms` (10Hz) via LaunchedEffect

**Critical Requirements**:
- ✅ Use `moveCamera()` for instant updates (smooth 10Hz tracking)
- ❌ NEVER use `animateCamera()` (causes lag and stuttering)
- ✅ Preserve user's zoom level
- ❌ NEVER auto-zoom

### User Panning Behavior

**After user pans map:**
1. ✅ Show "Return to Center" button
2. ✅ Stop automatic camera updates
3. ✅ User manually taps button to return
4. ❌ NEVER auto-return after timeout

**Pan Detection**:
```kotlin
// MapScreen.kt
map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
    override fun onMoveBegin(detector: MoveGestureDetector) {
        showReturnButton = true  // Show button
        // Do NOT start countdown timer!
    }
})
```

**Return Button Action**:
```kotlin
// User explicitly requests return
IconButton(onClick = {
    showReturnButton = false
    // Camera updates resume automatically
}) { Icon(Icons.Default.MyLocation) }
```

---

## UI Elements

### Compass Widget

**Visibility Rules**:
```kotlin
when (orientation.mode) {
    NORTH_UP -> false  // Hidden by default
    TRACK_UP -> true   // Shows "T" indicator
    HEADING_UP -> true // Shows "H" indicator
}
```

**Widget Appearance**:
- Size: `48.dp` circular widget
- Red needle points to North
- White needle points to South
- Rotates opposite to map rotation
- Click to toggle modes

**Mode Indicators**:
- Track-Up: "T" in blue at bottom
- Heading-Up: "H" in blue at bottom
- North-Up: No indicator

### Flight Mode Indicator

**Position**: Top-right corner
**Z-Index**: `5.0f`
**Shows**: Current flight mode (Cruise/Thermal/Final Glide)
**Independent**: Not affected by orientation mode

### Return to Center Button

**Position**: Near compass widget
**Visibility**: Only when user pans map
**Action**: Centers camera on GPS, resumes tracking
**Z-Index**: Should be above map elements

---

## Speed Thresholds and Fallback Logic

### Speed Threshold Constants

```kotlin
// MapOrientationManager.kt
MIN_SPEED_FOR_TRACK_KT = 2.0  // Minimum speed for valid GPS track
BEARING_CHANGE_THRESHOLD = 5.0  // Degrees - minimum change to update
```

**Why 2.0 knots?**
- GPS track unreliable when stationary
- Prevents "spinning map" when parked
- Reduced from 5kts to be responsive during slow flight

### Fallback Logic Flow

**TRACK_UP Mode**:
```
1. Is GPS fixed? NO → Keep last bearing
2. Speed >= 2kts? NO → Keep last bearing
3. Speed >= 2kts? YES → Use GPS track ✓
```

**HEADING_UP Mode**:
```
1. Magnetometer available? YES → Use magnetic heading ✓
2. Magnetometer available? NO → Check GPS
   3a. GPS fixed AND speed >= 2kts? YES → Use GPS track ✓
   3b. GPS fixed AND speed >= 2kts? NO → Keep last bearing
```

### Last Valid Bearing Hold

**Purpose**: Prevent orientation oscillation at low speeds

```kotlin
// MapOrientationManager.kt
private var lastValidBearing = 0.0

if (isValid) {
    lastValidBearing = bearing  // Update when valid
}

val finalBearing = if (isValid) bearing else lastValidBearing
```

**When Held**:
- GPS speed drops below 2kts
- Magnetometer becomes unreliable
- Sensor data temporarily unavailable

---

## Common Mistakes - DO NOT DO THESE

### ❌ MISTAKE 1: Auto-Return After Pan

```kotlin
// ❌ FORBIDDEN - Causes auto-return bug
LaunchedEffect(lastUserPanTime) {
    delay(5000)  // Wait 5 seconds
    if (showReturnButton) {
        showReturnButton = false  // Auto-hides button
        // Camera starts tracking again - USER LOSES CONTROL
    }
}
```

**Why Wrong**: Pilots pan to view task areas. Auto-return removes their control.

**✅ CORRECT**: Only manual return via button press.

---

### ❌ MISTAKE 2: Confusing Map Rotation with Icon Rotation

```kotlin
// ❌ WRONG - Rotating icon instead of map
aircraftIcon.rotation = -orientation.bearing

// ✅ CORRECT - Rotate map, icon stays fixed
cameraPosition.bearing = -orientation.bearing
```

**Why Wrong**: Icon should be fixed on screen. Map rotates beneath it.

---

### ❌ MISTAKE 3: Using animateCamera()

```kotlin
// ❌ WRONG - Causes lag and stuttering
map.animateCamera(newPosition, 1000)  // 1 second animation

// ✅ CORRECT - Instant updates for smooth tracking
map.moveCamera(newPosition)  // No animation, butter-smooth at 10Hz
```

**Why Wrong**: 10Hz updates + 1s animation = visual mess.

---

### ❌ MISTAKE 4: Auto-Zoom

```kotlin
// ❌ WRONG - Surprising zoom changes
if (speedIncreased) {
    zoom = 13.0  // Zoom out automatically - USER LOSES CONTROL
}

// ✅ CORRECT - Only user controls zoom
// Never change zoom programmatically after initial setup
```

**Why Wrong**: Pilots set zoom for their workflow. Don't override.

---

### ❌ MISTAKE 5: Wrong Data Source

```kotlin
// ❌ WRONG - Using magnetometer for TRACK_UP
when (mode) {
    TRACK_UP -> magneticHeading  // NO! Should be GPS track
}

// ✅ CORRECT - Mode-specific data sources
when (mode) {
    NORTH_UP -> 0.0
    TRACK_UP -> gpsTrack
    HEADING_UP -> magneticHeading (or gpsTrack fallback)
}
```

**Why Wrong**: TRACK_UP = where moving, HEADING_UP = where pointing.

---

### ❌ MISTAKE 6: Not Handling Sensor Unavailability

```kotlin
// ❌ WRONG - Crashes if magnetometer missing
val bearing = sensorData.magneticHeading  // NPE if sensor unavailable

// ✅ CORRECT - Fallback to GPS
val bearing = if (sensorData.hasValidHeading) {
    sensorData.magneticHeading
} else if (sensorData.groundSpeed >= 2.0) {
    sensorData.track
} else {
    lastValidBearing
}
```

**Why Wrong**: Not all devices have magnetometers. Must fallback gracefully.

---

### ❌ MISTAKE 7: Ignoring Speed Threshold

```kotlin
// ❌ WRONG - Using GPS track at 0kts
bearing = gpsTrack  // Will spin randomly when stationary

// ✅ CORRECT - Validate speed first
bearing = if (groundSpeed >= 2.0) {
    gpsTrack
} else {
    lastValidBearing  // Hold last bearing when stopped
}
```

**Why Wrong**: GPS track meaningless when stationary. Map will spin.

---

### ❌ MISTAKE 8: Excessive Update Rate

```kotlin
// ❌ WRONG - Updating every 10ms (100Hz)
delay(10)
updateMapRotation()  // Battery drain, unnecessary CPU load

// ✅ CORRECT - Throttled updates
.sample(66)  // 15Hz is plenty for smooth rotation
```

**Why Wrong**: Battery-powered device. 15Hz already butter-smooth.

---

## Performance Considerations

### Battery Usage

**Sensor Polling**:
- Magnetometer: 20Hz (50ms updates)
- Accelerometer: 20Hz (50ms updates)
- GPS: Continuous from FlightDataManager

**Optimization**:
- `SENSOR_DELAY_GAME` instead of `SENSOR_DELAY_FASTEST`
- Throttled updates via `.sample(66ms)`
- Low-pass filters reduce CPU for smoothing

**Battery Impact**:
- NORTH_UP: Minimal (no sensors)
- TRACK_UP: Low (GPS only)
- HEADING_UP: Medium (GPS + magnetometer + accelerometer)

### Memory Usage

**StateFlow Overhead**:
- `OrientationData`: ~48 bytes per update
- `OrientationSensorData`: ~56 bytes per update
- Flow buffers: Minimal (replay=1)

**Sensor Arrays**:
```kotlin
gravity: FloatArray(3)       // 12 bytes
geomagnetic: FloatArray(3)   // 12 bytes
rotationMatrix: FloatArray(9) // 36 bytes
orientation: FloatArray(3)    // 12 bytes
// Total: 72 bytes (negligible)
```

### CPU Usage

**Most Expensive Operations**:
1. `SensorManager.getRotationMatrix()` - Matrix calculation
2. `SensorManager.getOrientation()` - Trigonometric calculations
3. Bearing smoothing with boundary handling

**Optimization Applied**:
- Throttle heading calculations to 50ms
- Low-pass filter reduces smoothing overhead
- Modulo operations instead of branches for normalization

---

## Testing Requirements

### Manual Testing Checklist

**NORTH_UP Mode**:
- [ ] Map stays fixed, North pointing up
- [ ] Compass hidden by default
- [ ] No rotation regardless of movement/heading
- [ ] Aircraft icon shows GPS track direction

**TRACK_UP Mode**:
- [ ] Map rotates to keep GPS track pointing up
- [ ] Compass visible, shows "T" indicator
- [ ] At <2kts: Bearing holds steady (doesn't spin)
- [ ] At >2kts: Bearing follows GPS smoothly
- [ ] Compass needle points to North correctly

**HEADING_UP Mode**:
- [ ] Map rotates to keep device heading pointing up
- [ ] Compass visible, shows "H" indicator
- [ ] Turn device: Map rotates immediately
- [ ] If magnetometer missing: Falls back to GPS track
- [ ] Compass needle points to North correctly

**Mode Switching**:
- [ ] Tap compass to cycle modes
- [ ] Mode persists across app restarts
- [ ] Smooth transition between modes (no jumps)
- [ ] Correct mode indicator shown

**User Panning**:
- [ ] Pan map: Return button appears
- [ ] Camera stops auto-centering
- [ ] Tap return: Camera centers on GPS
- [ ] ❌ NO auto-return after timeout

**Performance**:
- [ ] Smooth rotation (no stuttering)
- [ ] Battery drain acceptable in HEADING_UP
- [ ] No memory leaks after 1 hour flight
- [ ] No crashes when GPS signal lost

### Unit Test Requirements

**MapOrientationManager**:
```kotlin
@Test fun northUp_alwaysZeroBearing()
@Test fun trackUp_belowSpeedThreshold_holdsLastBearing()
@Test fun trackUp_aboveSpeedThreshold_usesGPSTrack()
@Test fun headingUp_magnetometerAvailable_usesMagHeading()
@Test fun headingUp_magnetometerUnavailable_fallsBackToGPS()
@Test fun headingUp_noGPSnoMag_holdsLastBearing()
@Test fun modeSwitch_persistsToPreferences()
@Test fun userOverride_prevents10SecondUpdates()
```

**OrientationDataSource**:
```kotlin
@Test fun calculateHeading_correctRotationMatrix()
@Test fun smoothBearing_handles360Boundary()
@Test fun sensorUnavailable_fallsBackGracefully()
@Test fun lowPassFilter_smoothsSensorNoise()
```

### Integration Test Requirements

```kotlin
@Test fun endToEnd_northUp_mapStaysFixed()
@Test fun endToEnd_trackUp_mapFollowsMovement()
@Test fun endToEnd_headingUp_mapFollowsDeviceRotation()
@Test fun endToEnd_modeSwitch_compassIndicatorUpdates()
@Test fun endToEnd_userPan_stopsAutoTracking()
@Test fun endToEnd_returnButton_resumesTracking()
```

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                        MapScreen.kt                          │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  LaunchedEffect(orientation)                         │   │
│  │  - Reads orientation.bearing                         │   │
│  │  - Applies to camera.bearing                         │   │
│  │  - Centers camera on GPS position                    │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ OrientationData flow
                            │
┌─────────────────────────────────────────────────────────────┐
│                  MapOrientationManager.kt                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  calculateBearing(sensorData, mode)                  │   │
│  │  - NORTH_UP: return 0.0                              │   │
│  │  - TRACK_UP: return GPS track (if speed >= 2kts)     │   │
│  │  - HEADING_UP: return mag heading (or GPS fallback)  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │ OrientationSensorData flow
                            │
┌─────────────────────────────────────────────────────────────┐
│                 OrientationDataSource.kt                     │
│  ┌─────────────────────┐      ┌─────────────────────────┐   │
│  │  Magnetometer       │      │  FlightDataManager      │   │
│  │  + Accelerometer    │      │  (GPS)                  │   │
│  │  ↓                  │      │  ↓                      │   │
│  │  SensorManager      │      │  RealTimeFlightData     │   │
│  │  ↓                  │      │  - track                │   │
│  │  getRotationMatrix()│      │  - groundSpeed          │   │
│  │  ↓                  │      │  - isGPSFixed           │   │
│  │  getOrientation()   │      │                         │   │
│  │  ↓                  │      │                         │   │
│  │  magneticHeading ───┼──────┼─→ OrientationSensorData │   │
│  └─────────────────────┘      └─────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## Quick Reference

### When to Use Each Mode

| Mode | Use Case | Data Source | Rotation |
|------|----------|-------------|----------|
| **NORTH_UP** | Ground planning, traditional map reading | None | 0° (fixed) |
| **TRACK_UP** | Cross-country navigation, following route | GPS track | -GPS bearing |
| **HEADING_UP** | Thermal soaring, orientation awareness | Magnetometer | -Mag heading |

### Key Constants

```kotlin
MIN_SPEED_FOR_TRACK_KT = 2.0           // Speed threshold for valid GPS track
BEARING_UPDATE_THROTTLE_MS = 66        // ~15Hz update rate
USER_OVERRIDE_TIMEOUT_MS = 10000       // User pan override duration
HEADING_UPDATE_INTERVAL_MS = 50        // ~20Hz magnetometer rate
ALPHA = 0.8f                           // Low-pass filter constant
```

### Critical Files

- `MapOrientationManager.kt:104-128` - Mode-specific bearing calculation
- `MapOrientationManager.kt:131-143` - Validation logic with speed thresholds
- `OrientationDataSource.kt:190-242` - Magnetometer heading calculation
- `CompassWidget.kt:117-123` - Visual rotation (opposite map rotation)
- `MapScreen.kt` - Camera bearing application (search for `CameraPosition`)

---

**END OF DOCUMENT**

*This reference should prevent common orientation mistakes. Read it before modifying any orientation code.*
