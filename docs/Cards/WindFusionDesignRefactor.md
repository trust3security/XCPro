# Wind Fusion Refactor (Option A: Remove Wind From CompleteFlightData)

Goal: eliminate duplicate wind state by removing wind fields from `CompleteFlightData` and
ensuring all wind consumption flows only through `WindState`.

This plan is incremental and keeps the build green after each step.

## Scope
- Remove wind fields from `CompleteFlightData` and its mappers.
- Stop reading wind from `CompleteFlightData` in UI and helpers.
- Ensure UI uses `WindState` exclusively (via `MapScreenObservers.applyWindState`).

## Non-Goals (for this refactor)
- Full `WindSensorFusionRepository` implementation.
- Wind math changes (EKF, circling, store) beyond wiring.

## Green Build Definition (per step)
Run both:
- `./gradlew :feature:map:testDebugUnitTest`
- `./gradlew :app:assembleDebug`

If build time becomes an issue, the fallback is to run the same commands after every two steps,
but the default is to keep it green after each step.

## Implementation Notes
- Use `// AI-NOTE:` for non-obvious wiring changes so future AI/tools preserve intent.

## IM Plan (Incremental Milestones)

### IM0 - Inventory and Wiring Strategy
Steps:
1) Inventory all reads/writes of wind fields on `CompleteFlightData`.
2) Inventory all wind mappings in `FlightMetricsResult` and `FlightDisplayMapper`.
3) Inventory UI consumers reading wind from `CompleteFlightData`.
4) Identify replacements via `WindState` and `MapScreenObservers.applyWindState`.

Green build:
- Not required (no code changes).

### IM1 - UI Reads Wind Only From WindState (keep old fields temporarily)
Steps:
1) Update `convertToRealTimeFlightData` to stop reading wind from `CompleteFlightData`.
2) Ensure wind values are set only by `MapScreenObservers.applyWindState`.
3) Move any heading calculation that depended on `CompleteFlightData` wind to a place
   that has `WindState` available (likely after `applyWindState`).

Green build:
- `./gradlew :feature:map:testDebugUnitTest`
- `./gradlew :app:assembleDebug`

### IM2 - Remove Wind From Domain Outputs
Steps:
1) Remove wind fields from `FlightMetricsResult`.
2) Update `FlightDisplayMapper` and any downstream mapping to stop carrying wind.
3) Keep `WindState` unchanged; it remains the only wind SSOT.

Green build:
- `./gradlew :feature:map:testDebugUnitTest`
- `./gradlew :app:assembleDebug`

### IM3 - Remove Wind From CompleteFlightData
Steps:
1) Remove wind fields from `CompleteFlightData`.
2) Update constructors, mocks, and tests that still set those fields.
3) Update any logs or debug output that still reference `CompleteFlightData` wind.

Green build:
- `./gradlew :feature:map:testDebugUnitTest`
- `./gradlew :app:assembleDebug`

### IM4 - Cleanup and Docs
Steps:
1) Remove any dead utility methods that only existed for `CompleteFlightData` wind.
2) Confirm `MapScreenObservers` is the sole wind-to-UI adapter.
3) Update `WindFusionDesign.md` (or a follow-up note) to confirm wind SSOT is now only `WindState`.

Green build:
- `./gradlew :feature:map:testDebugUnitTest`
- `./gradlew :app:assembleDebug`

## Tracking Checklist
- [x] IM0 complete
- [x] IM1 complete
- [x] IM2 complete
- [x] IM3 complete
- [x] IM4 complete
