# Racing Task Module - Architecture Documentation

**Last Updated:** 2026-03-12
**Module Status:** ... Well-Organized & Production-Ready

## Current Ownership Note

- `:feature:tasks` now owns the shared racing task editor UI and task-core-facing editor atoms.
- This folder now documents the retained racing map shell in `:feature:map`: map rendering, MapLibre integration, compatibility wrappers, and map-owned task surfaces.
- Files moved to `:feature:tasks` during Phase 5B include `RacingWaypointList*.kt`, `racing/ui/RacingTaskPointTypeSelector.kt`, `SearchableWaypointField.kt`, and `RulesRacingTaskParameters.kt`.

---

## "< Quick Reference

**Working on Racing tasks?** Read this first to understand the module structure and avoid breaking changes.

### Module Organization

```
tasks/racing/
""" ARCHITECTURE.md                    * YOU ARE HERE
"
""" RacingTaskManager.kt               (377 lines) - Main coordinator
""" RacingTaskCalculator.kt            - Distance calculations (COORDINATOR ONLY)
""" RacingTaskDisplay.kt               (636 lines) - Map rendering coordinator
""" RacingTaskValidator.kt             (218 lines) - Task validation
""" RacingTaskInitializer.kt           - Task initialization
""" RacingTaskPersistence.kt           - SharedPreferences operations
""" RacingTaskStorage.kt               (262 lines) - File I/O (CUP format)
""" RacingWaypointManager.kt           (271 lines) - Waypoint CRUD
""" TaskMapRenderRouter.kt             - Shared task map overlay routing integration
""" RacingManageBTTab.kt               (471 lines) - Bottom tab UI
""" RacingGeometryUtils.kt             (241 lines) - Geometry utilities
""" RacingGeoJSONValidator.kt          (246 lines) - GeoJSON validation
"
""" models/
"   """ RacingTask.kt                  (451 lines) - Task data model
"   """" RacingWaypoint.kt              - Waypoint data model
"
""" turnpoints/                        * TURNPOINT TYPE SEPARATION
"   """ CylinderCalculator.kt          - Cylinder geometry calculations
"   """ CylinderDisplay.kt             - Cylinder map rendering
"   """ FAIQuadrantCalculator.kt       - FAI 90deg sector calculations
"   """ FAIQuadrantDisplay.kt          (446 lines) - FAI sector rendering
"   """ FAIStartSectorDisplay.kt       (195 lines) - Start sector rendering
"   """ KeyholeCalculator.kt           (228 lines) - Keyhole calculations
"   """ KeyholeDisplay.kt              (763 lines) - Keyhole rendering
"   """ StartLineCalculator.kt         (155 lines) - Start line calculations
"   """ FinishLineDisplay.kt           (158 lines) - Finish line rendering
"   """" SymmetricQuadrantCalculator.kt - Symmetric quadrant calculations
"
"""" retained in :feature:tasks/
    """ racing/RacingWaypointList.kt      - Shared racing waypoint editor UI
    """ racing/RacingWaypointListItems.kt - Shared racing waypoint item UI
    """ racing/ui/RacingTaskPointTypeSelector.kt - Point type UI
    """" SearchableWaypointField.kt        - Shared waypoint search field
```

---

## z Design Principles

### 1. **Zero Cross-Contamination with AAT Tasks**
- **CRITICAL:** Racing and AAT task types must NEVER share code
- No imports between `tasks/racing/` and `tasks/aat/`
- Each task type is completely autonomous
- See main `CLAUDE.md` for enforcement rules

### 2. **Turnpoint Type Separation**
- **GOAL:** Each turnpoint type has dedicated calculator & display classes
- **NO shared switches:** Avoid `when (turnpointType)` in calculations
- **Single Algorithm Principle:** Visual display and math use SAME geometry engine
- **Examples:**
  - FAI Quadrant: Finite radius 90deg sector (10km default, XCSoar parity), turn direction-based orientation
  - Cylinder: Fixed radius, simple circular geometry
  - Keyhole: Combined 500m cylinder + 10km sector, dual validation

### 3. **Coordinator Pattern**
- `RacingTaskManager` coordinates between specialized modules
- `RacingTaskCalculator` routes to appropriate turnpoint calculators
- `RacingTaskDisplay` routes to appropriate turnpoint displays
- **NO calculation logic in coordinators** - only delegation

---

## "| Module Reference Guide

###  Racing Task Manager (`RacingTaskManager.kt`)

**Purpose:** Main entry point and coordinator for racing tasks

**Responsibilities:**
- Task state management
- Coordinate between specialized modules
- Public API for task operations
- Integration with app components

**Key Methods:**
- Task CRUD operations
- Waypoint management delegation
- Rendering coordination
- Validation coordination

**Dependencies:**
- `RacingTaskCalculator` (distance calculations)
- `RacingTaskDisplay` (map rendering)
- `RacingTaskValidator` (validation)
- `RacingWaypointManager` (waypoint operations)
- `RacingTaskStorage` (file I/O)

**When to Use:**
- External code interacting with racing tasks
- Task creation/modification
- High-level task operations

---

### " Racing Task Calculator (`RacingTaskCalculator.kt`)

**Purpose:** COORDINATOR for distance calculations (routes to turnpoint calculators)

**Responsibilities:**
- Route distance calculations to appropriate turnpoint calculator
- Calculate total task distance using optimal FAI path
- **NO direct calculation logic** - delegates to turnpoint-specific calculators

**Key Methods:**
- `calculateTaskDistance()` - Total task distance
- Routes to: `CylinderCalculator`, `FAIQuadrantCalculator`, `KeyholeCalculator`, etc.

**Pattern:**
```kotlin
// COORDINATOR PATTERN - Routes, doesn't calculate
fun calculateDistance(waypoint: RacingWaypoint): Double {
    return when (waypoint.turnpointType) {
        CYLINDER -> cylinderCalculator.calculateDistance(waypoint)
        FAI_QUADRANT -> faiQuadrantCalculator.calculateDistance(waypoint)
        KEYHOLE -> keyholeCalculator.calculateDistance(waypoint)
        // Each has its own calculator!
    }
}
```

**When to Use:**
- Getting total task distance
- Need optimal path through turnpoints
- FAI-compliant distance calculations

---

### z Racing Task Display (`RacingTaskDisplay.kt`)

**Purpose:** COORDINATOR for map rendering (routes to turnpoint displays)

**Responsibilities:**
- Coordinate map layer rendering
- Route geometry generation to turnpoint-specific displays
- Manage map sources and layers
- **NO direct rendering logic** - delegates to turnpoint displays

**Key Methods:**
- `plotTaskOnMap()` - Main rendering entry point
- `clearTaskFromMap()` - Remove all racing layers
- Routes to: `CylinderDisplay`, `FAIQuadrantDisplay`, `KeyholeDisplay`, etc.

**Pattern:**
```kotlin
// COORDINATOR PATTERN - Routes, doesn't render
fun plotTurnpoint(waypoint: RacingWaypoint) {
    val geometry = when (waypoint.turnpointType) {
        CYLINDER -> cylinderDisplay.generateGeometry(waypoint)
        FAI_QUADRANT -> faiQuadrantDisplay.generateGeometry(waypoint)
        KEYHOLE -> keyholeDisplay.generateGeometry(waypoint)
        // Each has its own display!
    }
    addToMap(geometry)
}
```

**When to Use:**
- Rendering racing tasks on map
- Updating map after task changes
- Clearing racing visualization

---

### ... Racing Task Validator (`RacingTaskValidator.kt`)

**Purpose:** Validate racing tasks for FAI compliance and competition rules

**Responsibilities:**
- Basic validation (minimum waypoints, valid geometry)
- FAI rule compliance
- Competition class requirements
- Distance limits and constraints

**Key Methods:**
- `validateTask()` - Comprehensive validation
- `isValidForCompetition()` - Competition readiness
- `checkFAICompliance()` - FAI rule checks

**Validation Rules:**
- Minimum 2 waypoints (start + finish)
- Valid turnpoint geometry
- Distance requirements met
- Finish cylinder radius constraints

**When to Use:**
- Before saving/exporting tasks
- Competition task verification
- User feedback on task quality

---

### '3/4 Racing Task Storage (`RacingTaskStorage.kt`)

**Purpose:** File I/O operations (CUP format import/export)

**Responsibilities:**
- Save tasks to CUP files
- Load tasks from CUP files
- List saved task files
- Delete task files
- CUP format parsing

**Key Methods:**
- `saveTaskToFile()` - Export to CUP
- `loadTaskFromFile()` - Import from CUP
- `getSavedTasks()` - List files
- `deleteTask()` - Remove file

**CUP Format Support:**
- SeeYou-compatible format
- Waypoint coordinates (DD MM.MMM format)
- Turnpoint types and radii
- Task metadata

**When to Use:**
- Exporting tasks for flight computers
- Importing tasks from competition organizers
- Task backup/restore

---

### " Racing Waypoint Manager (`RacingWaypointManager.kt`)

**Purpose:** Waypoint CRUD operations

**Responsibilities:**
- Add/remove/reorder waypoints
- Role assignment (START/TURNPOINT/FINISH)
- Waypoint property updates
- State consistency

**Key Methods:**
- `addWaypoint()` - Add new waypoint
- `removeWaypoint()` - Remove waypoint (reassigns roles)
- `reorderWaypoints()` - Drag & drop reordering
- `updateWaypointType()` - Change turnpoint type

**Role Management:**
- First waypoint = START
- Last waypoint = FINISH
- Middle waypoints = TURNPOINT
- Automatic reassignment on changes

**When to Use:**
- Task creation UI
- Waypoint editing
- Task modification

---

## "section Turnpoint Type Modules

### "u Cylinder (`turnpoints/CylinderCalculator.kt` & `CylinderDisplay.kt`)

**Geometry:** Fixed radius circle centered on waypoint

**Calculations:**
- Optimal entry/exit points based on approach/departure bearings
- Distance calculation using cylinder edge (not center)
- Simple circular geometry

**Display:**
- Filled circle with configurable radius
- Color-coded by role (blue for racing)
- Rendered as GeoJSON polygon

**Use Cases:**
- Standard turnpoints
- Simple waypoint markers
- Default turnpoint type

**Example:**
```kotlin
val cylinderCalc = CylinderCalculator()
val entryPoint = cylinderCalc.calculateOptimalEntryPoint(
    waypoint,
    previousWaypoint,
    nextWaypoint
)
```

---

### " FAI Quadrant (`turnpoints/FAIQuadrantCalculator.kt` & `FAIQuadrantDisplay.kt`)

**Geometry:** 90deg sector with finite 10km radius, turn direction-based orientation

**Calculations:**
- Finite radius (10km default)
- 90deg sector angle
- Orientation based on: previous waypoint -> current -> next waypoint
- Turn direction determines which quadrant (left/right turn)
- Complex bisector calculations

**Display:**
- Large triangular sector extending to map edge
- Bisector line showing sector orientation
- Transparent fill with colored border
- Shows turn direction visually

**Use Cases:**
- Competition racing tasks
- FAI-compliant distance optimization
- Long-distance turnpoints

**Important:**
- Visual and mathematical algorithms **MUST match**
- Orientation calculated from task geometry
- Currently has known discrepancies (requires fix)

**Example:**
```kotlin
val faiCalc = FAIQuadrantCalculator()
val sector = faiCalc.calculateSectorGeometry(
    waypoint,
    previousWaypoint,
    nextWaypoint
)
// Returns 90deg sector with correct orientation
```

---

### "' Keyhole (`turnpoints/KeyholeCalculator.kt` & `KeyholeDisplay.kt`)

**Geometry:** Combined 500m cylinder + 90deg sector (10km radius)

**Calculations:**
- Two-part validation: cylinder OR sector
- 500m radius cylinder at waypoint center
- 90deg sector extending from cylinder
- Pilot can touch either part to validate turnpoint

**Display:**
- Small 500m cylinder (green fill)
- Large 90deg sector extending outward
- Composite geometry rendering
- Most complex display (763 lines)

**Use Cases:**
- Flexibility in turnpoint validation
- Challenging weather conditions
- Strategic route optimization

**Example:**
```kotlin
val keyholeCalc = KeyholeCalculator()
val isInKeyhole = keyholeCalc.isPointInObservationZone(
    pilotPosition,
    waypoint
)
// Returns true if in cylinder OR sector
```

---

### " Start Line (`turnpoints/StartLineCalculator.kt`)

**Geometry:** Perpendicular line to first leg, configurable width

**Calculations:**
- Line perpendicular to start -> first turnpoint bearing
- Configurable width (typically 2-5km)
- Midpoint at start waypoint coordinates

**Display:**
- Rendered by `FAIStartSectorDisplay.kt`
- Thick green line
- Visible from distance

**Use Cases:**
- Mass starts in competitions
- Fair start positioning
- Clear start timing

---

###  Finish Line (`turnpoints/FinishLineDisplay.kt`)

**Geometry:** Perpendicular line to final leg, configurable width

**Calculations:**
- Line perpendicular to last turnpoint -> finish bearing
- Configurable width (typically 0.5-2km)
- Midpoint at finish waypoint coordinates

**Display:**
- Thick red line
- Prominent visual
- Clear finish zone

**Use Cases:**
- Competition finishes
- Precise timing
- Photo finish capability

---

##  Common Pitfalls

### DON'T: Import AAT Code
```kotlin
// FORBIDDEN:
import com.trust3.xcpro.tasks.aat.*
```

### DON'T: Mix Turnpoint Calculations
```kotlin
// FORBIDDEN: Calculation logic in coordinator
fun calculateDistance(waypoint: RacingWaypoint): Double {
    if (waypoint.type == CYLINDER) {
        // Direct calculation here - NO!
        return /* calculate cylinder distance */
    }
}
```

### DON'T: Share Display Logic
```kotlin
// FORBIDDEN: Shared rendering in coordinator
fun renderTurnpoint(waypoint: RacingWaypoint) {
    when (waypoint.type) {
        CYLINDER -> /* render circle here */ // NO!
        FAI_QUADRANT -> /* render sector here */ // NO!
    }
}
```

### ... DO: Use Dedicated Calculator
```kotlin
// CORRECT: Delegate to turnpoint calculator
val cylinderCalc = CylinderCalculator()
val distance = cylinderCalc.calculateOptimalDistance(waypoint, prev, next)
```

### ... DO: Use Dedicated Display
```kotlin
// CORRECT: Delegate to turnpoint display
val faiDisplay = FAIQuadrantDisplay()
val geometry = faiDisplay.generateSectorGeometry(waypoint, prev, next)
addLayerToMap(geometry)
```

### ... DO: Keep Turnpoint Types Independent
```kotlin
// CORRECT: Each turnpoint type has own modules
turnpoints/
""" CylinderCalculator.kt      // Independent
""" CylinderDisplay.kt          // Independent
""" FAIQuadrantCalculator.kt    // Independent
""" FAIQuadrantDisplay.kt       // Independent
// No shared code between them!
```

---

## " Code Standards

### File Size Limits
- **Target:** < 500 lines per file
- **Maximum:** < 800 lines per file (display files can be larger due to geometry)
- **Current state:** Most files comply, a few display files exceed (acceptable for complex geometry)

### Function Guidelines
- **Target:** < 50 lines per function
- **Responsibility:** Single, clear purpose
- **Naming:** Descriptive, geometry-specific names

### Turnpoint Architecture
- Each turnpoint type has 2 files: Calculator + Display
- Calculator: Mathematical operations only
- Display: Map rendering and GeoJSON generation
- **NO cross-turnpoint dependencies**

---

## " Testing Strategy

### Unit Tests
- Test each turnpoint calculator independently
- Mock waypoint data
- Verify distance calculations
- Check edge cases (co-linear points, 180deg turns, etc.)

### Integration Tests
- Test coordinator routing logic
- Verify correct calculator/display selected
- Check role assignment logic

### Visual Tests
- Verify displayed geometry matches calculations
- Check colors and styling
- Test at different zoom levels

---

## > Known Issues

### FAI Quadrant Orientation Discrepancy
**Problem:** Visual display orientation sometimes differs from mathematical calculations
**Location:** `FAIQuadrantCalculator.kt` vs `FAIQuadrantDisplay.kt`
**Impact:** Task line may not align with sector visually
**Status:** Requires refactoring to use single algorithm
**Workaround:** Verify task distance calculations are correct

### Finish Cylinder Radius Bug (FIXED)
**Problem:** Finish cylinder radius was ignored in distance calculations
**Fix:** Updated `findOptimalFAIPath()` to use `calculateOptimalEntryPoint()` for finish waypoints
**Date Fixed:** 2025
**Test:** Change finish radius and verify distance updates

---

## " Additional Resources

- **Main Project Rules:** `/CLAUDE.md` (root)
- **Task Separation Report:** `../TaskSeparationEnforcementReport.md`
- **AAT Architecture:** `../aat/ARCHITECTURE.md`

---

## z" For New Developers

**Start Here:**
1. Read this `ARCHITECTURE.md` file (you're here!)
2. Read main `/CLAUDE.md` for cross-contamination rules
3. Understand turnpoint type separation pattern
4. Look at specific turnpoint calculator/display you need
5. Test changes with `./gradlew assembleDebug`

**Before Making Changes:**
- ... Understand coordinator vs calculator vs display roles
- ... Check for cross-contamination (no AAT imports!)
- ... Keep turnpoint types independent
- ... Update BOTH calculator AND display if changing geometry
- ... Verify build passes

**Adding New Turnpoint Type:**
1. Create `NewTypeCalculator.kt` in `turnpoints/`
2. Create `NewTypeDisplay.kt` in `turnpoints/`
3. Update `RacingTaskCalculator` to route to new calculator
4. Update `RacingTaskDisplay` to route to new display
5. Add enum value to `TurnpointType`
6. Update UI selectors

---

## "s Module Statistics

**Total Lines:** ~7,312 lines
**Main Files:** 14 files
**Turnpoint Types:** 10 files (5 calculator + 5 display pairs)
**UI Components:** retained map-shell UI plus shared editor UI now owned by `:feature:tasks`
**Models:** 2 files

**Largest Files In Retained Map Shell:**
- `KeyholeDisplay.kt` (763 lines) - Complex composite geometry
- `RacingTaskDisplay.kt` (636 lines) - Rendering coordinator
- `RacingManageBTTab.kt` (471 lines) - Bottom tab UI
- `RacingTask.kt` (451 lines) - Data model
- `FAIQuadrantDisplay.kt` (446 lines) - FAI sector rendering

**Well-Organized:**
- Clear module separation ...
- Turnpoint type independence ...
- Coordinator pattern implemented ...
- No excessive file sizes ...

---

##  Architecture Strengths

- ... **Turnpoint Type Separation:** Each type has dedicated calculator & display
- ... **Coordinator Pattern:** Clear delegation, minimal logic in coordinators
- ... **Independent Modules:** Waypoint manager, storage, validator all separate
- ... **Clean Directory Structure:** Models, turnpoints, UI organized logically
- ... **Zero AAT Cross-Contamination:** Complete task type isolation
- ... **Ready for Extensions:** Easy to add new turnpoint types

---

## z Future Improvements

### Priority 1: Fix FAI Quadrant Orientation
- Use single algorithm for visual and mathematical calculations
- Eliminate discrepancies between display and calculator
- Test thoroughly with various task geometries

### Priority 2: Extract State Management
- Create `RacingTaskStateManager` similar to AAT refactoring
- Move state properties out of coordinator
- Enable better testability

### Priority 3: Extract Rendering
- Create `RacingTaskRenderer` similar to AAT refactoring
- Consolidate all MapLibre operations
- Simplify `RacingTaskDisplay` to pure coordination

---

**Questions?** Check the module reference section above or contact the development team.

**Making Changes?** Review this document first, understand the coordinator pattern, then check the specific turnpoint module you need.

**Adding Turnpoint Types?** Follow the existing pattern - create both Calculator and Display classes, update coordinators.

