# Test And Validation Plan: Thermal Smoothing

Date
- 2026-02-27

Purpose
- Define repeatable verification for ownship snail-trail smoothing changes.
- Prevent regressions in determinism, architecture, and runtime performance.

## 1. Unit Tests To Add/Extend

Trail domain tests:
- `TrailProcessorTest`
  - adaptive live sample threshold behavior (cruise vs circling)
  - live wind smoothing branch (if added)
- `SnailTrailRenderPlannerTest`
  - circling-aware distance filtering rules
- `SnailTrailManager` tests (new if missing)
  - tail refresh cadence and gating behavior
- `ResolveCirclingUseCaseTest`
  - preserve replay fallback behavior

Properties to verify:
- replay path unchanged
- live path denser in circling
- deterministic outputs for same input sequence
- no time-base mixing

## 2. Integration/Runtime Validation

Map runtime checks:
- blue icon and tail endpoint remain coherent in thermal circles
- no layer ordering regression (trail below icon)
- no frozen trail when circling starts/stops

Replay checks:
- same replay file produces same trail output shape each run
- no new replay stutter introduced

## 3. Manual Flight Scenarios

Scenario A: Smooth sustained thermal
- Expected: arc-like trail, reduced cornering/jumps.

Scenario B: Weak/variable wind thermal
- Expected: drift remains stable without oscillatory trail jumps.

Scenario C: Thermal exit to glide
- Expected: behavior transitions cleanly, no trail reset artifact.

Scenario D: Zoom sweep while circling
- Expected: no severe aliasing jump due to distance filter shifts.

## 4. Performance And Telemetry Checks

Track:
- approximate per-render line/dot feature counts
- map render/frame stability while circling
- memory growth of trail point store (bounded)

Optional debug logging:
- use `SnailTrailLogger` only in debug builds
- avoid release hot-loop verbosity

## 5. Required Commands

Core verification:
- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Module-focused loop:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.trail.*"`

Windows lock-safe loop:
- `test-safe.bat :feature:map:testDebugUnitTest --tests "com.example.xcpro.map.trail.*"`

## 6. Exit Criteria

The change set is complete when:
- thermal visual jerk is materially reduced in manual scenario A/B
- no new test failures in trail and related map modules
- replay determinism remains intact
- architecture checks remain green
- documentation in `docs/SNAIL/` is updated with final behavior deltas

