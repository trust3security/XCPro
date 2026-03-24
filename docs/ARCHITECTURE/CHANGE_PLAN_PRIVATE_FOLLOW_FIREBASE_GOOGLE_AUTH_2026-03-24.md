## Purpose

Implement the next private-follow auth slice:

- real Firebase-backed Google sign-in in XCPro
- Firebase ID token verification in XCPro_Server
- keep the existing Phase 1 `/api/v2/me*` contract intact
- keep the configured dev-token seam available for local fallback
- do not add search/follow/live-entitlement behavior yet

Read first:

1. `ARCHITECTURE.md`
2. `CODING_RULES.md`
3. `PIPELINE.md`
4. `CODEBASE_CONTEXT_AND_INTENT.md`

## 0) Metadata

- Title: Private Follow Firebase Google Auth
- Owner: Codex / XCPro
- Date: 2026-03-24
- Issue/PR: Private-follow auth productionization slice 1
- Status: In progress

## 1) Scope

- Problem statement:
  Phase 1 added the account/profile/privacy application seam, but sign-in is still backed only by a configured dev bearer token.
- Why now:
  The private-follow lane needs a real identity provider before profile/privacy state can be exercised as a production user flow.
- In scope:
  - Firebase Google sign-in in `feature:livefollow`
  - Firebase-backed bearer refresh for restored Google sessions
  - conditional `google-services` plugin wiring in `:app`
  - Firebase ID token verification on the server
  - explicit setup notes for repo-external Firebase prerequisites
- Out of scope:
  - email-link sign-in
  - follow/search/live ACL behavior
  - replacing the existing dev-token seam
  - changing public `/api/v1/live/*`
- User-visible impact:
  - builds with valid Firebase config can use `Continue with Google`
  - builds without Firebase config continue to show Google sign-in as unavailable
- Rule class touched: Invariant

## 2) Architecture Contract

### 2.1 SSOT Ownership

| Data | Owner | Exposed As | Forbidden Duplicates |
|---|---|---|---|
| XCPro signed-in account state | `XcAccountRepository` | `StateFlow<XcAccountSnapshot>` | screen-owned auth truth |
| Firebase Google session refresh | `XcGoogleAuthGateway` | repository dependency | UI-owned token refresh logic |
| Interactive Google ID token pickup | `ManageAccount` UI helper | one-shot callback into viewmodel | repository/activity-owned credential UI |
| Firebase bearer verification on server | `XCPro_Server/app/main.py` auth resolver | `/api/v2/*` auth gate | endpoint-local token parsing |

### 2.1A State Contract

| State Item | Authoritative Owner | Allowed Mutators | Read Path | Derived From | Persistence Owner | Reset/Clear Conditions | Time Base | Required Tests |
|---|---|---|---|---|---|---|---|---|
| Persisted XCPro account session | `XcAccountRepository` + `XcAccountSessionStore` | sign-in success, sign-out, restore refresh | use case -> viewmodel -> UI | Firebase ID token or dev token | encrypted shared prefs | sign-out, invalid session, unavailable provider | Wall | repository tests |
| Firebase signed-in user | FirebaseAuth | provider sign-in/sign-out only | `XcGoogleAuthGateway` | Google credential + FirebaseAuth | Firebase SDK internal store | explicit sign-out or provider invalidation | Wall | gateway/repository tests |
| Server current user identity | server auth resolver | verified Firebase token or static token | `/api/v2/me*` | bearer token | PostgreSQL | never anonymous in v2 | Wall | API tests |

### 2.2 Dependency Direction

`UI -> domain -> data`

- Modules/files touched:
  - `feature:livefollow` account/auth files
  - `feature:livefollow` DI
  - `app` Gradle/config only
  - `XCPro_Server/app/*`
- Boundary risk:
  - interactive credential pickup must stay UI-edge only
  - Firebase bearer refresh must stay out of Composables

### 2.2A Reference Pattern Check

| Reference File | Why It Is Similar | Pattern To Reuse | Planned Deviation |
|---|---|---|---|
| `feature/weglide/.../WeGlideAuthManager.kt` | provider-backed app auth seam | provider logic behind focused auth boundary | Firebase Google uses Credential Manager first, then FirebaseAuth |
| `feature/weglide/.../WeGlideTokenStoreImpl.kt` | secure session persistence | encrypted prefs + fallback | Google sessions refresh through FirebaseAuth before backend calls |
| `feature/livefollow/.../CurrentApiActivePilotsDataSource.kt` | existing LiveFollow network owner | focused transport layer, repository owns orchestration | `/api/v2/me*` requires bearer refresh before calls |

### 2.2D File Ownership Plan

| File | New / Existing | Owner / Responsibility | Why Here | Why Not Another Layer/File | Split Needed? |
|---|---|---|---|---|---|
| `docs/ARCHITECTURE/CHANGE_PLAN_PRIVATE_FOLLOW_FIREBASE_GOOGLE_AUTH_2026-03-24.md` | New | plan/ownership contract | required non-trivial plan | not product docs | No |
| `feature/livefollow/.../XcFirebaseGoogleAuthConfig.kt` | New | stateless Firebase config lookup helpers | shared by UI helper + gateway | avoids hidden singleton state | No |
| `feature/livefollow/.../XcGoogleAuthGateway.kt` | New | FirebaseAuth-backed Google session owner | data/auth boundary | not UI or repository-owned logic | No |
| `feature/livefollow/.../XcGoogleIdTokenRequester.kt` | New | Credential Manager UI-edge helper | activity-context interaction owner | repository must not own credential UI | No |
| `feature/livefollow/.../XcAccountRepository.kt` | Existing | account SSOT + Google session refresh orchestration | authoritative account owner | not UI-owned | No |
| `feature/livefollow/.../ManageAccountScreen.kt` | Existing | UI event routing only | composable host already owns button handling | not repository-owned | No |
| `feature/livefollow/.../di/LiveFollowAccountModule.kt` | Existing | account/auth DI wiring | feature DI owner | not `app` | No |
| `gradle/libs.versions.toml` | Existing | dependency/plugin versions | canonical version catalog | not module-local literals | No |
| `app/build.gradle.kts` | Existing | conditional `google-services` application + Firebase BoM | app owns application plugin wiring | feature cannot apply app plugin | No |
| `app/src/main/res/xml/*.xml` | Existing | backup exclusion for account prefs | app owns backup policy | not feature-owned | No |
| `XCPro_Server/app/main.py` | Existing | Firebase token verification seam | existing API/auth owner | avoids endpoint-local verifier duplication | No |
| `XCPro_Server/app/tests/test_livefollow_api.py` | Existing | server auth regression coverage | existing API suite owner | keep `/api/v1` regression coverage adjacent | No |

## 3) Data Flow (Before -> After)

Before:

```
ManageAccount UI -> XcAccountRepository -> configured dev bearer token -> /api/v2/me*
```

After:

```
ManageAccount UI
  -> Credential Manager Google ID token request
  -> XcAccountViewModel
  -> XcAccountUseCase
  -> XcAccountRepository (SSOT)
     -> XcGoogleAuthGateway
        -> FirebaseAuth sign-in / token refresh
     -> CurrentApiXcAccountDataSource
        -> Authorization: Bearer <Firebase ID token>
        -> XCPro_Server bearer resolver
           -> static dev token map OR verified Firebase ID token
           -> /api/v2/me*
```

## 4) Test Plan

- Unit tests:
  - repository restore/sign-in/sign-out behavior for Google-backed sessions
  - viewmodel status propagation for Google sign-in failures
- Degraded/failure-mode tests:
  - missing Firebase config
  - restore without Firebase current user
  - server Firebase verifier failure
- Required checks:

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

## 5) Risks and Mitigations

| Risk | Impact | Mitigation | Owner |
|---|---|---|---|
| missing `google-services.json` in local/dev builds | Google sign-in unavailable | conditional plugin apply + explicit capability note | Codex |
| Firebase ID token expiry | session restore breaks after app restart | refresh through FirebaseAuth before backend calls | Codex |
| server env missing Firebase credentials | backend rejects Google bearer | keep explicit static-token seam and document required env setup | Codex |
| debug package mismatch in Firebase console | Google sign-in fails only on debug builds | document debug `applicationIdSuffix` requirement | Codex |
