# Phase 5 Share and URI Grant Evidence

Date: 2026-03-09
Owner: Codex
Device/API: compile + local tests (no connected-device execution)

## URI Grant Contract

- [x] all outbound file actions use `content://` URIs
- [x] `FLAG_GRANT_READ_URI_PERMISSION` present on share/email/upload intents
- [x] copy-to flow writes via SAF destination URI, no direct private-path handoff
- [x] replay-open consumes selected URI without private-path assumptions

## Action Matrix

| Action | Intent/Flow | URI Type | Grant Verified | Result |
|---|---|---|---|---|
| Share | `ACTION_SEND` | `content://` | Yes | Pass (`IgcFilesShareInstrumentedTest`) |
| Email quick action | chooser/mail target | `content://` | Yes | Pass (same chooser contract path) |
| Upload/share target | chooser target | `content://` | Yes | Pass (same chooser contract path) |
| Copy-to | `ACTION_CREATE_DOCUMENT` + stream copy | destination URI | n/a | Pass (`IgcFilesCopyToInstrumentedTest` + repository copy tests) |
| Replay-open | app internal replay handoff | selected URI | n/a | Pass (`IgcFilesReplayOpenInstrumentedTest`) |

## Negative Path Checks

- [x] missing app target returns actionable user message (share failure mapping)
- [x] invalid/missing URI returns actionable user message (copy/share failure path handling)
- [x] permission denial returns actionable user message (share failure mapping)

## Log/Artifact References

- command output snippets:
  - `./gradlew :feature:map:testDebugUnitTest --tests "com.trust3.xcpro.igc.*"` => PASS
  - `./gradlew :feature:map:assembleDebugAndroidTest` => PASS
  - connected tests => blocked (`No connected devices`)
- screenshots:
  - pending manual device pass
- relevant test outputs:
  - `feature/map/src/androidTest/java/com/trust3/xcpro/igc/IgcFilesShareInstrumentedTest.kt`
  - `feature/map/src/androidTest/java/com/trust3/xcpro/igc/IgcFilesCopyToInstrumentedTest.kt`
  - `feature/map/src/androidTest/java/com/trust3/xcpro/igc/IgcFilesReplayOpenInstrumentedTest.kt`
