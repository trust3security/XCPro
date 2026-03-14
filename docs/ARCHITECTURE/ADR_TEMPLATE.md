# ADR_TEMPLATE.md

## Purpose

Use this template for durable architecture decisions that should survive
individual tasks, PR descriptions, and prompt history.

ADRs are required when work changes:
- ownership or dependency boundaries across layers/modules
- public or cross-module API surface
- concurrency, buffering, cadence, or determinism policy
- exception patterns expected to recur
- performance/SLO budgets that will drive future tradeoffs

ADRs are not task logs. Use `CHANGE_PLAN_TEMPLATE.md` for execution planning.

Recommended file name:
- `docs/ARCHITECTURE/ADR_<SHORT_TITLE>_YYYY-MM-DD.md`

## Metadata

- Title:
- Date:
- Status: Proposed | Accepted | Superseded | Rejected
- Owner:
- Reviewers:
- Related issue/PR:
- Related change plan:
- Supersedes:
- Superseded by:

## Context

- Problem:
- Why now:
- Constraints:
- Existing rule/doc references:

## Decision

Describe the decision in direct terms.

Required:
- ownership/boundary choice
- dependency direction impact
- API/module surface impact
- time-base/determinism impact
- concurrency/buffering/cadence impact

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| | | |

## Consequences

### Benefits
- 

### Costs
- 

### Risks
- 

## Validation

- Tests/evidence required:
- SLO or latency impact:
- Rollout/monitoring notes:

## Documentation Updates Required

- `ARCHITECTURE.md`:
- `CODING_RULES.md`:
- `PIPELINE.md`:
- `CONTRIBUTING.md`:
- `KNOWN_DEVIATIONS.md`:

## Rollback / Exit Strategy

- What can be reverted independently:
- What would trigger rollback:
- How this ADR is superseded or retired:
