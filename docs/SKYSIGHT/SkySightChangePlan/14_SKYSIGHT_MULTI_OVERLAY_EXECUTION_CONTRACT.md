# 14 SkySight Multi Overlay Execution Contract

Date: 2026-02-17
Owner: XCPro Team
Status: Ready for implementation

## Purpose

Implement concurrent forecast overlays in XCPro where up to two non-wind
forecast products can be displayed together with an optional wind overlay,
without violating MVVM + UDF + SSOT architecture rules.

Contract update (2026-02-25):
- A SkySight tab wiring regression currently prevents intended non-wind pair
  selection from the active UI path.
- This contract locks expected behavior and remediation scope.

## Read order before coding

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/SKYSIGHT/SKYSIGHT_MULTI_OVERLAY_IMPLEMENTATION_NOTES.md`
6. `docs/SKYSIGHT/SkySightChangePlan/13_WIND_DISPLAY_MODES_IMPLEMENTATION_PLAN.md`

## Non-negotiables

- Keep exactly one selected primary non-wind parameter at a time.
- Allow one optional secondary non-wind parameter in parallel with primary.
- Add wind as an additional optional overlay branch.
- Keep all MapLibre runtime layer operations in map runtime classes only.
- Keep ViewModel/use-case boundaries intact.
- Keep replay and flight-data pipelines unchanged.

## Scope

In scope:

- Primary + secondary non-wind + wind concurrent display in map runtime.
- Stable selection state transitions for non-wind pairing from SkySight tab UI.
- Wind enable toggle and mode selection (arrow/barb; streams optional by flag).
- Stable z-ordering and cleanup across parameter/time/style changes.
- Error handling where wind failure does not clear valid primary layer.

Out of scope (this contract):

- More than two concurrent non-wind primary products
  (example: rain + thermal + cloud all at once).
- New backend/proxy services.
- Replay/domain algorithm changes.

## Target behavior

- User selects one primary forecast parameter.
- User can optionally add one secondary non-wind forecast parameter.
- User can optionally enable wind overlay at the same time.
- Convergence + Rain works.
- Rain + wind works.
- Convergence + Rain + wind works.
- Wind mode switch updates wind symbols without breaking non-wind overlays.

Regression fix requirement (2026-02-25):
- SkySight tab parameter chip actions must use toggle selection use-cases, not a
  single-select path that force-disables secondary non-wind state.
- SkySight tab UI must surface secondary non-wind state explicitly.

## Phase plan

### Phase 0 - Baseline and contract lock

Tasks:

- Verify current layer ownership and IDs in `ForecastRasterOverlay` and
  `MapOverlayManager`.
- Define final runtime layer ordering contract for:
  - primary fill/symbol
  - wind symbol/streams
  - operational overlays (airspace/task/traffic/ownship)

Exit criteria:

- Layer ownership and z-order are documented in code comments and this file.

### Phase 1 - State model and use-case wiring

Tasks:

- Ensure forecast state carries both:
  - primary non-wind selection
  - secondary non-wind selection + enabled state
  - wind overlay enabled/mode state
- Add missing use-case methods if any for wind toggle/mode intent path.
- Ensure SkySight tab intent path invokes toggle use-case behavior for non-wind
  pair selection transitions.
- Keep SSOT in `ForecastPreferencesRepository` and emitted UI state.

Exit criteria:

- One authoritative overlay state flow provides primary + secondary + wind config.

### Phase 2 - Runtime renderer composition

Tasks:

- Split runtime render path into composable branches:
  - primary branch
  - secondary non-wind branch
  - wind branch
- Ensure both branches can coexist on the map style simultaneously.
- Implement deterministic cleanup for:
  - primary change
  - wind enable/disable change
  - style reload
- Keep primary layer visible when wind fetch/render fails.

Exit criteria:

- Primary+secondary+wind can render together and switch without orphan layers.

### Phase 3 - UI controls and interaction

Tasks:

- Confirm Forecast UI exposes:
  - primary parameter selector
  - secondary non-wind selector/toggle
  - wind enable toggle
  - wind mode selector
- Ensure controls map to SSOT and survive process restart.

Exit criteria:

- UI can consistently reproduce convergence+rain, rain+wind, and
  convergence+rain+wind states.

### Phase 4 - Tests and hardening

Tasks:

- Add/update unit tests for repository/viewmodel state propagation.
- Add runtime tests for layer coexistence and cleanup.
- Add regression checks for fatal vs non-fatal errors:
  - wind errors non-fatal to primary
  - primary fatal errors clear overlay as expected

Exit criteria:

- Tests cover multi-overlay behavior and branch transitions.

### Phase 5 - Verification and docs sync

Tasks:

- Run required checks.
- Update pipeline/docs if runtime wiring changed.
- Keep `KNOWN_DEVIATIONS.md` unchanged unless approved exception is required.

Exit criteria:

- All checks pass and docs reflect shipped behavior.

## Required verification commands

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator is available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
```

## Acceptance checklist

- [ ] Primary + secondary render together (convergence + rain).
- [ ] Primary + wind render together (rain + wind).
- [ ] Primary + secondary + wind render together (convergence + rain + wind).
- [ ] Wind mode change does not clear primary overlay.
- [ ] Wind failure does not clear valid primary overlay.
- [ ] Style reload restores both overlay branches.
- [ ] Track Up and North Up remain stable and readable.

## Rollback plan

If regression occurs:

1. Disable secondary non-wind overlay composition path via feature flag or guarded branch.
2. Keep single-primary rendering path active.
3. Re-run required verification commands.
4. Re-introduce multi-overlay path only after failing case is covered by tests.
