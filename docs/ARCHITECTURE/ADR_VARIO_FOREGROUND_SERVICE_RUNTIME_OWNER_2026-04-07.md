# ADR_VARIO_FOREGROUND_SERVICE_RUNTIME_OWNER_2026-04-07

## Metadata

- Title: Foreground service owns variometer runtime lifetime and feature code requests runtime control through a port
- Date: 2026-04-07
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/Runtime_Ownership_Boundary_Standardization_Phased_IP_2026-03-14.md`
- Supersedes:
  - none
- Superseded by:
  - none

## Context

- Problem:
  - `VarioServiceManager` was creating its own long-lived scope even though
    `VarioForegroundService` was the real production lifetime owner.
  - map and replay code were also starting the manager directly, which made the
    runtime owner ambiguous and forced `feature:map` to know about app-level
    service startup policy.
  - feature code could still call direct `stop()` on the manager, leaving
    teardown ownership inconsistent with the new start seam.
- Why now:
  - the runtime-scope hardening slice already removed several hidden singleton
    scopes, and the variometer service path remained the highest-risk owner leak.
- Constraints:
  - keep replay deterministic.
  - preserve live stop/resume behavior for map and replay.
  - avoid adding a feature-to-app dependency from `feature:map`.
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/LEVO/levo.md`

## Decision

`VarioForegroundService` is the only production owner of the long-lived live
sensor runtime scope. `VarioServiceManager` no longer creates its own scope;
instead it requires `start(ownerScope: CoroutineScope)` and launches its
long-lived jobs under the caller-owned service scope while keeping execution on
the injected default dispatcher.

Required:
- ownership/boundary choice:
  - `VarioForegroundService` owns the parent runtime scope and is responsible
    for its cancellation.
  - `VarioServiceManager` owns orchestration and job handles, but not the
    parent lifetime.
  - `feature:map` and replay code request live-runtime start/stop through
    `VarioRuntimeControlPort`.
- dependency direction impact:
  - `feature:map` no longer needs to know about the app service class.
  - `app` binds the concrete foreground-service starter behind the
    `VarioRuntimeControlPort` interface.
- API/module surface impact:
  - `VarioServiceManager.start()` becomes
    `start(ownerScope: CoroutineScope)`.
  - new `feature:map` interface:
    `VarioRuntimeControlPort.ensureRunningIfPermitted(): Boolean` and
    `VarioRuntimeControlPort.requestStop()`.
  - `MapSensorsUseCase` and `ReplayPipeline` use request-based runtime control
    seams instead of direct manager lifetime ownership.
- time-base/determinism impact:
  - none intended.
  - replay still uses IGC time and live collection still uses the existing
    injected clocks/time sources.
- concurrency/buffering/cadence impact:
  - no change to collection cadence or buffering policy.
  - repeated foreground-service start commands are allowed and idempotent at
    the manager layer.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep `VarioServiceManager` self-scoped | smallest code diff | preserves the hidden lifetime owner |
| Inject a singleton DI scope into `VarioServiceManager` | consistent with other qualifiers | still hides the true Android service owner behind DI |
| Make `feature:map` call `VarioForegroundService` directly | simple wiring | violates dependency direction by introducing a feature-to-app dependency |

## Consequences

### Benefits
- Live variometer lifetime now has one explicit production owner.
- Map and replay code use an app-independent runtime control seam.
- Repeated service-start requests can restart the manager without recreating the
  service instance.
- Teardown ownership is now symmetric with startup ownership.

### Costs
- Constructors and tests need the new port/scope parameters.
- `MapSensorsUseCase.startSensors()` changes semantics from immediate readiness
  to start-request acceptance.

### Risks
- Callers that assumed immediate readiness after `startSensors()` could drift if
  not updated to request semantics.

## Validation

- Tests/evidence required:
  - `./gradlew enforceRules`
  - `:feature:map:testDebugUnitTest`
  - `:app:testDebugUnitTest`
  - `scripts/qa/run_root_unit_tests_reliable.bat`
  - `./gradlew assembleDebug`
- SLO or latency impact:
  - none expected
- Rollout/monitoring notes:
  - no staged rollout required; this is an ownership-boundary correction only

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update live sensor and replay ownership notes
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required

## Rollback / Exit Strategy

- What can be reverted independently:
  - the `VarioRuntimeControlPort` adapter/binding
  - the `start(ownerScope)` API change
  - the service command-start behavior
- What would trigger rollback:
  - unexpected regressions in live sensor startup/resume behavior
- How this ADR is superseded or retired:
  - supersede it if live sensor runtime ownership later moves to a different
    explicit host with the same single-owner guarantee
