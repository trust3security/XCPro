# Codex Brief - XCPro Bluetooth - Phased Execution

Paste this to Codex when you want it to start or continue the Bluetooth work.

---

You are working in `trust3security/XCPro`.

Read first, in this order:
1. `AGENTS.md`
2. `docs/ARCHITECTURE/ARCHITECTURE.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `CURRENT_STATUS_BLUETOOTH_2026-04-11.md`
5. `CHANGE_PLAN_BLUETOOTH_LXNAV_S100_2026-04-09.md`

## Task

Implement the next approved Bluetooth slice only for LXNAV S100 support in XCPro.

Use the change plan as the implementation contract.
Do not invent a different architecture.
Do not widen scope.
Do not do broad renames.
Do not move ownership into `feature:map`.
Do not restart historical phases that are already implemented in the repo.

## Hard rules

- Keep XCPro's current MVVM + UDF + SSOT architecture.
- Treat XCSoar only as transport/protocol behavior reference, not app-architecture template.
- Keep Bluetooth transport separate from LX sentence parsing.
- Keep parser logic pure and unit-tested.
- Keep field ownership / override policy in one explicit use-case or selector, not in parser or UI.
- Keep `feature:profile` as settings UI owner.
- Keep `feature:variometer` as live external-vario runtime owner.
- Keep fused-flight truth in the existing runtime owner; do not create a second app-wide truth owner.
- Keep `feature:map` consumer-only for this work.
- No BLE in this phase unless the codebase already proves the device path is BLE.
- No scan flow in v1; bonded devices only.
- No task declaration or writeback in this slice.
- No `xcsoar` vendor string in production Kotlin.
- No blocking or socket logic in Composables or ViewModels.

## Execution mode

Work slice-by-slice.
Implement only the requested open slice.
Stop after that slice.
Do not continue automatically into later slices.

If no slice is specified, start with the first open item listed in
`CURRENT_STATUS_BLUETOOTH_2026-04-11.md`.

## Required output format

At the end of the phase, output exactly this structure:

### Phase Summary
- Phase completed
- What changed
- Files touched
- Tests run
- Results
- Risks / follow-ups
- Recommended next phase

### Verification
- Commands run
- Pass / fail result for each command

### Architecture check
- Owner kept in correct module: yes/no
- Any boundary risk introduced: yes/no
- Any TODOs left in production path: yes/no

## Required checks

Run after the phase:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Also run this when the phase touches lint-sensitive Android code:

```bash
./gradlew lintDebug
```

If a command fails:
- fix the root cause,
- rerun the failed command,
- rerun the full gate set,
- only then report completion.

## Scope reminders by phase

The phase reminders below are the historical rollout plan. Check
`CURRENT_STATUS_BLUETOOTH_2026-04-11.md` first because some of these phases are
already implemented.

### Phase 0
Historical baseline only.
Do not restart this phase unless updating docs.

### Phase 1
Transport contracts and line framing only.
No full Android socket ownership leaks into UI.

### Phase 2
Bonded-device RFCOMM/SPP transport only.
No BLE.
No scan flow.

### Phase 3
Pure LX parser and snapshot building.
No source arbitration in parser.

### Phase 4
Runtime repository and external-vs-phone arbitration.
No second flight-data truth owner.

### Phase 5
Settings UI and diagnostics only.
No UI-owned transport implementation.

### Phase 6
Hardening, real hardware validation, and doc sync.

## One mistake that fails review

If this phase pushes Bluetooth transport, parsing, or data arbitration into `feature:map`, the phase fails.

Now implement the next approved phase only.
