> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# Racing Task Navigation - UX Design

**Created:** 2025-10-08
**Status:** z Design Specification
**Based on:** User description of existing/desired UI

---

## z User Expectation Summary

**"At the top of the mapscreen where it shows the task - current turn point name, it also has a left and right arrows that user can move to the next turnpoint or previous"**

This describes a **Task Status Bar** that should be integrated with the automatic navigation system.

---

## "+/- UI Components

### 1. Task Status Bar (Top of MapScreen)

**Location:** Top center of MapScreen, below hamburger menu and flight mode indicator

**Layout:**
```
"oe"""""""""""""""""""""""""""""""""""""""""""""""""""
"  [<]    TP 2/5: KEYSTONE (12.3 km)    [>]       "
"                    ACTIVE                         "
"""""""""""""""""""""""""""""""""""""""""""""""""""""
```

**Elements:**
- **[<] Left Arrow:** Go to previous turnpoint
- **Main Text:** Shows:
  - "TP 2/5" - Current turnpoint / Total count
  - "KEYSTONE" - Turnpoint name
  - "(12.3 km)" - Distance to turnpoint
- **Status Indicator:** "ACTIVE" | "COMPLETED "" | "PENDING"
- **[>] Right Arrow:** Go to next turnpoint

**Visual States:**

**Pending Turnpoint:**
```
"oe"""""""""""""""""""""""""""""""""""""""""""""""""""
"  [<]    TP 3/5: MOUNTAIN (25.7 km)    [>]       "
"                   PENDING                         "
"""""""""""""""""""""""""""""""""""""""""""""""""""""
   Gray text, white background
```

**Active Turnpoint:**
```
"oe"""""""""""""""""""""""""""""""""""""""""""""""""""
"  [<]    TP 2/5: KEYSTONE (12.3 km)    [>]       "
"                    ACTIVE                         "
"""""""""""""""""""""""""""""""""""""""""""""""""""""
   Blue text, highlighted background, distance updates live
```

**Completed Turnpoint:**
```
"oe"""""""""""""""""""""""""""""""""""""""""""""""""""
"  [<]    TP 1/5: START (0.0 km)    [>]           "
"              COMPLETED " 14:23                   "
"""""""""""""""""""""""""""""""""""""""""""""""""""""
   Green text, checkmark, timestamp shown
```

**Skipped Turnpoint (Invalid Task):**
```
"oe"""""""""""""""""""""""""""""""""""""""""""""""""""
"  [<]    TP 3/5: SKIPPED    [>]                  "
"              TASK INVALID                        "
"""""""""""""""""""""""""""""""""""""""""""""""""""""
   Red text, warning icon, task invalidated
```

---

## z(R) User Interaction Flow

### Automatic Detection (Primary)

**Scenario: Pilot approaching TP2**
1. **Task Bar Shows:**
   ```
   [<]    TP 2/5: KEYSTONE (12.3 km)    [>]
               ACTIVE
   ```
2. **Distance decreases:** (12.3 km -> 8.5 km -> 3.2 km -> 0.8 km)
3. **GPS enters zone:** Pilot crosses into observation zone
4. **Automatic advance:**
   - BEEP! + Vibration
   - Task bar updates to TP 3:
   ```
   [<]    TP 3/5: MOUNTAIN (42.1 km)    [>]
               ACTIVE
   ```
5. **TP 2 now shows as completed when using left arrow:**
   ```
   [<]    TP 2/5: KEYSTONE (0.0 km)    [>]
            COMPLETED " 14:35
   ```

### Manual Advance (Secondary)

**Scenario: Pilot wants to skip TP2 manually**
1. **User taps [>] right arrow**
2. **Confirmation dialog appears:**
   ```
   "oe""""""""""""""""""""""""""""""""""""""
   "  Skip TP2: KEYSTONE?                "
   "                                     "
   "  This will mark the task as INVALID "
   "  for competition scoring.           "
   "                                     "
   "  [Cancel]         [Skip Anyway]    "
   """"""""""""""""""""""""""""""""""""""""
   ```
3. **If user confirms:**
   - TP2 marked as SKIPPED
   - Task status becomes INVALID
   - Advance to TP3:
   ```
   [<]    TP 3/5: MOUNTAIN (42.1 km)    [>]
               ACTIVE (TASK INVALID  )
   ```

### Manual Review (Arrows)

**Scenario: Pilot wants to check previous turnpoint**
1. **User taps [<] left arrow**
2. **Task bar switches to TP1 (read-only view):**
   ```
   [<]    TP 1/5: START (0.0 km)    [>]
            COMPLETED " 14:15
   ```
3. **Flight data cards still show ACTIVE turnpoint (TP2)**
4. **User taps [>] to return to active turnpoint**

---

## "-- Integration with Navigation System

### How It Works Together

**Task Bar (Manual UI)** *" **RacingNavigationManager (Auto Logic)**

```kotlin
// RacingNavigationManager triggers UI updates
navigationManager.navigationState.collect { state ->
    // Update task bar display
    taskBarCurrentLeg = state.currentLeg
    taskBarStatus = state.turnpointStatuses[state.currentLeg]
    taskBarWaypoint = task.waypoints[state.currentLeg]
}

// Task bar arrows call navigation manager
onLeftArrowClick = {
    navigationManager.goToPrevious() // Manual navigation (review only)
}

onRightArrowClick = {
    navigationManager.advanceToNext(skipConfirmation = false) // Shows dialog
}
```

**Key Behaviors:**
- ... Arrows ALWAYS work (manual override)
- ... Auto-detection updates task bar automatically
- ... Task bar shows "ACTIVE" turnpoint, not just "current view"
- ... User can browse previous TPs without changing active TP
- ... Flight data cards (WPT DIST, WPT BRG) always show ACTIVE TP, not viewed TP

---

## z Visual Design Specification

### Task Bar Component

**Dimensions:**
- Height: 64dp
- Width: 90% screen width (centered)
- Padding: 16dp horizontal

**Typography:**
- Main text: 16sp, Bold
- Status: 12sp, Medium
- Distance: 14sp, Regular

**Colors (Light Mode):**
- PENDING: Gray (#757575) on White
- ACTIVE: Blue (#2196F3) on Light Blue (#E3F2FD)
- COMPLETED: Green (#4CAF50) on Light Green (#E8F5E9)
- SKIPPED: Red (#F44336) on Light Red (#FFEBEE)

**Arrows:**
- Size: 32dp touch target
- Icon: Material Icons chevron_left / chevron_right
- Disabled state: 30% opacity when at first/last TP

**Animations:**
- Status change: 300ms fade transition
- Distance update: None (live update, no animation)
- TP advance: 200ms slide + fade

### Confirmation Dialog

**Layout:**
```kotlin
AlertDialog(
    title = { Text("Skip TP${index + 1}: ${waypointName}?") },
    text = {
        Column {
            Text("This will mark the task as INVALID for competition scoring.")
            Spacer(height = 8.dp)
            Text("Are you sure?", fontWeight = FontWeight.Bold)
        }
    },
    confirmButton = {
        TextButton(onClick = { /* Skip */ }) {
            Text("Skip Anyway", color = MaterialTheme.colorScheme.error)
        }
    },
    dismissButton = {
        TextButton(onClick = { /* Cancel */ }) {
            Text("Cancel")
        }
    }
)
```

---

## "s Audio/Haptic Feedback

**Zone Entry (Automatic):**
- Sound: Short beep (200ms, 1kHz)
- Haptic: Single pulse (50ms)
- Task bar: Smooth transition to next TP

**Manual Advance:**
- Sound: Click sound (100ms)
- Haptic: Light tap
- Confirmation dialog appears (if skipping)

**Manual Skip Confirmed:**
- Sound: Warning tone (400ms, lower pitch)
- Haptic: Double pulse pattern
- Task bar: Shows INVALID warning

---

## "s Z-Index Layering

```
"oe""""""""""""""""""""""""""""""""""""""""""""""""""
"  Z-INDEX STACK                                  "
"""""""""""""""""""""""""""""""""""""""""""""""""""$
"  1.0f   Map Base                               "
"  2.0f   CardContainer (flight data)            "
"  3.0f   Task Overlay (turnpoints on map)       "
"  4.0f   Hamburger Menu                         "
"  5.0f   Flight Mode Indicator                  "
"  5.0f   Compass Widget                         "
"  6.0f    Task Status Bar (NEW)               "
"  10.0f  Dialogs (skip confirmation)            "
""""""""""""""""""""""""""""""""""""""""""""""""""""
```

**Why z-index 6f:**
- Above all map elements (clickable even over task overlay)
- Below dialogs (confirmation dialogs appear on top)
- Always visible and interactive

---

## sectiona User Scenarios

### Scenario 1: Normal Flight (Auto-Detection)
1. START -> Auto-advance when zone entered -> TP1
2. TP1 -> Auto-advance -> TP2
3. TP2 -> Auto-advance -> TP3
4. TP3 -> Auto-advance -> FINISH
5. Task bar shows: "TASK COMPLETED ""

### Scenario 2: Manual Skip (Weather Avoidance)
1. START -> Auto-advance -> TP1
2. TP1 approaching, but pilot decides weather ahead is unsafe
3. Pilot taps [>] arrow
4. Dialog: "Skip TP1? Task will be INVALID"
5. Pilot confirms
6. Task bar: "TP2 ACTIVE (TASK INVALID  )"
7. Pilot continues to TP2, but task won't count for scoring

### Scenario 3: Review Previous Turnpoints
1. Currently at TP3 (active)
2. Pilot taps [<] twice to view TP1
3. Task bar shows TP1 status: "COMPLETED " 14:15"
4. Flight cards STILL show TP3 distance/bearing (active TP)
5. Pilot taps [>] twice to return to TP3

### Scenario 4: Auto-Detection Failed
1. Pilot flew through TP2 zone but app didn't detect
2. Task bar still shows TP2 as ACTIVE
3. Pilot manually taps [>] to advance
4. Dialog: "Mark TP2 as completed?"
5. Pilot confirms
6. TP2 marked COMPLETED, advance to TP3
7. Task remains VALID (manual completion counts)

---

## ... Implementation Checklist

### Phase 1: Basic UI (MapScreen integration)
- [ ] Create `TaskStatusBar.kt` composable
- [ ] Add to MapScreen at z-index 6f
- [ ] Connect to `taskManager.currentLeg`
- [ ] Show waypoint name, number, distance
- [ ] Implement left/right arrow buttons
- [ ] Add click handlers for manual navigation

### Phase 2: Status Integration (Navigation Manager)
- [ ] Connect to `RacingNavigationManager.navigationState`
- [ ] Display turnpoint status (PENDING/ACTIVE/COMPLETED/SKIPPED)
- [ ] Show task validity indicator
- [ ] Update distance in real-time
- [ ] Show completion timestamps

### Phase 3: Manual Controls
- [ ] Implement skip confirmation dialog
- [ ] Add "Mark as completed" option
- [ ] Disable arrows appropriately (first/last TP)
- [ ] Handle review mode (viewing per-mille  changing active TP)

### Phase 4: Visual Polish
- [ ] Status color coding
- [ ] Smooth transitions
- [ ] Responsive layout (different screen sizes)
- [ ] Dark mode support

### Phase 5: Audio/Haptic
- [ ] Zone entry beep
- [ ] Manual click sounds
- [ ] Skip warning tone
- [ ] Haptic patterns

---

## " Acceptance Criteria

**Must Have:**
- ... Always shows current active turnpoint name and number
- ... Left/right arrows work for manual navigation
- ... Distance updates in real-time
- ... Auto-detection updates task bar immediately
- ... Skip confirmation dialog prevents accidental skips

**Should Have:**
- ... Status color coding (pending/active/completed)
- ... Task validity warning when skipped
- ... Completion timestamps shown
- ... Audio beep on zone entry

**Nice to Have:**
- Distance to zone edge (not just center)
- ETA to turnpoint
- Turn direction indicator (L/R)
- Swipe gestures on task bar

---

**END OF UX SPECIFICATION**

*This design integrates the manual task status bar with the automatic navigation manager for a seamless Racing task experience.*



