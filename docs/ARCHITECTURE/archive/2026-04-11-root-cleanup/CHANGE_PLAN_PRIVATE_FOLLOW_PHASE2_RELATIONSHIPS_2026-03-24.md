## Purpose

Implement Private Follow Phase 2 on top of the existing account/auth foundation:

- authenticated user search by handle
- follow request creation
- incoming/outgoing pending request lists
- accept/decline actions
- persisted directed follow relationships

Public `/api/v1/live/*` remains unchanged and follower-only live gating stays out of scope.

## 0) Metadata

- Title: Private Follow Phase 2 Relationships
- Owner: Codex / XCPro
- Date: 2026-03-24
- Issue/PR: Private-follow relationship graph slice
- Status: In progress

## 1) Scope

- Problem statement:
  Phase 1 established signed-in account/profile/privacy ownership, but there is still no usable social graph for finding pilots and following them.
- Why now:
  Search, requests, and accepted follow edges are the minimum durable data layer needed before any later follower-only live visibility can be enforced.
- In scope:
  - server `follow_requests` and `follow_edges`
  - authenticated `/api/v2/users/search`
  - authenticated follow-request create/list/accept/decline endpoints
  - app-side search and request UI in the LiveFollow-owned account area
  - relationship state rendering in search/request surfaces
- Out of scope:
  - new auth provider work
  - public LiveFollow changes
  - follower-only live gating
  - follower/following list screens
  - blocks/mute/report
- User-visible impact:
  - signed-in users can search pilots by handle
  - signed-in users can send and resolve follow requests
  - accepted relationships persist for later private-live slices
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Current-user profile/privacy/request snapshot | `XcAccountRepository` | `StateFlow<XcAccountSnapshot>` | request lists mirrored as authoritative UI state |
| Search query and current search results | `XcAccountViewModel` | `XcAccountUiState` | repository-owned cached search results treated as truth |
| Persisted follow requests | XCPro_Server `follow_requests` | `/api/v2/follow-requests/*` | app-local request truth |
| Persisted follow relationships | XCPro_Server `follow_edges` | relationship-state fields in `/api/v2/*` | request rows treated as accepted-edge truth without edge persistence |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Incoming pending requests | `XcAccountRepository` | refresh, accept, decline, sign-out | use case -> viewmodel -> Manage Account UI | `/api/v2/follow-requests/incoming` | XCPro_Server | sign-out, refresh, accept, decline | Wall | repository/viewmodel tests |
| Outgoing pending requests | `XcAccountRepository` | refresh, send follow request, sign-out | use case -> viewmodel -> Manage Account UI | `/api/v2/follow-requests/outgoing` | XCPro_Server | sign-out, refresh, follow send | Wall | repository/viewmodel tests |
| Search query/results | `XcAccountViewModel` | query edit, search action, follow actions rerunning current search | Manage Account UI | `/api/v2/users/search` | none; ephemeral screen state | sign-out, query clear, screen recreation | Wall | viewmodel/data-source tests |
| Follow request rows | XCPro_Server `follow_requests` | create, accept, decline | `/api/v2/follow-requests/*` | authenticated request actions | PostgreSQL | never client-cleared directly | Wall | API tests |
| Follow edge rows | XCPro_Server `follow_edges` | accept, auto-approve | relationship state helper | accepted follow transitions | PostgreSQL | future unfollow slice | Wall | API tests |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  - `feature:livefollow.account`
  - `feature:livefollow` DI/build transport only
  - `XCPro_Server/app/*`
- Boundary risk:
  - keep search result truth out of composables
  - keep bearer/session resolution centralized in the repository
  - avoid pushing follow policy rules into UI

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/livefollow/.../account/XcAccountRepository.kt` | existing private-follow account SSOT | repository owns signed-in current-user state and authenticated transport | search results stay viewmodel-owned because they are ephemeral query state |
| `feature/livefollow/.../account/CurrentApiXcAccountDataSource.kt` | existing `/api/v2/me*` transport owner | focused authenticated OkHttp transport | add relationship endpoints without changing the auth seam |
| `docs/LIVEFOLLOW/XCPro_Private_Follow_Proposed_API_Contract_v1.md` | existing in-repo relationship contract | relationship state vocabulary and endpoint direction | use `POST /api/v2/follow-requests` from the approved brief instead of `/users/{id}/follow` |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/ARCHITECTURE/CHANGE_PLAN_PRIVATE_FOLLOW_PHASE2_RELATIONSHIPS_2026-03-24.md` | New | Phase 2 ownership contract | required non-trivial plan | not product docs | No |
| `feature/livefollow/.../account/XcRelationshipModels.kt` | New | relationship/search domain models | keeps account model file bounded | avoid overloading `XcAccountModels.kt` | No |
| `feature/livefollow/.../account/XcRelationshipPayloads.kt` | New | parser helpers for relationship endpoints | keeps payload parsing focused | avoid bloating existing payload file | No |
| `feature/livefollow/.../account/XcAccountRemoteDataSource.kt` | Existing | authenticated transport calls | current `/api/v2` data-source owner | not UI/repository-owned | No |
| `feature/livefollow/.../account/XcAccountRepository.kt` | Existing | current-user SSOT plus request lists and actions | already owns private-follow signed-in state | avoid duplicate relationship repository | Maybe; only if file grows too broad |
| `feature/livefollow/.../account/XcAccountViewModel.kt` | Existing | screen orchestration for search/request actions | Manage Account screen state owner | not repository-owned | Maybe; split support/helpers if needed |
| `feature/livefollow/.../account/ManageAccountRelationshipSections.kt` | New | relationship/search UI rendering | keeps current section file under line budget | avoid one oversized mixed UI file | No |
| `feature/livefollow/.../account/ManageAccountSections.kt` | Existing | existing sign-in/profile/privacy cards | preserve narrow UI ownership | not relationship-specific | No |
| `XCPro_Server/app/main.py` | Existing | schema helpers, relationship state helpers, endpoints | current FastAPI owner | avoid parallel server module for one slice | No |
| `XCPro_Server/app/alembic/versions/<phase2>.py` | New | relationship schema migration | existing migration owner | not tests/docs | No |
| `XCPro_Server/app/tests/test_livefollow_api.py` | Existing | API regression plus relationship behavior tests | current LiveFollow server suite | keep `/api/v1` regression coverage adjacent | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `GET /api/v2/users/search` | XCPro_Server | `feature:livefollow.account` | public HTTP | pilot discovery | durable |
| `POST /api/v2/follow-requests` | XCPro_Server | `feature:livefollow.account` | public HTTP | request creation | durable |
| `GET /api/v2/follow-requests/incoming` | XCPro_Server | `feature:livefollow.account` | public HTTP | request inbox | durable |
| `GET /api/v2/follow-requests/outgoing` | XCPro_Server | `feature:livefollow.account` | public HTTP | request outbox | durable |
| `POST /api/v2/follow-requests/{id}/accept` | XCPro_Server | `feature:livefollow.account` | public HTTP | request acceptance | durable |
| `POST /api/v2/follow-requests/{id}/decline` | XCPro_Server | `feature:livefollow.account` | public HTTP | request decline | durable |

### 2.5A Error and Degraded-State Contract

| Condition | Category | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| self-follow attempt | User Action | server validation | clear rejection message | no retry until target changes | API + VM tests |
| duplicate active request | Recoverable | server validation | clear rejection message | wait for resolution | API tests |
| target follow policy closed | User Action | server validation | clear rejection message | none until target changes policy | API tests |
| search query too short | User Action | server validation + VM guard | no network call / clear prompt | refine query | VM + API tests |
| unauthenticated relationship call | User Action | repository auth seam | sign-out / re-auth prompt | sign in again | repository tests |

## 3) Data Flow (Before -> After)

Before:

```
ManageAccount UI
  -> XcAccountViewModel
  -> XcAccountUseCase
  -> XcAccountRepository
  -> /api/v2/me*
```

After:

```
ManageAccount UI
  -> XcAccountViewModel
     -> search query/results (ephemeral UI state)
  -> XcAccountUseCase
  -> XcAccountRepository (SSOT for signed-in current-user + request lists)
     -> CurrentApiXcAccountDataSource
        -> /api/v2/me*
        -> /api/v2/users/search
        -> /api/v2/follow-requests/*
  -> XCPro_Server
     -> users / pilot_profiles / privacy_settings
     -> follow_requests
     -> follow_edges
```

## 4) Implementation Phases

- Phase 0
  - Goal: document ownership and endpoint shape
  - Files: this plan
  - Exit criteria: relationship model chosen and file ownership explicit
- Phase 1
  - Goal: add server schema and endpoints
  - Files: `main.py`, migration, server tests
  - Exit criteria: search/request/accept/decline endpoints pass tests
- Phase 2
  - Goal: add app models, transport, repository, and viewmodel support
  - Files: `feature:livefollow.account` files
  - Exit criteria: account area can search, request, and resolve requests against `/api/v2`
- Phase 3
  - Goal: add relationship UI cards and verification
  - Files: Manage Account UI files, tests
  - Exit criteria: manual test flow in the brief is covered locally as far as possible

## 5) Test Plan

- Unit tests:
  - relationship payload parsing
  - repository refresh and follow-action state updates
  - viewmodel search/request state transitions
- Replay/regression tests:
  - none; slice is outside replay/runtime pipeline
- Degraded/failure-mode tests:
  - self-follow blocked
  - duplicate pending request blocked
  - accept creates edge
  - decline leaves no edge
  - hidden profiles omitted from search
- Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
python -m unittest app.tests.test_livefollow_api
```

## 6) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| relationship logic bloats current account files | review and maintenance risk | split models/payloads/UI sections into focused files | Codex |
| stale search results after accept/decline | confusing UI state | rerun current search after successful request actions | Codex |
| public LiveFollow regression | high | keep all changes in authenticated `/api/v2` and retain v1 tests | Codex |
