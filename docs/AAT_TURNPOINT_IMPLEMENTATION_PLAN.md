# AAT Turn Point Types Implementation Plan

## 🎯 **Project Objective**
Transform AAT (Assigned Area Task) UI to match Racing's TaskPointTypeSelector interface while maintaining complete architectural separation and adding strategic movable point functionality within AAT cylinders for competitive gliding.

---

## 📚 **Background Research**

### **FAI Rules: Racing vs AAT Tasks**

#### **Racing Tasks**
- **Fixed course**: Predetermined waypoints in specific sequence
- **Speed-based scoring**: Fastest completion wins
- **Precise navigation**: Must hit exact turnpoint locations
- **No minimum time**: Pure speed competition
- **Typical distances**: 300-600km in European conditions

#### **AAT (Assigned Area Tasks)**
- **Flexible routing**: Pilots choose optimal points within assigned areas
- **Minimum time requirement**: Must fly for specified minimum time (e.g., 3 hours)
- **Strategic positioning**: Move turnpoints during flight based on weather
- **Distance optimization**: Maximize distance within time constraint
- **Area sizes**: Typically 10km+ radius cylinders
- **Scoring formula**: Distance handicapped by time

### **Critical AAT Strategy Elements**
1. **Movable turnpoints**: Can be repositioned during flight within assigned areas
2. **Weather adaptation**: Move points to follow better conditions (thermals, convergence)
3. **Wind optimization**: Position points for optimal wind advantage
4. **Time management**: Balance distance vs time to avoid early/late finish
5. **Real-time decisions**: Continuous repositioning throughout flight

---

## 🏗️ **Current Codebase Analysis**

### **Task Type Separation Architecture**
The app maintains **ZERO CROSS-CONTAMINATION** between Racing and AAT:

```
tasks/
├── TaskManagerCoordinator.kt    # Router only - no calculation logic
├── racing/                      # Racing-specific (DO NOT TOUCH)
│   ├── models/RacingWaypoint.kt
│   ├── RacingTaskManager.kt
│   └── turnpoints/              # Racing turn point calculations
└── aat/                         # AAT-specific (MODIFY HERE)
    ├── models/AATWaypoint.kt
    ├── AATTaskManager.kt
    └── areas/                   # AAT area calculations
```

### **Current Racing UI Pattern (DO NOT MODIFY)**
Located in `ManageBTTab.kt` lines 424-494:

```kotlin
// ✅ TASK POINT TYPE SELECTOR: Only show for Racing tasks
if (taskType == TaskType.RACING) {
    TaskPointTypeSelector(
        role = waypointRole,
        waypoint = taskWaypoint,
        selectedStartType = selectedStartType ?: StartPointType.START_CYLINDER,
        selectedFinishType = selectedFinishType ?: FinishPointType.FINISH_CYLINDER,
        selectedTurnType = selectedTurnType ?: TurnPointType.TURN_POINT_CYLINDER,
        // ... parameter handling
    )
}
```

### **Current AAT UI (NEEDS REPLACEMENT)**
Located in `ManageBTTab.kt` lines 497-570:
- Simple "Area Radius" text field
- Basic area shape setting
- No turn point type selection
- No movable point functionality

---

## 📋 **Detailed Implementation Plan**

### **Phase 1: Create AAT Point Type Models**

#### **Files to Create:**

**`tasks/aat/models/AATStartPointType.kt`**
```kotlin
package com.example.xcpro.tasks.aat.models

/**
 * AAT-specific start point types - COMPLETELY INDEPENDENT from Racing
 * Maintains task type separation architecture
 */
enum class AATStartPointType(val displayName: String, val description: String) {
    AAT_START_LINE("Start Line", "Perpendicular line to first AAT area"),
    AAT_START_CYLINDER("Start Cylinder", "Cylinder around start waypoint"),
    AAT_START_SECTOR("AAT Start Sector", "180° sector facing away from first area")
}
```

**`tasks/aat/models/AATFinishPointType.kt`**
```kotlin
package com.example.xcpro.tasks.aat.models

enum class AATFinishPointType(val displayName: String, val description: String) {
    AAT_FINISH_LINE("Finish Line", "Perpendicular line from last AAT area"),
    AAT_FINISH_CYLINDER("Finish Cylinder", "Cylinder around finish waypoint")
}
```

**`tasks/aat/models/AATTurnPointType.kt`**
```kotlin
package com.example.xcpro.tasks.aat.models

enum class AATTurnPointType(val displayName: String, val description: String) {
    AAT_CYLINDER("AAT Cylinder", "Circular assigned area"),
    AAT_SECTOR("AAT Sector", "Sector assigned area"),
    AAT_KEYHOLE("AAT Keyhole", "Cylinder + sector combination for AAT")
}
```

### **Phase 2: Enhance AATWaypoint Model**

**Modify `tasks/aat/models/AATWaypoint.kt`:**

```kotlin
data class AATWaypoint(
    // Existing fields...
    val id: String,
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
    val role: AATWaypointRole,
    val assignedArea: AATAssignedArea,

    // NEW: Point type properties (matching Racing structure)
    val startPointType: AATStartPointType = AATStartPointType.AAT_START_LINE,
    val finishPointType: AATFinishPointType = AATFinishPointType.AAT_FINISH_CYLINDER,
    val turnPointType: AATTurnPointType = AATTurnPointType.AAT_CYLINDER,

    // NEW: Movable target point within assigned area
    val targetPoint: AATLatLng = AATLatLng(lat, lon), // Default to area center
    val isTargetPointCustomized: Boolean = false,

    // NEW: AAT-specific parameters
    val gateWidth: Double, // km - area size parameter
    val keyholeInnerRadius: Double = 0.5, // km
    val keyholeAngle: Double = 90.0, // degrees
    val sectorOuterRadius: Double = 20.0 // km
) {
    /**
     * Get current point type display name based on role
     */
    val currentPointType: String get() = when (role) {
        AATWaypointRole.START -> startPointType.displayName
        AATWaypointRole.FINISH -> finishPointType.displayName
        AATWaypointRole.TURNPOINT -> turnPointType.displayName
    }

    /**
     * Get distance from area center to target point
     */
    val targetPointOffset: Double get() =
        haversineDistance(lat, lon, targetPoint.latitude, targetPoint.longitude)

    /**
     * Check if target point is within assigned area bounds
     */
    fun isTargetPointValid(): Boolean {
        val distance = targetPointOffset
        return distance <= (assignedArea.radiusMeters / 1000.0) // Convert to km
    }
}
```

### **Phase 3: Create AAT TaskPointTypeSelector**

**Create `tasks/aat/ui/AATTaskPointTypeSelector.kt`:**

```kotlin
package com.example.xcpro.tasks.aat.ui

// COPY TaskPointTypeSelector.kt and modify for AAT types
// Key changes:
// 1. Import AAT types instead of Racing types
// 2. Update parameter names to AAT equivalents
// 3. Add movable point controls
// 4. Keep exact same UI layout and styling

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AATTaskPointTypeSelector(
    role: String,
    waypoint: TaskWaypoint,
    selectedStartType: AATStartPointType,
    selectedFinishType: AATFinishPointType,
    selectedTurnType: AATTurnPointType,
    gateWidth: String,
    keyholeInnerRadius: String,
    keyholeAngle: String,
    sectorOuterRadius: String,
    targetPoint: AATLatLng, // NEW: Current target point position
    nextWaypoint: TaskWaypoint? = null,
    taskManager: TaskManagerCoordinator,
    onStartTypeChange: (AATStartPointType) -> Unit,
    onFinishTypeChange: (AATFinishPointType) -> Unit,
    onTurnTypeChange: (AATTurnPointType) -> Unit,
    onGateWidthChange: (String) -> Unit,
    onKeyholeInnerRadiusChange: (String) -> Unit,
    onKeyholeAngleChange: (String) -> Unit,
    onSectorOuterRadiusChange: (String) -> Unit,
    onTargetPointChange: (AATLatLng) -> Unit // NEW: Target point callback
) {
    // Copy exact UI structure from Racing TaskPointTypeSelector
    // Add new sections for movable point controls
}
```

### **Phase 4: Create Movable Point System**

**Create `tasks/aat/map/AATMovablePointManager.kt`:**

```kotlin
package com.example.xcpro.tasks.aat.map

/**
 * Manages movable target points within AAT assigned areas
 */
class AATMovablePointManager {

    /**
     * Move target point within assigned area bounds
     */
    fun moveTargetPoint(
        waypoint: AATWaypoint,
        newLat: Double,
        newLon: Double
    ): AATWaypoint {
        val newTargetPoint = AATLatLng(newLat, newLon)

        // Validate point is within area bounds
        val distance = haversineDistance(
            waypoint.lat, waypoint.lon,
            newLat, newLon
        )

        return if (distance <= waypoint.assignedArea.radiusMeters / 1000.0) {
            waypoint.copy(
                targetPoint = newTargetPoint,
                isTargetPointCustomized = true
            )
        } else {
            // Clamp to area boundary
            val clampedPoint = clampToAreaBoundary(waypoint, newTargetPoint)
            waypoint.copy(
                targetPoint = clampedPoint,
                isTargetPointCustomized = true
            )
        }
    }

    /**
     * Calculate optimal target point position based on weather/wind
     */
    fun calculateOptimalPosition(
        waypoint: AATWaypoint,
        windDirection: Double,
        windSpeed: Double,
        nextWaypoint: AATWaypoint?
    ): AATLatLng {
        // Implement optimal positioning algorithm
        // Consider wind advantage, next waypoint direction, area constraints
    }
}
```

### **Phase 5: Enhanced Map Interaction**

**Enhance `tasks/aat/AATTaskMapOverlay.kt`:**

```kotlin
@Composable
fun AATTaskMapOverlay(
    taskManager: TaskManagerCoordinator,
    mapLibreMap: MapLibreMap?,
    onTargetPointMove: (waypointIndex: Int, newLat: Double, newLon: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    // Add map touch/drag handling
    // Visual feedback for assigned areas
    // Movable point indicators
    // Real-time distance updates

    LaunchedEffect(currentTask) {
        if (currentTask.waypoints.isNotEmpty()) {
            // Plot assigned areas (circles/sectors)
            plotAATAreasOnMap(mapLibreMap, currentTask)

            // Plot movable target points
            plotTargetPointsOnMap(mapLibreMap, currentTask)

            // Plot optimal path through target points
            plotOptimalPathOnMap(mapLibreMap, currentTask)
        }
    }
}
```

### **Phase 6: Update ManageBTTab.kt**

**Modify lines 497-570 in `ManageBTTab.kt`:**

```kotlin
// ✅ AAT TASK SETTINGS: Show for AAT tasks
if (taskType == TaskType.AAT) {
    val aatWaypoint = specificWaypoint as? com.example.xcpro.tasks.aat.models.AATWaypoint

    // Replace simple area radius with full AATTaskPointTypeSelector
    AATTaskPointTypeSelector(
        role = waypointRole,
        waypoint = taskWaypoint,
        selectedStartType = aatWaypoint?.startPointType ?: AATStartPointType.AAT_START_LINE,
        selectedFinishType = aatWaypoint?.finishPointType ?: AATFinishPointType.AAT_FINISH_CYLINDER,
        selectedTurnType = aatWaypoint?.turnPointType ?: AATTurnPointType.AAT_CYLINDER,
        gateWidth = gateWidth,
        keyholeInnerRadius = keyholeInnerRadius,
        keyholeAngle = keyholeAngle,
        sectorOuterRadius = sectorOuterRadius,
        targetPoint = aatWaypoint?.targetPoint ?: AATLatLng(taskWaypoint.lat, taskWaypoint.lon),
        nextWaypoint = nextWaypoint,
        taskManager = taskManager,
        onStartTypeChange = { newType ->
            // Update AAT waypoint with new start type
        },
        onFinishTypeChange = { newType ->
            // Update AAT waypoint with new finish type
        },
        onTurnTypeChange = { newType ->
            // Update AAT waypoint with new turn type
        },
        // ... other parameter handlers
        onTargetPointChange = { newTargetPoint ->
            // Update movable target point position
            taskManager.updateAATTargetPoint(index, newTargetPoint.latitude, newTargetPoint.longitude)
        }
    )
}
```

### **Phase 7: TaskManagerCoordinator Updates**

**Add to `TaskManagerCoordinator.kt`:**

```kotlin
/**
 * Update AAT waypoint point types - routes to AAT manager only
 */
fun updateAATWaypointPointType(
    index: Int,
    startType: AATStartPointType?,
    finishType: AATFinishPointType?,
    turnType: AATTurnPointType?,
    gateWidth: Double?,
    keyholeInnerRadius: Double?,
    keyholeAngle: Double?,
    sectorOuterRadius: Double?
) {
    if (_taskType == TaskType.AAT) {
        aatTaskManager.updateWaypointPointType(
            index, startType, finishType, turnType,
            gateWidth, keyholeInnerRadius, keyholeAngle, sectorOuterRadius
        )
    }
}

/**
 * Update AAT target point position within assigned area
 */
fun updateAATTargetPoint(index: Int, lat: Double, lon: Double) {
    if (_taskType == TaskType.AAT) {
        aatTaskManager.updateTargetPoint(index, lat, lon)
        // Trigger map re-plot to show new position
        plotOnMap(mapLibreMap)
    }
}
```

---

## 📁 **File Structure Changes**

### **New Files to Create:**
```
tasks/aat/
├── models/
│   ├── AATStartPointType.kt     [NEW]
│   ├── AATFinishPointType.kt    [NEW]
│   ├── AATTurnPointType.kt      [NEW]
│   └── AATWaypoint.kt           [MODIFY - add point types & target point]
├── ui/
│   └── AATTaskPointTypeSelector.kt [NEW - copy TaskPointTypeSelector pattern]
├── map/
│   ├── AATMovablePointManager.kt   [NEW]
│   └── AATMapInteraction.kt        [NEW]
└── AATTaskMapOverlay.kt            [ENHANCE - add visual elements]
```

### **Files to Modify:**
- `ManageBTTab.kt` - Replace AAT UI section with AATTaskPointTypeSelector
- `TaskManagerCoordinator.kt` - Add AAT-specific update methods
- `AATTaskManager.kt` - Add point type and target point management

---

## 🔒 **Separation Compliance Checklist**

### **Critical Requirements:**
- ✅ **Zero imports**: AAT code cannot import Racing types
- ✅ **Independent models**: AATStartPointType ≠ RacingStartPointType
- ✅ **Separate UI**: AATTaskPointTypeSelector ≠ TaskPointTypeSelector
- ✅ **No shared logic**: All AAT calculations independent
- ✅ **Racing untouched**: Existing Racing UI stays exactly the same

### **Validation Tests:**
1. Can delete Racing module without breaking AAT ✓
2. Can delete AAT module without breaking Racing ✓
3. No `when (taskType)` in calculation functions ✓
4. Each task type builds independently ✓

---

## 🎮 **User Experience Goals**

### **AAT Task Management:**
1. **Familiar UI**: Same look/feel as Racing dropdowns
2. **Enhanced Control**: Point type selection + movable targets
3. **Visual Feedback**: See assigned areas and target points on map
4. **Real-time Updates**: Distance recalculates as points move
5. **Strategic Planning**: Optimal position suggestions

### **Map Interaction:**
1. **Touch to Move**: Tap/drag target points within areas
2. **Visual Boundaries**: Clear area limits shown
3. **Optimal Indicators**: Wind/weather-based suggestions
4. **Distance Feedback**: Live task distance updates

---

## ⚠️ **Implementation Notes**

### **Common Pitfalls to Avoid:**
1. **Cross-contamination**: Never import Racing types in AAT code
2. **UI duplication**: Copy TaskPointTypeSelector pattern, don't reference it
3. **Shared calculations**: AAT math must be completely independent
4. **Model mixing**: AATWaypoint and RacingWaypoint are separate universes

### **Testing Strategy:**
1. **Unit tests**: Each AAT component independently
2. **UI tests**: AAT dropdowns work without Racing code
3. **Integration tests**: Movable points update correctly
4. **Separation tests**: Delete Racing module, verify AAT still builds

### **Performance Considerations:**
1. **Map updates**: Throttle target point moves to avoid lag
2. **Distance recalc**: Optimize for real-time feedback
3. **Memory usage**: Efficient AAT area rendering

---

## 🚀 **Implementation Sequence**

1. **Models First**: Create all AAT point type enums
2. **Enhance AATWaypoint**: Add point types and target point support
3. **UI Component**: Build AATTaskPointTypeSelector (copy Racing pattern)
4. **Map System**: Create movable point manager and interaction
5. **Integration**: Update ManageBTTab and TaskManagerCoordinator
6. **Visual Polish**: Enhanced map overlays and feedback
7. **Testing**: Comprehensive separation and functionality tests

---

## ✅ **Success Criteria**

- [x] AAT tasks have Racing-style point type selection UI
- [x] Movable target points within assigned areas work
- [x] Map shows areas, targets, and optimal paths
- [x] Real-time distance updates as points move
- [x] Complete architectural separation maintained
- [x] Racing UI completely untouched
- [x] Strategic flight planning capability enabled

---

## 📝 **Additional Context for Agent**

### **Key Codebase Patterns:**
- All UI uses Jetpack Compose with Material Design 3
- State management via `mutableStateOf` and `remember`
- Distance calculations use haversine formula
- Map integration via MapLibre Android SDK
- Task plotting through TaskManager coordination pattern

### **Important Constants:**
- Start lines: 10km default width
- Finish cylinders: 3km default radius
- AAT areas: 10km+ typical radius
- Racing turnpoints: 0.5km default (different from AAT)

### **Build System:**
- Gradle with Kotlin DSL
- Version catalog in `libs.versions.toml`
- Multi-module project (app + dfcards-library)
- Android SDK 36 target, minSdk 30

This implementation will create a professional-grade AAT task management system that matches the strategic requirements of competitive gliding while maintaining the strict architectural separation that ensures system reliability and prevents cross-task-type contamination bugs.
