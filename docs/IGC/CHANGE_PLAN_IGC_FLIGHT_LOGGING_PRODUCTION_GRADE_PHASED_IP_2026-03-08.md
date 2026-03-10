# CHANGE_PLAN_IGC_FLIGHT_LOGGING_PRODUCTION_GRADE_PHASED_IP_2026-03-08.md

## Purpose

Define a production-grade phased implementation plan for XCPro to:

1. capture required flight/application/sensor data,
2. generate valid IGC flight files,
3. persist one file per flight, and
4. let users download/share logs after flight.

This plan is derived from:

- `docs/IGC/IGC_FILE_FORMAT_RESEARCH_AND_DATA_MAPPING_2026-03-08.md`
- `docs/IGC/IGC_FILE_STRUCTURE_FIELD_REFERENCE_2026-03-08.md`
- `docs/IGC/xcpro_igc_file_spec.md`
- existing architecture contracts

## 0) Metadata

- Title: IGC Flight Logging Production Grade Phased IP
- Owner: XCPro Team
- Date: 2026-03-09
- Status: Proposed (spec-aligned update) with 2026-03-10 production compatibility addendum
- Scope: new IGC recording/export pipeline (not replay parser replacement)
- Dependencies:
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`
  - `docs/IGC/IGC_FILE_FORMAT_RESEARCH_AND_DATA_MAPPING_2026-03-08.md`
  - `docs/IGC/IGC_FILE_STRUCTURE_FIELD_REFERENCE_2026-03-08.md`
  - `docs/IGC/xcpro_igc_file_spec.md`

## 0.1) 2026-03-10 Production Compatibility Addendum

Historical sections of this plan describe `G` signing as Phase 8-only and treat
`XCP` as the baseline production manufacturer code.

That is no longer the current production implementation.

As of 2026-03-10, the actual app export path now emits a signed `XCS`
compatibility profile in production code for upload interoperability hardening.

Canonical implementation note:

- `docs/IGC/PRODUCTION_COMPATIBILITY_PROFILE_XCS_WEGLIDE_2026-03-10.md`

Use the addendum plus current code as the source of truth when older plan
sections disagree.

## 1) Success Contract (Areas 1-10, each >=95/100)

Scoring rubric per area:

- Spec coverage and behavior parity: 40
- Automated test coverage depth: 30
- Determinism/timebase and architecture compliance: 20
- Operational hardening and docs sync: 10

Areas:

1. IGC record-format conformance (`A/H/I/B/E/L`, optional `C/F/G` policy, CRLF contract, fixed widths)
2. Session lifecycle correctness (arm/start/stop/finalize)
3. B-record correctness (UTC, coordinate conversion carry rules, dropout behavior, altitudes)
4. Header metadata completeness and profile mapping (`NIL`/`NKN`, altitude datum headers)
5. Task and event export correctness (`C`/`E` mapping)
6. Persistence and IGC Files UX reliability (spec naming + daily flight index contract)
7. Crash recovery and idempotent finalize behavior
8. Parser round-trip and external interoperability confidence
9. Performance and battery impact under long flights
10. Release/CI readiness and operator diagnostics

Release blocked unless all 10 areas are >=95.

## 2) Scope

In scope:

- Live-flight IGC recording pipeline.
- One flight per file, finalized post-flight.
- User-visible list/download/share flow.
- Validation and regression test net for IGC writer behavior.

Out of scope for baseline:

- Replacing `IgcParser` replay flow.
- Claiming IGC-approved recorder status without approved security/signature chain.
- Full approved-recorder trust/distribution program beyond the implemented
  compatibility export profile.

### 2.1 Compliance Profiles (Explicit)

- Profile A (historical baseline): analysis-grade/professional export (`A/H/B`
  mandatory; `I/F/C/E/L` optional).
- Profile B (implemented compatibility profile as of 2026-03-10): signed
  `XCS` export with `G` records in the production finalize/recovery path.
- Product copy must not equate the compatibility profile with an approved
  FAI/CIVL recorder program unless a formal trust/distribution path exists.

## 3) Architecture Contract

Non-negotiables:

- Preserve MVVM + UDF + SSOT layering.
- Domain logic in use-cases/engines only.
- No ViewModel file I/O.
- Injected time source only (`Clock`), no direct wall/system clock in domain.
- Replay determinism unchanged.

Dependency direction:

- UI -> domain -> data

### 3.1 SSOT Ownership

| Data item | SSOT owner | Exposed as | Forbidden duplicates |
|---|---|---|---|
| in-flight recording state | new IGC recording repository/runtime | `StateFlow<IgcRecordingState>` | UI-managed recording flags |
| latest flight sample for logging | `FlightDataRepository` | `StateFlow<CompleteFlightData?>` | ad-hoc sensor mirrors |
| flight start/land gating | `FlightStateRepository` | `StateFlow<FlyingState>` | UI heuristics for takeoff/landing |
| finalized IGC downloads index | IGC downloads repository | `Flow<List<IgcLogEntry>>` | manual file scans in UI |

### 3.2 Time Base Declaration

| Value | Time base | Why |
|---|---|---|
| B-record HHMMSS | UTC wall time from sample timestamp | IGC file contract |
| recorder staleness/debounce windows | monotonic | robust runtime gating |
| replay timestamps | IGC log timestamp | preserve existing replay contract |
| displayed file timestamps | wall time | user-facing metadata only |

## 4) Target Design (Implementation Shape)

Proposed packages (aligned to the current module split):

- `feature/igc/src/main/java/com/example/xcpro/igc/domain/`
  - `IgcRecordFormatter`
  - `IgcSessionStateMachine`
  - `IgcBRecordValidationPolicy`
- `feature/igc/src/main/java/com/example/xcpro/igc/data/`
  - `IgcFlightLogRepository`
  - `IgcDownloadsRepository`
  - `IgcTextWriter`
  - `IgcRecordingRuntimeActionSink`
- `feature/igc/src/main/java/com/example/xcpro/igc/usecase/`
  - `IgcFilesUseCase`
  - `IgcReplayLauncher`
- `feature/igc/src/main/java/com/example/xcpro/igc/ui/`
  - `IgcFilesViewModel`
  - `IgcFilesUiContract`
- `feature/igc/src/main/java/com/example/xcpro/screens/replay/`
  - `IgcFilesScreen`
  - `IgcFilesShareIntents`
- `feature/igc/src/main/java/com/example/xcpro/replay/`
  - `IgcParser`
  - `IgcReplayUseCase`
  - `IgcReplayControllerPort`
- `feature/map/src/main/java/com/example/xcpro/igc/usecase/`
  - `IgcRecordingUseCase`
- `feature/map/src/main/java/com/example/xcpro/igc/data/`
  - `IgcMetadataSources`
- `feature/map/src/main/java/com/example/xcpro/replay/`
  - `IgcReplayControllerRuntime*`

### 4.1 Formatter And Output Contracts (Spec-Locked)

- `A` record is always first line.
- Writer output uses CRLF line endings (`\r\n`) only.
- Base `B` record length is exactly 35 characters before CRLF.
- At most one `I` record per file and only before first `B`.
- Coordinate formatter enforces decimal-degree -> `DDMMmmm` / `DDDMMmmm` carry rules.
- Altitude formatter enforces 5-char encoding (including signed negative values).
- When GNSS altitude is unavailable: validity `V`, `GGGGG=00000`.
- When GNSS position is unavailable: repeat last valid lat/lon, keep UTC continuity, set `V`.
- File naming uses long style `YYYY-MM-DD-MMM-XXXXXX-FF.IGC` where date is UTC date of first valid `B` fix.

### 4.2 IGC Files User Interaction Contract

Navigation and naming:

- Replace user-facing "IGC Replay" entry for file management with "IGC Files".
- Keep replay playback as a sub-action within the IGC Files area (open/play file).

Required user actions on each finalized IGC file:

- Select file from list (search/sort by date, name, size, duration).
- Share file via Android chooser (`ACTION_SEND` with content URI).
- Email file (quick action that pre-fills email share intent).
- Upload/share to third-party tools (including WeGlide path via standard share flow).
- Copy file to a user-selected destination (`ACTION_CREATE_DOCUMENT` / SAF copy-out).
- Copy metadata details (filename, UTC date, size, path/URI) for support workflows.

UX constraints:

- No direct app-private paths shown as primary user destination.
- All share/copy flows must use `content://` URIs with temporary grant permissions.
- User-visible failure states must be actionable (permission denied, missing app target, file missing).

### 4.3 IGC Storage Directory Contract (Recommended)

Recommended two-tier storage:

- Final user-visible archive directory (canonical for pilots):
  - `Downloads/XCPro/IGC/`
- Internal staging and recovery directory (not user-facing):
  - app-private `files/igc/staging/`

Rules:

- Write in-progress files to staging first, then publish atomically to `Downloads/XCPro/IGC/` on finalize.
- One finalized `.IGC` per flight session.
- Keep deterministic naming: `YYYY-MM-DD-MMM-XXXXXX-FF.IGC`.
- Maintain repository index from MediaStore IDs (not raw filesystem scans in UI).

Integration points:

- `VarioServiceManager` or adjacent runtime wiring starts/stops recorder orchestration.
- `FlightDataRepository` and `FlightStateRepository` feed recorder runtime.
- Storage uses MediaStore Downloads pattern (same operational style as Task/Profile exports).

## 5) Phased Implementation Plan

## Phase 0 - Baseline and Spec Lock

Deliverables:

- Freeze IGC record contract and XCPro field mapping.
- Add this plan and research doc to docs index.
- Add baseline regression fixtures for known replay/IGC files.
- Normalize spec docs to clean UTF-8/ASCII punctuation and keep one canonical spec location in `docs/IGC`.

Gate:

- docs review complete
- `python scripts/arch_gate.py`
- `./gradlew enforceRules`

## Phase 1 - Pure Domain IGC Formatter

Deliverables:

- Implement deterministic formatter for `A/H/I/B/E/L` records.
- Explicit extension mapping for IAS/TAS fields.
- Add unit tests for coordinate, altitude, timestamp, and line formatting.
- Enforce `I` record single-instance rule and strict `B` width contracts.
- Implement `IgcTextWriter` adapter with explicit UTF-8 bytes and canonical `CRLF` line endings.
- Add byte-level CRLF writer tests:
  - no bare `LF` (`\n` without preceding `\r`)
  - no bare `CR` (`\r` without following `\n`)
  - deterministic byte output across repeated writes
  - required trailing final `CRLF`
- Add formatter -> writer -> parser round-trip tests.
- Add writer stress test with `>=10,000` `B` lines and newline-invariant assertions.

Gate:

- formatter unit tests green
- no Android imports in formatter/domain
- golden tests for coordinate carry (`mmm==1000`) and negative altitude formatting
- writer-adapter byte-level CRLF tests green
- formatter -> writer -> parser round-trip tests green

## Phase 2 - Session State Machine and Runtime Wiring

Deliverables:

- Implement deterministic recorder lifecycle: `Idle -> Armed -> Recording -> Finalizing -> Completed/Failed`.
- Add an explicit state transition contract (allowed signals, guards, and side effects) and keep it implementation-locked.
- Drive transitions from `FlightStateRepository` with monotonic debouncing only (no wall-time fallback).
- Centralize Phase 2 timing knobs in one config surface:
  - arming debounce
  - takeoff debounce
  - landing debounce
  - pre/post baseline window
  - finalize timeout
- Enforce session/file invariants:
  - one flight -> one sessionId -> one finalize intent -> one file
  - finalize dispatch is idempotent per session
- Include pre/post flight capture baseline windows (`>=20s` ground fixes before takeoff and after landing when data exists).
- Add touch-and-go behavior contract:
  - if `isFlying` resumes during `Finalizing`, return to `Recording` for the same active session.
- Add restart recovery contract:
  - persist and restore state-machine snapshot so session continuity is deterministic across process death.
- Lock SSOT ownership for recording state:
  - authoritative owner: IGC recording runtime/repository
  - exposed as: `StateFlow<IgcRecordingState>`
  - forbidden duplicate owners: UI-local recording flags/finalize flags.

Phase 2 transition contract (spec-locked):

| From | Signal | Guard | To | Required action |
|---|---|---|---|---|
| `Idle` | grounded fix stream | arming debounce met | `Armed` | emit `EnterArmed` |
| `Armed` | `isFlying=true` | takeoff debounce met | `Recording` | allocate sessionId, emit `StartRecording` |
| `Recording` | sustained `onGround=true` | landing debounce met | `Finalizing` | hold post-flight window accumulation |
| `Finalizing` | finalize condition met | baseline window met OR timeout met | `Finalizing` | emit `FinalizeRecording` exactly once |
| `Finalizing` | grounded/fix confidence lost | `!isFlying && (!onGround OR !hasFix)` | `Finalizing` | pause finalize timeout until grounded fix resumes |
| `Finalizing` | finalize success | active session exists | `Completed` | emit `MarkCompleted`, clear active session |
| `Finalizing` | finalize failure | active session exists | `Failed` | emit `MarkFailed`, clear active session |
| `Finalizing` | `isFlying=true` | touch-and-go | `Recording` | cancel pending finalize, keep same sessionId |

Gate:

- lifecycle transition tests green (`Idle/Armed/Recording/Finalizing/Completed/Failed`)
- monotonic debounce tests green (including non-monotonic input rejection)
- idempotent finalize tests green (single finalize action per session)
- baseline window tests green (`>=20s` pre and post windows when data exists)
- touch-and-go transition tests green (`Finalizing -> Recording` same session)
- process restart recovery baseline added and green (snapshot/restore continuity)
- gate evidence captured with test class/file paths plus pass logs in change notes

### 2A) Phase 2 Score (2026-03-09)

Scoring method: weighted by behavior, test confidence, recovery hardening, wiring evidence, and architecture compliance.

- Current score: **99 / 100**

Evidence:
- [State machine transitions + debounce + idempotent finalize + touch-and-go coverage]
  - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcSessionStateMachineTest.kt`
- [Restart persistence + terminal recovery]
  - `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcSessionStateMachine.kt`
  - `feature/map/src/main/java/com/example/xcpro/igc/usecase/IgcRecordingUseCase.kt`
  - `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcSessionStateSnapshotStore.kt`
  - `feature/map/src/test/java/com/example/xcpro/igc/usecase/IgcRecordingUseCaseTest.kt`
- [Snapshot restore hardening]
  - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcSessionStateMachineTest.kt#fromSnapshot_sanitizesInvalidNextSessionIdAndKeepsRecoveryMonotonic`
- [Runtime action collection wiring]
  - `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - `feature/igc/src/main/java/com/example/xcpro/igc/IgcRecordingActionSink.kt`
  - `feature/map/src/test/java/com/example/xcpro/vario/VarioServiceManagerConstructionTest.kt`
- [Dependency binding]
  - `feature/igc/src/main/java/com/example/xcpro/di/IgcCoreBindingsModule.kt`
  - `feature/map/src/main/java/com/example/xcpro/di/IgcBindingsModule.kt`

Scoring notes:
- 100/100: spec-locked state machine contract implemented and validated by tests.
- 99/100: crash-recovery snapshot continuity proven for terminal recovery and in-flight restore.
- 100/100: monotonic, touch-and-go, baseline, and idempotent finalize behaviors covered by tests.
- 99/100: runtime action handlers are collected, logged, and dispatched to `IgcRecordingActionSink` (`VarioServiceManager` wiring under test).
- 99/100: snapshot restore hardening prevents invalid persisted `nextSessionId` values from corrupting subsequent session sequencing.
- 99/100: architecture layering kept (domain/state machine + use case + UI/runtime observer only).

## Phase 3 - B-Record Stream and Data Validity Policy

Deliverables:

- Map live samples to B records at configured cadence (default 1Hz; configurable `1..5s`).
- Validity policy for `A` vs `V` fixes (accuracy/staleness).
- Pressure-altitude fallback policy documented and tested.
- Implement GNSS drop-out handling policy (validity, zeroed GNSS altitude, repeated last known position).

### 3.1 Domain model and data contract

Add new domain/data contracts for sample ingestion and conversion:

- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcSampleToBRecordMapper.kt`
  - Input: live flight sample + live sensor state.
  - Output: validated `IgcRecordFormatter.BRecord`.
  - Responsibility: coordinate validity classification, altitude selection/fallbacks, extension population.
- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcBRecordCadencePolicy.kt`
  - Encapsulates cadence and jitter handling.
  - Default cadence `1s`; explicit config for `1..5s` in domain config.
- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcBRecordValidationPolicy.kt`
  - Validity states: `A` vs `V` with explicit reasons.
  - Staleness thresholds:
    - GNSS location stale window: 5s (must be configurable).
    - altitude stale window: 8s (configurable).
    - accuracy gate: if source provides precision metadata, reject low-accuracy fixes.
- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcSampleStreamErrorPolicy.kt`
  - Malformed sample detection and sanitization policy.
  - Explicit handling for missing longitude/latitude/pressure and impossible coordinates.

### 3.2 Runtime behavior to implement in use-case wiring

- Update `IgcRecordingUseCase` to consume flight data stream alongside state signals.
- Create a bounded B-record emission buffer during `Recording` and `Finalizing` phases only.
- Maintain last valid `B` payload (`lat/lon/alt/time`) for reuse on dropouts.
- Ensure one-file/one-session invariance:
  - no output when no active session.
  - deterministic session association for each emitted `B`.

### 3.3 Data quality and fallback policy (spec-locked)

- Accuracy and staleness
  - `B` is emitted only when cadence timer fires.
  - Fix validity:
    - `A` only when position and age are within policy.
    - `V` for stale/missing/untrustworthy positions and when fallback rules are used.
- Position drop-out
  - Missing position: repeat last known valid `lat/lon`; keep monotonic UTC; emit `V`.
  - If there is no valid prior position in-session: skip B or emit with explicit fallback only if policy allows first-fix requirement (recommended: do not emit first B until first valid fix).
- Altitude behavior
  - Pressure altitude:
    - If baro unavailable and previous value exists in-session: repeat prior valid baro pressure altitude.
    - Otherwise emit `00000` and mark `V`.
  - GNSS altitude:
    - If unavailable: emit `GGGGG=00000` with `V`.
    - If available but stale: emit fallback per policy and set `V`.
- UTC continuity
  - `B` time component must be HHMMSS from UTC sample wall time at record generation.
  - Never emit duplicate or backward timestamps within a file sequence.

### 3.4 Validation tests and gates for Phase 3

- Functional unit tests:
  - cadence tests for `1s`, `2s`, `3s`, `5s` policies.
  - validity policy matrix (`A` vs `V`) by age/accuracy inputs.
  - malformed sample rejection (`NaN`, infinities, out-of-range coords, invalid altitude range).
  - GNSS dropout scenarios:
    - missing position
    - missing pressure altitude
    - missing GNSS altitude
    - 2D-like degraded GPS sample
  - fallback continuity test for repeated last lat/lon under no-position windows.
- Integration tests:
  - long-flight simulation (>=3h equivalent sample stream) with bounded cadence and no state drift.
  - stream that enters/deepens dropouts and recovers without session split.
  - finalizing timeout window with live/no-position samples and required B output behavior.

Gate:

- cadence policy test matrix green (`1..5s`)
- malformed sample rejection tests green (all malformed classes)
- explicit no-position/no-alt/2D tests green
- long-flight simulation test green (>=3h equivalent input)
- UTC monotonic sequence test across dropouts and wrap cases green
- explicit drop-out scenario tests (2D/no-alt/no-position)
- evidence block recorded with:
  - test class/file list
  - sample stream fixture(s)
  - pass/fail notes

## Phase 4 - Header, Task, and Event Coverage

Deliverables:

- Profile/app metadata -> H records.
- Task declaration snapshot -> C records (feature-gated if task absent).
- Flight/task/system events -> E records with dedupe policy.
- Required `H` baseline coverage: `DTE`, `PLT`, `CM2`, `GTY`, `GID`, `DTM`, `RFW`, `RHW`, `FTY`, `GPS`, `PRS`, `FRS`.
- Altitude datum headers policy: `HFALGALTGPS` and `HFALPALTPRESSURE` with explicit `ELL/GEO/ISA/NIL/NKN` handling.

### 4.1 Required `H`-Field Mapping Contract (Spec-Locked)

All required `H` lines must be mapped through one explicit table-driven contract.
No implicit per-call-site header synthesis is allowed.

| H field | Value contract | SSOT/source owner | Fallback policy | Required test |
|---|---|---|---|---|
| `HFDTEDATE` | `DDMMYY,FF` (UTC date from first valid `B`; deterministic day flight index) | IGC recording repository/session metadata | If no valid `B` exists, use session-start UTC date and `FF=01`; gate evidence must record fallback path | first-valid-fix UTC date + rollover |
| `HFPLTPILOTINCHARGE` | pilot name (profile) | profile repository/use-case output | `NKN` when missing/blank | missing-profile fallback |
| `HFCM2CREW2` | crew-2 value | profile repository/use-case output | `NIL` when not applicable or unset | nullable crew-2 mapping |
| `HFGTYGLIDERTYPE` | glider type | profile repository/use-case output | `NKN` when missing | missing glider type |
| `HFGIDGLIDERID` | glider ID/registration | profile repository/use-case output | `NKN` when missing | missing glider ID |
| `HFDTMGPSDATUM` | fixed `WGS84` | formatter constant policy | no fallback; contract must fail test if not `WGS84` | fixed datum invariant |
| `HFRFWFIRMWAREVERSION` | app/version string | app metadata adapter port | `NKN` when unavailable | app metadata adapter fallback |
| `HFRHWHARDWAREVERSION` | device model/build string | device identity adapter port | `NKN` when unavailable | hardware metadata fallback |
| `HFFTYFRTYPE` | recorder type text (`XCPro,Mobile` baseline) | formatter/product contract | `XCPro,Mobile` baseline constant | recorder type baseline |
| `HFGPSRECEIVER` | GNSS receiver metadata | GNSS metadata adapter port | `NKN` when unavailable | GNSS metadata fallback |
| `HFPRSPRESSALTSENSOR` | pressure sensor metadata | pressure metadata adapter port | `NKN` when unavailable | pressure metadata fallback |
| `HFFRSSECURITY` | security mode label | compliance profile state | baseline profile: `UNSIGNED` | profile-mode labeling |
| `HFALGALTGPS` | altitude datum policy (`ELL/GEO/NIL/NKN`) | altitude policy contract | `NIL` when GNSS altitude unsupported; `NKN` when unknown | altitude-datum policy matrix |
| `HFALPALTPRESSURE` | pressure altitude datum policy (`ISA/MSL/NIL/NKN`) | altitude policy contract | `NIL` when pressure altitude unsupported; `NKN` when unknown | pressure-datum policy matrix |

### 4.2 `C`-Record Declaration Snapshot Semantics (Spec-Locked)

- Declaration snapshot source is the canonical task snapshot from task SSOT (no UI-derived task state).
- Snapshot timing is exactly once per session at `Armed -> Recording` (`StartRecording` action emission).
- Captured declaration is immutable for the rest of that session:
  - task edits after snapshot do not mutate in-flight `C` output.
- `C` block placement is deterministic:
  - after `A/H/(optional I/J)` and before first `B`.
- If task is absent, export omits `C` entirely (feature-gated behavior).
- If task snapshot is present but invalid/incomplete for declaration schema, omit `C` and surface deterministic export diagnostic reason (no partial synthetic declaration lines).
- Required tests:
  - no-task session emits no `C`.
  - valid task emits one deterministic `C` block before first `B`.
  - mid-flight task edits do not alter previously captured declaration output.

### 4.3 `E`-Record Event Taxonomy and Dedupe Policy (Spec-Locked)

- Event code set is explicit and 3-letter constrained:
  - `FLT` (flight lifecycle), `TSK` (task navigation/declaration), `SYS` (system/runtime conditions).
- Event payload normalization for dedupe:
  - trim, collapse CR/LF to spaces, uppercase ASCII policy.
- Dedupe key:
  - `sessionId + eventCode + normalizedPayload`.
- Dedupe window:
  - monotonic `5s` default (configurable via domain config surface).
- Max-rate policy:
  - per-key: max `1` emission per dedupe window.
  - global: max `1` `E` emission per second, deterministic drop of additional duplicates.
- Ordering policy:
  - primary sort: UTC second.
  - tie-breaker: stable capture sequence within session.
- Required tests:
  - duplicate bursts collapse under dedupe window.
  - near-window boundary emits expected second event deterministically.
  - stable ordering for same-second multi-event capture.

Gate:

- cross-check tests for required H fields using the mapping table above
- deterministic header-order test (`A` first, required `H` set, optional records in locked order)
- pre-first-`B` placement test for `C` block and optional `I/J` contract
- no duplicate mandatory `H` header tests
- task/event mapping tests with racing task fixtures
- immutable `C` snapshot test (task change after start does not alter exported declaration)
- `E` dedupe tests for key/window/max-rate/ordering behavior
- `NIL`/`NKN` fallback tests for missing profile/sensor metadata
- deterministic rerun test: same input/session fixture yields identical `H/C/E` output

## Phase 5 - Persistence, Download, and Share UX

Deliverables:

- Define explicit Phase 5 ownership and adapters:
  - `IgcFlightLogRepository` (session finalize publish + lookup by session).
  - `IgcDownloadsRepository` (MediaStore query/index owner).
  - `IgcFileNamingPolicy` (deterministic name and collision policy).
  - `IgcFilesUseCase` (UI-facing list/search/sort/actions surface).
  - `IgcFilesViewModel` (state/events only; no file I/O).
- Save finalized files via MediaStore under managed path:
  - `Downloads/XCPro/IGC/`
- Add IGC Files list/query flow and share actions.
- Ensure file naming uniqueness and deterministic collision handling.
- File naming must follow `YYYY-MM-DD-MMM-XXXXXX-FF.IGC` (UTC date from first valid `B`, deterministic per-day `FF` increment).
- Add per-file quick actions: share, email, upload/share target, copy-to, replay-open.
- Add explicit WeGlide-capable handoff via generic share intent (no hard-coded provider dependency required in baseline).
- Keep replay playback inside IGC Files as a file action, not as the primary file-management entry label.

### 5.1 SSOT and boundary contract (spec-locked)

Finalized IGC index:

- authoritative owner: `IgcDownloadsRepository`
- exposed as: `Flow<List<IgcLogEntry>>`
- forbidden duplicates:
  - UI/manual filesystem scans for IGC list state
  - ad-hoc ViewModel-maintained file caches

Existing finalized/staged IGC bytes:

- authoritative owner: `feature/igc` data adapter boundary
- exposed as: typed raw-byte or streaming-read contract on
  `IgcDownloadsRepository` or an adjacent IGC document-read port
- forbidden duplicates:
  - UI/use-case direct `ContentResolver.openInputStream(...)` calls
  - replay/share helper-owned stream openers
  - private one-off document readers that bypass typed diagnostics

Dependency direction:

- UI -> use-case -> repository/adapter
- Domain/use-case layers define required ports; data layer implements MediaStore/SAF adapters.

### 5.2 Deterministic naming and collision contract (spec-locked)

Filename pattern:

- `YYYY-MM-DD-MMM-XXXXXX-FF.IGC`

Deterministic derivation:

1. `YYYY-MM-DD` = UTC date from first valid `B` fix.
2. Fallback when no valid `B` exists = session-start UTC date (must emit deterministic diagnostic).
3. `MMM` = recorder manufacturer id (`XCP` baseline unless recorder contract changes).
4. `XXXXXX` = deterministic 6-digit session serial (same source as `A` record serial).
5. `FF` = deterministic per-day index (`01..99`) selected from existing same-day files in `Downloads/XCPro/IGC/`.

Collision policy:

- Query same-day existing names under `Downloads/XCPro/IGC/`.
- Select the lowest free `FF` value in ascending order.
- If no slot exists in `01..99`, fail finalize with typed error (`IGC_NAME_SPACE_EXHAUSTED`) and no partial publish.

### 5.3 Storage and publish protocol (spec-locked)

Storage tiers:

- Staging (internal only): `files/igc/staging/`
- Final archive (user visible): `Downloads/XCPro/IGC/`

Publish steps (atomic intent):

1. Build complete file bytes from deterministic line set (writer already enforces CRLF).
2. Write staging file for session.
3. Insert MediaStore item with:
   - `RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/XCPro/IGC"`
   - `DISPLAY_NAME = <resolved deterministic filename>`
   - `IS_PENDING = 1`
4. Stream bytes into MediaStore URI.
5. Mark `IS_PENDING = 0` only after successful write/flush.
6. On any failure, delete pending MediaStore row and preserve typed error result.

Invariants:

- exactly one finalized `.IGC` per completed session
- finalize is idempotent by `sessionId` (same session cannot publish duplicate files)
- no app-private paths exposed as user primary destination

### 5.4 IGC Files UX and interaction contract (spec-locked)

Navigation and labeling:

- Rename file-management label from `IGC Replay` to `IGC Files`.
- Replay remains available as `replay-open` action per file.

Required per-file actions:

- share (`ACTION_SEND` + `content://` URI + read grant)
- email quick action (mail-capable share handoff with attached content URI)
- upload/share target via generic chooser (covers WeGlide-capable handoff without provider hard-coding)
- copy-to via SAF (`ACTION_CREATE_DOCUMENT`) with streamed content copy
- replay-open action using selected file URI
- copy metadata action (name, UTC date, size, URI) for support workflows

Failure-state contract:

- permission denied
- missing share target
- missing/deleted file row
- copy destination write failure
- each failure must map to actionable user message + typed internal reason code

### 5.5 Gate and score contract (target >98/100)

Gate:

- instrumentation tests for download/query/share
- manual UX checklist for post-flight retrieval
- collision tests for multi-flight same UTC day naming
- instrumentation tests for chooser/share/email/copy-to flows and URI grants
- UX rename acceptance: "IGC Files" replaces file-management "IGC Replay" label in settings/navigation
- finalize idempotency tests: repeated finalize signal for same session publishes once only
- staging-to-publish recovery tests: pending row/file cleanup on write failure

Required evidence artifacts:

- `docs/IGC/phase5_evidence/phase5_gates.md`
- `docs/IGC/phase5_evidence/phase5_manual_checklist.md`
- `docs/IGC/phase5_evidence/phase5_naming_collision_matrix.md`
- `docs/IGC/phase5_evidence/phase5_share_uri_grants.md`

Phase 5 scoring target (release bar):

- spec coverage/parity: `>=39/40`
- automated test depth: `>=29/30`
- determinism/architecture compliance: `>=19/20`
- operational hardening/docs sync: `10/10`
- total: `>=98/100` required to mark Phase 5 production grade

### 5.6 Phase 5 100/100 implementation checklist (ordered execution)

`P5-0` Preflight and scope lock:

1. Confirm existing Phase 1-4 IGC tests are green before starting Phase 5 implementation.
2. Lock target files and owners in change notes:
   - production: `igc/domain`, `igc/data`, `igc/usecase`, `screens/replay|navdrawer`.
   - tests:
     - `feature/igc/src/test/java/com/example/xcpro/igc/*`
     - `feature/igc/src/androidTest/java/com/example/xcpro/igc/*`
     - `feature/map/src/test/java/com/example/xcpro/igc/usecase/*`
     - `feature/map/src/androidTest/java/com/example/xcpro/igc/*`

`P5-1` Add Phase 5 production contracts (ports first):

1. Add:
   - `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcFileNamingPolicy.kt`
   - `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcFlightLogRepository.kt`
   - `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcDownloadsRepository.kt`
   - `feature/igc/src/main/java/com/example/xcpro/igc/usecase/IgcFilesUseCase.kt`
   - `feature/igc/src/main/java/com/example/xcpro/igc/ui/IgcFilesViewModel.kt`
2. Bind core adapters in `feature/igc/src/main/java/com/example/xcpro/di/IgcCoreBindingsModule.kt`
   and keep runtime/navigation wiring in
   `feature/map/src/main/java/com/example/xcpro/di/IgcBindingsModule.kt`.
3. Add typed raw-byte read access for existing IGC documents in the same
   boundary layer so replay-open, validation, and redaction do not reopen files
   through scattered Android-only helpers.
4. Keep ViewModel free of file I/O and Android storage APIs.

`P5-2` Deterministic naming and collision implementation:

1. Implement filename policy `YYYY-MM-DD-MMM-XXXXXX-FF.IGC`.
2. Implement same-day collision scan + lowest-free `FF` selection.
3. Implement typed fail path `IGC_NAME_SPACE_EXHAUSTED`.
4. Add unit tests:
   - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcFileNamingPolicyTest.kt`
   - test vectors: first-valid-`B`, no-valid-`B` fallback, rollover day, collision chain, exhaustion.

`P5-3` Staging + atomic publish implementation:

1. Stage bytes to `files/igc/staging/`.
2. Publish to MediaStore using `IS_PENDING=1 -> write -> IS_PENDING=0`.
3. Use `RELATIVE_PATH=Download/XCPro/IGC`.
4. Enforce idempotent finalize by `sessionId`.
5. Add unit tests:
   - `feature/igc/src/test/java/com/example/xcpro/igc/data/IgcFlightLogRepositoryTest.kt`
   - `feature/igc/src/test/java/com/example/xcpro/igc/data/IgcFlightLogRepositoryIdempotencyTest.kt`
   - `feature/igc/src/test/java/com/example/xcpro/igc/data/IgcFlightLogRepositoryRecoveryTest.kt`

`P5-4` Index/query/list implementation:

1. Implement MediaStore-backed `Flow<List<IgcLogEntry>>` in `IgcDownloadsRepository`.
2. Implement list filtering/sorting/search in `IgcFilesUseCase`.
3. Add unit tests:
   - `feature/igc/src/test/java/com/example/xcpro/igc/data/IgcDownloadsRepositoryTest.kt`
   - `feature/igc/src/test/java/com/example/xcpro/igc/usecase/IgcFilesUseCaseTest.kt`

`P5-5` UX and interaction implementation:

1. Rename file-management label from `IGC Replay` to `IGC Files`.
2. Add per-file actions:
   - share, email, generic upload/share target, copy-to (SAF), replay-open, metadata copy.
3. Enforce `content://` URI grants for every external handoff path.
4. Add failure-state mapping to user actionable messages + typed internal reason codes.

`P5-6` Instrumentation suite (mandatory for 100):

1. Add:
   - `feature/igc/src/androidTest/java/com/example/xcpro/igc/IgcFilesListInstrumentedTest.kt`
   - `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcFilesShareInstrumentedTest.kt`
   - `feature/igc/src/androidTest/java/com/example/xcpro/igc/IgcFilesCopyToInstrumentedTest.kt`
   - `feature/igc/src/androidTest/java/com/example/xcpro/igc/IgcFilesReplayOpenInstrumentedTest.kt`
   - `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcFilesNavigationLabelInstrumentedTest.kt`
2. Cover download/query/share, chooser/email/copy-to URI grants, replay-open, and label rename acceptance.

`P5-7` Verification execution order (must run in this order):

1. `python scripts/arch_gate.py`
2. `./gradlew enforceRules`
3. `./gradlew :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.*" --tests "com.example.xcpro.replay.Igc*"`
4. `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.*"`
5. `./gradlew :feature:igc:assembleDebug`
6. `./gradlew :feature:map:assembleDebug`
7. `./gradlew :feature:igc:connectedDebugAndroidTest --no-parallel`
8. `./gradlew :feature:map:connectedDebugAndroidTest --no-parallel`
9. `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
10. `./gradlew connectedDebugAndroidTest --no-parallel`
11. `./gradlew testDebugUnitTest`
12. `./gradlew assembleDebug`

`P5-8` 100/100 claim criteria (all required, no partial credit):

1. All Phase 5 gates in Section 5.5 pass.
2. All required evidence artifacts are populated with command outputs and pass results.
3. No open Phase 5 architecture deviations in `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.
4. Full verification order from `P5-7` passes in two consecutive runs.
5. Release scorecard for Phase 5 is exactly:
   - spec coverage/parity: `40/40`
   - automated test depth: `30/30`
   - determinism/architecture compliance: `20/20`
   - operational hardening/docs sync: `10/10`

## Phase 6 - Recovery, Retention, and Privacy Hardening

Deliverables:

- Crash-safe temp file strategy and finalize-on-restart recovery.
- Retention policy (count/age cap) with user controls.
- Redaction/privacy policy docs for shared logs.

### 6.1 Recovery contract (spec-locked)

Recovery scope:

- Recovery applies to sessions that reached `Recording` or `Finalizing` and have
  persisted state in `IgcSessionStateSnapshotStore`.
- Recovery is restart-safe and idempotent by `sessionId`.
- Recovery must never create duplicate finalized exports for the same
  `sessionId`.

Recovery algorithm (startup + finalize path):

1. On app/service startup, enumerate persisted session snapshots in
   `IgcSessionStateSnapshotStore`.
2. For each snapshot in `Recording` or `Finalizing`, resolve recovery state:
   - finalized export already exists for `sessionId` -> mark recovered and clear
     stale staging artifacts.
   - finalized export missing and staged bytes are complete -> attempt finalize
     publish once.
   - staged bytes missing/corrupt/incomplete -> fail with typed recovery error,
     clear orphan artifacts, keep deterministic diagnostic.
3. Recovery publish uses the same idempotent finalize contract as Phase 5
   (`sessionId` authority).
4. Recovery validation must inspect staged raw bytes before any
   `readLines()`/newline-normalizing path; recovery must not mask CRLF or
   whitespace violations by reparsing normalized text.
5. After successful finalize or terminal failure handling, clear snapshot/staging
   artifacts for that `sessionId`.

Kill-point matrix (mandatory test set):

- `K1`: crash after snapshot persist, before staged write.
- `K2`: crash after staged write, before MediaStore row insert.
- `K3`: crash after MediaStore row insert (`IS_PENDING=1`), before byte copy.
- `K4`: crash mid-byte copy to pending URI.
- `K5`: crash after byte copy/flush, before `IS_PENDING=0`.
- `K6`: crash after `IS_PENDING=0`, before snapshot clear.
- `K7`: crash after snapshot clear.

Expected behavior by invariant:

- no duplicate final file for a single `sessionId`
- no leaked pending rows after recovery completion
- no leaked staging bytes after terminal recovery outcome
- deterministic typed recovery result for every kill-point

Typed recovery error taxonomy (minimum):

- `IGC_RECOVERY_STAGING_MISSING`
- `IGC_RECOVERY_STAGING_CORRUPT`
- `IGC_RECOVERY_PENDING_ROW_WRITE_FAILED`
- `IGC_RECOVERY_NAME_COLLISION_UNRESOLVED`
- `IGC_RECOVERY_DUPLICATE_SESSION_GUARD`

Recovery mapping contract:

- every expanded `IgcFinalizeResult.ErrorCode` added in Phase 7 must map to an
  explicit recovery outcome
- no default collapse of new lint/compatibility failures into generic
  `WRITE_FAILED`/`PENDING_ROW_WRITE_FAILED`

### 6.2 Retention contract (spec-locked)

Retention policy controls (user-facing):

- `maxFileCount` (`1..999`, default `200`)
- `maxFileAgeDays` (`1..3650`, default `365`)
- `autoPruneOnFinalize` (`true` default)
- manual `Run retention now` action

Retention scope:

- Applies to finalized files under `Downloads/XCPro/IGC/`.
- Never prunes active staged files or in-progress finalize session artifacts.
- Never runs inside ViewModel/UI; policy is domain/use-case + repository adapter
  owned.

Retention ordering and precedence:

1. Age cap pass: delete files older than `maxFileAgeDays` first.
2. Count cap pass: if remaining files exceed `maxFileCount`, delete oldest first.
3. Deterministic tie-break order for delete candidates:
   - file UTC date ascending
   - `DATE_MODIFIED` ascending
   - `DISPLAY_NAME` lexicographic ascending
   - URI string lexicographic ascending

Retention failure semantics:

- Partial prune is allowed, but every failed delete must emit typed reason code
  and actionable user message.
- Retention failure must not block finalize success for the current flight;
  retention failures are surfaced as non-fatal post-finalize diagnostics.

Typed retention error taxonomy (minimum):

- `IGC_RETENTION_QUERY_FAILED`
- `IGC_RETENTION_DELETE_FAILED`
- `IGC_RETENTION_PERMISSION_DENIED`
- `IGC_RETENTION_INVALID_POLICY_RANGE`

Time-base contract:

- File-age policy uses injected wall-clock time (`Clock`) against file UTC/wall
  metadata.
- No monotonic/wall mixed comparisons in retention logic.

### 6.3 Privacy and redaction contract (spec-locked)

Policy principle:

- Canonical archived flight file is immutable after finalize.
- Any redaction for external share is generated as a separate share copy only.

Share privacy modes (explicit):

- `ORIGINAL` (default baseline for expert/manual workflows)
- `REDACT_PERSONAL_HEADERS` (pilot/glider/registration/contact header fields)
- `REDACT_PERSONAL_AND_DEVICE` (personal headers + recorder/device-identifying
  headers and privacy-sensitive `L` diagnostics)

Redaction scope contract:

- `A`/`B` record structural validity is preserved.
- Redaction is deterministic for identical input file + mode.
- No in-place mutation of the canonical file in `Downloads/XCPro/IGC/`.
- Redacted output is written to app-controlled temporary share path and removed
  after handoff lifecycle completion where platform allows.

Redaction docs and UX contract:

- Privacy modes, field-level behavior, and limitations must be documented in
  in-repo docs and linked from share UX copy.
- User-facing copy must explicitly state when location/track coordinates remain
  present (for the two baseline redaction modes above).

Typed privacy/share error taxonomy (minimum):

- `IGC_SHARE_REDACTION_PARSE_FAILED`
- `IGC_SHARE_REDACTION_WRITE_FAILED`
- `IGC_SHARE_REDACTION_UNSUPPORTED_RECORD`
- `IGC_SHARE_TARGET_UNAVAILABLE`

### 6.4 Gate and score contract (target >=98/100)

Gate:

- kill-and-restart recovery matrix tests (`K1..K7`) green
- retention policy unit tests green (age/count/tie-break/active-session-exclusion)
- retention instrumentation tests green (MediaStore query + delete behavior)
- privacy redaction tests green (mode matrix + structural invariants)
- share-flow instrumentation tests green for redacted handoff URIs/grants
- docs sync complete for privacy mode behavior and recovery/retention wiring

Phase 6 scoring target (release bar):

- spec coverage/parity: `>=39/40`
- automated test depth: `>=29/30`
- determinism/architecture compliance: `>=19/20`
- operational hardening/docs sync: `10/10`
- total: `>=98/100` required to mark Phase 6 production grade

### 6.5 Required evidence artifacts

- `docs/IGC/phase6_evidence/phase6_gates.md`
- `docs/IGC/phase6_evidence/phase6_recovery_kill_matrix.md`
- `docs/IGC/phase6_evidence/phase6_retention_policy_matrix.md`
- `docs/IGC/phase6_evidence/phase6_privacy_redaction_matrix.md`
- `docs/IGC/phase6_evidence/phase6_share_uri_grants.md`
- `docs/IGC/phase6_evidence/phase6_manual_checklist.md`

Each artifact must include:

- command executed
- timestamp
- pass/fail result
- failing case IDs (if any)
- links to updated files/tests

### 6.6 Phase 6 100/100 implementation checklist (ordered execution)

`P6-0` Preflight and scope lock:

1. Confirm all Phase 5 gates and claim criteria remain green.
2. Lock target production/test/doc files and owners in change notes.
3. Confirm no open architecture deviation blocks in
   `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` for this workstream.

`P6-1` Add Phase 6 contracts (ports first):

1. Add/extend domain contracts for:
   - restart recovery policy and typed outcomes
   - retention policy and typed outcomes
   - redaction policy modes and field-level mapping rules
2. Keep boundary adapters in data layer only (MediaStore/file APIs).
3. Keep `UI -> use-case -> repository` direction only.

`P6-2` Recovery implementation:

1. Implement startup recovery orchestration using persisted session snapshots.
2. Implement deterministic cleanup of orphan staging/pending artifacts.
3. Enforce finalize idempotency for recovered sessions (`sessionId` authority).
4. Add unit tests:
   - `feature/igc/src/test/java/com/example/xcpro/igc/data/IgcFlightLogRepositoryRecoveryKillPointTest.kt`
   - `feature/map/src/test/java/com/example/xcpro/igc/usecase/IgcRecordingUseCaseTest.kt`
   - cover `K1..K7` matrix with explicit expected outcomes.

`P6-3` Retention implementation:

1. Implement policy evaluation (age pass then count pass) with deterministic
   tie-breakers.
2. Add user-control wiring (`maxFileCount`, `maxFileAgeDays`,
   `autoPruneOnFinalize`, manual run).
3. Add unit tests:
   - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcRetentionPolicyTest.kt`
   - `feature/igc/src/test/java/com/example/xcpro/igc/data/IgcDownloadsRepositoryRetentionTest.kt`
   - `feature/igc/src/test/java/com/example/xcpro/igc/usecase/IgcFilesUseCaseRetentionTest.kt`

`P6-4` Privacy/redaction implementation:

1. Implement deterministic redaction transformer by share mode.
2. Generate redacted share copy without mutating canonical archive file.
3. Surface typed share/redaction failure reasons to UX.
4. Consume canonical file bytes through the `feature/igc` document-read boundary
   rather than UI/replay helper-owned `ContentResolver` calls.
5. Add unit tests:
   - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcRedactionPolicyTest.kt`
   - `feature/igc/src/test/java/com/example/xcpro/igc/usecase/IgcShareRedactionUseCaseTest.kt`

`P6-5` UX and docs sync:

1. Add privacy mode selector + concise user copy in IGC share flow.
2. Add retention settings UI controls and validation messaging.
3. Update:
   - `docs/ARCHITECTURE/PIPELINE.md` if runtime wiring changes.
   - IGC docs to include redaction mode matrix and known limitations.

`P6-6` Instrumentation suite (mandatory for 100):

1. Add:
   - `feature/map/src/androidTest/java/com/example/xcpro/igc/IgcRecoveryRestartInstrumentedTest.kt`
   - `feature/igc/src/androidTest/java/com/example/xcpro/igc/IgcRetentionInstrumentedTest.kt`
   - `feature/igc/src/androidTest/java/com/example/xcpro/igc/IgcShareRedactionInstrumentedTest.kt`
2. Cover startup recovery, retention delete/query behavior, and redacted share
   URI grant correctness.

`P6-7` Verification execution order (must run in this order):

1. `python scripts/arch_gate.py`
2. `./gradlew enforceRules`
3. `./gradlew :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.*" --tests "com.example.xcpro.replay.Igc*"`
4. `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.*"`
5. `./gradlew :feature:igc:assembleDebug`
6. `./gradlew :feature:map:assembleDebug`
7. `./gradlew :feature:igc:connectedDebugAndroidTest --no-parallel`
8. `./gradlew :feature:map:connectedDebugAndroidTest --no-parallel`
9. `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
10. `./gradlew connectedDebugAndroidTest --no-parallel`
11. `./gradlew testDebugUnitTest`
12. `./gradlew assembleDebug`

`P6-8` 100/100 claim criteria (all required, no partial credit):

1. All Phase 6 gates in Section 6.4 pass.
2. All required evidence artifacts are populated with command outputs and pass
   results.
3. No open Phase 6 architecture deviations in
   `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.
4. Full verification order from `P6-7` passes in two consecutive runs.
5. Release scorecard for Phase 6 is exactly:
   - spec coverage/parity: `40/40`
   - automated test depth: `30/30`
   - determinism/architecture compliance: `20/20`
   - operational hardening/docs sync: `10/10`

## Phase 7 - Validation and Interoperability Net

Deliverables:

- Round-trip tests: write -> parse via `IgcParser` -> compare tolerances.
- Add external parser compatibility checks (fixture-based).
- Add `igc lint` style validation diagnostics for user-visible failures.
- Add strict sanity validators: `A` first, no spaces in `B/I`, monotonic UTC in `B`, CRLF enforcement, `I` byte-range validation.

Gate:

- compatibility suite green
- explicit error taxonomy documented
- lint diagnostics surfaced in export failure UI states

### 7.1 Phase entry prerequisites (blocking)

Before Phase 7 can claim `100/100`, all of the following must already be true:

1. Phase 5 claim criteria remain green and evidence is current.
2. Phase 6 claim criteria remain green and evidence is current.
3. No open Phase 5/6 architecture deviations block release readiness.
4. Connected-device instrumentation and manual checklist evidence is present for
   export/share/copy/replay flows (not unit-only evidence).

### 7.2 Domain and adapter contracts (ports first)

Add or extend explicit contracts for validation and diagnostics:

- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcLintValidator.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcLintIssue.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcLintRuleSet.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/usecase/IgcLintMessageMapper.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcExportValidationAdapter.kt`
- `feature/igc/src/main/java/com/example/xcpro/igc/data/IgcExportDiagnosticsRepository.kt`
- `feature/map/src/main/java/com/example/xcpro/igc/usecase/IgcRecordingUseCase.kt`
- `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`

Contract rules:

- Lint and compatibility decisions are domain/use-case outputs, not UI-local
  string parsing.
- Core formatter/writer/repository/UI contracts live in `feature/igc`; live
  sensor/runtime orchestration stays in `feature/map`.
- Share/export UI helpers under `feature/igc/src/main/java/com/example/xcpro/screens/replay/`
  must consume typed diagnostics mapping, not keep independent failure semantics.
- Existing-file validation, redaction, and replay-open preflight must consume a
  typed raw-byte read boundary in `feature/igc` (extend
  `IgcDownloadsRepository` or add an adjacent document-read port); do not keep
  direct `ContentResolver.openInputStream(...)` side paths in UI/use-case/replay
  helpers.
- Repositories return typed failure outcomes for lint/compatibility failures;
  generic throw-only error paths are not sufficient.
- Runtime wiring and UI consume typed outcomes and render actionable messages.

### 7.2A Diagnostics SSOT ownership (mandatory)

Authoritative owner:

- latest export/finalize diagnostics -> IGC diagnostics repository in
  `feature/igc`

Exposed as:

- `StateFlow<IgcExportDiagnostic?>` (or equivalent typed stream)

Forbidden duplicates:

- ViewModel-local finalize failure strings
- log-only failure handling in `VarioServiceManager`
- ad-hoc UI parsing of exception text

### 7.2B Existing document byte authority (mandatory)

Authoritative owner:

- raw bytes for finalized IGC documents and staged recovery files -> IGC data
  adapter in `feature/igc`

Exposed as:

- typed `Result<ByteArray>` or streaming-read boundary contract

Forbidden duplicates:

- replay helper direct `Context.contentResolver.openInputStream(...)`
- private `IgcDownloadsRepository`-local stream readers that are unavailable to
  validation/redaction flows
- screen/use-case-owned document re-open logic

### 7.3 Strict sanity validator matrix (spec-locked)

Minimum rule set (all required):

- `A` record is first line.
- no spaces inside `B` or `I` records.
- `B` timestamps are monotonic UTC within file sequence (with explicit rollover
  policy).
- CRLF contract: no bare `LF`, no bare `CR`, trailing final `CRLF` present.
- `I` record byte-range validity:
  - numeric ranges only
  - `start >= 36`
  - `end >= start`
  - no overlaps
  - within actual `B` record width contract
- at most one `I` record and only before first `B`.
- reject embedded line-break characters in any record payload.

### 7.3A Parser, formatter, and lint parity contract

The same `I`-record and line-structure rules must be enforced consistently by:

- `IgcRecordFormatter`
- `IgcParser`
- `IgcLintValidator`

Required parity checks:

- `I` byte start floor matches writer contract (`>=36` for `B` extensions)
- overlap/ordering rules are consistent
- late or repeated `I` records are rejected consistently
- invalid-extension fixtures are tested against parser + lint behavior together
- parser permissiveness is explicitly accounted for: lint must validate raw bytes
  or unsanitized lines before parser normalization/trimming
- staged recovery bytes are linted before any recovery reserialize path so CRLF
  and whitespace violations cannot be hidden by newline normalization

### 7.4 Error taxonomy and UI surfacing contract (spec-locked)

Minimum typed error taxonomy:

- `IGC_LINT_A_RECORD_NOT_FIRST`
- `IGC_LINT_B_RECORD_WHITESPACE`
- `IGC_LINT_I_RECORD_WHITESPACE`
- `IGC_LINT_B_TIME_NON_MONOTONIC`
- `IGC_LINT_CRLF_VIOLATION`
- `IGC_LINT_I_RANGE_INVALID`
- `IGC_LINT_I_MULTIPLE_OR_LATE`
- `IGC_ROUNDTRIP_TOLERANCE_EXCEEDED`
- `IGC_COMPAT_EXTERNAL_PARSER_REJECTED`

Integration requirements:

- Extend `IgcFinalizeResult.ErrorCode` and repository failure outputs so lint and
  compatibility failures are typed.
- Update recovery translation so every expanded finalize/lint/compatibility code
  has an explicit recovery mapping.
- Replace generic finalize exception-only propagation with structured typed
  propagation.
- Route background finalize/export failures through diagnostics SSOT so they are
  observable even when the IGC Files screen is not currently open.
- Surface actionable lint/compatibility messages in export failure UI states.
- Ensure replay-open does not remain an unvalidated direct-load side path for
  finalized files.
- Keep verbose parser diagnostics in logs/evidence artifacts, not end-user copy.

### 7.5 Compatibility and round-trip suite contract

Internal round-trip suite:

- verify writer -> parser parity across:
  - coordinates
  - altitude fields
  - extension fields
  - UTC day rollover
  - dropout/fallback sequences
- use explicit tolerance table and fail on threshold breach.

External compatibility suite:

- fixture-based compatibility checks against canonical fixture plus generated
  edge fixtures.
- include at least two independent external parser/validator implementations in
  reproducible local/CI harness.
- for every failure, record fixture ID, first failing line, and parser reason.

### 7.6 Gate and score contract (target = 100/100)

Gate (all required):

- compatibility suite green (internal + external).
- explicit lint/error taxonomy documented and mapped to typed failures.
- lint diagnostics surfaced in export failure UI states.
- background finalize diagnostics are observable from SSOT and not log-only.
- no generic "unknown error" fallback for typed lint/compatibility failures.
- Phase 7 evidence artifacts complete and linked.

Phase 7 scoring target (claim bar):

- spec coverage/parity: `40/40`
- automated test depth: `30/30`
- determinism/architecture compliance: `20/20`
- operational hardening/docs sync: `10/10`
- total: `100/100` required to mark Phase 7 complete.

### 7.7 Required evidence artifacts

- `docs/IGC/phase7_evidence/phase7_gates.md`
- `docs/IGC/phase7_evidence/phase7_roundtrip_tolerance_matrix.md`
- `docs/IGC/phase7_evidence/phase7_external_compatibility_matrix.md`
- `docs/IGC/phase7_evidence/phase7_lint_rule_matrix.md`
- `docs/IGC/phase7_evidence/phase7_parser_lint_parity_matrix.md`
- `docs/IGC/phase7_evidence/phase7_error_taxonomy_mapping.md`
- `docs/IGC/phase7_evidence/phase7_manual_checklist.md`

Each artifact must include:

- command executed
- timestamp
- pass/fail result
- failing case IDs (if any)
- links to updated files/tests

### 7.8 Phase 7 100/100 implementation checklist (ordered execution)

`P7-0` Preflight and scope lock:

1. Confirm Phase 5 and Phase 6 claim criteria still pass.
2. Lock Phase 7 target files/tests/docs in change notes.
3. Confirm no blocking IGC architecture deviations are open.

`P7-1` Add contracts and models:

1. Add lint domain contracts and issue taxonomy.
2. Add message-mapping use-case contract for UI-safe diagnostics.
3. Keep adapter/platform parser invocations in data layer only.
4. Add diagnostics SSOT contract for background finalize/export failures.

`P7-2` Implement lint validation in finalize/export path:

1. Validate assembled file lines before publish.
2. Return typed lint failures from repository/use-case boundary.
3. Add unit tests for each lint rule and mixed-failure precedence.
4. Keep validation implementation in `feature/igc`; use `feature/map` only for
   orchestration/wiring.

`P7-3` Implement typed failure surfacing:

1. Extend finalize failure codes for lint/compatibility.
2. Replace generic finalize exception propagation with typed outcomes.
3. Publish finalize/export diagnostics to SSOT from background runtime paths.
4. Map typed outcomes to actionable UI copy in IGC file/export flows.
5. Remove screen-local failure taxonomy drift from replay/share helpers and use
   the shared diagnostics/message mapper.

`P7-4` Implement round-trip tolerance suite:

1. Add deterministic round-trip tests for standard and edge fixtures.
2. Add tolerance-threshold assertions with explicit failure messages.
3. Add parser-vs-lint parity tests for invalid `I` definitions and line-order
   failures.

`P7-5` Implement external compatibility harness:

1. Add fixture runner for external parser/validator checks.
2. Record parser-specific rejection details for diagnostics artifacts.
3. Add CI/local command path for reproducible execution.

`P7-6` Docs and evidence sync:

1. Populate all `phase7_evidence` artifacts.
2. Update `docs/ARCHITECTURE/PIPELINE.md` if runtime wiring changes.
3. Update IGC docs for lint taxonomy and user-facing diagnostics behavior.

`P7-7` Verification execution order (must run in this order):

1. `python scripts/arch_gate.py`
2. `./gradlew enforceRules`
3. `./gradlew :feature:igc:testDebugUnitTest --tests "com.example.xcpro.igc.*" --tests "com.example.xcpro.replay.Igc*"`
4. `./gradlew :feature:map:testDebugUnitTest --tests "com.example.xcpro.igc.*"`
5. `./gradlew :feature:igc:assembleDebug`
6. `./gradlew :feature:map:assembleDebug`
7. `./gradlew :feature:igc:connectedDebugAndroidTest --no-parallel`
8. `./gradlew :feature:map:connectedDebugAndroidTest --no-parallel`
9. `./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"`
10. `./gradlew connectedDebugAndroidTest --no-parallel`
11. `./gradlew testDebugUnitTest`
12. `./gradlew assembleDebug`

`P7-8` 100/100 claim criteria (all required, no partial credit):

1. All Phase 7 gates in Section 7.6 pass.
2. All required evidence artifacts are populated with pass results.
3. No open Phase 7 architecture deviations in
   `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md`.
4. Full verification order from `P7-7` passes in two consecutive runs.
5. Evidence demonstrates both module paths are covered:
   - `feature/igc` core formatter/parser/repository/UI
   - `feature/map` live runtime orchestration/wiring
6. Release scorecard for Phase 7 is exactly:
   - spec coverage/parity: `40/40`
   - automated test depth: `30/30`
   - determinism/architecture compliance: `20/20`
   - operational hardening/docs sync: `10/10`

## Phase 8 - Remaining Security/Validator Hardening

Deliverables:

- Formalize the trust/distribution model for the already-implemented production
  compatibility signer path.
- Define validator distribution and operator verification workflow.
- Decide whether XCPro will remain a compatibility export only, or pursue a
  formal approved-recorder program.
- Keep explicit non-approved-source labeling unless and until that program
  exists.

Gate:

- security design review
- no false claim of approved-recorder status

## Phase 9 - Release Hardening and Rollout

Deliverables:

- Final area rescoring (1..10).
- Operator runbook and troubleshooting guide.
- staged rollout flags and rollback plan.

Gate:

- all areas >=95/100
- full verification pack green

## Appendix A - Phase 3 Detailed Execution Pack

### A.1 Definition of Done (Go/No-Go)

- DoD item checklist (all required before gate pass):
  - [ ] Domain mapper exists for live-sample → `BRecord`.
  - [ ] Cadence policy enforces `1..5s` and default `1s`.
  - [ ] Validity policy classifies `A`/`V` with deterministic reason codes.
  - [ ] No-position dropout policy is implemented and tested.
  - [ ] Missing altitude policy is implemented and tested for pressure/GNSS separately.
  - [ ] No malformed sample can crash output generation.
  - [ ] UTC sequence remains strictly monotonic in output B records.
  - [ ] All tests listed in Section 3.4 are linked to passing artifact evidence.
  - [ ] No direct ViewModel/file-I/O handling in domain/data mappers.

- No-Go conditions:
  - [ ] Any `B` emitted while no active session exists.
  - [ ] `B` emitted with backward/duplicate HHMMSS without explicit rollover policy.
  - [ ] GNSS dropout handling can still finalize a session while landing confidence is uncertain.
  - [ ] Unbounded cadence or scheduler drift without explicit clamp.

### A.2 Phase 3 risks and mitigations

- Risk: position source jitter causes false `V` inflation.
  - Mitigation: define and test a 5s staleness gate plus max jump filter.
- Risk: session replay with `onGround` and missing fix can generate noisy B stream.
  - Mitigation: explicit policy to skip emission until first valid fix unless continuity rule explicitly allows.
- Risk: altitude source oscillation between pressure and GNSS availability.
  - Mitigation: precedence and hysteresis rules captured in `IgcBRecordValidationPolicy`.
- Risk: monotonic UTC breaks on restart/time drift.
  - Mitigation: monotonic guard and rollback-safe test at serializer boundary.
- Risk: performance hit at 1Hz on long flights.
  - Mitigation: bounded in-memory buffering and constant-space rolling writer adapter (pre-implemented in previous phases).

### A.3 Evidence template (for each gate)

- Evidence item: `phase_3_mappers`
  - `scope`: class file path and methods
  - `tests`: new/updated test class paths
  - `fixtures`: synthetic stream fixture list
  - `result`: pass/fail + timestamp
  - `notes`: policy decisions and open exceptions
- Evidence item: `phase_3_stream_dropout`
  - `scope`: dropout test cases and expected fixities
  - `tests`: unit + integration paths
  - `result`: pass/fail + sample outputs
- Evidence item: `phase_3_performance_longflight`
  - `scope`: 3h simulation and output cardinality
  - `tests`: performance fixture path + result summary
  - `result`: pass/fail + memory/time observation

### A.4 Implementation Work-Pack (No code yet)

- Work-Pack sequence (ordered, minimum-risk):
  1. Add contracts and shared domain models
     - New files:
       - `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcSampleToBRecordMapper.kt`
       - `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcBRecordCadencePolicy.kt`
       - `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcBRecordValidationPolicy.kt`
       - `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcSampleStreamErrorPolicy.kt`
       - `feature/igc/src/main/java/com/example/xcpro/igc/domain/IgcSamplingState.kt`
  2. Add unit test skeletons for pure policies/mappers (new test classes only)
     - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcBRecordCadencePolicyTest.kt`
     - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcBRecordValidationPolicyTest.kt`
     - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcSampleToBRecordMapperTest.kt`
     - `feature/igc/src/test/java/com/example/xcpro/igc/domain/IgcSampleStreamErrorPolicyTest.kt`
  3. Wire stream subscription in `IgcRecordingUseCase`
     - subscribe to SSOT sample stream in addition to flight-state stream.
     - only emit B payloads in `Recording` / `Finalizing`.
  4. Implement in-session continuity + session binding
     - keep rolling last-valid navigation sample.
     - maintain session-scoped sequence metadata.
  5. Implement validity and fallback logic
     - `A/V` classification
     - position drop-out policy
     - altitude fallback policy
     - malformed sample rejection path
  6. Add integration stream tests
     - `feature/map/src/test/java/com/example/xcpro/igc/usecase/IgcRecordingUseCaseBRecordStreamTest.kt`
     - long-flight fixture stream under `feature/map/src/test/resources/igc/phase3-long-flight-igc-stream.csv`
  7. Add evidence package
     - `docs/IGC/phase3_evidence/phase3_runbook.md`
     - `docs/IGC/phase3_evidence/phase3_gates.md`
  8. Close gate with all tests and evidence blocks attached to section 3.

- Test-driven ordering with fail-fast checkpoints:
  - Checkpoint A: cadence + validation policy tests green.
  - Checkpoint B: mapper outputs A/V transitions correctly for valid/missing/stale data.
  - Checkpoint C: long-flight integration test green.
  - Checkpoint D: end-to-end `Recording`/`Finalizing` lifecycle with dropouts green.
  - Checkpoint E: evidence pack complete (`phase3_gates.md`) and score re-pass >=95.

- New evidence names to capture
  - `docs/IGC/phase3_evidence/phase3-mappers-gate.md`
  - `docs/IGC/phase3_evidence/phase3-stream-dropout-gate.md`
  - `docs/IGC/phase3_evidence/phase3-longflight-gate.md`
  - `docs/IGC/phase3_evidence/phase3-monotonicity-gate.md`
  - `docs/IGC/phase3_evidence/phase3-latency-benchmark.md`

## 6) Required Verification Pack

Minimum per milestone:

```bash
python scripts/arch_gate.py
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When device/emulator available:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 7) Minimum Test Matrix

- formatter tests: record syntax and field encoding.
- formatter edge tests: coordinate carry, hemispheres, signed altitude width, fixed-width `B=35`.
- writer-adapter tests: byte-level CRLF invariants and deterministic byte output.
- writer stress tests: large-file output with no bare `LF/CR` and trailing `CRLF` policy.
- writer integration tests: formatter output -> writer bytes -> `IgcParser` round-trip.
- state machine tests: full lifecycle transition table, debounce spikes, finalize idempotency, touch-and-go.
- state machine timebase tests: monotonic-only contract and non-monotonic input handling.
- state machine recovery tests: process death snapshot/restore with same session continuity.
- integration tests: sample stream -> finalized file with drop-out transitions.
- storage tests: MediaStore write/query/share failure handling.
- storage directory tests: publish to `Downloads/XCPro/IGC/`, stage in app-private temp.
- recovery tests: process death mid-flight and finalize recovery.
- interoperability tests: parse with XCPro parser + external fixtures.
- naming tests: first-valid-fix UTC date + per-day `FF` incrementation.
- header policy tests: required `H` set and altitude datum headers.
- interaction tests: share/email/copy-to intents include valid URI grants and MIME handling.

## 8) Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| false claim of competition-grade validity | high | explicit mode labels, signer phase separated |
| missing satellite/security data | medium/high | keep optional records gated; do not fabricate data |
| battery/performance overhead | medium | bounded cadence, buffered writes, profiling gates |
| file corruption on crash | high | temp + atomic finalize strategy |
| clock/timestamp drift bugs | high | injected-clock tests + UTC formatting validation |
| spec drift or duplicate/corrupted spec sources | medium | lock canonical spec docs in `docs/IGC`, add spec-alignment checklist in phase gates |
| third-party upload flow fragility (provider/app changes) | medium | baseline on standards-based Android share flow; keep provider-specific integration optional |

## 9) Rollback Strategy

Rollback units:

1. recorder runtime hooks,
2. storage/download UI,
3. optional signature path.

Rollback rule:

- if production regressions appear, disable IGC recording feature flag,
  keep replay parser unaffected, and preserve previously written files.

## 10) Immediate Next Execution Pack

1. Phase 0 acceptance and architecture sign-off.
2. Phase 1 formatter skeleton + golden fixtures.
3. Phase 2 state machine implementation with fail-safe finalize.
4. Phase 3 B-record pipeline and validity policy.
5. Phase 4 header/task/event contracts and deterministic session assembly.
6. Phase 5 persistence + IGC Files UX implementation with evidence pack and score gate `>=98/100`.

This is the minimum path to first production-grade post-flight downloadable IGC output.
