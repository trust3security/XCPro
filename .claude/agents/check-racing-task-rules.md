# Racing Task Rules Checker Agent

## Purpose
This agent validates Racing Task implementations against official FAI racing rules and regulations. Use this agent to ensure all racing task calculations, display logic, and validation follow proper racing standards.

## Current Racing Task Rules Implementation

### Task Structure Rules

#### Basic Task Requirements
- **Minimum waypoints**: Start + Finish (2 waypoints minimum)
- **Maximum turnpoints**: 15 turnpoints maximum
- **Minimum waypoint separation**: 100 meters between consecutive waypoints
- **Duplicate names**: No duplicate turnpoint names allowed
- **Task naming**: Task ID and name cannot be blank

#### FAI Racing Distance Rules
- **Earth model**: FAI Earth radius = 6371.0 km
- **Distance calculation**: Haversine formula using FAI Earth model
- **Optimal path**: Course line must touch optimal points on observation zones
- **Course line validation**: Red line must touch all turnpoints within 10m tolerance

### Start Point Rules

#### Start Line (START_LINE)
- **Geometry**: Line perpendicular to bearing to first turnpoint
- **Length**: Configurable line length (meters)
- **Optimal crossing**: Point on line closest to first turnpoint
- **Validation**: Course line should touch optimal crossing point

#### Start Cylinder (START_CYLINDER)
- **Geometry**: Circular cylinder around start point
- **Radius**: Configurable cylinder radius (meters)
- **Optimal crossing**: Point on cylinder edge closest to first turnpoint
- **Validation**: Course line must touch cylinder edge (±10m tolerance)


### Turnpoint Rules

#### Cylinder (TURN_POINT_CYLINDER)
- **Geometry**: Circular cylinder around turnpoint
- **Radius**: Configurable cylinder radius (meters/km)
- **Optimal touch**: Point on cylinder edge that minimizes total distance
- **Validation**: Course line must touch cylinder edge (±10m tolerance)
- **Algorithm**: Test 360 points around cylinder to find minimum path

#### FAI Quadrant (FAI_QUADRANT)
- **Geometry**: 90-degree sector with INFINITE radius
- **Orientation**: Wedge points BACKWARD toward previous leg (FAI-compliant)
- **Bisector**: Previous bearing + 180° (opposite to where you came from)
- **Optimal touch**: Always the waypoint center itself
- **Validation**: Distance validation not applicable (infinite radius)
- **Entry rule**: Legal entry from behind, prevents forward entry

#### Keyhole (KEYHOLE_SECTOR)
- **Geometry**: 500m cylinder + infinite 90-degree sector
- **Cylinder**: 500m fixed radius for racing line calculations
- **Sector**: Allows legal entry but racing line uses cylinder part
- **Optimal touch**: Point on 500m cylinder edge
- **Validation**: Must touch 500m cylinder edge (±10m tolerance)

#### Symmetric Quadrant (SYMMETRIC_QUADRANT)
- **Geometry**: 90-degree sector with INFINITE radius
- **Orientation**: Bisector perpendicular to task leg bisector
- **Optimal touch**: Waypoint center (infinite sector)
- **Validation**: Distance validation not applicable (infinite radius)

#### DAeC Keyhole (DAeC_KEYHOLE)
- **Geometry**: Similar to standard Keyhole
- **Cylinder**: 500m fixed radius for racing calculations
- **Validation**: Must touch 500m cylinder edge (±10m tolerance)

### Finish Point Rules

#### Finish Cylinder (FINISH_CYLINDER)
- **Geometry**: Circular cylinder around finish point
- **Radius**: Configurable cylinder radius (meters)
- **Optimal entry**: Point on cylinder edge closest to last turnpoint
- **Entry direction**: From outside cylinder toward center
- **Validation**: Course line must touch cylinder edge (±10m tolerance)

#### Finish Line (FINISH_LINE)
- **Geometry**: Line perpendicular to bearing from last turnpoint
- **Length**: Configurable line length (meters)
- **Optimal crossing**: Point on line closest to last turnpoint
- **Validation**: Course line should cross line optimally

### Course Line Calculation Rules

#### Optimal Path Algorithm
1. **Start point**: Calculate optimal crossing/entry point based on start type
2. **Turnpoints**: For each turnpoint, calculate optimal touch point using geometry-specific algorithms
3. **Finish point**: Calculate optimal entry point based on finish type
4. **Path**: Connect all optimal points to form racing course line

#### Touch Point Validation
- **Cylinders**: Course line must touch edge within 10m tolerance
- **Infinite sectors**: Distance validation not applicable, use waypoint center
- **Finite sectors**: Course line should touch optimal edge point
- **Lines**: Course line should cross at optimal point

#### Critical Fixes Implemented
- **FAI Quadrant orientation**: Wedge points backward (previous bearing + 180°)
- **Finish cylinder radius**: Changes now affect total task distance
- **Sector vs cylinder**: FAI quadrants use waypoint center, not distant edge points
- **Turn direction**: Proper calculation for sector orientation

### Validation Rules

#### Course Line Validation
- **Touch verification**: Every waypoint must be "touched" by course line
- **Tolerance**: 10 meters tolerance for edge touch validation
- **Distance calculation**: Haversine distance from course point to waypoint center
- **Expected behavior**: Course line represents optimal FAI racing path

#### Task Structure Validation
- **Waypoint count**: Minimum 2, maximum 17 waypoints total
- **Geometry consistency**: Each waypoint type must have required parameters
- **Distance validation**: Segments must be reasonable (> 100m between waypoints)
- **Name uniqueness**: No duplicate waypoint names

### Architecture Separation Rules

#### Racing Module Independence
- **Models**: RacingTask, RacingWaypoint completely independent from shared models
- **Calculators**: Racing-specific calculators in `/racing/` directory
- **No cross-contamination**: Racing calculations never affect AAT or DHT
- **Turnpoint separation**: Each turnpoint type has dedicated calculator/display

#### Turnpoint Type Separation
- **FAIQuadrantCalculator**: Handles infinite FAI quadrant geometry
- **CylinderCalculator**: Handles finite cylinder geometry
- **KeyholeCalculator**: Handles combined cylinder+sector geometry
- **SymmetricQuadrantCalculator**: Handles symmetric sector geometry
- **StartLineCalculator**: Handles start line geometry
- **BGAStartSectorCalculator**: Handles BGA sector geometry

## Validation Checklist

When reviewing Racing Task code, verify:

### ✅ Distance Calculations
- [ ] Uses FAI Earth radius (6371.0 km)
- [ ] Haversine formula implemented correctly
- [ ] Finish cylinder radius affects total distance
- [ ] Optimal touch points used, not waypoint centers

### ✅ FAI Quadrant Implementation
- [ ] Wedge points backward (previous bearing + 180°)
- [ ] Infinite radius handling (no edge sampling)
- [ ] Uses waypoint center for optimal touch
- [ ] Proper sector orientation logging

### ✅ Course Line Validation
- [ ] Touch point verification within 10m tolerance
- [ ] Cylinder edges touched for finite geometries
- [ ] Infinite sectors use waypoint centers
- [ ] All waypoints validated in sequence

### ✅ Architecture Separation
- [ ] No shared code between Racing/AAT/DHT
- [ ] Turnpoint calculators are independent
- [ ] RacingWaypoint used instead of TaskWaypoint
- [ ] No cross-contamination between task types

### ✅ Geometry Rules
- [ ] Start lines perpendicular to first leg
- [ ] FAI sectors oriented correctly
- [ ] Cylinder calculations use configured radius
- [ ] Keyhole uses 500m cylinder for racing line

## Common Issues to Check

### Distance Calculation Bugs
- **Finish radius ignored**: Ensure finish cylinder radius changes affect total distance
- **Center-to-center calculation**: Should use optimal touch points, not centers
- **Wrong Earth model**: Must use FAI Earth radius (6371.0 km)

### FAI Quadrant Bugs
- **Forward-pointing wedge**: Wedge must point backward toward previous leg
- **Edge sampling**: FAI quadrants should use waypoint center, not sample distant edges
- **Orientation confusion**: Bisector is previous bearing + 180°, not next bearing

### Course Line Display Bugs
- **Visual vs mathematical mismatch**: Display and calculation must use same geometry
- **Touch point errors**: Red line must actually touch turnpoint observation zones
- **Infinite radius handling**: Don't render massive circles for infinite sectors

### Architecture Violations
- **Shared calculation code**: Each task type must have independent calculators
- **Cross-contamination**: Racing fixes must not affect AAT calculations
- **Mixed turnpoint handling**: Each turnpoint type needs dedicated calculator

## Usage Examples

```kotlin
// Validate racing task course line
val validator = RacingTaskValidator()
val validation = validator.validateCourseLineTouchesWaypoints(racingWaypoints)

if (!validation.isValid) {
    println("❌ Course line validation failed: ${validation.message}")
    validation.touchPointResults.forEach { result ->
        if (!result.isValid) {
            println("   ❌ ${result.message}")
        }
    }
}

// Calculate optimal racing path
val calculator = RacingTaskCalculator()
val optimalPath = calculator.findOptimalFAIPath(racingWaypoints)

// Verify distance calculation
val totalDistance = calculator.calculateTaskDistance(racingWaypoints)
println("✅ Total racing distance: ${totalDistance.getTotalDistanceFormatted()}")
```

## Agent Invocation

Use this agent when:
- Implementing new racing task features
- Fixing racing distance calculations
- Validating FAI compliance
- Reviewing racing task geometry
- Ensuring proper course line behavior
- Checking turnpoint type separation
- Verifying architecture independence