# Task Type Rules and Defaults Specification

## 🎯 STANDARDIZED DEFAULTS - APPLIES TO ALL TASK TYPES

### Universal Default Values
These defaults apply consistently across Racing, AAT, and DHT task types:

- **START WAYPOINTS**: 10km start lines (perpendicular to first leg)
- **FINISH WAYPOINTS**: 3km finish cylinders
- **TURNPOINT WAYPOINTS**: Task-specific defaults (see below)

### Task-Specific Turnpoint Defaults
- **Racing Tasks**: 0.5km cylinder radius
- **AAT Tasks**: 10km area radius
- **DHT Tasks**: 1km cylinder radius

## 📋 DETAILED WAYPOINT TYPE SPECIFICATIONS

### Start Waypoint Rules
**Default Geometry**: START_LINE with 10km length
**Available Types**:
- START_LINE: 10km perpendicular line to first leg
- START_CYLINDER: 10km radius cylinder (if user changes from line)
- FAI_START_QUADRANT: 90° sector, infinite radius

**Display Format**: "Start Line Length: 10.0km"

### Finish Waypoint Rules
**Default Geometry**: FINISH_CYLINDER with 3km radius
**Available Types**:
- FINISH_CYLINDER: 3km radius cylinder
- FINISH_LINE: 3km perpendicular line to last leg (if user changes from cylinder)

**Display Format**: "Finish Cylinder Radius: 3.0km"

### Turnpoint Waypoint Rules
**Racing Tasks**:
- Default: CYLINDER with 0.5km radius
- Available: CYLINDER, FAI_QUADRANT, KEYHOLE, SYMMETRIC_QUADRANT
- Display: "Turnpoint Cylinder Radius: 0.5km"

**AAT Tasks**:
- Default: CIRCLE with 10km radius
- Available: CIRCLE, SECTOR
- Display: "AAT Area Radius: 10.0km"

**DHT Tasks**:
- Default: CYLINDER with 1km radius
- Available: CYLINDER only
- Display: "DHT Cylinder Radius: 1.0km"

## 🔄 TASK TYPE SWITCHING BEHAVIOR

### Value Preservation Priority
1. **User Customizations**: If user has modified a value, preserve it exactly
2. **Standardized Defaults**: If no customization, apply universal defaults (10km start, 3km finish)
3. **Task-Specific Defaults**: For turnpoints, use task-appropriate defaults

### Conversion Examples
**Scenario 1: User has 5km start line in Racing task**
- Switch to AAT: Preserve 5km start line (user customization)
- Switch to DHT: Preserve 5km start line (user customization)

**Scenario 2: User has default Racing start (should be 10km)**
- Switch to AAT: Keep 10km start line (standardized default)
- Switch to DHT: Keep 10km start line (standardized default)

**Scenario 3: User has 2km turnpoint in Racing**
- Switch to AAT: Create 2km AAT area (preserve radius, change geometry)
- Switch to DHT: Create 2km DHT cylinder (preserve radius, change geometry)

## ⚠️ CURRENT ISSUES TO FIX

### Issue 1: Inconsistent Start Defaults
**Problem**: Manage tab shows "5.0km" but Rules tab promises "10km" default
**Root Cause**: ManageBTTab is not using the standardized defaults system
**Fix Required**: Update waypoint creation in ManageBTTab to use TaskWaypoint.getEffectiveRadius()

### Issue 2: Finish Defaults Don't Match
**Problem**: Finish waypoints not getting 3km default consistently
**Root Cause**: Same as above - direct waypoint creation bypassing standardized defaults
**Fix Required**: Ensure all waypoint creation goes through the standardized defaults system

### Issue 3: Display vs Actual Value Mismatch
**Problem**: UI shows one value but calculations use another
**Root Cause**: Multiple sources of truth for default values
**Fix Required**: Single source of truth through TaskWaypoint.getEffectiveRadius()

## 🎯 IMPLEMENTATION REQUIREMENTS

### Rule 1: Single Source of Truth
ALL default values MUST come from `TaskWaypoint.getEffectiveRadius()` method in TaskManagerCoordinator.kt

### Rule 2: Consistent Display
ALL UI components (ManageBTTab, RulesBTTab, etc.) MUST show the same default values

### Rule 3: Universal Application
The 10km start / 3km finish defaults MUST apply when:
- Creating new waypoints in ManageBTTab
- Switching task types in RulesBTTab
- Displaying default values in any UI component
- Calculating task distances and geometries

### Rule 4: Customization Preservation
User modifications MUST be preserved during:
- Task type switches
- UI refreshes
- App state changes
- Session persistence

## 🔧 FILES REQUIRING UPDATES

### ✅ COMPLETED - Racing Task Fixes
1. **RacingWaypoint.kt**: ✅ FIXED - Removed hardcoded `gateWidth = 5.0`, added `createWithStandardizedDefaults()`
2. **RacingTaskManager.kt**: ✅ FIXED - Updated `initializeRacingTask()` and `addRacingWaypoint()` to use standardized defaults

### 🔄 HIGH PRIORITY - Immediate Fixes Needed
1. **AATTaskManager.kt**: ❌ HAS HARDCODED `radiusMeters = 5000.0` (should be 10000.0 for AAT turnpoints)
2. **DHTWaypoint.kt**: ❌ HAS HARDCODED `cylinderRadiusKm: Double = 0.5` (should use standardized defaults)
3. **ManageBTTab.kt**: Update waypoint creation to use standardized defaults
4. **TaskPointTypeSelector.kt**: Ensure default value display matches rules

### Medium Priority - Verification Needed
1. **TaskManagerCoordinator.kt**: Verify getEffectiveRadius() implementation
2. **RulesBTTab.kt**: Ensure consistency with actual defaults applied
3. **SwipeableTaskBottomSheet.kt**: Check any waypoint creation/display

## 📝 TESTING CHECKLIST

### Test 1: Default Value Consistency
- [ ] Create new start waypoint → Shows "Start Line Length: 10.0km"
- [ ] Create new finish waypoint → Shows "Finish Cylinder Radius: 3.0km"
- [ ] Create new Racing turnpoint → Shows "Cylinder Radius: 0.5km"
- [ ] Create new AAT turnpoint → Shows "Area Radius: 10.0km"
- [ ] Create new DHT turnpoint → Shows "Cylinder Radius: 1.0km"

### Test 2: Task Type Switching
- [ ] Create Racing task with defaults → Switch to AAT → Values preserved correctly
- [ ] Customize Racing start to 5km → Switch to DHT → 5km preserved
- [ ] Reset to defaults → Switch between all task types → Always shows 10km start, 3km finish

### Test 3: UI Consistency
- [ ] ManageBTTab defaults match RulesBTTab promises
- [ ] All UI components show same default values
- [ ] Display format consistent across all screens

## 🚀 SUCCESS CRITERIA

✅ **Consistency**: Rules tab promises match Manage tab reality
✅ **Standardization**: 10km start, 3km finish across ALL task types
✅ **Preservation**: User customizations maintained during switches
✅ **Clarity**: No confusion between displayed and actual values
✅ **Reliability**: Same behavior every time, no edge case failures