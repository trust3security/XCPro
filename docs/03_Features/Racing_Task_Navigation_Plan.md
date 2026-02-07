> NOTICE (2026-02-06): Task refactor plan is documented in $plan. Review before implementing task-related changes.

# Racing Task Navigation - Implementation Plan

**Created:** 2025-10-08
**Status:** section Planning
**Priority:** HIGH - Core flight functionality

---

## "< Table of Contents

- [Overview](#overview)
- [Current State Analysis](#current-state-analysis)
- [Racing Task User Requirements](#racing-task-user-requirements)
- [Implementation Phases](#implementation-phases)
- [Architecture Design](#architecture-design)
- [Testing Strategy](#testing-strategy)

---

## Overview

This document outlines the implementation plan for Racing task navigation features. The implementation will be done in phases, with Racing tasks first, maintaining complete separation from AAT tasks as per CLAUDE.md requirements.

### Goals

1. **Automatic turnpoint detection** - Monitor GPS position and detect zone entry
2. **Clear visual feedback** - Show which turnpoint is active, completed, pending
3. **Audio/haptic alerts** - Notify pilot when turnpoint reached
4. **Manual override** - Allow manual advancement/skip for edge cases
5. **Task validity tracking** - Warn if task becomes invalid (skipped turnpoint)

---

## Current State Analysis

### ... What We Have

**Separation Architecture:**
- ... Complete Racing/AAT separation via `TaskManagerCoordinator`
- ... Racing-specific models: `RacingWaypoint`, `RacingTask`
- ... Racing-specific managers: `RacingTaskManager`, `RacingTaskDisplay`
- ... Basic leg tracking: `currentLeg` in `RacingTaskManager`

**Racing Task Files:**
```
tasks/racing/
""" RacingTaskManager.kt           // Main manager with basic leg tracking
""" RacingTaskCalculator.kt        // Distance calculations
""" RacingTaskDisplay.kt           // Map visualization
""" RacingTaskValidator.kt         // Task validation
""" models/
"   """ RacingTask.kt              // Task data model
"   """" RacingWaypoint.kt          // Waypoint model with roles
"""" turnpoints/
    """ CylinderCalculator.kt      // Cylinder geometry
    """ FAIQuadrantCalculator.kt   // FAI sector geometry
    """ KeyholeCalculator.kt       // Keyhole geometry
    """" *Display.kt                // Visual rendering
```

**Basic Navigation Methods:**
- ... `advanceToNextLeg()` - Manually advance to next waypoint
- ... `goToPreviousLeg()` - Go back to previous waypoint
- ... `currentLeg` property - Track current waypoint index

**AAT Comparison (for reference only):**
- AAT has dedicated `AATNavigationManager.kt` (we can learn from the architecture)
- AAT navigation is separate from AAT task management (good pattern)

### oe What's Missing for Racing Navigation

**Core Navigation Features:**
- oe No automatic turnpoint detection (GPS zone entry monitoring)
- oe No turnpoint status tracking (pending/active/completed/skipped)
- oe No audio/haptic feedback on zone entry
- oe No task validity checking (did pilot skip turnpoint?)
- oe No observation zone geometry checking (is GPS inside zone?)
- oe No dedicated navigation manager (logic mixed in RacingTaskManager)

**UI Features:**
- oe No visual turnpoint status indicators
- oe No "Next TP" button on map
- oe No turnpoint list screen
- oe No task status indicator (Valid/Invalid)
- oe No completed turnpoint markers (checkmarks)

**Configuration:**
- oe No user preferences for auto-advance behavior
- oe No audio alert toggle
- oe No haptic feedback toggle

---

## Racing Task User Requirements

### How Racing Tasks Work

**Sequential Navigation:**
- Pilot MUST visit turnpoints in strict order: START -> TP1 -> TP2 -> ... -> FINISH
- Skipping a turnpoint = **task invalid** (unlike AAT which is flexible)
- Each turnpoint has an observation zone (cylinder, FAI quadrant, keyhole)

**Turnpoint Completion:**
- Turnpoint is "reached" when GPS enters observation zone
- Once reached, pilot advances to next turnpoint
- Previous turnpoints marked as completed (visual checkmark)

**Task Validity:**
- All turnpoints must be visited in order
- If pilot skips a turnpoint, task becomes invalid
- App must warn pilot immediately

### User Expectations

**Automatic Detection (Preferred):**
```
Pilot approaching TP1:
"" Distance: 8.5 km (decreasing)
"" Bearing: 045deg
"" Status: "TP1 Active"
""" Visual: TP1 highlighted on map

[GPS enters TP1 observation zone]
"" BEEP! + Vibration
"" Message: "TP1 reached"
"" Auto-advance to TP2
""" TP1 marked with " checkmark

Now showing TP2:
"" Distance: 42.3 km
"" Bearing: 135deg
"" Status: "TP2 Active"
""" TP1: " 14:23 (completed at time)
```

**Manual Override (Backup):**
- "Next TP" button always available
- Confirmation dialog: "Skip TP2? This will invalidate the task."
- Option to manually mark as reached (if auto-detection failed)

**Visual Indicators:**
- Map overlay:
  - Active TP: Highlighted, larger, pulsing
  - Completed TP: Green checkmark, faded
  - Pending TP: Normal display
  - Skipped TP: Red X, warning color
- Status bar: "Task Valid: TP2 of 5" or "Task Invalid: TP3 skipped"

**Audio/Haptic Feedback:**
- Zone entry: BEEP + short vibration
- Task complete: Success sound + long vibration
- Task invalid: Warning sound + buzz pattern

---

## Implementation Phases

### Phase 1: Racing Navigation Manager (Foundation)
**Goal:** Extract navigation logic into dedicated manager

**Files to Create:**
```
tasks/racing/navigation/
"""" RacingNavigationManager.kt
```

**Features:**
- Turnpoint status tracking (PENDING/ACTIVE/COMPLETED/SKIPPED)
- Current leg management
- Navigation between turnpoints
- Task validity checking
- Status change events (for UI/audio)

**Testing:**
- Unit tests for status transitions
- Task validity logic tests

**Estimated Time:** 1 day

---

### Phase 2: Observation Zone Detection
**Goal:** Monitor GPS and detect zone entry

**Files to Create:**
```
tasks/racing/navigation/
""" RacingZoneDetector.kt          // GPS monitoring & zone geometry checks
"""" RacingNavigationState.kt       // State models for navigation
```

**Features:**
- Continuous GPS monitoring (10Hz from FlightDataManager)
- Zone geometry checks using existing turn point calculators
- Entry/exit detection with hysteresis (prevent flickering)
- Distance to zone tracking

**Integration:**
- Connect to FlightDataManager for GPS updates
- Use turnpoint calculators for geometry checks
- Emit zone entry events to navigation manager

**Testing:**
- Simulated flight tests with known coordinates
- Edge case testing (zone boundaries, GPS jitter)

**Estimated Time:** 2 days

---

### Phase 3: Audio & Haptic Feedback
**Goal:** Provide sensory alerts on turnpoint events

**Files to Create:**
```
tasks/racing/navigation/
""" RacingNavigationAudio.kt       // Sound playback
"""" RacingNavigationHaptics.kt     // Vibration patterns
```

**Features:**
- Zone entry beep (short, clear)
- Task complete sound (success melody)
- Warning sound (task invalid)
- Vibration patterns matching audio cues
- User preferences (enable/disable, volume)

**Assets Needed:**
- beep.wav (zone entry)
- success.wav (task complete)
- warning.wav (task invalid)

**Testing:**
- Audio playback on real device
- Vibration pattern testing
- Preference toggle testing

**Estimated Time:** 1 day

---

### Phase 4: Visual UI Updates
**Goal:** Show turnpoint status visually on map and UI

**Files to Modify:**
```
tasks/racing/RacingTaskDisplay.kt           // Map markers
screens/NavigationScreen.kt (NEW)           // Turnpoint list screen
MapScreen.kt                                // Status indicator
```

**Features:**
- Map marker styling based on status:
  - Active: Large, pulsing, highlighted color
  - Completed: Green checkmark overlay, 50% opacity
  - Pending: Normal blue marker
  - Skipped: Red X overlay, warning color
- Status bar on map: "TP 2/5 - Valid" or "Invalid: TP3 skipped"
- "Next TP" FAB button on map
- Turnpoint list screen (swipe to open)

**UI Components:**
- `RacingTurnpointStatusMarker.kt` - Map marker composable
- `RacingTaskStatusBar.kt` - Status indicator
- `RacingNavigationFAB.kt` - Next TP button
- `RacingTurnpointListScreen.kt` - Full list view

**Testing:**
- Visual regression testing
- Different task configurations (2-10 turnpoints)
- Status transition animations

**Estimated Time:** 3 days

---

### Phase 5: Manual Controls & Preferences
**Goal:** Allow manual override and user configuration

**Files to Create:**
```
tasks/racing/navigation/
"""" RacingNavigationPreferences.kt
screens/settings/
"""" RacingNavigationSettings.kt
```

**Features:**
- Manual advance confirmation dialog
- Manual skip with task invalid warning
- Undo last advance (within time limit)
- User preferences:
  - Auto-advance on zone entry (ON/OFF)
  - Audio alerts (ON/OFF, volume)
  - Haptic feedback (ON/OFF)
  - Confirmation dialogs (NONE/SKIP_ONLY/ALWAYS)

**UI:**
- Settings screen for navigation preferences
- Confirmation dialogs with clear warnings
- "Undo" button (time-limited)

**Testing:**
- Preference persistence
- Confirmation dialog flows
- Undo functionality

**Estimated Time:** 2 days

---

### Phase 6: Integration & Polish
**Goal:** Integrate all components and refine UX

**Tasks:**
- Connect all components (manager -> detector -> audio -> UI)
- Add comprehensive logging for debugging
- Performance optimization (battery usage)
- Edge case handling (GPS loss, task reload)
- Documentation updates

**Testing:**
- End-to-end flight simulation
- Real-world flight testing
- Battery usage testing
- GPS edge cases (tunnel, loss of signal)

**Estimated Time:** 2 days

---

## Architecture Design

### Component Diagram

```
"oe""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
"                     MapScreen.kt                         "
"  - Displays navigation status                           "
"  - Shows turnpoint markers with status                  "
"  - "Next TP" FAB button                                 "
""""""""""""""""""""""""not""""""""""""""""""""""""""""""""""""
                      " observes state
                      -1/4
"oe""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
"           RacingNavigationManager.kt                     "
"  - Manages turnpoint status (PENDING/ACTIVE/COMPLETED)  "
"  - Validates task (sequential order, no skips)          "
"  - Emits status change events                           "
"  - Provides current/next turnpoint info                 "
""""""""not""""""""""""""""""""""""""""""""not""""""""""""""""""""
      " updates status                 " triggers
      -1/4                                -1/4
"oe""""""""""""""""""""""""    "oe""""""""""""""""""""""""""
" RacingZoneDetector.kt "    "  Audio & Haptics        "
" - GPS monitoring      "    "  - Zone entry beep      "
" - Zone geometry check "    "  - Task complete sound  "
" - Entry/exit events   "    "  - Warning sound        "
""""""""""not""""""""""""""""    "  - Vibration patterns   "
        " uses geometry      """"""""""""""""""""""""""""
        -1/4
"oe"""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
"              Turnpoint Calculators                        "
"  - CylinderCalculator.isWithinObservationZone()          "
"  - FAIQuadrantCalculator.isWithinObservationZone()       "
"  - KeyholeCalculator.isWithinObservationZone()           "
"""""""""""""""""""""""""""""""""""""""""""""""""""""""""""""
        -^2
        " receives GPS
        "
"oe""""""""""""""""""""""""
" FlightDataManager.kt  "
" - GPS position stream "
" - 10Hz updates        "
""""""""""""""""""""""""""
```

### Data Models

```kotlin
/**
 * Turnpoint status for Racing tasks
 */
enum class RacingTurnpointStatus {
    PENDING,    // Not yet reached
    ACTIVE,     // Current target
    COMPLETED,  // Successfully reached
    SKIPPED     // Pilot bypassed (invalidates task)
}

/**
 * Racing navigation state
 */
data class RacingNavigationState(
    val currentLeg: Int,
    val turnpointStatuses: List<RacingTurnpointStatus>,
    val isTaskValid: Boolean,
    val distanceToActiveTurnpoint: Double?, // km
    val isWithinActiveZone: Boolean,
    val completedTimestamps: Map<Int, LocalDateTime>
)

/**
 * Navigation event for triggering audio/haptic
 */
sealed class RacingNavigationEvent {
    data class ZoneEntered(val turnpointIndex: Int) : RacingNavigationEvent()
    data class TurnpointCompleted(val turnpointIndex: Int) : RacingNavigationEvent()
    object TaskCompleted : RacingNavigationEvent()
    data class TaskInvalidated(val reason: String) : RacingNavigationEvent()
}
```

### Key Interfaces

```kotlin
interface RacingNavigationManager {
    // State
    val navigationState: StateFlow<RacingNavigationState>
    val navigationEvents: SharedFlow<RacingNavigationEvent>

    // Manual control
    fun advanceToNext(skipConfirmation: Boolean = false)
    fun goToPrevious()
    fun markTurnpointCompleted(index: Int)
    fun resetNavigation()

    // Automatic detection
    fun onGPSUpdate(lat: Double, lon: Double)
    fun updateZoneDetection(enabled: Boolean)
}

interface RacingZoneDetector {
    fun isWithinZone(
        gpsLat: Double,
        gpsLon: Double,
        waypoint: RacingWaypoint
    ): Boolean

    fun distanceToZone(
        gpsLat: Double,
        gpsLon: Double,
        waypoint: RacingWaypoint
    ): Double // km
}
```

---

## Testing Strategy

### Unit Tests (Phase 1-2)

**RacingNavigationManager Tests:**
```kotlin
@Test fun `advanceToNext updates status correctly`()
@Test fun `skipped turnpoint invalidates task`()
@Test fun `task validity with sequential completion`()
@Test fun `reset clears all statuses`()
@Test fun `cannot skip start waypoint`()
```

**RacingZoneDetector Tests:**
```kotlin
@Test fun `cylinder zone detection`()
@Test fun `FAI quadrant zone detection`()
@Test fun `keyhole zone detection`()
@Test fun `distance to zone calculation`()
@Test fun `hysteresis prevents flapping`()
```

### Integration Tests (Phase 6)

**End-to-End Navigation:**
```kotlin
@Test fun `complete task in sequence`()
@Test fun `auto-advance on zone entry`()
@Test fun `manual skip with confirmation`()
@Test fun `GPS loss and recovery`()
@Test fun `task reload preserves state`()
```

### Manual Flight Testing

**Test Scenarios:**
1. **Normal Flight:** Complete 3-turnpoint task in sequence
2. **Skip Turnpoint:** Manually skip TP2, verify task invalid warning
3. **Auto-Advance:** Fly through zones, verify auto-detection
4. **GPS Loss:** Simulate GPS dropout during flight
5. **Audio/Haptic:** Test all sound and vibration cues
6. **Battery:** Monitor battery usage over 2-hour flight

---

## Git Branch Strategy

```bash
# Main branch for Racing navigation work
racing-task-navigation/

# Sub-branches for each phase
""" phase-1-navigation-manager
""" phase-2-zone-detection
""" phase-3-audio-haptic
""" phase-4-visual-ui
""" phase-5-manual-controls
"""" phase-6-integration
```

**Workflow:**
1. Create phase branch from `racing-task-navigation`
2. Complete phase implementation
3. Test thoroughly
4. Merge back to `racing-task-navigation`
5. Test integration
6. Repeat for next phase

**Final Merge:**
- Merge `racing-task-navigation` -> `main` after Phase 6 complete
- Tag as `v1.x-racing-navigation`

---

## Success Criteria

### Must Have (MVP)
- ... Automatic turnpoint detection works reliably
- ... Clear visual feedback (active/completed markers)
- ... Audio beep on zone entry
- ... Manual advance button always available
- ... Task validity tracking and warning

### Should Have
- ... Haptic feedback on zone entry
- ... Turnpoint list screen
- ... User preferences for auto-advance
- ... Undo last advance

### Nice to Have
- Distance circles auto-center on active turnpoint
- Estimated time to next turnpoint
- Turn direction indicator (left/right)
- "Optimal route" line updates

---

## Risks & Mitigation

**Risk 1: GPS Accuracy**
- **Issue:** GPS jitter near zone boundary causes flickering
- **Mitigation:** Hysteresis in zone detection (must be inside for 3 consecutive readings)

**Risk 2: Battery Drain**
- **Issue:** 10Hz GPS monitoring increases battery usage
- **Mitigation:** Throttle to 1Hz when far from zone, 10Hz when close

**Risk 3: Separation Violation**
- **Issue:** Accidental AAT code in Racing module
- **Mitigation:** Strict code reviews, automated import checks

**Risk 4: User Confusion**
- **Issue:** Auto-advance surprises pilot
- **Mitigation:** Clear audio cue + visual confirmation, toggle in settings

---

## Timeline

**Total Estimated Time:** 11 days

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Navigation Manager | 1 day | None |
| Phase 2: Zone Detection | 2 days | Phase 1 |
| Phase 3: Audio/Haptic | 1 day | Phase 2 |
| Phase 4: Visual UI | 3 days | Phase 1-3 |
| Phase 5: Manual Controls | 2 days | Phase 1-4 |
| Phase 6: Integration | 2 days | All phases |

**With buffer:** 13-15 days for real-world delays and testing

---

**END OF PLAN**

*This plan maintains complete separation between Racing and AAT task navigation, with Racing implemented first as the simpler, more structured task type.*



