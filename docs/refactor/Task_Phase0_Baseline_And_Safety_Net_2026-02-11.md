# Task Phase 0 Baseline and Safety Net (2026-02-11)

Parent plan:
- `docs/refactor/Task_Architecture_Compliance_Refactor_Plan.md`

Phase:
- Phase 0 - Baseline + Safety Net

## 1) Inventory Method

Commands used:
- `rg -n "TaskManagerCoordinator|getAATTaskManager\(|getRacingTaskManager\(|taskManager\." feature/map/src/main/java app/src/main/java -g "*.kt"`
- `rg -n "TaskManagerCoordinator" feature/map/src/main/java app/src/main/java -g "*.kt"`
- `rg -n "getAATTaskManager\(|getRacingTaskManager\(" feature/map/src/main/java -g "*.kt"`
- `rg -n "taskManager\.(addWaypoint|setTaskType|removeWaypoint|reorderWaypoints|replaceWaypoint|setActiveLeg|plotOnMap|setMapInstance|currentTask|currentLeg|taskType)" feature/map/src/main/java/com/example/xcpro/tasks feature/map/src/main/java/com/example/xcpro/map/ui/task -g "*.kt"`
- `rg -n "TaskManagerCoordinator|updateAATTargetPoint|calculateSimpleSegmentDistance|setTaskType\(|addWaypoint\(|removeWaypoint\(|reorderWaypoints\(" feature/map/src/test/java -g "*.kt"`

## 2) Inventory Summary

| Category | Count | Notes |
|---|---:|---|
| Main-source references to `TaskManagerCoordinator` | 107 | Broad usage across DI, map runtime, task UI, and use-cases |
| Direct manager escape-hatch calls (`getAATTaskManager` / `getRacingTaskManager`) | 11 | Primary bypass points for internals |
| Direct `taskManager.*` mutation/query calls in task/map-task UI surfaces | 37 | Includes UI calls that bypass ViewModel intents/state |
| Test-source references to task coordinator/phase behaviors | 16 | Existing baseline test points before expansion |

## 3) High-Risk Bypass Call Sites (Current)

Representative direct bypasses:
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskSearchBarsOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskTopDropdownPanel.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/SwipeableTaskBottomSheet.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/BottomSheetState.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/aat/AATManageList.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskMapOverlay.kt`
- `feature/map/src/main/java/com/example/xcpro/map/ui/task/MapTaskScreenUi.kt`
- `feature/map/src/main/java/com/example/xcpro/tasks/TaskNavigationController.kt`
- `feature/map/src/main/java/com/example/xcpro/map/MapScreenReplayCoordinator.kt`

## 4) Safety-Net Test Expansion

Expanded in:
- `feature/map/src/test/java/com/example/xcpro/tasks/TaskManagerCoordinatorTest.kt`

Added coverage:
- Racing add/remove/reorder waypoint behavior.
- AAT add/remove/reorder waypoint behavior.
- Task type switch preserving waypoint IDs across Racing/AAT.
- Racing segment distance calculation sanity (positive and symmetric).

Already-covered baseline retained:
- AAT target update delegation/guard behavior in `TaskManagerCoordinatorTest`.

## 5) Phase 0 Exit Gate

Gate requirement:
- No production behavior changes.

Status:
- Met. Changes are test-only plus documentation artifact.
