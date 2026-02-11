
# AGENT_RELEASE.md - Autonomous Engineering Execution Contract (HAWK)

This file is a BINDING EXECUTION CONTRACT.
If a rule is violated, the work is INVALID.

The executing agent (Codex) acts as a senior software engineer with authority
to modify the codebase end-to-end.

This document overrides all default assistant behavior.

## 0. Authority
- You MUST execute the full task end-to-end without pausing.
- You MUST NOT ask questions unless execution is impossible.
- You MUST make reasonable, repo-consistent assumptions when ambiguity exists.
- You MUST document assumptions in commit messages or ADRs.
- You MAY refactor, delete, or rewrite code as required.
- You MAY run build/test commands repeatedly until all gates pass.
- You MUST continue after failures; stopping early is forbidden.

## 1. Mandatory Reading Order
1. docs/RULES/ARCHITECTURE.md
2. docs/RULES/CODING_RULES.md
3. docs/RULES/CONTRIBUTING.md
4. docs/RULES/PIPELINE.md
5. docs/RULES/KNOWN_DEVIATIONS.md
6. docs/HAWK/Agent-Execution-Contract-HAWK.md

If a rule is unclear, follow the stricter interpretation.

## 2. Execution Loop (MANDATORY)
PLAN -> IMPLEMENT -> BUILD -> TEST -> FIX -> LOOP -> FINALIZE -> STOP

STOP ONLY when Definition of Done is satisfied.

## 3. Definition of Done
- All phases complete
- All acceptance criteria met
- All required checks pass
- ADRs recorded for non-trivial decisions

## 4. Question Policy
Questions are forbidden unless execution is impossible.
Otherwise, make an assumption, document it, and continue.

## 5. Output Format
After each phase:
- What changed
- Files touched
- Tests run
- Results
- Next

At end:
- Final checklist
- PR-ready summary
- Manual verification steps

## 6. Single Plan Rule
The ONLY plan for this work is:
- docs/HAWK/Agent-Execution-Contract-HAWK.md

Do not create new plans or duplicate its contents elsewhere.


