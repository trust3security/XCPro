# MAPSCREEN Completion Contract (Automated Runner)

Date: 2026-03-05  
Owner: XCPro Team  
Status: Active

## 1) Purpose

This contract provides a single resumable runner that completes the MAPSCREEN
artifact and gate flow from current package state to release package finalization.

Primary entrypoint:

`scripts/qa/run_mapscreen_completion_contract.ps1`

Windows wrapper:

`scripts/qa/run_mapscreen_completion_contract.bat`

## 2) Contract Scope

The runner executes and records:

1. phase preflight and script presence checks,
2. phase-2 package metric refresh (`pkg-d1`, `pkg-g1`, `pkg-w1`),
3. `pkg-e1` scaffold capture for phase-3,
4. Tier A and Tier B map frame metrics capture + apply into `metrics.json`,
5. package threshold verification (`pkg-e1` then all packages),
6. required architecture/build gates,
7. release package finalization (`pkg-r1`).

State file:

`logs/phase-runner/mapscreen-completion-contract-state.json`

## 3) Phase Map

| Phase | Name | Key Outputs |
|---|---|---|
| 0 | Preflight and Contract Lock | state initialized, required scripts verified, optional device preflight |
| 1 | Refresh Phase 2 Package Gates | updated `metrics.json`, `gate_result.json`, `threshold_check.json` for `pkg-d1/g1/w1` |
| 2 | Capture pkg-e1 Scaffold | `artifacts/mapscreen/phase3/pkg-e1/<runId>/...` scaffold and gate files |
| 3 | Tier A Frame Metrics Capture and Apply | `tier_a/gfxinfo_summary.json`, `MS-UX-01` Tier A metrics applied |
| 4 | Tier B Frame Metrics Capture and Apply | `tier_b/gfxinfo_summary.json`, `MS-UX-01` Tier B metrics applied |
| 5 | Verify pkg-e1 Thresholds | `threshold_check.json` and updated `gate_result.json` for `pkg-e1` |
| 6 | Verify All Package Thresholds | consolidated package promotion decisions |
| 7 | Required Verification Gates | `scripts/arch_gate.py`, `enforceRules`, `testDebugUnitTest`, `assembleDebug` (+ optional connected tests) |
| 8 | Finalize Release Package pkg-r1 | `artifacts/mapscreen/phase4/pkg-r1/<runId>/` release artifact bundle |

## 3A) MS-UX-01 Runtime Remediation Directives (Mandatory)

Any implementation targeting `MS-UX-01` must keep these runtime contracts:

1. camera interaction mode:
   - while user pan/zoom/rotate is active, non-critical overlay cadence must be throttled.
2. weather/rain interaction policy:
   - interaction mode must force rain transition duration to `0 ms`,
   - rain frame applies must be rate-limited (bounded minimum interval),
   - deferred rain frame updates must flush when interaction ends.
3. traffic overlay cadence:
   - OGN and ADS-B overlay render applies must honor interaction-aware cadence floors,
   - deferred traffic updates must flush at interaction end.
4. overlay z-order churn control:
   - `bringToFront` reconciliation must be interaction-throttled and signature-gated.
5. determinism and layering:
   - cadence logic remains runtime/UI scoped only, with no domain replay timebase drift.

## 4) Run Commands

Default full contract run:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1
```

Start with connected test lanes enabled:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 `
  -RunConnectedAppTestsForPkgE1 `
  -RunConnectedAppTestsAtEnd `
  -RunConnectedAllModulesAtEnd `
  -RequireConnectedDevice
```

Continue through reporting/finalization phases when threshold failures are
expected, while still recording the failure decisions:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 `
  -AllowPkgE1ThresholdFailure `
  -AllowThresholdRollupFailure `
  -AllowNonGreenReleasePackage
```

Resume from last failure:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 -Resume -FromPhase <N>
```

Reset state and restart:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_completion_contract.ps1 -ResetState
```

## 5) AGENTS and Architecture Compliance Mapping

This contract aligns with `AGENTS.md` and `docs/ARCHITECTURE/AGENT.md` by enforcing:

1. required checks:
   - `python scripts/arch_gate.py`
   - `./gradlew enforceRules`
   - `./gradlew testDebugUnitTest`
   - `./gradlew assembleDebug`
2. optional connected checks when device/emulator is available:
   - `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
   - `./gradlew connectedDebugAndroidTest --no-parallel`
3. package artifact contract presence:
   - `manifest.json`, `metrics.json`, `trace_index.json`, `gate_result.json`,
     `arch_gate_result.txt`, `timebase_citations.md`
4. timebase citation continuity:
   - `core/time/src/main/java/com/example/xcpro/core/time/Clock.kt`
   - `app/src/main/java/com/example/xcpro/di/TimeModule.kt`

## 6) Failure and Resume Rules

1. Any non-zero gate command marks the active phase as `failed` in state.
2. The runner prints an explicit resume command with `-Resume -FromPhase <failedPhase>`.
3. `RunId` is persisted in state; resume continues the same artifact lane.
4. `-ResetState` is required to switch to a new `RunId`.

## 7) Promotion Expectations

Release package promotion (`pkg-r1`) is green only when all package lanes report
`ready_for_promotion` in threshold checks.

Current known hard blocker if still present:

- `pkg-e1` fails when `MS-UX-01` Tier A/B frame/jank metrics remain above threshold.

## 8) Latest Strict Run Evidence (2026-03-05)

1. RunId `20260305-193049`:
   - Tier A: `p95=25 ms`, `p99=34 ms`, `jank=6.56%`
   - Tier B: `p95=25 ms`, `p99=34 ms`, `jank=6.74%`
   - phase-5 result: `blocked_failed_thresholds` (`MS-UX-01`).
2. RunId `20260305-195205`:
   - Tier A: `p95=25 ms`, `p99=34 ms`, `jank=6.39%`
   - Tier B: `p95=24 ms`, `p99=34 ms`, `jank=6.21%`
   - phase-5 result: `blocked_failed_thresholds` (`MS-UX-01`).
3. Strict completion status remains non-green until `pkg-e1` threshold verification passes in phase 5.

## 9) Deferred-Fix Note (Approved Pause)

1. As of 2026-03-05, `pkg-e1` `MS-UX-01` is explicitly deferred for a later optimization pass.
2. Defer-until reference:
   - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` issue `RULES-20260305-12` (expiry: 2026-04-15).
3. Current execution policy while deferred:
   - phase-2 lanes may proceed as green (`pkg-d1`, `pkg-g1`, `pkg-w1`),
   - release packaging may proceed only via non-green/exception path,
   - strict-green promotion remains blocked.
4. Re-entry criteria for "fix now":
   - rerun strict contract with no allow-failure flags and reach phase 8,
   - phase-5 `pkg-e1` threshold verification reports `ready_for_promotion`.
