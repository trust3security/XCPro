# Test And Validation Plan: Thermal Smoothing

Date
- 2026-02-27

Purpose
- Define repeatable verification for ownship snail-trail smoothing changes.
- Prevent regressions in determinism, architecture, and runtime performance.
- Provide a repo-native ground-validation path before flight signoff.

## 1. Unit Tests To Add/Extend

Trail domain tests:
- `TrailProcessorTest`
  - adaptive live sample threshold behavior (cruise vs circling)
  - live wind smoothing branch (if added)
- `SnailTrailRenderPlannerTest`
  - circling-aware distance filtering rules
- `SnailTrailManager` tests (new if missing)
  - tail refresh cadence and gating behavior
  - replay display-pose trail rendering
  - transient display connector renders without storing connector points
- `ResolveCirclingUseCaseTest`
  - preserve replay fallback behavior

Properties to verify:
- replay path unchanged
- replay data path unchanged while replay display-pose trail rendering is enabled
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

## 3A. Ground Validation Before Flight

Ground validation does not replace real flight testing, but it should be the
mandatory pre-flight confidence pass for this slice.

Professional default:
- prove repo/runtime invariants first
- validate replay thermal shape on the ground with controlled replay or a known
  real thermal segment first
- validate live ground movement outdoors with stable GNSS
- only then move to real flight signoff

Ground scenarios:
- `scenario-01-replay-thermal`
  - replay a controlled replay or known real thermal segment
  - let the replay run to completion
  - expected: tail/head coherence, full multi-loop thermal remains visible at finish, no replay drift, no stale tail after scrub
- `scenario-02-live-circle`
  - repeated tight circles outdoors for 2-3 minutes
  - expected: materially less polygonal live arc shape than the old fixed `2 s` trail
- `scenario-03-circle-exit`
  - straight -> circles -> straight
  - expected: prompt entry/exit reaction with no stale circling artifact
- `scenario-04-lifecycle`
  - background/foreground while moving
  - expected: blue icon and trail remain aligned after resume
- `scenario-05-zoom-stress`
  - zoom sweeps while moving/circling
  - expected: no tail flicker or severe shape jump
- `scenario-06-live-replay-switch`
  - live -> replay -> live in one session
  - expected: no timebase contamination or stale display-pose reuse

Preferred live setup:
- open outdoor area with stable GNSS lock
- bicycle or slow vehicle loops if safe and available
- walking is acceptable, but repeated circles are less consistent

## 3B. Ground Validation Artifact Contract

Use the scaffold command to create a repeatable evidence pack:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/scaffold_snail_ground_validation.ps1
```

Windows wrapper:

```bat
scripts\qa\run_snail_ground_validation_scaffold.bat
```

Optional flags:
- `-RunId <yyyyMMdd-HHmmss>` to force a deterministic artifact folder
- `-PkgF1RunId <yyyyMMdd-HHmmss>` to link the lifecycle/cadence evidence run
- `-BuildVariant <debug|...>` to record the tested variant

Artifact path:
- `artifacts/snail/ground/<timestamp>/`

The scaffold creates:
- `manifest.json`
- `RUNBOOK.md`
- `ACCEPTANCE.md`
- `observations.md`
- `tier_a/<scenario-id>/...`
- `tier_b/<scenario-id>/...`

Each scenario folder contains:
- `screen_recordings/`
- `screenshots/`
- `notes/`
- `CHECKLIST.md`

Replay input guidance:
- `docs/IGC/example.igc` remains the canonical real-world parser smoke fixture.
- Do not invent a custom sub-second `.igc` format for thermalling validation.
- The preferred deterministic pre-flight baseline is the synthetic in-memory thermal replay path loaded through `IgcReplayController.loadLog(...)` and replayed at `ReplayCadenceProfile.LIVE_100MS`.

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

Lifecycle/cadence proof:
- `powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_pkg_f1_evidence_capture.ps1 -RunConnectedAppTests -RequireConnectedDevice`
- attach Tier A/B measured values for `MS-ENG-06` and `MS-ENG-10`
- rerun `scripts/qa/verify_mapscreen_package_evidence.ps1 -PackageId pkg-f1 -RunId <runId> -UpdateGateResult`

Module-focused loop:
- `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.trail.*"`

Windows lock-safe loop:
- `test-safe.bat :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.trail.*"`

## 6. Exit Criteria

The change set is complete when:
- thermal visual jerk is materially reduced in manual scenario A/B
- no new test failures in trail and related map modules
- replay determinism remains intact
- architecture checks remain green
- `pkg-f1` is no longer `blocked_pending_perf_evidence`
- ground scenarios `01-06` are recorded and reviewed on both Tier A and Tier B
- documentation in `docs/SNAIL/` is updated with final behavior deltas
