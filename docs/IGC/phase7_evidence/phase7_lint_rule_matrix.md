# Phase 7 Lint Rule Matrix

Purpose:

- map active `IgcLintRuleSet.Phase7Strict` rules to implementation, diagnostics, and tests

| Rule | Engine | Severity | Evidence |
| --- | --- | --- | --- |
| `FILE_NOT_EMPTY` | `StrictIgcLintValidator` | ERROR | `StrictIgcLintValidatorTest.validate_rejects...` |
| `A_RECORD_FIRST` | `StrictIgcLintValidator` | ERROR | `StrictIgcLintValidatorTest.validate_rejectsARecordNotFirst`, `IgcParserLintParityTest.aRecordNotFirst_isRejectedByLint_evenWhenParserCanStillReadPoints` |
| `B_RECORD_NO_SPACES` | `StrictIgcLintValidator` | ERROR | `StrictIgcLintValidatorTest` (string-based payload scenarios) |
| `I_RECORD_NO_SPACES` | `StrictIgcLintValidator` | ERROR | `StrictIgcLintValidator` rule-set coverage and boundary tests |
| `B_RECORD_UTC_MONOTONIC` | `StrictIgcLintValidator` | ERROR | `StrictIgcLintValidatorTest.validate_rejectsNonMonotonicBRecordTimes` |
| `CANONICAL_CRLF_LINE_ENDINGS` | `StrictIgcLintValidator` | ERROR | `StrictIgcLintValidatorTest.validate_rejectsNonCanonicalLineEndings`, writer smoke via `IgcTextWriterTest.writeLines_writesCanonicalCrlfOnly` |
| `I_RECORD_EXTENSION_COUNT_MATCHES_DECLARATION` | `StrictIgcLintValidator` | ERROR | `IRecord` count checks in `StrictIgcLintValidator` |
| `I_RECORD_EXTENSION_BYTE_RANGE_VALID` | `StrictIgcLintValidator` | ERROR | `StrictIgcLintValidatorTest.validate_rejectsInvalidIRecordExtensionStartBelowWriterFloor`, `IgcParser` hardening |
| `I_RECORD_EXTENSION_BYTE_RANGE_NON_OVERLAPPING` | `StrictIgcLintValidator` | ERROR | `StrictIgcLintValidator` overlapping-range validation path |

## Rule Set Ownership

- Rule set declaration: `IgcLintRuleSet.Phase7Strict` in `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcLintRuleSet.kt`
- Validator entrypoint: `StrictIgcLintValidator` in `feature/igc/src/main/java/com/example/xcpro/igc/domain/StrictIgcLintValidator.kt`
- Finalize integration: `IgcExportValidationAdapter` in `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcExportValidationAdapter.kt`
- UI message contract: `IgcLintMessageMapper` in `feature/igc/src/main/java/com/example/xcpro/igc/usecase/IgcLintMessageMapper.kt`
