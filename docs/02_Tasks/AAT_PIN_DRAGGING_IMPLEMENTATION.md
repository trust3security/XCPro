# AAT Pin Dragging Implementation Guide

## z **CURRENT IMPLEMENTATION (2025-01-XX)**

### **How AAT Edit Mode Works**

#### **Enter Edit Mode**:
- **Double-tap on AAT turnpoint** inside the turnpoint area
- **Camera automatically zooms** to optimal level based on turnpoint size:
  - **Small areas (5km)**: Zoom in closer to fill screen
  - **Medium areas (10km)**: Standard zoom for comfortable view
  - **Large areas (20km)**: Zoom out to fit entire area on screen
- **Previous camera position saved** for restoration on exit
- Turnpoint becomes highlighted/selected
- Red FAB exit button appears in bottom-right corner
- Map zoom/pan gestures are blocked during edit mode

#### **While in Edit Mode**:
- **Drag finger** to move the pin within the AAT area boundaries
- Pin stays within the assigned area circle
- Task line updates in real-time as pin moves
- Red FAB button remains visible
- **Zoomed view** shows area boundaries clearly for accurate positioning

#### **Exit Edit Mode** (2 ways):
1. **Double-tap anywhere outside AAT areas**
2. **Tap the red FAB button** (bottom-right corner, single tap)

**On Exit**:
- **Camera automatically zooms back** to previous position (where user was before edit mode)
- User returns to task overview at original zoom level
- Smooth workflow: zoom in -> edit -> zoom out automatically

**REMOVED**: Long press entry/exit (replaced with double-tap for better UX)

---

## "section **Critical Bug Fix: Ghost Dragging After FAB Exit**

### **Problem**:
When user taps FAB to exit edit mode:
- Visual feedback disappears (turnpoint unhighlighted, FAB hidden)
- BUT gesture handler keeps internal state active
- User can still drag the pin around even though edit mode appears off

### **Root Cause**:
- `CustomMapGestureHandler` has local state (`aatEditModeIndex`)
- FAB button only updates `isAATEditMode` in MapScreen
- No synchronization between external state and gesture handler state

### **Solution**:
```kotlin
// CustomMapGestures.kt - Add parameter
fun CustomMapGestureHandler(
    // ... other params
    isAATEditMode: Boolean = false, // ... NEW: External edit mode state
    // ... other params
) {
    // ... CRITICAL FIX: Reset local state when external edit mode is disabled
    LaunchedEffect(isAATEditMode) {
        if (!isAATEditMode) {
            Log.d(TAG, ""section RESET: External edit mode disabled - clearing local gesture state")
            aatEditModeIndex = -1
            isDraggingAAT = false
        }
    }
}

// MapScreen.kt - Pass the state
CustomMapGestureHandler(
    // ... other params
    isAATEditMode = isAATEditMode, // ... CRITICAL FIX: Pass edit mode state
    // ... other params
)
```

**Files Modified**:
- `CustomMapGestures.kt`: Added `isAATEditMode` parameter and `LaunchedEffect` to reset state
- `MapScreen.kt`: Pass `isAATEditMode` state to gesture handler

---

## z **Implementation Details**

### **Core Concept**
1. **Double-tap on waypoint** within turnpoint circle -> Enter edit mode + auto-zoom in
2. **Edit mode active** -> Disable map zoom/pan, enable pin dragging
3. **Drag pin** -> Move target point within AAT area boundaries
4. **Double-tap outside OR tap FAB** -> Exit edit mode + auto-zoom out to previous position

### **Why This Design**
- ... **Double-tap** is intuitive and fast for edit mode entry
- ... **Automatic zoom** provides context: zoom in for precision, zoom out for overview
- ... **Camera save/restore** maintains user's workflow and context
- ... FAB provides clear visual feedback and easy exit
- ... No need to manually zoom in/out every time (saves pilot workload)
- ... Similar to photo apps: tap to zoom in, tap to zoom out

### **Camera Zoom Behavior**
- **Entry Zoom**: Saves current position (lat, lon, zoom) -> Calculates optimal zoom based on turnpoint size AND screen layout
- **Exit Zoom**: Restores saved position -> Returns to task overview
- **Intelligent Adaptive Zoom Calculation**:
  - **Considers**:
    - Turnpoint radius (5km, 10km, 20km, 30km+)
    - Actual screen width/height in pixels
    - Bottom sheet/UI height covering map
    - Screen orientation (portrait/landscape)
    - Latitude (Mercator projection distortion)
  - **Adaptive Padding by Circle Size**:
    - **5km radius** -> 60% of screen (40% padding) - Small circles need less padding
    - **10km radius** -> 50% of screen (50% padding) - Medium circles need balanced padding
    - **20km radius** -> 45% of screen (55% padding) - Large circles need more breathing room
    - **30km+ radius** -> 40% of screen (60% padding) - XL circles need maximum padding
    - **Why**: Larger circles occupy more screen edge-to-edge, need proportionally MORE padding to feel comfortable
  - **Formula**:
    ```
    paddingFactor = adaptive based on radius (0.40 - 0.60)
    metersPerPixel = diameterMeters / (minAvailableMapDimension x paddingFactor)
    zoom = log(earthCircumference / (tileSize x metersPerPixel)) + log(cos(latitude))
    ```
  - **Screen Layout Aware**:
    - Accounts for bottom sheet covering map area
    - Uses available map height (screenHeight - bottomSheetHeight)
    - Ensures at least 60% of screen is available
  - **Zoom Bounds**: Clamped between 11.0 - 16.0 for usability
- **Smooth Transitions**: Animated zoom via MapCameraManager with 300ms duration

##  **Critical Implementation Notes**

### **Gesture Hierarchy**
- **Double-tap detection** -> Enter/Exit edit mode (with auto-zoom)
- **Drag detection** -> Move pin within edit mode
- **FAB tap** -> Exit edit mode immediately (with zoom restore)

### **Map Gesture Management**
- **Edit mode entry** -> Disable all map gestures (`setAllGesturesEnabled(false)`)
- **Edit mode exit** -> Re-enable all map gestures (`setAllGesturesEnabled(true)`)

### **State Synchronization** (CRITICAL FIX)
- **Problem**: FAB button exits edit mode visually, but gesture handler keeps internal state active
- **Solution**: Pass `isAATEditMode` state to gesture handler as parameter
- **Implementation**: `LaunchedEffect(isAATEditMode)` resets local gesture state when external state changes
- **Files**: `CustomMapGestures.kt` (gesture handler) + `MapScreen.kt` (FAB button)

### **Coordinate System**
- **Screen coordinates** -> Used for gesture detection
- **Map coordinates** -> Used for waypoint hit testing and pin positioning
- **Conversion** -> Use `AATMapCoordinateConverter.screenToMap()`

## z **Success Criteria**

- ... Double-tap on AAT waypoint enters edit mode
- ... **Camera zooms to optimal level based on turnpoint radius**
- ... **Previous camera position saved before zoom**
- ... Map zoom/pan disabled during edit mode
- ... Pin can be dragged smoothly without zoom interference
- ... Pin stays within AAT area boundaries
- ... Area boundaries clearly visible at calculated zoom level
- ... Double-tap outside exits edit mode
- ... **Camera restores to previous position on exit**
- ... Red FAB button appears during edit mode
- ... Tapping FAB exits edit mode and restores camera
- ... Map zoom/pan restored after edit mode
- ... Visual feedback shows edit mode state (highlighted turnpoint + FAB)
- ... **CRITICAL FIX**: FAB properly resets gesture handler state (no ghost dragging)

## sectiona **Testing Strategy**

### **Test Cases**
1. **Load AAT task** -> Should see AAT areas/circles on map at normal zoom
2. **Double-tap on 5km waypoint** -> Should zoom to ~14.2 (60% padding), comfortable fit
3. **Double-tap on 10km waypoint** -> Should zoom to ~12.8 (50% padding), balanced view with good breathing room
4. **Double-tap on 20km waypoint** -> Should zoom to ~12.2 (45% padding), large circle fits with generous padding
5. **Check zoom level** -> Should show turnpoint centered, full area visible with adequate padding
6. **Verify NO edge cutoff** -> Circle should NEVER touch screen edges, always has comfortable margin
6. **Drag pin** -> Should move smoothly without zoom interference
7. **Tap FAB** -> Should exit edit mode, **zoom back to original position**, FAB disappears, **no more dragging**
8. **Double-tap outside** -> Should also exit edit mode and restore zoom properly
9. **Verify zoom restoration** -> Should return to exact position before entering edit mode
10. **Test different sizes** -> Verify zoom adjusts appropriately for 5km, 10km, 20km turnpoints

### **Bug Verification**
After tapping FAB to exit:
- oe OLD BUG: Can still drag pin (ghost dragging)
- ... FIXED: Dragging disabled, edit mode fully exited

### **Debugging Logs to Monitor**
```bash
# Clear logs and monitor AAT activity
adb logcat -c
adb logcat -s "AAT" "MapScreen" "CustomMapGestures" -v time
```

**Expected Log Sequence:**
1. **Double-tap detection**: `z AAT: Double-tap detected on waypoint [X] - entering edit mode`
2. **Camera zoom in**: `z AAT: Saved camera position - lat=..., lon=..., zoom=...`
3. **Screen size detection**: `z AAT: Screen size - width=1080px, height=2340px, bottomSheet=500px, available=1080px`
4. **Adaptive padding calculation**: `z AAT: Radius=10.0km -> paddingFactor=50% -> usablePixels=540px`
5. **Camera zoom calculation**: `z AAT: Zoomed to turnpoint ${waypoint.title} (radius=10.0km) for edit mode`
6. **Dynamic zoom**: `z AAT: Zooming to turnpoint - radius=10.0km, diameter=20.0km, screen=1080px, calculated zoom=12.04, at: ...`
7. **Edit mode entry**: `z Entered AAT edit mode for waypoint [X]`
6. **Drag start**: `z AAT: Started dragging pin [X]`
7. **FAB tap or double-tap outside**: `z FAB: Exited AAT edit mode`
8. **Camera zoom out**: `z AAT: Restored camera position - lat=..., lon=..., zoom=...`
9. **State reset**: `"section RESET: External edit mode disabled - clearing local gesture state`

## "section **Files Modified**

### **Primary Changes**
- `CustomMapGestures.kt`:
  - Added `isAATEditMode: Boolean` parameter
  - Added `LaunchedEffect(isAATEditMode)` to reset state
  - Double-tap detection for edit mode entry/exit
  - Simplified gesture handling

- `MapCameraManager.kt`:
  - Added `savedCameraPosition` state for save/restore
  - Added `zoomToAATAreaForEdit(lat, lon, radiusKm, bottomSheetHeight)` - intelligent adaptive zoom
  - Reads actual screen dimensions from `mapState.safeContainerSize`
  - Accounts for bottom sheet height covering map area
  - Uses minimum available map dimension (width or availableHeight) to handle both orientations
  - **Adaptive padding by circle size**:
    - per-mille$5km: 60% of screen (generous for small circles)
    - per-mille$10km: 50% of screen (balanced for medium circles)
    - per-mille$20km: 45% of screen (more padding for large circles)
    - >20km: 40% of screen (maximum padding for XL circles)
  - Calculates meters-per-pixel needed to fit circle with adaptive padding
  - Adjusts for latitude using Mercator projection factor: `cos(latitude)`
  - Logarithmic zoom formula: `log(earthCircumference / (tileSize x metersPerPixel)) + log(cos(lat))`
  - Zoom bounds: 11.0 - 16.0 for usability
  - Detailed debug logging for radius -> paddingFactor -> usablePixels calculation
  - Added `restoreAATCameraPosition()` - restores saved position on exit

- `MapGestureSetup.kt`:
  - Extracts AAT area radius from waypoint custom parameters
  - Wired `onAATLongPress` callback to call `cameraManager.zoomToAATAreaForEdit(lat, lon, radiusKm)`
  - Wired `onAATExitEditMode` callback to call `cameraManager.restoreAATCameraPosition()`
  - Passes cameraManager to gesture handler for zoom control

- `MapScreen.kt`:
  - Pass `cameraManager` to `MapGestureSetup.GestureHandlerOverlay()`
  - Pass `isAATEditMode` state to gesture handler
  - Added red FAB button during edit mode

- `AAT_PIN_DRAGGING_IMPLEMENTATION.md`:
  - Updated with current implementation
  - Documented ghost dragging bug fix
  - Documented automatic zoom in/out behavior

---

**Last Updated**: 2025-10-02 - Added intelligent adaptive zoom with circle-size-based padding. Larger circles (10km+) now get MORE relative padding than small circles (5km) to prevent cramped feel. Accounts for bottom sheet covering map area. Fixes issue where 10km turnpoints were too zoomed in while 5km looked good.

