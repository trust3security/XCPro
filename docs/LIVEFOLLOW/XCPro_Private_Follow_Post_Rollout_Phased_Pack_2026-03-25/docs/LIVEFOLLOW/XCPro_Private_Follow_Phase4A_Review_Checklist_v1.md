# XCPro Private Follow — Phase 4A Review Checklist v1
## Pass/fail gate for follower/following lists, counts, and list visibility

**Date:** 2026-03-25  
**Purpose:** Review whether Phase 4A was implemented correctly.  
**Scope:** Followers/following list reads, counts, list screens, and follow-back entry path only.  
**Not the owner:** This checklist is a gate, not the implementation brief.

## Pass/fail rule
Approve Phase 4A only if all required sections below pass.

Reject Phase 4A if it quietly expands into relationship controls, blocks, notifications, watcher counts, or public-lane changes.

---

## A. Scope discipline
### Pass if
- the phase adds follower/following list reads and screens
- counts are surfaced where intended
- follow-back uses the existing follow-request flow
- no remove follower / unfollow / cancel request mutations were added unless explicitly approved
- no block / unblock work was added
- no push notifications or push token work was added
- no watcher counts were added

### Fail if
- the phase silently expands into Phase 4B, 4C, or 4D work
- public LiveFollow behavior changes as part of this phase

---

## B. Server list endpoints
### Pass if
- follower list read exists and works
- following list read exists and works
- the returned rows contain enough identity + relationship data for the app
- response shapes are consistent with existing repo patterns

### Fail if
- list reads are only partially wired
- app rows require extra ad-hoc calls to render basic identity/state
- endpoint behavior is ambiguous or undocumented

---

## C. Connection list visibility enforcement
### Pass if
- `owner_only` allows only the owner to read full lists
- `mutuals_only` allows owner and mutual viewers only
- `public` allows signed-in viewers according to policy
- unauthorized list reads are rejected clearly

### Fail if
- full lists leak to callers who should not see them
- `mutuals_only` is implemented incorrectly
- list visibility is ignored or only enforced in the UI

---

## D. Counts behavior
### Pass if
- follower and following counts are surfaced where intended
- counts stay separate from watcher/live-viewer concepts
- counts reflect approved relationships only

### Fail if
- watcher-count logic is mixed into follower/following counts
- counts include pending requests
- counts are stale or incorrect after basic actions

---

## E. App screens and states
### Pass if
- users can reach Followers and Following screens from the intended private-follow area
- loading, empty, and error states exist
- rows render relationship state clearly
- signed-out users do not hit broken private-follow screens

### Fail if
- screens dead-end or crash
- list rows are too incomplete to be useful
- signed-out behavior is broken

---

## F. Follow-back entry path
### Pass if
- `followed_by` rows give the user a clear follow-back path
- follow-back reuses the existing follow request flow cleanly
- resulting row state updates correctly (`following` or `mutual`, or `outgoing_pending` if policy requires)

### Fail if
- a new special-case follow-back server primitive was added unnecessarily
- follow-back state does not update correctly
- follow-back is impossible from the new surfaces

---

## G. Non-regression
### Pass if
- current public `/api/v1/live/*` behavior is unchanged
- current private-follow live entitlement remains correct
- existing follow request flows still work

### Fail if
- list work regresses public browse/watch
- list work regresses current follow request flows
- list work regresses live entitlement behavior

---

## H. Tests and verification
### Pass if
- server tests cover list reads + visibility rules
- app tests cover list screens + follow-back path
- repo-native verification commands were run
- the final report lists exact commands and results

### Fail if
- tests are missing for key visibility cases
- verification is claimed without exact commands

---

## I. Docs
### Pass if
- the current private-follow repo-state doc was updated
- docs still distinguish private-follow repo reality from public deployed-contract ownership

### Fail if
- docs remain stale about what is implemented
- docs blur repo-only behavior with public deployed reality

---

## Final decision
### Approve if
- followers/following lists and counts work
- visibility rules are correct
- follow-back path works
- no public or live-entitlement regression exists
- tests/docs are in order

### Reject if
- scope drift occurred
- visibility rules are wrong
- counts are wrong
- the phase regressed current private-follow or public LiveFollow behavior
