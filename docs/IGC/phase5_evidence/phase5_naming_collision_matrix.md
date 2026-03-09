# Phase 5 Naming and Collision Matrix

Date: 2026-03-09
Owner: Codex

Filename contract: `YYYY-MM-DD-MMM-XXXXXX-FF.IGC`

## Deterministic Cases

| Case ID | Input condition | Existing same-day files | Expected filename result | Pass/Fail |
|---|---|---|---|---|
| NC-01 | first valid `B` on UTC day D | none | `...-01.IGC` | Pass (`IgcFileNamingPolicyTest`) |
| NC-02 | same day, one prior file | `...-01.IGC` | `...-02.IGC` | Pass (`IgcFileNamingPolicyTest`) |
| NC-03 | sparse slots | `...-01.IGC`, `...-03.IGC` | `...-02.IGC` | Pass (`IgcFileNamingPolicyTest`) |
| NC-04 | no valid `B` fallback | none | uses session-start UTC day with `...-01.IGC` | Pass (`IgcFileNamingPolicyTest`) |
| NC-05 | timezone boundary near UTC midnight | varies | UTC-day-consistent result | Pass (UTC conversion policy + covered by UTC date resolver path) |
| NC-06 | FF exhausted | `...-01` to `...-99` | typed error `IGC_NAME_SPACE_EXHAUSTED` | Pass (`IgcFileNamingPolicyTest`) |

## Serial and Prefix Checks

- [x] `MMM` uses recorder manufacturer contract value.
- [x] `XXXXXX` serial is deterministic and matches session serial source.
- [x] extension is uppercase `.IGC`.

## Collision Algorithm Checks

- [x] repository queries same-day names in `Downloads/XCPro/IGC/`.
- [x] lowest free `FF` is selected.
- [x] no duplicate filename emitted across repeated finalize attempts.
- [x] finalize idempotency prevents second publish for same `sessionId`.
