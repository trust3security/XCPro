# Codex Prompt — Phase 2: Ownership Split & Maintainability

## Mission

Reduce maintenance risk by splitting orchestration hotspots into ownership-focused seams while preserving behavior.

## Mandatory context to read first

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`

## Recommended hotspot candidates

- `feature/traffic/.../MapScreenTrafficCoordinator.kt`
- `feature/tasks/.../TaskManagerCoordinator.kt`
- large UI settings surfaces where multiple concerns are mixed.

## Refactor policy

- Split by responsibility (streaming gate, selection reconciliation, ownship propagation, persistence bridge, etc.).
- Keep each resulting file below preferred budget when practical.
- Preserve public behavior and existing contracts unless explicitly planned.
- Add/adjust targeted unit tests to lock behavior.

## Required output from Codex

1. Before editing, list file ownership plan table:
   - file
   - owner/responsibility
   - why this layer owns it
2. Apply focused splits and remove mixed responsibilities.
3. Summarize ownership after changes for review.
4. Update `PIPELINE.md` if runtime ownership/wiring changed.

## Verification (must run)

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Add targeted tests for moved logic and boundary lock tests where bypasses are removed.

## Exit criteria

- Hotspot responsibilities are decomposed with explicit ownership.
- Behavior parity proven by tests.
- No architecture drift introduced.

