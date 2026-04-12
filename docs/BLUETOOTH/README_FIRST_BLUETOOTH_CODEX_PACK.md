# XCPro Bluetooth Codex Pack - Read This First

Date: 2026-04-09
Owner: draft for XCPro team
Status: Historical phased execution pack with current-state overlay
Target repo: trust3security/XCPro

## Purpose

This pack gives Codex a clean, low-churn execution path for adding LXNAV S100 Bluetooth support to XCPro.

As of 2026-04-11, this pack is no longer describing a pre-implementation repo.
Production Bluetooth wiring has already landed in code. Read
`CURRENT_STATUS_BLUETOOTH_2026-04-11.md` before using the older phase docs below.

It is intentionally written to match the way XCPro has been handling non-trivial work:
- explicit architecture contract first,
- phase-by-phase execution,
- small reviewable slices,
- no hidden ownership changes,
- no feature logic dumped into `feature:map`.

This pack assumes the approved direction is:
- keep XCPro's current MVVM + UDF + SSOT architecture,
- treat XCSoar only as transport/protocol reference behavior,
- keep Bluetooth transport separate from LX sentence parsing,
- integrate external instrument data through the existing runtime ownership model.

## File manifest

1. `CURRENT_STATUS_BLUETOOTH_2026-04-11.md`
   - The authoritative current repo-status note.
   - Read this first.

2. `CHANGE_PLAN_BLUETOOTH_LXNAV_S100_2026-04-09.md`
   - The master implementation contract.
   - This is the main document Codex must follow.

3. `PHASE0_BASELINE_AND_BOUNDARIES.md`
   - Phase 0 baseline audit and ownership freeze.
   - Historical snapshot of the repo before Bluetooth production wiring landed.

4. `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`
   - Follow-up implementation slice for LX TAS/DF-card wiring and settings metadata.

5. `GLO2.md`
   - Garmin GLO 2 external GNSS note.
   - Records confirmed behavior, likely sentence families, current XCPro seams,
     and recommended UI/status text for future implementation work.

6. `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`
   - The copy-paste brief to start Codex.
   - Updated so future work does not restart historical phases by mistake.

7. `BLUETOOTH_PHASE_REVIEW_PROMPTS.md`
   - Copy-paste review prompts for pass/fail checks after each phase.
   - Use these after Codex finishes a phase.

8. `RAW_CAPTURE_AND_HARDWARE_VALIDATION_BLUETOOTH.md`
   - Bench validation plan for a real S100.
   - Use before final signoff and to expand parser fixtures.

9. `fixtures/README.md`
   - Docs-only placeholder for future sanitized raw-capture fixtures.
   - No production code or tests consume it in Phase 0.

## Current repo summary on 2026-04-11

- Phases 1 to 5 have materially landed in production code.
- Pressure altitude and TE vario are already wired into fused runtime.
- Settings UI already supports bonded-device selection and connect/disconnect.
- The main open LX S100 gaps are:
  - TAS to DF-cards
  - parsed device metadata in Bluetooth settings
  - final hardware validation and fixture evidence

## Historical execution order

The sequence below is the original intended rollout order. It is still useful as
design history, but it should not be read as a claim that the repo is still at
Phase 0 today.

### PR 1 - Docs and baseline only
- Add the change plan.
- Refresh the pack docs so they match the actual repo layout.
- Freeze ownership boundaries for later phases.
- Add baseline notes and fixture placeholders if needed.
- Historical only. Production Bluetooth wiring now exists.

### PR 2 - Transport contracts and line framing
- Define Bluetooth transport abstractions.
- Define connection state and diagnostics models.
- Add pure line buffering/framing tests.

### PR 3 - Android SPP transport
- Implement bonded-device RFCOMM connection.
- No scanning.
- No BLE.
- No parser ownership leaks into UI.

### PR 4 - LXNAV parser and external snapshot repository
- Implement pure sentence parser.
- Build external snapshot state.
- Add ownership policy tests.

### PR 5 - Settings UI and runtime bridge
- Surface paired device selection and connect/disconnect.
- Show connection state and diagnostics.
- Bridge external baro/vario into runtime selection policy.

### PR 6 - Hardening and hardware validation
- Malformed-line hardening.
- Reconnect stability.
- Metrics.
- Real S100 raw capture validation.
- `PIPELINE.md` sync in the same PR that lands the final active wiring.

## How to use this pack with Codex

### Best workflow
1. Start with `CURRENT_STATUS_BLUETOOTH_2026-04-11.md`.
2. Use `CHANGE_PLAN_BLUETOOTH_LXNAV_S100_2026-04-09.md` for architecture and ownership rules.
3. If working on LX S100 card/settings follow-up, use `01_LX_S100_DF_CARD_WIRING_PLAN_2026-04-10.md`.
4. Paste the brief from `CODEX_BRIEF_BLUETOOTH_PHASED_EXECUTION.md`.
5. Tell Codex the exact open slice to implement next.
6. Run the matching review prompt from `BLUETOOTH_PHASE_REVIEW_PROMPTS.md` if doing a phase review.

This is the lowest-risk path.

## Non-negotiables

- No ad hoc Bluetooth logic inside map Composables or map managers.
- No parser logic in UI or ViewModels.
- No new permanent runtime ownership in `feature:map`.
- No sweeping rename of existing `hawk` surfaces in this slice.
- No BLE unless a real device capture proves SPP is wrong.
- No Bluetooth scan flow in v1; use bonded devices only.
- No writeback/control path in v1 (no declaration, no MC/bugs/ballast writeback yet).

## Required checks after each implementation phase

Prefer the repo-standard gates:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When the phase touches lint-sensitive Android code, also run:

```bash
./gradlew lintDebug
```

## One architectural rule to enforce hard

Do not let Bluetooth support become a disguised patch to `feature:map`.

If the Bluetooth feature only works because map code owns the transport, parser, or external-value arbitration, the design is already wrong.
