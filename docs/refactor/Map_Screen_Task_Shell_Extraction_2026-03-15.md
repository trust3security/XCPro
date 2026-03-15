# Map Screen Task Shell Extraction

## 0) Metadata

- Title: Extract the retained task shell from `MapScreenViewModel`
- Owner: Codex
- Date: 2026-03-15
- Issue/PR: TBD
- Status: Complete

## 1) Scope

- Problem statement:
  - `MapScreenViewModel` still owned task gesture creation and a duplicated AAT edit-mode UI flag.
  - The AAT edit-mode flag mirrored task-owned state instead of reading the task authority seam.
- Why now:
  - After the map-runtime right-sizing, this was the remaining bounded task shell owner inside `feature:map`.
- In scope:
  - Publish AAT edit-mode from the task authority seam.
  - Add a focused map-shell adapter for task gesture creation and AAT edit-mode forwarding.
  - Rewire `MapScreenViewModel` to delegate that responsibility.
- Out of scope:
  - UI redesign.
  - `MapOverlayManager` changes.
  - New task/business logic.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Active task payload | `TaskManagerCoordinator` | `taskSnapshotFlow` | `MapScreenViewModel` task copies |
| Active AAT edit waypoint | `TaskManagerCoordinator` | `aatEditWaypointIndexFlow` | local `MutableStateFlow<Boolean>` in map shell code |
| Map task shell gesture creation + callback forwarding | `MapScreenTaskShellCoordinator` | ViewModel-facing methods and state | direct task-shell logic spread across `MapScreenViewModel` |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `aatEditWaypointIndexFlow` | `TaskManagerCoordinator` | `enterAATEditMode`, `exitAATEditMode`, task-type switch, task load/restore validation | `MapTasksUseCase` -> `MapScreenTaskShellCoordinator` -> `MapScreenViewModel` | AAT delegate edit mode plus waypoint-range validation | none | exit edit mode, switch away from AAT, invalidated task/edit index, task load/restore | n/a | `TaskManagerCoordinatorTest`, `MapScreenTaskShellCoordinatorTest` |
| `isAATEditMode` | `MapScreenTaskShellCoordinator` | none directly; derived only | `MapScreenViewModel.isAATEditMode` | `aatEditWaypointIndexFlow != null` | none | whenever edit waypoint becomes `null` | n/a | `MapScreenTaskShellCoordinatorTest` |

### 2.2 Dependency Direction

`UI -> MapScreenViewModel -> MapScreenTaskShellCoordinator -> MapTasksUseCase -> TaskManagerCoordinator`

- Modules/files touched:
  - `feature:tasks`
  - `feature:map-runtime`
  - `feature:map`
- Any boundary risk:
  - avoid reintroducing duplicated AAT edit-mode state in `feature:map`

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenProfileSessionCoordinator.kt` | focused ViewModel collaborator | narrow concern-specific coordinator | task shell seam depends on task runtime use case instead of profile/session dependencies |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenWeGlidePromptBridge.kt` | extracted screen-level adapter seam | keep ViewModel API stable while moving direct responsibility out | none |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| AAT edit-mode shell flag | `MapScreenViewModel` local state | `TaskManagerCoordinator` via `aatEditWaypointIndexFlow` | removes duplicated SSOT | coordinator + shell tests |
| Task gesture creation and AAT edit forwarding | `MapScreenViewModel` | `MapScreenTaskShellCoordinator` | narrows retained map-shell concern to one focused file | shell coordinator tests |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `feature/tasks/src/main/java/com/example/xcpro/tasks/TaskManagerCoordinator.kt` | Existing | canonical task + AAT edit-mode runtime read seam | task authority already lives here | not map-owned state | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/map/MapTasksUseCase.kt` | Existing | map-runtime read adapter over coordinator task/edit state | existing runtime task seam | not UI-specific | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenTaskShellCoordinator.kt` | New | focused map-shell adapter for task gesture creation and AAT edit forwarding | map screen VM seam | not task-core and not UI composable state | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` | Existing | screen state owner and orchestration only | screen ViewModel remains public shell contract | do not make the new coordinator a second ViewModel | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `TaskManagerCoordinator.aatEditWaypointIndexFlow` | `feature:tasks` | `MapTasksUseCase` | public read-only `StateFlow` | remove duplicated map-shell edit-mode state | retain as canonical task edit seam |
| `MapScreenTaskShellCoordinator` | `feature:map` | `MapScreenViewModel` | internal | isolate map-shell task concern | retain while map screen keeps a dedicated task shell |

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| `MapScreenViewModel.createTaskGestureHandler(...)` and AAT edit methods | `MapScreenViewModel` delegating to `MapScreenTaskShellCoordinator` | keep screen-facing API stable for current UI bindings | direct binding to grouped task-shell contract if root/bindings are narrowed further | future root/binding simplification only if needed | `MapScreenTaskShellCoordinatorTest` + existing map compile/tests |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| duplicated AAT edit-mode state reappears in map shell | SSOT, ownership defaults | test + review | `TaskManagerCoordinatorTest`, `MapScreenTaskShellCoordinatorTest` |
| task shell logic drifts back into `MapScreenViewModel` | narrow-file ownership defaults | review + line budget | `MapScreenViewModel.kt` |

## 3) Data Flow (Before -> After)

Before:

```text
TaskManagerCoordinator edit state
  -> hidden AAT delegate state
  -> MapScreenViewModel local MutableStateFlow<Boolean>
  -> UI
```

After:

```text
TaskManagerCoordinator.aatEditWaypointIndexFlow
  -> MapTasksUseCase
  -> MapScreenTaskShellCoordinator
  -> MapScreenViewModel
  -> UI
```

## 4) Implementation Phases

- Goal:
  - remove duplicated map-shell AAT edit-mode state and extract the retained task shell from `MapScreenViewModel`
- Files to change:
  - `TaskManagerCoordinator.kt`
  - `MapTasksUseCase.kt`
  - `MapScreenTaskShellCoordinator.kt`
  - `MapScreenViewModel.kt`
  - focused tests and architecture docs
- Tests to add/update:
  - `TaskManagerCoordinatorTest`
  - `MapScreenTaskShellCoordinatorTest`
- Exit criteria:
  - `MapScreenViewModel` no longer owns a local AAT edit-mode flag
  - map shell reads AAT edit mode from task authority
  - required verification passes

## 5) Test Plan

- Unit tests:
  - coordinator edit-mode flow publish/reset coverage
  - map-shell coordinator flow/gesture creation coverage
- Boundary tests for removed bypasses:
  - AAT edit mode no longer comes from a local ViewModel mirror

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| stale edit-mode index survives task-type switch or load | High | clear and validate canonical edit-mode state in `TaskManagerCoordinator` | Codex |
| new shell coordinator becomes a passive wrapper only | Medium | keep ownership explicit: task gesture creation + edit-mode derivation live there | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: Yes
- ADR file:
  - `docs/ARCHITECTURE/ADR_TASK_RUNTIME_AUTHORITY_2026-03-15.md`
- Decision summary:
  - AAT edit-mode state is part of the task authority seam, and `feature:map` consumes it through a focused shell adapter.
- Why this belongs in an ADR instead of plan notes:
  - it changes the cross-module runtime API and removes a duplicated owner class of bug

## 7) Acceptance Gates

- No duplicate SSOT ownership introduced
- `MapScreenViewModel` remains the single screen-state owner, but no longer owns task edit-mode state directly
- `TaskManagerCoordinator` remains the canonical task runtime read owner

## 8) Rollback Plan

- What can be reverted independently:
  - `MapScreenTaskShellCoordinator` and the ViewModel delegation
  - `aatEditWaypointIndexFlow` consumer rewires
- Recovery steps if regression is detected:
  - revert this slice only and restore the previous local VM flag temporarily
