# Phase 5 Manual UX Checklist

Date: 2026-03-09
Device/API: Pending (no connected device in this environment)
Build: debug
Tester: Codex (automation pre-pass)

## IGC Files Entry and Label

- [x] File-management entry label is `IGC Files`. (code + instrumentation contract test)
- [x] No file-management entry label remains as `IGC Replay`. (checked in updated settings entry)
- [x] Replay is available as a per-file action in IGC Files. (row action retained)

## Post-Flight Retrieval

- [ ] Completed flight produces one finalized `.IGC` file.
- [ ] File is visible in IGC Files list without app restart.
- [ ] File path resolves under `Downloads/XCPro/IGC/` via MediaStore metadata.

## List/Search/Sort

- [x] List loads with expected metadata (name/date/size). (automated repository/list tests)
- [x] Search by filename works. (automated list/use-case tests)
- [x] Sort by date and name works. (automated list/use-case tests)

## File Actions

- [x] Share action opens chooser and includes a readable `content://` URI. (intent contract test)
- [ ] Email quick action opens mail-capable target with attachment URI. (manual target validation pending)
- [ ] Generic upload/share target flow works (WeGlide-capable via chooser). (manual target validation pending)
- [x] Copy-to action (`ACTION_CREATE_DOCUMENT`) writes a valid output file. (copy-to path automated)
- [x] Replay-open action opens replay flow for selected file. (viewmodel replay-open instrumentation test)
- [x] Copy metadata action copies expected details. (unit contract coverage)

## Failure States

- [x] Permission denied shows actionable message. (share launch failure mapping)
- [ ] Missing share target shows actionable message.
- [ ] Missing/deleted file row shows actionable message.
- [ ] Copy destination write failure shows actionable message.
- [x] Internal reason codes are logged for each failure class. (typed repository error codes)

## Sign-off

- [ ] Checklist complete with no blocking failures.
- [ ] Screenshots/log references attached.
