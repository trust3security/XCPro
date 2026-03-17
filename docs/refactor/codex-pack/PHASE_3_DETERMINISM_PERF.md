# Codex Prompt — Phase 3: Determinism, Replay Confidence, and Performance Guarding

## Mission

Strengthen deterministic replay/timebase confidence and runtime performance assurance for critical paths affected in prior phases.

## Mandatory context to read first

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`

If touching variometer/replay behavior also read:
- `docs/LEVO/levo.md`
- `docs/LEVO/levo-replay.md`

## Focus areas

1. Deterministic repeat-run tests for replay-critical behavior touched by prior phases.
2. Timebase contract tests (monotonic/replay/wall boundaries) for modified domain/fusion/task paths.
3. Performance guardrails and measurable evidence on map/overlay/task gesture paths when touched.

## Required output from Codex

1. Added/updated deterministic regression tests with explicit clocks/time sources.
2. Added/updated tests that prove degraded/error states remain explicit.
3. SLO/performance evidence bundle for any changed map-render interaction behavior.
4. Final quality rescore and residual risk notes.

## Verification (must run)

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run connected tests when runtime/device behavior changed and environment allows.

## Exit criteria

- Determinism confidence is materially improved with repeatable evidence.
- Timebase policy is explicit and tested for touched paths.
- Any impacted mandatory SLO is green, or approved time-boxed deviation is recorded.

