# MapScreenViewModel Refactor Plan (Perfect-Grade)

Purpose: reduce `MapScreenViewModel.kt` to orchestration-only while enforcing
SSOT, MVVM/UDF, and time‑base rules. No behavior changes.

This plan is **incremental** and **reversible** at each step.

---

## 0) Constraints (hard rules)

- SSOT stays in repositories and `MapStateStore`.
- ViewModel owns UI state only (`MutableStateFlow` + `SharedFlow`).
- Helpers may **not** own state; they only forward actions/callbacks.
- No file I/O in UI layer (see CODING_RULES §7). If found, isolate to use-case.
- No Android UI types in domain layer.

---

## 1) Target Structure (end state)

`MapScreenViewModel.kt` should contain:
- Constructor + injected dependencies
- `MapStateStore` creation
- Flow wiring (derived state only)
- Helper wiring + `init` bootstraps
- Public API surface (UI intents)

Everything else moves to small, stateless helpers.

---

## 2) Phase 1 — MapStateActions delegate (fast win)

**Goal:** remove boilerplate methods that simply forward to `MapStateStore`.

Add:
`feature/map/src/main/java/com/example/xcpro/map/MapStateActionsDelegate.kt`

Responsibilities:
- Implements `MapStateActions`
- Delegates to `MapStateStore`
- No state, no side effects beyond forwarding.

Change in `MapScreenViewModel`:
- Expose `val mapStateActions: MapStateActions = MapStateActionsDelegate(mapStateStore)`
- Update `MapScreenRoot` to pass `mapViewModel.mapStateActions` into runtime managers.
- Remove action methods from the ViewModel.

**Acceptance:** behavior unchanged, ~40–50 LOC removed.
**Status:** ✅ implemented.

---

## 3) Phase 2 — UI event handler extraction

**Goal:** isolate drawer/edit‑mode mutations + UI event dispatch.

Add:
`feature/map/src/main/java/com/example/xcpro/map/MapScreenUiEventHandler.kt`

Responsibilities:
- `onEvent(MapUiEvent)`
- `setUiEditMode`, `toggleDrawer`, `setDrawerOpen`
- Uses ViewModel callbacks to mutate `_uiState`/`_uiEffects`.

Inputs:
- `_uiState: MutableStateFlow<MapUiState>`
- `_uiEffects: MutableSharedFlow<MapUiEffect>`

**Acceptance:** ViewModel no longer contains any drawer/edit logic.
**Status:** ✅ implemented.

---

## 4) Phase 3 — Waypoint loading isolation (fix rule gap)

**Goal:** remove file I/O from ViewModel.

Add:
`feature/map/src/main/java/com/example/xcpro/map/domain/LoadWaypointsUseCase.kt`

Responsibilities:
- `suspend fun execute(): Result<List<WaypointData>>`
- Uses `WaypointLoader` + `Context` internally.

Change in `MapScreenViewModel`:
- Inject `LoadWaypointsUseCase`
- Replace direct `waypointLoader.load(context)` with `useCase.execute()`
- Keep UI state mutations in ViewModel.

**Acceptance:** ViewModel no longer performs file I/O.

---

## 5) Phase 4 — QNH actions extraction

Add:
`feature/map/src/main/java/com/example/xcpro/map/MapQnhActions.kt`

Responsibilities:
- `onAutoCalibrateQnh()`
- `onSetManualQnh(hpa)`
- No state; emits UI effects via callback.

**Acceptance:** ViewModel no longer contains QNH business flow code.

---

## 6) Phase 5 — Variometer layout actions extraction

Add:
`feature/map/src/main/java/com/example/xcpro/map/VariometerLayoutActions.kt`

Responsibilities:
- `ensureVariometerLayout(...)`
- `onVariometerOffsetCommitted(...)`
- `onVariometerSizeCommitted(...)`

**Acceptance:** ViewModel keeps only orchestration.

---

## 7) Final Cleanup (polish)

- Move `QnhCalibrationFailureReason.toUserMessage()` into a dedicated file:
  `feature/map/src/main/java/com/example/xcpro/map/qnh/QnhMessages.kt`
- Ensure `MapScreenViewModel.kt` < 400 LOC.

---

## 8) Tests / Validation

Required:
- Unit tests pass (`:feature:map:testDebugUnitTest`)
- Smoke check: map loads, replay still works, waypoint load still works.

---

## 9) Red Flags (review blockers)

- Any helper that introduces new state ownership
- UI logic moved into domain classes
- File I/O in ViewModel
- Multiple sources mutating UI state

---

## 10) Definition of Done

- `MapScreenViewModel.kt` is orchestration‑only and < 400 LOC
- No behavior change (verified via tests + smoke)
- All rules in ARCHITECTURE/CODING_RULES respected
