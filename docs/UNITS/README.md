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

## Why SI Is Non-Negotiable In XCPro

XCPro combines competition geometry and high-rate sensor fusion where unit drift causes silent correctness failures.

Core domains already align with SI:
- FAI task geometry and scoring distances (meters/kilometers semantics).
- Turnpoint cylinders (for example 500 m radius) and finish zones (for example >= 3 km).
- GPS distance/position deltas in meters.
- Barometer to altitude pipelines in meters.
- Accelerometers in m/s^2.
- Variometer/STF/wind vectors in m/s.

What mixed units break:
- Start-line and finish crossing detection.
- OZ/zone intersection logic.
- STF and polar interpolation paths.
- Near-miss/conflict distance logic.
- Task/scoring distance calculations.

Policy:
- Internal domain/fusion/task/replay calculations use SI only.
- km/h, knots, feet, NM, and miles are boundary-only (UI/protocol/file adapters).

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
- Re-pass #8 found unit-boundary leaks in production UI distance outputs (distance circles + task distance labels hard-coded to metric text).
- Polar domain still uses km/h internal data contracts.
- Overall status: Not compliant.
