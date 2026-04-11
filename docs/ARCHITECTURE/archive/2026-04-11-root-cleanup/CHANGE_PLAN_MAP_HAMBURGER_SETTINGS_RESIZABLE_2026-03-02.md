# CHANGE_PLAN_MAP_HAMBURGER_SETTINGS_RESIZABLE_2026-03-02.md

## 0) Metadata

- Title: Resizable MapScreen Hamburger and Settings Shortcut Widgets
- Owner: XCPro map/ui
- Date: 2026-03-02
- Issue/PR: TBD
- Status: Draft

## 1) Scope

- Problem statement:
  - MapScreen hamburger and settings shortcut widgets are draggable but not user-resizable.
  - Sizes are currently hardcoded in widget composables (`90dp` and `56dp`) and are not SSOT-managed.
- Why now:
  - The map now has multiple movable overlays; users need consistent scale control for accessibility and cockpit readability.
- In scope:
  - Add persisted size state for `SIDE_HAMBURGER` and `SETTINGS_SHORTCUT`.
  - Add UI edit-mode resize affordance for both widgets.
  - Clamp size values with explicit min/max bounds.
  - Preserve existing drag behavior and settings navigation behavior.
- Out of scope:
  - New settings routes/screens.
  - Changes to task/forecast/replay pipelines.
  - Visual redesign of icon shapes/assets.
- User-visible impact:
  - In map UI edit mode, hamburger and settings shortcut can be resized and remain persisted across app restarts.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Hamburger widget size | `MapWidgetLayoutRepository` via `MapWidgetLayoutUseCase` | `MapWidgetLayoutViewModel` state consumed by `MapScreenRootHelpers` | Hardcoded size-only authority inside widget composables |
| Settings shortcut widget size | `MapWidgetLayoutRepository` via `MapWidgetLayoutUseCase` | `MapWidgetLayoutViewModel` state consumed by `MapScreenRootHelpers` | Independent per-widget ad-hoc persistence paths |
| In-gesture display size while dragging handle | Widget composable local `remember` state | UI-local transient only | Treating transient gesture value as persistent SSOT |

### 2.2 Dependency Direction

Confirmed unchanged:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map` widget layout model/repository/use-case/viewmodel.
  - `feature/map` map UI widget composables and scaffold plumbing.
- Any boundary risk:
  - Low if size policy/clamping is centralized in `MapWidgetLayoutUseCase`.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Hamburger/settings size defaults and bounds | Widget composable default args and local math | `MapWidgetLayoutUseCase` | Keep one authoritative policy and prevent UI drift | Unit tests for load/clamp/save |
| Hamburger/settings size persistence | None (not persisted) | `MapWidgetLayoutRepository` | Persist user customization through existing widget SSOT path | Repository/use-case tests |
| Resize interaction commit path | None | `MapScreenRootHelpers` -> `MapWidgetLayoutViewModel` -> `MapWidgetLayoutUseCase` | Keep UI mutation path aligned with UDF/SSOT | Compose gesture tests + unit tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MapUIWidgets.SideHamburgerMenu` default size arg | Hardcoded `sizeDp = 90f` acting as implicit authority | Inject persisted/clamped size from widget layout state | Phase 2 |
| `MapUIWidgets.SettingsShortcut` default size arg | Hardcoded `sizeDp = 56f` acting as implicit authority | Inject persisted/clamped size from widget layout state | Phase 2 |
| `SideHamburgerMenuImpl` and `SettingsShortcutWidgetImpl` local-only sizing | No persistent write path on resize | Commit through `onSizeChange` callbacks into use-case/repository | Phase 2 |

### 2.3 Time Base

No new time-dependent domain values introduced.

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Widget sizes (dp/px) | N/A | Static UI layout data, not time-based |

Explicitly forbidden comparisons remain unchanged:
- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - UI gesture and resize preview on `Main`.
  - Persistence writes remain in existing repository path.
- Primary cadence/gating sensor:
  - N/A (UI gesture interaction only).
- Hot-path latency budget:
  - Resize and drag updates should remain frame-safe with no blocking work in gesture handlers.

### 2.5 Replay Determinism

- Deterministic for same input: Yes (unchanged).
- Randomness used: No.
- Replay/live divergence rules:
  - None added. Widget size is UI preference only.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Duplicate state owners for widget sizes | SSOT rules (`ARCHITECTURE.md`) | Unit tests + review | `MapWidgetLayoutUseCaseTest` |
| Business/policy drift into composables | UI rules (`CODING_RULES.md`) | Review + enforceRules | widget files + review checklist |
| Off-screen placement after resize/orientation change | UI correctness and stability | Unit tests + compose tests | `MapWidgetLayoutUseCaseTest`, widget gesture tests |
| Gesture regression (tap/long-press/drag/resize conflicts) | Map gesture routing contract | Compose tests | `MapOverlayWidgetGesturesTest` plus new resize tests |

## 3) Data Flow (Before -> After)

Before:

`Widget composable (hardcoded size) -> local drag state -> offset persisted`

After:

`MapWidgetLayoutRepository (offset + size SSOT) -> MapWidgetLayoutUseCase (defaults + clamp) -> MapWidgetLayoutViewModel -> MapScreenRootHelpers -> MapOverlayStack/MapUIWidgets -> widget composable (display + gesture resize preview) -> onSizeCommit -> ViewModel -> UseCase -> Repository`

## 4) Implementation Phases

### Phase 0 - Baseline and Guardrails

- Goal:
  - Lock current behavior and identify exact call chain for both widgets.
- Files to change:
  - none (analysis only).
- Tests to add/update:
  - Confirm baseline tap/long-press tests remain green.
- Exit criteria:
  - Baseline references documented and no behavior changes introduced.

### Phase 1 - SSOT Model Extension (Offset + Size)

- Goal:
  - Extend widget layout SSOT to include hamburger/settings size.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/widgets/MapWidgetLayoutModels.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/widgets/MapWidgetLayoutRepository.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/widgets/MapWidgetLayoutUseCase.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/widgets/MapWidgetLayoutViewModel.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRootHelpers.kt`
- Tests to add/update:
  - `feature/map/src/test/java/com/example/xcpro/map/widgets/MapWidgetLayoutUseCaseTest.kt`
- Exit criteria:
  - Both widget sizes load default, clamp to range, and persist in one authoritative path.

### Phase 2 - UI Wiring and Resize Interaction

- Goal:
  - Thread size state from root to widgets and add resize affordance in edit mode.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRoot.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffoldInputs.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenScaffold.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenContent.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/MapOverlayStack.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/OverlayActions.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/MapUIWidgets.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/SideHamburgerMenuImpl.kt`
  - `feature/map/src/main/java/com/example/xcpro/map/ui/widgets/SettingsShortcutWidgetImpl.kt`
- Tests to add/update:
  - `app/src/test/java/com/example/xcpro/MapOverlayWidgetGesturesTest.kt` (add resize commit assertions).
- Exit criteria:
  - Resize works only in UI edit mode, updates bounds safely, and commits to SSOT.

### Phase 3 - Hardening and Consistency

- Goal:
  - Ensure robust behavior across orientation/size changes and avoid duplicate implementation drift.
- Files to change:
  - Reconcile duplicate hamburger implementation files if both remain (`SideHamburgerMenu.kt` vs `SideHamburgerMenuImpl.kt`).
  - Any shared widget helper files needed for common resize handle behavior.
- Tests to add/update:
  - Add bounds/clamp tests for screen shrink and oversized persisted values.
- Exit criteria:
  - No out-of-bounds widgets after resize or screen dimension changes; one canonical implementation path.

### Phase 4 - Docs and Verification

- Goal:
  - Sync architecture docs and run required checks.
- Files to change:
  - `docs/ARCHITECTURE/PIPELINE.md` (map widget sizing flow update).
  - This change-plan doc status/notes.
- Tests to add/update:
  - none beyond previous phases.
- Exit criteria:
  - Required quality gates pass and docs reflect final wiring.

## 5) Test Plan

- Unit tests:
  - `MapWidgetLayoutUseCaseTest`: defaults, persisted values, clamping for hamburger/settings sizes.
- Replay/regression tests:
  - N/A (no replay path changes).
- UI/instrumentation tests:
  - Extend `MapOverlayWidgetGesturesTest` with edit-mode resize interaction and callback assertions.
- Degraded/failure-mode tests:
  - Persisted size below min or above max clamps correctly.
  - Screen resize/orientation changes re-clamp offsets using current size.
- Boundary tests for removed bypasses:
  - Verify widget render path consumes injected size state, not internal hardcoded authority.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Gesture conflicts between drag and resize | Medium | Restrict resize handle to edit mode; keep tap path unchanged outside edit mode | XCPro map/ui |
| Widget moves off-screen after resize | Medium | Centralized clamp in use-case and UI drag math | XCPro map/ui |
| Duplicate hamburger implementation drift | Medium | Consolidate or enforce single call path in Phase 3 | XCPro map/ui |
| Regression in drawer/settings tap behavior | Low | Keep existing tap callbacks and add regression tests | XCPro map/ui |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling remains unchanged and explicit.
- Replay behavior remains deterministic.
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved.

## 8) Rollback Plan

- What can be reverted independently:
  - Revert resize UI affordance while keeping size-read path.
  - Revert size persistence extension and fall back to fixed defaults.
- Recovery steps if regression is detected:
  - Pin widgets back to existing hardcoded sizes (`90dp`/`56dp`) and keep offset persistence intact.
  - Re-run required checks before merging rollback.
