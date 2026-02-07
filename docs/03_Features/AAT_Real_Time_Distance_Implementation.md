> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# AAT Real-Time Distance Implementation Plan

**Status**: "< PLANNED
**Created**: 2025-10-09
**Related**: Racing real-time distance (... COMPLETED 2025-10-09)

## z Objective

Implement real-time GPS distance updates for AAT (Assigned Area Task) tasks in the TaskMinimizedIndicator, showing the pilot live distance to the current AAT target point as they fly.

## "- Background

### What Was Done for Racing Tasks (Already Completed)

Racing task real-time distance was implemented with these changes:

1. **RacingTaskCalculator.kt** - Added `calculateDistanceToOptimalEntry()` method
   - Uses same geometry calculators as visual display (CylinderCalculator, FAIQuadrantCalculator, etc.)
   - Calculates distance to optimal entry point (cylinder edge, sector boundary, line crossing)
   - NOT distance to waypoint center - matches visual display exactly

2. **RacingTaskManager.kt** - Added `calculateDistanceToCurrentWaypointEntry()` method
   - Exposes calculator method through manager layer
   - Routes to current leg waypoint

3. **TaskManagerCoordinator.kt** - Updated `calculateDistanceToCurrentWaypoint()` (lines 556-572)
   - Routes Racing tasks to optimal entry point calculation
   - Routes AAT tasks to area center (TEMPORARY - needs enhancement)

4. **BottomSheetState.kt** - Modified `TaskMinimizedIndicator` component
   - Added optional `currentGPSLocation` parameter
   - Replaced static leg distance with real-time GPS calculation
   - Display updates live: "12.3 km" -> "11.8 km" -> "11.2 km"

5. **MapTaskScreenManager.kt** - Updated to provide GPS location
   - Extracts GPS from `flightDataManager.liveFlightData`
   - Passes GPS to TaskMinimizedIndicator

### Critical Design Principle: Competition Safety

**ABSOLUTE REQUIREMENT**: Distance shown to pilot MUST match the visual course line displayed on map.

**Why?** If map shows target point at 15km but display shows 20km to area center, pilot could:
- Miss time window for area entry
- Make incorrect strategic decisions
- Violate task rules

**Solution**: Use the SAME calculators that generate visual display for distance calculations.

## "" AAT vs Racing: Key Differences

### Racing Task Distance
- **Target**: Optimal entry point of observation zone
  - Cylinder: Edge of cylinder (shortest path to enter)
  - FAI Quadrant: Sector boundary edge
  - Start Line: Optimal line crossing point
  - Finish Cylinder: Cylinder edge entry
- **Fixed**: Waypoint geometry doesn't change during flight
- **Calculation**: To observation zone boundary

### AAT Task Distance
- **Target**: Movable TARGET POINT inside assigned area
  - Pilot can reposition target point for strategic flight planning
  - Target point shown as green dot (normal) or red dot (edit mode)
  - Target point must stay within assigned area boundaries
- **Dynamic**: Target point can move during flight planning
- **Calculation**: To TARGET POINT (lat/lon), NOT area center

### Why This Matters

AAT tasks use **assigned areas** (circles, sectors, lines) with **movable target points**:
```
Assigned Area (10km radius circle)
    "" Area center: (51.234, -0.567) - FIXED
    """ Target point: (51.240, -0.560) - MOVABLE by pilot
```

Distance calculation MUST use **target point**, not area center, because:
1. Pilot repositions target for optimal route
2. Visual course line connects target points (not area centers)
3. Task scoring uses target point positions
4. Strategic planning depends on accurate target distances

## "< Implementation Plan

### Phase 1: AATTaskCalculator Enhancement

**File**: `app/src/main/java/com/example/xcpro/tasks/aat/AATTaskCalculator.kt`

**Add New Method**:
```kotlin
/**
 * Calculate distance from current GPS position to target point of current waypoint
 *
 * CRITICAL FOR AAT COMPETITION: Uses TARGET POINT position, not area center!
 * This ensures distance matches the visual course line displayed on map.
 *
 * @param gpsLat Current GPS latitude
 * @param gpsLon Current GPS longitude
 * @param waypointIndex Index of target waypoint in AAT task
 * @param waypoints Complete list of AAT task waypoints
 * @return Distance in kilometers to target point, or null if calculation fails
 */
fun calculateDistanceToTargetPoint(
    gpsLat: Double,
    gpsLon: Double,
    waypointIndex: Int,
    waypoints: List<AATWaypoint>
): Double? {
    // Validate waypoint index
    if (waypointIndex < 0 || waypointIndex >= waypoints.size) {
        return null
    }

    val currentWaypoint = waypoints[waypointIndex]

    // Get target point position (NOT area center!)
    val targetLat = currentWaypoint.targetPoint.lat
    val targetLon = currentWaypoint.targetPoint.lon

    // Calculate haversine distance from GPS to target point
    // CRITICAL: Use AATGeometryUtils (AAT-specific calculator)
    return AATGeometryUtils.haversineDistance(gpsLat, gpsLon, targetLat, targetLon)
}
```

**Why This Approach?**
- Simple haversine distance is correct for AAT (point-to-point)
- No complex geometry needed (target point is just a lat/lon)
- Matches visual display (course line connects target points)
- Maintains AAT/Racing separation (uses AATGeometryUtils)

### Phase 2: AATTaskManager Enhancement

**File**: `app/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt`

**Add New Method**:
```kotlin
/**
 * Calculate distance from GPS to target point of current waypoint
 *
 * CRITICAL: Uses TARGET POINT position for accurate AAT navigation!
 * Distance updates in real-time as pilot flies toward assigned area.
 *
 * @param gpsLat Current GPS latitude
 * @param gpsLon Current GPS longitude
 * @return Distance in km to current waypoint's target point, or null if no waypoint active
 */
fun calculateDistanceToCurrentTargetPoint(gpsLat: Double, gpsLon: Double): Double? {
    return aatTaskCalculator.calculateDistanceToTargetPoint(
        gpsLat = gpsLat,
        gpsLon = gpsLon,
        waypointIndex = currentLeg,
        waypoints = _currentAATTask.waypoints
    )
}
```

**Integration Point**: Exposes calculator method through manager layer, following established pattern.

### Phase 3: TaskManagerCoordinator Update

**File**: `app/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt`

**Update Existing Method** (lines 556-572):
```kotlin
/**
 * Calculate real-time distance from current GPS position to active waypoint
 * ROUTES to task-specific calculators - maintains complete task type separation
 *
 * CRITICAL: Uses SAME geometry calculators as visual display for competition safety!
 *
 * Returns:
 * - Racing: Distance to OPTIMAL ENTRY POINT (cylinder edge/sector boundary/line crossing)
 * - AAT: Distance to TARGET POINT (movable pin inside assigned area)
 * - null: If no waypoint is active or GPS unavailable
 */
fun calculateDistanceToCurrentWaypoint(currentLat: Double, currentLon: Double): Double? {
    return when (_taskType) {
        TaskType.RACING -> {
            // Racing: Calculate distance to optimal entry point using same calculators as visual display
            // CRITICAL: This matches the visual course line displayed on map
            racingTaskManager.calculateDistanceToCurrentWaypointEntry(currentLat, currentLon)
        }
        TaskType.AAT -> {
            // ... ENHANCED: Calculate distance to TARGET POINT (not area center!)
            // CRITICAL: This matches the visual course line connecting target points
            aatTaskManager.calculateDistanceToCurrentTargetPoint(currentLat, currentLon)
        }
    }
}
```

**Changes from Current Code**:
- **OLD**: `aatTaskManager.calculateSegmentDistance(currentLat, currentLon, it.lat, it.lon)` (to area center)
- **NEW**: `aatTaskManager.calculateDistanceToCurrentTargetPoint(currentLat, currentLon)` (to target point)
- **Impact**: More accurate AAT navigation matching visual display

### Phase 4: No Changes Needed

**Files That Don't Need Modification**:
- ... `BottomSheetState.kt` - Already accepts GPS location and calls `taskManager.calculateDistanceToCurrentWaypoint()`
- ... `MapTaskScreenManager.kt` - Already extracts GPS and passes to TaskMinimizedIndicator
- ... UI components - Already wired up for real-time updates

**Why?** The coordinator routing handles task type differences transparently. Once `calculateDistanceToCurrentWaypoint()` returns AAT target point distance, the existing UI automatically displays it.

## sectiona Testing Plan

### Test 1: AAT Target Point Distance Accuracy

**Setup**:
1. Create AAT task with 3 waypoints
2. Set waypoint 2 assigned area: 10km radius circle at (51.500, -0.200)
3. Move target point to edge of circle at (51.550, -0.200) - approximately 5.5km north of center
4. Set GPS position at (51.450, -0.200) - 5.5km south of area center

**Expected Results**:
- Distance shown: ~11.0 km (GPS to target point at north edge)
- NOT: ~5.5 km (GPS to area center)
- Visual course line should point to target point at north edge
- Distance and visual display should match exactly

### Test 2: Target Point Movement Updates Distance

**Setup**:
1. AAT task with waypoint at current leg
2. Initial target point at area center
3. Note displayed distance (e.g., 20.0 km)
4. Move target point 5km east within area
5. Observe distance update

**Expected Results**:
- Distance changes immediately when target point moves
- New distance reflects GPS to new target point position
- Visual course line updates to new target point
- Distance accuracy within 0.1 km

### Test 3: Task Type Switching Preserves Functionality

**Setup**:
1. Create AAT task, verify real-time distance works
2. Switch to Racing task type
3. Verify Racing distance still shows optimal entry point
4. Switch back to AAT
5. Verify AAT distance shows target point again

**Expected Results**:
- No errors during task type switches
- Each task type shows correct distance calculation
- No cross-contamination between task types
- GPS updates continue working after switches

### Test 4: Edit Mode Distance Accuracy

**Setup**:
1. AAT task with waypoint at current leg
2. Enter edit mode for AAT area (double-tap area)
3. Target point turns RED and enlarges
4. Drag target point to new position
5. Observe distance updates in TaskMinimizedIndicator

**Expected Results**:
- Distance updates in real-time as target point drags
- Smooth distance changes during drag operation
- Final distance matches visual display after drop
- No performance lag during target point manipulation

### Test 5: Multiple Waypoint Navigation

**Setup**:
1. AAT task with 5 waypoints, each with different target point positions
2. Use chevron arrows to navigate between waypoints
3. Observe distance changes for each waypoint

**Expected Results**:
- Distance updates immediately when changing waypoints
- Each waypoint shows distance to its own target point
- Waypoint name and distance both update together
- No stale distance from previous waypoint

##  Critical Implementation Notes

### 1. Use AATGeometryUtils, Not RacingGeometryUtils

```kotlin
// ... CORRECT - AAT task separation
return AATGeometryUtils.haversineDistance(gpsLat, gpsLon, targetLat, targetLon)

// oe FORBIDDEN - cross-contamination violation
return RacingGeometryUtils.haversineDistance(gpsLat, gpsLon, targetLat, targetLon)
```

**Why?** CLAUDE.md ZERO cross-contamination rule requires complete task type separation.

### 2. Target Point, Not Area Center

```kotlin
// ... CORRECT - uses target point
val targetLat = currentWaypoint.targetPoint.lat
val targetLon = currentWaypoint.targetPoint.lon

// oe WRONG - uses area center (old behavior)
val targetLat = currentWaypoint.lat  // This is area center!
val targetLon = currentWaypoint.lon
```

**Why?** Visual course line connects target points. Distance must match visual.

### 3. Null Safety for GPS Data

```kotlin
// ... CORRECT - graceful degradation
val currentGPSLocation = taskScreenManager.mapState.flightDataManager?.liveFlightData?.let {
    Pair(it.latitude, it.longitude)
}

if (currentGPSLocation != null) {
    // Calculate distance
} else {
    // Don't show distance (GPS unavailable)
}
```

**Why?** GPS may be unavailable during testing or in poor signal conditions.

### 4. AAT Model Structure Verification

**Before implementing, verify AATWaypoint has target point**:
```kotlin
// Expected structure in AAT models
data class AATWaypoint(
    val id: String,
    val title: String,
    val lat: Double,  // Area center latitude
    val lon: Double,  // Area center longitude
    val targetPoint: TargetPoint,  // ... This is what we need!
    val assignedArea: AssignedArea
    // ... other fields
)

data class TargetPoint(
    val lat: Double,  // Target point latitude (movable)
    val lon: Double   // Target point longitude (movable)
)
```

**If TargetPoint doesn't exist**, need to add it to AAT models first.

### 5. Performance Considerations

- GPS updates at ~1 Hz (once per second)
- Distance calculation is simple haversine (fast)
- No complex geometry calculations needed for AAT
- Compose recomposition handles updates efficiently
- No performance concerns expected

## " Implementation Checklist

Use this checklist when implementing:

- [ ] Read Racing implementation for reference (RacingTaskCalculator.kt:127-222, RacingTaskManager.kt:368-383)
- [ ] Verify AATWaypoint model has `targetPoint` field with lat/lon
- [ ] Add `calculateDistanceToTargetPoint()` to AATTaskCalculator
- [ ] Add `calculateDistanceToCurrentTargetPoint()` to AATTaskManager
- [ ] Update `calculateDistanceToCurrentWaypoint()` in TaskManagerCoordinator (lines 556-572)
- [ ] Add CRITICAL safety comments about target point vs area center
- [ ] Test with real AAT task and moved target points
- [ ] Verify distance matches visual course line display
- [ ] Test task type switching (AAT *" Racing)
- [ ] Test edit mode target point dragging with live distance updates
- [ ] Verify ZERO cross-contamination (no Racing imports in AAT files)
- [ ] Update this document status to ... COMPLETED when done

## z" Key Learnings from Racing Implementation

### What Went Well
1. **Separation architecture worked perfectly** - Racing changes didn't affect AAT
2. **Coordinator routing pattern** - Clean delegation to task-specific managers
3. **Existing UI reuse** - No TaskMinimizedIndicator changes needed
4. **Competition safety focus** - Using visual display calculators prevents dangerous errors

### What to Avoid
1. **Don't calculate to waypoint center** - Use target point for AAT
2. **Don't mix Racing/AAT utilities** - Maintain complete separation
3. **Don't skip safety comments** - Critical for future maintainers
4. **Don't forget null checks** - GPS data may be unavailable

## " Related Files

**AAT Task Files** (modify these):
- `app/src/main/java/com/example/xcpro/tasks/aat/AATTaskCalculator.kt` - Add distance calculation
- `app/src/main/java/com/example/xcpro/tasks/aat/AATTaskManager.kt` - Expose through manager
- `app/src/main/java/com/example/xcpro/tasks/aat/models/AATWaypoint.kt` - Verify TargetPoint exists
- `app/src/main/java/com/example/xcpro/tasks/aat/AATGeometryUtils.kt` - Verify haversineDistance exists
- `app/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt` - Update routing (lines 556-572)

**Reference Files** (don't modify, use as examples):
- `app/src/main/java/com/example/xcpro/tasks/racing/RacingTaskCalculator.kt` - See lines 127-222
- `app/src/main/java/com/example/xcpro/tasks/racing/RacingTaskManager.kt` - See lines 368-383
- `app/src/main/java/com/example/xcpro/tasks/BottomSheetState.kt` - Already handles GPS (lines 106-122)
- `app/src/main/java/com/example/xcpro/map/MapTaskScreenManager.kt` - Already provides GPS (lines 265-289)

## "-- Architecture Diagram

```
GPS Location (from FlightDataManager)
    "
    "" MapTaskScreenManager extracts GPS ... (already done)
    "
    """ TaskMinimizedIndicator receives GPS ... (already done)
           "
           """ Calls taskManager.calculateDistanceToCurrentWaypoint(lat, lon) ... (already done)
                  "
                  """ TaskManagerCoordinator routes by task type   (needs enhancement)
                         "
                         "" Racing: racingTaskManager.calculateDistanceToCurrentWaypointEntry() ... (already done)
                         "     """ Uses optimal entry point (cylinder edge, sector boundary)
                         "
                         """ AAT: aatTaskManager.calculateDistanceToCurrentTargetPoint() "< (TODO - implement)
                               """ Uses target point (movable pin inside assigned area)
```

##  Estimated Effort

- **AATTaskCalculator method**: 15-20 lines of code, 10 minutes
- **AATTaskManager method**: 10-15 lines of code, 5 minutes
- **TaskManagerCoordinator update**: 3 line change, 2 minutes
- **Testing**: 30 minutes (5 test scenarios)
- **Total**: ~45-50 minutes

**Risk Level**: LOW (following proven Racing implementation pattern)

## ... Success Criteria

Implementation is successful when:

1. ... AAT tasks show real-time distance to target point (not area center)
2. ... Distance updates every ~1 second as pilot flies
3. ... Distance matches visual course line display exactly
4. ... Moving target point immediately updates displayed distance
5. ... Task type switching works (Racing *" AAT) without errors
6. ... Edit mode shows live distance updates during target point drag
7. ... No Racing/AAT cross-contamination in imports or utilities
8. ... All 5 test scenarios pass
9. ... No UI changes (same layout, only distance value updates)
10. ... No performance degradation

---

**END OF IMPLEMENTATION PLAN**



