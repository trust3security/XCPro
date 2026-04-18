# ADR_HAWK_VARIO_PREVIEW_READ_PORT_2026-03-15

## Metadata

- Title: HAWK settings move to profile ownership behind a read-only preview port
- Date: 2026-03-15
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/Feature_Map_Settings_Lane_Release_Grade_Phased_IP_2026-03-15.md`
  - `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
- Related ADR:
  - `docs/ARCHITECTURE/ADR_HAWK_RUNTIME_OWNER_2026-03-15.md`
- Supersedes:
  - none
- Superseded by:
  - none

## Context

- Problem:
  - HAWK settings were still compiled in `feature:map` even though settings
    persistence already lived with the vario/profile settings lane.
  - The screen also depended on live HAWK preview state from the map runtime,
    so a direct move would either keep the wrong owner or create a second live
    runtime owner.
  - The screen consumed units through a map-owned wrapper even though the
    canonical units use case already exists outside `feature:map`.
- Why now:
  - HAWK was the last mixed-owner settings surface left in the
    `feature:map` settings-lane program.
- Constraints:
  - Keep one live HAWK runtime owner.
  - Preserve direct-route and General Settings sub-sheet entrypoints.
  - Preserve existing replay/live cadence semantics for HAWK preview.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/ARCHITECTURE/ADR_SETTINGS_SCREEN_OWNER_MODULES_2026-03-15.md`

## Decision

HAWK settings UI is profile-owned, and live HAWK preview crosses modules only
through a read-only preview contract.

Required:
- ownership/boundary choice:
  - `feature:profile` owns `HawkVarioSettingsScreen`,
    `HawkVarioSettingsUseCase`, `HawkVarioSettingsViewModel`, and their tests.
  - `feature:variometer` owns the cross-module preview contract:
    `HawkVarioPreviewReadPort`, `HawkVarioUiState`, and `HawkConfidence`.
  - `feature:variometer` also owns the live `HawkVarioUseCase` runtime owner.
  - `HawkVarioUseCase` implements `HawkVarioPreviewReadPort`; consumers may
    read preview state through that interface only.
  - `feature:map` keeps only temporary `HawkSensorStreamPort` /
    `HawkActiveSourcePort` adapters until Parent Phase 2B replaces those
    upstream owners.
  - the moved screen reads units through `UnitsSettingsUseCase`, not the map
    wrapper.
- dependency direction impact:
  - `feature:profile` does not depend on `feature:map` for HAWK settings.
  - `feature:map` depends on variometer-owned runtime interfaces only through
    temporary adapters and does not own the runtime implementation.
- API/module surface impact:
  - the new cross-module API surface is the read-only preview port plus the
    preview DTO/state types.
  - public package/class names for the screen stay stable to avoid route churn.
- time-base/determinism impact:
  - the preview port is read-only and inherits the existing HAWK runtime
    cadence/time-base from `HawkVarioUseCase`.
  - no new wall-clock or randomness is introduced.
- concurrency/buffering/cadence impact:
  - no new runtime owner or ticker is introduced.
  - `HawkVarioUseCase` remains the single cadence owner for live preview.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep HAWK settings in `feature:map` | smallest code change | preserves wrong owner and leaves the settings lane incomplete |
| Move HAWK settings to `feature:variometer` with runtime preview | keeps everything “vario” together | conflicts with the repo’s existing pattern where settings screens live in the profile-owned settings lane |
| Move the screen first and inject `HawkVarioUseCase` directly into profile | fastest implementation | creates a direct profile -> map dependency and hides the runtime boundary |

## Consequences

### Benefits
- `feature:map` no longer owns the HAWK settings surface.
- One explicit HAWK runtime owner remains.
- The shared HAWK preview contract is visible and testable.
- The settings-lane plan can close cleanly and the right-sizing program can
  move on to flight-runtime extraction.

### Costs
- New cross-module preview contract files exist in `feature:variometer`.
- Temporary runtime adapter/binding files exist in `feature:map`.

### Risks
- Future changes could abuse the preview port as a write path unless the
  read-only boundary is preserved.
- Temporary map-backed sensor/source adapters could linger after the
  flight-runtime seam is ready unless Parent Phase 2B removes them.

## Validation

- Tests/evidence required:
  - `feature/profile/src/test/java/com/trust3/xcpro/screens/navdrawer/HawkVarioSettingsUseCaseTest.kt`
  - `feature/profile/src/test/java/com/trust3/xcpro/screens/navdrawer/HawkVarioSettingsViewModelTest.kt`
  - `feature/variometer/src/test/java/com/trust3/xcpro/hawk/HawkVarioUiStateTest.kt`
  - `feature/variometer/src/test/java/com/trust3/xcpro/hawk/HawkVarioEngineTest.kt`
  - `feature/variometer/src/test/java/com/trust3/xcpro/hawk/HawkVarioRepositoryTest.kt`
  - app General Settings policy coverage keeps the HAWK tile/sub-sheet contract
  - standard AGENTS gates
- SLO or latency impact:
  - none expected; preview cadence remains map-owned
- Rollout/monitoring notes:
  - no staged rollout required; this is internal ownership cleanup

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update HAWK settings ownership and preview-port runtime notes
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - the HAWK settings owner move
  - the preview-port contract and binding
- What would trigger rollback:
  - HAWK settings preview regressions or route/sub-sheet resolution regressions
- How this ADR is superseded or retired:
  - keep this ADR for the preview-port boundary; runtime-owner changes are now
    tracked by `ADR_HAWK_RUNTIME_OWNER_2026-03-15.md`
