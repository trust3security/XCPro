# IGC Docs Index

## Purpose

Canonical index for XCPro IGC file specification, mapping, and phased implementation.
All IGC planning and contract docs should be discoverable from this file.

## Canonical Documents

- [IGC spec for XCPro](./xcpro_igc_file_spec.md)
- [IGC format research and XCPro data mapping](./IGC_FILE_FORMAT_RESEARCH_AND_DATA_MAPPING_2026-03-08.md)
- [IGC file structure field reference](./IGC_FILE_STRUCTURE_FIELD_REFERENCE_2026-03-08.md)
- [IGC production-grade phased implementation plan](./CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md)
- [Production XCS / WeGlide compatibility profile](./PRODUCTION_COMPATIBILITY_PROFILE_XCS_WEGLIDE_2026-03-10.md)
- [IGC recovery release-grade phased plan](./CHANGE_PLAN_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md)
- [IGC recovery focused code pass review](./REVIEW_IGC_RECOVERY_FOCUSED_CODE_PASS_2026-03-09.md)
- [IGC recovery automation agent contract](./AGENT_AUTOMATION_CONTRACT_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md)
- [IGC Phase 7 automation agent contract](./AGENT_AUTOMATION_CONTRACT_IGC_PHASE7_RELEASE_GRADE_2026-03-10.md) - canonical autonomous entrypoint for full Phase 7 completion
- [IGC recovery execution log](./EXECUTION_LOG_IGC_RECOVERY_RELEASE_GRADE_2026-03-09.md)
- [IGC Phase 7 execution log](./EXECUTION_LOG_IGC_PHASE7_RELEASE_GRADE_2026-03-10.md)

## Phase 7 Evidence Templates

- [Phase 7 gate evidence](./phase7_evidence/phase7_gates.md)
- [Round-trip tolerance matrix](./phase7_evidence/phase7_roundtrip_tolerance_matrix.md)
- [External compatibility matrix](./phase7_evidence/phase7_external_compatibility_matrix.md)
- [Lint rule matrix](./phase7_evidence/phase7_lint_rule_matrix.md)
- [Parser/lint parity matrix](./phase7_evidence/phase7_parser_lint_parity_matrix.md)
- [Error taxonomy mapping](./phase7_evidence/phase7_error_taxonomy_mapping.md)
- [Phase 7 manual checklist](./phase7_evidence/phase7_manual_checklist.md)
- External runner: `docs/IGC/phase7_evidence/phase7_external_compatibility_check.py`

## Canonical Fixture

- `docs/IGC/example.igc` is the canonical real-world sample fixture provided for production-shape validation.
- Test runtime fixture copy for automated parser/regression tests:
  - `feature/igc/src/test/resources/replay/example-production.igc`

### IGC Fixture Utilities

- Generate a deterministic signed upload fixture locally:
  - `python docs/IGC/phase7_evidence/generate_igc_fixture.py`
  - Default timestamp policy:
    - previous UTC date, `09:00:00` UTC start time
  - Explicit WeGlide/XCS generation:
    - `python docs/IGC/phase7_evidence/generate_igc_fixture.py --profile weglide --manufacturer XCS --logger-id AAA`
  - Unsigned XCPro parser fixture:
    - `python docs/IGC/phase7_evidence/generate_igc_fixture.py --profile xcpro --signer none`
  - Output file:
    - `docs/IGC/phase7_evidence/fixtures/generated_phase7_basic.igc`
- Validate the generated fixture with the external compatibility runner:
  - `python docs/IGC/phase7_evidence/phase7_external_compatibility_check.py --fixture "generated::docs/IGC/phase7_evidence/fixtures/generated_phase7_basic.igc"`

## Production Compatibility Notes

- The fixture generator is not the production app export path.
- Actual app exports now use the production compatibility contract documented in:
  - [Production XCS / WeGlide compatibility profile](./PRODUCTION_COMPATIBILITY_PROFILE_XCS_WEGLIDE_2026-03-10.md)
- Current production app behavior:
  - `A` record manufacturer `XCS`
  - `HFFTYFRTYPE:XCPro,SignedMobile`
  - `G` records appended during finalize and re-applied during recovery
- Future agents debugging upload compatibility must inspect app code first, not
  just `docs/IGC/phase7_evidence/generate_igc_fixture.py`.

## Phase 5 Evidence Templates

- `docs/IGC/phase5_evidence/phase5_gates.md`
- `docs/IGC/phase5_evidence/phase5_manual_checklist.md`
- `docs/IGC/phase5_evidence/phase5_naming_collision_matrix.md`
- `docs/IGC/phase5_evidence/phase5_share_uri_grants.md`

## Recovery Phase 6 Evidence

- `docs/IGC/phase6_evidence/phase6_recovery_kill_matrix.md`
- `docs/IGC/phase6_evidence/phase6_gates.md`
- `docs/IGC/phase6_evidence/phase6_manual_checklist.md`

## Phase 0 Closeout Checklist

- [x] Freeze IGC record contract and XCPro field mapping.
- [x] Add plan and research docs to an explicit IGC index (`docs/IGC/README.md`).
- [x] Add baseline regression fixture path for known replay/IGC files.
- [x] Keep canonical spec location in `docs/IGC` and remove duplicate non-canonical copy.
- [x] Normalize `xcpro_igc_file_spec.md` punctuation/encoding artifacts.
- [x] Run and record required gates:
  - `python scripts/arch_gate.py`
  - `./gradlew enforceRules`

## Phase 0 Gate Evidence (2026-03-09)

- `python scripts/arch_gate.py`
  - Result: `ARCH GATE PASSED`
- `./gradlew enforceRules`
  - Result: `Rule enforcement passed.`

## Notes

- User-visible IGC archive target remains `Downloads/XCPro/IGC/`.
- In-progress writer staging remains app-private (`files/igc/staging/`).
- Recovery review and release-grade hardening documents in this directory are
  recovery-slice only; retention and privacy remain separate workstreams.
