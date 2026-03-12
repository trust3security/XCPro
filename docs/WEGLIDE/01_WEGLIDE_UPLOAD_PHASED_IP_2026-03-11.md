# WeGlide Advanced Integration Phased Implementation Plan 2026-03-11

## 0) Metadata

- Title: WeGlide OAuth, aircraft mapping, and upload queue integration
- Owner: XCPro Team
- Date: 2026-03-11
- Issue/PR: TBD
- Status: Draft
- Source spec:
  - `docs/WEGLIDE/XCPRO_WEGLIDE_ADVANCED_SPEC.md`
  - `docs/WEGLIDE/XCPRO_WEGLIDE_API-UPLOAD_SPEC.md`

## 1) Scope

- Problem statement:
  XCPro currently has a WeGlide settings sheet shell and temporary local settings state, but it does not implement the production WeGlide architecture defined in the advanced spec:
  - no OAuth account connection
  - no aircraft cache
  - no per-profile aircraft mapping
  - no resilient upload queue
  - no post-finalization upload workflow
- Why now:
  The new WeGlide direction is no longer "single global numeric aircraft id + simple upload". The approved direction is:
  - OAuth 2 authorization code flow with PKCE
  - OAuth-first upload
  - profile-linked WeGlide aircraft mapping
  - Room-backed queue and WorkManager execution
- In scope:
  - Replace temporary WeGlide settings assumptions with the advanced-spec model.
  - Add WeGlide account link state and token storage.
  - Add aircraft discovery/cache and per-profile aircraft mapping.
  - Add upload queue, duplicate protection, and worker execution.
  - Add manual upload entrypoint from IGC files UI.
  - Add finalized-flight prompt/queue flow after successful live IGC finalization.
  - Add diagnostics/status surfaces in WeGlide UI.
- Out of scope:
  - WeGlide server-side API contract negotiation.
  - Logger approval/contest acceptance work with WeGlide.
  - ADS-B refactors or ADS-B test failures.
  - Non-WeGlide profile redesign.
- User-visible impact:
  - User can connect/disconnect a WeGlide account.
  - User can refresh a WeGlide aircraft list and map each XCPro profile to one WeGlide aircraft.
  - User can queue or upload finalized flights from XCPro.
  - Post-flight flow can prompt/queue WeGlide upload without forcing manual file export.

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| WeGlide account link (`userId`, `displayName`, `email`, `authMode`, `connectedAt`) | New WeGlide account repository/store | `Flow<WeGlideAccountLink?>` | Copies in ViewModels, workers, or map managers |
| WeGlide token bundle (`accessToken`, `refreshToken`, `expiresAtEpochMs`) | New encrypted WeGlide token store | token store API only | Any plaintext UI/ViewModel copy |
| WeGlide aircraft cache | New Room DAO/repository | `Flow<List<WeGlideAircraftEntity>>` | Ad hoc in-memory maps as source of truth |
| WeGlide aircraft mapping by XCPro profile id | New mapping DAO/repository | `Flow<List<WeGlideAircraftMappingEntity>>` and per-profile lookup | Global single `aircraftId` setting |
| WeGlide upload queue state | New upload queue DAO/repository | `Flow<List<WeGlideUploadQueueEntity>>` and per-flight lookup | Fire-and-forget worker memory state |
| Active XCPro profile identity | Existing `ProfileRepository.activeProfile` | `StateFlow<UserProfile?>` | Duplicated "current profile" state in WeGlide module |
| Active local aircraft/polar selection for a profile | Existing `GliderRepository` scoped by `profileId` | `GliderConfigRepository` flows | Separate WeGlide-owned local aircraft identity |

Current mismatch to remove:
- The current temporary WeGlide settings implementation stores `pilotId` and `token`.
- That model is not the approved production contract and must be removed from the implementation path.

### 2.2 Dependency Direction

Required flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - new WeGlide implementation area under a dedicated boundary
  - `feature/profile` integration for stable `profileId` ownership
  - `feature/igc` for manual upload entrypoint
  - `feature/map` for finalized-flight prompt/queue trigger
- Boundary risk:
  - `VarioServiceManager` must never own WeGlide networking or queue state.
  - `IgcFilesViewModel` must never upload directly.
  - WeGlide UI must never infer aircraft mapping from display strings.
  - WeGlide must consume existing profile SSOT, not duplicate it.

### 2.2A Boundary Moves (Mandatory)

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Temporary WeGlide credentials/settings model | Current placeholder settings classes | WeGlide account store + token store + mapping repository | Align implementation with approved architecture | WeGlide repository and ViewModel tests |
| Single-aircraft upload assumption | Old draft plan | Per-profile WeGlide aircraft mapping | Multi-aircraft support and SSOT correctness | Mapping use case tests |
| Upload execution responsibility | No owner yet | WeGlide repository + WorkManager worker | Keep network and retries in data/worker boundary | Worker and repository tests |
| Finalized-flight upload decision | No owner yet | Pure WeGlide finalize-trigger use case | Keep Vario/IGC pipeline deterministic and thin | Finalize-trigger tests |

### 2.2B Bypass Removal Plan (Mandatory)

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `feature/traffic/.../WeGlidePreferencesRepository.kt` | Temporary direct settings shape (`pilotId`, `token`) | Replace with account/connectivity and upload-preference state only | Phase 1 |
| `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt` | Temptation to upload directly in finalize callback | Queue/prompt use case only | Phase 4 |
| `feature/igc/src/main/java/com/example/xcpro/igc/ui/IgcFilesViewModel.kt` | Temptation to perform provider call directly from ViewModel | Manual upload use case -> repository enqueue/execute | Phase 4 |
| WeGlide UI | Ad hoc validation in Compose | Pure use cases for connect, mapping resolution, and queue eligibility | Phase 3 and Phase 4 |

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| IGC finalization timeout and landing gating | Monotonic | Already defined by IGC session state machine; must stay deterministic |
| Token expiry | Wall | OAuth expiry is wall-clock based |
| Account linked time | Wall | Persistence and diagnostics only |
| Aircraft sync freshness | Wall | User-visible stale/sync timing |
| Queue `queuedAtEpochMs`, `updatedAtEpochMs` | Wall | Persistence and worker scheduling |
| Duplicate detection hash | N/A | Content-based, time independent |
| Replay upload behavior | Replay-disabled | Replay must not create live network side effects |

Explicitly forbidden:
- Monotonic vs wall comparisons in WeGlide domain logic.
- Replay vs wall comparisons for network side effects.
- Direct system time calls in domain/policy paths.

### 2.4 Threading and Cadence

- Dispatcher ownership:
  - `Main`: auth launch, prompt rendering, UI events
  - `Default`: pure policy/eligibility/mapping resolution if needed
  - `IO`: Room, encrypted token storage, file digest, file part creation, HTTP, worker execution
- Primary cadence/gating sensor:
  - event-driven from auth, aircraft sync requests, manual upload intent, and IGC finalization success
- Hot-path latency budget:
  - live finalize path may only enqueue/publish prompt state; it must not perform upload or heavy digesting synchronously

### 2.5 Replay Determinism

- Deterministic for same input: Yes, for mapping, eligibility, queue insertion decisions
- Randomness used: No
- Replay/live divergence rules:
  - Replay must never enqueue or execute WeGlide upload automatically
  - Manual upload remains an explicit user action only
  - Live finalized flights may create prompt/queue state only after successful finalization

### 2.6 Enforcement Coverage (Mandatory)

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| Duplicate aircraft identity ownership | `ARCHITECTURE.md` SSOT | Unit test + review | New mapping/repository tests |
| UI/business logic drift | `ARCHITECTURE.md` MVVM + UDF | Unit test + review | WeGlide use case and ViewModel tests |
| Direct time usage in policy/domain | `ARCHITECTURE.md` timebase, `CODING_RULES.md` 1A | `scripts/arch_gate.py`, `enforceRules`, review | New WeGlide domain files |
| Worker/network logic leaking into UI/VM | `ARCHITECTURE.md` dependency direction | `enforceRules`, review | WeGlide UI/worker/repository code |
| Finalize path doing upload work | `AGENT.md` phase contract | Unit/integration test + review | Vario/IGC integration tests |
| Replay network side effects | `ARCHITECTURE.md` determinism | Unit test | Finalize-trigger and queue tests |
| Duplicate upload rows or duplicate remote upload | SSOT/idempotency policy | Repository/worker tests | Upload queue tests |

### 2.7 Visual UX SLO Contract

This is not a map-render-loop change, but it is a user-visible operational flow change.

| Outcome | SLO ID | Baseline | Target | Validation/Test | Phase Gate |
|---|---|---|---|---|---|
| Connected WeGlide account and aircraft mappings are visible from one settings path | WG-UX-01 | Not implemented | 100% in covered tests | Settings ViewModel/UI tests | Phase 3 |
| Each XCPro profile can resolve exactly one mapped WeGlide aircraft or an explicit missing-mapping state | WG-UX-02 | Not implemented | 100% in covered tests | Mapping resolution tests | Phase 3 |
| Eligible finalized live flight creates at most one upload prompt/queue action | WG-UX-03 | Not implemented | 100% in covered tests | Finalize-trigger tests | Phase 4 |
| Same finalized IGC content is not uploaded twice after success | WG-ENG-01 | Not implemented | 100% in covered tests | Queue/repository duplicate tests | Phase 5 |

## 3) Data Flow (Before -> After)

Before:

```text
ProfileRepository -> activeProfile
GliderRepository -> selected local glider model for profile
IgcSessionStateMachine
  -> VarioServiceManager
  -> IgcRecordingActionSink
  -> IgcFlightLogRepository
  -> finalized IGC entry published
```

After:

```text
ProfileRepository -> activeProfile(profileId)
GliderRepository(profileId) -> local aircraft/polar selection
WeGlide account store -> linked account state
WeGlide aircraft cache -> searchable aircraft list
WeGlide mapping repository(profileId -> weglideAircraftId)

IgcSessionStateMachine
  -> VarioServiceManager
  -> IgcRecordingActionSink
  -> IgcFlightLogRepository
  -> WeGlide finalize-trigger use case
     -> resolve active profileId
     -> resolve mapped WeGlide aircraft
     -> insert queue/prompt state
     -> schedule WeGlide worker if policy allows

IgcFiles UI
  -> manual upload intent
  -> WeGlide upload use case
  -> queue/repository

WeGlide worker
  -> token store/account store
  -> refresh token if needed
  -> WeGlide API
  -> queue state update
  -> status flows back to UI
```

## 4) Implementation Phases

### Phase 0 - Rebaseline and Planning Lock

- Goal:
  - Align the implementation plan to the advanced spec.
  - Mark the current WeGlide settings implementation as temporary baseline only.
  - Lock the current live IGC finalization path so WeGlide changes do not blur ownership.
- Files to change:
  - `docs/WEGLIDE/01_WEGLIDE_UPLOAD_PHASED_IP_2026-03-11.md`
  - optionally `docs/WEGLIDE/XCPRO_WEGLIDE_ADVANCED_SPEC.md` if wording corrections are needed
- Tests to add/update:
  - baseline tests confirming current finalization still ends at published IGC without WeGlide side effects
- Exit criteria:
  - plan and advanced spec agree
  - WeGlide temporary fields are explicitly called out as non-production

### Phase 1 - SSOT Foundation

- Goal:
  - Create the authoritative WeGlide data model and storage contracts first.
  - Attach WeGlide aircraft mapping to existing XCPro `profileId` ownership.
- Files to change:
  - new WeGlide domain/data files, ideally in a dedicated `feature/weglide` boundary
  - likely additions:
    - `WeGlideAccountLink`
    - `WeGlideTokenBundle`
    - `WeGlideAircraftEntity`
    - `WeGlideAircraftMappingEntity`
    - `WeGlideUploadQueueEntity`
    - DAOs for aircraft, mapping, queue
    - `WeGlideRepository` interface
    - `WeGlideTokenStore` interface
- Tests to add/update:
  - entity/DAO tests
  - mapping uniqueness tests
  - repository contract tests
- Exit criteria:
  - one authoritative `profileId -> weglideAircraftId` mapping exists
  - no global single-aircraft WeGlide setting remains in the design

### Phase 2 - OAuth and Account Link

- Goal:
  - Implement OAuth 2 authorization code with PKCE and encrypted token storage.
  - Establish linked account state without touching upload queue yet.
- Files to change:
  - auth manager, PKCE factory, redirect activity, token store, auth API, DI
  - manifest redirect registration
  - WeGlide settings/account connection UI state
- Tests to add/update:
  - PKCE tests
  - state validation tests
  - token expiry and refresh tests
  - disconnect/clear-token tests
- Exit criteria:
  - user can connect/disconnect WeGlide
  - account link state is durable and secure
  - no password storage is introduced

### Phase 3 - Aircraft Sync and Profile Mapping UI

- Goal:
  - Fetch/cache aircraft list and let the user map each XCPro profile to a WeGlide aircraft.
- Files to change:
  - aircraft sync service/API
  - aircraft cache DAO/repository
  - mapping use cases such as:
    - `ResolveWeGlideAircraftForProfileUseCase`
    - `SetWeGlideAircraftMappingUseCase`
  - WeGlide settings screen/viewmodel/content
  - profile-linked UI surfaces
- Tests to add/update:
  - aircraft sync tests
  - mapping resolution tests
  - settings ViewModel tests
- Exit criteria:
  - connected account can fetch aircraft list
  - each XCPro profile can be mapped independently
  - settings screen shows current profile mappings clearly

### Phase 4 - Finalized-Flight Queue Trigger and Manual Upload Entry

- Goal:
  - Wire upload intent sources without running direct network work in UI or finalize paths.
- Files to change:
  - `feature/map/src/main/java/com/example/xcpro/vario/VarioServiceManager.kt`
  - `feature/igc/src/main/java/com/example/xcpro/igc/ui/IgcFilesViewModel.kt`
  - `feature/igc/src/main/java/com/example/xcpro/igc/usecase/IgcFilesUseCase.kt`
  - WeGlide queue use cases and schedulers
- Tests to add/update:
  - finalize-trigger use case tests
  - manual upload intent tests
  - replay exclusion tests
- Exit criteria:
  - successful live finalization can create exactly one queue/prompt action
  - manual upload action exists from IGC files UI
  - no HTTP call happens directly from finalize callback or ViewModel

### Phase 5 - Upload Execution, Retry, and Duplicate Protection

- Goal:
  - Execute uploads via WorkManager and repository logic.
  - Add duplicate suppression and response classification.
- Files to change:
  - upload worker
  - repository implementation
  - multipart factory
  - response parser/classifier
  - queue scheduler
- Tests to add/update:
  - 201 success parsing tests
  - 401 refresh/fail tests
  - 422 permanent failure tests
  - 5xx retryable failure tests
  - duplicate hash and queue dedupe tests
- Exit criteria:
  - OAuth-first upload works through repository/worker path
  - duplicate uploads are suppressed locally
  - queue states move deterministically

### Phase 6 - Diagnostics, Status UX, and Hardening

- Goal:
  - Complete settings diagnostics, upload status visibility, notifications, docs, and final verification.
- Files to change:
  - WeGlide settings diagnostics/status cards
  - flight-detail/status surfaces if approved
  - `docs/ARCHITECTURE/PIPELINE.md` if final wiring changes the published pipeline
  - other WeGlide docs if implementation details diverge
- Tests to add/update:
  - diagnostics/status UI tests where practical
  - worker retry exhaustion tests
  - persisted queue recovery tests
- Exit criteria:
  - status and failure states are understandable to users
  - required verification passes or unrelated failures are handled separately

## 5) Test Plan

- Unit tests:
  - PKCE generation
  - auth state validation
  - token expiry/refresh policy
  - mapping resolution by `profileId`
  - upload eligibility and duplicate detection
  - upload response classification
- Integration tests:
  - connect flow success/failure
  - aircraft sync success/cached fallback
  - queue insert on finalization success
  - manual upload enqueue
  - replay exclusion
  - upload success 201
  - upload 401 then refresh
  - upload 422 permanent failure
  - upload 503 retry
- UI/instrumentation tests when relevant:
  - connect/disconnect UI
  - aircraft picker search/mapping
  - WeGlide settings state rendering
  - post-flight prompt/status rendering if implemented in UI
- Degraded/failure-mode tests:
  - missing mapping for active profile
  - missing token/expired token
  - malformed response body
  - offline finalization then later upload
  - duplicate finalized IGC content

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

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| Current temporary WeGlide settings shape drifts further from approved design | Rework and wrong persisted data | Freeze and replace the temporary field model early | XCPro Team |
| WeGlide mapping duplicates existing local aircraft identity | Confusing state and bugs | Keep local aircraft identity in `ProfileRepository` + `GliderRepository`; WeGlide stores remote mapping only | XCPro Team |
| Finalization path latency regression | Flight-end instability | Restrict finalize path to queue/prompt creation only | XCPro Team |
| Replay accidentally enqueues upload | Determinism break | Explicit replay exclusion tests and guards | XCPro Team |
| OAuth server details remain partially unknown | Integration risk | Keep auth endpoints/scopes configurable and document TODOs | XCPro Team |
| Existing unrelated unit failures pollute readiness | Harder to prove completion | Treat them as separate baseline issues and isolate in evidence | XCPro Team |

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- `profileId -> weglideAircraftId` mapping is the only WeGlide aircraft authority
- Replay behavior remains side-effect free
- No password storage is introduced
- Finalization path remains thin and deterministic
- Queue and upload state survive process death
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

## 8) Rollback Plan

- What can be reverted independently:
  - WeGlide account connection UI
  - aircraft sync and mapping UI
  - manual upload entrypoint
  - finalized-flight queue trigger
  - worker/repository upload execution
- Recovery steps if regression is detected:
  - first disable/suspend finalize-trigger queue insertion
  - keep account/mapping screens if harmless
  - revert worker scheduling and repository execution next
  - confirm IGC finalization returns to pre-WeGlide behavior

## Recommended First Implementation Slice

Start with Phase 1, not OAuth.

1. Define the WeGlide persistent SSOT:
   - account link entity/model
   - aircraft cache entity
   - aircraft mapping entity
   - upload queue entity
2. Key mapping by `UserProfile.id`.
3. Add a pure use case:
   - `ResolveWeGlideAircraftForProfileUseCase`
4. Remove the current temporary `pilotId/token` assumptions from the active WeGlide implementation path.

Reason:
- XCPro already has authoritative profile and local aircraft identity.
- The advanced spec's hardest requirement is multi-aircraft mapping.
- If that ownership is wrong, OAuth and upload code will be built on the wrong model.
