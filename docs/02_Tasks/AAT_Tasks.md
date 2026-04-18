
# AAT (Assigned Area Tasks) - Complete Guide

**Last Updated:** 2026-03-29
**Status:** ... Current - Consolidated from multiple sources
**Module:** task logic in `feature/tasks/src/main/java/.../tasks/aat/`; rendering/edit surfaces in `feature/map` and `feature/map-runtime`

> **Quick Links:** [Task Separation Rules](./Task_Type_Separation.md) | [Default Values](./Default_Values.md) | [AAT ARCHITECTURE](../../feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ARCHITECTURE.md)

---

## "- What is an AAT?

Assigned Area Task (AAT) is a **strategic** competitive gliding format:
- Pilots fly through **large areas**, not fixed points
- Pilot **chooses optimal path** within each area
- **Minimum time requirement** (typically 2-4 hours)
- Winner has **highest speed** after minimum time
- **Movable turnpoints** - pilot adjusts during flight

**Key Difference from Racing:** Flexible routing within areas vs. fixed turnpoints.

---

## z AAT Task Specifications

### Area Geometry Types

AAT tasks use assigned areas instead of fixed points:

#### 1. Circle (Most Common)
- **Default radius:** 10km
- **Rule:** Pilot must enter the circular area
- **Strategy:** Pilot can fly anywhere within circle to optimize distance/conditions
- **Use case:** Standard AAT areas
- **FAI reference:** SC3 Section 6.3.2

#### 2. Sector
- **Geometry:** Circular sector (pie slice)
- **Parameters:**
  - Inner radius (optional, often 0)
  - Outer radius (e.g., 20km)
  - Start bearing (e.g., 45deg)
  - End bearing (e.g., 135deg)
- **Orientation:** Perpendicular to task line bisector, outward from turn
- **Rule:** Pilot must enter the sector
- **Use case:** Directional constraints, wind optimization
- **FAI reference:** SC3 Section 6.3.2

#### 3. Keyhole (AAT Version)
- **Inner cylinder:** Variable radius (e.g., 0.5km)
- **Outer sector:** 90deg sector with variable radius (e.g., 20km)
- **Orientation:** Sector opens away from previous waypoint (same as Racing keyhole logic)
- **Rule:** Must enter either cylinder OR sector
- **Use case:** Flexible routing with safety margin near waypoint
- **FAI reference:** SC3 Annex A Section 6.3.2

### Start Types

| Type | Geometry | Default Size | Use Case |
|------|----------|--------------|----------|
| **AAT Start Line** | Perpendicular to first area | 10km | Standard |
| **AAT Start Cylinder** | Circle around start | 10km radius | Alternative |
| **AAT Start Sector** | 180deg sector | Faces away from first area | Competition |

### Finish Types

| Type | Geometry | Default Size | Use Case |
|------|----------|--------------|----------|
| **AAT Finish Line** | Perpendicular from last area | 3km | Standard |
| **AAT Finish Cylinder** | Circle around finish | 3km radius | Alternative |

---

## +/- Time Requirements

### Minimum Task Time

**Critical AAT Concept:** AAT tasks have a **minimum time** pilots must fly:
- Typical: 2-4 hours depending on competition class
- Pilot CANNOT finish before minimum time
- **Scoring:** Speed = Distance / MAX(elapsed_time, minimum_time)

**Strategic Implications:**
- Fly farther into areas if finishing early
- Optimize route to hit minimum time exactly
- Weather changes mid-flight? Adjust area penetration

### Maximum Task Time (Optional)

Some competitions set maximum task time:
- Typical: 4-5 hours
- Penalties if exceeded
- Safety consideration (daylight, fuel)

---

## " Default Values

See [Default_Values.md](./Default_Values.md) for complete reference.

**Quick Summary:**
- Start lines: **10km** (universal default)
- Finish cylinders: **3km** (universal default)
- AAT areas: **10km** circle (task-specific default)

**Preservation Rule:** User customizations preserved when switching task types.

---

## z Movable Target Points

### Strategic Positioning

AAT's **key feature**: Pilots can move target points within areas during flight.

**Use Cases:**
1. **Weather Optimization:** Move toward better thermal conditions
2. **Wind Advantage:** Position for optimal tailwind component
3. **Distance Maximization:** Fly deeper into areas to increase task distance
4. **Time Management:** Adjust penetration to hit minimum time exactly

### Implementation

**UI:** Drag-and-drop target pins on map
**Constraint:** Target point must stay within assigned area boundary
**Real-time:** Task distance updates as points move

**Source Code:** `aat/map/AATMovablePointManager.kt`

---

## section(R) Distance Calculations

### AAT Distance Types

Unlike Racing (one optimal distance), AAT has **multiple distance values**:

1. **Minimum Distance:** Shortest path through nearest area edges
2. **Maximum Distance:** Longest path through farthest area edges
3. **Nominal Distance:** Path through area centers
4. **Actual Distance:** Pilot's chosen path through target points

**Source Code:** `aat/calculations/AATDistanceCalculator.kt`

### Credited Fixes

**Post-flight scoring** uses **credited fixes**:
- One fix per area (point where pilot achieved the area)
- Actual distance calculated using credited fixes
- Speed = Actual Distance / MAX(elapsed_time, minimum_time)

**Source Code:** `aat/areas/AreaBoundaryCalculator.kt`

---

## -- File Organization

### AAT Module Structure

```
app/src/main/java/.../tasks/aat/
"
""" AATTaskManager.kt            * Main coordinator
""" AATTaskCalculator.kt         * Distance/speed calculations
""" AATTaskDisplay.kt            * Map visualization
""" AATTaskValidator.kt          * FAI compliance validation
"
""" models/
"   """ AATWaypoint.kt          * AAT-specific waypoint model
"   """ AATArea.kt              * Area geometry
"   """ AATTask.kt              * Task definition
"   """" AATResult.kt            * Post-flight scoring
"
""" calculations/
"   """ AATMathUtils.kt         * Math utilities (autonomous)
"   """ AATDistanceCalculator.kt * Distance calculations
"   """" AATSpeedCalculator.kt   * AAT speed formula
"
""" areas/
"   """ CircleAreaCalculator.kt
"   """ SectorAreaCalculator.kt
"   """" AreaBoundaryCalculator.kt
"
""" validation/
"   """ ComprehensiveAATValidator.kt
"   """ FAIComplianceRules.kt
"   """" README.md               * Validation system docs
"
""" map/
"   """ AATMovablePointManager.kt
"   """ AATMapInteractionHandler.kt
"   """" AATTargetPointDragHandler.kt
"
""" geometry/
"   """" AATGeometryGenerator.kt
"
"""" ui/
    """ AATTaskPointTypeSelector.kt
    """" AATEditModeOverlay.kt
```

### Refactoring Status

**... Complete (Stages 1-6):**
- Stage 1: Geometry generation extracted
- Stage 2: File I/O separated
- Stage 3: Navigation management isolated
- Stage 4: Interactive editing modularized
- Stage 5: Validation bridge created
- Stage 6: Rendering extracted

**... Code Cleanup (Jan 2025):**
- Removed 1,220 lines of dead code
- Zero debug statements
- Production-ready

See [aat/ARCHITECTURE.md](../../feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ARCHITECTURE.md) for details.

---

## - FAI Compliance

### AAT Validation

**Rules Enforced (FAI Section 6.3.2):**
1. Minimum task time per-milleYen 30 minutes
2. Areas separated by per-milleYen 1km
3. Valid area geometry (radius bounds)
4. Minimum distance reasonable for minimum time
5. Areas achieved in sequence

**Validators:**
- `AATTaskValidator.kt` - Basic validation
- `ComprehensiveAATValidator.kt` - Full FAI compliance
- `FAIComplianceRules.kt` - Official rule specifications

See [aat/validation/README.md](../../feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/README.md) for complete guide.

### Competition Classes

AAT validation supports specific competition classes:
- **Club Class:** 2hr min, 100-400km, relaxed rules
- **Standard Class:** 2.5hr min, 150-500km, standard compliance
- **Open Class:** 2.5hr min, 200-750km, strict rules
- **World Class:** 2.5hr min, 150-500km, maximum compliance

---

## z UI Components

### AATTaskPointTypeSelector

Dropdown interface for AAT-specific types:
- Start types: Line, Cylinder, Sector
- Finish types: Cylinder, Line
- Area types: Cylinder, Sector, Keyhole
- Area radius controls
- Target point positioning

**Location:** `aat/ui/AATTaskPointTypeSelector.kt`

### Map Display

AAT tasks display:
- Green task line (#4CAF50)
- Assigned area circles/sectors
- Movable target point pins (red when in edit mode)
- Start/finish zones
- Task line through target points (updated in real-time)

**Location:** `aat/AATTaskDisplay.kt`, `aat/rendering/AATTaskRenderer.kt`

---

## sectiona Testing

### Unit Tests

Each AAT component has dedicated tests:
```bash
# Run AAT tests only
./gradlew test --tests "*AAT*"

# Specific component tests
./gradlew test --tests "AATDistanceCalculatorTest"
./gradlew test --tests "ComprehensiveAATValidatorTest"
./gradlew test --tests "CircleAreaCalculatorTest"
```

### FAI Compliance Testing

Comprehensive validation test suite:
```bash
# Full FAI compliance check
./gradlew test --tests "FAIComplianceRulesTest"

# Competition class validation
./gradlew test --tests "CompetitionValidationTest"
```

---

## "section Common Operations

### Creating an AAT Task

```kotlin
val aatManager = AATTaskManager()

// Add start
aatManager.addWaypoint(startWaypoint, role = START, type = AAT_START_LINE)

// Add assigned areas
aatManager.addWaypoint(area1, role = TURNPOINT, type = AAT_CYLINDER, radius = 10.0)
aatManager.addWaypoint(area2, role = TURNPOINT, type = AAT_SECTOR, radius = 20.0, angle = 90.0)
aatManager.addWaypoint(area3, role = TURNPOINT, type = AAT_CYLINDER, radius = 15.0)

// Add finish
aatManager.addWaypoint(finishWaypoint, role = FINISH, type = AAT_FINISH_CYLINDER)

// Set minimum time
aatManager.updateAATTimes(minTime = Duration.ofHours(3), maxTime = null)

// Calculate distance range
val minDistance = aatManager.calculateMinimumDistance()
val maxDistance = aatManager.calculateMaximumDistance()
```

### Moving Target Points

```kotlin
// Enter edit mode for area 2
aatManager.setEditMode(waypointIndex = 2, enabled = true)

// Move target point within area
aatManager.updateTargetPoint(
    index = 2,
    lat = newLat,
    lon = newLon
)

// Task distance updates automatically
val newDistance = aatManager.calculateAATDistance()

// Exit edit mode
aatManager.setEditMode(waypointIndex = 2, enabled = false)
```

### Validating AAT Task

```kotlin
val validator = ComprehensiveAATValidator()
val result = validator.validateTask(aatTask, competitionClass = "Standard")

println("Valid: ${result.isValid}")
println("Errors: ${result.criticalErrors}")
println("Warnings: ${result.warnings}")
println("Grade: ${result.validationScore.letterGrade}")
```

---

## z(R) Strategic Use

### Real-Time Optimization

AAT pilots continuously optimize during flight:

1. **Start of Task:** Set conservative target points (near area centers)
2. **Mid-Flight:** Adjust based on actual conditions
   - Better weather? Move targets deeper into areas
   - Worse weather? Move targets closer
3. **Time Management:**
   - Finishing early? Fly deeper to maximize distance
   - Running late? Fly minimum path

### Weather Adaptation

**Example Scenario:**
- Minimum time: 3 hours
- Strong thermal street discovered NE of planned route
- **Action:** Move target points toward thermal street
- **Result:** Increased distance while maintaining minimum time

---

## " Related Documentation

### Core Docs
- [Task_Type_Separation.md](./Task_Type_Separation.md) - **MUST READ** separation rules
- [Default_Values.md](./Default_Values.md) - Default sizes and preservation
- [Racing_Tasks.md](./Racing_Tasks.md) - Comparison with Racing

### AAT-Specific
- [aat/ARCHITECTURE.md](../../feature/map/src/main/java/com/trust3/xcpro/tasks/aat/ARCHITECTURE.md) - Module architecture
- [aat/validation/README.md](../../feature/map/src/main/java/com/trust3/xcpro/tasks/aat/validation/README.md) - Validation guide
- [AAT_PIN_DRAGGING_IMPLEMENTATION.md](./AAT_PIN_DRAGGING_IMPLEMENTATION.md) - Current AAT edit-mode interaction notes

### Historical
- [archive/2026-03-task-doc-cleanup/README.md](./archive/2026-03-task-doc-cleanup/README.md) - Archived task implementation plans and summaries

### Reference
- [Quick_Reference.md](./Quick_Reference.md) - Command cheat sheet
- [AGENTS.md](../../AGENTS.md) - Agent execution contract

---

##   Critical Reminders

1. **NEVER import Racing code** - See [Task_Type_Separation.md](./Task_Type_Separation.md)
2. **ALWAYS use AATMathUtils** - Not shared utilities
3. **Green task lines only** - AAT = #4CAF50, Racing = #0066FF
4. **Validate minimum time** - Must be per-milleYen 30 minutes

---

**Questions?** See [ARCHITECTURE README](../ARCHITECTURE/README.md) for documentation entrypoints.


