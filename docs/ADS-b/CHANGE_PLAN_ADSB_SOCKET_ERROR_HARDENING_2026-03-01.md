# CHANGE PLAN - ADS-B Socket Error Hardening (2026-03-01)

## 0) Metadata

- Title: ADS-B socket error hardening (polling + metadata sync + auth retry)
- Owner: XCPro Team
- Date: 2026-03-01
- Issue/PR: TBD
- Status: Complete

Execution update (2026-03-01):
- Implemented:
  - ADS-B metadata HTTP client split with download-safe timeout profile.
  - Metadata CSV temp-file staging so import runs after HTTP response closure.
  - Token transient-failure cooldown to avoid per-poll auth retry storms.
  - Metadata importer batch flushing using `INSERT_BATCH_SIZE`.
- Verification:
  - `./gradlew.bat enforceRules`
  - `./gradlew.bat testDebugUnitTest`
  - `./gradlew.bat assembleDebug`

## 1) Scope

- Problem statement:
  - Users report regular ADS-B socket errors during normal operation.
  - Current runtime classifies and retries network failures, but there are still avoidable implementation-level timeout amplifiers in metadata sync and auth paths.
- Why now:
  - Frequent socket errors degrade trust and reduce traffic availability.
  - The current ADS-B docs and runtime contract do not give future agents a single active execution plan for this issue class.
- In scope:
  - ADS-B polling client timeout/retry hardening.
  - ADS-B metadata sync transport/import flow hardening.
  - OpenSky token transient failure cooldown hardening.
  - Documentation updates for future agent execution.
- Out of scope:
  - ADS-B visual design changes.
  - OGN protocol/runtime changes.
  - Provider/API contract changes outside XCPro runtime behavior.
- User-visible impact:
  - Fewer repeated socket timeout/error episodes.
  - More stable ADS-B target updates in marginal mobile networks.

## 2) Re-pass Findings (What can cause repeated socket errors)

1. Metadata sync keeps a long-running HTTP call open while importing rows into Room:
   - `OpenSkyMetadataClient.downloadCsv(...)` executes importer work inside the active response scope.
   - ADS-B HTTP client uses strict `callTimeout(15s)`.
2. Metadata importer currently upserts per row (not batch inserts), increasing sync duration under one active socket response.
3. ADS-B polling and ADS-B metadata use the same unqualified `OkHttpClient` profile, so metadata sync latency can contribute to recurrent timeout pressure patterns.
4. Token fetch transient failures have no explicit cooldown cache, so auth fetch can be retried each poll cycle under unstable auth network.

## 3) Architecture Contract

### 3.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| ADS-B live targets and connection snapshot | `AdsbTrafficRepository` | `targets`, `snapshot` `StateFlow` | UI/runtime authoritative mirrors |
| ADS-B metadata sync state | `AircraftMetadataSyncRepository` | `syncState` `StateFlow` | ViewModel-owned sync state copies |
| OpenSky token cache state | `OpenSkyTokenRepository` | access-state methods | Parallel token caches in caller layers |
| Network timeout/retry policy values | DI + repository policy classes | injected client config + policy methods | Inline scattered timeout literals |

### 3.2 Dependency Direction

Confirmed flow remains:

`UI -> domain/use-case -> data`

- Modules/files touched:
  - `feature/map` ADS-B data/domain/DI docs and code paths.
- Boundary risk:
  - Low, if transport changes remain inside data adapters and repository policy classes.

### 3.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Metadata download lifetime vs import lifetime | `OpenSkyMetadataClient.downloadCsv` closure scope | Dedicated metadata-sync adapter path (download first, import second) | Prevent long DB import under single active HTTP call timeout | Metadata sync tests + timeout behavior tests |
| Token transient failure pacing | Implicit poll-loop cadence | `OpenSkyTokenRepository` cooldown policy | Avoid repeated auth socket pressure on transient failures | Token repository tests |

### 3.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `AircraftMetadataSyncRepositoryImpl.runSyncNow` | Import work runs in HTTP response callback scope | Explicit two-step: download to temp file, then import from file stream | Phase 2 |

### 3.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Poll retry backoff/circuit windows | Monotonic | Runtime retry stability without wall-clock drift |
| Token cooldown windows | Monotonic | Deterministic retry gating in live runtime |
| Metadata checkpoint timestamps | Wall | User-facing sync status and persisted bookkeeping |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 3.4 Threading and Cadence

- Dispatcher ownership:
  - `IO`: HTTP, file download, CSV parsing, Room I/O.
  - `Default`: non-I/O merge/filter computations only.
  - `Main`: UI rendering only.
- Primary cadence/gating sensor:
  - ADS-B polling repository loop cadence and policy.
- Hot-path latency budget:
  - Maintain existing poll cadence floor and bounded backoff behavior.

### 3.5 Replay Determinism

- Deterministic for same input: Yes (replay pipeline unaffected).
- Randomness used: Existing retry jitter in live ADS-B only; not used in replay pipeline.
- Replay/live divergence rules:
  - ADS-B networking is live-only and must not alter replay deterministic outputs.

### 3.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Metadata import under active HTTP call timeout | ARCHITECTURE + CODING_RULES threading/I-O ownership | Unit tests + code review | `OpenSkyMetadataClientTest`, `AircraftMetadataSyncRepositoryImplTest` |
| Token transient failure retry storm | CODING_RULES hot-path safety | Unit tests | `OpenSkyTokenRepositoryTest` |
| Timeout policy drift across ADS-B clients | CODING_RULES maintainability | Unit tests + review | DI module tests/review + provider client tests |
| SSOT boundary drift | ARCHITECTURE SSOT/UDF | enforceRules + review | `./gradlew enforceRules` |

## 4) Data Flow (Before -> After)

Before:

`Metadata HTTP response open -> CSV parse + Room import in same call scope -> timeout/cancel risk -> repeated sync retry`

After:

`Metadata HTTP download to temp file -> close response -> import file stream into Room with batching -> shorter socket lifetime and lower timeout risk`

Before:

`Poll cycle -> token transient failure -> immediate retry next poll`

After:

`Poll cycle -> token transient failure -> repository-scoped cooldown -> reduced repeated auth socket failures`

## 5) Implementation Phases

### Phase 0 - Baseline and Regression Lock

- Goal:
  - Reproduce and lock current failure-prone behaviors with tests before changing logic.
- Files:
  - `feature/map/src/test/java/com/example/xcpro/adsb/OpenSkyTokenRepositoryTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/metadata/OpenSkyMetadataClientTest.kt`
  - `feature/map/src/test/java/com/example/xcpro/adsb/metadata/AircraftMetadataSyncRepositoryImplTest.kt`
- Tests:
  - Add baseline tests for long download/import path and transient token failure pacing.
- Exit criteria:
  - Baseline tests fail for known bug paths (or document why not reproducible) and current behavior is captured.

### Phase 1 - ADS-B HTTP Client Policy Split

- Goal:
  - Decouple live polling client policy from metadata-sync client policy.
- Planned changes:
  - Introduce dedicated ADS-B metadata HTTP qualifier/client.
  - Keep polling client tuned for live cadence; tune metadata client for large downloads and variable mobile latency.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/di/AdsbNetworkModule.kt`
  - ADS-B metadata client constructors using the dedicated qualifier.
- Tests:
  - DI wiring/unit coverage for qualifiers and client injection.
- Exit criteria:
  - Polling and metadata traffic no longer share the same timeout profile.

### Phase 2 - Metadata Sync Transport + Import Hardening

- Goal:
  - Remove long-running import work from active HTTP response scope.
- Planned changes:
  - Download metadata source to temp file first.
  - Import from file stream after response closure.
  - Apply insert batching (`INSERT_BATCH_SIZE`) in importer path.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/OpenSkyMetadataClient.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/AircraftMetadataSyncRepositoryImpl.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/AircraftMetadataImporter.kt`
  - `feature/map/src/main/java/com/example/xcpro/adsb/metadata/data/AircraftMetadataDao.kt` (if batch API needed)
- Tests:
  - Metadata sync repository tests for staged download/import behavior.
  - Importer tests for batching and promotion safety.
- Exit criteria:
  - No DB import work runs inside active HTTP response scope.
  - Metadata import uses bounded batch writes.

### Phase 3 - Token Transient Failure Cooldown

- Goal:
  - Prevent auth endpoint transient failures from triggering repeated per-poll socket failures.
- Planned changes:
  - Add monotonic cooldown for transient token fetch failures in token repository.
  - Preserve credential-rejected behavior and auth-mode semantics.
- Files:
  - `feature/map/src/main/java/com/example/xcpro/adsb/OpenSkyTokenRepository.kt`
- Tests:
  - Extend `OpenSkyTokenRepositoryTest` for transient failure cooldown and recovery behavior.
- Exit criteria:
  - Repeated transient failures are rate-limited by repository cooldown policy.

### Phase 4 - Verification and Docs Sync

- Goal:
  - Lock behavior with tests and keep docs agent-ready.
- Files:
  - `docs/ADS-b/ADSB.md`
  - `docs/ADS-b/README.md`
  - `docs/ADS-b/archive/ADSB_Improvement_Plan.md`
  - `docs/ARCHITECTURE/PIPELINE.md` (only if runtime wiring changes in implementation)
- Tests:
  - Full ADS-B unit suite and required repo checks.
- Exit criteria:
  - Docs reference one active execution plan for this hardening slice.
  - Verification evidence captured.

## 6) Test Plan

- Unit tests:
  - Token cooldown behavior and single-flight correctness.
  - Metadata download/import step separation.
  - Import batching/promotion safety.
- Replay/regression tests:
  - Confirm replay behavior unchanged.
- UI/instrumentation tests:
  - Not required for transport-layer hardening unless behavior regressions are observed.
- Degraded/failure-mode tests:
  - DNS/timeout/connect/no-route/TLS classifications remain mapped and recovered by policy.
  - Metadata download failure fallback path remains correct.
- Boundary tests for removed bypasses:
  - No importer execution inside HTTP response scope.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Optional when relevant:

```bash
./gradlew connectedDebugAndroidTest
```

## 7) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Timeout values tuned too high increase stale waits | Medium | Keep bounded max timeouts and preserve retry/circuit policy | XCPro Team |
| Batch import changes dedupe behavior | High | Keep existing dedupe SQL semantics and add regression tests | XCPro Team |
| Token cooldown too aggressive delays auth recovery | Medium | Keep short cooldown and clear on successful fetch | XCPro Team |
| Added DI qualifiers cause wiring errors | Low | Add injection tests and compile-time verification | XCPro Team |

## 8) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`.
- No duplicate SSOT ownership introduced.
- Time base handling explicit in code and tests.
- Replay behavior remains deterministic and unchanged.
- `KNOWN_DEVIATIONS.md` unchanged unless explicitly approved.

## 9) Rollback Plan

- Revert independently:
  - DI client split.
  - Metadata download/import refactor.
  - Token cooldown policy.
- Recovery steps:
  - Restore prior single-client wiring and importer path if critical regression appears.
  - Keep tests that identify the original socket-timeout amplifiers for follow-up fix.
