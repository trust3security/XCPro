# Map Task Runtime Module Right-Sizing

## 0) Metadata

- Title: Reduce `feature:map` breadth by moving reusable task MapLibre runtime code into `feature:map-runtime`
- Owner: XCPro Team
- Date: 2026-03-15
- Issue/PR: TBD
- Status: Completed 2026-03-15

## 1) Scope

- Problem statement:
  - `feature:map` is currently the largest nearby module and still owns reusable non-UI task MapLibre runtime code that fits better in `feature:map-runtime`.
- Why now:
  - Phase 5 fixed the ownership violation, but it increased `feature:map` breadth. This slice trims the module without changing task authority.
- In scope:
  - Move non-Compose task gesture/runtime helpers from `feature:map` to `feature:map-runtime`.
  - Keep Compose/UI wrappers and screen/ViewModel wiring in `feature:map`.
  - Move ownership-aligned tests with the runtime code.
- Out of scope:
  - Task authority/persistence changes.
  - New task features.
  - Broad AAT overlay redesign.
- User-visible impact:
  - None intended.
- Rule class touched: Default

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Task gesture runtime contract | `feature:map-runtime` | reusable runtime types/factory | duplicate map-typed gesture contracts in `feature:map` or `feature:tasks` |
| AAT MapLibre gesture runtime | `feature:map-runtime` | reusable non-UI handler | task-side or UI-owned copies |
| AAT coordinate conversion helper | `feature:map-runtime` | reusable MapLibre converter | duplicate converter implementations across modules |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  - `feature/map-runtime`
  - `feature/map`
- Boundary risk:
  - Avoid moving Compose/UI wrappers into `feature:map-runtime`.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/map-runtime/src/main/java/com/example/xcpro/map/TaskRenderSyncCoordinator.kt` | runtime-only map task collaborator | keep pure runtime owners in `feature:map-runtime` | none |
| `feature/map/src/main/java/com/example/xcpro/map/MapGestureSetup.kt` | UI wrapper over map runtime behavior | keep Compose/UI shell in `feature:map` | none |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| task gesture contract | `feature:map` | `feature:map-runtime` | map runtime reusable seam | compile + tests |
| AAT gesture runtime handler | `feature:map` | `feature:map-runtime` | non-UI MapLibre runtime logic | compile + tests |
| AAT coordinate converter | `feature:map` | `feature:map-runtime` | shared non-UI MapLibre utility | compile + tests |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Map_Task_Runtime_Module_Right_Sizing_2026-03-15.md` | New | focused execution record for this slice | this is a separate follow-on refactor track | not part of the completed ownership plan | No |
| `feature/map-runtime/build.gradle.kts` | Existing | runtime module dependency boundary | runtime-only Compose type deps belong here if required | not a UI module concern | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/gestures/*.kt` | New | reusable task gesture contract/factory | runtime seam is shared by map UI and handlers | not task-core and not UI-specific | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/tasks/aat/gestures/AatGestureHandler.kt` | New | AAT MapLibre gesture runtime | non-UI runtime owner | not screen/UI shell code | No |
| `feature/map-runtime/src/main/java/com/example/xcpro/tasks/aat/map/AATMapCoordinateConverter.kt` | New | shared MapLibre coordinate conversion helper | runtime utility shared by AAT map components | not task-core and not UI-only | No |
| `feature/map/src/main/java/com/example/xcpro/map/MapScreenViewModel.kt` | Existing | screen orchestration only | may need import-only adjustment | still the screen owner | No |
| `feature/map/src/main/java/com/example/xcpro/gestures/*.kt` | Existing | UI gesture shell only after extraction | remove runtime code from the UI module | not the reusable runtime owner | No |
| `feature/map/src/test/java/...` and `feature/map-runtime/src/test/java/...` | Existing/New | ownership-aligned runtime tests | tests should live with the production owner | not in task module once ownership moved | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| task gesture contract/factory | `feature:map-runtime` | `feature:map` | cross-module | map UI still needs a runtime contract | keep package stable while module moves |
| AAT coordinate converter | `feature:map-runtime` | `feature:map`, `feature:map-runtime` | cross-module | shared runtime helper | keep package stable while module moves |

### 2.2I Stateless Object / Singleton Boundary

| Object / Holder | Why `object` / Singleton Is Needed | Mutable State? | Why It Is Non-Authoritative | Why Not DI-Scoped Instance? | Guardrail / Test |
|---|---|---|---|---|---|
| `TaskGestureHandlerFactory` | simple stateless creation helper | No | creates handlers from caller-owned task snapshot/callbacks only | no lifecycle/state to inject | factory unit test |

## 3) Data Flow

Before:

`feature:map` UI/viewmodel -> map-owned task gesture contract/factory/handler -> task mutations`

After:

`feature:map` UI/viewmodel -> `feature:map-runtime` task gesture contract/factory/handler -> task mutations`

## 4) Implementation Phases

### Phase 0
- Goal:
  - Lock the extraction seam and keep package/API compatibility.

### Phase 1
- Goal:
  - Move reusable non-UI task gesture/runtime files and tests to `feature:map-runtime`.
- Outcome:
  - Completed 2026-03-15. Moved the task gesture contract/factory, AAT gesture runtime, AAT coordinate converter, and their ownership-aligned tests into `feature:map-runtime`.

### Phase 2
- Goal:
  - Sync docs and run required gates.
- Outcome:
  - Completed 2026-03-15. Updated `PIPELINE.md`, updated the task runtime ADR, tightened CI guards, and passed the compile plus required Gradle gates.

## 5) Test Plan

- Unit tests:
  - moved AAT gesture tests
  - factory test
- Verification:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
  - `./gradlew :feature:map-runtime:compileDebugKotlin`
  - `./gradlew :feature:map:compileDebugKotlin`
