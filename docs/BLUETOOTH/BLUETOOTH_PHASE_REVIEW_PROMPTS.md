# Bluetooth Phase Review Prompts

Use these after Codex completes a phase.

Each prompt is meant for review only.
The reviewer must not implement new code in the review pass.
The reviewer should read the relevant repo docs and return PASS or FAIL with exact reasons.

These prompts follow the historical Phase 0 to Phase 6 rollout plan.
For current repo status, read `CURRENT_STATUS_BLUETOOTH_2026-04-11.md` first.

---

## Generic review prompt template

```text
Review the completed Bluetooth phase only.
Do not implement code. Review only.

Read first:
1. AGENTS.md
2. docs/ARCHITECTURE/ARCHITECTURE.md
3. docs/ARCHITECTURE/PIPELINE.md
4. CHANGE_PLAN_BLUETOOTH_LXNAV_S100_2026-04-09.md

Return:
Review Verdict: PASS or FAIL
- What the phase was supposed to do
- What was actually implemented
- Boundary compliance
- Ownership compliance
- Test sufficiency
- Runtime risks
- Exact fixes required before next phase

Be strict.
Fail the phase if transport/parser/arbitration leaked into feature:map or UI.
```

---

## Review prompt - Phase 0

```text
Review Bluetooth Phase 0 only.
Do not implement code. Review only.

Check that:
- the docs define one clear authoritative Bluetooth implementation contract
- scope and non-goals are explicit
- for the historical Phase 0 review only, no production Bluetooth wiring had landed yet
- the plan keeps profile as settings owner, variometer as external-vario runtime owner, and map as consumer-only
- the phase did not introduce broad rename churn

Return PASS or FAIL and exact corrections required.
```

---

## Review prompt - Phase 1

```text
Review Bluetooth Phase 1 only.
Do not implement code. Review only.

Check that:
- transport abstractions exist and are testable with fakes
- connection state and error models are explicit
- line framing is pure and unit-tested
- framing handles partial lines, multiple lines per read, and CRLF cleanup
- no LX parser logic was stuffed into transport
- no UI or map layer owns transport responsibilities

Return PASS or FAIL and exact corrections required.
```

---

## Review prompt - Phase 2

```text
Review Bluetooth Phase 2 only.
Do not implement code. Review only.

Check that:
- bonded-device RFCOMM/SPP transport is implemented cleanly
- there is no BLE or scan-flow scope creep
- Android permission use is minimal and correct for bonded-device connection
- reconnect logic exists and does not spam state/UI
- socket lifecycle is not owned by Composables, ViewModels, or map classes
- the implementation is still behind the transport seam from Phase 1

Return PASS or FAIL and exact corrections required.
```

---

## Review prompt - Phase 3

```text
Review Bluetooth Phase 3 only.
Do not implement code. Review only.

Check that:
- the LX parser is pure and unit-tested
- supported sentence subset is explicit and matches the plan
- checksum policy is explicit
- blank fields and malformed lines are tolerated safely
- parser outputs structured results, not UI-facing strings
- parser owns no source-selection, staleness, or UI policy

Return PASS or FAIL and exact corrections required.
```

---

## Review prompt - Phase 4

```text
Review Bluetooth Phase 4 only.
Do not implement code. Review only.

Check that:
- external instrument snapshot state has one runtime owner
- external-vs-phone field arbitration is explicit and centralized
- fallback on disconnect or staleness is implemented and tested
- there is no second app-wide fused-flight truth owner
- map remains a consumer only
- tests cover full override, partial override, disconnect fallback, and stale fallback

Return PASS or FAIL and exact corrections required.
```

---

## Review prompt - Phase 5

```text
Review Bluetooth Phase 5 only.
Do not implement code. Review only.

Check that:
- settings UI is owned by the correct settings-side module
- device selection and connect/disconnect are intent-driven through ViewModel/use-case paths
- diagnostics are read-only UI projections from runtime state
- no parser or socket logic was pushed into UI or ViewModel
- the feature is operable by a pilot without debug-only hacks

Return PASS or FAIL and exact corrections required.
```

---

## Review prompt - Phase 6

```text
Review Bluetooth Phase 6 only.
Do not implement code. Review only.

Check that:
- hardening covers malformed lines, reconnect behavior, and diagnostics counters
- real hardware validation evidence exists or the gap is explicitly documented
- sanitized capture fixtures were added if available
- PIPELINE.md was updated in the same change that landed final active wiring
- no temporary bypass remains as the production path

Return PASS or FAIL and exact corrections required.
```

---

## Stop conditions that should fail any phase

Fail the phase if any of the following happened:
- Bluetooth transport lives in `feature:map`
- parser logic lives in UI or ViewModel
- broad rename churn was introduced without delivering the phase value
- BLE or scanning was added without approved scope change
- declaration/writeback was mixed into read-only v1 work
- test coverage is missing for the core logic introduced by the phase
