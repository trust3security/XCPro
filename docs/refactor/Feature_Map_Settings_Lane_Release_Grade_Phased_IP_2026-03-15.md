# Feature:Map Remaining Settings Lane Release-Grade Phased IP

## Purpose

Detailed execution contract for the remaining mixed-owner settings lane still
compiled in `feature:map` after Phase 1B of the broader right-sizing program.

This plan exists because the remaining lane is not one seam:

- thermalling settings are already backed by a `feature:profile` repository
- polar/glider settings are backed by profile-owned persistence with map-owned
  UI wrappers
- layout is a profile-scoped settings wrapper over `CardPreferences`
- colors is a profile-scoped settings surface, but it still has duplicated
  theme authority, duplicated profile export/import coverage, and a generic UI
  dependency that lives in `feature:map`
- HAWK settings still mix variometer preferences with map-owned live runtime

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/CONTRIBUTING.md`
6. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
7. `docs/ARCHITECTURE/AGENT.md`
8. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`
9. `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
10. `docs/ARCHITECTURE/ADR_SETTINGS_SCREEN_OWNER_MODULES_2026-03-15.md`
11. `docs/refactor/Feature_Map_Autonomous_Agent_Execution_Contract_2026-03-15.md`

## 0) Metadata

- Title: Remove the remaining mixed-owner settings lane from `feature:map`
- Owner: Codex
- Date: 2026-03-15
- Issue/PR: TBD
- Status: Draft
- Execution rules:
  - This is a boundary-removal track, not a line-budget cleanup exercise.
  - Follow phases in order.
  - Do not mix `FlightMgmt`, task routes, diagnostics, or map widget runtime
    into this lane.
  - Preserve dual-entry behavior:
    - direct app routes
    - app-shell General Settings sub-sheets
  - Reuse existing owner modules where the authoritative repository or
    persistence already lives.
  - HAWK moves must not invent a second live runtime owner.
- Progress note:
  - 2026-03-15: Phase 1A removed the General Settings host from `feature:map`.
  - 2026-03-15: Phase 1B removed forecast/weather/units wrapper ownership from
    `feature:map`.
  - 2026-03-15: this detailed IP was created for the remaining settings lane:
    thermalling, HAWK, polar, layout, and colors.
  - 2026-03-15: Phase 1 landed. Thermalling settings screen/use-case/viewmodel,
    the General Settings sub-sheet entrypoint, thermalling resources, and
    thermalling tests now live in `feature:profile`; `feature:map` keeps only
    live thermalling runtime automation.
  - 2026-03-15: Phase 2 landed. Polar settings screen/cards, glider
    settings-side ViewModel/use-case, glider DI bindings, polar runtime
    provider, and glider/polar contract tests now live in `feature:profile`;
    `feature:map` keeps only runtime consumers of those APIs.
  - 2026-03-15: Phase 3 seam lock found that layout and colors are not one
    symmetric move:
    - layout is a clean `CardPreferences` settings wrapper move
    - colors is launched from the profile-owned `LookAndFeelScreen` and shares
      theme state with the map/app theme runtime, so it needs an explicit
      settings-owner vs runtime-read split
  - 2026-03-15: Phase 3A landed. Layout settings screen/use-case/viewmodel and
    layout owner-module tests now live in `feature:profile`; `feature:map`
    keeps only map widget layout runtime ownership.
  - 2026-03-15: Phase 3B seam/code pass found additional colors-lane debt that
    must be fixed before the owner move:
    - `LookAndFeelPreferences` and `ThemePreferencesRepository` both persist
      `profile_<id>_color_theme`
    - `LookAndFeelProfileSettingsContributor` and
      `ThemeProfileSettingsContributor` both capture/apply the same theme ID
    - `ModernColorPicker` is generic UI but still lives in `feature:map`
    - `Baseui1Theme` / `ThemeViewModel` are app-wide theme runtime readers that
      still live physically in `feature:map`
  - 2026-03-15: Phase 3B.1 landed. `LookAndFeelPreferences` no longer owns the
    theme ID, `LookAndFeelProfileSettingsContributor` no longer snapshots or
    restores theme payload, and profile snapshot/restore tests now prove theme
    is captured and applied only through `ThemePreferencesRepository` /
    `ThemeProfileSettingsContributor`.
  - 2026-03-15: Phase 3B.2 landed. The generic `ModernColorPicker` and helper
    files moved from `feature:map` to `core:ui`, so the remaining colors owner
    move no longer depends on map-owned generic UI.
  - 2026-03-15: Phase 3B.3 landed. `ColorsScreen`, `ColorsViewModel`,
    `ColorsScreenComponents`, `ColorsScreenPickers`, and
    `ThemePreferencesUseCase` moved to `feature:profile`; `feature:map` keeps
    only `ThemeViewModel` / `Baseui1Theme` as the temporary runtime read path.
  - 2026-03-15: Phase 4 landed. `HawkVarioSettingsScreen`,
    `HawkVarioSettingsUseCase`, and `HawkVarioSettingsViewModel` moved to
    `feature:profile`; `HawkVarioUiState`, `HawkConfidence`, and
    `HawkVarioPreviewReadPort` now live in `feature:variometer`; Parent
    Phase 2A then moved the live HAWK runtime owner there as well, so
    `feature:map` now keeps only temporary sensor/source adapters.
  - 2026-03-15: Phase 5 landed. Drift guards now cover HAWK settings wrappers,
    and the remaining mixed-owner settings lane is eliminated.

## 1) Scope

- Problem statement:
  - `feature:map` still compiles a mixed settings lane under
    `screens/navdrawer/**` that no longer fits one owner.
  - The remaining lane is architecturally split:
    - thermalling settings already persist through
      `feature/profile/src/main/java/com/trust3/xcpro/thermalling/ThermallingModePreferencesRepository.kt`
    - polar/glider configuration persists through profile-owned glider
      repositories while UI/ViewModel stay in `feature:map`
    - colors now persist and render through `feature:profile`, while
      `feature:map` keeps only the temporary `ThemeViewModel` /
      `Baseui1Theme` runtime read path
    - layout preferences and the layout settings screen/use-case are now
      profile-owned
    - HAWK settings combine `feature:variometer` preferences with
      `feature:map` live runtime state
- Why now:
  - The easy owner-wrapper cuts are done.
  - The remaining lane needs a seam-accurate plan or it will regress into ad
    hoc churn.
- In scope:
  - the remaining settings lane:
    - HAWK
    - documentation of the temporary theme runtime read seam left in
      `feature:map`
  - route and General Settings sub-sheet wiring required by those moves
  - owner-module test migration for the moved settings surfaces
- Out of scope:
  - `FlightMgmt`
  - map widget drag/resize runtime (`MapWidgetLayoutViewModel`)
  - task routes
  - diagnostics
  - sensor/runtime extraction already planned under later phases
- User-visible impact:
  - no intended behavior change
  - route and sheet entry behavior must stay stable
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Thermalling mode settings | `feature:profile` thermalling repository | settings flows + owner settings screen/viewmodel | map-owned persistence or second settings repository |
| Thermalling runtime mode state | current map thermalling runtime controller until flight-runtime phase | runtime port only | screen/viewmodel copies of runtime state |
| Glider/polar configuration | profile-owned glider repository | owner use-case/viewmodel/screen | second glider settings owner in `feature:map` |
| Card layout settings (`cardsAcrossPortrait`, `anchorPortrait`) | `dfcards-library` `CardPreferences` | owner use-case/viewmodel/screen | duplicate layout preference coordinators in `feature:map` |
| Theme/color settings | `feature:profile` `ThemePreferencesRepository` after Phase 3B authority cleanup | owner settings use-case/viewmodel/screen + explicit theme runtime read contract | `LookAndFeelPreferences` color-theme persistence, map-owned settings facades, duplicate profile export/import sections |
| HAWK settings preferences | `feature:variometer` preferences repository | owner settings use-case/viewmodel/screen | duplicated HAWK settings persistence in `feature:map` |
| HAWK live runtime state | `feature:variometer` HAWK runtime owner, fed temporarily by map-backed sensor/source adapters until Parent Phase 2B | runtime read port only | settings screen becoming a second live runtime owner |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Thermalling settings UI state | owner screen/viewmodel after move to `feature:profile` | owner screen intents only | owner screen + app routes/sub-sheets | `ThermallingModePreferencesRepository` | `feature:profile` thermalling repo | repository defaults or profile cleanup rules | none/user settings only | moved viewmodel + sheet tests |
| Polar/glider settings UI state | owner screen/viewmodel after move to `feature:profile` | owner screen intents only | owner screen + app routes/sub-sheets | profile glider repository | profile glider repository | profile reset/restore rules | none/user settings only | moved screen/viewmodel tests |
| Layout settings UI state | owner screen/viewmodel after move to `feature:profile` or dedicated owner shell | owner screen intents only | owner screen + app routes/sub-sheets | `CardPreferences` | `CardPreferences` | profile cleanup / default layout rules | none/user settings only | moved screen/viewmodel tests |
| Colors settings UI state | owner screen/viewmodel after move to `feature:profile` | owner screen intents only | owner screen + app routes | `ThemePreferencesRepository` | `feature:profile` theme repo | profile cleanup rules | none/user settings only | moved screen/viewmodel tests |
| App theme runtime read state | one explicit runtime read contract after Phase 3B | runtime read path only; no settings writes | `ThemeViewModel` / `Baseui1Theme` and any later replacement owner | `ThemePreferencesRepository` | `feature:profile` theme repo | profile cleanup rules | none/user settings only | runtime observation tests + route smoke |
| HAWK settings UI state | owner screen/viewmodel after move to `feature:profile` | owner screen intents only | owner screen + app routes/sub-sheets | HAWK settings preferences + HAWK runtime read port + units read port | `feature:variometer` prefs repo | existing preference reset rules | mixed: prefs none, runtime monotonic/replay unchanged | boundary tests + moved screen/viewmodel tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:map`
  - `feature:profile`
  - `feature:variometer`
  - `dfcards-library`
  - `app`
  - possibly `feature:flight-runtime` only if the HAWK runtime seam requires it
- Boundary risks:
  - moving screen wrappers without moving their owner use-cases/viewmodels
  - mixing settings persistence moves with map widget runtime
  - creating a second HAWK runtime owner while moving the HAWK settings screen

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `docs/ARCHITECTURE/ADR_SETTINGS_SCREEN_OWNER_MODULES_2026-03-15.md` | same owner-wrapper correction category | keep owner-module wrapper ownership and direct app dependency model | this lane has harder mixed-owner seams than forecast/weather/units |
| `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md` | parent execution contract for the same program | keep release-grade phase gates and owner-first sequencing | this doc splits Phase 1 into lane-specific sub-phases |
| `docs/ARCHITECTURE/ADR_GENERAL_SETTINGS_HOST_2026-03-15.md` | preserves dual-entry settings behavior while owner moves | keep app-hosted route/sub-sheet contract | this lane moves owner screens, not the host itself |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Thermalling settings screen/use-case/viewmodel/sub-sheet | `feature:map` | `feature:profile` | the repository and settings policy are already profile-owned | moved tests + route/sub-sheet smoke |
| Glider/polar settings screen/cards/viewmodel/use-case | `feature:map` | `feature:profile` | profile already owns the authoritative glider persistence | moved tests + route/sub-sheet smoke |
| Layout settings screen/use-case/viewmodel | `feature:map` | profile-owned settings shell | settings are `CardPreferences`, not map shell state | moved tests + route smoke |
| Colors/theme settings screen/use-case/viewmodel | `feature:map` | `feature:profile` | theme persistence is already profile-owned and the colors surface belongs with the profile-owned Look and Feel lane | moved tests + route smoke |
| Theme ID persistence and profile export/import ownership | split between `LookAndFeelPreferences` and `ThemePreferencesRepository` plus two contributors | `ThemePreferencesRepository` and `ThemeProfileSettingsContributor` only | Phase 3B must remove duplicate authority before moving the colors UI | boundary tests + snapshot/restore proof |
| Generic color-picker UI utility | `feature:map` | non-map owner such as `core:ui` or the final colors owner module | the colors owner move must not leave a production dependency back into `feature:map` for generic UI | compile proof + moved tests |
| HAWK settings screen/use-case/viewmodel | `feature:map` | `feature:profile` | the repo already keeps vario/settings UI in the profile-owned settings lane | boundary tests + route/sub-sheet smoke |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt` thermalling/polar/layout/colors/HAWK routes | app routes still resolve to map-owned settings screens | direct dependencies on the true owner modules | Phases 1-4 |
| `app/src/main/java/com/trust3/xcpro/appshell/settings/GeneralSettingsRouteSubSheets.kt` | app sub-sheets wrap map-owned settings screens | app sub-sheets wrap owner-module settings screens | Phases 1-4 |
| `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt` -> `"colors"` | profile-owned Look & Feel flow still navigates to a map-owned colors route | keep the route stable, but resolve it to the profile-owned colors screen | Phase 3 |
| `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/**` | owner settings use-cases/viewmodels/screens compiled in map shell | move to owner module or delete after migration | Phases 1-4 |
| `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/LookAndFeelPreferences.kt` color-theme methods | look-and-feel persistence duplicates theme authority | remove or delegate color-theme ownership to `ThemePreferencesRepository` | Phase 3B |
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/LookAndFeelProfileSettingsContributor.kt` theme payload | look-and-feel export/import duplicates theme section payload | keep theme export/import in `ThemeProfileSettingsContributor` only | Phase 3B |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Feature_Map_Settings_Lane_Release_Grade_Phased_IP_2026-03-15.md` | New | active execution contract for the remaining settings lane | the lane now needs its own seam-accurate plan | too detailed for the parent IP | No |
| `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ThermallingSettings*.kt` | Existing | current thermalling settings lane to remove from map | it is the cleanest remaining move because persistence is already profile-owned | not a map-runtime concern | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Polar*.kt` | Existing | current polar/glider settings lane to remove from map | it is isolated around glider settings | not a generic map-shell concern | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/glider/GliderUseCase.kt` | Existing | current glider owner facade to relocate | only used by polar cards/settings | belongs with profile-owned glider persistence | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Layout*.kt` | Existing | current card-layout settings lane to remove from map | layout settings are profile/card-preference concerns | keep map widget runtime separate | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Colors*.kt` | Existing | current theme/color settings lane to remove from map | theme persistence already lives in profile | not a map-shell concern | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/ui/theme/ThemePreferencesUseCase.kt` | Existing | current theme settings facade to relocate | only wraps profile-owned persistence | belongs near its repository owner | Yes |
| `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/LookAndFeelPreferences.kt` | Existing | current look-and-feel persistence helper that still duplicates theme ID ownership | Phase 3B must narrow it back to look-and-feel-only state | theme persistence has a canonical repository already | Yes |
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/LookAndFeelProfileSettingsContributor.kt` and `ThemeProfileSettingsContributor.kt` | Existing | current profile export/import seam for look-and-feel and theme payloads | Phase 3B must remove duplicate theme capture/apply ownership | one section should not duplicate another section's authority | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/ui/components/ColorPicker*.kt` | Existing | generic UI dependency currently compiled in `feature:map` | colors cannot move cleanly while it depends on map-owned generic UI | not a map-shell concern | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/ui/theme/Theme.kt` and `ThemeViewModel.kt` | Existing | app theme runtime read path that must stay explicit during the colors move | Phase 3B must preserve runtime observation without leaving hidden settings writes here | do not let the colors UI move silently break the app theme runtime | Yes |
| `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/HawkVarioSettings*.kt` | Existing | current mixed-owner HAWK settings lane | needs dedicated seam extraction before moving | cannot stay in map long term | Yes |
| `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt` | Existing | route registrar for direct settings entry routes | app owns route registration | not owner of settings business/UI logic | No |
| `app/src/main/java/com/trust3/xcpro/appshell/settings/GeneralSettingsRouteSubSheets.kt` | Existing | app-owned sub-sheet wrappers | app host already owns General Settings composition | remains the composition root | No |
| `app/src/main/java/com/trust3/xcpro/appshell/settings/GeneralSettingsSubSheetContent.kt` | Existing | app-owned sub-sheet selection switchboard | same as above | do not reintroduce host logic in `feature:map` | No |
| `scripts/ci/enforce_rules.ps1` | Existing | drift guards after each owner move | enforce regression protection | required for release-grade closure | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| Owner-module settings screens for thermalling/polar/layout/colors | owner feature module | `app` routes + app sub-sheets | public | lets app compose the true owner surfaces directly | keep stable packages during migration where practical |
| HAWK runtime read port | `feature:variometer` | HAWK settings screen/viewmodel, possible map shell consumers | public cross-module | separates live HAWK runtime from the settings screen move | remove temporary map-backed shim once runtime owner is finalized |
| Theme settings write use-case | `feature:profile` after move | colors screen/viewmodel | public | aligns settings writes with the existing profile-owned repository | replace the map-owned settings facade in Phase 3 |
| Theme runtime read contract | explicit read-only contract after Phase 3B | `ThemeViewModel`, `Baseui1Theme`, other theme consumers | public | colors owner move must not orphan app/theme runtime reads | keep a temporary runtime reader only until the app-wide theme owner is simplified later |
| Generic color-picker UI contract | approved non-map owner after Phase 3B | colors screen/components | public or internal to the owner module | prevents a production dependency from `feature:profile` back into `feature:map` for generic UI | delete the old map-owned picker after move |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| Existing screen `ViewModel` scopes only | this lane moves settings UI owners, not runtime cadence | unchanged | unchanged | no new long-lived scope is needed for the plan |
| Existing HAWK runtime cadence only | HAWK live preview already depends on runtime cadence | unchanged | unchanged | settings screen must consume a read port, not own runtime cadence |

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| app route/sub-sheet wrappers still targeting map-owned settings screens | `app` temporarily | preserve dual-entry behavior during migration | owner-module screens | remove per phase when the owner screen is moved | route + sub-sheet smoke |
| HAWK sensor/source adapters backed by current map owners | `feature:map` temporarily | feeds the variometer-owned runtime until Parent Phase 2B moves the upstream owners | final flight-runtime-facing source ports | remove when Parent Phase 2B lands | HAWK boundary tests |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| Thermalling clamps/defaults | `feature/profile/src/main/java/com/trust3/xcpro/thermalling/ThermallingModePreferencesRepository.kt` and its thermalling constants package | thermalling settings UI + runtime | persistence and defaults already live there | No |
| Glider/polar config policy | profile-owned glider repository/common glider model files | polar cards/settings | persistence owner already canonical | No |
| Theme/color preference policy | `feature/profile/src/main/java/com/trust3/xcpro/ui/theme/ThemePreferencesRepository.kt` | colors screen + theme consumers | persistence owner already canonical | No |
| Look and Feel theme quick-selection policy | `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt` plus the canonical theme repository | look-and-feel sheet + colors route | profile-owned settings lane already canonical for quick theme selection | No second persistence owner |
| Card layout preference policy | `dfcards-library/src/main/java/com/example/dfcards/dfcards/CardPreferences.kt` | layout settings screen + card runtime | canonical card preference owner already exists | No |
| HAWK confidence/runtime mapping | `feature/variometer/src/main/java/com/trust3/xcpro/hawk/HawkVarioUseCase.kt` | HAWK settings preview + map/runtime consumers | live HAWK runtime is centralized in the variometer owner | No second runtime owner |

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| HAWK live preview timing | Monotonic / replay unchanged through the runtime port | screen move must not change HAWK runtime cadence |
| Thermalling/polar/layout/colors settings | none/user settings only | settings flows are not time-driven |

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged by default
- Primary cadence/gating sensor:
  - only HAWK live runtime is cadence-sensitive in this lane
- Hot-path latency budget:
  - HAWK port extraction must not add heavy work on the main thread

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - thermalling/polar/layout/colors settings moves should not affect replay
  - HAWK runtime port extraction must preserve current replay/live behavior

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| route resolves to missing owner screen after move | Terminal during build/test | app route registrar + owner module | screen unavailable | block phase exit | compile + route smoke |
| General Settings sub-sheet points at stale map wrapper | Terminal during build/test | app host | sub-sheet unavailable | block phase exit | sub-sheet smoke |
| HAWK screen moves before runtime read port is stable | Degraded / Terminal | HAWK seam owner | live preview missing or stale | do not ship; block phase exit | HAWK boundary tests |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| thermalling/polar/layout/colors wrappers drift back into `feature:map` | module boundary + owner-module wrapper rule | enforceRules + review | new Phase 1C guards in `scripts/ci/enforce_rules.ps1` |
| HAWK screen moves without resolving the mixed runtime owner | SSOT + no duplicate runtime owner | review + boundary tests | HAWK seam tests |
| map widget runtime gets mixed into layout settings move | scope ownership + out-of-scope guard | review + plan stop rule | this plan + file list |

## 3) Data Flow (Before -> After)

Current:

```text
app route / app General Settings sheet
  -> feature:map settings screen
  -> map-owned ViewModel/use-case
  -> owner repository in a mix of profile / variometer / dfcards / map
```

Target:

```text
app route / app General Settings sheet
  -> owner feature settings screen
  -> owner ViewModel/use-case
  -> canonical repository / runtime port
```

Special HAWK target:

```text
app route / app General Settings sheet
  -> owner HAWK settings screen
  -> owner ViewModel/use-case
  -> HAWK settings preferences owner + HAWK runtime read port
```

## 4) Implementation Phases

### Phase 0 - Lane Lock And Test Inventory

- Goal:
  - lock exact seams and stop accidental widening of scope
- Files to change:
  - planning docs only
- Ownership/file split changes in this phase:
  - none in production code
- Tests to add/update:
  - inventory only
- Exit criteria:
  - thermalling, polar/glider, layout/colors, and HAWK are explicitly treated
    as separate sub-lanes
  - existing tests and gaps are recorded before code changes

### Phase 1 - Thermalling Settings Owner Move

- Goal:
  - move thermalling settings UI ownership from `feature:map` to
    `feature:profile`
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/ThermallingSettings*.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/SettingsDfRuntimeSheets.kt`
  - owner destination under `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/**`
  - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
  - `app/src/main/java/com/trust3/xcpro/appshell/settings/**`
- Ownership/file split changes in this phase:
  - `feature:profile` owns thermalling settings screen, use-case, viewmodel,
    and sub-sheet wrapper
  - `feature:map` keeps only the runtime controller path used by map behavior
- Tests to add/update:
  - move `ThermallingSettingsViewModelTest`
  - move `ThermallingSettingsContentTest`
  - add route and sub-sheet smoke
- Exit criteria:
  - `feature:map` no longer owns thermalling settings UI/viewmodel/use-case
  - app routes and General Settings sub-sheet both resolve to the profile-owned
    screen
- Landed result:
  - `feature:profile` now owns thermalling settings screen, use-case,
    viewmodel, sub-sheet, resources, and moved tests
  - `feature:map` keeps only the runtime controller path used by map behavior
  - app tile policy still opens the local thermalling sub-sheet instead of
    navigating away from the General Settings host

### Phase 2 - Polar / Glider Owner Move

- Goal:
  - move glider/polar settings ownership to `feature:profile`
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/glider/GliderUseCase.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/glider/GliderViewModel.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Polar*.kt`
  - owner destination under `feature/profile/src/main/java/com/trust3/xcpro/**`
  - `app` route/sub-sheet registrars
- Ownership/file split changes in this phase:
  - `feature:profile` owns glider settings use-case/viewmodel and polar cards
  - `feature:map` stops owning glider settings UI entirely
- Tests to add/update:
  - add glider/polar boundary tests if missing
  - add route/sub-sheet smoke
- Exit criteria:
  - `feature:map` no longer owns glider settings viewmodels/use-cases/cards
  - polar route and sub-sheet both resolve to profile-owned screens
- Landed result:
  - `feature:profile` now owns `PolarSettingsScreen`, the polar cards,
    `GliderViewModel`, `GliderUseCase`, `PolarCalculator`,
    `GlidePolarMetricsResolver`, `StillAirSinkProvider`, and the glider DI bindings
  - glider/polar repository and math contract tests now live in `feature:profile`
  - `feature:map` consumes the profile-owned glider APIs and no longer owns the
    glider settings lane

### Phase 3 - Layout And Colors Owner Move

- Goal:
  - move the remaining profile-scoped layout and theme/color settings out of
    `feature:map`
  - execute the phase in this order:
    - Phase 3A: layout
    - Phase 3B: remove Look & Feel/theme duplicates first, then move colors
      with the theme-runtime read split
- Required seam lock before editing:
  - layout:
    - keep `MapWidgetLayoutViewModel`, `MapWidgetLayoutUseCase`,
      `MapWidgetLayoutRepository`, and map widget drag/resize runtime out of
      scope
    - keep the move limited to the `CardPreferences` settings wrapper path
  - colors:
    - keep the `LookAndFeelScreen -> ColorThemeSheet -> "colors"` navigation
      contract stable while changing the route owner
    - do not assume `ThemePreferencesUseCase` is colors-only:
      `feature:map/src/main/java/com/trust3/xcpro/ui/theme/ThemeViewModel.kt`
      and `feature:map/src/main/java/com/trust3/xcpro/ui/theme/Theme.kt` still
      use the same contract for app/theme runtime reads
    - treat duplicate Look & Feel/theme ownership as the first blocker, not an
      incidental cleanup item:
      `LookAndFeelPreferences` must stop owning `colorThemeId`, and
      `LookAndFeelProfileSettingsContributor` must stop duplicating the theme
      export/import payload already owned by `ThemeProfileSettingsContributor`
    - collapse duplicate theme authority before moving the screen:
      `LookAndFeelPreferences` must stop owning `colorThemeId`, and
      `LookAndFeelProfileSettingsContributor` must stop duplicating theme
      export/import payload already owned by `ThemeProfileSettingsContributor`
    - re-home or replace `feature:map`'s `ModernColorPicker` before the colors
      screen moves, so the new owner does not keep a production dependency back
      into `feature:map`
    - split settings-owner writes from theme-runtime reads instead of creating
      a hidden compatibility owner
    - explicitly treat the app-wide `Baseui1Theme` owner problem as follow-on
      debt; Phase 3B must preserve runtime reads, not pretend that app theme
      ownership is finished
- Files to change:
  - Phase 3A layout:
    - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Layout*.kt`
    - owner destination under
      `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/**`
    - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
    - `app/src/main/java/com/trust3/xcpro/appshell/settings/GeneralSettingsRouteSubSheets.kt`
  - Phase 3B colors:
    - first remove duplicate Look & Feel/theme authority and duplicate profile
      export/import payload
    - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/Colors*.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/ui/theme/ThemePreferencesUseCase.kt`
    - `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/LookAndFeelPreferences.kt`
    - `feature/profile/src/main/java/com/trust3/xcpro/profiles/LookAndFeelProfileSettingsContributor.kt`
    - `feature/profile/src/main/java/com/trust3/xcpro/profiles/ThemeProfileSettingsContributor.kt`
    - `feature/map/src/main/java/com/trust3/xcpro/ui/components/ColorPicker*.kt`
    - if needed, a new or renamed read-only theme contract used by
      `ThemeViewModel`
    - owner destination under
      `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/**`
      and/or `feature/profile/src/main/java/com/trust3/xcpro/ui/theme/**`
    - `feature/profile/src/main/java/com/trust3/xcpro/screens/navdrawer/lookandfeel/LookAndFeelScreen.kt`
    - `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt`
- Ownership/file split changes in this phase:
  - Phase 3A:
    - `feature:profile` owns layout settings UI/use-case/viewmodel
    - `feature:map` keeps only map widget runtime layout owner(s)
  - Phase 3B:
    - `feature:profile` owns colors/theme settings UI/viewmodel/components and
      the settings-side write contract
    - `ThemePreferencesRepository` is the only persistence owner for theme ID
      and custom colors
    - profile export/import keeps theme payload in one section only
    - `feature:map` keeps only the temporary app/theme runtime read path until
      that runtime is simplified later
- Tests to add/update:
  - Phase 3A:
    - add layout viewmodel/content tests in the owner module
    - add route and General Settings sub-sheet smoke for layouts
  - Phase 3B:
    - add colors viewmodel/content/route tests in the owner module
    - add proof that `LookAndFeelScreen` still reaches `"colors"`
    - add boundary proof that the app/theme runtime read path still observes
      theme and custom color updates
    - add snapshot/restore proof that theme ID is captured/applied only once
      after the authority cleanup
    - add compile proof that the moved colors owner does not depend on
      `feature:map` for generic color-picker UI
- Exit criteria:
  - after Phase 3A:
    - `feature:map` no longer owns layout settings UI/use-case/viewmodel
    - layout route and General Settings sub-sheet resolve to the owner module
  - after Phase 3B:
    - `feature:map` no longer owns colors settings UI/viewmodel/components
    - theme settings writes are owner-owned
    - `ThemePreferencesRepository` is the only theme persistence owner
    - profile snapshot/restore has one canonical theme section
    - one explicit theme runtime read path still exists for `ThemeViewModel` /
      `Baseui1Theme`
    - `LookAndFeelScreen -> colors` still resolves correctly
- Landed result:
  - Phase 3A layout landed:
    - `feature:profile` now owns `LayoutScreen`, `LayoutViewModel`,
      `LayoutPreferencesUseCase`, and layout content/viewmodel tests
    - `feature:map` no longer owns the layout settings wrapper path
    - app route and General Settings sub-sheet imports stayed stable because
      the public package/class names were preserved while the physical owner
      module changed
    - `feature:map` keeps `MapWidgetLayoutViewModel`,
      `MapWidgetLayoutUseCase`, and `MapWidgetLayoutRepository` as explicit
      out-of-scope runtime owners
  - Phase 3B.1 duplicate-theme cleanup landed:
    - `LookAndFeelPreferences` now owns only status-bar and card-style
      persistence
    - `LookAndFeelUseCase` routes theme reads/writes through the canonical
      `ThemePreferencesRepository`
    - `LookAndFeelProfileSettingsContributor` now snapshots/restores only
      look-and-feel payload, while `ThemeProfileSettingsContributor` remains
      the sole theme snapshot/restore owner
    - profile snapshot/restore tests now prove theme payload is emitted and
      applied once
  - Phase 3B.2 generic picker move landed:
    - `ModernColorPicker`, `ColorPickerDrawing`, `ColorPickerInputs`, and
      `ColorPickerComponents` now live in `core:ui`
    - `feature:map` no longer owns generic color-picker UI code
    - the remaining colors owner move can now target `feature:profile`
      without a production dependency back into `feature:map`
  - Phase 3B.3 colors owner move landed:
    - `feature:profile` now owns `ColorsScreen`, `ColorsViewModel`,
      `ColorsScreenComponents`, `ColorsScreenPickers`, and
      `ThemePreferencesUseCase`
    - `feature:map` no longer owns the colors settings UI or settings-side
      write contract
    - `ThemeViewModel` / `Baseui1Theme` remain the explicit temporary runtime
      read path until the app-wide theme owner is simplified later
    - boundary tests now prove both the owner screen state path and the map
      theme runtime read path

### Phase 4 - HAWK Settings Runtime Split And Owner Move

- Goal:
  - separate HAWK settings screen ownership from HAWK live runtime ownership
- Required seam lock before editing:
  - identify the final owner of HAWK settings UI
  - extract one runtime read port for live HAWK data
  - forbid a second live HAWK runtime owner
- Files to change:
  - `feature/map/src/main/java/com/trust3/xcpro/screens/navdrawer/HawkVarioSettings*.kt`
  - `feature/map/src/main/java/com/trust3/xcpro/hawk/HawkVarioUseCase.kt` or a new port file
  - owner destination under `feature/variometer/**` or approved module
  - `app` route/sub-sheet registrars
- Ownership/file split changes in this phase:
  - settings UI moves to the approved owner module
  - live HAWK runtime remains one canonical owner behind a read port
  - map shell stops owning the HAWK settings screen
- Tests to add/update:
  - HAWK boundary tests for the runtime read port
  - moved HAWK screen/viewmodel tests
  - route/sub-sheet smoke
- Exit criteria:
  - `feature:map` no longer owns HAWK settings UI
  - one explicit HAWK runtime owner remains
- Landed result:
  - `feature:profile` now owns `HawkVarioSettingsScreen`,
    `HawkVarioSettingsUseCase`, `HawkVarioSettingsViewModel`, and HAWK
    settings tests
  - `feature:variometer` now owns the cross-module HAWK preview contract:
    `HawkVarioPreviewReadPort`, `HawkVarioUiState`, and `HawkConfidence`
  - Parent Phase 2A then moved the live HAWK runtime owner to
    `feature:variometer`; `feature:map` now keeps only temporary sensor/source
    adapters and their DI binding
  - the moved HAWK screen now sets the active profile explicitly for units
    instead of relying on ambient map-session state

### Phase 5 - Hardening And Drift Guards

- Goal:
  - close the settings lane cleanly and prevent regression
- Files to change:
  - `scripts/ci/enforce_rules.ps1`
  - parent right-sizing docs if the lane is complete
- Ownership/file split changes in this phase:
  - add explicit guards for thermalling/polar/layout/colors/HAWK wrappers
  - reduce the remaining settings lane in `feature:map` to only explicitly
    deferred surfaces, if any
- Tests to add/update:
  - final route/sub-sheet smoke
  - any remaining owner-boundary tests
- Exit criteria:
  - the remaining mixed-owner settings lane is eliminated or explicitly
    documented as deferred with reason
  - `feature:map` no longer owns these five settings categories
- Landed result:
  - `scripts/ci/enforce_rules.ps1` now rejects HAWK settings wrappers drifting
    back into `feature:map`
  - the settings lane is complete; the next active program phase is the parent
    Phase 2 flight-runtime extraction

## 5) Test Plan

- Unit tests:
  - thermalling viewmodel/content tests moved with the thermalling lane
  - new glider/polar tests where coverage is missing
  - new layout/colors viewmodel/boundary tests where coverage is missing
  - HAWK boundary tests for the runtime read port
- Replay/regression tests:
  - HAWK runtime regression only if the live runtime seam changes
- UI/instrumentation tests:
  - route smoke for thermalling, polar, layout, colors, and HAWK
  - General Settings sub-sheet smoke for each moved surface that still uses a
    sub-sheet entry
  - Look & Feel child-navigation smoke for colors
- Degraded/failure-mode tests:
  - missing owner route/sub-sheet resolution fails fast
  - HAWK screen without runtime port is blocked before ship
- Boundary tests for removed bypasses:
  - app routes no longer resolve through map-owned settings screens
  - map shell no longer compiles the moved settings viewmodels/use-cases
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Owner move / bypass removal | Boundary tests + compile proof | route/sub-sheet smoke + moved tests |
| Settings persistence / restore | Round-trip or restore proof where behavior changes | existing owner tests + new moved tests |
| Runtime read-port extraction | Boundary tests + deterministic runtime proof | HAWK seam tests |
| UI interaction / lifecycle | UI or instrumentation coverage | route and sub-sheet smoke |

Required checks for implementation phases:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| thermalling move accidentally pulls runtime controller out of map in the same slice | Medium | keep runtime controller explicitly out of scope for Phase 1 | Codex |
| polar move misses the glider use-case/viewmodel seam and only relocates surface files | High | move `GliderUseCase` and `GliderViewModel` in the same slice | Codex |
| layout move accidentally mixes in map widget runtime layout | High | treat `MapWidgetLayoutViewModel`, `MapWidgetLayoutUseCase`, and `MapWidgetLayoutRepository` as explicitly out of scope | Codex |
| colors move treats `ThemePreferencesUseCase` as colors-only and breaks `ThemeViewModel` / `Baseui1Theme` | High | split settings-owner writes from theme-runtime reads and prove runtime observation still works | Codex |
| colors move leaves `LookAndFeelPreferences` and `ThemePreferencesRepository` as duplicate owners of `colorThemeId` | High | collapse persistence authority into `ThemePreferencesRepository` before moving the screen | Codex |
| colors move leaves duplicated profile export/import payload across look-and-feel and theme sections | High | remove theme payload from `LookAndFeelProfileSettingsContributor` and add snapshot/restore proof | Codex |
| colors owner move keeps a production dependency on `feature:map` because `ModernColorPicker` is still map-owned | Medium | re-home or replace the generic picker in the same slice and add compile proof | Codex |
| HAWK move creates a second runtime owner | Closed | resolved by the variometer-owned preview read port and the map-owned single runtime implementation | Codex |
| app General Settings sub-sheets drift from direct routes | High | update route and sub-sheet entrypoints in the same slice each time | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: satisfied for the HAWK runtime preview boundary
- ADR file:
  - `docs/ARCHITECTURE/ADR_SETTINGS_SCREEN_OWNER_MODULES_2026-03-15.md`
  - `docs/ARCHITECTURE/ADR_HAWK_VARIO_PREVIEW_READ_PORT_2026-03-15.md`
- Decision summary:
  - the remaining settings lane must be decomposed into separate owner moves,
    not one bulk cleanup
- Why this belongs in plan notes instead of a new ADR:
  - this document is sequencing and seam control, not yet a new durable module
    boundary decision

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` or `CODING_RULES.md`
- Thermalling, polar, layout, colors, and HAWK are not treated as one bulk move
- No duplicate SSOT ownership introduced
- App routes and app sub-sheets remain aligned for every moved surface
- HAWK retains one explicit live runtime owner
- `feature:map` loses a real owner boundary in every phase
- `KNOWN_DEVIATIONS.md` changes only if explicitly approved and required

## 8) Rollback Plan

- What can be reverted independently:
  - each lane phase independently
- Recovery steps if regression is detected:
  - revert the current lane phase only
  - keep previously validated owner moves intact
  - rerun the required checks

## 9) Recommendation

The executed order was:

1. Thermalling
2. Polar / glider
3. Layout
4. Colors after duplicate-theme cleanup and a theme-runtime read split
5. HAWK last, after a dedicated runtime seam lock

That order matched ownership reality:

- thermalling and polar already persist outside `feature:map`
- layout is the remaining clean `CardPreferences` wrapper move
- colors is now profile-owned, while the map theme runtime keeps one explicit
  temporary read seam until later cleanup
- HAWK was the last category that still mixed settings ownership with a live
  runtime seam, and it is now closed
