## Purpose

Replace the private-follow Firebase-based Google auth seam with the approved
Phase 1 auth path:

- Android Credential Manager obtains a Google ID token
- XCPro app exchanges that token with XCPro-Server
- XCPro-Server verifies Google identity and issues the XCPro bearer used by `/api/v2/*`

Private-follow relationship/search behavior stays unchanged.

## 0) Metadata

- Title: Private Follow Google Server Auth
- Owner: Codex / XCPro
- Date: 2026-03-24
- Issue/PR: Phase 1 auth decision refactor
- Status: In progress

## 1) Scope

- In scope:
  - server `POST /api/v2/auth/google/exchange`
  - server-issued bearer verification for authenticated `/api/v2/*`
  - app Google sign-in gateway/config refactor away from Firebase
  - docs/setup updates for non-Firebase Google auth
- Out of scope:
  - email-link auth
  - FCM/push
  - changes to Phase 2 relationship behavior
  - public `/api/v1/live/*`

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| Stored XCPro bearer session | `XcAccountSessionStore` | `XcAccountSnapshot.session` | duplicate auth-state owners |
| Google ID token verification result | XCPro_Server auth exchange | `/api/v2/auth/google/exchange` | client-side provider session as authoritative XCPro bearer |
| Authenticated current-user account/profile/privacy | `XcAccountRepository` + XCPro_Server account tables | `/api/v2/me*` | UI-owned auth truth |

### 2.2 Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/livefollow/.../XcGoogleIdTokenRequester.kt` | already owns Credential Manager Google ID token request | keep Credential Manager request flow | config source changes from Firebase resource to XCPro build config |
| `feature/livefollow/.../XcAccountRepository.kt` | already owns signed-in session restore/sign-out | keep repository/session-store ownership | Google restore no longer refreshes through Firebase |
| `XCPro_Server/app/main.py` | already owns `/api/v2` auth/account bearer resolution | keep centralized bearer parsing/validation | replace Firebase verifier with XCPro-issued bearer verifier + Google exchange |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/ARCHITECTURE/CHANGE_PLAN_PRIVATE_FOLLOW_GOOGLE_SERVER_AUTH_2026-03-24.md` | New | auth-refactor ownership contract | required non-trivial plan | not product docs | No |
| `feature/livefollow/.../XcGoogleSignInConfig.kt` | New | Google client-id config for Credential Manager | auth config owner | not repository/UI | No |
| `feature/livefollow/.../XcGoogleAuthGateway.kt` | Existing | server-backed Google exchange gateway | existing auth gateway owner | avoid new auth service locator | No |
| `feature/livefollow/.../XcGoogleIdTokenRequester.kt` | Existing | Credential Manager Google ID token request | current request owner | not gateway/network owner | No |
| `feature/livefollow/.../XcAccountRemoteDataSource.kt` | Existing | `/api/v2` account/auth HTTP calls | existing transport owner | keep HTTP logic out of gateway | No |
| `feature/livefollow/.../XcAccountPayloads.kt` | Existing | shared API error parsing | existing payload owner | reused by auth exchange parser | No |
| `feature/livefollow/.../XcRelationship*` | Existing | unchanged relationship transport/state | preserve Phase 2 ownership | not part of auth refactor | No |
| `feature/livefollow/build.gradle.kts` | Existing | library auth dependency/build config wiring | current feature config owner | not app UI | No |
| `app/build.gradle.kts` | Existing | app-wide plugin/dependency cleanup | current app build owner | not feature module | No |
| `gradle/libs.versions.toml` | Existing | shared dependency catalog cleanup | current dependency owner | not module-local | No |
| `XCPro_Server/app/main.py` | Existing | Google token verification, XCPro bearer issue/verify, exchange endpoint | current server auth owner | avoid parallel server module for one slice | No |
| `XCPro_Server/app/requirements.txt` | Existing | server auth verification dependency | existing runtime deps owner | not tests/docs | No |
| `XCPro_Server/app/tests/test_livefollow_api.py` | Existing | server auth/account regression tests | current API suite owner | keep auth + relationship coverage together | No |

## 3) Data Flow

```
Credential Manager
  -> Google ID token
  -> XCPro app gateway
  -> POST /api/v2/auth/google/exchange
  -> XCPro_Server verifies Google token
  -> XCPro_Server upserts account primitives
  -> XCPro_Server issues XCPro bearer
  -> XcAccountSessionStore
  -> XcAccountRepository
  -> /api/v2/me* and relationship endpoints
```

## 4) Test Plan

- Server:
  - valid Google exchange returns XCPro bearer
  - issued bearer works on `/api/v2/me`
  - invalid Google token rejected cleanly
- App:
  - Google exchange transport parsing
  - repository sign-in/restore with server-issued bearer
  - existing relationship/account state tests stay green
- Required checks:
  - targeted `feature:livefollow` account tests
  - `scripts/qa/run_change_verification.bat -Profile pr-ready`
  - `python -m unittest app.tests.test_livefollow_api`
