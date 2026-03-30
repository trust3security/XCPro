# Codex master brief — OGN reconnect/runtime hardening

## Read first

Before editing anything, read the repository contract in this order:

1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/CODING_RULES.md`
4. `docs/ARCHITECTURE/PIPELINE.md`
5. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
6. `docs/ARCHITECTURE/CONTRIBUTING.md`
7. `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`
8. `docs/ARCHITECTURE/AGENT.md`
9. `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`

Use `02_CHANGE_PLAN_OGN_RECONNECT_HARDENING.md` as the working implementation plan.

## Objective

Harden OGN reconnect/runtime behavior so it is easier to reason about, easier to support, and less likely to hide transport loss. Fix the user-facing clean-EOF blind spot first. Then add explicit offline waiting. Then serialize OGN runtime state ownership safely. Finish with structured telemetry and regression coverage.

## Current seam summary

### 1) Clean EOF can loop forever at the minimum retry cadence and stay mostly invisible
Current behavior in `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt`:

- `runConnectionLoop()` resets `backoffMs` to the start value after any non-throwing `connectAndRead()` return.
- `connectAndRead()` returns `ConnectionExitReason.StreamEnded` on `readLine() == null`.
- The map UI only shows OGN loss for `OgnConnectionState.ERROR`.

That combination means repeated clean socket EOF can keep retrying at 1 second and never surface as "lost".

### 2) OGN has no explicit offline wait seam
Current OGN logic connects, errors, and backs off blindly. ADS-B already has a better seam in `AdsbTrafficRepositoryRuntimeNetworkWait.kt`, where the runtime pauses until connectivity returns.

### 3) OGN state ownership is not single-writer serialized
`OgnTrafficRepositoryRuntime.kt` uses the raw injected dispatcher and multiple launched collectors. It also mutates some runtime state directly from caller-thread entry points such as `updateCenter()`. ADS-B uses a serialized writer dispatcher, but OGN cannot copy that pattern blindly because OGN's socket loop uses blocking `readLine()` with timeouts.

### 4) OGN telemetry is coarse
`sanitizeError()` currently compresses failures to a throwable class simple name. Support/debug value is limited.

## Hard constraints

- Repository runtime remains the SSOT for OGN connection truth.
- UI stays read-only; do not move transport policy into UI.
- Use injected clocks and existing time-base conventions.
- Do not introduce hidden global mutable state.
- Do not serialize blocking socket I/O on the same lane that owns all state mutation.
- Prefer small, responsibility-focused files over extending a mixed file past reason.
- If boundaries change, update docs and add an ADR only if the change is durable architecture, not just local refactor mechanics.

## Phase order

- Phase 0: baseline guardrails and scaffolding
- Phase 1: unexpected stream-end semantics and visible backoff
- Phase 2: explicit offline wait seam
- Phase 3: serialized state ownership
- Phase 4: telemetry + regression hardening

Do not skip ahead. Land one phase at a time.

## Mandatory pre-edit declaration for each phase

Before editing, state:

1. SSOT ownership for any new or changed state
2. file ownership plan for each created/modified file
3. time base for any new timestamps, delays, or deadlines
4. similar reference files reviewed
5. why the chosen structure is safer than the smallest possible diff

## Output format required after each phase

Use the repo's phase summary style:

### Phase N Summary
- What changed
- Reference pattern reused
- Ownership confirmation
- Files touched
- Tests added/updated
- Verification results
- Risks / follow-ups

## Verification policy

Minimum:
```bash
./gradlew enforceRules
```

When a phase changes runtime behavior or tests:
```bash
./gradlew :feature:traffic:testDebugUnitTest
```

At the end of the final phase:
```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## Important implementation caution

Do not "fix" Phase 3 by moving the entire OGN runtime and blocking socket read loop onto `limitedParallelism(1)`. That is too blunt. OGN uses blocking `Socket` + `BufferedReader.readLine()` with timeouts. State ownership must become single-writer, but the blocking I/O path should remain an event producer, not the sole state writer thread.
