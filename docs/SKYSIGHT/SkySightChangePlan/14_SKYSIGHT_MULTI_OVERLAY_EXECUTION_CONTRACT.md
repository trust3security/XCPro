# 14 SkySight Multi Overlay Execution Contract

Date: 2026-02-17
Owner: XCPro Team
Status: Ready for implementation

## Purpose

Implement SkySight-style concurrent overlays in XCPro where one primary
forecast product can be displayed together with an optional wind overlay,
without violating MVVM + UDF + SSOT architecture rules.

## Read order before coding

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/SKYSIGHT/SKYSIGHT_MULTI_OVERLAY_IMPLEMENTATION_NOTES.md`
6. `docs/SKYSIGHT/SkySightChangePlan/13_WIND_DISPLAY_MODES_IMPLEMENTATION_PLAN.md`

## Non-negotiables

- Keep a single primary parameter selected at a time.
- Add wind as a secondary optional overlay, not as a second primary stack.
- Keep all MapLibre runtime layer operations in map runtime classes only.
- Keep ViewModel/use-case boundaries intact.
- Keep replay and flight-data pipelines unchanged.

## Scope

In scope:

- Primary + wind concurrent display in map runtime.
- Wind enable toggle and mode selection (arrow/barb; streams optional by flag).
- Stable z-ordering and cleanup across parameter/time/style changes.
- Error handling where wind failure does not clear valid primary layer.

Out of scope (this contract):

- Multi-primary stacking (example: rain + thermal + cloud at once).
- New backend/proxy services.
- Replay/domain algorithm changes.

## Target behavior

- User selects one primary forecast parameter.
- User can enable wind overlay at the same time.
- Rain + wind works.
- Thermal + wind works.
- Thermal height + wind works.
- Wind mode switch updates wind symbols without breaking primary layer.

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
  - primary selection
  - wind overlay enabled/mode state
- Add missing use-case methods if any for wind toggle/mode intent path.
- Keep SSOT in `ForecastPreferencesRepository` and emitted UI state.

Exit criteria:

- One authoritative overlay state flow provides primary + wind config.

### Phase 2 - Runtime renderer composition

Tasks:

- Split runtime render path into composable branches:
  - primary branch
  - wind branch
- Ensure both branches can coexist on the map style simultaneously.
- Implement deterministic cleanup for:
  - primary change
  - wind enable/disable change
  - style reload
- Keep primary layer visible when wind fetch/render fails.

Exit criteria:

- Primary+wind can render together and switch without orphan layers.

### Phase 3 - UI controls and interaction

Tasks:

- Confirm Forecast UI exposes:
  - primary parameter selector
  - wind enable toggle
  - wind mode selector
- Ensure controls map to SSOT and survive process restart.

Exit criteria:

- UI can consistently reproduce rain+wind and thermal+wind states.

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

- [ ] Primary + wind render together (rain + wind).
- [ ] Primary + wind render together (thermal + wind).
- [ ] Primary + wind render together (thermal height + wind).
- [ ] Wind mode change does not clear primary overlay.
- [ ] Wind failure does not clear valid primary overlay.
- [ ] Style reload restores both overlay branches.
- [ ] Track Up and North Up remain stable and readable.

## Rollback plan

If regression occurs:

1. Disable secondary wind overlay composition path via feature flag or guarded branch.
2. Keep single-primary rendering path active.
3. Re-run required verification commands.
4. Re-introduce multi-overlay path only after failing case is covered by tests.
