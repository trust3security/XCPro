# Condor Docs Index

This folder is the active Condor planning and architecture pack for XCPro.

Current focus:

- Condor 2 first
- full live-source integration, not replay emulation
- ownship must come from fused live `flightData.gps`
- replay binder must remain replay-only

## Start here

1. `01_CONDOR2_FULL_INTEGRATION_PHASED_IP_2026-04-18.md`
2. `02_CONDOR2_BOUNDARIES_AND_SOURCE_ROUTING_2026-04-18.md`
3. `00_CONDOR2_RESEARCH_REFERENCE_2026-04-18.md`
4. `03_CONDOR2_PC_BRIDGE_AND_CAPTURE_PLAN_2026-04-18.md`
5. `04_CONDOR2_VERIFICATION_AND_QA_2026-04-18.md`
6. `05_CONDOR2_ARCHITECTURE_DECISION_DRAFT_2026-04-18.md`

## Current status

- Condor is not implemented in XCPro today.
- The current live GPS owner is phone GPS via `UnifiedSensorManager`.
- The map already renders live ownship from fused `flightData.gps`, not from a
  map-only GPS source.
- Foreground service startup and map GPS status are still phone-permission and
  phone-sensor oriented.

## Key decisions in this pack

- `CONDOR2_FULL` is a live runtime mode, not a replay mode.
- Condor ownship must enter the existing fused live pipeline.
- `feature:simulator` is the target owner for Condor transport and parser
  runtime.
- The persisted desired live mode must live outside runtime orchestration.
- `feature:flight-runtime` should expose narrow live-source seams, not a
  dependency-bag style runtime bundle.
- IGC and WeGlide policy stays in their own modules; runtime exposes source
  classification, not upload policy.

## File guide

- `00_CONDOR2_RESEARCH_REFERENCE_2026-04-18.md`
  - research summary and local code seams
- `01_CONDOR2_FULL_INTEGRATION_PHASED_IP_2026-04-18.md`
  - implementation-ready phased plan
- `02_CONDOR2_BOUNDARIES_AND_SOURCE_ROUTING_2026-04-18.md`
  - authoritative seam, ownership, and routing rules
- `03_CONDOR2_PC_BRIDGE_AND_CAPTURE_PLAN_2026-04-18.md`
  - Windows Condor-to-Android bridge and fixture capture strategy
- `04_CONDOR2_VERIFICATION_AND_QA_2026-04-18.md`
  - unit, integration, replay, and manual QA expectations
- `05_CONDOR2_ARCHITECTURE_DECISION_DRAFT_2026-04-18.md`
  - ADR-ready draft for the durable boundary decisions

## Practical takeaway

If XCPro is running on Android while Condor 2 is flying at Lake Keepit, NSW,
the user should see Lake Keepit on the map only when Condor is the selected
live source feeding fused live `flightData.gps`.
