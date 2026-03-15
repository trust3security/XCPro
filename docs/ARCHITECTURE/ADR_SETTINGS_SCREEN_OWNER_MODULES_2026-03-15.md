# ADR_SETTINGS_SCREEN_OWNER_MODULES_2026-03-15

## Metadata

- Title: Settings route wrappers stay with their owner feature modules
- Date: 2026-03-15
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
- Supersedes:
  - none
- Superseded by:
  - none

## Context

- Problem:
  - After Phase 1A, `feature:map` no longer owned the General Settings host, but
    it still compiled duplicate settings-route wrappers for forecast, weather,
    and units.
  - After Phase 1B, thermalling still remained in `feature:map` even though its
    settings repository and defaults were already profile-owned.
  - Polar/glider settings were still split: profile owned the glider
    persistence, while `feature:map` still owned the settings screen, cards,
    settings-side ViewModel/use-case, and the glider DI binding.
  - Layout settings were still split: canonical `CardPreferences` already sat
    outside `feature:map`, while `feature:map` still owned the layout screen,
    settings-side ViewModel, and the wrapper use-case.
  - Colors/theme settings were still split: profile owned theme persistence,
    while `feature:map` still owned the colors screen, colors ViewModel, and
    the settings-side `ThemePreferencesUseCase`.
  - Those wrappers are not map-shell responsibilities. They are compatibility
    entrypoints for owner-module settings content and screen-local ViewModels.
- Why now:
  - Phase 1 of the `feature:map` right-sizing program explicitly targets true
    owner-module settings wrappers after the host extraction.
- Constraints:
  - Preserve existing routes and dual-entry behavior.
  - Keep `FlightMgmt`, task routes, diagnostics, and map-local settings
    surfaces out of this slice.
  - Minimize call-site churn by keeping public packages stable where practical.
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/ADR_GENERAL_SETTINGS_HOST_2026-03-15.md`

## Decision

Settings route wrappers stay with their owner feature modules, not in
`feature:map`.

Required implications:
- ownership/boundary choice:
  - `feature:forecast` owns `ForecastSettingsScreen`.
  - `feature:weather` owns `WeatherSettingsScreen`, `WeatherSettingsSheet`,
    `WeatherSettingsSubSheet`, and weather sheet behavior tests.
  - `feature:profile` owns `UnitsSettingsScreen` and
    `UnitsSettingsViewModel`.
  - `feature:profile` owns `ThermallingSettingsScreen`,
    `ThermallingSettingsSubSheet`, `ThermallingSettingsViewModel`,
    `ThermallingSettingsUseCase`, and thermalling settings tests/resources.
  - `feature:profile` owns `PolarSettingsScreen`, the polar cards,
    `GliderViewModel`, `GliderUseCase`, `PolarCalculator`,
    `GlidePolarMetricsResolver`, `StillAirSinkProvider`,
    `PolarStillAirSinkProvider`, and the glider DI bindings/tests.
  - `feature:profile` owns `LayoutScreen`, `LayoutViewModel`,
    `LayoutPreferencesUseCase`, and the layout settings tests.
  - `feature:profile` owns `ColorsScreen`, `ColorsViewModel`,
    `ColorsScreenComponents`, `ColorsScreenPickers`,
    `ThemePreferencesUseCase`, and colors settings tests.
  - `feature:profile` owns `HawkVarioSettingsScreen`,
    `HawkVarioSettingsUseCase`, `HawkVarioSettingsViewModel`, and HAWK
    settings tests.
  - `feature:map` keeps only `ThemeViewModel` / `Baseui1Theme` as the
    temporary app theme runtime read path.
  - `feature:variometer` owns the live HAWK runtime owner
    (`HawkVarioUseCase`) plus the read-only `HawkVarioPreviewReadPort`.
  - `feature:map` keeps only temporary HAWK sensor/source adapters while the
    parent flight-runtime extraction is still pending.
  - `feature:map` keeps only map-local settings wrappers that are still truly
    map-owned or explicitly deferred.
- dependency direction impact:
  - `app` depends directly on the owner feature modules for the routes it
    registers.
  - `feature:map` continues to depend on owner modules only where it composes
    their surfaces.
- API/module surface impact:
  - public package names remain stable for these wrappers to avoid route/import
    churn.
  - the physical owner module, not the package namespace, is the authoritative
    ownership signal.
- time-base/determinism impact:
  - none; this is settings/UI ownership only.
- concurrency/buffering/cadence impact:
  - none; no new runtime scope or background owner was introduced.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep duplicate wrappers in `feature:map` until all settings files can move together | smallest local code delta | preserves wrong ownership and keeps duplicate class drift risk |
| Move the wrappers into `app` with the General Settings host | app already owns the host | wrappers are still feature-owned entrypoints, not app-shell business/UI owners |
| Rename packages while moving wrappers | cleaner package semantics looked tempting | widens the slice with avoidable import and route churn |

## Consequences

### Benefits
- `feature:map` loses another clear non-map ownership bucket.
- Route wrappers, screen-local ViewModels, and behavior tests now live with
  their owner features.
- Thermalling settings UI now shares the same owner module as its repository
  defaults and persistence contract.
- Polar/glider settings UI, settings-side policy, and polar runtime provider
  now share the same owner module as the glider persistence contract.
- Layout settings UI now shares the same owner module as the broader
  profile-owned settings lane while still reading canonical `CardPreferences`.
- Colors settings UI and the settings-side theme write contract now share the
  same owner module as the canonical theme persistence owner.
- HAWK settings UI now shares the same owner module as the other
  profile-owned settings surfaces while the live HAWK runtime remains singular,
  explicit, and variometer-owned.
- App-shell route registration depends on the true owners directly.

### Costs
- `app`, `feature:forecast`, and `feature:weather` need direct dependency/test
  wiring updates.
- Remaining mixed-owner settings files in `feature:map` still require later
  seam passes.

### Risks
- Stable packages can still mislead future edits unless file path/module
  ownership is checked first.
- A future cleanup could accidentally reintroduce wrapper duplicates in
  `feature:map`.

## Validation

- Tests/evidence required:
  - weather sheet behavior tests moved to `feature:weather`
  - thermalling screen/viewmodel tests moved to `feature:profile`
  - glider/polar repository and math contract tests moved to `feature:profile`
  - layout viewmodel/content tests moved to `feature:profile`
  - colors viewmodel tests moved to `feature:profile`
  - map theme runtime read test kept in `feature:map`
  - HAWK settings use-case/viewmodel tests moved to `feature:profile`
  - HAWK UI-state contract test moved to `feature:variometer`
  - HAWK engine/repository tests moved to `feature:variometer`
  - thermalling General Settings tile policy remains app-owned and local-sheet only
  - polar General Settings tile policy remains app-owned and local-sheet only
  - layout General Settings tile policy remains app-owned and local-sheet only
  - colors route compile proof through `app`
  - route compile proof through `app`
  - standard AGENTS verification gates
- SLO or latency impact:
  - none expected
- Rollout/monitoring notes:
  - no staged rollout required; this is internal ownership cleanup

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update settings wrapper ownership paths for thermalling, polar, layout, and colors
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - direct app dependencies on `feature:forecast` / `feature:weather`
  - moved weather tests
  - wrapper deletions in `feature:map`
- What would trigger rollback:
  - route resolution regressions for forecast, weather, or units settings
- How this ADR is superseded or retired:
  - retire when the broader `feature:map` right-sizing program finishes and
    the remaining mixed-owner settings lane is fully resolved
