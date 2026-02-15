# SkySight Integration — Index

This folder contains everything needed to implement SkySight forecast overlays in XCPro **without guessing**.

## 1) Execution order (read this first)
- `SKYSIGHT_MVP_EXECUTION_ORDER.md`

## 2) Stage A (can be implemented immediately)
Stage A builds the provider-neutral forecast overlay architecture and a Fake provider so XCPro’s map overlay wiring is complete before SkySight endpoints are known.

- `TRACK_A_FAKE_FORECAST_PROVIDER.md`

## 3) Stage B gate (do not proceed without evidence)
Stage B implements the real SkySight provider adapter. It is **blocked** until the API contract evidence pack exists.

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
`AGENTS.md` at repo root points Codex to these docs and enforces the Stage A → Stage B rule.

## 8) Stage B implementation plan (after unblock)
- `12_STAGE_B_IMPLEMENTATION_PLAN.md`
  - This is the Stage B change-plan artifact aligned to `docs/ARCHITECTURE/CHANGE_PLAN_TEMPLATE.md`.

---

## Quick “what to do today”
1) Implement Stage A:
   - Follow `TRACK_A_FAKE_FORECAST_PROVIDER.md`
2) Capture SkySight evidence pack:
   - Follow `SKYSIGHT_EVIDENCE_CAPTURE.md`
3) Only then implement SkySight adapter:
   - Follow `SKYSIGHT_API_CONTRACT_REQUIRED.md`
