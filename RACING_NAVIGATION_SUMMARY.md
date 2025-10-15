# Racing Task Navigation - Quick Summary

**Branch:** `racing-task-navigation`
**Created:** 2025-10-08
**Status:** Planning complete, ready for Phase 1

---

## What We're Building

Automatic turnpoint navigation for Racing tasks with:
- Automatic GPS-based turnpoint detection
- Visual feedback (active/completed markers)
- Audio beeps and haptic vibration on zone entry
- Manual override controls
- Task validity tracking

---

## Implementation Phases

### ✅ Planning Complete
- Full implementation plan in `docs/03_Features/Racing_Task_Navigation_Plan.md`
- 6 phases, 11-15 days estimated
- Complete Racing/AAT separation maintained

### 🚧 Next Steps - Phase 1 (1 day)

**Create:** `tasks/racing/navigation/RacingNavigationManager.kt`

**Features:**
- Turnpoint status tracking (PENDING/ACTIVE/COMPLETED/SKIPPED)
- Current leg management
- Task validity checking
- Status change events

**Files to modify:**
- Extract navigation logic from `RacingTaskManager.kt`
- Create dedicated navigation state models

**Branch:** Create `phase-1-navigation-manager` from `racing-task-navigation`

---

## Current State Analysis

### ✅ Already Have (Good Separation)
- Complete Racing/AAT separation via TaskManagerCoordinator
- Racing-specific models (RacingWaypoint, RacingTask)
- Racing-specific managers (RacingTaskManager, RacingTaskDisplay)
- Basic leg tracking (currentLeg property)
- Turnpoint geometry calculators

### ❌ Missing for Navigation
- No automatic turnpoint detection
- No turnpoint status tracking
- No audio/haptic feedback
- No observation zone monitoring
- No dedicated navigation manager
- No visual status indicators

---

## Architecture Overview

```
RacingNavigationManager (Phase 1)
    ↓ triggers
RacingZoneDetector (Phase 2) → FlightDataManager (GPS)
    ↓ uses
Turnpoint Calculators (existing)
    ↓ triggers
Audio & Haptics (Phase 3)
    ↓ updates
UI Components (Phase 4)
```

---

## Key Design Decisions

1. **Racing First:** Implement Racing navigation completely before AAT
2. **Zero Cross-Contamination:** No shared code between Racing/AAT navigation
3. **Separation of Concerns:** Dedicated manager for navigation logic
4. **User Control:** Auto-advance can be disabled, manual override always available
5. **Safety:** Task validity warnings when turnpoint skipped

---

## Success Criteria

### Must Have (MVP)
- Automatic turnpoint detection works reliably
- Clear visual feedback (active/completed markers)
- Audio beep on zone entry
- Manual advance button
- Task validity tracking

### Should Have
- Haptic feedback
- Turnpoint list screen
- User preferences
- Undo last advance

---

## Testing Strategy

- Unit tests for each phase
- Integration tests after Phase 6
- Real-world flight testing
- Battery usage monitoring
- GPS edge case testing

---

## Next Action

```bash
# Start Phase 1
git checkout -b phase-1-navigation-manager racing-task-navigation

# Create navigation manager
# File: tasks/racing/navigation/RacingNavigationManager.kt
```

See `docs/03_Features/Racing_Task_Navigation_Plan.md` for complete details.

---

**Questions? Issues?**
Refer to the full plan document for architecture details, data models, and testing requirements.
