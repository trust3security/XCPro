# Implement FAI Racing Task for Gliding App

## Project Context
- Android app for gliding/sailplane pilots
- Kotlin DSL, TOML catalogue, Android 11 (API 30)
- Existing codebase has Task models that need to be extended
- Must implement FAI Sporting Code Section 3 compliant Racing Tasks

## Core Requirements

### 1. Racing Task Structure
Implement a Racing Task that consists of:
- **Start**: Mandatory crossing point to begin the task
- **Turn Points**: 1 to n waypoints that must be achieved in sequence
- **Finish**: Mandatory crossing point to complete the task
- **Optimal Distance**: Shortest valid path through all points
- **Minimal Distance**: Actual distance flown (for scoring)

### 2. Start Implementation
Create start geometry handling for:
- **Line Start**: 
  - Perpendicular line to track (typically 1-10km width)
  - Direction of crossing matters (outbound from start)
  - Maximum start altitude (MSL or AGL)
  - Start time window (opening and closing times)
- **Cylinder Start**:
  - Exit cylinder detection (typically 0.5-5km radius)
  - Time and altitude recording at exit

Detection requirements:
- Must detect valid crossing (line) or exit (cylinder)
- Record exact time and position of start
- Handle multiple start attempts (restarts allowed until passing first TP)
- Validate altitude is below maximum start height

### 3. Turn Point Implementation
Support these observation zone types:
- **Cylinder**: Fixed radius (typically 0.5-50km)
- **FAI Sector**: 90° sector, bisector perpendicular to legs
- **Keyhole**: Cylinder (500m) + 90° sector (10km)
- **Line**: Perpendicular to track

Turn point achievement:
- Must enter observation zone
- Record time and position of achievement
- Sequential validation (can't skip TPs)
- Handle AAT areas (pilot chooses point within area)

### 4. Finish Implementation
- **Line Finish**: Cross perpendicular line
- **Cylinder Finish**: Enter cylinder
- Record exact crossing time and position
- Validate minimum task time (if applicable)

### 5. Distance Calculations
Implement distance calculations using:
- FAI Earth model (sphere R = 6371.0 km)
- Great circle distances between points
- Task distance = sum of legs from start through all TPs to finish
- Scored distance includes actual path flown in observation zones

### 6. Validation Rules
Implement FAI validation:
- Minimum task distance checks
- Valid start (correct direction, altitude, time window)
- All turn points achieved in sequence
- Valid finish
- Task time validation (minimum/maximum if set)

### 7. Data Models Required
```kotlin
data class RacingTask(
    val id: String,
    val name: String,
    val start: StartPoint,
    val turnPoints: List<TurnPoint>,
    val finish: FinishPoint,
    val minTaskTime: Duration? = null,
    val maxStartAltitude: Int? = null // meters MSL
)

data class StartPoint(
    val position: LatLng,
    val type: StartType, // LINE, CYLINDER
    val radius: Double? = null, // for cylinder
    val lineLength: Double? = null, // for line
    val direction: Double? = null // bearing for line
)

data class TurnPoint(
    val position: LatLng,
    val name: String,
    val observationZone: ObservationZone
)

data class ObservationZone(
    val type: ZoneType, // CYLINDER, FAI_SECTOR, KEYHOLE, LINE
    val radius: Double? = null,
    val angleInner: Double? = null, // for sectors
    val angleOuter: Double? = null
)

data class TaskFlight(
    val task: RacingTask,
    val startTime: Instant?,
    val turnPointTimes: List<Instant?>,
    val finishTime: Instant?,
    val trackPoints: List<TrackPoint>
)
### 8. Key Functions to Implement
// Core detection functions
fun detectStart(track: List<TrackPoint>, start: StartPoint): StartDetection?
fun detectTurnPoint(track: List<TrackPoint>, tp: TurnPoint, afterTime: Instant): TurnPointDetection?
fun detectFinish(track: List<TrackPoint>, finish: FinishPoint, afterTime: Instant): FinishDetection?

// Validation
fun validateTask(task: RacingTask): List<ValidationError>
fun validateFlight(flight: TaskFlight): FlightValidation

// Distance calculations
fun calculateOptimalDistance(task: RacingTask): Double
fun calculateFlownDistance(flight: TaskFlight): Double

// Geometry helpers
fun isInsideCylinder(point: LatLng, center: LatLng, radius: Double): Boolean
fun crossedLine(p1: TrackPoint, p2: TrackPoint, line: Line): CrossingPoint?
fun isInSector(point: LatLng, sectorCenter: LatLng, bisector: Double, angle: Double): Boolean

### 9. Test Coverage Required
Create comprehensive unit tests for:

Start detection (all types, edge cases)
Turn point detection (each zone type)
Finish detection
Distance calculations (verify against known values)
Task validation rules
Edge cases:

Early start (before window opens)
Restart after passing TP1
Missed turn point
Wrong direction crossing
Altitude violations

### 10. Integration Points
### 10. Integration Points

Current Task model location:
- app/src/main/java/com/example/baseui1/tasks/TaskManager.kt (lines 48+ for RacingTask data class)
- Task types defined in same file (TaskType enum)

**Racing Task Module Structure (IMPLEMENTED):**

Main Racing Task Files:
📁 /app/src/main/java/com/example/baseui1/tasks/racing/
- RacingTaskCalculator.kt     // Main coordinator for racing calculations
- RacingTaskDisplay.kt        // Main coordinator for racing visual display
- RacingTaskValidator.kt      // Validation logic for racing tasks

Turnpoint-Specific Files:
📁 /app/src/main/java/com/example/baseui1/tasks/racing/turnpoints/
- TurnPointInterfaces.kt      // Common interfaces for all turnpoint types
- FAIQuadrantCalculator.kt    // FAI quadrant mathematical calculations
- FAIQuadrantDisplay.kt       // FAI quadrant visual generation (🔧 FIXED 45° offset)
- KeyholeCalculator.kt        // Keyhole sector mathematical calculations
- KeyholeDisplay.kt           // Keyhole sector visual generation (🔧 FIXED 45° offset)
- CylinderCalculator.kt       // Cylinder mathematical calculations
- CylinderDisplay.kt          // Cylinder visual generation
- SymmetricQuadrantCalculator.kt  // Symmetric quadrant calculations
- SymmetricQuadrantDisplay.kt     // Symmetric quadrant visual generation

Architecture Notes:
- Main coordinators (RacingTaskCalculator.kt, RacingTaskDisplay.kt) route to specialized classes
- Each turnpoint type has its own calculator and display class
- No shared logic between turnpoint types (prevents cross-contamination of bugs)
- Follows CLAUDE.md architecture requirements for task type separation

Recently Fixed Issues:
- FAIQuadrantDisplay.kt - Fixed aviation-to-mathematical bearing conversion for FAI quadrants
- KeyholeDisplay.kt - Fixed aviation-to-mathematical bearing conversion for keyhole sectors

Database:
- SharedPreferences for task persistence (no Room/SQLite detected)
- Task saving/loading via JSON serialization in TaskManager
- Storage location: context.getSharedPreferences("task_prefs", Context.MODE_PRIVATE)

Location tracking service:
- No dedicated GPS service class found - appears to use direct location APIs
- Location handling integrated into MapScreen.kt via MapLibre
- **NEEDS IMPLEMENTATION**: FlightTrackingService for real-time GPS tracking

Track recording:
- No flight track recording system detected in current codebase
- Task waypoint recording only (not flight path recording)
- **NEEDS IMPLEMENTATION**: TrackPoint recording and storage

UI components that will use this:
- MapScreen.kt - Main map display with task visualization
- TaskBottomSheet.kt - Task details and management
- SwipeableTaskBottomSheet.kt - Extended task interface
- TaskCreation.kt - Task creation workflow
- ManageBTTab.kt - Task management tab
- TaskMapOverlay.kt - Map overlay for task display

Existing models to integrate with:
- Task models: app/src/main/java/com/example/baseui1/tasks/TaskManager.kt
  - RacingTask data class
  - TaskWaypoint data class
  - WaypointRole enum (START/TURNPOINT/FINISH)
  - StartPointType, FinishPointType, TurnPointType enums
- Racing module: app/src/main/java/com/example/baseui1/tasks/racing/
  - Fully separated calculator and display classes for each turnpoint type
  - Coordinator pattern prevents cross-contamination between task types
- Pilot model location: No dedicated pilot model found
- Flight model location: No flight model detected
- GPS/Track model location: No dedicated GPS model - location handled via MapLibre/Android Location APIs

Key Racing Task Functions to integrate with:
- TaskManager.calculateTaskDistance() - FAI optimal distance calculation
- TaskManager.calculateOptimalFAIRacingDistance() - Core racing algorithm
- TaskManager.findOptimalFAIPath() - Optimal path through turnpoints
- TaskManager.haversineDistance() - Distance calculations
- RacingTaskCalculator.* - Delegates to specific turnpoint calculators
- RacingTaskDisplay.* - Delegates to specific turnpoint display classes
- TurnPointInterfaces - Common contracts for all turnpoint implementations

Map Integration:

MapLibre Android for map display
GeoJSON for waypoint and path rendering
Style layers for visual representation


### 10.5 GPS Implementation Requirements
Location Service Configuration:

Use Android's FusedLocationProviderClient for GPS tracking
Implement as a foreground service for continuous tracking during flight
Handle Android 11 (API 30) location permissions including background location

GPS Settings:

Update frequency: 2Hz (twice per second) for better turn point detection accuracy
Altitude source:

Primary: GPS altitude
Secondary: Barometric sensor if available (use SensorManager to detect pressure sensor)
Record both if available for post-flight analysis


Accuracy filtering:

Ignore points with horizontal accuracy > 20 meters
Flag but record points with accuracy 20-50m (may be useful in poor conditions)
Alert user if accuracy consistently > 50m

Use HIGH_ACCURACY mode during task flying


Track Point Data Structure:
data class TrackPoint(
    val timestamp: Instant,
    val latitude: Double,
    val longitude: Double,
    val altitudeGPS: Double,        // GPS altitude in meters MSL
    val altitudeBaro: Double?,      // Barometric altitude if available
    val speed: Float,                // Ground speed in m/s
    val bearing: Float,              // Track bearing in degrees
    val accuracy: Float,             // Horizontal accuracy in meters
    val verticalAccuracy: Float?,   // Vertical accuracy if available
    val satelliteCount: Int?,       // Number of satellites if available
    val isValid: Boolean            // False if accuracy > 20m
)
Recording Requirements:

Buffer last 100 points in memory for detection algorithms
Save to persistent storage every 30 seconds
Implement circular buffer for memory efficiency
Continue recording even if app is backgrounded
Auto-pause recording if speed < 5 km/h for > 5 minutes (landed)
Auto-resume if speed > 20 km/h (launched again)

Permissions and Service Setup:

// Required permissions in manifest
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

// Foreground service notification
- Show ongoing notification with: current altitude, speed, task progress
- Quick actions: Stop recording, Mark position
11. External References

FAI Sporting Code Section 3 Annex A (Task types)
Use WGS84 coordinates (standard Android Location)
FAI Earth model (sphere R = 6371.0 km)

12. CRITICAL BUG FINDINGS - Course Line Display Issues

### BUG: Racing Task Course Line Does Not Touch Turn Points
**Status**: IDENTIFIED - NEEDS FIX IN SEPARATED RACING MODULE
**Impact**: Course line displays incorrectly for non-cylinder turn points

#### Root Cause Analysis:
Current `TaskManager.kt:findOptimalFAIPath()` function has incomplete turn point handling:
- ✅ `TURN_POINT_CYLINDER`: Proper optimal touch point calculation
- ❌ `FAI_QUADRANT`: Falls through to center point (WRONG)
- ❌ `KEYHOLE_SECTOR`: Falls through to center point (WRONG) 
- ❌ `SYMMETRIC_QUADRANT`: Falls through to center point (WRONG)

#### Required Fix:
```kotlin
// In separated RacingTaskCalculator.kt - NOT TaskManager.kt
private fun calculateOptimalSectorTouchPoint(
    sectorWaypoint: RacingTaskWaypoint,
    previousWaypoint: RacingTaskWaypoint?,
    nextWaypoint: RacingTaskWaypoint
): Pair<Double, Double> {
    // Calculate optimal entry point for FAI sectors
    // Test 20 points along sector edge to find minimum total distance
    // Respect sector boundaries (45-degree span for FAI sectors)
}
```

#### Architecture Requirement:
- **DO NOT ADD TO TaskManager.kt** - File is already oversized
- **MUST IMPLEMENT** in separated racing task module per CLAUDE.md
- **FOLLOWS** racing_task_spec.md separation requirements

#### Test Case:
1. Create racing task with FAI_QUADRANT turn point
2. Verify course line touches sector edge at optimal entry point
3. Test applies to ALL turn point types (not just cylinders)

### BUG: TaskManager.kt File Size Violation
**Status**: ARCHITECTURAL VIOLATION
**Impact**: Violates CLAUDE.md "REQUIRED ARCHITECTURE REFACTOR" requirements

#### CLAUDE.md Violation:
Current `TaskManager.kt` mixes all task types, leading to:
- Bug fixes in one task type can break others
- Mixed logic makes debugging difficult
- Code changes have unpredictable side effects across task types

#### Required Solution:
Implement separated racing task architecture:
```
tasks/
├── TaskManager.kt (coordinator only)
├── racing/
│   ├── RacingTaskCalculator.kt ✅ FAI distance calculations
│   ├── RacingTaskDisplay.kt    ✅ Course line generation  
│   └── RacingTaskValidator.kt  ✅ Turn point validation
```

13. Code Style

Kotlin idioms (use data classes, extension functions where appropriate)
Null safety (avoid !! operator)
Follow Android Studio default formatting
Use coroutines for any async operations

14. Deliverables

Complete implementation of all data models
All detection and calculation functions
GPS tracking service with foreground notification
Comprehensive unit tests (JUnit + Mockito)
Integration tests for complete task scenarios
Documentation for each public function
Example usage code demonstrating task flying workflow
**✅ SEPARATED RACING TASK MODULE** - per CLAUDE.md architecture requirements

# The user has identified a critical issue with the course line calculation. The problem is that the algorithm doesn't
  account for the direction/orientation of sectors when calculating optimal touch points.