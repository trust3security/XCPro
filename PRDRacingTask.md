# Racing Task Product Requirements Document (PRD)

## Overview

A **Racing Task** is a competitive gliding navigation course consisting of waypoints that pilots must navigate in sequence to complete the task. Racing tasks are designed for speed and efficiency, with pilots competing to complete the course in the shortest time possible while following specific geometric rules for each waypoint type.

### Key Characteristics
- **Competitive Focus**: Optimized for speed and racing performance
- **FAI Compliance**: Follows Federation Aeronautique Internationale rules
- **Geometric Precision**: Each turnpoint type has specific observation zone geometries
- **Optimal Path Calculation**: Uses tangent-based algorithms for shortest legal distance
- **Real-time Validation**: Continuous course validation and distance updates

## Task Architecture

### Task Type Separation
Racing tasks are completely autonomous within the application architecture:
- **Zero Cross-Contamination**: No shared calculation logic with AAT or DHT tasks
- **Independent Management**: `RacingTaskManager.kt` handles all racing-specific operations
- **Dedicated Models**: `RacingTask.kt` and `RacingWaypoint.kt` provide racing-specific data structures

## Task Components

### 1. Start Points
Start points define how pilots begin the racing task:

#### START_LINE
- **Geometry**: Line perpendicular to the first task leg
- **Parameters**: `lineLength` (meters) - defines the width of the start line
- **Usage**: Most common for racing tasks, allows multiple pilots to start simultaneously
- **Validation**: Requires positive line length

#### START_CYLINDER
- **Geometry**: Circular cylinder around the start point
- **Parameters**: `cylinderRadius` (meters) - defines the radius of the start cylinder
- **Usage**: Alternative start method, pilot must exit the cylinder to start timing
- **Validation**: Requires positive cylinder radius

#### BGA_START_SECTOR
- **Geometry**: British Gliding Association specified start sector
- **Parameters**: `sectorRadius` (meters) - defines the radius of the start sector
- **Usage**: BGA competition standard start method
- **Validation**: Requires positive sector radius
- **Visual**: Displayed as sector extending from start point


### 2. Finish Points
Finish points define how pilots complete the racing task:

#### FINISH_LINE
- **Geometry**: Line perpendicular to the final task leg
- **Parameters**: `lineLength` (meters) - defines the width of the finish line
- **Usage**: Standard finish for racing tasks
- **Validation**: Requires positive line length

#### FINISH_CYLINDER
- **Geometry**: Circular cylinder around the finish point
- **Parameters**: `cylinderRadius` (meters) - defines the radius of the finish cylinder
- **Usage**: Alternative finish method, pilot must enter cylinder to finish
- **Validation**: Requires positive cylinder radius

### 3. Turnpoints
Turnpoints are intermediate waypoints that pilots must navigate around. Racing tasks support five distinct turnpoint types:

#### TURN_POINT_CYLINDER
- **Geometry**: Standard circular cylinder
- **Parameters**:
  - `cylinderRadius` (meters) - typically 500m for racing
  - Finite radius with clear boundaries
- **Navigation**: Pilot must touch or enter the cylinder to satisfy the turnpoint
- **Distance Calculation**: Uses optimal tangent points to minimize task distance
- **Usage**: Most common turnpoint type, suitable for precise navigation
- **Visual**: Displayed as red circle on map

#### FAI_QUADRANT
- **Geometry**: 90-degree sector with infinite radius
- **Parameters**:
  - Sector angle: 90 degrees
  - Radius: Infinite (no outer boundary)
  - Orientation: Aligned with task geometry and turn direction
- **Navigation**: Pilot must enter the correct quadrant based on task direction
- **Distance Calculation**: Complex orientation rules determine optimal path
- **Usage**: FAI-standard for official competitions, allows flexible routing
- **Visual**: Displayed as 90-degree sector extending to visual limit (20km)
- **Rules**: Direction-dependent - left/right turn determines which quadrant is valid

#### KEYHOLE (Standard FAI Keyhole)
- **Geometry**: Composite - 500m cylinder + infinite 90-degree sector
- **Parameters**:
  - Inner cylinder: 500m radius (fixed)
  - Sector: 90-degree quadrant extending infinitely
  - Combined observation zone
- **Navigation**: Pilot can satisfy turnpoint by entering either:
  1. The 500m inner cylinder (any approach direction), OR
  2. The correct 90-degree sector (direction-dependent)
- **Distance Calculation**: Algorithm chooses optimal between cylinder edge or sector entry
- **Usage**: Flexible turnpoint allowing both precise and strategic approaches
- **Visual**: Shows 500m cylinder with adjacent infinite sector
- **Strategic Value**: Offers tactical options - close approach (cylinder) or wide approach (sector)

#### SYMMETRIC_QUADRANT
- **Geometry**: 90-degree sectors on both sides of the task bisector
- **Parameters**:
  - Sector angle: 90 degrees each side
  - Radius: Infinite (no outer boundary)
  - Orientation: Perpendicular to bisector of incoming and outgoing task legs
- **Navigation**: Pilot can enter either of the two symmetric sectors
- **Distance Calculation**: Both sectors offer equal optimal path options
- **Usage**: Provides routing flexibility while maintaining geometric precision
- **Visual**: Two 90-degree sectors extending infinitely on both sides of bisector

#### DAeC_KEYHOLE (German Aviation Association Keyhole)
- **Geometry**: Variant of standard keyhole with German specific rules
- **Parameters**:
  - Inner cylinder: 500m radius (fixed)
  - Sector: 90-degree quadrant with DAeC-specific orientation rules
  - German gliding association specifications
- **Navigation**: Similar to standard keyhole but with DAeC geometric rules
- **Distance Calculation**: Uses DAeC-compliant algorithms
- **Usage**: Required for German gliding competitions
- **Visual**: Similar to standard keyhole but with DAeC orientation

## Technical Implementation

### Distance Calculation Algorithms

#### Optimal FAI Racing Path
- **Method**: Tangent-based calculations to find shortest legal path
- **Cylinder Handling**: Uses optimal touch points on cylinder edges, not centers
- **Sector Handling**: Complex geometry to find optimal sector entry points
- **Finish Integration**: Includes finish cylinder/line geometry in total distance
- **Real-time Updates**: Recalculates when turnpoint types or parameters change

#### Center-to-Center Distance (Reference)
- **Method**: Simple great circle distances between waypoint centers
- **Purpose**: Nominal distance reference, not used for competition scoring
- **Usage**: Quick approximation and validation checks

### Task Validation

#### Structural Validation
- **Minimum Waypoints**: Racing tasks require at least 2 waypoints (start + finish)
- **Maximum Turnpoints**: Limited to 15 turnpoints per task
- **Unique Names**: No duplicate turnpoint names allowed
- **Minimum Separation**: Waypoints must be at least 100m apart

#### Geometric Validation
- **Parameter Validation**: Each turnpoint type validates its required parameters
- **Observation Zone**: Ensures observation zones don't overlap inappropriately
- **Course Line**: Validates that course line touches observation zones correctly
- **FAI Compliance**: Ensures all geometric rules meet FAI standards

#### Performance Validation
- **Distance Limits**: Validates task distance is within reasonable bounds
- **Leg Length**: Ensures individual leg lengths meet minimum requirements
- **Turn Angles**: Validates turn angles are achievable

### Map Visualization

#### Waypoint Display
- **Markers**: Red circles with white borders for racing waypoints
- **Numbering**: Sequential numbering (START, TP1, TP2, ..., FINISH)
- **Role Indication**: Color coding - Green (START), Red (FINISH), Blue (TURNPOINTS)
- **Interactive**: Tap waypoints for detailed information

#### Course Line
- **Path**: Red line connecting optimal touch points (not waypoint centers)
- **Width**: 3px with 80% opacity
- **Geometry**: Shows actual FAI racing line, not straight connections
- **Updates**: Real-time updates when turnpoint parameters change

#### Observation Zones
- **Cylinders**: Filled circles with appropriate radius
- **Sectors**: Sector shapes extending to visual limit
- **Keyhole**: Combined cylinder + sector visualization
- **Colors**: Semi-transparent overlays for clear navigation

### Data Management

#### Task Storage
- **Format**: JSON serialization via Gson
- **Persistence**: SharedPreferences for current task
- **Export**: QR code generation for task sharing
- **Import**: QR code scanning for task loading

#### Task Sharing
- **QR Code**: Compact encoding of complete task definition
- **Format**: `GLIDER_TASK:RACING:waypoint1|waypoint2|...`
- **Portability**: Cross-platform task exchange
- **Validation**: Automatic validation on import

## User Experience

### Task Creation Workflow
1. **Task Type Selection**: User selects "Racing" from task type options
2. **Waypoint Addition**: Tap + button to add waypoints sequentially
3. **Turnpoint Configuration**: Tap waypoints to configure types and parameters
4. **Real-time Preview**: Map updates showing course line and distances
5. **Validation Feedback**: Immediate validation errors and warnings
6. **Save/Share**: Export via QR code or save to device

### Task Editing Features
- **Drag & Drop**: Reorder waypoints by dragging
- **Type Changes**: Change turnpoint types with immediate recalculation
- **Parameter Tuning**: Adjust cylinder radii and line lengths
- **Visual Feedback**: Course line updates in real-time
- **Validation**: Continuous validation with error highlighting

### Task Information Display
- **Distance**: Real-time optimal FAI racing distance
- **Waypoint Count**: Current number of waypoints
- **Task Type**: Clearly labeled as "Racing"
- **Validation Status**: Green checkmark for valid tasks
- **Summary**: "Racing Task: X waypoints, Y.Z km"

## Competition Integration

### FAI Compliance
- **Official Rules**: Implements current FAI racing task regulations
- **Scoring**: Distance calculations suitable for official competition scoring
- **Validation**: Ensures tasks meet competition requirements
- **Documentation**: Provides task parameters for official documentation

### Task Analysis
- **Performance Metrics**: Distance, optimal speeds, leg analysis
- **Strategic Options**: Shows routing choices for keyhole and sector turnpoints
- **Risk Assessment**: Identifies potential issues or challenges
- **Weather Integration**: Compatible with weather routing analysis

## Future Enhancements

### Advanced Features
- **Turn Direction Indicators**: Visual arrows showing required turn directions
- **Altitude Integration**: Start height limits and altitude considerations
- **Wind Optimization**: Wind-adjusted optimal routing
- **Multi-day Tasks**: Support for multi-day racing events

### UI Improvements
- **3D Visualization**: Three-dimensional task preview
- **Terrain Integration**: Terrain-aware task validation
- **Animation**: Animated course line drawing
- **Accessibility**: Enhanced accessibility features

## Technical Architecture Summary

### Code Organization
```
tasks/racing/
├── RacingTaskManager.kt        # Main racing task management
├── models/
│   ├── RacingTask.kt          # Complete racing task model
│   ├── RacingWaypoint.kt      # Racing waypoint types
│   └── RacingLatLng.kt        # Coordinate handling
├── turnpoints/               # Turnpoint-specific calculations
├── display/                  # Visualization components
└── validation/              # Task validation logic
```

### Key Classes
- **RacingTask**: Main task data model with full FAI compliance
- **RacingWaypoint**: Waypoint model with all five turnpoint types
- **RacingTaskManager**: Complete task management and operations
- **SimpleRacingTask**: Lightweight task representation for UI

### Integration Points
- **TaskManagerCoordinator**: Routes racing tasks to appropriate handlers
- **MapLibre**: Map visualization and interaction
- **Compose UI**: Modern Android UI framework integration
- **QR Codes**: Task sharing and import functionality

This racing task system provides a comprehensive, FAI-compliant solution for competitive gliding navigation with support for all standard turnpoint types and advanced geometric calculations.