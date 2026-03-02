# AIRSPACE Docs

This folder is the entrypoint for airspace-specific context and execution guidance.

## Start Here

1. `AIRSPACE_AGENT_KNOWLEDGE_2026-03-01.md`
2. `../refactor/Airspace_Correctness_Compliance_Plan_2026-03-01.md`

## Purpose

- Preserve airspace architecture and bug context for future agents.
- Provide a single starting point before changing airspace code paths.
- Keep implementation and verification aligned with repository architecture rules.

## Update Rule

When airspace wiring, ownership, parser behavior, or runtime apply semantics change:

1. Update `AIRSPACE_AGENT_KNOWLEDGE_2026-03-01.md`.
2. Update `../ARCHITECTURE/PIPELINE.md` if pipeline wiring changed.
3. Update the active refactor plan document if phase scope/gates changed.
