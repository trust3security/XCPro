# Keyhole Racing Task Implementation and Testing Specification

## FAI Requirements Summary
Based on FAI Sporting Code Section 3 and competition rules, a keyhole observation zone consists of:
- **Inner cylinder**: 500m radius centered on the turnpoint
- **FAI sector**: 90-degree sector (45° each side of bisector) with 10km radius
- **Orientation**: Sector bisector perpendicular to the line between previous and next waypoints
- **Direction**: Sector oriented symmetrically and remote from the bisector (points outward from course)

## Project Context
- Android gliding app with existing racing task implementation
- Files located in: /app/src/main/java/com/example/baseui1/tasks/racing/turnpoints/
- KeyholeCalculator.kt and KeyholeDisplay.kt already exist
- Must ensure calculations and display match EXACTLY

## Agent Task: Update and Test Keyhole Implementation

### 1. Verify FAI Compliance
Update KeyholeCalculator.kt and KeyholeDisplay.kt to ensure:
- 500m cylinder radius (inner zone)
- 10km sector radius (outer zone)  
- 90-degree sector angle (45° each side)
- Sector bisector calculation:
  - For turnpoint N, bisector is perpendicular to line from turnpoint N-1 to turnpoint N+1
  - Sector points AWAY from the inside of the course
  - Handle first and last turnpoints correctly

### 2. Use Test Racing Task "keyhole1"
**IMPORTANT: Use the racing task named "keyhole1"**
- Find the task "keyhole1" in the system (check TaskManager, SharedPreferences, or saved tasks)
- This task should have start, 3 keyhole turnpoints, and finish
- Use ONLY this task for all verification tests
- DO NOT create any new test tasks

### 3. Implement Verification System
Create diagnostic functions that:
- Load the "keyhole1" racing task from the system
- Calculate optimal touch point on each keyhole boundary using KeyholeCalculator
- Generate visual representation using KeyholeDisplay
- Compare the two results and log any discrepancies
- Output exact coordinates of calculated vs displayed touch points

### 4. Fix Synchronization Issues
Ensure PERFECT alignment between calculation and display:
- Both must use identical algorithms for sector orientation
- The course line drawn on map must connect the EXACT points used in distance calculation
- Total task distance shown must equal the sum of displayed leg distances

### 5. Test Output Requirements
The test must produce clear output showing:
=== KEYHOLE TASK VERIFICATION ===
Task: keyhole1
Turnpoint 1 (Keyhole):

Calculated touch point: (lat, lon)
Displayed touch point:  (lat, lon)
Difference: X.XX m [PASS/FAIL]

Turnpoint 2 (Keyhole):

Calculated touch point: (lat, lon)
Displayed touch point:  (lat, lon)
Difference: X.XX m [PASS/FAIL]

Turnpoint 3 (Keyhole):

Calculated touch point: (lat, lon)
Displayed touch point:  (lat, lon)
Difference: X.XX m [PASS/FAIL]

Total Task Distance:

Calculated: XX.XX km
Displayed:  XX.XX km
Match: [PASS/FAIL]

FAI Compliance:

Cylinder radius: 500m ✓
Sector radius: 10000m ✓
Sector angle: 90° ✓
Orientation: Correct ✓


### 6. Visual Debugging Features
Add temporary debug overlays showing:
- Red dot: Calculated optimal touch point
- Blue dot: Display system touch point
- Green line: Sector bisector
- Yellow arc: 90-degree sector boundaries

## Critical Requirements
1. **Use task "keyhole1"** - Must find and use this specific task
2. **Zero Tolerance**: Touch points must match to within 1 meter
3. **FAI Compliance**: All dimensions must match FAI specifications exactly
4. **Course Line**: Must visually touch the keyhole boundary, not the center

## Known Issues to Fix
From racing_task_spec.md Section 12:
- Course line currently goes to turnpoint center instead of optimal sector edge
- Need to implement proper calculateOptimalSectorTouchPoint function
- Must work within existing separated architecture

## Success Criteria
- Successfully loads and uses the "keyhole1" task
- All keyhole turnpoints display correctly per FAI rules
- Calculated and displayed touch points match exactly (< 1m difference)
- Course line visually touches keyhole boundaries at optimal points
- Total distance calculation matches what's drawn on screen

## Do NOT:
- Create any new test tasks
- Implement GPS tracking or flight detection
- Create general task creation UI
- Work on other turnpoint types
- Modify files outside the racing/turnpoints directory

Focus ONLY on perfecting the keyhole implementation using the "keyhole1" task.
