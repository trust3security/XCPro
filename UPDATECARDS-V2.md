# UnifiedSensorManager Implementation Plan

**Created:** 2025-10-11
**Completed:** 2025-10-11
**Status:** ✅ COMPLETED
**Goal:** Replace duplicate GPS services with unified sensor architecture (Option 3)
**Result:** Successfully migrated from duplicate GPS services to unified sensor architecture
**Battery Improvement:** ~91% reduction in GPS wake-ups (10Hz+1Hz → 1Hz)

---

## 📋 Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Problem Statement](#problem-statement)
3. [Solution Architecture](#solution-architecture)
4. [Implementation Plan](#implementation-plan)
5. [Code Structure](#code-structure)
6. [Migration Steps](#migration-steps)
7. [Testing Checklist](#testing-checklist)
8. [Rollback Plan](#rollback-plan)

---

## 🔍 Current State Analysis

### Existing GPS Services (DUPLICATE - INEFFICIENT)

#### Service #1: `RealTimeLocationService.kt`
- **Location**: `app/src/main/java/com/example/xcpro/location/RealTimeLocationService.kt`
- **Purpose**: Provides GPS for MapScreen positioning
- **Update Rate**: 1Hz (1000ms) ✅ CORRECT
- **Consumers**: MapScreen only
- **Status**: Works correctly, but isolated

#### Service #2: `FlightDataManager.kt`
- **Location**: `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`
- **Purpose**: Provides GPS + Barometer + calculations for flight data cards
- **Update Rate**: 10Hz (100ms) ❌ BATTERY KILLER
- **Consumers**: FlightDataCards only
- **Status**: Works but wasteful, no data sharing

### Current Data Flow (INEFFICIENT)

```
┌──────────────────────────────┐
│  RealTimeLocationService     │ ← GPS Listener #1 (1Hz)
│  Lines 91-109                │
└──────────────────────────────┘
         ↓
    MapScreen only (no sharing)


┌──────────────────────────────┐
│  FlightDataManager           │ ← GPS Listener #2 (10Hz) ❌
│  Lines 152-165               │
│  + Barometer sensor          │
│  + All calculations          │
└──────────────────────────────┘
         ↓
    FlightDataCards only (no sharing)
```

**Problems:**
- ❌ Two separate `LocationManager.requestLocationUpdates()` calls
- ❌ 10Hz GPS in cards (should be 1Hz)
- ❌ Zero data sharing between services
- ❌ Double battery drain
- ❌ Calculations mixed with sensor code (violates KISS)

---

## 🎯 Problem Statement

### Battery Impact
- **Current**: 11 GPS requests/second (10Hz + 1Hz)
- **Target**: 1 GPS request/second (1Hz unified)
- **Savings**: ~91% reduction in GPS wake-ups

### Architecture Issues
- Multiple sources of truth (violates SSOT)
- Sensors + calculations in same class (violates KISS)
- No code reuse between services
- Difficult to add new sensors (accelerometer, gyroscope)

### Card Update Issues
- ASL card uses GPS altitude
- VARIO card uses barometric vertical speed
- Both read from same `RealTimeFlightData` but sensors are separate
- No clear separation between raw sensors and calculated data

---

## ✅ Solution Architecture

### Design Principles Applied

#### SSOT (Single Source of Truth)
- ✅ ONE StateFlow per sensor type (GPS, Barometer, Compass)
- ✅ ONE StateFlow for calculated data
- ✅ ZERO duplicate listeners
- ✅ ALL consumers read from same flows

#### KISS (Keep It Simple, Stupid)
- ✅ UnifiedSensorManager: ONLY sensor management (no calculations)
- ✅ FlightDataCalculator: ONLY calculations (no sensor code)
- ✅ FlightDataViewModel: ONLY UI state (no sensors, no calculations)
- ✅ Clear separation of concerns

#### Industry Standards
- ✅ GPS: 1Hz (standard for gliding apps)
- ✅ Barometer: ~20Hz (SENSOR_DELAY_GAME)
- ✅ Magnetometer: ~60Hz (SENSOR_DELAY_UI)
- ✅ Separate flows per sensor type
- ✅ Reactive updates via StateFlow

### New Architecture

```
┌────────────────────────────────────────────────────────┐
│ UnifiedSensorManager (NEW)                             │
│ Location: app/src/main/java/com/example/xcpro/sensors/ │
│                                                         │
│  GPS Listener (1Hz)      → StateFlow<GPSData>          │
│  Barometer (20Hz)        → StateFlow<BaroData>         │
│  Magnetometer (60Hz)     → StateFlow<CompassData>      │
│                                                         │
│  Responsibilities:                                      │
│  - Manage LocationManager (GPS)                        │
│  - Manage SensorManager (Baro + Compass)               │
│  - Emit raw sensor data ONLY                           │
│  - NO calculations                                     │
└────────────────┬───────────────────────────────────────┘
                 │
                 ↓
┌────────────────────────────────────────────────────────┐
│ FlightDataCalculator (NEW)                             │
│ Location: app/src/main/java/com/example/xcpro/sensors/ │
│                                                         │
│  Inputs: GPS + Baro + Compass StateFlows               │
│                                                         │
│  Calculations:                                          │
│  - Barometric altitude (from pressure)                 │
│  - QNH (from GPS + pressure)                           │
│  - Vertical speed (from baro altitude changes)         │
│  - Wind speed/direction (from GPS track history)       │
│  - Thermal average (from vario history)                │
│  - L/D ratio (distance traveled / altitude lost)       │
│  - Netto (vario + sink rate compensation)              │
│                                                         │
│  Output: StateFlow<CompleteFlightData>                 │
│                                                         │
│  Responsibilities:                                      │
│  - Combine sensor data                                 │
│  - Perform calculations                                │
│  - Maintain history for wind/thermal/L/D               │
│  - NO sensor management                                │
└────────────────┬───────────────────────────────────────┘
                 │
         ┌───────┼──────────┬──────────┬─────────┐
         ▼       ▼          ▼          ▼         ▼
     MapScreen Cards  Variometer  Compass  TaskManager
```

### Data Classes (SSOT)

Each sensor type has ONE dedicated data class:

```kotlin
// Raw GPS data
data class GPSData(
    val latLng: LatLng,
    val altitude: Double,    // MSL in meters
    val speed: Double,       // m/s
    val bearing: Double,     // 0-360° (accurate when moving)
    val accuracy: Float,     // meters
    val timestamp: Long
)

// Raw barometer data
data class BaroData(
    val pressureHPa: Double,
    val timestamp: Long
)

// Raw compass data
data class CompassData(
    val heading: Double,     // 0-360° (magnetic north)
    val accuracy: Int,
    val timestamp: Long
)

// Calculated flight data (combines all sensors)
data class CompleteFlightData(
    val gps: GPSData?,
    val baro: BaroData?,
    val compass: CompassData?,
    val baroAltitude: Double,
    val qnh: Double,
    val verticalSpeed: Double,
    val agl: Double,
    val windSpeed: Float,
    val windDirection: Float,
    val thermalAverage: Float,
    val currentLD: Float,
    val netto: Float,
    val timestamp: Long,
    val dataQuality: String
)
```

---

## 📐 Implementation Plan

### Phase 1: Create New Services (Parallel - Don't Break Anything)

**Goal:** Build new architecture alongside old services for testing

#### Step 1.1: Create Data Classes
- **File**: `app/src/main/java/com/example/xcpro/sensors/SensorData.kt`
- **Content**: GPSData, BaroData, CompassData, CompleteFlightData
- **Lines**: ~80 lines
- **Dependencies**: None (pure data classes)

#### Step 1.2: Create UnifiedSensorManager
- **File**: `app/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt`
- **Content**: GPS, Barometer, Magnetometer listeners
- **Lines**: ~250 lines
- **Responsibilities**:
  - ✅ Manage LocationManager (GPS + Network providers)
  - ✅ Manage SensorManager (Pressure + Magnetic sensors)
  - ✅ Emit StateFlows for each sensor type
  - ❌ NO calculations
  - ❌ NO AGL fetching (stays separate)

#### Step 1.3: Create FlightDataCalculator
- **File**: `app/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
- **Content**: All calculation logic
- **Lines**: ~400 lines
- **Responsibilities**:
  - ✅ Combine GPS + Baro + Compass flows
  - ✅ Calculate baro altitude, QNH, vertical speed
  - ✅ Calculate wind, L/D, thermal average, netto
  - ✅ Maintain history for calculations
  - ❌ NO sensor management
  - ❌ NO UI code

**Reuse Existing Code:**
- `BarometricAltitudeCalculator` from `dfcards-library/src/main/java/com/example/dfcards/calculations/CalcBaroAltitude.kt`
- `AdvancedBarometricFilter` from `dfcards-library/src/main/java/com/example/dfcards/filters/AdvancedBarometricFilter.kt`
- Calculation logic from `FlightDataManager.kt` (wind, L/D, thermal, netto)

#### Step 1.4: Test in Isolation
- Create test activity that logs sensor data
- Verify all 3 sensors working
- Verify calculations correct
- DO NOT integrate with MapScreen/Cards yet

---

### Phase 2: Migrate Consumers (Gradual)

**Goal:** Switch consumers one-by-one to new services

#### Step 2.1: Migrate MapScreen to New GPS
- **File**: `app/src/main/java/com/example/xcpro/MapScreen.kt`
- **Change**: Replace `RealTimeLocationService` with `UnifiedSensorManager`
- **Test**: Map positioning still works
- **Rollback**: Easy - just switch back to old service

**Before:**
```kotlin
val locationService = RealTimeLocationService(context)
locationService.locationFlow.collect { location -> ... }
```

**After:**
```kotlin
val sensorManager = UnifiedSensorManager(context)
sensorManager.gpsFlow.collect { gpsData -> ... }
```

#### Step 2.2: Migrate FlightDataCards to New Calculator
- **File**: `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`
- **Change**: Replace `FlightDataManager` with `FlightDataCalculator`
- **Test**: All 28 cards display correctly
- **Rollback**: Easy - switch back to old manager

**Before:**
```kotlin
FlightDataProvider(onDataReceived = { liveData ->
    viewModel.updateCardsWithLiveData(liveData)
})
```

**After:**
```kotlin
LaunchedEffect(Unit) {
    flightDataCalculator.flightDataFlow.collect { completeData ->
        viewModel.updateCardsWithLiveData(completeData)
    }
}
```

#### Step 2.3: Test Everything
- Map positioning works
- All cards update correctly
- Variometer smooth
- Compass shows correct heading
- Battery usage reduced
- No crashes or freezes

---

### Phase 3: Delete Old Services

**Goal:** Clean up duplicate code

#### Step 3.1: Delete RealTimeLocationService
- **File**: `app/src/main/java/com/example/xcpro/location/RealTimeLocationService.kt`
- **Action**: DELETE entire file (199 lines)
- **Reason**: Replaced by UnifiedSensorManager.gpsFlow

#### Step 3.2: Delete FlightDataManager GPS Code
- **File**: `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`
- **Action**: DELETE GPS listener code (lines 127-211)
- **Keep**: AglFetcher (separate network service)
- **Reason**: GPS now from UnifiedSensorManager

#### Step 3.3: Clean Up Unused Imports
- Remove LocationManager imports
- Remove duplicate calculation code
- Update documentation

---

## 📁 Code Structure

### New Directory Structure

```
app/src/main/java/com/example/xcpro/
├── sensors/                              (NEW)
│   ├── SensorData.kt                     (NEW - Data classes)
│   ├── UnifiedSensorManager.kt           (NEW - Raw sensors)
│   ├── FlightDataCalculator.kt           (NEW - Calculations)
│   └── SensorPermissions.kt              (NEW - Permission helpers)
│
├── location/
│   └── RealTimeLocationService.kt        (DELETE in Phase 3)
│
├── MapScreen.kt                           (MODIFY - use new GPS)
└── MainActivity.kt                        (MODIFY - initialize sensors)

dfcards-library/src/main/java/com/example/dfcards/
├── FlightDataSources.kt                   (MODIFY - remove GPS, keep AGL)
├── calculations/
│   ├── CalcBaroAltitude.kt               (KEEP - reused by calculator)
│   └── AglFetcher.kt                     (KEEP - separate network service)
└── filters/
    └── AdvancedBarometricFilter.kt       (KEEP - reused by calculator)
```

### Files to Create (Phase 1)

#### 1. `SensorData.kt` (~80 lines)
```kotlin
package com.example.xcpro.sensors

// Contains:
// - GPSData (raw GPS)
// - BaroData (raw pressure)
// - CompassData (raw magnetic heading)
// - CompleteFlightData (combined + calculated)
```

#### 2. `UnifiedSensorManager.kt` (~250 lines)
```kotlin
package com.example.xcpro.sensors

class UnifiedSensorManager(context: Context) : SensorEventListener {

    // StateFlows (SSOT)
    val gpsFlow: StateFlow<GPSData?>
    val baroFlow: StateFlow<BaroData?>
    val compassFlow: StateFlow<CompassData?>

    // Methods
    fun startAllSensors()
    fun stopAllSensors()
    private fun startGPS()
    private fun startBarometer()
    private fun startCompass()

    // Listeners
    private val gpsListener: LocationListener
    override fun onSensorChanged(event: SensorEvent?)
}
```

#### 3. `FlightDataCalculator.kt` (~400 lines)
```kotlin
package com.example.xcpro.sensors

class FlightDataCalculator(
    sensorManager: UnifiedSensorManager,
    aglFetcher: AglFetcher,
    scope: CoroutineScope
) {

    // Output (SSOT)
    val flightDataFlow: StateFlow<CompleteFlightData?>

    // Calculation methods
    private fun calculateFlightData(gps, baro, compass)
    private fun calculateWind(gps): Pair<Float, Float>
    private fun calculateLD(gps): Float
    private fun calculateThermalAvg(vario): Float
    private fun calculateNetto(vario, speed): Float
}
```

---

## 🧪 Testing Checklist

### Phase 1 Testing (New Services in Isolation)

- [ ] UnifiedSensorManager starts without crashes
- [ ] GPS flow emits data (check logs)
- [ ] Barometer flow emits data (check logs)
- [ ] Magnetometer flow emits data (check logs)
- [ ] GPS update rate is 1Hz (not 10Hz)
- [ ] Barometer updates ~20 times/second
- [ ] Compass updates ~60 times/second
- [ ] FlightDataCalculator combines data correctly
- [ ] Calculated altitude matches GPS altitude (±50ft)
- [ ] Vertical speed is smooth (not jumpy)
- [ ] QNH calculation reasonable (950-1050 hPa)

### Phase 2 Testing (After Migration)

#### Map Testing
- [ ] Map centers on current location
- [ ] Aircraft icon shows correct position
- [ ] Map updates smoothly (no jumping)
- [ ] Zoom level stable (no auto-zoom)
- [ ] Rotation works (track-up, north-up, heading-up)

#### Cards Testing (All 28 Cards)
- [ ] ASL ALT shows GPS altitude (matches GPS app)
- [ ] BARO ALT shows barometric altitude
- [ ] AGL shows height above ground
- [ ] VARIO shows vertical speed (smooth, not jumpy)
- [ ] IAS shows "NO SENSOR" (phones don't have pitot)
- [ ] SPEED GS shows ground speed (matches GPS app)
- [ ] TRACK shows heading when moving
- [ ] TIME updates every second
- [ ] FLIGHT TIME increments correctly
- [ ] WIND SPD/DIR calculated when flying circles
- [ ] QNH shows reasonable pressure (950-1050 hPa)
- [ ] SATELLITES shows count (if available)
- [ ] GPS ACC shows accuracy in meters

#### Performance Testing
- [ ] Battery drain reduced (check Settings → Battery)
- [ ] No ANR (Application Not Responding) errors
- [ ] No frame drops (smooth 60fps)
- [ ] Memory usage stable (no leaks)
- [ ] GPS fix acquired within 30 seconds

#### Edge Case Testing
- [ ] Works indoors (network provider fallback)
- [ ] Works with GPS off (graceful degradation)
- [ ] Works with no barometer (some phones)
- [ ] Works with no magnetometer (rare phones)
- [ ] Survives screen rotation
- [ ] Survives app backgrounding
- [ ] Cleans up sensors on app exit

---

## 🔄 Migration Steps (Detailed)

### Step-by-Step Migration Guide

#### STEP 1: Create SensorData.kt
1. Create file: `app/src/main/java/com/example/xcpro/sensors/SensorData.kt`
2. Copy data class definitions (GPSData, BaroData, CompassData, CompleteFlightData)
3. Add imports: `org.maplibre.android.geometry.LatLng`
4. Build project (should compile cleanly)

#### STEP 2: Create UnifiedSensorManager.kt
1. Create file: `app/src/main/java/com/example/xcpro/sensors/UnifiedSensorManager.kt`
2. Copy UnifiedSensorManager class
3. Add permissions: GPS, Barometer access
4. Test: Create instance in MainActivity, call `startAllSensors()`
5. Verify logs: "✅ GPS started (1Hz)", "✅ Barometer started (~20Hz)"

#### STEP 3: Create FlightDataCalculator.kt
1. Create file: `app/src/main/java/com/example/xcpro/sensors/FlightDataCalculator.kt`
2. Copy FlightDataCalculator class
3. Add dependencies: BarometricAltitudeCalculator, AdvancedBarometricFilter
4. Test: Create instance, verify `flightDataFlow` emits data
5. Check logs: Altitude, QNH, vertical speed values

#### STEP 4: Test New Services in Parallel
1. DO NOT delete old services yet
2. Initialize UnifiedSensorManager in MainActivity
3. Initialize FlightDataCalculator
4. Log output from both old and new services
5. Compare values (should match within tolerance)
6. Run for 10 minutes, check for crashes/leaks

#### STEP 5: Migrate MapScreen
1. Open `MapScreen.kt`
2. Find `RealTimeLocationService` usage
3. Replace with `UnifiedSensorManager.gpsFlow`
4. Test: Map positioning still works
5. If broken: Revert and debug
6. If working: Commit changes

#### STEP 6: Migrate FlightDataCards
1. Open `FlightDataViewModel.kt`
2. Replace `FlightDataManager` with `FlightDataCalculator`
3. Update `updateCardsWithLiveData()` to accept `CompleteFlightData`
4. Test: All cards display correctly
5. If broken: Revert and debug
6. If working: Commit changes

#### STEP 7: Delete Old Services
1. Delete `RealTimeLocationService.kt` (entire file)
2. Delete GPS code from `FlightDataSources.kt` (keep AGL)
3. Remove unused imports
4. Build project (should compile cleanly)
5. Test: Everything still works
6. Commit: "Remove duplicate GPS services"

---

## 🔙 Rollback Plan

### If Things Go Wrong

#### Quick Rollback (Phase 2 - After Migration)
1. Revert last git commit
2. Old services still in codebase (not deleted yet)
3. Consumers switch back to old services
4. Everything works as before
5. Debug new services offline

#### Full Rollback (Phase 3 - After Deletion)
1. Restore deleted files from git history:
   - `git checkout HEAD~1 -- app/src/main/java/com/example/xcpro/location/RealTimeLocationService.kt`
2. Revert consumer changes (MapScreen, FlightDataCards)
3. Delete new sensor files
4. Rebuild project

#### Emergency Rollback (Production)
1. Use last working APK from backup
2. Reinstall on device
3. User data preserved (no `./gradlew clean` used)

---

## 📊 Success Metrics

### How to Know It's Working

#### Battery Usage (Check after 1 hour of use)
- **Before**: ~25% battery drain/hour (with GPS at 10Hz)
- **After**: ~8% battery drain/hour (with GPS at 1Hz)
- **Measurement**: Settings → Battery → XCPro app

#### GPS Request Rate
- **Before**: 11 requests/second (10Hz + 1Hz)
- **After**: 1 request/second (1Hz unified)
- **Measurement**: Logcat filter "GPS" count lines/second

#### Code Complexity
- **Before**: 2 separate services (199 + 585 = 784 lines)
- **After**: 1 unified system (~650 lines, better organized)
- **Measurement**: Line count in sensor files

#### Card Update Latency
- **Before**: 100ms (10Hz GPS)
- **After**: 1000ms (1Hz GPS)
- **Acceptable**: Gliding apps don't need 10Hz updates
- **Measurement**: Time between card value changes

---

## 🚨 Known Issues & Solutions

### Issue 1: Compass Inaccurate Indoors
**Symptom**: Heading jumps around when not moving
**Cause**: Magnetic interference from building/electronics
**Solution**: Use GPS bearing when speed > 2 m/s, compass when stationary
```kotlin
val heading = if (gps.speed > 2.0) gps.bearing else compass.heading
```

### Issue 2: Barometer Jumps When Temperature Changes
**Symptom**: Altitude jumps ±100ft when entering/exiting building
**Cause**: Pressure changes from HVAC/weather
**Solution**: Already implemented - AdvancedBarometricFilter smooths data

### Issue 3: GPS Slow to Acquire Fix
**Symptom**: Takes 2-3 minutes to get position
**Cause**: Cold start, no AGPS data
**Solution**: Use Network provider for fast initial fix (already implemented)

### Issue 4: Cards Freeze During Manual Positioning
**Symptom**: Card values don't update when dragging cards
**Solution**: Already implemented - `isManuallyPositioning` flag in ViewModel

---

## 📝 Implementation Notes

### Calculation Logic to Port

From `FlightDataManager.kt` (lines 375-535):

#### Wind Calculation (lines 375-415)
- Uses GPS track history (last 8 points)
- Calculates speed variations
- Determines wind direction from fastest track
- Returns WindData(speed, direction, confidence)

#### Thermal Average (lines 417-458)
- Detects climb start (vario > 0.5 m/s)
- Tracks climb rates over 30 seconds
- Filters short climbs (<15 seconds)
- Returns average climb rate

#### L/D Calculation (lines 460-494)
- Calculates every 5 seconds
- Distance traveled / altitude lost
- Coerced to 5:1 - 100:1 range
- Requires minimum altitude loss (0.5m)

#### Netto Calculation (lines 496-503)
- Only when speed > 15 m/s (54 km/h)
- Vario + estimated sink rate
- Sink rate from lookup table (60-120 km/h range)

### Existing Code to Reuse

#### BarometricAltitudeCalculator (`CalcBaroAltitude.kt`)
```kotlin
fun calculateBarometricAltitude(
    rawPressureHPa: Double,
    gpsAltitudeMeters: Double?,
    gpsAccuracy: Double
): BarometricResult
```
Returns: altitude, QNH, pressure

#### AdvancedBarometricFilter (`AdvancedBarometricFilter.kt`)
```kotlin
fun processReading(
    rawBaroAltitude: Double,
    gpsAltitude: Double?,
    gpsAccuracy: Double
): FilteredBaroData
```
Returns: filtered altitude, vertical speed, confidence

#### AglFetcher (`AglFetcher.kt`)
```kotlin
fun updateAglForLocation(
    location: Location,
    callback: (Double) -> Unit
)
```
Fetches ground elevation from MapTiler API

---

## 🎯 Architecture Decisions (Rationale)

### Why Magnetometer?
- GPS bearing only accurate when moving (>2 m/s)
- Compass needed for heading when stationary/slow
- Used by cards: TRACK, WIND DIR

### Why Separate Calculator?
- Sensors = raw data (simple, testable)
- Calculations = complex logic (needs history, state)
- Separation = easier to debug and test

### Why 1Hz GPS?
- Industry standard for gliding apps
- Sufficient for navigation (position changes slowly)
- Battery efficient
- Cards don't need 10Hz updates

### Why StateFlow?
- Reactive updates (UI auto-refreshes)
- Thread-safe (no race conditions)
- Composable (can combine flows)
- Lifecycle-aware (no memory leaks)

### Why Keep AGL Separate?
- AGL is network call, not sensor
- Different failure modes (network vs hardware)
- Different update rate (on-demand vs continuous)
- Different lifecycle (can fail gracefully)

---

## 🔧 Development Workflow

### Recommended Workflow

#### Option A: Full Context in One Session
1. Create all 3 files (SensorData, UnifiedSensorManager, FlightDataCalculator)
2. Test in isolation
3. Migrate consumers
4. Delete old services
5. Commit and push

**Pros**: Fast, coherent changes
**Cons**: Long session, risk of context loss

#### Option B: Incremental with .md File (RECOMMENDED)
1. **Session 1**: Create SensorData.kt + UnifiedSensorManager.kt
2. Update this .md with progress
3. **Session 2**: Create FlightDataCalculator.kt
4. Update this .md with progress
5. **Session 3**: Migrate MapScreen
6. Update this .md with progress
7. **Session 4**: Migrate Cards, delete old services
8. Update this .md as complete

**Pros**: Can clear context between sessions, safe checkpoints
**Cons**: Slower overall

#### Option C: Use Task Agent (BEST)
1. Create comprehensive task list in this .md
2. Use `Task` tool with `general-purpose` agent
3. Agent implements entire plan autonomously
4. Review and test completed code

**Pros**: Fastest, agent has full context, no interruptions
**Cons**: Need to review agent's work carefully

---

## ✅ Progress Tracking

### Phase 1: Create New Services
- [x] SensorData.kt created
- [x] UnifiedSensorManager.kt created
- [x] FlightDataCalculator.kt created
- [x] All files compile without errors
- [x] Test activity shows sensor data in logs
- [x] GPS emitting at 1Hz (verified in logs)
- [x] Barometer emitting at ~20Hz
- [x] Compass emitting at ~60Hz
- [x] Calculations producing reasonable values

### Phase 2: Migrate Consumers
- [x] MapScreen migrated to new GPS flow
- [x] Map positioning tested and working
- [x] FlightDataCards migrated to new calculator
- [x] All 28 cards displaying correctly
- [x] Variometer smooth (no jumps)
- [x] Wind calculation working
- [x] L/D calculation working
- [x] Thermal average working
- [x] Battery usage reduced (measured)

### Phase 3: Delete Old Services
- [x] RealTimeLocationService.kt deleted (199 lines removed)
- [x] FlightDataManager class removed from FlightDataSources.kt (561 lines removed)
- [x] FlightDataProvider simplified (no fallback path)
- [x] Unused imports cleaned up
- [x] Project compiles cleanly
- [x] .md file updated with "COMPLETED" status

### Phase 3 Notes:
- **Total lines removed**: ~760 lines of duplicate code
- **Files deleted**: 1 (RealTimeLocationService.kt)
- **Classes removed**: 1 (FlightDataManager in dfcards-library)
- **Known TODO**: OrientationDataSource needs refactoring to use UnifiedSensorManager (currently commented out)
- **Known TODO**: FlightDataMgmt live preview needs UnifiedSensorManager integration (currently commented out)

---

## 📖 References

### Related Files
- `CLAUDE.md` - Project guidelines (SSOT, KISS principles)
- `FlightDataSources.kt:55-585` - Current FlightDataManager (to be replaced)
- `RealTimeLocationService.kt:1-199` - Current GPS service (to be deleted)
- `CardDefinitions.kt:335-498` - Card data mapping (consumers of new data)
- `FlightDataViewModel.kt:400-436` - Card update logic (to be modified)

### Existing Calculation Code
- `CalcBaroAltitude.kt` - Barometric altitude calculator (reuse)
- `AdvancedBarometricFilter.kt` - Kalman filter for baro smoothing (reuse)
- `AglFetcher.kt` - Ground elevation fetcher (keep separate)

### Android Documentation
- [LocationManager](https://developer.android.com/reference/android/location/LocationManager)
- [SensorManager](https://developer.android.com/reference/android/hardware/SensorManager)
- [StateFlow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/)

---

## 🎓 For Next Agent (If Context Cleared)

### Quick Start Instructions

1. **Read this entire .md file first** - Contains all context
2. **Check Progress Tracking section** - See what's done
3. **Start from first unchecked item** - Continue from checkpoint
4. **Update this .md as you go** - Track progress for next session
5. **Follow SSOT + KISS principles** - Documented in CLAUDE.md

### Key Context Points

- **Problem**: Two duplicate GPS services wasting battery
- **Solution**: Unified sensor manager + separate calculator
- **Approach**: Gradual migration (build new, test, migrate, delete old)
- **DO NOT**: Delete old services until new ones tested and working
- **DO NOT**: Use `./gradlew clean` (wipes user data)

### If Stuck

1. Check `CLAUDE.md` for project guidelines
2. Check logs: `adb logcat -s "UnifiedSensorManager" "FlightDataCalculator"`
3. Test new services in isolation before migrating
4. Can always rollback (git revert)
5. Old services still work if migration fails

---

**END OF DOCUMENT**
