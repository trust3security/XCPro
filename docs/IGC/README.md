# IGC Docs Index

## Purpose

Canonical index for XCPro IGC file specification, mapping, and phased implementation.
All IGC planning and contract docs should be discoverable from this file.

## Canonical Documents

- [IGC spec for XCPro](./xcpro_igc_file_spec.md)
- [IGC format research and XCPro data mapping](./IGC_FILE_FORMAT_RESEARCH_AND_DATA_MAPPING_2026-03-08.md)
- [IGC file structure field reference](./IGC_FILE_STRUCTURE_FIELD_REFERENCE_2026-03-08.md)
- [IGC production-grade phased implementation plan](./CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md)

## Canonical Fixture

- `docs/IGC/example.igc` is the canonical real-world sample fixture provided for production-shape validation.
- Test runtime fixture copy for automated parser/regression tests:
  - `feature/map/src/test/resources/replay/example-production.igc`

## Phase 5 Evidence Templates

- `docs/IGC/phase5_evidence/phase5_gates.md`
- `docs/IGC/phase5_evidence/phase5_manual_checklist.md`
- `docs/IGC/phase5_evidence/phase5_naming_collision_matrix.md`
- `docs/IGC/phase5_evidence/phase5_share_uri_grants.md`

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
