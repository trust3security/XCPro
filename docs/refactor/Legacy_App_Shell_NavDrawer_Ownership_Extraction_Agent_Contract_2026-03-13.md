# Legacy App Shell / NavDrawer Ownership Extraction Agent Contract

## Purpose

This contract governs implementation of:

- `docs/refactor/Legacy_App_Shell_NavDrawer_Ownership_Extraction_Plan_2026-03-13.md`

It exists to prevent ad hoc cleanup and package-wide churn while extracting the
legacy app-shell/navdrawer surface to the correct long-term owners.

## Mission

Implement the long-term ownership cleanup for:

- `AppNavGraph.kt`
- `Support.kt`
- `About.kt`
- `ManageAccount.kt`
- `Task.kt`

without turning the work into a broad `com.example.ui1.screens` rename.

## Non-Negotiables

- Owner-first, wrapper-first, package-rename-last.
- `:app` remains the root nav graph owner.
- `:feature:map-runtime` is out of scope.
- No package-wide rename in this program.
- No broad cleanup outside the phase in progress.
- Route names stay stable unless the plan explicitly changes them.

## Phase Order

Strict phase order:

1. Phase 0 - Baseline and ownership freeze
2. Phase 1 - App-shell extraction for Support/About
3. Phase 2 - Profile-owned Manage Account extraction
4. Phase 3 - Task route decomposition
5. Phase 4 - Legacy package severance and explicit imports

Do not skip ahead.

## Mandatory Pre-Phase Rule

Before each phase:

1. Do one seam pass for that phase only.
2. Confirm:
   - exact files in scope
   - exact files out of scope
   - route stability strategy
   - whether tests/SLO validation are required
3. If the seam pass changes the safe scope materially, update the plan first.

Do not recursively re-audit the whole legacy surface before every phase.

## Allowed Work By Phase

### Phase 1

Allowed:
- Move `Support`/`About` content to `:app`
- Add thin compatibility wrappers if required
- Stabilize route behavior
- Preserve the existing support callback surface (`onShowBottomSheet`, `onHideBottomSheet`) until the route owner explicitly changes it
- Remove `SupportCopy.kt` only if the real support owner path is stable

Forbidden:
- Moving `Task.kt`
- Package-wide rename
- Generic cleanup of unrelated legacy screens

Phase-specific seam rule:
- Treat `Support.kt` / `About.kt` as simple app-shell route moves only.
- `NavigationDrawer.kt` and menu registration are not a reason to widen scope.
- `SupportCopy.kt` remains out of scope unless route stabilization is already complete.

### Phase 2

Allowed:
- Move `ManageAccount` content to `:feature:profile`
- Wire the existing Edit Profile path properly
- Align with profile-owned routes

Forbidden:
- Task route work
- Broad navdrawer modernization

Phase-specific seam rule:
- Keep this as a profile-owner move, not a general profile/settings redesign.
- If the `Edit Profile` path needs broader profile work than the current route wiring, stop and update the plan.

### Phase 3

Allowed:
- Split `Task.kt` into route shell + task-owned content
- Add temporary compatibility shims
- Preserve the existing route callback surface (`selectedNavItem`, `onShowBottomSheet`, `onHideBottomSheet`) while ownership moves
- Touch task/map tests required to preserve behavior

Forbidden:
- Bundling unrelated map shell cleanup into the same phase
- Broad `com.example.ui1.screens` rename

Phase-specific seam rule:
- `Task.kt` is a route/content decomposition phase, not a file move.
- `TaskScreenUseCasesViewModel.kt` is in scope for this phase.
- `FlightDataMgmt.kt` and related legacy task/flight-data screens are seam evidence only unless the phase-specific seam pass proves they must move.
- Do not assume the whole legacy `Task.kt` body belongs in `:feature:tasks`; prove ownership per extracted slice.
- The existing `:feature:tasks` file UI is not an automatic replacement for the current airspace/waypoint/class bottom-sheet path.
- Keep the embedded MapLibre route shell in `feature:map` unless the seam pass proves a smaller shell wrapper is safe.

### Phase 4

Allowed:
- Narrow `AppNavGraph.kt` to explicit owner imports
- Extract shared legacy models only if still required to remove the wildcard import
- Narrow other direct retained callsites only if they are in the scoped blocker list (`SettingsDfRuntimeRouteSubSheets`, `MapScreenScaffold`) and the owner move already justifies it

Forbidden:
- Opportunistic package cleanup outside the scoped blockers
- Treating Phase 4 as mandatory full package elimination when explicit blocker accounting is the safer end state

Phase-specific seam rule:
- Treat `SettingsDfRuntime.kt` / `GeneralSettingsSheetHost` and `MapScreenScaffold.kt` as real retained blockers unless a prior owner move already resolves them.
- Keep `Logbook.kt` deferred unless the plan is explicitly widened in a later program.

## Stop Rules

Stop and report only if:

- a real cross-module back-edge appears
- a phase requires unplanned shared-model ownership not described in the plan
- a verification failure changes the safe scope
- the planned ownership goal for the current phase is fully satisfied

Do not stop merely because a phase completed. Continue until Phase 4 is complete unless a stop rule is triggered.

## Verification

For each non-trivial implementation phase, run:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

If Phase 3 touches embedded map behavior, also run the relevant task/map tests and required SLO validation.

## Documentation Sync

Update only active docs:

- the main plan
- this contract
- `PIPELINE.md` only if route/content wiring actually changes in a way the pipeline doc covers

Do not rewrite historical evidence docs.

## Final Rule

This program succeeds only if long-term ownership improves with low churn.

If a proposed change is mostly package churn, naming churn, or unrelated cleanup, it is out of scope.
