# Private Follow Live Entitlement Phase 3

## 0) Metadata

- Title: Private-follow live entitlement, authenticated session ownership, and authorized watch flow
- Owner: Codex
- Date: 2026-03-24
- Issue/PR: Phase 3 private-follow live entitlement
- Status: In progress

## 1) Scope

- Problem statement:
  - XCPro already has signed-in account state, privacy defaults, search, follow requests, and accepted follow edges, but live sessions are still treated as anonymous public sessions.
  - The current public LiveFollow lane cannot enforce `off | followers | public` visibility or viewer entitlement.
- Why now:
  - Phase 1 and Phase 2 are already implemented, and the approved Phase 3 brief requires the live-value path to use the existing relationship graph.
- In scope:
  - server-owned live-session `owner_user_id` and `visibility`
  - authenticated `POST /api/v2/live/session/start`
  - authenticated visibility patch, following-active list, user live lookup, and session read endpoints
  - public-v1 endpoint gating so non-public sessions do not leak
  - app-side pilot visibility selection
  - app-side signed-in following-live browse and authorized watch flow
  - reuse of existing v1 write-token upload path for positions/task/end
- Out of scope:
  - push notifications
  - block/report/mute
  - follower/following list screens
  - redesign of the public Friends Flying spectator MVP
- User-visible impact:
  - signed-in pilots can choose `Off`, `Followers`, or `Public` for live sessions
  - signed-in followers can browse currently live followed pilots and open authorized watches
  - non-public sessions disappear from public active/share/session reads
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Live session owner and visibility | `XCPro_Server/app/main.py` `LiveSession` row | server JSON responses and app session snapshot | app-local guessed owner/visibility |
| Live session transport/write token | `feature/livefollow/.../CurrentApiLiveFollowSessionGateway.kt` | `LiveFollowSessionGatewaySnapshot` | UI or ViewModel copies of write token |
| App live-session state | `feature/livefollow/.../LiveFollowSessionRepository.kt` | `StateFlow<LiveFollowSessionSnapshot>` | separate pilot/watch state mirrors |
| Public active pilot browse list | `feature/livefollow/.../FriendsFlyingRepository.kt` | `StateFlow<FriendsFlyingSnapshot>` | UI-owned mutable lists |
| Signed-in following-live browse list | new focused following repository in `feature/livefollow` | `StateFlow<FollowingLiveSnapshot>` | reuse inside public repo with mixed ownership |
| Watch polling lane selection | `feature/livefollow/.../CurrentApiDirectWatchTrafficSource.kt` | `WatchTrafficRepository.state` | UI-picked endpoints |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| `LiveSession.owner_user_id` | server `LiveSession` row | authenticated v2 start only | v2 live read/list endpoints | authenticated caller identity | DB migration + SQLAlchemy model | never for signed-in v2 sessions; null for legacy v1 sessions | Wall | API tests |
| `LiveSession.visibility` | server `LiveSession` row | v2 start + v2 visibility patch | v1/v2 read/list gating and app session snapshot | request body or privacy default | DB migration + SQLAlchemy model | patchable while session active; defaults to `public` for legacy v1 | Wall | API tests |
| App selected pilot visibility | pilot ViewModel local editor state | pilot UI intents | pilot UI state | account privacy default + explicit user selection | none | reset on remote privacy sync and after session end when needed | None | VM tests |
| Authorized following-live list | following repository | repository refresh only | friends-following UI tab | authenticated `/api/v2/live/following/active` | none | clear on sign-out, replay, or refresh failure | Wall | repository/data-source tests |
| Watch lookup lane | session repository snapshot | join watch commands only | direct watch traffic source | public share/session route or authenticated following selection | none | clear on leave/watch stop | None | gateway/watch tests |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  - `feature:livefollow`
  - app navigation host
  - adjacent server repo `XCPro_Server`
- Any boundary risk:
  - do not let UI infer visibility entitlement from public share codes
  - do not make watch polling choose endpoints ad hoc outside the data/session seam
  - do not make data-layer live session code depend upward on account ViewModels or use-cases

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/session/CurrentApiLiveFollowSessionGateway.kt` | Existing session transport owner | transport-local write token + gateway snapshot + repository-owned session state | add auth-aware start/read/patch logic without moving session SSOT |
| `feature/livefollow/src/main/java/com/example/xcpro/livefollow/data/friends/CurrentApiActivePilotsDataSource.kt` | Existing browse data-source seam | narrow HTTP fetcher returning focused list models | add a separate following-live data source instead of widening the public fetcher |
| `XCPro_Server/app/main.py` `/api/v2/me*` and follow-request handlers | Existing auth + relationship owner path | bearer validation and relationship lookup helpers | extend this owner with live entitlement helpers and v2 live endpoints |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| Live session visibility authority | implicit public-only v1 session behavior | explicit server `LiveSession.visibility` | entitlement must be enforced server-side | API tests |
| Signed-in following browse | public active list only | new following repository/data source | public and follower-aware lanes have different auth and identity needs | Kotlin tests |
| Watch endpoint selection | public-only lookup handling | lookup-type-aware direct watch source | follower-only sessions cannot poll public endpoints | Kotlin tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| Public v1 read endpoints | all live sessions visible when not ended | filter/gate by `visibility == public` | Server |
| Friends Flying authorized watch | public share-code only | session-id authorized join path for following-live entries | App |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/refactor/Private_Follow_Live_Entitlement_Phase3_Phased_IP_2026-03-24.md` | New | execution plan and state contract | task-level architecture record | not durable enough for ADR-only | No |
| `docs/ARCHITECTURE/ADR_PRIVATE_FOLLOW_LIVE_LANE_SPLIT_2026-03-24.md` | New | durable public-v1 + auth-v2 decision | long-lived API/boundary choice | not a task log | No |
| `../XCPro_Server/app/main.py` | Existing | live-session schema, entitlement helpers, v2 live endpoints, v1 gating | current authoritative server owner | server has no narrower split yet | No |
| `../XCPro_Server/app/alembic/versions/<phase3>.py` | New | schema migration for live-session ownership/visibility | persistence owner | not in app repo | No |
| `../XCPro_Server/app/tests/test_livefollow_api.py` | Existing | server entitlement and compatibility regressions | current API test owner | keep server proof near server code | No |
| `feature/livefollow/.../LiveFollowSessionModels.kt` | Existing | visibility enums and snapshot contract | session domain contract owner | not UI-only | No |
| `feature/livefollow/.../LiveFollowSessionGateway.kt` | Existing | gateway interface surface | transport seam owner | avoid leaking transport details into repo/UI | No |
| `feature/livefollow/.../CurrentApiLiveFollowSessionGateway.kt` | Existing | authenticated start/patch/public fallback transport | current transport owner | write token and HTTP details already live here | Yes if line budget pressure appears |
| `feature/livefollow/.../CurrentApiDirectWatchTrafficSource.kt` | Existing | public-v1 vs auth-v2 polling | current watch transport owner | do not move endpoint choice into UI | No |
| `feature/livefollow/.../following/*` | New | signed-in following-live fetch + SSOT | distinct owner from public browse lane | public repo would become mixed-purpose | No |
| `feature/livefollow/.../friends/*` | Existing | public + following tab UI composition | existing browse UI shell | keep pilot/watch code separate | likely split by tab helper if needed |
| `feature/livefollow/.../pilot/*` | Existing | visibility selector and signed-in/default visibility behavior | pilot UI/VM owner | do not push into gateway or screen-only state | No |
| `feature/livefollow/.../watch/LiveFollowWatchUseCase.kt` and `...ViewModel.kt` | Existing | authorized watch join path | current watch orchestration owner | preserve session repository as SSOT | No |
| `docs/ARCHITECTURE/PIPELINE.md` | Existing | livefollow pipeline wiring update | repo source of truth | required by docs rule | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `POST /api/v2/live/session/start` | server live-session owner | signed-in XCPro app | public server API | authenticated ownership + visibility start | durable API |
| `PATCH /api/v2/live/session/{session_id}/visibility` | server live-session owner | signed-in XCPro app | public server API | in-session visibility changes | durable API |
| `GET /api/v2/live/following/active` | server entitlement owner | signed-in XCPro app | public server API | follower-aware live discovery | durable API |
| `GET /api/v2/live/users/{user_id}` | server entitlement owner | signed-in XCPro app/future callers | public server API | per-user live lookup | durable API |
| `GET /api/v2/live/session/{session_id}` | server entitlement owner | signed-in XCPro app | public server API | authorized live reads | durable API |
| `LiveFollowSessionVisibility` and authenticated watch lookup types | `feature:livefollow` session contract | pilot/watch/friends owners | internal | app lane selection without UI inference | durable internal contract |

### 2.2F Scope Ownership and Lifetime

- No new long-lived scope owner is planned.
- Existing `feature:livefollow` repository scopes remain the owners for session, browse, and watch collection.

### 2.2G Compatibility Shim Inventory

| Shim / Bridge | Owner | Reason | Target Replacement | Removal Trigger | Test Coverage |
|---|---|---|---|---|---|
| v2 authenticated start reusing v1 position/task/end writes | `CurrentApiLiveFollowSessionGateway.kt` + server live-session owner | preserve current uploader path while adding entitlement | future full authenticated write lane if needed | only when server/app intentionally replace write-token uploads | gateway + server API regressions |

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| live visibility values `off | followers | public` | server `app/main.py` plus app `LiveFollowSessionVisibility` mapping | server endpoints, session gateway, pilot UI | entitlement is a cross-repo contract and must remain explicit | No |

### 2.2I Stateless Object / Singleton Boundary

- No new Kotlin `object` or hidden singleton state owners are planned.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| Session `created_at`, `ended_at`, `last_position_at` | Wall | persisted server timestamps |
| Position wire timestamps | Wall | existing v1 upload contract |
| Browse recency labels | Wall | UI display only |
| Watch `fixMonoMs` derivation from wire payload | Monotonic derived from wall age | existing local watch runtime contract |

Explicitly forbidden comparisons:

- Monotonic vs wall in domain logic
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - HTTP fetches on injected IO dispatchers
  - repositories and ViewModels keep current structured concurrency owners
- Primary cadence/gating sensor:
  - unchanged; watch polling remains timer-driven and pilot uploads remain driven by live ownship/task streams
- Hot-path latency budget:
  - unchanged for flight runtime; added auth logic stays off the flight-math path

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: Yes, existing session/share/write token generation on the server at session-creation boundaries only
- Replay/live divergence rules:
  - replay still blocks session side effects and browse/watch refresh
  - no replay logic will depend on bearer auth or live visibility

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| signed-out following-live refresh | User Action | following repository/UI | prompt to sign in for follower-only live | no network call | Kotlin tests |
| non-owner visibility patch | User Action | server entitlement owner | failure message from API | no fallback | API tests |
| unauthorized non-public live read via public v1 | Unavailable | server entitlement owner | 404/not found | no fallback | API tests |
| unavailable auth transport | Recoverable / Degraded | gateway/data source | transport message in UI | retry via existing refresh/start actions | Kotlin tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| live session id | server start endpoint | existing UUID boundary | No | server owns session persistence |
| share code | server start endpoint | existing random server generator | No | server owns public lane identity |
| write token | server start endpoint | existing random server generator | No | server owns write authorization |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type | File/Test |
|---|---|---|---|
| duplicate session visibility owner in app | ARCHITECTURE authoritative state contract | review + tests | gateway/session repository tests |
| non-public session leaking through public endpoints | approved Phase 3 contract | API tests | `../XCPro_Server/app/tests/test_livefollow_api.py` |
| UI choosing watch endpoints ad hoc | MVVM/UDF layering rules | review + Kotlin tests | watch VM/source tests |
| unsigned public pilot regression | compatibility requirement | API + gateway tests | server tests + gateway tests |

## 3) Data Flow (Before -> After)

Before:

`Pilot UI -> LiveFollowPilotUseCase -> LiveFollowSessionRepository -> CurrentApiLiveFollowSessionGateway -> POST /api/v1/session/start -> v1 public reads`

After:

`Pilot UI -> LiveFollowPilotUseCase -> LiveFollowSessionRepository -> CurrentApiLiveFollowSessionGateway -> POST /api/v2/live/session/start (signed-in) or POST /api/v1/session/start (legacy public fallback) -> v1 write-token uploads`

`Friends Flying public tab -> FriendsFlyingRepository -> GET /api/v1/live/active`

`Friends Flying following tab -> FollowingLiveRepository -> GET /api/v2/live/following/active -> LiveFollowWatchViewModel authorized join -> GET /api/v2/live/session/{id}`

## 4) Implementation Phases

- Phase 0:
  - Goal: audit existing live/session paths and lock owners
  - Files: docs plan + ADR
  - Tests: none yet
  - Exit criteria: ownership and API plan explicit
- Phase 1:
  - Goal: server session ownership/visibility and v2 endpoints
  - Files: server main, migration, API tests
  - Tests: entitlement and compatibility regressions
  - Exit criteria: public lane gated, authenticated lane works
- Phase 2:
  - Goal: app transport/session/watch contracts
  - Files: livefollow session/watch transport files and tests
  - Tests: gateway/watch tests
  - Exit criteria: authenticated start/watch flow works without breaking public watch
- Phase 3:
  - Goal: app pilot visibility selector and following-live browse tab
  - Files: pilot/friends UI + following repository/data source + tests
  - Tests: VM/data-source tests
  - Exit criteria: signed-in pilots choose visibility and signed-in followers browse live followed pilots
- Phase 4:
  - Goal: docs + verification
  - Files: `PIPELINE.md`, final test/build results
  - Exit criteria: required gates complete

## 5) Test Plan

- Unit tests:
  - session gateway start/patch/read behavior
  - following-live data source and repository
  - pilot and watch ViewModel behavior
- Replay/regression tests:
  - existing replay-block behavior remains intact
- UI/instrumentation tests (if needed):
  - none planned unless runtime collection behavior forces it
- Degraded/failure-mode tests:
  - signed-out following tab
  - unauthorized reads
  - transport failures
- Boundary tests for removed bypasses:
  - non-public v1 read rejection
  - authenticated v2 read authorization
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / policy / math | Unit tests + regression cases | server entitlement tests + Kotlin gateway/repo tests |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | existing watch tests plus replay-block assertions |
| Persistence / settings / restore | Round-trip / restore / migration tests | Alembic migration + API tests |
| Ownership move / bypass removal / API boundary | Boundary lock tests | public-v1 gating and authorized-v2 read tests |
| UI interaction / lifecycle | UI or instrumentation coverage | ViewModel tests; no runtime gesture changes planned |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | not applicable to map/render SLOs in this slice |

Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Server verification:

```bash
pytest app/tests/test_livefollow_api.py
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| public lane regression | public watch breaks | keep v1 write/read compatibility tests and signed-out public fallback | Codex |
| auth lane mixed into public browse owner | long-term maintenance drift | separate following repository/data source | Codex |
| hidden watch-endpoint inference | unauthorized polling bugs | explicit lookup type for authenticated vs public watch | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: Yes
- ADR file:
  - `docs/ARCHITECTURE/ADR_PRIVATE_FOLLOW_LIVE_LANE_SPLIT_2026-03-24.md`
- Decision summary:
  - keep the public v1 lane for anonymous/public watch and add a parallel authenticated v2 live lane that owns entitlement and reuses the existing v1 writer path
- Why this belongs in an ADR instead of plan notes:
  - it changes durable server/app API surface and boundary responsibilities

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- Error/degraded-state behavior is explicit and tested where behavior changed
- Ownership/boundary/public API decisions are captured in the ADR
- `KNOWN_DEVIATIONS.md` unchanged unless an approved exception becomes necessary

## 8) Rollback Plan

- What can be reverted independently:
  - following-live browse tab
  - authenticated app transport additions
  - server v2 live endpoints
  - v1 public gating if emergency rollback is required
- Recovery steps if regression is detected:
  - revert the authenticated lane changes first while preserving existing public v1 session flow
