# SkySight Integration - Index

This folder contains everything needed to implement SkySight forecast overlays in XCPro without guessing.

## Current status (2026-02-25)
- Stage A is complete in code.
- Stage B is complete in code and bound in DI (`SkySightForecastProviderAdapter`).
- Evidence pack exists at `docs/integrations/skysight/evidence/`.
- SkySight satellite overlay runtime is implemented:
  - API-backed satellite imagery/radar/lightning overlays in map runtime.
  - SkySight tab controls for layer toggles, animation, and history-frame count.
  - `Sat View` remains as a separate transient map-style toggle.
  - OGN glider readability coupling is implemented:
    - when satellite overlays are active, glider icons use white-contrast mode.
    - icon changes refresh immediately on mode transition, then return to normal OGN update cadence.

## 1) Execution order (read this first)
- `SKYSIGHT_MVP_EXECUTION_ORDER.md`

## 2) Stage A (can be implemented immediately)
Stage A builds the provider-neutral forecast overlay architecture and a Fake provider so XCPro's map overlay wiring is complete before SkySight endpoints are known.

- `TRACK_A_FAKE_FORECAST_PROVIDER.md`

## 3) Stage B gate (do not proceed without evidence)
Stage B implements the real SkySight provider adapter. It is blocked until the API contract evidence pack exists.

- `SKYSIGHT_API_CONTRACT_REQUIRED.md`

## 4) Evidence capture (how to obtain the real contract)
Short guide:
- `SKYSIGHT_EVIDENCE_CAPTURE.md`

Full guide (recommended):
- `10_SKYSIGHT_API_CONTRACT_UNBLOCK_MVP.md`

Evidence files live here (commit redacted):
- `docs/integrations/skysight/evidence/`

## 5) Autonomous agent execution contract (Codex / agent runner)
- `Agent-Execution-Contract-SkySight-AUTONOMOUS.md`

## 6) Risks and gates checklist
- `SKYSIGHT_RISK_AND_GATES.md`

## 7) Repo-wide Codex instructions
`AGENTS.md` at repo root points Codex to these docs and enforces the Stage A -> Stage B rule.

## 8) Stage B implementation plan (after unblock)
- `12_STAGE_B_IMPLEMENTATION_PLAN.md`
  - This is the Stage B change-plan artifact aligned to `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`.

## 9) Wind display modes plan
- `13_WIND_DISPLAY_MODES_IMPLEMENTATION_PLAN.md`
  - Detailed implementation plan for selectable wind rendering modes (arrow/barb/dot)
    using the existing SkySight wind vector contract.

## 10) Multi overlay research notes
- `../SKYSIGHT_MULTI_OVERLAY_IMPLEMENTATION_NOTES.md`
  - Research-backed notes on SkySight simultaneous overlays (primary + wind),
    plus XCPro extended multi-overlay contract notes and known regression status.

## 11) Multi overlay execution contract
- `14_SKYSIGHT_MULTI_OVERLAY_EXECUTION_CONTRACT.md`
  - Phase-by-phase implementation contract for primary + secondary non-wind +
    optional wind concurrent overlay behavior in XCPro with architecture/test gates.

## 12) Arrow speed color plan
- `15_WIND_ARROW_SPEED_COLOR_IMPLEMENTATION_PLAN.md`
  - Implementation plan for legend-aligned wind-speed color coding in ARROW mode,
    while keeping BARB mode unchanged.

## 13) Convergence availability and implementation notes
- `16_CONVERGENCE_AVAILABILITY_AND_IMPLEMENTATION_NOTES.md`
  - Verified `wblmaxmin` convergence availability in authenticated SkySight
    artifacts and defines the XCPro implementation path plus point-value caveat.

## 14) Convergence implementation plan
- `17_CONVERGENCE_IMPLEMENTATION_PLAN.md`
  - Comprehensive phase-by-phase plan to implement convergence in XCPro with
    architecture boundaries, tests, acceptance criteria, and rollback.

## 15) Satellite overlay implementation plan
- `18_SATELLITE_OVERLAY_IMPLEMENTATION_PLAN.md`
  - Architecture-compliant SkySight satellite overlay plan and implementation
    details (satellite/radar/lightning layers + time-step behavior).

---

## Quick "what to do today"
1) If changing forecast contracts:
   - Start from `SKYSIGHT_API_CONTRACT_REQUIRED.md` and refresh the evidence pack first.
2) If changing satellite layer behavior or options:
   - Follow `18_SATELLITE_OVERLAY_IMPLEMENTATION_PLAN.md`.
   - Keep `09_SKYSIGHT_API_CONTRACT_DETAILS.md` in sync with captured tile contract evidence.
3) Keep architecture docs in sync for pipeline/runtime wiring updates.
