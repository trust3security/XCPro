# Phase 7 External Compatibility Matrix

Purpose:

- prove at least two independent external parser checks against canonical and intentionally-invalid fixtures
- record fixture ID, parser output, and reasoned failure lines
- provide a reproducible command path for Phase 7 P7-5

Command:

- `python docs/IGC/phase7_evidence/phase7_external_compatibility_check.py --output-json docs/IGC/phase7_evidence/phase7_external_compatibility_results.json --output-markdown docs/IGC/phase7_evidence/phase7_external_compatibility_report.md`
  - Status: PASS (2026-03-10 10:28:54)

Fixture Set:

### Canonical Fixture

| Fixture ID | Path | Expected outcome | Notes |
| --- | --- | --- | --- |
| `production_real` | `feature/igc/src/test/resources/replay/example-production.igc` | mixed PASS/WARN | real-world export from XCPro test fixtures |

### Invalid Fixtures

| Fixture ID | Path | Expected failing line | Expected reason |
| --- | --- | --- | --- |
| `invalid_a_not_first` | `docs/IGC/phase7_evidence/fixtures/phase7_a_not_first.igc` | line 2 (order check) | policy failure for lint only; parser still reads points |
| `invalid_malformed_b_short` | `docs/IGC/phase7_evidence/fixtures/phase7_malformed_b_short.igc` | line 4 (`B120001...`) | malformed B payload; parser-specific hard fail expected |
| `invalid_bad_time` | `docs/IGC/phase7_evidence/fixtures/phase7_bad_time.igc` | line 4 (`B996000...`) | malformed time/payload; parser-specific hard fail expected |

Observed Results Snapshot:

| Fixture ID | Parser | Result | Record Count | Error summary |
| --- | --- | --- | --- | --- |
| `production_real` | `aerofiles` | WARN | 18969 | `MissingRecordsError` (satellite record summary warning) |
| `production_real` | `igc_parser` | PASS | 18969 | n/a |
| `invalid_a_not_first` | `aerofiles` | PASS | 3 | n/a |
| `invalid_a_not_first` | `igc_parser` | PASS | 3 | n/a |
| `invalid_malformed_b_short` | `aerofiles` | WARN | 2 | `MissingRecordsError` (fix_records parse warning) |
| `invalid_malformed_b_short` | `igc_parser` | FAIL | n/a | `InvalidTrackPointLine` |
| `invalid_bad_time` | `aerofiles` | WARN | 2 | `MissingRecordsError` (fix_records parse warning) |
| `invalid_bad_time` | `igc_parser` | FAIL | n/a | `InvalidTrackPointLine` |

Interpretation:

- The two parsers show intentional divergence by design: `igc_parser` is strict on malformed B points while `aerofiles` can emit record-level warnings and continue.
- This supports the Phase 7 compatibility scope requirement of at least two independent external checks and explicit reason capture.

Evidence Files:

- Runner script: `docs/IGC/phase7_evidence/phase7_external_compatibility_check.py`
- Repro output target: `docs/IGC/phase7_evidence/phase7_external_compatibility_results.json`
- Markdown report: `docs/IGC/phase7_evidence/phase7_external_compatibility_report.md`
- Fixtures:
  - `docs/IGC/phase7_evidence/fixtures/phase7_a_not_first.igc`
  - `docs/IGC/phase7_evidence/fixtures/phase7_malformed_b_short.igc`
  - `docs/IGC/phase7_evidence/fixtures/phase7_bad_time.igc`
