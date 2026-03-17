# Codex Prompt — Phase 1: Stability & Risk Closure

## Mission

Execute a focused stability phase that closes the highest-risk active deviations and improves release confidence without changing product scope.

## Mandatory context to read first

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`

## Primary targets

1. Close/mitigate map SLO deviation path (`MS-UX-01`, pkg-e1 evidence lane) with measurable, reproducible artifacts.
2. Close/mitigate production logging drift by enforcing canonical logging seam and reducing privacy-risk callsites.

## Constraints

- Preserve MVVM + UDF + SSOT and dependency direction.
- Do not move business logic into UI.
- No hidden mutable singleton state.
- Keep replay behavior deterministic.

## Required output from Codex

1. A short change plan (in PR description or plan doc) listing:
   - SSOT owners touched
   - files to modify and ownership rationale
   - timebase declaration for touched values
2. Implementation in small, auditable commits.
3. Updated docs where required:
   - `PIPELINE.md` (if wiring changed)
   - `KNOWN_DEVIATIONS.md` (if resolved, narrowed, or renewed)
4. Test/evidence artifacts for map SLO path and logging policy hardening.

## Verification (must run)

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Also run map evidence scripts for touched map-interaction/overlay paths per MAPSCREEN docs.

## Exit criteria

- Targeted deviation scope is measurably improved and documented.
- No new architecture/timebase violations.
- Required gates pass.

