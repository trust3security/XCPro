# Legacy App Shell / NavDrawer Ownership Extraction Plan

## 0) Metadata

- Title: Legacy App Shell / NavDrawer Ownership Extraction
- Owner: XCPro Team
- Date: 2026-03-13
- Issue/PR: TBD
- Status: In Progress (Phases 1-4 implemented)
- Execution contract: `docs/refactor/Legacy_App_Shell_NavDrawer_Ownership_Extraction_Agent_Contract_2026-03-13.md`

## 1) Scope

- Problem statement:
  - The legacy `com.example.ui1.screens` surface now spans `:app`, `:feature:map`, `:feature:profile`, `:feature:traffic`, and `:feature:weglide`.
  - `AppNavGraph.kt` still imports that surface via a wildcard, and several route screens are owned by the wrong module long term.
  - The specific long-term owner questions raised for `AppNavGraph.kt`, `Support.kt`, `About.kt`, `ManageAccount.kt`, and `Task.kt` cannot be solved safely by a package-wide rename. The real problem is mixed route/content ownership.
- Why now:
  - `feature:map` has already been thinned substantially.
  - The remaining legacy navdrawer/app-shell screens are now the clearer long-term ownership drift.
  - A dedicated plan avoids ad hoc moves and prevents broad `com.example.ui1.screens` churn.
- In scope:
  - Long-term ownership for:
    - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Support.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/About.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ManageAccount.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Task.kt`
  - Boundary files and route helpers that block those moves:
    - `feature/map/src/main/java/com/trust3/xcpro/navdrawer/DrawerMenuSections.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/TaskScreenUseCasesViewModel.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/screens/flightdata/FlightDataModels.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SupportCopy.kt`
  - Import/route cleanup in `AppNavGraph.kt`.
  - Compatibility-wrapper strategy so route names stay stable while ownership changes.
  - Identification of retained legacy route/helper blockers that must stay explicit if Phase 4 stops short of full package severance.
- Out of scope:
  - Package-wide rename of all `com.example.ui1.screens` files in one phase.
  - `feature:map-runtime` work.
  - Generic settings/runtime cleanup for `SettingsDfRuntime*`.
  - Whole-screen redesigns or UX changes.
  - Traffic/WeGlide sub-sheet ownership changes, except as seam evidence.
- User-visible impact:
  - None intended during the phased move.
  - Route names should stay stable.
  - UI behavior should remain unchanged while ownership improves.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data / responsibility | Owner | Exposed As | Forbidden duplicates |
|---|---|---|---|
| Root application nav graph | `:app` | `AppNavGraph` | Feature-local duplicate root graphs |
| Support / About static shell content | `:app` | App-shell route screens | Copies in `feature:map` long term |
| Account/profile management content | `:feature:profile` | Profile-owned screen/content | Map-owned account screen content |
| Task route shell | `:feature:map` or `:app` compatibility shell | Thin route wrapper only | Embedded MapLibre/content-heavy route body living forever in `feature:map` |
| Task-owned task editor/content | `:feature:tasks` | Task-owned content host / route body | Generic assumption that the whole legacy `Task.kt` file is already task-owned |
| Drawer route registration | `:app` | `NavHost` destinations | Duplicate route registration in feature modules |
| Shared legacy file/airspace row models | Neutral owner, if still needed | Explicit shared model file | Continued hidden dependence on `com.example.ui1.screens` package for shared models |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`app shell -> feature UI/content -> feature/domain/data`

- Modules/files touched:
  - `:app`
  - `:feature:map`
  - `:feature:profile`
  - `:feature:tasks`
- Boundary risk:
  - `:app` must remain the root route assembler.
  - `:feature:profile` and `:feature:tasks` must not depend back on `:app`.
  - `:feature:map` must not remain the long-term owner for generic drawer pages.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Root graph assembly | `:app` | `:app` (unchanged) | Already correct long-term owner | `AppNavGraph.kt` remains root `NavHost` |
| Support screen content | `:feature:map` | `:app` | Static app-shell page, not map/runtime | Route still works; no `feature:map` ownership needed |
| About screen content | `:feature:map` | `:app` | Static app-shell page, not map/runtime | Route still works; packaged class resolves from app owner |
| Manage Account screen content | `:feature:map` | `:feature:profile` | Profile/account-owned UI and already tracked in profile docs | Edit Profile entrypoint aligns with profile flow |
| Task route body | Legacy `com.example.ui1.screens` file in `:feature:map` | Modern `feature:map` navdrawer owner with legacy wrapper | Decompose the route before any later owner split by slice | Task route still works through wrapper/shim |
| Wildcard legacy imports in `AppNavGraph.kt` | Mixed legacy package | Explicit owner imports | Reduce hidden cross-module coupling | `AppNavGraph.kt` compiles with explicit imports |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `AppNavGraph.kt` | `import com.example.ui1.screens.*` | Explicit imports from owner modules | Phase 4 |
| `ManageAccount.kt` | Profile entrypoint TODO/no-op in map-owned screen | Profile-owned content with real profile navigation | Phase 2 |
| `Task.kt` | Legacy map-owned task screen with direct map/airspace/waypoint wiring | Thin route shim + decomposed content, with owner decided per extracted slice | Phase 3 |

### 2.3 Time Base

This plan is route/content ownership work. No new time-dependent domain logic is intended.

| Value | Time Base | Why |
|---|---|---|
| N/A | N/A | Ownership move only |

Explicitly forbidden:
- Introducing direct wall/system time usage while moving screens.

### 2.4 Threading and Cadence

- Dispatcher ownership: unchanged
- Primary cadence/gating sensor: unchanged
- Hot-path latency budget: unchanged

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - None are intended in this plan.
  - If `Task.kt` decomposition touches embedded map/task runtime behavior, replay and map visual behavior must be verified separately.

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| Wrong module ownership for generic shell pages | `ARCHITECTURE.md` dependency direction | Plan review + compile | This plan |
| New direct business logic in app shell | `CODING_RULES.md` UI/ViewModel rules | Review + tests | Route-content split phases |
| Ad hoc package-wide rename churn | Change safety requirements | Agent contract + phase gate | Agent contract |
| Legacy wildcard import survives long term | Maintainability/change safety | Compile + review | `AppNavGraph.kt` |
| Task route move breaks map behavior | Map visual/runtime contract | Tests + map validation when touched | Phase 3 specific |

### 2.7 Visual UX SLO Contract

Only Phase 3 may touch embedded task/map behavior. If that phase changes map interaction or overlay/runtime behavior:
- declare impacted SLO IDs before implementation
- follow `docs/MAPSCREEN/02_BASELINE_PROFILING_AND_SLO_MATRIX_2026-03-05.md`
- follow `docs/MAPSCREEN/04_TEST_VALIDATION_AND_ROLLBACK_2026-03-05.md`

## 3) Data Flow (Before -> After)

Before:

```text
AppNavGraph (:app)
  -> wildcard imports from com.example.ui1.screens
  -> routes into mixed legacy package
     -> static shell pages in :feature:map
     -> profile screen in :feature:profile but legacy package
     -> traffic/weglide sub-sheets in owner modules but legacy package
     -> heavy Task screen in :feature:map
```

After:

```text
AppNavGraph (:app)
  -> explicit owner imports
  -> route wrappers / route registration in :app
     -> app-shell content (Support/About) in :app
     -> profile-owned account content in :feature:profile
     -> task-owned content in :feature:tasks
     -> compatibility wrappers only where temporarily required
```

## 4) Seam Findings That Shape The Plan

These findings came from the code pass and are the reason this plan is owner-first and wrapper-first:

1. `com.example.ui1.screens` already spans multiple modules:
   - `:feature:map`
   - `:feature:profile`
   - `:feature:traffic`
   - `:feature:weglide`
2. `AppNavGraph.kt` imports that shared surface with a wildcard, so package cleanup before owner cleanup would create noisy cross-module churn.
3. `ManageAccount.kt` is clearly profile-owned and is already tracked as a profile gap in:
   - `docs/03_Features/Profiles_Current_Architecture.md`
   - `docs/03_Features/Profiles_Workboard.md`
4. `Task.kt` is not a simple shell screen. It directly owns:
   - `MapView` / `MapLibreMap`
   - `AirspaceViewModel`
   - `WaypointsViewModel`
   - `TaskScreenUseCasesViewModel`
   - file pickers and gesture/camera behavior
   This means `Task.kt` cannot be handled in the same low-risk phase as `Support.kt` / `About.kt`.
5. `FlightDataModels.kt` still defines `FileItem` and `AirspaceClassItem` in the legacy package, and those models are imported by map/airspace/waypoint flows. Shared-model extraction must be deliberate, not opportunistic.
6. `SupportCopy.kt` is a stale duplicate and should be removed only when the support route ownership is stabilized.
7. `GeneralSettingsSheetHost` still lives in the legacy package and is imported by `MapScreenScaffold.kt`. That proves package-wide severance must be deferred until after owner moves.
8. `ProfilesScreen` already lives in `:feature:profile` while keeping the legacy package, and `AdsbSettingsSubSheet` / `OgnSettingsSubSheet` / `HotspotsSettingsSubSheet` / `WeGlideSettingsSubSheet` already live in owner modules with the same pattern. That is the right precedent: move ownership first, defer package cleanup.
9. `Support.kt` is not just a static route name; it also carries `onShowBottomSheet` / `onHideBottomSheet` callbacks from `AppNavGraph.kt`. Phase 1 must keep that callback contract stable while moving ownership.
10. `Task.kt` is not just a route destination; `AppNavGraph.kt` currently passes `selectedNavItem`, `onShowBottomSheet`, and `onHideBottomSheet`. Phase 3 must preserve that shell callback surface while decomposing the screen.
11. The current `Task.kt` implementation is only nominally "task" content. It does not depend on `:feature:tasks` UI/content at all; instead it directly owns:
   - `MapView` / `MapLibreMap`
   - `AirspaceViewModel`
   - `WaypointsViewModel`
   - `TaskScreenUseCasesViewModel` (airspace use-case only)
   - `loadAndApplyAirspace(...)`
   - `loadAndApplyWaypoints(...)`
   - file pickers and gesture/camera state
   This means Phase 3 cannot assume the whole route body should move to `:feature:tasks`.
12. The existing task-owned file UI in `:feature:tasks` is not a drop-in replacement for the legacy `Task.kt` bottom sheet:
   - `feature/tasks/src/main/java/com/trust3/xcpro/tasks/TaskFilesTab.kt` manages persisted task import/export/share
   - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/tasks/TaskFilesSheetContent.kt` manages airspace, waypoint, and class toggles/imports
   So Phase 3 must not force a wrong owner move just because a `FilesBTTab` already exists.
13. `AppNavGraph.kt` is not the only retained legacy-package callsite. The following still keep the old package surface alive:
   - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntimeRouteSubSheets.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt`
14. The retained route/helper blockers after Phases 1-3 are likely:
   - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Files.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Logbook.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/screens/DFNavboxes.kt`
   - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntime.kt` (`GeneralSettingsSheetHost`)
   - `feature/profile/src/main/java/com/example/ui1/screens/Profiles.kt`
   That means Phase 4 should be framed as explicit-import narrowing and blocker accounting, not guaranteed full package elimination.

## 5) Implementation Phases

### Phase 0 - Baseline and ownership freeze

- Goal:
  - Freeze the long-term ownership targets before any code move.
  - Confirm no new feature work adds fresh `com.example.ui1.screens` dependencies in the scoped screens.
- Files to change:
  - Plan/contract docs only.
- Tests to add/update:
  - None.
- Exit criteria:
  - Ownership targets are documented.
  - In-scope/out-of-scope boundaries are explicit.
  - Phase-by-phase seam findings are recorded before code moves begin.

### Phase 1 - App-shell extraction for Support/About

- Goal:
  - Move `Support.kt` and `About.kt` content out of `feature:map` and into `:app`.
  - Keep route names stable (`"support"`, `"about"`).
  - Preserve the current support bottom-sheet callback contract while ownership moves.
- Files to change:
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
  - new app-shell screen files under `:app`
  - temporary compatibility wrappers only if required
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SupportCopy.kt` only after route stabilization
- Phase 1 seam findings:
  - `Support.kt` and `About.kt` are genuinely low-risk app-shell candidates.
  - Both screens are simple route shells with `NavHostController` / `DrawerState` back behavior and no map-runtime ownership.
  - `Support.kt` still owns a local bottom-sheet contract through `onShowBottomSheet` / `onHideBottomSheet`; that callback surface must remain stable in this phase.
  - `NavigationDrawer.kt` is not a blocker for this phase; it only routes through `BottomMenuItems`.
  - `SupportCopy.kt` is a stale duplicate and should remain out of scope unless the real support owner path is already stabilized.
- Tests to add/update:
  - Basic route compile validation.
- Exit criteria:
  - `MySupport`/`MyAbout` no longer need `feature:map` ownership.
  - Route behavior unchanged.
  - Status: Implemented 2026-03-13
  - Notes:
    - `MySupport` and `MyAbout` now live in `:app`.
    - `AppNavGraph.kt` now imports the app-owned route screens explicitly.
    - `feature:map` no longer owns `Support.kt` / `About.kt`.
    - `assembleDebug` passed after the move.

### Phase 2 - Profile-owned Manage Account extraction

- Goal:
  - Move `ManageAccount.kt` content into `:feature:profile`.
  - Align the screen with the existing profile flow and workboard item for Edit Profile.
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ManageAccount.kt`
  - new/updated profile-owned screen in `:feature:profile`
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
  - if needed, `feature/map/src/main/java/com/trust3/xcpro/navdrawer/DrawerMenuSections.kt`
- Phase 2 seam findings:
  - `ManageAccount.kt` is still a simple route/content screen with no map-runtime ownership.
  - The real blockers are route wiring and finishing the existing profile-owned `Edit Profile` path, not code complexity.
  - This remains a low-risk owner move as long as it stays aligned with the profile workboard and does not widen into general profile UX cleanup.
- Tests to add/update:
  - Profile navigation path tests or route smoke coverage.
- Exit criteria:
  - Manage Account content is profile-owned.
  - Edit Profile no longer stays TODO/no-op.
  - Status: Implemented 2026-03-13
  - Notes:
    - `ManageAccount` now lives in `:feature:profile`.
    - `Edit Profile` now routes to the active profile settings when available, or to profile selection otherwise.
    - `feature:map` no longer owns `ManageAccount.kt`.
    - `assembleDebug` passed after the move.

### Phase 3 - Task route decomposition

- Goal:
  - Split `Task.kt` into a thin route shell and decomposed content slices.
  - Identify which slices are genuinely task-owned and which remain flight-data/map-shell owned.
  - Preserve the current route-local shell inputs:
    - `selectedNavItem`
    - `onShowBottomSheet`
    - `onHideBottomSheet`
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Task.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/TaskScreenUseCasesViewModel.kt`
  - new task-owned content host in `:feature:tasks`
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
- Phase 3 seam findings:
  - `Task.kt` is the only heavy/risky phase in this program.
  - It is a route/content split target, not a safe blind owner move.
  - It still owns `MapView`, `MapLibreMap`, `AirspaceViewModel`, `WaypointsViewModel`, `TaskScreenUseCasesViewModel`, file pickers, and map/camera behavior.
  - `TaskScreenUseCasesViewModel.kt` remains part of the legacy seam and stays in Phase 3 scope.
  - The first safe split is likely to keep the embedded MapLibre route shell in `feature:map` while extracting only clearly-owned content slices.
  - The current bottom-sheet content is not task-core UI; it is airspace/waypoint/class management, so Phase 3 must not assume `:feature:tasks` is the immediate owner for that slice.
  - Existing `:feature:tasks` file UI (`TaskFilesTab.kt`) is seam evidence only, not an automatic replacement.
  - `FlightDataMgmt.kt` and the surrounding legacy task/flight-data package prove that this phase must not turn into package-wide cleanup.
- Tests to add/update:
  - Task route compile validation
  - Map/task behavior tests if embedded map host logic changes
  - SLO validation if map interaction/runtime behavior changes
- Exit criteria:
  - The task route remains stable.
  - The route is decomposed enough that future owner moves can be done per slice instead of around the whole legacy file.
  - Status: Implemented 2026-03-13
  - Notes:
    - The legacy `Task.kt` file is now a thin compatibility wrapper in `com.example.ui1.screens`.
    - The real route body now lives in `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/TaskRouteScreen.kt`.
    - `TaskScreenUseCasesViewModel` moved to the modern navdrawer package.
    - `assembleDebug` passed after the move.

### Phase 4 - Legacy package severance and explicit imports

- Goal:
  - Narrow `AppNavGraph.kt` away from `com.example.ui1.screens.*`.
  - Resolve remaining shared-model or wrapper dependencies deliberately.
  - Make retained legacy blockers explicit instead of hiding them behind wildcard imports.
- Files to change:
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntimeRouteSubSheets.kt` if explicit imports there are also narrowed
  - `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenScaffold.kt` if `GeneralSettingsSheetHost` ownership changes by then
  - any scoped wrappers/models that still force the wildcard import
  - `feature/map/src/main/java/com/trust3/xcpro/screens/flightdata/FlightDataModels.kt` if shared model extraction is needed
- Phase 4 seam findings:
  - This phase is explicit-import narrowing and blocker accounting, not guaranteed full package elimination.
  - `SettingsDfRuntime.kt` remains a concrete blocker because `GeneralSettingsSheetHost` is still consumed by `MapScreenScaffold.kt`.
  - `Logbook.kt` is a likely follow-on app-shell candidate, but it is deliberately deferred to avoid scope creep in this program.
  - `DFNavboxes.kt`, `Files.kt`, `Logbook.kt`, `SettingsDfRuntime.kt`, and `Profiles.kt` remain the expected retained blockers unless earlier owner phases explicitly remove them.
- Tests to add/update:
  - Compile validation across touched modules.
- Exit criteria:
  - `AppNavGraph.kt` uses explicit owner imports.
  - The scoped screens no longer depend on the legacy wildcard surface.
  - Any retained legacy-package callsites are explicit and documented, not hidden behind wildcard imports.
  - Status: Implemented 2026-03-13
  - Notes:
    - `AppNavGraph.kt` no longer uses `import com.example.ui1.screens.*`.
    - The retained legacy blockers are now explicit imports or same-package references instead of a hidden wildcard surface.
    - `assembleDebug` passed after the move.
    - `enforceRules` still fails only on the pre-existing `MapScreenViewModel.kt` line-budget gate.
    - `testDebugUnitTest` still fails only on the pre-existing `GlideTargetRepositoryTest.kt`, `MapScreenViewModelTestRuntime.kt`, and `ProfileRepositoryTest.kt` blockers.

## 6) Test Plan

- Unit tests:
  - Route/content ownership changes do not need new domain math tests.
  - Add focused route/content smoke tests where practical.
- Replay/regression tests:
  - Not required unless Task phase changes runtime behavior.
- UI/instrumentation tests:
  - Optional for simple shell screens.
  - Relevant for Task route if embedded map behavior changes.
- Degraded/failure-mode tests:
  - Manage Account -> Edit Profile path should fail visibly only on real profile lookup failure, not route drift.
- Boundary tests for removed bypasses:
  - `AppNavGraph.kt` compile with explicit imports in later phase.

Required checks for non-trivial code phases:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Package-wide cleanup attempted too early | High churn across multiple modules | Keep package rename out of scope for this program | XCPro Team |
| Task route split drags MapLibre/runtime work into the same slice | High regression risk | Treat Task as its own phase with map-specific verification | XCPro Team |
| Shared legacy models block clean import severance | Medium | Extract models deliberately only if Phase 4 proves they still block explicit imports | XCPro Team |
| Manage Account move diverges from active profile work | Medium | Align with `Profiles_Workboard` and existing profile routes | XCPro Team |
| Support/About route move accidentally changes drawer/nav behavior | Low | Keep route names stable and use thin wrappers if needed | XCPro Team |

## 8) Acceptance Gates

- Long-term ownership for `AppNavGraph.kt`, `Support.kt`, `About.kt`, `ManageAccount.kt`, and `Task.kt` is explicit.
- No new reverse dependency from feature modules to `:app`.
- `feature:map` is no longer the long-term owner for generic app-shell pages.
- `Task.kt` is not moved as a blind file move; it is decomposed with explicit route/content ownership.
- No package-wide legacy rename is attempted in this program.
- `KNOWN_DEVIATIONS.md` is updated only if explicitly approved.

## 9) Rollback Plan

- What can be reverted independently:
  - Support/About app-shell move
  - Manage Account profile move
  - Task route decomposition
  - AppNavGraph explicit import narrowing
- Recovery steps if regression is detected:
  - Revert the last owner move only.
  - Keep compatibility wrappers in place until the replacement route/content path is proven.
  - Do not proceed to the next phase until the previous phase compiles and route behavior is stable.
