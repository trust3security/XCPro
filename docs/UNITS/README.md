# UNITS Docs

Date: 2026-02-22
Owner: Engineering
Status: In Progress

## Purpose
This folder tracks SI-unit compliance work across XCPro.
The target state is:
- Internal calculations use SI units only.
- Non-SI units exist only at explicit input/output boundaries.
- Unit contracts are documented and test-guarded.

## Document Index
- `CHANGE_PLAN_SI_UNITS_COMPLIANCE_2026-02-22.md`: End-to-end implementation plan.
- `SI_BOUNDARY_CONTRACTS_2026-02-22.md`: Current and target unit contracts by module.
- `EXECUTION_BACKLOG_SI_MIGRATION_2026-02-22.md`: Prioritized engineering tasks.
- `RISK_REGISTER_SI_MIGRATION_2026-02-22.md`: Known risks and mitigations.
- `VERIFICATION_MATRIX_SI_2026-02-22.md`: Verification gates and tests.
- `SI_REPASS_FINDINGS_2026-02-22.md`: Deep-pass audit and compliance status.

## Current Compliance Summary
- Flight/sensor fusion paths: Mostly SI compliant.
- ADS-B/OGN: SI internally with explicit boundary conversions.
- Replay: boundary conversion is SI-correct, but re-pass #6 found a movement snapshot distance contract bug (`distanceMeters` assigned from `speedMs`).
- Task/AAT/racing: Not SI compliant end-to-end.
- AAT contains active meter-vs-km correctness bugs; re-pass #7 extended `AATTaskQuickValidationEngine` defect scope to include `validateFinish`.
- Polar domain still uses km/h internal data contracts.
- Overall status: Not compliant.
