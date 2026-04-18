# IGC Flight Log Repository Release-Grade Phased IP

## 0) Metadata

- Title: Release-grade ownership extraction for `IgcFlightLogRepository`
- Owner: XCPro Team
- Date: 2026-03-14
- Issue/PR: TBD
- Status: Phase 3 complete
- Execution rules:
  - This is an ownership and release-hardening track, not a broad IGC redesign.
  - Keep publish and recovery behavior stable while shrinking owner width.
  - Favor thin-coordinator extraction over generic utility churn.
  - Do not mix recorder business-rule changes, declaration changes, or UI work into this track.
  - Remove the line-budget exception by splitting responsibility, not by raising the cap.
- Current status snapshot:
  - Phase 1 is complete: identity codec, staged metadata parser, and neutral downloads storage-path owner are extracted.
  - Phase 2 is complete: staging file IO now lives in `IgcRecoveryStagingStore`, and MediaStore/legacy publish branches now live in `IgcFlightLogPublishTransport`.
  - Phase 3 is complete: persisted finalized-entry lookup now lives in `IgcRecoveryDownloadsLookup`, and duplicate-match / pending-row cleanup now live in `IgcRecoveryFinalizedEntryResolver`.
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt` is now `260` lines and remains the recovery/finalize coordinator only.
  - The shared `IgcDownloadsRepository` port stayed unchanged, and the metadata-gated no-metadata recovery outcome is preserved.

## 1) Scope

- Problem statement:
  - The IGC finalize/recovery path started as a `552` line mixed-responsibility hotspot.
  - After Phases 1 and 2,
    `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt`
    is down to `375` lines, but the same coordinator file still owns:
    - repository contract and no-op wiring
    - finalize transaction orchestration
    - recovery orchestration
    - pending-row cleanup
    - finalized-entry duplicate matching
    - recovery failure mapping
  - The repository also owns a process-local published-entry cache used for
    idempotent finalize behavior; that cache must stay explicit in the
    coordinator and remain directly tested during the split.
  - Pending-row cleanup is also spread across all terminal recovery branches,
    not just duplicate-match branches, so the recovery extraction must preserve
    those branch semantics instead of hiding them behind one opaque resolve call.
  - Recovery lookup still reaches through `downloadsRepository.entries.value`
    during finalized-entry matching, so the coordinator still depends on a
    reactive state surface instead of a narrow recovery lookup boundary.
  - The shared `IgcDownloadsRepository` port is used by unrelated file-list and
    use-case tests, so widening that port for recovery-only lookup would create
    avoidable churn outside this seam.
  - That makes the final publish path harder to review and raises change risk on a release-critical path.
- Why now:
  - The higher-ROI ownership seams in map shell, profile/card, and forecast/weather runtime are already closed.
  - Phases 1 and 2 removed the line-budget exception, so the remaining work is
    now a narrow correctness seam rather than a broad hotspot rewrite.
  - The path already has strong unit and instrumentation coverage, so it is refactorable without blind risk.
- In scope:
  - Split `MediaStoreIgcFlightLogRepository` into a thin coordinator plus focused leaf owners.
  - Remove duplicated file-identity normalization from the repository path and make one canonical owner explicit.
  - Keep the existing repository interface and publish/recovery semantics stable.
  - Update pipeline/docs for the final owner layout.
- Out of scope:
  - IGC recorder business rules, B-record cadence, declaration semantics, or lint rules.
  - UI, ViewModel, or navigation work.
  - Storage-location changes or MediaStore product behavior changes.
  - New modules or public cross-feature APIs.
- User-visible impact:
  - None intended.
  - Publish file names, recovery behavior, and failure semantics must remain behaviorally equivalent.
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| final publish/recovery transaction result | `MediaStoreIgcFlightLogRepository` coordinator | `IgcFinalizeResult` / `IgcRecoveryResult` | leaf helpers returning alternate authoritative session state |
| staged file bytes and artifact lifecycle | `IgcRecoveryStagingStore` | narrow read/write/delete methods | direct file writes scattered in repository and tests |
| staged header-derived recovery metadata | `IgcStagedRecoveryMetadataParser` | pure parse result | duplicate header/date parsing in repository helpers |
| session file identity normalization and prefix composition | `IgcSessionFileIdentityCodec` | pure normalization/prefix methods | duplicate manufacturer/serial normalization in repository and naming policy |
| IGC downloads storage path constants | `IgcDownloadsStoragePaths` | internal constants reused by downloads query and publish transport | repository/transport reaching into `MediaStoreIgcDownloadsRepository` for path literals |
| final publish side effects (MediaStore / legacy file write) | `IgcFlightLogPublishTransport` | narrow publish method returning `IgcLogEntry` | repository owning platform write details inline |
| finalized-entry matching and pending-row cleanup | `IgcRecoveryFinalizedEntryResolver` | explicit `findExistingFinalizedMatch(...)` and `cleanupPendingRows(...)` methods | repository-owned duplicate matching and pending-row deletion logic |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| published entry cache by session ID | `MediaStoreIgcFlightLogRepository` | `finalizeSession`, `recoverSession` success paths | repository interface consumers | publish and recovery results | none | process restart or repository reconstruction | wall metadata only | publish parity, recovery parity |
| staged session file | `IgcRecoveryStagingStore` | `write`, `delete`, recovery-read path | repository coordinator only | finalized payload bytes | app-private files dir | finalize failure cleanup, recovery cleanup | wall file timestamp is non-authoritative | staging write/recover/delete tests |
| staged recovery metadata parse result | `IgcStagedRecoveryMetadataParser` | parse only | repository coordinator only | staged payload header and first valid B record | none | recomputed on demand | wall UTC from staged header/B line | parser tests |
| finalized-entry recovery match in persisted Downloads state | `IgcRecoveryFinalizedEntryResolver` | `findExistingFinalizedMatch(...)` only | repository coordinator only after refresh and only when merged metadata has UTC identity | recovery downloads lookup + metadata identity | MediaStore Downloads metadata | recomputed on demand | wall UTC date identity | duplicate guard, cache-bypass, metadata-gated lookup, and resolver tests |
| pending-row cleanup for recovery session identity | `IgcRecoveryFinalizedEntryResolver` | `cleanupPendingRows(...)` only | repository coordinator terminal recovery branches | recovery metadata identity | MediaStore Downloads metadata | terminal recovery branch completion | wall UTC date identity | cleanup parity tests + androidTest restart |
| publish transport output entry | `IgcFlightLogPublishTransport` | publish only | repository coordinator only | validated signed payload + naming result | MediaStore or legacy Downloads file system | failed writes delete pending row / no entry | wall metadata only | MediaStore/legacy publish tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`feature:map use case -> IgcFlightLogRepository port -> data coordinator -> data leaf helpers -> platform/file IO`

- Modules/files touched:
  - `feature:igc`
  - `feature:map` tests only if constructor wiring or NoOp helpers need updates
  - `docs/refactor`
  - `docs/ARCHITECTURE/PIPELINE.md`
- Boundary risk:
  - accidental duplication of filename identity policy between the repository and `IgcFileNamingPolicy`
  - leaving the publish path coupled to `MediaStoreIgcDownloadsRepository.DOWNLOAD_RELATIVE_PATH`
  - recovery matching still depending on `downloadsRepository.entries.value`
    instead of a narrow query or snapshot boundary
  - widening the shared `IgcDownloadsRepository` port just to support recovery
    lookup and forcing unrelated use-case/file-list fakes to change
  - making the coordinator thinner without obscuring publish/recovery ordering
  - accidentally moving or weakening the process-local idempotency cache (`publishedBySessionId`) while splitting helpers
  - preserving real MediaStore pending-row cleanup semantics while moving code

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcRecordingRuntimeActionSink.kt` | thin coordinator over focused collaborators in the same IGC slice | coordinator owns transaction order while collaborators own signing/metadata/publish details | `IgcFlightLogRepository` is repository/data-owner facing, not an action sink |
| `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeForecastWeatherDelegate.kt` | recent successful split from mixed owner into thin coordinator plus leaf owners | shrink mixed owner by extracting focused leaf owners first, keep coordinator ABI stable | IGC leaves are storage/recovery owners rather than runtime delegates |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| staged file write/read/delete | mixed repository file | `IgcRecoveryStagingStore` | isolate file IO and artifact lifecycle | staging store tests + repository regression tests |
| staged metadata parsing | mixed repository file | `IgcStagedRecoveryMetadataParser` | pure parsing should not live beside platform writes | parser unit tests + recovery tests |
| MediaStore/legacy publish details | mixed repository file | `IgcFlightLogPublishTransport` | isolate platform publish side effects and pending-row finalization | publish tests + androidTest restart recovery |
| finalized-entry matching and pending-row cleanup | mixed repository file | `IgcRecoveryFinalizedEntryResolver` | isolate persisted recovery identity lookup and pending-row cleanup while keeping branch timing explicit in the coordinator | recovery tests + kill-point tests |
| recovery-only Downloads lookup over current finalized entries | coordinator reach-through to `downloadsRepository.entries.value` | `IgcRecoveryDownloadsLookup` | remove the coordinator reach-through without widening the shared downloads port | resolver tests + repository parity tests |
| manufacturer/serial normalization and recovery prefix composition | mixed repository file and `IgcFileNamingPolicy` | `IgcSessionFileIdentityCodec` | one canonical owner for file identity policy | naming policy tests + recovery resolver tests |
| Downloads relative path / legacy subdir constants | concrete downloads implementation | `IgcDownloadsStoragePaths` | publish and query paths should share one neutral data-owner constant source | publish, recovery, and downloads repository tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `MediaStoreIgcFlightLogRepository` direct file writes | repository owns staging file IO inline | `IgcRecoveryStagingStore` | Phase 2 |
| `MediaStoreIgcFlightLogRepository` direct MediaStore/legacy publish branches | repository owns transport details inline | `IgcFlightLogPublishTransport` | Phase 2 |
| `MediaStoreIgcFlightLogRepository` duplicate finalized match and pending-row delete helpers | repository owns recovery resolver behavior inline | `IgcRecoveryFinalizedEntryResolver` with explicit `findExistingFinalizedMatch(...)` / `cleanupPendingRows(...)` methods | Phase 3 |
| `MediaStoreIgcFlightLogRepository` normalization helpers | repository duplicates identity policy | `IgcSessionFileIdentityCodec` reused by resolver and naming policy | Phase 1 |
| `MediaStoreIgcFlightLogRepository` direct use of `MediaStoreIgcDownloadsRepository.DOWNLOAD_RELATIVE_PATH` | concrete implementation constant reach-through | `IgcDownloadsStoragePaths` | Phase 1 |
| `MediaStoreIgcFlightLogRepository` direct use of `downloadsRepository.entries.value` for duplicate matching | reactive state reach-through from coordinator | `IgcRecoveryDownloadsLookup` behind the resolver, without widening `IgcDownloadsRepository` | Phase 3 |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Igc_Flight_Log_Repository_Release_Grade_Phased_IP_2026-03-14.md` | New | execution plan and acceptance contract | required non-trivial refactor plan | production code must not carry execution planning | no |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt` | Existing | repository port + current thin finalize/recovery coordinator | kept the stable entrypoint and avoided churn while Phase 1/2 owners moved out | the file is now below the hotspot budget, so a separate coordinator file is optional rather than automatic | maybe, Phase 4 only if still justified |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/MediaStoreIgcFlightLogRepository.kt` | Deferred | optional dedicated coordinator file | only create if Phase 4 evidence shows port/coordinator colocation is still a review or ownership burden | forcing a new file before it is justified would be churn | deferred |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcRecoveryStagingStore.kt` | New | staging artifact IO owner | file IO belongs in one focused data helper | parser or coordinator should not own direct file operations | no |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcStagedRecoveryMetadataParser.kt` | New | pure staged header parser | parsing logic should be testable without Android/file writes | transport/store should not own parse policy | no |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcDownloadsStoragePaths.kt` | New | canonical downloads relative path and legacy subdir constants | publish and downloads-query code must not depend on another concrete implementation for path literals | reusing `MediaStoreIgcDownloadsRepository` constants keeps the concrete-owner leak alive | no |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogPublishTransport.kt` | New | MediaStore/legacy publish side effects | isolates platform write path and pending finalization details | repository coordinator should not own branchy platform code | no |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcRecoveryFinalizedEntryResolver.kt` | New | duplicate finalized match lookup and pending-row cleanup | isolates recovery resolution semantics | downloads repository should remain file-list owner, not session-recovery owner | no |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcRecoveryDownloadsLookup.kt` | New | recovery-only finalized-entry snapshot/query boundary | avoids broadening the shared downloads repository port just for recovery | unrelated file-list and use-case consumers should not absorb recovery-only API churn | no |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcSessionFileIdentityCodec.kt` | New | canonical normalization/prefix identity policy | keeps file identity normalization with naming policy semantics | repository helpers must not duplicate this policy | no |
| `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcFileNamingPolicy.kt` | Existing | naming policy owner reusing canonical identity codec | keeps naming as the durable file-name authority | resolver should reuse policy pieces, not copy them | no |
| `feature/igc/src/main/java/com/trust3/xcpro/di/IgcCoreBindingsModule.kt` | Existing | DI wiring for new leaf helpers if constructor injection requires it | keep construction explicit and stable | ad hoc constructor wiring in tests/owners is not acceptable in production | no |
| `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryTest.kt` | Existing | finalize-path behavior lock | existing parity coverage | replacing it would lose baseline behavior proof | no |
| `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryIdempotencyTest.kt` | Existing | idempotent finalize cache behavior lock | keeps `publishedBySessionId` semantics explicit during the split | broader recovery tests do not directly prove finalize idempotency | no |
| `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryRecoveryTest.kt` | Existing | recovery-path parity lock | existing recovery coverage | broader integration tests are too slow for seam locking | no |
| `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcFlightLogRepositoryRecoveryKillPointTest.kt` | Existing | crash-window recovery parity lock | existing release-critical kill-point coverage | coordinator split must stay covered at kill points | no |
| `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcStagedRecoveryMetadataParserTest.kt` | New | pure parser coverage | locks extracted parser logic directly | repository tests alone would hide parser drift | no |
| `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcRecoveryFinalizedEntryResolverTest.kt` | New | duplicate-match and pending-row cleanup coverage | locks extracted recovery resolver semantics directly | repository tests alone obscure resolver ownership | no |
| `feature/igc/src/test/java/com/trust3/xcpro/igc/data/IgcRecoveryDownloadsLookupTest.kt` | New | recovery-only Downloads lookup coverage | locks the new lookup boundary without touching unrelated file-list tests | repository tests alone would hide lookup-boundary churn | no |
| `feature/igc/src/androidTest/java/com/trust3/xcpro/igc/IgcRecoveryRestartInstrumentedTest.kt` | Existing | real MediaStore restart recovery proof | release-grade proof for pending-row cleanup and single-file publish | unit tests cannot fully prove platform behavior | no |
| `docs/ARCHITECTURE/PIPELINE.md` | Existing | end-to-end ownership map | pipeline wiring changes must be documented in the same change | plan doc is not the runtime wiring source of truth | no |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `IgcFlightLogRepository` | `feature:igc` | `feature:map` use cases and tests | existing public contract | stable repository port already exists | unchanged |
| `MediaStoreIgcFlightLogRepository` constructor dependencies | `feature:igc` | DI and same-module tests | existing constructor visibility | required to compose the thinner coordinator without hidden fallbacks | keep out of cross-feature consumption by convention and review |
| `IgcRecoveryStagingStore` | `feature:igc` | repository coordinator tests and DI | existing class visibility | focused file-IO owner | keep out of cross-feature consumption by convention and review |
| `IgcDownloadsStoragePaths` | `feature:igc` data package | downloads repository, publish transport, androidTest helpers | `internal` | one neutral owner for storage-location constants | no public exposure planned |
| `IgcFlightLogPublishTransport` | `feature:igc` | repository coordinator tests and DI | existing class visibility | focused publish side-effect owner | keep out of cross-feature consumption by convention and review |
| `IgcRecoveryFinalizedEntryResolver` | `feature:igc` | repository coordinator tests and DI | `internal` | focused recovery resolution owner | no public exposure planned |
| `IgcRecoveryDownloadsLookup` | `feature:igc` | recovery resolver only | `internal` | recovery-only Downloads snapshot/query boundary without widening the shared downloads port | must stay minimal and avoid reusing UI file-list scan/build-duration paths |
| `IgcSessionFileIdentityCodec` | `feature:igc` domain package | naming policy + recovery resolver | `internal` | canonical identity normalization owner | no public exposure planned |

### 2.2F Scope Ownership and Lifetime

- No new long-lived scope is planned in this track.
- Existing behavior remains synchronous/transactional inside the repository lock.

### 2.2G Compatibility Shim Inventory

- None planned.
- If a temporary adapter is needed during the split, it must stay internal to `feature:igc`, carry a removal trigger, and land with direct regression coverage.

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| session file identity normalization (`manufacturerId`, `sessionSerial`, date prefix) | `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcSessionFileIdentityCodec.kt` | `IgcFileNamingPolicy`, recovery resolver | both naming and recovery match logic depend on the same normalized identity | no |
| final IGC file name generation | `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcFileNamingPolicy.kt` | repository coordinator | naming remains a domain policy, not a repository helper concern | no |
| IGC downloads storage location constants | `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcDownloadsStoragePaths.kt` | downloads repository, publish transport, MediaStore androidTest helpers | publish and query paths must share the same storage contract without concrete implementation reach-through | no |

### 2.2I Stateless Object / Singleton Boundary

| Object / Holder | Why `object` / Singleton Is Needed | Mutable State? | Why It Is Non-Authoritative | Why Not DI-Scoped Instance? | Guardrail / Test |
|---|---|---|---|---|---|
| `NoopIgcFlightLogRepository` | existing explicit disabled/test boundary | no | it never becomes a publish authority; it only reports disabled behavior | retaining a no-op object keeps tests/convenience wiring simple | repository tests and use-case tests already lock no-op behavior |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| file naming UTC date | Wall UTC | final file identity comes from first valid fix or session start wall UTC |
| staged metadata date parsing | Wall UTC | parsed from staged `HFDTEDATE` and first valid `B` record time |
| MediaStore `DATE_MODIFIED` | Wall metadata | UI/display metadata only; not used for replay or domain decisions |
| recovery duplicate-match date prefix | Wall UTC | matches finalized file identity in Downloads |

Explicitly forbidden:
- comparing staged/header UTC values with monotonic clocks
- using MediaStore modified time as authoritative recovery identity

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - unchanged; repository stays synchronous and transaction-ordered
- Primary cadence/gating sensor:
  - not applicable; finalize/recovery is event-driven
- Hot-path latency budget:
  - no new extra full-file reads or redundant MediaStore scans on the success path

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - none introduced
  - publish naming must remain deterministic for the same request plus the same existing-name set

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| empty payload | Recoverable failure | repository coordinator | finalize fails with explicit error | no publish attempt | repository unit tests |
| naming space exhausted | Recoverable failure | naming policy via coordinator | finalize fails with explicit error | no publish attempt | naming and repository tests |
| staging write failure | Recoverable failure | staging store via coordinator | finalize fails with write error | no publish attempt | repository tests |
| MediaStore insert/write/finalize failure | Recoverable failure | publish transport via coordinator | finalize fails with write error | pending row cleanup if needed | publish tests + androidTest |
| staged metadata corrupt/missing during recovery | Degraded / terminal for session recovery | parser/coordinator | recovery reports explicit failure | cleanup artifacts and stop | recovery tests |
| duplicate finalized match | Terminal guard | recovery resolver via coordinator | recovery reports `DUPLICATE_SESSION_GUARD` | cleanup pending rows and artifacts | recovery tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| published `IgcLogEntry` | publish transport | MediaStore/file result + naming result | yes | the transport owns the final document URI and metadata |
| recovery metadata parse result | staged metadata parser | staged headers and B record wall UTC | yes | parse boundary owns interpretation of staged payload |
| session file identity prefix | canonical identity codec | request/recovery metadata wall UTC + normalized manufacturer/serial | yes | one canonical policy is required for both naming and recovery matching |

### 2.5C No-Op / Test Wiring Contract

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| `IgcFlightLogRepository` | `NoopIgcFlightLogRepository` | existing explicit optional/test path only | returns disabled/failure semantics without pretending publish succeeded | keep no-op behavior unchanged and covered by repository/use-case tests |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| mixed publish/recovery/file-IO/platform logic remains in one hotspot | file ownership and line-budget rules | `enforceRules` + review | `IgcFlightLogRepository.kt` and any optional future coordinator split file |
| file identity normalization remains duplicated | canonical policy owner rule | direct unit tests + review | `IgcSessionFileIdentityCodecTest.kt` or naming/recovery tests |
| concrete implementation constant leak remains in publish/query paths | dependency-direction and owner-boundary rules | direct unit tests + review | `IgcDownloadsStoragePaths.kt`, publish/downloads tests |
| recovery duplicate matching still depends on the downloads reactive state surface | owner-boundary rule | review + recovery resolver tests | `IgcRecoveryFinalizedEntryResolver.kt`, `IgcRecoveryDownloadsLookup.kt` |
| recovery lookup accidentally reuses UI file-list scan or duration parsing path | owner-boundary and hot-path cost rule | review + direct lookup tests | `IgcRecoveryDownloadsLookup.kt`, `IgcDownloadsRepository.kt` |
| recovery duplicate guard or pending-row cleanup regresses | explicit degraded-state contract | unit tests + androidTest | recovery tests and `IgcRecoveryRestartInstrumentedTest.kt` |
| MediaStore publish cleanup regresses after split | release-grade persistence contract | unit tests + androidTest | publish tests + restart instrumentation |
| finalize idempotency cache regresses | explicit owner-state contract | direct unit test | `IgcFlightLogRepositoryIdempotencyTest.kt` |
| shared downloads file-list port widens for recovery-only behavior | owner-boundary and anti-churn rule | review + compile/test impact check | `IgcDownloadsRepository.kt`, file-list/use-case tests |
| no-op path becomes silent production fallback | no-op boundary rule | review + existing tests | use-case tests and DI review |

### 2.7 Visual UX SLO Contract

- Not applicable.
- This track does not change map/overlay/replay interaction behavior.

## 3) Data Flow (Before -> After)

Before:

```text
IgcRecordingRuntimeActionSink
  -> IgcFlightLogRepository
     -> naming / signing / validation
     -> staging file IO
     -> MediaStore or legacy publish
     -> recovery parse
     -> finalized-entry matching
     -> pending-row cleanup
```

After:

```text
IgcRecordingRuntimeActionSink
  -> MediaStoreIgcFlightLogRepository (thin coordinator)
     -> IgcFileNamingPolicy
     -> IgcRecoveryStagingStore
     -> IgcDownloadsStoragePaths
     -> IgcFlightLogPublishTransport
     -> IgcStagedRecoveryMetadataParser
     -> IgcRecoveryFinalizedEntryResolver
     -> IgcRecoveryDownloadsLookup
     -> IgcDownloadsRepository / IgcRecoveryMetadataStore
```

## 4) Implementation Phases

### Phase 0 - Seam lock and baseline

- Goal:
  - lock the split shape before moving code
  - confirm current coverage is sufficient for finalize, recovery, kill-point, and real MediaStore restart paths
- Files to change:
  - this plan doc only
- Ownership/file split changes in this phase:
  - none
- Tests to add/update:
  - none unless a concrete gap is discovered during implementation
- Exit criteria:
  - file ownership plan is explicit
  - behavior-critical tests are named and accepted as the baseline gate

### Phase 1 - Pure identity and parser extraction

- Goal:
  - extract pure logic first so naming/recovery identity and staged metadata parsing stop living in the repository implementation
  - remove the concrete `MediaStoreIgcDownloadsRepository.DOWNLOAD_RELATIVE_PATH`
    reach-through before the transport split starts
- Files to change:
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcSessionFileIdentityCodec.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/domain/IgcFileNamingPolicy.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcStagedRecoveryMetadataParser.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcDownloadsStoragePaths.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcDownloadsRepository.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt`
  - focused parser/naming tests
- Ownership/file split changes in this phase:
  - normalization and staged parsing move out of the repository
  - storage location constants move to one neutral data-owner file
- Tests to add/update:
  - parser unit tests
  - naming policy tests if codec reuse changes internals
  - existing finalize/recovery tests updated only where path constants are referenced
  - existing recovery tests updated only if constructor wiring changes
- Exit criteria:
  - repository no longer owns parse helpers or duplicate normalization policy
  - publish and query paths no longer reach into `MediaStoreIgcDownloadsRepository` for storage constants
  - pure logic is Android-free and directly unit tested

### Phase 2 - Staging and publish transport extraction

- Goal:
  - remove direct file IO and publish-transport branching from the repository
- Files to change:
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcRecoveryStagingStore.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogPublishTransport.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt`
  - publish-path tests
- Ownership/file split changes in this phase:
  - staging artifact lifecycle moves to the store
  - MediaStore/legacy write details move to the transport
  - repository stays in the existing file and becomes the finalize/recovery coordinator over the new owners
- Tests to add/update:
  - staging store unit tests
  - publish transport unit tests
  - existing finalize tests
  - existing idempotency test
  - androidTest remains green
- Exit criteria:
  - repository no longer owns direct file writes or MediaStore branch internals
  - finalize success/failure parity is preserved
  - finalize idempotency parity is preserved
  - no new file split is introduced unless the coordinator still needs it after the leaf extractions

### Phase 3 - Recovery resolver extraction

- Goal:
  - remove finalized-entry matching and pending-row cleanup from the repository coordinator
- Files to change:
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcRecoveryFinalizedEntryResolver.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcRecoveryDownloadsLookup.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt`
  - recovery tests and kill-point tests
- Ownership/file split changes in this phase:
  - recovery duplicate guard and pending-row cleanup move to a focused resolver with separate `findExistingFinalizedMatch(...)` and `cleanupPendingRows(...)` methods
  - finalized-entry lookup stops reading `downloadsRepository.entries.value`
    directly from the coordinator and moves behind a recovery-only Downloads
    lookup boundary
  - persisted finalized-entry lookup remains metadata-gated: the coordinator
    only calls the recovery lookup when merged metadata exists and has UTC
    identity; missing metadata still falls through to the current
    staging-based recovery path
  - `IgcRecoveryDownloadsLookup` must use a minimal recovery query/snapshot and
    must not reuse the UI/file-list scan path in `IgcDownloadsRepository`
    (`refreshEntries()`, `queryEntries()`, `buildEntry()`, or duration parsing)
  - `downloadsRepository.refreshEntries()` stays in the coordinator so existing
    refresh semantics and test expectations remain stable
  - `publishedBySessionId` cache short-circuit stays in the coordinator and is
    checked before resolver-owned persisted lookup
  - repository stays the transaction coordinator for recovery flow
- Tests to add/update:
  - new resolver tests
  - new recovery Downloads lookup tests if that helper owns query/filter logic
  - existing recovery tests
  - existing kill-point recovery tests
  - existing real MediaStore restart test
  - explicit cache-hit recovery bypass test
  - explicit metadata-gated lookup test proving that no merged metadata means no
    persisted finalized-entry search and the path still reports the current
    staging-driven result
  - explicit pending-row cleanup parity tests for `STAGING_MISSING` and `STAGING_CORRUPT` branches
  - update recovery tests to lock recovery result, cleanup behavior, and cache
    bypass semantics rather than treating raw `refreshCalls` counts as the
    long-term Phase 3 seam contract
- Exit criteria:
  - repository no longer owns duplicate-match search or pending-row cleanup details
  - shared `IgcDownloadsRepository` file-list port is not widened for recovery-only behavior
  - recovery lookup does not reuse the UI/file-list scan path or duration parsing
  - persisted finalized-entry lookup remains metadata-gated and does not change
    the current no-metadata recovery outcome
  - cache-hit and refresh-order behavior remain equivalent
  - recovery parity and cleanup parity are preserved

### Phase 4 - Coordinator slimming and release-grade hardening

- Goal:
  - finish the split only if still justified and prove the path is release-grade
- Files to change:
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/IgcFlightLogRepository.kt`
  - `feature/igc/src/main/java/com/trust3/xcpro/igc/data/MediaStoreIgcFlightLogRepository.kt` only if the final coordinator split is still warranted
  - `docs/ARCHITECTURE/PIPELINE.md`
  - targeted tests/docs only as required
- Ownership/file split changes in this phase:
  - optional: port file holds only the contract and explicit no-op boundary
  - coordinator file, whether split out or not, owns orchestration only and stays narrow enough for review
- Tests to add/update:
  - no new test theme; rerun the full finalize/recovery suite
- Exit criteria:
  - the remaining coordinator ownership is narrow and reviewable
  - pipeline docs reflect the final owner layout
  - required release-grade verification passes

## 5) Test Plan

- Unit tests:
  - finalize success/failure parity
  - finalize idempotency parity
  - staged parser coverage
  - recovery resolver duplicate/pending cleanup coverage
  - recovery cache-bypass coverage
  - recovery lookup minimal-query coverage
  - metadata-gated persisted lookup coverage
  - naming identity normalization coverage
  - storage path constant reuse coverage where helpful
- Replay/regression tests:
  - deterministic finalize naming for same request and same existing-name set
  - kill-point recovery tests
- UI/instrumentation tests (if needed):
  - `feature/igc` androidTest restart recovery against real MediaStore
- Degraded/failure-mode tests:
  - empty payload
  - staging write failure
  - pending-row cleanup on publish failure
  - staged metadata corrupt/missing
  - duplicate finalized session guard
- Boundary tests for removed bypasses:
  - direct parser tests
  - direct transport tests
  - direct resolver tests
  - direct recovery Downloads lookup tests
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / policy | unit tests | naming identity and recovery parse tests |
| Time-base / replay / cadence | deterministic repeat-run tests | naming + recovery tests with fixed wall UTC inputs |
| Persistence / restore / recovery | round-trip and restart tests | recovery tests, kill-point tests, androidTest restart |
| Ownership move / bypass removal / API boundary | boundary lock tests | direct parser/transport/resolver tests plus repository parity tests |
| UI interaction / lifecycle | not applicable | none |
| Performance-sensitive path | avoid extra IO/scans on finalize path | review plus existing fast unit suite timing |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Release-grade checks when device/emulator is available:

```bash
./gradlew :feature:igc:connectedDebugAndroidTest --no-parallel
./gradlew connectedDebugAndroidTest --no-parallel
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| split hides the same logic behind too many tiny wrappers | medium | keep one thin coordinator and only extract real owners: parser, staging store, transport, recovery resolver | XCPro Team |
| naming identity drift appears between naming policy and recovery resolver | high | introduce one canonical identity codec and lock it with direct tests | XCPro Team |
| storage path constants drift between downloads query and publish transport | medium | move them to one internal owner file and update androidTest helpers to reuse it | XCPro Team |
| recovery resolver split accidentally preserves a direct reactive-state reach-through | medium | Phase 3 must move lookup behind the resolver and add a narrow downloads snapshot/query boundary if direct filtering is still needed | XCPro Team |
| MediaStore pending-row cleanup regresses on real devices | high | preserve and rerun restart androidTest before closure | XCPro Team |
| constructor churn leaks into use cases/tests unnecessarily | medium | keep the public repository port unchanged and adjust DI only at the repository boundary | XCPro Team |
| idempotent finalize behavior regresses while splitting helpers | medium | keep `publishedBySessionId` in the coordinator and rerun `IgcFlightLogRepositoryIdempotencyTest` every phase | XCPro Team |
| release-grade proof stops at unit tests | high | require androidTest restart proof before marking the slice complete | XCPro Team |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file:
- Decision summary:
  - this is a focused ownership split inside an existing module and existing repository port
- Why this belongs in an ADR instead of plan notes:
  - no new module boundary or cross-feature public API is planned

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- File identity normalization has one canonical owner
- `IgcFlightLogRepository` path is no longer a line-budget exception
- Publish and recovery behavior remain deterministic for identical inputs
- Error/degraded-state behavior remains explicit and regression-tested
- `PIPELINE.md` is updated if coordinator/leaf ownership wiring changes
- `KNOWN_DEVIATIONS.md` is updated only if a new approved exception is required

## 8) Rollback Plan

- What can be reverted independently:
  - parser/identity extraction
  - staging store extraction
  - publish transport extraction
  - recovery resolver extraction
- Recovery steps if regression is detected:
  - revert the last landed phase only
  - keep any new direct regression tests that still reproduce the fault
  - do not collapse back to one large file without first restoring passing finalize/recovery coverage
