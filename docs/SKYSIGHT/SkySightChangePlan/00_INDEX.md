# SkySight Integration - Index

This folder contains everything needed to implement SkySight forecast overlays in XCPro without guessing.

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
    supported options, and XCPro architecture guidance.

## 11) Multi overlay execution contract
- `14_SKYSIGHT_MULTI_OVERLAY_EXECUTION_CONTRACT.md`
  - Phase-by-phase implementation contract for adding primary+wind concurrent
    overlay behavior in XCPro with architecture/test gates.

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

---

## Quick "what to do today"
1) Implement Stage A:
   - Follow `TRACK_A_FAKE_FORECAST_PROVIDER.md`
2) Capture SkySight evidence pack:
   - Follow `SKYSIGHT_EVIDENCE_CAPTURE.md`
3) Only then implement SkySight adapter:
   - Follow `SKYSIGHT_API_CONTRACT_REQUIRED.md`
