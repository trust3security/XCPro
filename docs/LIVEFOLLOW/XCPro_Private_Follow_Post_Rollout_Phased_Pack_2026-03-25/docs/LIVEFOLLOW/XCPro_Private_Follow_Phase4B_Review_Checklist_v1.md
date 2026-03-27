# XCPro Private Follow — Phase 4B Review Checklist v1
## Pass/fail gate for relationship management controls

**Date:** 2026-03-25  
**Purpose:** Review whether Phase 4B was implemented correctly.  
**Scope:** Cancel outgoing request, unfollow, remove follower, and relationship-state/count updates only.

## Pass/fail rule
Approve Phase 4B only if all required sections below pass.

Reject Phase 4B if it quietly expands into block/unblock, notifications, watcher counts, or public-lane changes.

---

## A. Scope discipline
### Pass if
- the phase adds only cancel request / unfollow / remove follower controls
- no block / unblock was added
- no push token or notification work was added
- no watcher-count work was added

### Fail if
- block/unblock work appears in this phase
- push or watcher-count work appears in this phase

---

## B. Relationship mutations
### Pass if
- outgoing pending request can be cancelled correctly
- approved relationship can be unfollowed correctly
- follower can be removed without blocking
- server authorization is correct for each mutation

### Fail if
- cancel/unfollow/remove mutate the wrong side
- unauthorized callers can perform mutations
- remove follower behaves like a block

---

## C. State and counts
### Pass if
- relationship states update correctly after each mutation
- follower/following counts update correctly
- pending requests are not counted as approved relationships

### Fail if
- counts remain stale or wrong after mutations
- row state becomes inconsistent with server truth

---

## D. App controls and UX
### Pass if
- outgoing request cancel is reachable and clear
- unfollow is reachable and clear
- remove follower is reachable and clear
- success/failure states are understandable

### Fail if
- actions are missing from the intended surfaces
- controls are ambiguous or too destructive without reason

---

## E. Non-regression
### Pass if
- public `/api/v1/live/*` is unaffected
- current follower-only live entitlement is unaffected
- Phase 4A follower/following list reads still work

### Fail if
- new controls regress list reads
- new controls regress live entitlement or public LiveFollow

---

## F. Tests and docs
### Pass if
- server tests cover cancel/unfollow/remove
- app tests cover user actions and resulting state updates
- docs were updated honestly
- exact verification commands are reported

### Fail if
- key mutation tests are missing
- docs remain stale about repo reality

---

## Final decision
### Approve if
- relationship controls work
- counts and states stay correct
- no block/notification scope drift occurred
- tests/docs are in order

### Reject if
- remove follower semantics are wrong
- counts are wrong
- scope drift occurred
- current live/public behavior regressed
