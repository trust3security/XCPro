# ADR_MAP_RUNTIME_VISUAL_PRIMITIVES_2026-03-16.md

## Metadata

- Title: Move map visual/runtime primitives to `feature:map-runtime`
- Date: 2026-03-16
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/Feature_Map_Right_Sizing_Release_Grade_Phased_IP_2026-03-15.md`
  - `docs/refactor/Feature_Map_Autonomous_Agent_Execution_Contract_2026-03-15.md`
- Supersedes:
- Superseded by:

## Context

- Problem:
  - `feature:map` was still compiling runtime-owned visual primitives that are
    not shell responsibilities:
    `BlueLocationOverlay`, `SailplaneIconBitmapFactory`, and
    `MapScaleBarController`.
- Why now:
  - Parent Phase 3 of the active right-sizing plan starts the final
    `feature:map` to `feature:map-runtime` burn-down.
  - The first clean owner move is the visual/runtime primitive set; starting
    from `MapOverlayManager` or `MapScreenViewModel` would have been churn
    because those are already mostly shell wrappers.
- Constraints:
  - `feature:map-runtime` must not depend back on `feature:map`.
  - `feature:map` must keep shell-held runtime handles and composition wiring.
  - No behavior or cadence change is allowed on the hot map render path.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`

## Decision

The visual/runtime primitive set belongs to `feature:map-runtime`.

Required boundary choice:
- `feature:map-runtime` owns:
  - `BlueLocationOverlay`
  - `SailplaneIconBitmapFactory`
  - `MapScaleBarController`
  - the narrow `MapScaleBarRuntimeState` contract used by
    `MapScaleBarController`
- `feature:map` owns:
  - shell composition and runtime wiring
  - `MapScreenState` as the shell-held implementation of
    `MapScaleBarRuntimeState`

Dependency direction impact:
- `feature:map` depends on `feature:map-runtime` for these runtime owners.
- `feature:map-runtime` does not depend on `feature:map`; shell-held map state
  is exposed only through the narrow `MapScaleBarRuntimeState` port.

API/module surface impact:
- `MapScaleBarController` becomes a cross-module runtime owner.
- `MapScaleBarRuntimeState` is the explicit shell/runtime bridge for scale-bar
  handles.
- `BlueLocationOverlay` and `SailplaneIconBitmapFactory` remain runtime-owned
  types in the shared `com.trust3.xcpro.map` package surface.

Time-base/determinism impact:
- None. This move changes owner/module placement only.

Concurrency/buffering/cadence impact:
- None. Runtime cadence and render behavior stay with the existing runtime
  owners; no new buffering or scheduling layer is introduced.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep the primitives in `feature:map` and only split wrappers | smaller code diff | leaves wrong owners in the shell and does not reduce architecture drift |
| Start Parent Phase 3 with `MapOverlayManager` or `MapScreenViewModel` cleanup | those files looked like visible hotspots | seam pass showed they are already mostly shell-facing; starting there would be churn rather than a real owner move |
| Move `MapScaleBarController` and let it depend directly on `MapScreenState` | lowest-effort compile fix | creates a `feature:map-runtime -> feature:map` back-edge and violates dependency direction |

## Consequences

### Benefits
- `feature:map` is smaller and more shell-focused.
- Runtime visual primitives now have one clear owner.
- The scale-bar bridge is explicit and testable through a narrow port.

### Costs
- A new runtime bridge interface is part of the long-lived module surface.
- One owner test currently remains in `feature:map` because that module already
  has the working Robolectric/MapLibre harness for it.

### Risks
- Future shell code could try to grow the bridge port beyond runtime-handle
  ownership.
- The temporary test location mismatch could hide drift if not revisited when
  the harness is cleaned up.

## Validation

- Tests/evidence required:
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- SLO or latency impact:
  - none expected; no runtime cadence change
- Rollout/monitoring notes:
  - continue Parent Phase 3 seam locks against the new owner split

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - none
- `CODING_RULES.md`:
  - none
- `PIPELINE.md`:
  - update runtime-owner bullets and key-file paths for the moved primitives
- `CONTRIBUTING.md`:
  - none
- `KNOWN_DEVIATIONS.md`:
  - none

## Rollback / Exit Strategy

- What can be reverted independently:
  - this visual/runtime primitive move only
- What would trigger rollback:
  - build/test regression or a discovered `feature:map-runtime -> feature:map`
    back-edge
- How this ADR is superseded or retired:
  - superseded only if a later module boundary replaces the current
    `feature:map` / `feature:map-runtime` split
