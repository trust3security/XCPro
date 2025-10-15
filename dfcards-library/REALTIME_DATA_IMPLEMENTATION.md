# Real-Time Data Implementation Guide

**Last Updated**: 2025-10-02
**Purpose**: Document how real-time flight data flows to DFCards and displays live values on the map

---

## 🎯 GOAL

Display live sensor data in flight data cards on the map for:
- **GPS Alt** - GPS altitude in feet
- **Baro Alt** - Barometric altitude in feet with QNH
- **AGL** - Height above ground level from MapTiler
- **Vario** - Vertical speed (climb/sink rate)
- **IAS** - Indicated airspeed (estimated from ground speed)
- **Ground Speed** - GPS ground speed in knots

---

## ✅ CURRENT IMPLEMENTATION STATUS

### **Working Components**

1. ✅ **GPS Data Collection** - `FlightDataManager` (FlightDataSources.kt) collects GPS + sensors
2. ✅ **Barometric Sensor** - Pressure sensor reads ambient pressure
3. ✅ **AGL Calculation** - `AglFetcher` queries MapTiler elevation API
4. ✅ **Data Mapping** - `CardLibrary.mapLiveDataToCard()` converts data to card format
5. ✅ **Update Mechanism** - Cards update every 2 seconds
6. ✅ **Data Provider** - `FlightDataProvider` composable manages lifecycle

---

## 🔄 DATA FLOW ARCHITECTURE

### High-Level Flow

```
Android Sensors (GPS, Barometer, etc.)
    ↓
FlightDataManager.updateFlightData()
    ↓
RealTimeFlightData (data class)
    ↓
FlightDataProvider { liveData -> }
    ↓
FlightDataManager.updateLiveFlightData(liveData)
    ↓
MapComposeEffects.LaunchedEffect(liveData)
    ↓
FlightDataViewModel.updateCardsWithLiveData(liveData)
    ↓
CardLibrary.mapLiveDataToCard(cardId, liveData)
    ↓
EnhancedFlightDataCard displays values
```

---

## 📂 KEY FILES AND RESPONSIBILITIES

### 1. **FlightDataSources.kt** (dfcards-library)

**Location**: `dfcards-library/src/main/java/com/example/dfcards/FlightDataSources.kt`

**Purpose**: Core sensor data collection and processing

**Key Classes**:

#### `FlightDataManager` (Lines 55-592)
```kotlin
class FlightDataManager(private val context: Context) : SensorEventListener {
    private val _flightDataFlow = MutableStateFlow(RealTimeFlightData())
    val flightDataFlow: StateFlow<RealTimeFlightData> = _flightDataFlow.asStateFlow()

    // Sensors
    private val locationManager: LocationManager
    private val sensorManager: SensorManager
    private val pressureSensor: Sensor?
    private val baroCalculator = BarometricAltitudeCalculator()
    private val aglFetcher = AglFetcher(context, CoroutineScope(Dispatchers.IO))
}
```

**Responsibilities**:
- Collect GPS location updates
- Read barometric pressure sensor
- Calculate barometric altitude with QNH correction
- Fetch AGL from MapTiler API
- Calculate derived values (vertical speed, L/D, thermal avg)
- Emit `RealTimeFlightData` via StateFlow

**Update Frequency**: GPS location updates + sensor changes (typically 1-2 Hz)

---

#### `RealTimeFlightData` (Lines 593-624)
```kotlin
data class RealTimeFlightData(
    // GPS Position
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val gpsAltitude: Double = 0.0,     // GPS altitude (MSL)
    val groundSpeed: Double = 0.0,     // km/h
    val track: Double = 0.0,           // degrees

    // Barometric Data
    val baroAltitude: Double = 0.0,    // Calculated from pressure
    val currentPressureHPa: Double = 0.0,
    val qnh: Double = 1013.25,

    // Derived Values
    val agl: Double = 0.0,             // Height above ground (from MapTiler)
    val verticalSpeed: Double = 0.0,   // m/s (climb/sink)
    val ias: Double = 0.0,             // Estimated indicated airspeed

    // Flight Performance
    val thermalAverage: Float = 0f,
    val netto: Float = 0f,
    val currentLD: Float = 0f,

    // Wind
    val windSpeed: Double = 0.0,
    val windDirection: Double = 0.0,

    // Status
    val isGPSFixed: Boolean = false,
    val dataQuality: DataQuality = DataQuality.INVALID,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

#### `FlightDataProvider` (Lines 20-53)
```kotlin
@Composable
fun FlightDataProvider(
    onDataReceived: (RealTimeFlightData) -> Unit
) {
    val context = LocalContext.current
    val dataSource = remember { FlightDataManager(context) }

    DisposableEffect(Unit) {
        dataSource.forceStartDataCollection()

        val job = CoroutineScope(Dispatchers.Main).launch {
            dataSource.flightDataFlow.collect { data ->
                onDataReceived(data)
            }
        }

        onDispose {
            job.cancel()
            dataSource.stopDataCollection()
        }
    }
}
```

**Usage in MapScreen**:
```kotlin
// MapScreen.kt:392-397
FlightDataProvider { liveData ->
    flightDataManager.updateLiveFlightData(liveData)
    println("DEBUG: MapScreen received live data - GPS: ${liveData.isGPSFixed}")
}
```

---

### 2. **FlightDataViewModel.kt** (dfcards-library)

**Location**: `dfcards-library/src/main/java/com/example/dfcards/dfcards/FlightDataViewModel.kt`

**Purpose**: Manage card state and update cards with live data

#### `updateCardsWithLiveData()` (Lines 436-457)
```kotlin
fun updateCardsWithLiveData(liveData: RealTimeFlightData) {
    // ✅ SKIP updates during manual positioning
    if (isManuallyPositioning) {
        return
    }

    // ✅ THROTTLE: Only update every 2 seconds
    val now = System.currentTimeMillis()
    if (now - lastUpdateTime < UPDATE_THROTTLE_MS) {
        return
    }
    lastUpdateTime = now

    // ✅ UPDATE: Map live data to each card
    val updatedCards = _cardStates.value.map { cardState ->
        val updatedFlightData = mapRealDataToFlightData(cardState.flightData, liveData)
        cardState.copy(flightData = updatedFlightData)
    }

    _cardStates.value = updatedCards
}

companion object {
    private const val UPDATE_THROTTLE_MS = 2000L  // 2-second throttle
}
```

**Throttling Logic**:
- Prevents excessive recomposition
- Updates at most every 2 seconds
- Paused during manual card positioning

---

#### `mapRealDataToFlightData()` (Lines 458-557)
```kotlin
private fun mapRealDataToFlightData(
    currentFlightData: FlightData,
    realData: RealTimeFlightData
): FlightData {
    val (primaryValue, secondaryValue) = when (currentFlightData.id) {
        "gps_alt" -> {
            val altFt = (realData.gpsAltitude * 3.28084).roundToInt()
            Pair("$altFt ft", "GPS")
        }
        "baro_alt" -> {
            if (realData.currentPressureHPa > 0) {
                val altFt = (realData.baroAltitude * 3.28084).roundToInt()
                val qnh = realData.qnh.roundToInt()
                Pair("$altFt ft", "QNH $qnh")
            } else {
                Pair("-- ft", "NO BARO")
            }
        }
        "agl" -> {
            if (realData.isGPSFixed && realData.agl > 0) {
                val aglFt = (realData.agl * 3.28084).roundToInt()
                Pair("$aglFt ft", "AGL")
            } else {
                Pair("-- ft", "NO GPS")
            }
        }
        // ... (continues for all card types)
    }

    return currentFlightData.copy(
        primaryValue = primaryValue,
        secondaryValue = secondaryValue
    )
}
```

**Note**: This function duplicates logic from `CardLibrary.mapLiveDataToCard()` - consider consolidating.

---

### 3. **CardDefinitions.kt** (dfcards-library)

**Location**: `dfcards-library/src/main/java/com/example/dfcards/CardDefinitions.kt`

**Purpose**: Central card definitions and data mapping

#### `mapLiveDataToCard()` (Lines 335-556)
```kotlin
fun mapLiveDataToCard(
    cardId: String,
    liveData: RealTimeFlightData?
): Pair<String, String?> {
    if (liveData == null) return Pair("--", "NO DATA")

    val isGPSValid = liveData.isGPSFixed && liveData.dataQuality == DataQuality.LIVE
    val isBaroValid = liveData.currentPressureHPa > 0

    return when (cardId) {
        "gps_alt" -> {
            if (isGPSValid) {
                val altFt = (liveData.gpsAltitude * 3.28084).roundToInt()
                Pair("$altFt ft", "GPS")
            } else {
                Pair("-- ft", "NO GPS")
            }
        }
        "baro_alt" -> {
            if (isBaroValid) {
                val altFt = (liveData.baroAltitude * 3.28084).roundToInt()
                val qnh = liveData.qnh.roundToInt()
                val status = when {
                    kotlin.math.abs(liveData.qnh - 1013.25) > 5 -> "QNH $qnh"
                    isGPSValid -> "CAL"
                    else -> "STD"
                }
                Pair("$altFt ft", status)
            } else {
                Pair("-- ft", "NO BARO")
            }
        }
        "agl" -> {
            if (isGPSValid && liveData.agl > 0) {
                val aglFt = (liveData.agl * 3.28084).roundToInt()
                val status = when {
                    aglFt < 100 -> "LOW"
                    aglFt < 500 -> "MED"
                    aglFt > 2000 -> "HIGH"
                    else -> "AGL"
                }
                Pair("$aglFt ft", status)
            } else {
                Pair("-- ft", if (isGPSValid) "LOADING" else "NO GPS")
            }
        }
        "vario" -> {
            if (isGPSValid) {
                val varioMs = (liveData.verticalSpeed * 10).roundToInt() / 10.0
                val sign = if (varioMs >= 0) "+" else ""
                Pair("$sign$varioMs m/s", null)
            } else {
                Pair("-- m/s", "NO GPS")
            }
        }
        "ias" -> {
            if (isGPSValid && liveData.groundSpeed > 1.0) {
                val iasKt = (liveData.groundSpeed * 0.95).roundToInt()
                Pair("$iasKt kt", "EST")
            } else {
                Pair("-- kt", "NO DATA")
            }
        }
        "ground_speed" -> {
            if (isGPSValid) {
                val speedKt = liveData.groundSpeed.roundToInt()
                Pair("$speedKt kt", "GPS")
            } else {
                Pair("-- kt", "NO GPS")
            }
        }
        // ... (26 total cards)
    }
}
```

**Validation Rules**:
- **GPS-dependent cards**: Require `isGPSFixed && dataQuality == LIVE`
- **Barometric cards**: Require `currentPressureHPa > 0`
- **AGL**: Requires GPS + AGL > 0
- **Fallback**: Shows "NO GPS", "NO BARO", "NO DATA" when invalid

---

### 4. **MapComposeEffects.kt** (app)

**Location**: `app/src/main/java/com/example/xcpro/map/MapComposeEffects.kt`

**Purpose**: Wire live data to ViewModel

#### Live Data Update Effect (Lines 151-158)
```kotlin
// Update cards and location with live flight data
LaunchedEffect(flightDataManager.liveFlightData) {
    flightDataManager.liveFlightData?.let { liveData ->
        flightViewModel.updateCardsWithLiveData(liveData)  // ✅ Updates cards
        locationManager.updateLocationFromFlightData(liveData, orientationData.bearing)
    } ?: Log.d(TAG, "📡 No GPS data available (liveFlightData is null)")
}
```

**Trigger**: Runs whenever `flightDataManager.liveFlightData` changes

---

### 5. **EnhancedFlightDataCard.kt** (dfcards-library)

**Location**: `dfcards-library/src/main/java/com/example/dfcards/dfcards/EnhancedFlightDataCard.kt`

**Purpose**: Display card UI with live data

#### Card Composable (Lines 29-150)
```kotlin
@Composable
fun EnhancedFlightDataCard(
    flightData: FlightData,
    cardWidth: Float,
    cardHeight: Float,
    isEditMode: Boolean = false,
    isLiveData: Boolean = false,
    dataQuality: DataQuality = DataQuality.INVALID,
    modifier: Modifier = Modifier
) {
    // Display primary value (e.g., "1250 ft")
    Text(
        text = flightData.primaryValue,
        fontSize = stableFontSizes.primarySize.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = editModeAlpha),
        textAlign = TextAlign.Center,
        maxLines = 1
    )

    // Display secondary value (e.g., "GPS")
    flightData.secondaryValue?.let { secondary ->
        Text(
            text = secondary,
            fontSize = stableFontSizes.secondarySize.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * editModeAlpha),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
```

**Visual Feedback**:
- Live data: Subtle scale animation (1.02x)
- Edit mode: Increased scale (1.05x), red border, reduced alpha
- No data: Shows "--" with status text

---

## 🔧 HOW EACH CARD WORKS

### **GPS Altitude**
- **Source**: `RealTimeFlightData.gpsAltitude` (from GPS location)
- **Unit Conversion**: Meters → Feet (`* 3.28084`)
- **Validation**: Requires GPS fix + LIVE quality
- **Display**: `"1250 ft"` / `"GPS"`
- **Fallback**: `"-- ft"` / `"NO GPS"`

### **Barometric Altitude**
- **Source**: `RealTimeFlightData.baroAltitude` (calculated from pressure)
- **Calculation**: `BarometricAltitudeCalculator.calculateAltitude(pressure, qnh)`
- **Validation**: Requires `currentPressureHPa > 0`
- **Display**: `"1200 ft"` / `"QNH 1015"` or `"CAL"` or `"STD"`
- **Fallback**: `"-- ft"` / `"NO BARO"`
- **QNH Status**:
  - `QNH xxxx` - Non-standard pressure setting
  - `CAL` - Calibrated to GPS (QNH ~ 1013.25 and GPS available)
  - `STD` - Standard pressure (1013.25 hPa, no GPS)

### **AGL (Height Above Ground)**
- **Source**: `RealTimeFlightData.agl` (from MapTiler Elevation API)
- **Calculation**: `AglFetcher.getTerrainElevation()` → `GPS Alt - Terrain Elevation`
- **Validation**: Requires GPS + AGL > 0
- **Display**: `"500 ft"` / `"MED"`
- **Status Indicators**:
  - `LOW` - < 100 ft (danger zone)
  - `MED` - 100-500 ft (caution)
  - `HIGH` - > 2000 ft (safe)
  - `AGL` - 500-2000 ft (normal)
- **Fallback**: `"-- ft"` / `"LOADING"` (GPS OK) or `"NO GPS"`

### **Vario (Vertical Speed)**
- **Source**: `RealTimeFlightData.verticalSpeed` (calculated from GPS alt changes)
- **Calculation**: `(currentAlt - lastAlt) / (currentTime - lastTime)`
- **Filtering**: Advanced filtering to reduce GPS noise
- **Display**: `"+2.5 m/s"` (climbing) or `"-1.2 m/s"` (sinking)
- **Validation**: Requires GPS fix
- **Fallback**: `"-- m/s"` / `"NO GPS"`

### **IAS (Indicated Airspeed)**
- **Source**: Estimated from `RealTimeFlightData.groundSpeed`
- **Calculation**: `groundSpeed * 0.95` (rough wind correction)
- **Unit Conversion**: km/h → knots (`* 0.539957`)
- **Validation**: Requires GPS + ground speed > 1.0
- **Display**: `"45 kt"` / `"EST"`
- **Fallback**: `"-- kt"` / `"NO DATA"`
- **Note**: This is a rough estimate; true IAS requires pitot tube

### **Ground Speed**
- **Source**: `RealTimeFlightData.groundSpeed` (from GPS)
- **Unit Conversion**: km/h → knots (already in knots from GPS)
- **Validation**: Requires GPS fix
- **Display**: `"48 kt"` / `"GPS"`
- **Fallback**: `"-- kt"` / `"NO GPS"`

---

## ⚙️ CONFIGURATION

### Update Frequency

**GPS Location Updates**:
```kotlin
// FlightDataManager.kt:151-161
locationManager.requestLocationUpdates(
    LocationManager.GPS_PROVIDER,
    1000L,        // ✅ 1 second minimum interval
    0f,           // ✅ 0 meters minimum distance (all updates)
    locationListener
)
```

**Card Update Throttle**:
```kotlin
// FlightDataViewModel.kt:433
private const val UPDATE_THROTTLE_MS = 2000L  // 2 seconds
```

**Actual Update Rate**:
- GPS provides updates every ~1 second
- Cards throttle to every 2 seconds
- **Effective Rate**: 0.5 Hz (once every 2 seconds)

---

## 🐛 COMMON ISSUES & SOLUTIONS

### Issue 1: Cards Show "NO GPS"

**Symptoms**: All GPS-dependent cards show `"-- ft"` / `"NO GPS"`

**Causes**:
1. Location permissions not granted
2. GPS not initialized
3. No GPS fix yet (cold start can take 30+ seconds)
4. `dataQuality != LIVE`

**Debug**:
```kotlin
println("GPS Status:")
println("- isGPSFixed: ${liveData.isGPSFixed}")
println("- dataQuality: ${liveData.dataQuality}")
println("- gpsAltitude: ${liveData.gpsAltitude}m")
```

**Solution**:
- Check `FlightDataProvider` is active in MapScreen
- Verify `FlightDataManager.forceStartDataCollection()` called
- Wait for GPS fix (30-60 seconds outdoors)
- Check `dataQuality == DataQuality.LIVE`

---

### Issue 2: Baro Alt Shows "NO BARO"

**Symptoms**: Barometric altitude shows `"-- ft"` / `"NO BARO"`

**Causes**:
1. Device has no pressure sensor
2. Sensor not initialized
3. `currentPressureHPa == 0`

**Debug**:
```kotlin
println("Baro Status:")
println("- currentPressureHPa: ${liveData.currentPressureHPa}")
println("- baroAltitude: ${liveData.baroAltitude}m")
println("- qnh: ${liveData.qnh}")
println("- sensorAvailable: ${pressureSensor != null}")
```

**Solution**:
- Check device specs for barometer (many phones don't have one)
- Verify `pressureSensor != null` in FlightDataManager
- Check sensor registration in `forceStartDataCollection()`

---

### Issue 3: AGL Shows "LOADING"

**Symptoms**: AGL card shows `"-- ft"` / `"LOADING"` indefinitely

**Causes**:
1. MapTiler API not responding
2. No internet connection
3. Invalid GPS coordinates
4. Rate limit exceeded

**Debug**:
```kotlin
println("AGL Status:")
println("- agl: ${liveData.agl}m")
println("- latitude: ${liveData.latitude}")
println("- longitude: ${liveData.longitude}")
println("- isGPSFixed: ${liveData.isGPSFixed}")
```

**Solution**:
- Check internet connection
- Verify `AglFetcher` API calls successful
- Check MapTiler API key validity
- Wait 5-10 seconds for API response

---

### Issue 4: Cards Not Updating

**Symptoms**: Cards display but values don't change

**Causes**:
1. `FlightDataProvider` not collecting data
2. `updateCardsWithLiveData()` not called
3. Throttle preventing updates
4. Manual positioning active

**Debug**:
```kotlin
// In MapComposeEffects.kt
LaunchedEffect(flightDataManager.liveFlightData) {
    println("🔄 Live data changed: ${flightDataManager.liveFlightData}")
    flightDataManager.liveFlightData?.let { liveData ->
        println("📡 Updating cards with live data")
        flightViewModel.updateCardsWithLiveData(liveData)
    }
}
```

**Solution**:
- Verify `FlightDataProvider` composable is active
- Check `LaunchedEffect` in MapComposeEffects is triggering
- Ensure `isManuallyPositioning == false`
- Check throttle timing (2 seconds between updates)

---

## 🚀 PERFORMANCE OPTIMIZATION

### Current Throttle (2 seconds)

**Pros**:
- Reduced battery drain
- Fewer recompositions
- Smoother UI

**Cons**:
- Updates feel slow
- Vario response delayed

### Recommended Throttle (1 second)

**Change**:
```kotlin
// FlightDataViewModel.kt:433
private const val UPDATE_THROTTLE_MS = 1000L  // 1 second
```

**Impact**:
- Faster visual feedback
- Better for dynamic flight data (vario, speed)
- Minimal battery impact (GPS already running at 1 Hz)

---

## 📝 FUTURE ENHANCEMENTS

### 1. Live Data Preview in Screens Tab

**Current**: Flight Data → Screens tab shows card selection without live values

**Enhancement**: Display live data in card selection grid

**Implementation**:
```kotlin
// FlightDataScreensTab.kt
FlightDataScreensTab(
    liveFlightData = flightDataManager.liveFlightData,  // ✅ Pass live data
    ...
)

// CardsGridSection.kt
CardGridItem(
    card = card,
    isSelected = isSelected,
    liveData = liveFlightData,  // ✅ Show live preview
    onToggle = { ... }
)
```

**Benefit**: Users can see what data each card will show before selecting it

---

### 2. Explicit Empty Template Flag

**Current**: Fallback mechanism loads cards even when user deselected all

**Enhancement**: Track when template is explicitly empty vs. not loaded

**Implementation**:
```kotlin
data class FlightTemplate(
    val id: String,
    val name: String,
    val cardIds: List<String>,
    val isExplicitlyEmpty: Boolean = false  // ✅ NEW
)

// CardPreferences.kt
suspend fun saveProfileTemplateCards(profileId: String, templateId: String, cardIds: List<String>) {
    context.dataStore.edit { preferences ->
        val cardsKey = "profile_${profileId}_template_${templateId}_cards"
        preferences[stringPreferencesKey(cardsKey)] = cardIds.joinToString(",")

        val emptyFlagKey = "profile_${profileId}_template_${templateId}_is_explicitly_empty"
        preferences[booleanPreferencesKey(emptyFlagKey)] = (cardIds.isEmpty())  // ✅ Track empty state
    }
}

// FlightDataViewModel.kt:initializeCards()
if (_cardStates.value.isEmpty() && !isExplicitlyEmpty) {
    loadEssentialCardsOnStartup(...)  // Only load if truly not loaded
}
```

**Benefit**: Respects user's choice to have no cards

---

### 3. Consolidate Data Mapping

**Current**: Two functions do the same mapping:
- `FlightDataViewModel.mapRealDataToFlightData()` (lines 458-557)
- `CardLibrary.mapLiveDataToCard()` (lines 335-556)

**Enhancement**: Use single source of truth

**Implementation**:
```kotlin
// FlightDataViewModel.kt:mapRealDataToFlightData()
private fun mapRealDataToFlightData(
    currentFlightData: FlightData,
    realData: RealTimeFlightData
): FlightData {
    val (primaryValue, secondaryValue) = CardLibrary.mapLiveDataToCard(
        currentFlightData.id,
        realData
    )

    return currentFlightData.copy(
        primaryValue = primaryValue,
        secondaryValue = secondaryValue
    )
}
```

**Benefit**: Easier maintenance, single place to update logic

---

## 📚 REFERENCE

### Data Quality Enum

```kotlin
enum class DataQuality {
    LIVE,       // Real-time sensor data
    SIMULATED,  // Test/debug data
    INVALID     // No data available
}
```

### FlightModeSelection Enum

```kotlin
enum class FlightModeSelection(val displayName: String) {
    CRUISE("Cruise"),
    THERMAL("Thermal"),
    FINAL_GLIDE("Final Glide")
}
```

### Essential Card IDs

```kotlin
val essentialCards = listOf(
    "gps_alt",      // GPS Altitude
    "baro_alt",     // Barometric Altitude
    "agl",          // Height Above Ground
    "vario",        // Vertical Speed
    "ias",          // Indicated Airspeed (estimated)
    "ground_speed"  // GPS Ground Speed
)
```

---

## 🔍 TESTING CHECKLIST

### GPS Altitude
- [ ] Shows altitude in feet when GPS fix acquired
- [ ] Updates every 2 seconds
- [ ] Shows "NO GPS" when GPS unavailable
- [ ] Secondary shows "GPS"

### Barometric Altitude
- [ ] Shows altitude calculated from pressure sensor
- [ ] Displays QNH value when non-standard
- [ ] Shows "CAL" when GPS-calibrated
- [ ] Shows "STD" when using standard pressure
- [ ] Shows "NO BARO" when sensor unavailable

### AGL (Height Above Ground)
- [ ] Shows height above terrain from MapTiler
- [ ] Updates every 2 seconds
- [ ] Shows "LOADING" while fetching terrain data
- [ ] Shows "LOW" / "MED" / "HIGH" status based on height
- [ ] Shows "NO GPS" when GPS unavailable

### Vario (Vertical Speed)
- [ ] Shows climb rate with "+" sign
- [ ] Shows sink rate with "-" sign
- [ ] Updates smoothly (filtered for GPS noise)
- [ ] Shows "NO GPS" when GPS unavailable

### IAS (Indicated Airspeed)
- [ ] Shows estimated airspeed from ground speed
- [ ] Secondary shows "EST" (estimated)
- [ ] Shows "NO DATA" when speed < 1 km/h
- [ ] Updates every 2 seconds

### Ground Speed
- [ ] Shows GPS speed in knots
- [ ] Secondary shows "GPS"
- [ ] Updates every 2 seconds
- [ ] Shows "NO GPS" when GPS unavailable

---

**Document Version**: 1.0
**Next Review**: When adding new card types or changing data flow
**Maintained By**: Reference this document when making DFCards changes

*Generated by Claude Code Analysis*
