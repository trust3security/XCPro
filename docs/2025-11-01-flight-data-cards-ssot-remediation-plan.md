# Flight Data Cards SSOT Remediation Plan (2025-11-01)

## Context

Recent refactors moved the core card configuration logic into `FlightDataViewModel`, but the management UI still bypasses the ViewModel and writes directly to `CardPreferences`. This leaves multiple sources of truth in play and the build currently fails because of syntax errors introduced in `FlightDataMgmt.kt`. The outstanding issues risk reintroducing synchronization bugs and blocking further testing.

## Objectives

- Restore clean builds by fixing the `FlightDataMgmt.kt` syntax errors.
- Route *all* flight card reads and writes through `FlightDataViewModel`, making it the single source of truth.
- Remove UI-layer synchronization logic that duplicates ViewModel responsibilities.
- Add coverage to guard the new flows and document the completed architecture.

## Workstreams

1. **Compilation & Hygiene**
   - Repair malformed sections in `FlightDataMgmt.kt` and rerun `./gradlew :app:testDebugUnitTest`.
   - Audit the file for stray debug logging and unused imports added during the stalled refactor.
2. **UI to ViewModel Integration**
   - Replace `CardPreferences` usage in `FlightDataScreensTab` with flows from `FlightDataViewModel` (`profileModeCards`, `profileModeTemplates`, `activeTemplateId`, `activeCards`).
   - Persist card and template changes exclusively via `FlightDataViewModel` APIs (`setProfileCards`, `setProfileTemplate`, etc.).
   - Ensure `FlightMgmt` initializes the shared ViewModel once and passes state down rather than creating new preference instances per composable.
3. **State Synchronization Cleanup**
   - Remove or refactor `LaunchedEffect` blocks that manually fetch/persist template state in the composable; rely on collected flows instead.
   - Confirm `FlightDataManager` only keeps transient real-time data and observes ViewModel state for template/layout updates.
4. **Testing & Validation**
   - Add unit tests covering profile/mode switching and card toggles driven via `FlightDataViewModel` to ensure ViewModel emits expected state.
   - Add instrumentation or Compose UI tests (where feasible) to verify map and management screens react immediately to toggles/profile changes.
   - Re-run `:app:testDebugUnitTest` and lint; capture any additional regressions.
5. **Documentation**
   - Update or retire `Flight_Data_Cards_SSOT_Refactor.md` once the implementation matches the plan.
   - Document the finalized flow in `Flight_Data_Cards.md` or an updated design note so future contributors understand the SSOT boundaries.

## Success Criteria

- `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lint` complete without errors.
- All card state mutations travel through `FlightDataViewModel`; no direct `CardPreferences` calls remain in composables.
- Map and management screens stay in sync without manual DataStore fetches.
- New tests cover the SSOT pathways and prevent regressions.
- Documentation reflects the completed architecture and the dated remediation plan can be archived.
