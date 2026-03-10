# Phase 7 Manual Checklist

## Release-Grade Finalization and UX Checks

- [x] Confirm finalize diagnostics are surfaced from `IgcExportDiagnosticsRepository` in list flow.
- [x] Confirm typed finalize failures are no longer exception-only (typed `IgcFinalizeResult.Failure` flows end-to-end).
- [ ] Confirm user-visible failure copy for at least one lint failure on a real generated file.
- [x] Confirm external compatibility runner executes and captures JSON + markdown artifact outputs.
- [x] Generate and validate a deterministic fixture file for manual confirmation:
  - `docs/IGC/phase7_evidence/fixtures/generated_phase7_basic.igc`
- [x] Confirm `phase7_evidence` artifacts are linked from `docs/IGC/README.md`.
- [ ] Run full P7-7 verification order and record consecutive-pass output.
- [ ] Confirm `phase7_evidence` artifacts include command outputs and failure reason capture from each command in this phase.

## External Compatibility Focus

- [x] Compatibility runner script exists with reproducible fixture list.
- [x] Invalid fixture set includes expected failure lines and reasoned coverage.
- [ ] Re-run runner in final packaging state and freeze versioned artifact snapshots.

## Documentation Focus

- [x] Add required phase7 evidence file set.
- [ ] Verify scorecard and gate files are synchronized after P7-7/P7-8 completion.
