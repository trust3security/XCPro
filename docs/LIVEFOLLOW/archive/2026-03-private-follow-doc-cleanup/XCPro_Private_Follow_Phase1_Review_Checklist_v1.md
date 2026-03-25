# XCPro Private Follow — Phase 1 Review Checklist v1
## Pass/fail gate for accounts, profiles, and privacy foundation

**Purpose:** Use this checklist to review whether Phase 1 was implemented correctly.  
**Scope:** Accounts, profile onboarding/editing, privacy settings, and `/api/v2/me*` foundation only.  
**Not the owner:** This checklist is a gate, not the product brief or API proposal owner.

## Pass/fail rule
Approve Phase 1 only if all required sections below pass.

Reject Phase 1 if it quietly expands into Phase 2 follow/search/live-entitlement work, or if it breaks the current public LiveFollow lane.

---

## A. Scope discipline
### Pass if
- only Phase 1 foundation work was added
- no pilot search was shipped
- no follow / unfollow / request / block behavior was shipped
- no follower/following lists were shipped
- no follower-only live visibility gating was shipped

### Fail if
- Codex implemented Phase 2 or Phase 3 work without being asked
- public LiveFollow behavior was changed as part of this phase

---

## B. Auth/account foundation
### Pass if
- XCPro has a signed-in vs signed-out account state
- `/api/v2/*` requires bearer auth
- the server resolves a bearer token to a stable `user_id`
- invalid or missing auth returns `401`
- sign-out works cleanly in the app

### Fail if
- auth is faked in an insecure production-like way
- Phase 1 forces a new weak password flow when the repo did not previously support it
- the auth boundary is unclear or inconsistent

---

## C. Server schema and model
### Pass if
- the server has durable storage for:
  - users
  - auth identities
  - pilot profiles
  - privacy settings
- `handle` is unique and normalized
- `display_name` is stored cleanly
- privacy settings are persisted using explicit enums

### Fail if
- profile data is only stored locally in the app
- handle uniqueness is not enforced
- privacy data is missing or not persisted

---

## D. Required `/api/v2` endpoints
### Pass if
These endpoints exist and work:
- `GET /api/v2/me`
- `PATCH /api/v2/me/profile`
- `PATCH /api/v2/me/privacy`

### Pass if response/update behavior is correct
- current profile is returned for the signed-in user
- profile updates validate and persist
- privacy updates validate and persist
- machine-readable error envelopes are returned on failure

### Fail if
- endpoint behavior is only partially wired
- validation is missing
- auth is optional on `/api/v2/*`

---

## E. Validation and error behavior
### Pass if
- handle uniqueness is enforced case-insensitively
- invalid handles are rejected clearly
- invalid privacy enum values are rejected clearly
- validation failures return structured errors
- handle-taken behavior is surfaced clearly to the app

### Fail if
- the same handle can be claimed twice with case variation
- the app cannot distinguish handle-taken from generic failure
- server validation is missing or inconsistent

---

## F. App onboarding and profile UX
### Pass if
- first sign-in can complete profile onboarding
- onboarding requires valid required fields
- user can edit profile later
- user can edit privacy settings later
- signed-out state has a clear entry point to sign in
- auth failure sends the user to a sensible recovery path

### Fail if
- onboarding is incomplete or dead-ends
- profile edits do not round-trip to server state
- privacy UI exists but does not persist

---

## G. Privacy settings behavior
### Pass if
These settings can be read and updated end to end:
- discoverability
- follow policy
- default live visibility
- connection list visibility

### Pass if labels and stored meaning are clear
- `Allow all XCPro followers` maps cleanly to `auto_approve`
- `Public` live remains distinct from follow policy

### Fail if
- settings are overloaded or conflated
- `auto_approve` is incorrectly treated as anonymous public visibility

---

## H. Non-regression of current public LiveFollow
### Pass if
These remain intact and unchanged in behavior unless explicitly intended and documented:
- `POST /api/v1/session/start`
- `POST /api/v1/position`
- `POST /api/v1/task/upsert`
- `POST /api/v1/session/end`
- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`

### Fail if
- public watch now requires sign-in
- public active-list behavior changed unintentionally
- current public DTOs were repurposed for account identity without plan/doc changes

---

## I. Tests
### Pass if
- server tests cover auth + `/api/v2/me*` happy path and failures
- app tests cover onboarding/profile/privacy state flows
- repo-native test/build commands were actually run
- the report includes the exact commands run

### Fail if
- no tests were added for new server behavior
- no app tests were added for new state flows
- the report claims tests passed without listing commands

---

## J. Docs and ownership
### Pass if
- newly deployed Phase 1 behavior is documented somewhere current
- future proposal docs are not the only source of truth for newly deployed behavior
- `README.md` was updated if the LIVEFOLLOW canon changed
- current docs still keep deployed reality separate from future proposals

### Fail if
- future `/api/v2` proposal text is presented as current reality without implementation
- deployed behavior changed but current docs were not updated
- docs now mix product plan, server provenance, and deployed contract ownership into one confusing owner

---

## K. Required implementation report
### Pass if the report clearly states
- what auth/account infrastructure already existed
- what was added on server
- what was added in XCPro app
- which endpoints were implemented
- what external provider setup is still required, if any
- what docs changed
- what tests were run
- what remains intentionally deferred to Phase 2

### Fail if
- the report is vague
- important gaps are hidden
- deferred work is not called out explicitly

---

## Final decision
### Approve if
- all required Phase 1 foundation behavior works
- public LiveFollow is preserved
- docs and tests are in order
- scope stayed disciplined

### Reject if
- public LiveFollow regressed
- scope drifted into later phases without control
- auth/account/profile/privacy behavior is incomplete or only half-wired
