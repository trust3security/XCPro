# MapScreenRoot Refactor Plan (Perfect‑Grade)

Goal: reduce `MapScreenRoot.kt` to orchestration-only while preserving UI
behavior and architecture rules (MVVM/UDF, SSOT, no UI logic in domain).

Target: < 350 LOC in `MapScreenRoot.kt` with no behavior change.

---

## 0) Constraints (non‑negotiable)

- UI renders state only; mutations go through `MapScreenViewModel` / `MapStateActions`.
- No new state owners in UI helpers.
- No domain logic in Compose helpers.
- No Android UI types in domain layer.

---

## 1) Phase 1 — Manager wiring extraction (largest win)

Create:
`feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenManagers.kt`

Contents:
- `data class MapScreenManagers(...)`
- `@Composable fun rememberMapScreenManagers(...)`
  - constructs: `SnailTrailManager`, `MapOverlayManager`, `MapUIWidgetManager`,
    `MapTaskScreenManager`, `MapCameraManager`, `LocationManager`,
    `MapLifecycleManager`, `MapModalManager`, `MapInitializer`.

**Acceptance:** MapScreenRoot removes ~120+ lines of `remember { ... }` blocks.

---

## 2) Phase 2 — State collection extraction

Create:
`feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenBindings.kt`

Contents:
- `data class MapScreenBindings(...)`
- `@Composable fun rememberMapScreenBindings(viewModel, mapStateReader, flightDataManager)`
  - collects: `gpsStatus`, `currentMode`, `currentZoom`, `replaySession`,
    `suppressLiveGps`, `allowSensorStart`, `locationForUi`, `trailSettings`,
    `flightState`, `showReturnButton`, `showRecenterButton`, `savedLocation`,
    `savedZoom`, `savedBearing`, `hasInitiallyCentered`, `showDistanceCircles`,
    `cardHydrationReady`, `isAATEditMode`, etc.

**Acceptance:** MapScreenRoot is reduced to bindings + render calls.

**Status:** Completed

---

## 3) Phase 3 — Runtime side‑effects extraction

Create:
`feature/map/src/main/java/com/example/xcpro/map/ui/MapScreenRuntimeEffects.kt`

Contents:
- `@Composable fun MapScreenRuntimeEffects(...)`
  - AAT edit mode reset when task type changes
  - drawer gesture blocking logic
  - snail trail update effect
  - orientation/flight‑mode sync

**Acceptance:** All `LaunchedEffect` blocks move out of root composable.

**Status:** Completed

---

## 4) Phase 4 — Scaffold input bundling (optional polish)

Create:
`data class MapScreenScaffoldInputs(...)`

Use:
- Build inputs from `bindings + managers + viewModel`
- Pass a single struct into `MapScreenScaffold`

**Acceptance:** `MapScreenScaffold(...)` call site < 60 lines.

**Status:** Completed

---

## 5) Tests / Validation

- `./gradlew :feature:map:testDebugUnitTest`
- Manual smoke check:
  - map loads, overlay toggles, drawer works
  - replay buttons still functional
  - task UI still responds

---

## 6) Red Flags (review blockers)

- Any helper owns mutable state beyond Compose `remember` locals.
- Any domain logic added inside Compose helper files.
- Any direct repository access from UI helpers.

---

## 7) Definition of Done

- `MapScreenRoot.kt` < 350 LOC
- No behavior change
- Tests pass
