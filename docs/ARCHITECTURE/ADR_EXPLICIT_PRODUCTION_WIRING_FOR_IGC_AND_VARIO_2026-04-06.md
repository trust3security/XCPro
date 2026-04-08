## Purpose

Record the durable Phase 3 decision for IGC and vario runtime owners that must
not silently install production fallbacks for mandatory behavior.

## Metadata

- Title: Explicit production wiring for IGC recording and vario runtime seams
- Date: 2026-04-06
- Status: Accepted
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: Phase 3 of `XCPro Architecture Hardening IP`
- Related change plan: `docs/refactor/XCPro_Architecture_Hardening_Release_Grade_Phased_IP_2026-04-06.md`
- Supersedes:
- Superseded by:

## Context

- Problem:
  - `IgcRecordingUseCase` exposed production-reachable convenience construction
    that silently installed `NoOp` recording and recovery collaborators.
  - `IgcRecoveryBootstrapUseCase` still carried a main-source no-op
    diagnostics path even though startup recovery diagnostics are part of the
    production seam contract.
  - `VarioServiceManager` exposed default `NoOp` or nullable collaborators for
    mandatory IGC and WeGlide post-flight runtime behavior.
- Why now:
  - The architecture hardening seam pass closes silent degraded production
    wiring so runtime correctness is decided at composition time, not by
    constructor defaults.
- Constraints:
  - Preserve runtime behavior and replay determinism.
  - Keep test construction ergonomic without routing tests through full DI.
  - Avoid widening public APIs or creating new runtime owners.
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`

## Decision

Production wiring for the IGC and vario seams is explicit.

- `IgcRecordingUseCase`
  - keeps the injected production constructor as the public production path
  - requires explicit recording action sink, recovery bootstrap, and flight-log
    repository collaborators in production source
  - keeps any convenience construction internal or test-only and explicit about
    disabled collaborators

- `IgcRecoveryBootstrapUseCase`
  - requires an explicit `IgcRecoveryDiagnosticsReporter` in production source
  - no longer carries a main-source convenience constructor that silently
    installs `NoOpIgcRecoveryDiagnosticsReporter`

- `VarioServiceManager`
  - requires explicit IGC recording and WeGlide post-flight collaborators in
    the production constructor
  - no longer treats disabled behavior as implicit constructor defaults

Dependency direction impact:
- none; ownership stays in the existing map/IGC runtime owners with explicit
  collaborator inputs only

API/module surface impact:
- production constructors are narrower and more intentional
- tests use dedicated support builders instead of public convenience defaults

Time-base/determinism impact:
- none; no time source or replay behavior changed

Concurrency/buffering/cadence impact:
- none; this is construction-path cleanup only

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep public/default constructor fallbacks and rely on review discipline | lowest churn | leaves silent production degradation reachable |
| Keep fallbacks as `internal` main-source constructors | narrower surface | still leaves production-source convenience paths instead of test-only support |
| Push disabled behavior deeper into runtime logic | centralizes null/no-op handling | hides composition policy instead of making it explicit in DI or test support |

## Consequences

### Benefits
- Mandatory production behavior cannot disappear through constructor defaults.
- Startup recovery diagnostics remain explicit and reviewable.
- Tests still have narrow helper paths without polluting production APIs.

### Costs
- More explicit collaborator arguments in tests and DI.
- Slightly more test-support code.

### Risks
- Constructor rewiring can break Hilt or tests if a callsite is missed.
- Test helpers can drift if production constructor contracts change.

## Validation

- Tests/evidence required:
  - `./gradlew enforceRules`
  - targeted IGC and vario tests
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- SLO or latency impact:
  - none expected
- Rollout/monitoring notes:
  - watch constructor and Hilt compile failures first

## Documentation Updates Required

- `ARCHITECTURE.md`: no change
- `CODING_RULES.md`: no change
- `PIPELINE.md`: update IGC and vario production wiring notes
- `CONTRIBUTING.md`: no change
- `KNOWN_DEVIATIONS.md`: no change

## Rollback / Exit Strategy

- What can be reverted independently:
  - `IgcRecordingUseCase` constructor cleanup
  - `IgcRecoveryBootstrapUseCase` no-op removal
  - `VarioServiceManager` explicit-collaborator cleanup
  - test-support helpers
- What would trigger rollback:
  - unresolved Hilt binding regression
  - unacceptable test churn with no stable helper path
- How this ADR is superseded or retired:
  - supersede only if a later runtime-ownership ADR deliberately redefines the
    approved composition boundary for these seams
