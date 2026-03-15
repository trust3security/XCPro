# ADR_GENERAL_SETTINGS_HOST_2026-03-15

## Metadata

- Title: General Settings host is app-shell owned, not map-shell owned
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
  - `feature:map` was still owning the cross-feature General Settings host and
    registry through `SettingsDfRuntime*.kt`, `MapScreenScaffold.kt`, and
    `MapModalManager`.
  - That made the map shell the residual owner for a cross-feature settings
    surface that is not map-specific.
- Why now:
  - Phase 1 of the `feature:map` right-sizing program starts by removing the
    broadest non-map shell owner from `feature:map`.
- Constraints:
  - Preserve the existing dual-entry behavior during transition:
    - map-launched General Settings modal
    - `SettingsRoutes.GENERAL` compatibility route
  - Do not widen the slice into `FlightMgmt`, task routes, diagnostics, or
    unrelated settings feature rewrites.
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`

## Decision

The cross-feature General Settings host is owned by the app shell.

Required implications:
- ownership/boundary choice:
  - `app` owns the General Settings sheet host, tile registry, and
    `OPEN_GENERAL_SETTINGS_ON_MAP` compatibility signal handling.
  - `feature:map` only emits `onOpenGeneralSettings` intents from map UI shell
    surfaces.
  - `MapModalManager` owns only airspace modal state.
- dependency direction impact:
  - dependency direction remains `app -> feature:map -> owner modules`.
  - `feature:map` does not gain any new dependency on `app`.
- API/module surface impact:
  - `MapScreen` now exposes `onOpenGeneralSettings` instead of owning launch
    state for the General Settings sheet.
  - three remaining map-local settings sub-sheet wrappers stay public
    temporarily so the app-owned host can reuse them without copying feature
    internals.
- time-base/determinism impact:
  - none; this is UI-shell ownership only.
- concurrency/buffering/cadence impact:
  - no new long-lived scopes or background pipelines were introduced.
  - the app shell uses ordinary Compose state plus existing nav saved-state
    signaling.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep General Settings host in `feature:map` and only clean up local files | lowest code churn | preserves the wrong owner and blocks the right-sizing goal |
| Move the host into `core:ui` | central reusable UI surface looked tempting | the host is cross-feature composition logic, not reusable core UI |
| Move every settings wrapper out of `feature:map` in the same slice | would reduce map ownership further | too wide for Phase 1A and would mix host extraction with unrelated owner moves |

## Consequences

### Benefits
- `feature:map` no longer owns the cross-feature General Settings host.
- The map shell now has a narrow open-intent seam instead of modal ownership.
- The compatibility route and map modal behavior are enforced in one owner.

### Costs
- App shell now temporarily composes a few public map-owned settings wrappers.
- This slice adds one app-shell settings host package and test surface.

### Risks
- Future drift could reintroduce a second General Settings host in `feature:map`.
- Remaining map-owned settings wrappers could be mistaken for permanent owners.

## Validation

- Tests/evidence required:
  - app-shell General Settings policy test
  - explicit `OPEN_GENERAL_SETTINGS_ON_MAP` signal test
  - `MapModalManager` airspace-only regression test
  - repo compile and standard AGENTS checks
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
  - update General Settings host ownership and map modal ownership notes
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - app-shell General Settings host files
  - `MapScreen` open-intent seam
  - `MapModalManager` airspace-only reduction
- What would trigger rollback:
  - regression in map-launched General Settings or compatibility route behavior
- How this ADR is superseded or retired:
  - retire when the broader `feature:map` right-sizing program finishes and the
    General Settings host owner is either unchanged or deliberately replaced by
    a new shell owner
