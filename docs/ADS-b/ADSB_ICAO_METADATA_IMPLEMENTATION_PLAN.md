# ADSB_ICAO_METADATA_IMPLEMENTATION_PLAN.md

Last updated: 2026-02-10
Status: Draft implementation contract

## 0) Goal

Implement ICAO24 metadata enrichment for ADS-B so tapping an aircraft can show:

- registration
- typecode
- model
- manufacturer
- operator (when available)

This plan is mandatory for release-grade implementation and must comply with:

- `docs/ARCHITECTURE/ARCHITECTURE.md`
- `docs/ARCHITECTURE/CODING_RULES.md`
- `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

## 0A) Mandatory Preflight Read Order

Before implementation starts, read architecture docs in this exact order
(per `docs/ARCHITECTURE/README_FOR_CODEX.md`):

1. `docs/ARCHITECTURE/ARCHITECTURE.md`
2. `docs/ARCHITECTURE/CODING_RULES.md`
3. `docs/ARCHITECTURE/CODEBASE_CONTEXT_AND_INTENT.md`

Release gate: implementation is not complete if this preflight contract is skipped.

## 1) Current Baseline and Gap

Current ADS-B implementation already does:

- stable identity by ICAO24
- OpenSky fetch with `extended=1`
- category parse and icon mapping
- details sheet showing category and live kinematics

Current missing capability:

- no local ICAO metadata store
- no metadata sync pipeline
- no metadata join into selected ADS-B target details

## 1A) Existing Runtime Behavior to Preserve (Non-Regression Baseline)

Current ADS-B behavior that must remain unchanged unless explicitly approved:

1. receive radius remains 15 km (`RECEIVE_RADIUS_KM = 15`)
2. airborne filter remains altitude >100 ft (`MIN_AIRBORNE_ALTITUDE_M = 30.48`)
3. airborne filter remains speed >40 kt (`MIN_AIRBORNE_SPEED_MPS = 20.5778`)
4. FLARM-sourced rows (`position_source = 3`) remain excluded
5. displayed target cap remains 30 (`MAX_DISPLAYED_TARGETS = 30`)

Current details UI debt to resolve in this feature:

1. details sheet currently labels category as `Type` and `Category`
2. it must be migrated to explicit `Emitter category` (label + raw int)

## 2) Architecture Compliance Contract

This implementation must satisfy all of the following:

1. Dependency direction: `UI -> domain(use cases) -> data(repositories)`.
2. ViewModel purity: ViewModels depend on use cases only.
3. SSOT: metadata ownership is in a repository, not in ViewModel/UI.
4. Determinism: all freshness/staleness logic uses injected `Clock`.
5. DI only: all components constructed via Hilt.
6. Lifecycle: UI collection via `collectAsStateWithLifecycle`.
7. No hidden mutable global state.
8. No domain business logic in composables.
9. Map runtime types (MapLibre) remain UI/runtime-layer only.
10. Errors must be modeled as typed data, not swallowed exceptions.
11. Keep `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` unchanged (no new deviations).
12. No new production Kotlin vendor strings in user-facing/public APIs.
13. No new mutable `object` singletons for metadata state/sync.
14. Preferences or sync checkpoints are repository-owned (no ViewModel preference access).
15. Use-cases depend on repositories only (no direct Worker/DAO/UI dependencies).
16. Domain/use-case code stays Android-framework free.
17. Repositories/use-cases/ViewModels remain testable without Android framework runtime.
18. Repositories expose immutable `Flow`/`StateFlow` only; no mutable state leaks.
19. New production Kotlin in this feature remains ASCII-only.
20. Metadata feature must not add new Android permissions.
21. Metadata enrichment must not add work to map render hot paths.
22. One-off sync intents/events use `SharedFlow` semantics, not persisted `StateFlow`.
23. New flow members follow `_state` private mutable and `state` public immutable naming.
24. Metadata implementation must not change map-position ownership (`FlightDataRepository` remains source).
25. Avoid shared cross-task utility abstractions unless explicitly justified and documented.
26. New use-case/state types follow naming conventions (`XUseCase`, `XUiState` where applicable).
27. Any lifecycle-collection exception in previews/tests is explicitly commented.
28. New/updated docs for this feature should avoid non-ASCII punctuation to prevent Windows terminal encoding drift.

## 2A) Explicit SSOT Ownership Map

Authoritative owners after implementation:

1. Remote metadata source version (`key`, `etag`, `lastModified`) -> `AircraftMetadataSyncRepository`.
2. ICAO metadata rows -> `AircraftMetadataRepository`.
3. Live ADS-B kinematics -> `AdsbTrafficRepository`.
4. Enriched selected target view data -> `AdsbMetadataEnrichmentUseCase` output consumed by `MapScreenViewModel`.
5. UI rendering state -> `MapScreenViewModel` immutable `StateFlow`.

No duplicate ownership is allowed.

## 2B) Required State Machines

`MetadataSyncState` (typed and explicit):

1. `Idle`
2. `Scheduled`
3. `Running`
4. `PausedByUser(lastSuccessWallMs?)`
5. `Success(lastSuccessWallMs, sourceKey, etag)`
6. `Failed(reason, lastAttemptWallMs, retryAtWallMs?)`

Allowed transitions:

1. `Idle -> Scheduled`
2. `Scheduled -> Running`
3. `Running -> Success`
4. `Running -> Failed`
5. `Failed -> Scheduled`
6. `Success -> Scheduled` (periodic refresh)
7. `Scheduled -> PausedByUser` (overlay disabled before execution)
8. `Running -> PausedByUser` (worker cancellation requested immediately; transition on cancellation acknowledgement)
9. `Success -> PausedByUser` (overlay disabled)
10. `PausedByUser -> Scheduled` (overlay re-enabled)
11. `Failed -> PausedByUser` (overlay disabled after failure)

`MetadataAvailability` for selected aircraft:

1. `Ready`
2. `Missing`
3. `SyncInProgress`
4. `Unavailable(errorSummary)`

UI must render these states honestly and never fabricate type/model data.

## 2C) Threading and Dispatcher Contract

1. Network download and DB I/O run on `Dispatchers.IO`.
2. CSV parsing and normalization may run on `Dispatchers.Default` or `Dispatchers.IO`, but never on Main.
3. ViewModel transforms remain lightweight and non-blocking.
4. No `runBlocking`, `GlobalScope`, or manual thread management.

## 2D) Package and Layer Boundaries

Required package shape (names can vary, layering cannot):

1. `adsb/metadata/data/*` for DB entities/dao/import/sync repositories.
2. `adsb/metadata/domain/*` for use-cases and pure models.
3. `adsb/metadata/ui/*` only if UI-specific mapping helpers are needed.

Hard rules:

1. UI code must not import metadata data-layer classes directly.
2. Repository/data layer must not import Compose/UI classes.
3. Domain/use-case layer must not import Android framework UI/runtime types.

## 2E) State Exposure and Event Modeling

1. Metadata repositories may keep mutable internals, but only expose immutable `Flow`/`StateFlow`.
2. One-off sync actions are modeled as `SharedFlow` events; they are not persisted as UI truth state.
3. Selected target enrichment is derived from flows (`selectedIcao24` + ADS-B targets + metadata), not manually mirrored caches.
4. New flow member naming follows repo convention (`_state` private mutable, `state` public immutable).

## 3) External Data Contract

Primary source:

- OpenSky metadata bucket listing:
  `https://s3.opensky-network.org/data-samples?list-type=2&prefix=metadata/`

Selection rule:

- Prefer latest `metadata/aircraft-database-complete-YYYY-MM.csv`.
- Fallback to `metadata/aircraftDatabase.csv` only if no complete snapshot exists.

Selection parsing requirements:

1. match complete snapshots with strict regex:
   `^metadata/aircraft-database-complete-(\\d{4})-(\\d{2})\\.csv$`
2. parse year/month numerically and choose max `(year, month)`; do not rely on plain string sort only
3. ignore non-matching keys (`.zip`, README, doc tables, incomplete monthly keys)

Bucket listing pagination requirement:

1. file discovery must handle S3 XML pagination (`IsTruncated`, `NextContinuationToken`)
2. selection is performed after aggregating all pages
3. if pagination parse fails mid-stream, fallback path is used and previous local metadata is preserved

Notes from upstream README:

- `aircraftDatabase.csv` and `aircraftDatabase-YYYY-MM.csv` are incomplete.
- `aircraft-database-complete-YYYY-MM.csv` is complete, but column schema differs.

Implementation requirement:

- importer must map by header names, never fixed column indexes.

Source resilience requirement:

1. if bucket listing fails (network error, parse error, endpoint change), do not block feature rollout
2. first fallback source is `metadata/aircraftDatabase.csv` from the same metadata bucket
3. second fallback source is `https://opensky-network.org/datasets/metadata/aircraftDatabase.csv`
4. emit typed sync state describing fallback path used
5. keep last good local metadata active on any source-discovery failure
6. if selected complete-snapshot download fails (404/403/5xx/timeout), retry with direct fallback dataset URL before emitting terminal failure
7. if fallback dataset download also fails, keep last good local metadata and emit typed terminal failure

## 4) Target Design (SSOT and Data Flow)

Authoritative flow:

1. `AircraftMetadataSyncRepository` discovers latest metadata file and imports to local DB.
2. `AircraftMetadataRepository` serves lookup by ICAO24 from local DB.
3. `AdsbMetadataEnrichmentUseCase` combines live ADS-B targets with metadata lookups.
4. `MapScreenViewModel` consumes only use-case outputs and exposes selected enriched target.
5. `AdsbMarkerDetailsSheet` renders enriched fields.

No layer may bypass repository ownership.

## 4A) Pipeline Update Contract

This feature must update the architecture pipeline docs when code lands:

- update `docs/ARCHITECTURE/PIPELINE.md`
- include metadata sync and enrichment path in the ADS-B branch of the flow
- update both the quick-map section and the primary files index section

Required ADS-B pipeline after this feature:

1. OpenSky states -> `AdsbTrafficRepository`
2. ICAO metadata sync worker/importer -> `AircraftMetadataRepository` (SSOT)
3. ADS-B + metadata join -> `AdsbMetadataEnrichmentUseCase` output model
4. `MapScreenViewModel` selected target flow
5. `AdsbMarkerDetailsSheet` render
6. `MapOverlayManager` and `AdsbTrafficOverlay` keep rendering from live ADS-B state only

Pipeline invariants:

1. overlay marker rendering does not block on metadata readiness
2. metadata sync never mutates live ADS-B kinematics
3. details enrichment is additive only (no behavioral changes to selection/filter/range)

## 4B) Map Position Non-Regression Contract

1. Map position ownership remains unchanged (`FlightDataRepository` path).
2. `MapScreenViewModel` must not add direct raw-sensor access for metadata work.
3. Metadata feature must not alter camera update cadence or source.

## 5) Proposed Components and File Map

New package root:

- `feature/map/src/main/java/com/example/xcpro/adsb/metadata/`

Data storage:

- `AircraftMetadataEntity.kt`
- `AircraftMetadataDao.kt`
- `AdsbMetadataDatabase.kt`

Repository and importer:

- `AircraftMetadataRepository.kt` (interface)
- `AircraftMetadataRepositoryImpl.kt`
- `AircraftMetadataSyncRepository.kt` (interface)
- `AircraftMetadataSyncRepositoryImpl.kt`
- `AircraftMetadataSyncCheckpointStore.kt` (persistent sync checkpoints: source key/etag/lastSuccess/error)
- `AircraftMetadataImporter.kt`
- `AircraftMetadataFileSelector.kt`
- `AircraftMetadataCsvParser.kt`
- `AircraftMetadataSyncPolicy.kt`
- `AircraftMetadataSyncState.kt`

Background sync:

- `AircraftMetadataSyncWorker.kt` (`@HiltWorker`)
- `AircraftMetadataSyncScheduler.kt`

Domain/use case boundary:

- `AdsbMetadataEnrichmentUseCase.kt` (required)
- `ScheduleAircraftMetadataSyncUseCase.kt` (required)
- `ObserveAircraftMetadataSyncStateUseCase.kt` (required)

UI model update:

- keep `AdsbTrafficUiModel` focused on live overlay fields used in render paths
- add a dedicated selected-target details model (wrapper/enriched view model) for metadata fields
- metadata enrichment must apply to selected target details flow, not per-frame overlay target rendering

DI wiring:

- add a dedicated metadata DI module for metadata repository/sync/import bindings
- use `MapBindingsModule` only for explicit bridge bindings required by existing map wiring
- keep metadata repositories scoped in `SingletonComponent`
- keep use-cases scoped to ViewModel injection paths only

App wiring for WorkManager:

- update `app/src/main/java/com/example/xcpro/XCProApplication.kt`
  to provide `Configuration.Provider` with `HiltWorkerFactory`

Dependencies:

- add Room runtime/ktx/compiler dependencies in `feature:map` module
- add WorkManager runtime (`work-runtime-ktx`) in `feature:map` module (worker host)
- add `androidx.hilt:hilt-work` in `feature:map` module (for `@HiltWorker`)
- add `androidx.hilt:hilt-work` in `app` module (for `HiltWorkerFactory`)
- add WorkManager runtime (`work-runtime-ktx`) in `app` module for application-level worker configuration
- add/verify version-catalog aliases for Room + WorkManager + hilt-work if missing

## 5A) Database Migration and Versioning Contract

1. Metadata DB schema changes require explicit Room migrations.
2. `fallbackToDestructiveMigration` is forbidden for release path.
3. Add migration tests for every schema bump touching metadata tables.
4. Import staging tables must be included in migration coverage.

## 6) Data Model

DB entity (lowercase `icao24` primary key):

- `icao24: String`
- `registration: String?`
- `typecode: String?`
- `model: String?`
- `manufacturerName: String?`
- `operator: String?`
- `operatorCallsign: String?`
- `icaoAircraftType: String?`
- `updatedAtEpochMs: Long`

Normalization rules:

1. trim all text
2. convert empty string to null
3. normalize ICAO24 to lowercase `[0-9a-f]{6}`

## 7) Sync Strategy

All thresholds and intervals are centralized in `AircraftMetadataSyncPolicy` (no scattered magic numbers):

- periodic cadence (days)
- retry backoff bounds
- batch size
- metadata staleness warning threshold

### 7.1 Discovery

1. list metadata bucket XML
2. parse keys matching:
   - `aircraft-database-complete-YYYY-MM.csv`
3. choose newest by numeric `(year, month)` comparison (not plain lexical sort)
4. persist chosen `key`, `lastModified`, and `etag`

### 7.2 Import

1. stream download via OkHttp (no full-file memory load)
2. parse CSV row-by-row
3. map columns by header names (case-insensitive)
4. upsert in batches (for example 1000 rows per transaction)
5. replace/import atomically:
   - write into staging table
   - swap to active table in one DB transaction

Header robustness requirements:

1. strip UTF-8 BOM from the first header token if present
2. trim header tokens before matching
3. support alias header names for key fields (for example snake_case vs camelCase variants)
4. treat missing optional headers as nullable fields, not import failure

Duplicate ICAO24 resolution (deterministic):

1. if multiple rows share the same normalized ICAO24 in one import, resolve by deterministic rule
2. prefer row with more non-empty primary fields (`registration`, `typecode`, `model`)
3. tie-breaker: keep the later row in file order
4. resolution rule must be unit tested and documented in code comments

Atomic swap safety guard:

1. do not swap staging -> active if import did not complete successfully
2. do not swap staging -> active when staging row count is zero
3. on guard trigger, keep previous active table and emit typed failure state

Lookup scalability guard:

1. metadata lookup by ICAO list must chunk `IN (...)` queries to stay below SQLite bind limits
2. chunking behavior must be deterministic and covered by unit tests

### 7.2A Large File Resilience

1. Import pipeline must handle at least the current complete file size class (>100 MB) without OOM.
2. Worker and importer must support cancellation cleanly (stop work without corrupting active table).
3. Temporary files/partial artifacts are cleaned after success, failure, or cancellation.
4. If content metadata (`etag`/`lastModified`) is unchanged, skip download and parse.

### 7.3 Scheduling

1. initial one-time sync is requested via use-case when ADS-B overlay is first enabled
2. periodic sync every 30 days with `NetworkType.UNMETERED` and battery-not-low
3. manual "retry now" hook can be debug-only at first
4. scheduler operations must be idempotent (safe repeated calls)
5. initial scheduling trigger must be based on overlay preference transition (`false -> true`), not runtime streaming state
6. metadata scheduling must not be gated by map visibility or sensor-start gating
7. bootstrap path required: if overlay preference is already true on cold app start and metadata has never succeeded, schedule initial one-time sync
8. initial one-time sync should use `NetworkType.CONNECTED` (not `UNMETERED`) to avoid first-run deadlock
9. periodic work is cancelled when overlay preference is turned off
10. unique work names and policies must prevent duplicate parallel imports
11. disabling overlay cancels both pending one-time and periodic metadata sync work
12. use explicit unique-work policies:
    - one-time sync: `enqueueUniqueWork(..., ExistingWorkPolicy.KEEP, ...)`
    - periodic sync: `enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.UPDATE, ...)`
13. disabling overlay while running requests worker cancellation immediately and transitions to `PausedByUser`
14. re-enable after rapid disable/enable race must still guarantee one-time sync is eventually enqueued

### 7.4 Failure Policy

1. if sync fails, keep existing DB
2. metadata sync state reports typed error + last success time
3. UI keeps working with live ADS-B and shows "Metadata not available"
4. failures must propagate as data (`MetadataSyncState.Failed`), not silent catches

### 7.5 Replay and Source-Gating Compatibility

1. Metadata sync/enrichment must not interfere with existing live/replay source gating.
2. Replay-mode determinism tests must use fixed metadata fixtures (no live network dependency).
3. If metadata is unavailable in replay, UI must stay explicit (`Missing`/`Unavailable`) and stable.

## 8) Runtime Join and UI Contract

Selected target details sheet must always show:

- ICAO24
- callsign (if available)
- altitude, speed, track, vertical rate
- emitter category label/int

When metadata exists, additionally show:

- registration
- typecode
- model
- manufacturer
- operator/operator callsign
- icao aircraft type

When metadata missing:

- show explicit fallback text: `Metadata not available`
- show sync status text derived from typed sync state (`Scheduled`, `Running`, `Success`, `Failed`) plus last-success/last-error context when available

Metadata uncertainty contract:

1. expose `MetadataAvailability` on selected target UI model
2. never display guessed registration/typecode/model as authoritative values
3. if metadata becomes unavailable mid-session, keep UI stable and transition through typed state
4. on selected target change (`icao24` changes), metadata fields from prior target must be cleared immediately; never show previous target metadata for new selection

Anti-flicker contract for selected target metadata:

1. For the same selected ICAO24, do not oscillate between `Ready` and `Missing` during a single in-flight sync attempt.
2. Transition to `Missing` only after sync state is terminal for that attempt (`Success` with no row, or `Failed`).
3. Always prefer explicit unknown/missing state over guessed aircraft type/model.

Important:

- overlay marker rendering does not depend on metadata readiness
- no blocking UI wait for metadata sync
- no per-frame DB lookups in map rendering paths

## 9) Timebase and Freshness

Rules:

1. metadata sync timestamps use injected `Clock.nowWallMs()` for persistence/audit only
2. live ADS-B display age/staleness continues using current monotonic flow rules
3. never mix monotonic deltas with wall-time deltas

## 10) Security, Privacy, and Logging

1. do not log location data in release builds
2. do not log full aircraft metadata rows in release builds
3. keep upstream source URL configurable in one constant/provider
4. preserve current OpenSky auth/token handling
5. do not emit per-row import logs in parser/import loops

## 11) Phased Implementation Plan

Execution rule:

- Do not start next phase until current phase gates pass.
- Add tests before or with behavior changes in the same phase.

## 11A) Phase Ownership

1. Phase 1 owner: data/repository implementation owner.
2. Phase 2 owner: sync/import + infrastructure owner.
3. Phase 3 owner: domain/use-case + map-viewmodel/ui owner.
4. Phase 4 owner: release-quality verification owner.

Ownership note:

- if a single developer/agent executes all phases, role boundaries still apply for review checklists.

### Phase 1: Data foundation

- add Room entities/dao/database
- add metadata repository + sync repository interfaces/implementations
- add sync state models and documented transitions
- add unit tests for normalization and DAO lookups
- add Room migration test scaffold for metadata DB

Exit criteria:

- repository returns correct values for known ICAO24 set
- tests pass
- `./gradlew enforceRules` passes

### Phase 2: Import and sync pipeline

- implement file selector (latest complete snapshot)
- implement CSV importer with batch upsert
- implement worker + scheduler
- add sync state reporting flow
- add fake `Clock` and `TestDispatcher` tests for scheduling/backoff logic
- add cancellation and cleanup tests for interrupted imports
- implement bootstrap scheduling path for already-enabled overlays on cold start
- implement discovery fallback when bucket listing is unavailable
- implement complete-snapshot download failure fallback to direct dataset URL
- implement deterministic duplicate-ICAO resolution policy in importer
- implement staging swap guard (no empty/partial promotion)
- implement paginated metadata bucket discovery
- implement chunked metadata lookup queries for ICAO lists
- implement explicit unique-work policy and disable-cancel behavior
- implement strict complete-snapshot filename regex + numeric year/month selection
- implement source fallback cascade (bucket fallback then direct dataset URL)
- implement enable/disable race-safe scheduling guarantees

Exit criteria:

- import test with fixture files passes
- repeated sync with same etag is skipped
- failed sync keeps prior data intact
- `./gradlew enforceRules` passes
- no import path performs blocking work on Main
- cold-start with overlay already enabled triggers initial sync scheduling
- source-listing failure still schedules/executes fallback dataset import path
- source-listing failure follows fallback cascade: bucket `metadata/aircraftDatabase.csv` then direct dataset URL
- complete-snapshot download failure still attempts direct dataset fallback before terminal failure
- BOM/alias headers are parsed correctly
- duplicate ICAO rows resolve deterministically
- zero-row or partial import never replaces active metadata table
- paginated listing selects latest snapshot correctly
- disabling overlay cancels pending one-time + periodic sync work
- metadata lookup handles large ICAO lists via deterministic chunking
- strict filename regex + numeric year/month selection picks expected latest complete snapshot
- total source failure preserves previously imported metadata and reports typed terminal failure
- rapid disable/enable race still results in eventual one-time sync scheduling

### Phase 3: ADS-B join and UI integration

- keep overlay target list model unchanged for rendering hot-path stability
- add selected-target enriched details model for metadata
- join metadata via `AdsbMetadataEnrichmentUseCase` selected target flow
- update details sheet rendering and fallback
- migrate details labels from `Type`/`Category` to `Emitter category` (label + raw int)
- verify no direct UI import of metadata data-layer classes
- verify overlay rendering path does not add metadata DB work per frame

Exit criteria:

- selecting target with known ICAO shows type/model/registration
- unknown ICAO shows fallback text
- selected target shows `Emitter category` instead of ambiguous `Type`
- `./gradlew enforceRules` passes
- no map render hot-path regression attributable to metadata lookup

### Phase 4: Hardening and release gates

- replay/unit/integration coverage updates
- verify no architecture violations
- verify no regression in overlay behavior and performance
- update `docs/ARCHITECTURE/PIPELINE.md` to match final wiring
- verify `docs/ARCHITECTURE/KNOWN_DEVIATIONS.md` remains with no new entries
- verify map-position pipeline ownership/cadence unchanged

Exit criteria:

- `./gradlew enforceRules` passes
- `./gradlew testDebugUnitTest` passes
- `./gradlew lintDebug assembleDebug` passes
- `./gradlew detekt ktlintCheck` passes when those tasks exist in this repo; if absent, record "not configured" and rely on `enforceRules` + lint gates
- `./gradlew connectedDebugAndroidTest` passes on device
- deterministic replay gate (existing repo gate/script) passes
- compose preview sources compile in touched modules
- UI state collection remains lifecycle-aware (`collectAsStateWithLifecycle` / `repeatOnLifecycle` as applicable)
- any preview/test lifecycle exception has explicit inline rationale comment

## 12) Test Plan

Mandatory new tests:

1. `AircraftMetadataCsvParserTest`
   - quoted comma fields
   - schema variant headers
   - missing optional columns
2. `AircraftMetadataFileSelectorTest`
   - chooses newest complete snapshot
   - falls back correctly
3. `AircraftMetadataRepositoryTest`
   - normalization and lookup
4. `AircraftMetadataImporterTest`
   - batch upsert, empty rows skipped
5. `EnrichSelectedAdsbTargetUseCaseTest`
   - known ICAO attaches metadata
   - unknown ICAO leaves null fields
   - enrichment only affects selected-target details model, not overlay target list model
6. `AdsbMarkerDetailsSheet` UI test
   - metadata rendered
   - fallback rendered when missing
   - sync status text rendered for missing/running/error paths
7. `AircraftMetadataSyncStateMachineTest`
   - validates allowed transitions only
   - validates typed failure propagation
8. `AircraftMetadataSchedulerTimeTest`
   - uses fake `Clock` and `TestDispatcher`
   - verifies periodic cadence and retry behavior deterministically
9. `AdsbMetadataEnrichmentDeterminismTest`
   - fixed ADS-B + metadata input stream replayed twice
   - identical enriched outputs produced
10. `AircraftMetadataMigrationTest`
   - verifies schema migration without destructive fallback
11. `AircraftMetadataImportCancellationTest`
   - interruption leaves active metadata table consistent
   - temporary files are removed
12. `LayerBoundaryTest` (or architecture rule test)
   - verifies UI module code paths do not depend on metadata data-layer types
13. `AdsbMetadataRenderPathTest`
   - verifies metadata lookup is not executed in per-frame overlay render/update loops
14. `MetadataAvailabilityStabilityTest`
   - validates anti-flicker transitions (`SyncInProgress` -> terminal states) for selected target
   - validates selected-target switch clears prior-target metadata immediately
15. `MapPositionPipelineNonRegressionTest`
   - verifies metadata feature does not alter map-position source ownership/wiring
16. `MetadataSyncBootstrapTest`
   - overlay preference already true at app start + no prior success -> one-time sync is scheduled
17. `MetadataSyncPausedStateTransitionTest`
   - disabling overlay transitions sync state to `PausedByUser`
   - re-enabling overlay transitions `PausedByUser -> Scheduled`
18. `MetadataSourceFallbackTest`
   - bucket listing failure path tries bucket `metadata/aircraftDatabase.csv` first, then direct dataset URL if needed
   - typed sync state indicates fallback source path
   - complete-snapshot download failure path falls back to direct dataset URL
19. `MetadataSourceTotalFailureRetentionTest`
   - when all source attempts fail, prior local metadata remains active
   - typed terminal failure state is emitted without destructive table changes
20. `MetadataCsvHeaderRobustnessTest`
   - UTF-8 BOM first header token is handled
   - alias header variants map to canonical fields
21. `MetadataDuplicateIcaoResolutionTest`
   - duplicate ICAO rows resolve by documented deterministic policy
22. `MetadataSwapGuardTest`
   - zero-row/partial staging import cannot replace active metadata table
   - prior active metadata remains queryable after guard-triggered failure
23. `MetadataBucketPaginationTest`
   - multi-page S3 listing aggregation yields correct latest snapshot selection
   - mid-pagination parse failure triggers fallback path without data loss
24. `MetadataLookupChunkingTest`
   - ICAO lookup list above single-query bind threshold is chunked deterministically
25. `MetadataSyncUniqueWorkPolicyTest`
   - repeated schedule calls do not create duplicate one-time/periodic workers
26. `MetadataSyncWorkerCancellationTest`
   - disabling overlay cancels pending one-time and periodic sync workers
27. `MetadataEnableDisableRaceSchedulingTest`
   - rapid disable/enable sequence still leads to eventual one-time sync scheduling
28. `MetadataSyncCheckpointPersistenceTest`
   - source key/etag/last success/error checkpoints survive process restart
29. `MetadataCompleteSnapshotSelectionTest`
   - strict regex filtering excludes non-complete/non-csv keys
   - numeric year/month comparison selects correct latest complete snapshot

## 13) Non-Goals (for this plan)

1. no change to ADS-B polling cadence
2. no change to airborne filter thresholds
3. no map icon behavior changes
4. no OGN architecture changes
5. no change to ADS-B receive radius (15 km) or displayed target cap (30)

## 14) Acceptance Criteria

Release-ready means all are true:

1. Details sheet shows real aircraft type/model/registration when ICAO metadata exists.
2. Metadata import is resilient to schema drift between complete and daily files.
3. App remains responsive during import (streaming, batched transactions).
4. Existing ADS-B overlay behavior is unchanged when metadata is unavailable.
5. All architecture and coding rule gates pass with no new deviations.
6. Metadata availability/uncertainty is surfaced explicitly via typed UI model state.
7. Metadata import and migration paths are resilient (no destructive fallback, no active-table corruption on cancellation).
8. Layer boundaries are enforced and verifiable (no UI->data shortcuts).
9. Replay determinism remains intact with metadata enrichment.
10. Metadata feature introduces no new Android permissions.
11. Map-position pipeline ownership and cadence remain unchanged.
12. ADS-B runtime baseline remains unchanged (15 km radius, >100 ft, >40 kt, FLARM-filtered, cap=30).
13. Initial metadata sync is triggered by overlay enable preference transition, not by map visibility/streaming state.
14. Cold-start with overlay already enabled still triggers first metadata sync when no prior success exists.
15. Metadata source discovery failure degrades gracefully via fallback cascade (bucket `metadata/aircraftDatabase.csv` then direct dataset URL) without wiping last good local metadata.
16. Complete-snapshot download failures degrade through direct dataset fallback before terminal failure.
17. CSV import is robust to BOM/header alias variance and does not regress silently on schema drift.
18. Duplicate ICAO rows in source produce deterministic metadata results.
19. Active metadata table is never replaced by an empty or partial import.
20. Multi-page metadata source listing is handled correctly and deterministically.
21. Metadata lookup scales beyond current target cap via deterministic chunked queries.
22. Overlay disable reliably cancels pending one-time and periodic metadata sync work.
23. Selected-target switch never shows stale metadata from previously selected ICAO.
24. Total source failure preserves last good local metadata and surfaces typed terminal sync failure.
25. Rapid disable/enable sequences still result in eventual one-time sync scheduling.
