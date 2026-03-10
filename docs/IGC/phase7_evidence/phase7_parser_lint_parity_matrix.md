# Phase 7 Parser/Lint Parity Matrix

Purpose:

- prove internal parser behavior is consistent with lint authority for known edge conditions

## Parity Cases

| Case | Lint expectation | Parser expectation | Test evidence |
| --- | --- | --- | --- |
| `A` not first | ISSUE: `A_RECORD_NOT_FIRST` | parser still reads points | `IgcParserLintParityTest.aRecordNotFirst_isRejectedByLint_evenWhenParserCanStillReadPoints` |
| Invalid `I` range start below writer floor | ISSUE: `I_RECORD_EXTENSION_RANGE_INVALID` | parser ignores extension values | `IgcParserLintParityTest.invalidIRecord_isRejectedByLint_andIgnoredByParserExtensions` |
| Invalid `I` declaration count/shape | ISSUE: extension count/range invalid | parser returns 0 extension values for mismatched entries | `IgcParserTest.parse_rejectsIExtensionsBelowWriterFloor` |
| Non-CRLF line endings | ISSUE: `LINE_ENDING_NOT_CRLF` | parser historically tolerant of raw newline variants | `StrictIgcLintValidatorTest.validate_rejectsNonCanonicalLineEndings`, `IgcTextWriterTest.writeLines_writesCanonicalCrlfOnly` |

## Boundary Enforcement

- Parser change recorded in `IgcParser.kt`:
  - removed blanket `trim()`
  - raised `I` extension start floor to 36
  - requires exact `I` extension width and byte continuity
- Lint remains the authoritative source of strictness failures in finalize path.
- Finalize export short-circuits on any lint findings before staging/publish.
