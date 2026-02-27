# Genius_Level_Crash_Stability_Plan_2026-02-26.md

## Purpose

Deliver release-grade crash stability by eliminating repeated runtime OOM
failures without reducing sensor cadence or breaking MVVM/UDF/SSOT boundaries.

Read first:

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/PIPELINE.md`
4. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`
5. `docs/ARCHITECTURE/AGENT.md`

Use with:

- `docs/refactor/Agent-Execution-Contract-GeniusLevel-2026-02-24.md`

## 0) Metadata

- Title: Genius-Level Crash Stability Campaign
- Owner: XCPro Team
- Date: 2026-02-26
- Issue/PR: TBD
- Status: Active

## 0A) Baseline Evidence (2026-02-26)

Observed on connected device (`SM-S908E`):

- `adb logcat -b crash -d` shows repeated `OutOfMemoryError` for
  `com.example.openxcpro.debug`.
- Crash window: `2026-02-26 11:40:20` through `2026-02-26 16:37:36` (device local).
- Fatal count in that window: `49`.
- Total in current dropbox artifact window (`2026-02-25` + `2026-02-26`): `58` OOM crashes.
- Dominant signatures:
  - TLS/HTTP allocation frames (`ConscryptEngine.newResult`, `Linux.getsockname`)
  - terrain fetch path (`OpenMeteoElevationApi.fetchElevation`)
  - flow emission under pressure (`CombineKt`, wind input adapter)
- Process heap growth limit repeatedly hit: `268435456` bytes.

Baseline code findings:

- duplicate AGL/elevation compute path in parallel pipelines,
- AGL update fan-out creating overlapping coroutine work under high-frequency updates,
- unbounded elevation cache growth during long sessions,
- aggressive retry behavior after failed terrain fetches.

## 0B) Re-pass Missed Findings (Code Evidence)

1. Retry policy still uses call-count backoff, not monotonic time.
- `SimpleAglCalculator.kt:20`, `SimpleAglCalculator.kt:44`, `SimpleAglCalculator.kt:55`
- Risk: retries still happen every few seconds at current cadence under sustained failure.

2. Coordinate validation does not reject NaN/Infinity explicitly.
- `OpenMeteoElevationApi.kt:106`
- Risk: invalid samples can still produce outbound requests and allocation churn.

3. Replay path currently has no explicit "disable online terrain fetch" guard.
- unconditional AGL update call: `CalculateFlightMetricsUseCase.kt:181`
- request model lacks replay flag: `CalculateFlightMetricsUseCase.kt:461`
- request construction has no replay discriminator: `FlightDataEmitter.kt:74`
- Risk: replay can trigger terrain network path, hurting determinism and memory budget.

4. ADS-B metadata miss-attempt map has no TTL/cap pruning.
- state holder: `AircraftMetadataRepositoryImpl.kt:125`
- Risk: long sessions with high ICAO churn can accumulate retry entries indefinitely.

5. Audio lifecycle still has stop/release race potential.
- beep loop writer: `VarioBeepController.kt:97`
- stop without join: `VarioBeepController.kt:69`
- unsynchronized write/stop/release path: `VarioToneGenerator.kt:196`, `VarioToneGenerator.kt:343`
- engine release sequencing: `VarioAudioEngine.kt:354`
- Risk: `AudioTrack` write/release overlap can hit native instability during rapid lifecycle churn.

6. Map scale-bar listener lacks explicit detach path.
- one-way attach gate is not reset: `MapScaleBarController.kt:25`, `MapScaleBarController.kt:33`
- listener add path: `MapScaleBarController.kt:35`
- no matching remove path found in map lifecycle cleanup.
- Risk: stale callbacks after map churn can target outdated map state, and listener re-attach may be skipped for new map instances.

7. Airspace GeoJSON cache in runtime repository is unbounded.
- static cache maps: `AirspaceRepository.kt:41`, `AirspaceRepository.kt:42`
- stores full parsed GeoJSON per `(file,lastModified,selectedKey)` entry: `AirspaceRepository.kt:174`, `AirspaceRepository.kt:184`
- only same-file old-modified entries are pruned; selection variants remain: `AirspaceRepository.kt:181`
- Risk: repeated class-selection changes can accumulate large cached strings and increase heap pressure over long sessions.

## 1) Scope

- Problem statement:
  - the app enters repeated OOM crash loops under sustained operation.
- Why now:
  - current failure rate is release-blocking and impacts core flight reliability.
- In scope:
  - memory-pressure hardening in AGL/elevation and adjacent hot paths,
  - bounded resource behavior and failure backoff,
  - instrumentation and rollout safety gates.
- Out of scope:
  - feature redesign,
  - sensor cadence reduction,
  - unrelated UI refactor.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| fused AGL | `FlightDataRepository` via fusion output | `StateFlow<CompleteFlightData?>` | secondary AGL network fetch in flight-state path |
| terrain elevation cache | `SimpleAglCalculator` + `ElevationCache` | internal bounded cache | parallel unbounded maps in other layers |
| flight state | `FlightStateRepository` | `StateFlow<FlyingState>` | UI-local flight-state authority |

### 2.2 Dependency Direction

Flow remains:

`UI -> domain -> data`

Rules:

- no Android/network logic added to domain use-cases,
- no UI ownership of terrain/AGL policy,
- no hidden mutable global state for fetch throttling.

### 2.3 Time Base

| Value | Time Base | Why |
|---|---|---|
| live cadence/freshness | Monotonic | deterministic delta math |
| replay cadence | Replay | reproducibility |
| logs and labels | Wall | diagnostics only |

### 2.4 Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why |
|---|---|---|---|
| AGL network fetch in flight-state path | `FlightStateRepository` | fusion AGL only (`FlightDataRepository` SSOT) | remove duplicate expensive pipeline |
| high-frequency AGL launch behavior | unbounded per-update launches | single-worker coalesced queue in `FlightCalculationHelpers` | prevent coroutine/fetch pileup |
| terrain cache lifecycle | unbounded map | bounded LRU in `ElevationCache` | hard memory cap |
| failed fetch retry policy | immediate retry tendency | bounded skip/backoff in `SimpleAglCalculator` | prevent request storms |

## 3) Data Flow (Before -> After)

Before:

```
GPS/baro updates -> fusion AGL fetch + flight-state AGL fetch -> duplicate network work
                 -> unbounded cache growth -> heap pressure -> OOM crash loop
```

After:

```
GPS/baro updates -> fusion AGL path (single owner) -> coalesced fetch worker
                 -> bounded cache + retry backoff -> stable heap behavior
                 -> flight-state consumes SSOT AGL only
```

## 4) Implementation Phases

### Phase 0: Baseline Lock

- Goals:
  - capture crash signatures and counts,
  - lock architecture constraints and non-negotiables.
- Exit criteria:
  - baseline evidence documented and reproducible command set recorded.

### Phase 1: Immediate OOM Guardrails (Done)

- Changes:
  - removed duplicate AGL fetch path in `FlightStateRepository`,
  - added single-worker AGL coalescing in `FlightCalculationHelpers`,
  - bounded `ElevationCache` (LRU cap, synchronized access),
  - hardened `OpenMeteoElevationApi` cleanup + reduced debug allocation churn,
  - added failed-fetch backoff in `SimpleAglCalculator`.
- Exit criteria:
  - compile/test/build gates pass,
  - no immediate crash on launch smoke test.

### Phase 2: Memory and Concurrency Hardening

- Goals:
  - guarantee bounded in-flight terrain work.
- Planned additions:
  - per-grid singleflight dedupe for concurrent identical requests,
  - strict max concurrent terrain fetches (`1`),
  - monotonic time-based exponential retry backoff (replace call-count skip),
  - explicit circuit breaker open/half-open/closed transitions for repeated failures,
  - strict coordinate finite/range gate before any URL construction.
- Exit criteria:
  - stress tests show bounded in-flight fetch count and stable memory profile.

### Phase 3: Failure-Mode Degradation Policy

- Goals:
  - graceful behavior under network loss or persistent API failure.
- Planned additions:
  - replay-mode policy: disable online terrain fetches and keep deterministic fallback behavior,
  - stale-AGL age tagging instead of rapid null churn,
  - policy tests for degraded modes (including replay no-network fetch assertions).
- Exit criteria:
  - no crash and predictable fallback behavior during prolonged offline runs.

### Phase 3B: Non-Terrain Memory Pressure Hardening

- Goals:
  - close secondary unbounded-memory paths that can amplify OOM risk.
- Planned additions:
  - add TTL/cap pruning for `onDemandAttemptByIcao24` in ADS-B metadata repository,
  - bound `AirspaceRepository` GeoJSON cache with explicit LRU cap + stale-file pruning policy,
  - add low-overhead counters for retry-map size and prune events.
- Exit criteria:
  - bounded retry-map growth under synthetic high-ICAO churn tests,
  - bounded airspace cache growth under class-selection churn tests.

### Phase 3C: Native Stability Hardening (Audio/Map Lifecycle)

- Goals:
  - eliminate known stop/release races and stale lifecycle callbacks that map to historical tombstone families.
- Planned additions:
  - serialize `VarioToneGenerator` state transitions with a single lock/ownership path,
  - make `VarioBeepController.stop()` await loop quiescence before tone-generator stop/release,
  - add scale-bar listener handle storage and explicit remove on map detach/destroy,
  - add stale-callback guards for map lifecycle churn.
- Exit criteria:
  - rapid start/stop stress does not produce audio-native tombstones,
  - repeated map attach/detach/style churn does not produce map-native tombstones.

### Phase 4: Observability and Telemetry

- Goals:
  - detect early drift before user-visible crash loops.
- Planned additions:
  - breadcrumbs for AGL queue depth, cache size, retry backoff state,
  - OOM context capture and crash signature bucketing,
  - debug-only memory watermark logs with rate limit.
- Exit criteria:
  - dashboards/alerts can distinguish regression source within one release cycle.

### Phase 5: Release Qualification and Rollout

- Goals:
  - production-confidence validation with staged rollout controls.
- Planned steps:
  - 60-120 minute on-device soak tests on representative devices,
  - staged rollout (`internal -> 5% -> 25% -> 100%`) with crash SLO gates,
  - remote kill-switch for terrain online fetch if field regression appears.
- Exit criteria:
  - no OOM crash clusters in soak and early rollout cohorts.

## 5) Test Plan

- Unit tests:
  - AGL coalescing queue behavior,
  - retry/backoff and circuit-breaker transitions (time-based assertions),
  - cache eviction and bounded size guarantees (terrain + airspace caches).
- Integration tests:
  - long-run synthetic sensor stream with terrain fetch enabled,
  - replay determinism parity to ensure no replay drift,
  - replay mode asserts no online terrain fetch calls,
  - invalid coordinate (`NaN`/`Infinity`) inputs rejected before network path,
  - airspace class-toggle churn keeps cache size under configured cap.
- Lifecycle/race tests:
  - rapid audio start/stop/release loop does not crash and does not write after release,
  - map lifecycle churn test validates listener detach/reattach invariants.
- Device validation:
  - memory profile checkpoints using `dumpsys meminfo`,
  - crash-buffer verification with `adb logcat -b crash`.

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

When relevant:

```bash
./gradlew :app:connectedDebugAndroidTest --no-parallel "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| hidden memory pressure outside terrain path | High | add telemetry and staged rollout gates | XCPro Team |
| behavior drift from aggressive throttling | Medium | keep sensor cadence fixed; constrain only fetch path | XCPro Team |
| replay/live divergence from new logic | Medium | replay parity tests before rollout | XCPro Team |
| observability overhead | Low | debug-only sampling and rate limiting | XCPro Team |

## 7) Acceptance Gates

- zero fatal OOM in 2-hour soak tests on target devices,
- stable heap profile (no monotonic growth trend to limit),
- no monotonic cache growth in configured capped caches (terrain/metadata/airspace),
- required checks pass (`enforceRules`, `testDebugUnitTest`, `assembleDebug`),
- no architecture-rule violations introduced,
- no new permanent deviations unless approved with issue/owner/expiry,
- quality rescore at close:
  - Architecture cleanliness >= 4.7/5
  - Maintainability/change safety >= 4.7/5
  - Test confidence on risky paths >= 4.7/5
  - Release readiness >= 4.7/5

## 8) Rollback Plan

- Each phase must ship in revertable slices.
- Recovery steps:
  1. Revert only failing phase commits.
  2. Keep harmless observability and tests where possible.
  3. Re-run required checks.
  4. Reattempt with narrower blast radius.
