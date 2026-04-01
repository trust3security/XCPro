# Racing Task Docs

This folder contains XCPro's Racing Task reference material and the active
production-grade execution plan for the racing-task slice.

These documents are implementation guidance for XCPro. They are not the FAI
rulebook. When a rule question matters, verify against the official FAI
documents first.

## Current reference docs

- `sources.md`
- `racing_task_definition.md`
- `task_elements_and_geometry.md`
- `start_procedure.md`
- `turnpoints_and_observation_zones.md`
- `finish_procedure.md`
- `validation_algorithms.md`
- `task_creation_ui_spec.md`
- `task_json_schema_example.md`

## Current execution docs

- `CHANGE_PLAN_RACING_TASK_PRODUCTION_GRADE_PHASED_IP_2026-03-07.md`
- `AGENT_AUTOMATION_CONTRACT_RACING_TASK_2026-03-08.md`

## Archived historical plans

- `archive/2026-04-doc-pass/CHANGE_PLAN_RACING_TASK_RT_COMPLIANCE_2026-02-20.md`
- `archive/2026-04-doc-pass/CHANGE_PLAN_RACING_TASK_PHASE1_CANONICAL_MODEL_95PLUS_2026-03-07.md`

## Primary rules source

- FAI Sporting Code Section 3 Annex A, 2025 edition:
  `https://www.fai.org/sites/default/files/sc3a_2025.pdf`
- FAI Sporting Code Section 3, 2025 edition:
  `https://www.fai.org/sites/default/files/sc3_2025.pdf`
- FAI documents index:
  `https://www.fai.org/page/documents-0`

## Exactness note

For XCPro live navigation, task progression should be driven by the declared
boundary geometry and credited crossing evidence. Near-miss and tolerance cases
must be surfaced explicitly and must not silently auto-advance the task as if
they were clean achievements.
