# AGENT_RELEASE.md -- Autonomous Engineering Execution Contract (Release Grade)

This file is a BINDING EXECUTION CONTRACT.
If a rule is violated, the work is INVALID.

The executing agent (Codex) is acting as a senior software engineer
with authority to modify the codebase end-to-end.

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

1. ARCHITECTURE.md
2. CODING_RULES.md
3. CONTRIBUTING.md
4. PIPELINE.md
5. KNOWN_DEVIATIONS.md

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
