## Purpose

Implement the Phase 1 private-follow foundation across XCPro and XCPro_Server:

- authenticated XCPro account state for the private-follow lane
- server-side `users`, `auth_identities`, `pilot_profiles`, and `privacy_settings`
- authenticated `/api/v2/me*` endpoints
- app onboarding/editing UI for cloud profile + privacy
- no search/follow/follower-only-live behavior yet
- no regression to the current public `/api/v1/live/*` lane

Read first:

1. `ARCHITECTURE.md`
2. `CODING_RULES.md`
3. `PIPELINE.md`
4. `CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: Private Follow Phase 1 Account/Profile Foundation
- Owner: Codex / XCPro
- Date: 2026-03-24
- Issue/PR: Phase 1 private-follow foundation
- Status: In progress

## 1) Scope

- Problem statement:
  XCPro needs a signed-in account/profile/privacy foundation for the future private-follow lane, but the repo currently only has the public LiveFollow lane plus local aircraft profile management.
- Why now:
  Follow relationships and follower-only live ACL cannot be added safely until the app and server share a stable signed-in `user_id` model and profile/privacy owner.
- In scope:
  - server auth seam for bearer-protected `/api/v2/me*`
  - server persistence for users, auth identities, pilot profiles, privacy settings
  - app account session storage + bearer bootstrap
  - app signed-out/signed-in/onboarding/profile-edit/privacy-edit UX
  - explicit report of existing auth infra and remaining provider-specific setup
- Out of scope:
  - user search
  - follow edges / requests / blocks
  - follower/following lists
  - follower-only live ACL
  - changing public `/api/v1/live/*`
  - inventing a password system
- User-visible impact:
  - `Manage Account` becomes the XCPro private-follow account host
  - signed-out users get a clear sign-in entry
  - signed-in users can finish profile onboarding and edit privacy settings
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| XCPro bearer session in app | `XcAccountRepository` + encrypted session store | `StateFlow<XcAccountSnapshot>` | UI-local auth truth, ad hoc token caches |
| XCPro cloud profile in app | `XcAccountRepository` | `StateFlow<XcAccountSnapshot>` | separate screen-owned profile mirrors treated as truth |
| XCPro cloud privacy state in app | `XcAccountRepository` | `StateFlow<XcAccountSnapshot>` | separate screen-owned privacy mirrors treated as truth |
| Signed-in user identity on server | `users` + `auth_identities` | `/api/v2/me*` | profile-only or client-only user IDs |
| Pilot profile persistence on server | `pilot_profiles` | `/api/v2/me`, `/api/v2/me/profile` | app-local only copies presented as server truth |
| Privacy persistence on server | `privacy_settings` | `/api/v2/me`, `/api/v2/me/privacy` | app-local only privacy copies presented as server truth |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| App account session | `XcAccountRepository` | sign-in success, sign-out, bootstrap 401 recovery | use case -> viewmodel -> manage-account UI | auth provider result + encrypted session store | encrypted session store | sign-out, invalid bearer, store clear | Wall | repository/viewmodel tests |
| App account snapshot | `XcAccountRepository` | bootstrap, profile patch, privacy patch, sign-out | use case -> viewmodel -> manage-account UI | session + `/api/v2/me*` reads | session store for bearer only; server for profile/privacy | sign-out, auth loss | Wall | repository/viewmodel tests |
| Server user record | `users` | authenticated identity resolution | `/api/v2/me*` handlers | bearer token resolver output | PostgreSQL | never client-cleared in Phase 1 | Wall | API + schema tests |
| Server pilot profile | `pilot_profiles` | `/api/v2/me/profile` | `/api/v2/me*` | user row + validated patch payload | PostgreSQL | nullable fields until onboarding completes | Wall | API + validation tests |
| Server privacy settings | `privacy_settings` | user bootstrap defaults, `/api/v2/me/privacy` | `/api/v2/me*` | user row + validated patch payload | PostgreSQL | reset only by explicit patch | Wall | API + validation tests |

### 2.2 Dependency Direction

Confirm dependency flow remains:

`UI -> domain -> data`

- Modules/files touched:
  - `feature:livefollow` account UI/use-case/data/auth files
  - `app` navigation wiring
  - `XCPro_Server/app/*` API/data/auth files
- Any boundary risk:
  - Avoid mixing XCPro cloud account ownership into `feature:profile`, which remains the local aircraft/profile owner.
  - Avoid moving business validation into Composables; keep validation in viewmodel/repository/server.

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/weglide/src/main/java/com/trust3/xcpro/weglide/auth/WeGlideAuthManager.kt` | existing external account/auth seam | keep provider exchange behind an auth manager/repository boundary | Phase 1 private-follow auth is simpler and will not implement full provider exchange in-repo without external setup |
| `feature/weglide/src/main/java/com/trust3/xcpro/weglide/auth/WeGlideTokenStoreImpl.kt` | secure token persistence pattern | encrypted shared prefs with safe fallback | store one bearer session instead of OAuth refresh bundle |
| `feature/livefollow/src/main/java/com/trust3/xcpro/livefollow/data/friends/CurrentApiActivePilotsDataSource.kt` | current LiveFollow network style | OkHttp + focused parser + result mapping | add bearer header and `/api/v2/me*` account transport |
| `feature/profile/src/main/java/com/trust3/xcpro/profiles/ManageAccountScreen.kt` | existing account/settings route host | reuse `manage_account` route as the UI host | move XCPro cloud account ownership into `feature:livefollow`, not `feature:profile` |

### 2.2B Boundary Moves

| Responsibility | Old Owner | New Owner | Why | Validation |
|---|---|---|---|---|
| XCPro cloud account UI under `manage_account` | placeholder `feature:profile` screen | `feature:livefollow.account` | private-follow account lane belongs with LiveFollow/private-follow work, not aircraft profile settings | navigation compile + UI tests |

### 2.2C Bypass Removal Plan

| Bypass Callsite | Current Bypass | Planned Replacement | Phase |
|---|---|---|---|
| `manage_account` route -> local aircraft profile editor | placeholder account route piggybacks local profile state | route hosts XCPro cloud account state and can still link to local profile screens separately if needed | App phase |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/ARCHITECTURE/CHANGE_PLAN_PRIVATE_FOLLOW_PHASE1_ACCOUNT_FOUNDATION_2026-03-24.md` | New | plan/ownership contract | required non-trivial change plan owner | not a proposal doc or pipeline doc | No |
| `feature/livefollow/build.gradle.kts` | Existing | module config for account build settings | auth/account code lives in `feature:livefollow` | not app-owned feature logic | No |
| `feature/livefollow/.../account/*.kt` | New | cloud-account data/use-case/viewmodel/ui | private-follow account lane belongs in LiveFollow feature | not `feature:profile`, which owns local aircraft profiles | Yes, split by auth/data/ui responsibility |
| `feature/livefollow/.../di/LiveFollowModule.kt` or new account DI file | Existing/New | DI wiring for account repo/auth/data sources | module DI owner | not `app`, to preserve feature ownership | Maybe |
| `app/src/main/java/com/trust3/xcpro/AppNavGraph.kt` | Existing | route wiring only | app owns nav graph composition | not feature-owned | No |
| `XCPro_Server/app/main.py` | Existing | current FastAPI endpoint owner for LiveFollow + new account lane | existing service currently owns API and SQLAlchemy models | splitting whole server is unnecessary for Phase 1 | Yes if new helpers become too broad; prefer focused local helper sections |
| `XCPro_Server/app/alembic/versions/<phase1>.py` | New | schema migration for Phase 1 account tables | follows existing migration pattern | not in tests or docs | No |
| `XCPro_Server/app/tests/test_livefollow_api.py` | Existing | API regression + Phase 1 endpoint tests | current server test owner | keep v1 regression coverage in existing suite | No |

### 2.2E Module and API Surface

| Contract / API | Owner | Consumers | Visibility | Why Needed | Compatibility / Removal Plan |
|---|---|---|---|---|---|
| `/api/v2/me` | XCPro_Server | `feature:livefollow.account` | public HTTP | signed-in bootstrap owner | durable |
| `/api/v2/me/profile` | XCPro_Server | `feature:livefollow.account` | public HTTP | onboarding/profile edit owner | durable |
| `/api/v2/me/privacy` | XCPro_Server | `feature:livefollow.account` | public HTTP | privacy edit owner | durable |
| `XcAccountRepository` state/use-case seam | `feature:livefollow.account` | account viewmodel/UI | internal module API | single app SSOT for private-follow account state | durable |
| bearer auth resolver seam on server | XCPro_Server | `/api/v2/*` endpoints | internal server helper | keep provider-specific token verification behind one owner | durable; add real provider verifier later without changing endpoints |

### 2.2F Scope Ownership and Lifetime

| Scope / Owner | Why It Exists | Dispatcher | Cancellation Trigger | Why Not Caller-Owned / Existing Scope |
|---|---|---|---|---|
| `XcAccountRepository` scope | bootstrap persisted bearer session and keep account SSOT alive across screens | `Default`/`IO` injected dispatchers | app process / DI singleton teardown | repository is the authoritative state owner; UI scopes are too short-lived |

### 2.2G Compatibility Shim Inventory

No compatibility shim planned for Phase 1. Public `/api/v1/live/*` remains untouched rather than wrapped.

### 2.2H Canonical Formula / Policy Owner

| Formula / Constant / Policy | Canonical Owner File | Reused By | Why This Owner Is Canonical | Temporary Duplicates Allowed? |
|---|---|---|---|---|
| Handle normalization/validation rules | XCPro_Server account validation helper | `/api/v2/me/profile`, app-side local validation mirrors | server must be canonical for uniqueness and acceptance | app may mirror rules for UX, but server remains authority |
| Privacy enum values/defaults | shared account model files + server validators | app UI + server patch handlers | these values define future follow/live behavior | No undocumented duplicates |

### 2.2I Stateless Object / Singleton Boundary

No new Kotlin `object` state owner is planned. DI singletons remain repository/store/helper instances with explicit ownership.

### 2.3 Time Base

| Value | Time Base (Monotonic / Replay / Wall) | Why |
|---|---|---|
| session/token persistence timestamps | Wall | account/auth state is UX/network state, not replay-sensitive |
| server `created_at` / `updated_at` | Wall | persistence metadata only |

Explicitly forbidden comparisons:

- Monotonic vs wall
- Replay vs wall

### 2.4 Threading and Cadence

- Dispatcher ownership (`Main` / `Default` / `IO`):
  - UI rendering on `Main`
  - repository orchestration on injected `Default`
  - network/storage on injected `IO`
- Primary cadence/gating sensor:
  - none; account flow is request/response
- Hot-path latency budget:
  - not a hot-path runtime feature

### 2.4A Logging and Observability Contract

No new production logging path is planned beyond existing error propagation.

### 2.5 Replay Determinism

- Deterministic for same input: Yes
- Randomness used: No
- Replay/live divergence rules:
  - account flow is outside replay/fusion logic and must not affect replay determinism

### 2.5A Error and Degraded-State Contract

| Condition | Category (Recoverable / Degraded / Unavailable / Terminal / User Action) | Owner | User-Visible Behavior | Retry / Fallback Policy | Test Coverage |
|---|---|---|---|---|---|
| missing/invalid bearer | User Action | server auth seam + account repo | sign-out/re-auth prompt | clear local session on 401 | API + repository tests |
| handle already taken | Recoverable | server validation + viewmodel | inline error on save | user edits handle and retries | API + viewmodel tests |
| provider setup missing in build | Unavailable | auth provider seam | disabled method or explicit message | external provider setup required | provider/viewmodel tests |
| network/bootstrap failure | Recoverable | account repo | retryable error on manage-account screen | keep signed-in session, allow retry | repository/viewmodel tests |

### 2.5B Identity and Model Creation Strategy

| Entity / Value | Created By | ID / Time Source | Deterministic Required? | Why This Boundary Owns Creation |
|---|---|---|---|---|
| server `users.id` | server auth resolution | UUID4 at server boundary | No | server owns stable backend identity |
| server profile/privacy timestamps | server patch/bootstrap handlers | wall-clock `utcnow()` | No | persistence owner boundary |
| app bearer session snapshot | auth provider result | external bearer token string | Yes for same token | provider/auth seam owns session acquisition |

### 2.5C No-Op / Test Wiring Contract

| Class / Boundary | NoOp / Convenience Path | Production Allowed? | Safe Degraded Behavior | Visibility / Guardrail |
|---|---|---|---|---|
| app auth provider seam | explicit unsupported-provider result when no real provider config exists | Yes, explicit only | user sees sign-in unavailable/config required, never silently signs in | no hidden fallback auth |
| server auth resolver seam | explicit invalid/unauthenticated result when no configured resolver accepts token | Yes | `/api/v2/*` returns `401` | no silent anonymous fallback |

### 2.6 Enforcement Coverage

| Risk | Rule Reference | Guard Type (lint/enforceRules/test/review) | File/Test |
|---|---|---|---|
| cloud account logic drifts into `feature:profile` | ownership matrix / dependency direction | review + file ownership plan | this plan + code review |
| `/api/v1/live/*` regression | LiveFollow preservation rule | server regression tests | `XCPro_Server/app/tests/test_livefollow_api.py` |
| invalid handle/privacy accepted | validation rules | API tests | `XCPro_Server/app/tests/test_livefollow_api.py` |
| app auth/account SSOT duplicated in UI | MVVM/UDF/SSOT rules | repository/viewmodel tests + review | new account tests |
| raw bearer auth optional on `/api/v2/*` | auth boundary rule | API tests | `XCPro_Server/app/tests/test_livefollow_api.py` |

### 2.7 Visual UX SLO Contract

Not applicable. No map/overlay/replay interaction changes are planned in Phase 1.

## 3) Data Flow (Before -> After)

Before:

```
Public LiveFollow UI -> LiveFollow gateway -> /api/v1/live/* and write-token endpoints
```

After:

```
ManageAccount UI
  -> XcAccountViewModel
  -> XcAccountUseCase
  -> XcAccountRepository (SSOT)
     -> auth provider seam + encrypted session store
     -> account API data source
        -> Authorization: Bearer <token>
        -> XCPro_Server /api/v2/me*
           -> auth identity resolver
           -> users / auth_identities / pilot_profiles / privacy_settings
```

Public LiveFollow path remains:

```
LiveFollow pilot/watch -> current gateways -> /api/v1/*
```

## 4) Implementation Phases

- Phase 0
  - Goal: finish audit, write plan, confirm reference patterns, preserve dirty-worktree changes
  - Files to change: change plan doc only
  - Tests to add/update: none
  - Exit criteria: file ownership and auth seam explicit
- Phase 1
  - Goal: server auth seam + schema + `/api/v2/me*`
  - Files to change: `XCPro_Server/app/main.py`, migration, server tests
  - Tests to add/update: auth-required + profile/privacy validation + v1 regression
  - Exit criteria: bearer-protected `/api/v2/me*` works and auto-creates stable user rows
- Phase 2
  - Goal: app account SSOT + network/session/auth seam
  - Files to change: new `feature:livefollow.account` files + DI/build config
  - Tests to add/update: repository/viewmodel tests for signed-out/signed-in/onboarding/edit flows
  - Exit criteria: manage-account route can bootstrap session and round-trip profile/privacy
- Phase 3
  - Goal: route/UI integration and docs sync
  - Files to change: `AppNavGraph.kt`, docs as needed
  - Tests to add/update: route/VM coverage if needed
  - Exit criteria: route reachable, docs accurately describe repo implementation and external provider gap
- Phase 4
  - Goal: verification hardening
  - Files to change: only if fixes needed
  - Tests to add/update: targeted fixes only
  - Exit criteria: required gates pass

## 5) Test Plan

- Unit tests:
  - app account repository/viewmodel state transitions
  - account payload parsing/validation helpers where useful
- Replay/regression tests:
  - none; feature is outside replay path
- UI/instrumentation tests (if needed):
  - not planned initially; prefer JVM tests for VM/repository and keep UI thin
- Degraded/failure-mode tests:
  - missing auth
  - invalid bearer
  - handle taken
  - invalid privacy enum
  - bootstrap 401 clears session
- Boundary tests for removed bypasses:
  - manage-account route uses new account state owner rather than local aircraft profile owner
- Change-type coverage matrix:

| Change Type | Required Proof | Planned Evidence |
|---|---|---|
| Business rule / math / policy | Unit tests + regression cases | handle/privacy validation tests |
| Time-base / replay / cadence | Fake clock + deterministic repeat-run tests | not applicable beyond normal wall-time metadata |
| Persistence / settings / restore | Round-trip / restore / migration tests | session store tests + server API tests |
| Ownership move / bypass removal / API boundary | Boundary lock tests | account route/viewmodel tests + `/api/v2/me*` tests |
| UI interaction / lifecycle | UI or instrumentation coverage | thin UI; VM tests first |
| Performance-sensitive path | Benchmark, metric, or SLO artifact | not applicable |

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
| repo lacks real provider setup | sign-in buttons cannot be fully production-backed in-repo | keep provider exchange behind explicit auth seam; report remaining external setup clearly; add no silent fallback | Codex |
| dirty worktree on target files | accidental overwrite of user changes | inspect diffs first and patch minimally | Codex |
| public LiveFollow regression | high | leave `/api/v1/*` untouched and keep existing regression suite | Codex |
| account logic mixed with local profile logic | medium | keep new SSOT in `feature:livefollow.account` and reuse route only | Codex |

## 6A) ADR / Durable Decision Record

- ADR required: No
- ADR file:
- Decision summary:
  Phase 1 keeps module boundaries intact by placing private-follow account state in `feature:livefollow` while reusing the existing `manage_account` route shell from `app`.
- Why this belongs in an ADR instead of plan notes:
  No durable module-boundary change beyond this feature slice is planned yet.

## 7) Acceptance Gates

- No rule violations from `ARCHITECTURE.md` and `CODING_RULES.md`
- No duplicate SSOT ownership introduced
- Time base handling is explicit in code and tests
- Replay behavior remains deterministic
- Error/degraded-state behavior is explicit and tested where behavior changed
- Ownership/boundary/public API decisions are captured in an ADR when required
- `KNOWN_DEVIATIONS.md` updated only if explicitly approved

## 8) Rollback Plan

- What can be reverted independently:
  - app account UI and repository
  - server `/api/v2/me*` lane
  - migration can be rolled back independently from `/api/v1` behavior
- Recovery steps if regression is detected:
  - revert `/api/v2` account lane and app manage-account routing changes
  - keep current public `/api/v1/live/*` lane intact throughout rollback
