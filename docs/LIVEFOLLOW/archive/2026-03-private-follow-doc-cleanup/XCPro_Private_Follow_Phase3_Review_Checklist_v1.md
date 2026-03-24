# XCPro Private Follow — Phase 3 Review Checklist v1
## Pass/fail gate for follower-only live entitlement

**Purpose:** Use this checklist to review whether private-follow Phase 3 was implemented correctly.  
**Scope:** Follower-only live entitlement, session visibility, authenticated live discovery/read flow, public-lane gating, and required doc cleanup.  
**Not the owner:** This checklist is a gate, not the product brief or API proposal owner.

## Pass/fail rule
Approve Phase 3 only if all required sections below pass.

Reject Phase 3 if it breaks the public LiveFollow lane, leaks non-public sessions into the public lane, or quietly expands into unrelated notification/social work.

---

## A. Scope discipline
### Pass if
- Phase 3 implemented follower-only live entitlement
- session visibility was added
- authenticated live discovery/read was added
- required doc updates/archives were done
- no push notifications were added unless explicitly requested
- no block/report/mute system was added unless explicitly requested
- no large spectator-UI redesign was introduced

### Fail if
- the change sprawled into unrelated social features
- notifications or block/report/mute were added without approval
- the public spectator MVP was unnecessarily redesigned

---

## B. Server session ownership and visibility
### Pass if
- live sessions now have durable ownership for the private-follow lane
- live sessions now have explicit visibility:
  - `off`
  - `followers`
  - `public`
- the ownership/visibility model works with the current live-session storage
- compatibility with the existing public lane was preserved

### Fail if
- ownership is inferred unreliably from the client
- visibility is not persisted or is inconsistently derived
- private-follow sessions cannot be distinguished safely from public sessions

---

## C. Authenticated live-session start
### Pass if
- `POST /api/v2/live/session/start` exists
- it requires bearer auth
- it returns:
  - `session_id`
  - `status`
  - `visibility`
  - `write_token`
  - `share_code`
- `share_code` is `null` for `followers` and `off`
- `share_code` exists only for `public`
- omitted visibility defaults correctly from profile settings

### Fail if
- authenticated start is missing
- auth is optional
- returned visibility/share-code behavior is wrong
- the app cannot use the returned write token with the existing uploader path

---

## D. Visibility update behavior
### Pass if
- `PATCH /api/v2/live/session/{session_id}/visibility` exists
- owner-only authorization is enforced
- switching to `public` enables public visibility
- switching from `public` to `followers` or `off` revokes public visibility immediately
- switching to `off` blocks follower reads

### Fail if
- non-owners can change visibility
- public visibility lingers after switching away
- visibility changes are not reflected in reads/discovery

---

## E. Authenticated viewer discovery and reads
### Pass if
- `GET /api/v2/live/following/active` exists and is auth-protected
- it lists only sessions the caller is authorized to view
- `GET /api/v2/live/users/{user_id}` exists and is auth-protected
- `GET /api/v2/live/session/{session_id}` exists and is auth-protected
- owner can always read own session
- approved follower can read a followers-only session
- pending request does not authorize read
- unrelated signed-in user cannot read a followers-only session
- `off` visibility is owner-only

### Fail if
- following-live discovery leaks unauthorized sessions
- authenticated reads return follower-only sessions to the wrong viewer
- the owner cannot reliably read their own session

---

## F. Public-lane gating
### Pass if
Public sessions still behave correctly through:
- `GET /api/v1/live/active`
- `GET /api/v1/live/{session_id}`
- `GET /api/v1/live/share/{share_code}`

And non-public sessions:
- do not appear in `GET /api/v1/live/active`
- are not readable through public live-read endpoints

### Pass if public sessions still work
- current public active list still works for public sessions
- current public watch flow still works for public sessions

### Fail if
- follower-only/off sessions leak into public discovery or watch
- public sessions accidentally disappear or break
- public watch now requires sign-in

---

## G. Current write flow compatibility
### Pass if
- current write endpoints still work:
  - `POST /api/v1/position`
  - `POST /api/v1/task/upsert`
  - `POST /api/v1/session/end`
- authenticated start returns what the app needs to keep using that path
- there is no hidden write-path regression

### Fail if
- write flow broke
- write flow changed materially without being clearly called out
- the app cannot continue uploading positions/tasks/ending the session after v2 start

---

## H. Pilot-side app UX
### Pass if
- a signed-in pilot can start live with:
  - `Off`
  - `Followers`
  - `Public`
- current session visibility is visible somewhere sensible
- the pilot can change visibility while live
- the UI change is small and durable

### Fail if
- pilots cannot choose visibility
- pilots cannot change visibility during a live session
- the UI redesign is much larger than needed for the phase

---

## I. Viewer-side app UX
### Pass if
- signed-in viewers have a usable `Following Live` / `Live Now` path
- they can see followed pilots who are live and visible to them
- tapping a row opens the watch flow
- signed-out behavior is sensible and not broken
- unauthorized watch failure is handled cleanly

### Fail if
- there is no usable path for viewers to watch followed live pilots
- the app shows broken private-follow UI when signed out
- watch routing is incomplete

---

## J. Meaning of `Allow all XCPro followers`
### Pass if
- `auto_approve` / `Allow all XCPro followers` only affects follow approval
- it does not accidentally mean anonymous public visibility
- `Public` remains a separate session visibility choice

### Fail if
- `Allow all XCPro followers` was conflated with public live
- follow policy and live visibility are mixed together incorrectly

---

## K. Tests
### Pass if
- server tests cover start/update/read/discovery authorization and public-lane gating
- app tests cover visibility selection and following-live watch flow
- repo-native verification commands were actually run
- the report lists the exact commands run

### Fail if
- tests do not cover entitlement failures
- tests do not cover public-lane non-regression
- the report claims tests passed without listing commands

---

## L. Docs and archives
### Pass if
- the current private-follow repo-state doc was updated or version-bumped
- `README_current.md` was updated if the current doc canon changed
- newly implemented repo behavior is documented without being mislabeled as deployed
- completed phase-specific execution docs were archived
- the report names the exact archive folder created
- the report names the exact `.md` files updated and archived

### Fail if
- current docs are now more confusing than before
- repo-only behavior is presented as deployed reality
- completed phase docs remain active when they are no longer owners
- `.md` updates/archives are omitted from the report

---

## M. Required implementation report
### Pass if the report clearly states
- exact schema/model changes
- exact `/api/v2/live/*` endpoints implemented
- exact public-lane gating behavior
- exact app flows added/updated
- whether the write flow changed
- exact `.md` files updated
- exact `.md` files archived
- exact tests run
- what remains intentionally deferred

### Fail if
- the report is vague
- important gaps are hidden
- doc/archive work is not listed explicitly

---

## Final decision
### Approve if
- follower-only live entitlement works end to end
- public LiveFollow still works for public sessions
- non-public sessions do not leak publicly
- docs and archives are in order
- scope stayed disciplined

### Reject if
- public/non-public visibility boundaries are broken
- unauthorized viewers can read followers-only sessions
- docs are not updated cleanly
- scope drifted without control
