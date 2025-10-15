# Racing Task Navigation - Existing UI Analysis

**Created:** 2025-10-08
**Status:** 📍 Current Implementation Documentation
**Purpose:** Document existing task UI before adding navigation features

---

## 🔍 Current Task UI Components

### 1. Task Bottom Sheet (Input)
**File:** `SwipeableTaskBottomSheet.kt`
**Location:** Bottom of MapScreen
**Purpose:** User adds/manages task via "Manage" tab

**Flow:**
1. User opens Navigation Drawer → "Add Task"
2. Bottom sheet appears with tabs: **MANAGE | RULES | FILES | ...**
3. In **MANAGE tab**, user adds turnpoints
4. Routes to task-specific UI:
   - Racing: `RacingManageBTTab.kt`
   - AAT: `AATManageBTTab.kt`

**Manager:** `MapTaskScreenManager.kt`
```kotlin
fun showTaskBottomSheet(initialHeight: BottomSheetState)
fun hideTaskBottomSheet()
```

---

### 2. Task Minimized Indicator (Top Display)
**File:** `MapTaskScreenManager.kt:257-284`
**Location:** **Top center of MapScreen**
**Z-Index:** 20f
**Visibility:** When bottom sheet is closed AND task has waypoints

**Current Implementation:**
```kotlin
@Composable
fun TaskMinimizedIndicatorOverlay(
    taskScreenManager: MapTaskScreenManager,
    modifier: Modifier = Modifier
) {
    // Only show when bottom sheet is hidden and task exists
    if (!taskScreenManager.showTaskBottomSheet &&
        taskScreenManager.taskManager.currentTask.waypoints.isNotEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
                .zIndex(20f)
        ) {
            TaskMinimizedIndicator(
                task = taskScreenManager.taskManager.currentTask,
                taskManager = taskScreenManager.taskManager,
                onClick = {
                    taskScreenManager.handleMinimizedIndicatorClick()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp) // Position just under system status bar
            )
        }
    }
}
```

**What It Shows (Currently):**
```
┌──────────────────────────────────────┐
│  [Circle: 5]  RACING Task            │
│               5 waypoints            │
└──────────────────────────────────────┘
```
- Circle with waypoint count
- Task type text
- Total waypoint count

**On Click:** Opens bottom sheet to FULLY_EXPANDED state

---

### 3. TaskMinimizedIndicator Component
**File:** `TaskBottomSheetComponents.kt:49-93`

**MinimizedContent (Row layout):**
```kotlin
@Composable
fun MinimizedContent(task: Task, taskManager: TaskManagerCoordinator) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TaskMinimizedIndicator(
            taskType = taskManager.taskType,
            waypointCount = task.waypoints.size
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${taskManager.taskType} Task",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${task.waypoints.size} waypoints",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
```

**TaskMinimizedIndicator (Circle badge):**
```kotlin
@Composable
fun TaskMinimizedIndicator(
    taskType: TaskType,
    waypointCount: Int
) {
    Text(
        text = "$waypointCount", // Just the number
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .wrapContentSize(),
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}
```

---

## 🎯 What's Missing for Navigation

### Current Limitations:
❌ **No current turnpoint shown** - Only shows total count
❌ **No turnpoint name** - User can't see which TP is active
❌ **No distance to next TP** - No real-time navigation data
❌ **No left/right arrows** - Can't manually browse TPs
❌ **No status indicator** - Can't see if TP is pending/active/completed
❌ **No currentLeg tracking** - Only shows total waypoints, not which one is active

### What It SHOULD Show:
```
┌──────────────────────────────────────────────────┐
│  [<]    TP 2/5: KEYSTONE (12.3 km)    [>]       │
│                    ACTIVE                         │
└──────────────────────────────────────────────────┘
```

---

## 🔧 Integration Plan for Navigation

### Option 1: Enhance Existing TaskMinimizedIndicator
**Modify:** `MapTaskScreenManager.kt:257-284`
**Change:** Replace simple indicator with full navigation bar

**Before:**
```kotlin
TaskMinimizedIndicator(
    task = taskScreenManager.taskManager.currentTask,
    taskManager = taskScreenManager.taskManager,
    onClick = { /* Open bottom sheet */ }
)
```

**After:**
```kotlin
TaskStatusBar( // NEW Component
    task = taskScreenManager.taskManager.currentTask,
    taskManager = taskScreenManager.taskManager,
    navigationManager = racingNavigationManager, // NEW
    onLeftArrow = { /* Previous TP */ },
    onRightArrow = { /* Next TP */ },
    onBarClick = { /* Open bottom sheet */ }
)
```

### Option 2: Add Separate Navigation Bar
**Keep:** Existing minimized indicator (for when no task active)
**Add:** New TaskStatusBar component (when task is active)

**Structure:**
```kotlin
if (task has waypoints) {
    if (task is being navigated) {
        TaskStatusBar(...) // NEW - With arrows and status
    } else {
        TaskMinimizedIndicator(...) // EXISTING - Simple display
    }
}
```

---

## 📋 Recommended Approach

### ✅ Option 1 (Replace) - RECOMMENDED

**Reasons:**
- Cleaner - one component instead of two
- More useful - always shows navigation info when task exists
- Simpler logic - less conditional rendering
- Better UX - pilot always sees current TP status

**Implementation:**
1. Create `TaskStatusBar.kt` (new file)
2. Replace TaskMinimizedIndicator usage in `MapTaskScreenManager.kt:272-281`
3. Connect to `RacingNavigationManager` for status updates
4. Add left/right arrow handlers
5. Keep click-to-expand functionality

**Files to Modify:**
```
Phase 4 (Visual UI):
├── Create: tasks/racing/ui/TaskStatusBar.kt
├── Modify: map/MapTaskScreenManager.kt (lines 257-284)
└── Modify: tasks/TaskBottomSheetComponents.kt (optional cleanup)
```

---

## 🎨 Current Visual Layout

**MapScreen Z-Index Stack:**
```
┌─────────────────────────────────────────────────┐
│  1.0f   Map Base                               │
│  2.0f   CardContainer (flight data cards)      │
│  3.0f   Task Overlay (turnpoints on map)       │
│  4.0f   Hamburger Menu                         │
│  5.0f   Flight Mode Indicator                  │
│  5.0f   Compass Widget                         │
│  20.0f  TaskMinimizedIndicator (Top Center) ← HERE
│  25.0f  SwipeableTaskBottomSheet (Bottom)      │
└─────────────────────────────────────────────────┘
```

**Current Position:**
- Alignment: `Alignment.TopCenter`
- Padding: `top = 8.dp` (just under system status bar)
- Z-Index: `20f` (above all map elements)

**New TaskStatusBar should:**
- Use same position (TopCenter, 8dp padding)
- Keep same z-index (20f)
- Expand width to accommodate arrows
- Add visual status indicators

---

## 🔗 Data Flow

### Current (Minimal):
```
TaskManagerCoordinator.currentTask
    ↓
MapTaskScreenManager (checks waypoint count)
    ↓
TaskMinimizedIndicatorOverlay (visibility logic)
    ↓
TaskMinimizedIndicator (displays count only)
```

### Needed for Navigation:
```
RacingNavigationManager.navigationState
    ↓ (current leg, status, distance)
TaskStatusBar
    ↓
Display: "TP 2/5: KEYSTONE (12.3 km) - ACTIVE"
         [<] arrows [>]
```

---

## 📝 Implementation Checklist

### Phase 4 (Visual UI) - TaskStatusBar Creation
- [ ] Find existing TaskMinimizedIndicator usage (✅ DONE - MapTaskScreenManager.kt:272)
- [ ] Create TaskStatusBar.kt component
- [ ] Add left/right arrow buttons
- [ ] Connect to RacingNavigationManager.navigationState
- [ ] Display current leg waypoint name
- [ ] Show distance to waypoint (real-time)
- [ ] Show turnpoint status (PENDING/ACTIVE/COMPLETED)
- [ ] Add status color coding
- [ ] Keep click-to-expand functionality
- [ ] Test visibility logic (show when bottom sheet hidden)

### Phase 5 (Manual Controls) - Arrow Functionality
- [ ] Wire up left arrow → navigationManager.goToPrevious()
- [ ] Wire up right arrow → navigationManager.advanceToNext()
- [ ] Add skip confirmation dialog
- [ ] Implement review mode (browse without changing active)
- [ ] Show task validity indicator

---

## 🎯 Key Insights

**What User Described:**
> "At the top of the mapscreen where it shows the task - current turn point name, it also has a left and right arrows that user can move to the next turnpoint or previous"

**What Currently Exists:**
- ✅ Task display at top of MapScreen
- ✅ Shows task type and waypoint count
- ❌ NO turnpoint name
- ❌ NO arrows
- ❌ NO navigation functionality

**What Needs to Be Added:**
- Current turnpoint name (e.g., "KEYSTONE")
- Turnpoint number (e.g., "TP 2/5")
- Distance to turnpoint (e.g., "12.3 km")
- Left/right arrows for manual browsing
- Status indicator (PENDING/ACTIVE/COMPLETED/SKIPPED)
- Real-time updates from navigation manager

---

**END OF ANALYSIS**

*This document captures the existing task UI infrastructure that will be enhanced with Racing task navigation features.*
