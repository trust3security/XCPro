# TASK.md - Autonomous Execution Task (HAWK)

## Objective
Implement the HAWK-inspired variometer pipeline as a separate, removable
calculation path that consumes existing sensors but does not change existing
TE calculations or outputs.

## Authoritative Plan (single plan)
- docs/HAWK/Agent-Execution-Contract-HAWK.md

## Constraints
- docs/RULES/ARCHITECTURE.md
- docs/RULES/CODING_RULES.md
- docs/RULES/PIPELINE.md
- docs/RULES/KNOWN_DEVIATIONS.md

## Execution Rules
- Follow docs/HAWK/AGENT_RELEASE.md without exception.
- Do not ask questions unless execution is impossible.
- Preserve behavior parity unless explicitly allowed.
- Keep HAWK fully separate from existing TE calculations.

## Stop Condition
STOP only when all phases are complete and all checks pass.

