## Purpose

Record the durable Phase 3 decision for constructor and DI boundaries that
must not silently install production fallback behavior.

## Metadata

- Title: Explicit production wiring for ProfileRepository and ADS-B runtime seams
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
  - `ProfileRepository` exposed public convenience construction paths that hid
    `NoOp` collaborators and ad hoc scope creation.
  - `AdsbTrafficRepositoryImpl` and `AdsbTrafficRepositoryRuntime` exposed
    silent production defaults for network availability, emergency-audio
    settings/output, and bootstrap feature flags.
- Why now:
  - Phase 3 of the architecture hardening IP explicitly closes production
    fallback paths so mandatory behavior does not degrade silently.
- Constraints:
  - Keep existing runtime behavior unchanged.
  - Preserve test ergonomics without leaving convenience constructors in main
    production code.
  - Keep dependency direction and repository ownership unchanged.
- Existing rule/doc references:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`

## Decision

Production wiring for these seams is now explicit.

- `ProfileRepository`
  - keeps one injected production constructor only
  - receives a named DI-owned `@ProfileRepositoryScope CoroutineScope`
  - no longer exposes convenience constructors that install `NoOp`
    collaborators or self-create scopes
  - test-only fallback wiring lives in app test support builders

- `AdsbTrafficRepositoryImpl`
  - requires explicit production collaborators for network availability,
    emergency-audio settings, emergency-audio output, and bootstrap feature
    flags
  - no longer exposes convenience constructors or silent defaults in
    production source

- `AdsbTrafficRepositoryRuntime`
  - no longer mirrors fallback defaults from the repository wrapper
  - remains a runtime owner with explicit collaborator inputs only

- ADS-B emergency-audio bootstrap policy
  - is now declared explicitly in DI through `AdsbBindingsModule`
  - default-disabled bootstrap remains allowed, but it is a named DI choice
    rather than an injected constructor default

Dependency direction impact:
- none; repositories remain repository-owned SSOT/runtime owners with injected
  collaborators only

API/module surface impact:
- `ProfileRepository` and `AdsbTrafficRepositoryImpl` production constructors
  are narrower and explicit
- test helpers become the only convenience path for disabled/fallback
  collaborators

Time-base/determinism impact:
- none; no clock or replay behavior changed

Concurrency/buffering/cadence impact:
- `ProfileRepository` runtime scope ownership is explicit in DI; no cadence or
  buffering behavior changed

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep public convenience constructors/defaults and rely on review discipline | Lowest diff | Leaves silent production degradation reachable and conflicts with architecture rules |
| Keep fallbacks as `internal` main-source constructors | Narrower than public | Still leaves production-source convenience paths instead of test-only support |
| Move all disabled policy into runtime logic instead of DI | Centralizes defaults | Hides bootstrap policy in implementation instead of making production wiring explicit |

## Consequences

### Benefits
- Mandatory production behavior cannot silently degrade through constructor defaults.
- Runtime scope ownership is explicit and reviewable.
- Test fallback wiring remains available without polluting production API surface.

### Costs
- More explicit constructor arguments in tests and DI.
- Slightly more supporting test infrastructure.

### Risks
- Constructor rewiring can break Hilt or tests if any callsite is missed.
- Test helper defaults can drift if production collaborator contracts change.

## Validation

- Tests/evidence required:
  - `./gradlew enforceRules`
  - targeted profile repository tests
  - targeted ADS-B repository tests
  - touched module compile checks
- SLO or latency impact:
  - none expected; no runtime cadence or map interaction logic changed
- Rollout/monitoring notes:
  - watch for constructor or Hilt binding compile failures first

## Documentation Updates Required

- `ARCHITECTURE.md`: no change
- `CODING_RULES.md`: no change
- `PIPELINE.md`: update profile scope ownership and ADS-B explicit wiring notes
- `CONTRIBUTING.md`: no change
- `KNOWN_DEVIATIONS.md`: no change

## Rollback / Exit Strategy

- What can be reverted independently:
  - profile scope qualifier/module
  - ADS-B explicit-constructor cleanup
  - test helper rewiring
- What would trigger rollback:
  - unresolved Hilt wiring regression
  - unacceptable test churn with no stable helper path
- How this ADR is superseded or retired:
  - supersede only if a later repository/runtime ownership ADR defines a
    different approved constructor and DI policy
