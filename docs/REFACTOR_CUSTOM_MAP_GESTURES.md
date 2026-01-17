# Refactor Plan: Custom Map Gestures

Status: Draft (approved)
Owner: Map feature
Scope: feature/map/src/main/java/com/example/xcpro/gestures/CustomMapGestures.kt

## Goals
- Remove task-specific logic from shared gesture code.
- Preserve behavior for mode switching, zoom, pan, and AAT edit gestures.
- Enforce Task_Type_Separation.md (no cross-contamination).
- Reduce file size and remove non-ASCII strings in Kotlin sources.
- Add at least one unit test for extracted pure logic.

## Constraints (must follow)
- No AAT or Racing imports in shared gesture code.
- TaskManagerCoordinator does routing only.
- No business logic in UI beyond input handling.
- No non-ASCII characters in source.

## Current Behavior Summary
- Single-finger horizontal: mode switch (threshold 250px).
- Single-finger vertical: zoom with clamp.
- Two-finger: pan map; breaks tracking and saves return position.
- AAT: double-tap on area enters edit mode; double-tap again exits.
- AAT: long press exits edit mode.
- AAT: drag target point in edit mode.

## State Machine (target)
States: Idle, SingleFinger, TwoFinger, AatEditIdle, AatDragging
Transitions:
- Idle -> SingleFinger (1 pointer down)
- Idle -> TwoFinger (2 pointers down)
- AatEditIdle -> AatDragging (drag threshold exceeded)
- AatDragging -> AatEditIdle (pointer up)
- Any -> Idle (all pointers up)
- AatEditIdle <-> Idle via enter/exit edit mode actions

## Phases

### Phase 1: Task-agnostic gesture core
- Remove AAT logic and task imports from CustomMapGestures.
- Add TaskGestureHandler interface in gestures package.
- Ensure overlay filtering and generic gestures stay intact.
- Replace non-ASCII logs/comments with ASCII or remove.

### Phase 2: AAT gesture handler (AAT module only)
- Add AatGestureHandler in tasks/aat/gestures.
- Use core TaskWaypoint list for hit testing and radius data.
- Implement double-tap enter/exit, long-press exit, drag updates.
- Provide callbacks for UI actions (enter/exit/drag).

### Phase 3: Routing only
- TaskManagerCoordinator creates the correct gesture handler.
- MapGestureSetup passes callbacks and map provider.
- No task-type switches in CustomMapGestures.

### Phase 4: Tests
- Add a unit test for AAT hit detection in tasks/aat/gestures.
- Tests must not depend on Android or MapLibre.

## Verification Checklist
- No AAT imports in CustomMapGestures.kt.
- No Racing imports in AAT gesture handler.
- AAT edit mode still works via double-tap and long press.
- Dragging target point still updates via callbacks.
- Single-finger and two-finger gestures behave unchanged.
- File size warnings reduced for CustomMapGestures.kt.
