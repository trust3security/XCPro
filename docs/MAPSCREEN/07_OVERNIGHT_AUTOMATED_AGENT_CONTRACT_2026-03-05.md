# MAPSCREEN Overnight Automated Agent Contract

Date: 2026-03-05  
Owner: XCPro Team  
Status: Active

## 1) Purpose

Provide a no-input overnight runner for MAPSCREEN completion flow so the pipeline
continues while unattended.

Primary entrypoint:

`scripts/qa/run_mapscreen_overnight_agent_contract.ps1`

Windows wrapper:

`scripts/qa/run_mapscreen_overnight_agent_contract.bat`

## 2) What It Automates

1. Runs strict MAPSCREEN completion contract attempts in sequence.
2. Retries strict runs up to a configured limit.
3. Sleeps between attempts to avoid immediate repeated capture churn.
4. Optionally executes one fallback non-green finalization attempt when strict
   attempts are exhausted.
5. Writes full session logs and machine-readable summary JSON.

Underlying strict runner:

`scripts/qa/run_mapscreen_completion_contract.ps1`

## 3) Default Stop Rules

1. Stop immediately if a strict attempt passes (`strict_green`).
2. If strict attempts fail and fallback is enabled, run one fallback attempt with:
   - `-AllowPkgE1ThresholdFailure`
   - `-AllowThresholdRollupFailure`
   - `-AllowNonGreenReleasePackage`
3. Final status is one of:
   - `strict_green`
   - `fallback_non_green_finalized`
   - `failed`

## 4) Session Artifacts

For each overnight session:

- session log: `logs/phase-runner/overnight/<session-id>/session.log`
- state file: `logs/phase-runner/overnight/<session-id>/contract-state.json`
- summary JSON: `logs/phase-runner/overnight/<session-id>/summary.json`

## 5) Run Commands

Default unattended run (strict retries + fallback enabled):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_overnight_agent_contract.ps1
```

Strict-only overnight run (no non-green fallback):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_overnight_agent_contract.ps1 `
  -FinalizeNonGreenOnExhaustedStrictAttempts:$false
```

Custom retry budget and cooldown:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_overnight_agent_contract.ps1 `
  -MaxStrictAttempts 8 `
  -CooldownMinutesBetweenAttempts 15
```

Dry-run (contract command generation and flow only):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/qa/run_mapscreen_overnight_agent_contract.ps1 -DryRun
```

Dry-run note:
- executes completion contract in dry-run mode for phases `0..2` only (preflight + package refresh + scaffold wiring),
  which validates unattended orchestration without requiring real frame-capture outputs.

## 6) Operational Notes

1. Keep device connected and awake for realistic Tier A/B captures.
2. If strict `MS-UX-01` failure persists, fallback mode can still complete a
   non-green packaging path automatically.
3. This contract does not modify code; it only automates evidence/gate execution.
4. Current known blocker for strict green remains tracked in:
   - `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` issue `RULES-20260305-12`.

## 7) Morning Check Checklist

1. Open `summary.json` and check `overallStatus`.
2. If `strict_green`, proceed with normal promotion.
3. If `fallback_non_green_finalized`, treat release as exception lane.
4. If `failed`, inspect `session.log` and rerun from current state.
