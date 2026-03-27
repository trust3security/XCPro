# XCPro Private Follow — Phase 4C Review Checklist v1
## Pass/fail gate for block / unblock and abuse-control hardening

**Date:** 2026-03-25  
**Purpose:** Review whether Phase 4C was implemented correctly.  
**Scope:** Block/unblock, search suppression, relationship cleanup, and follower-only live access removal.

## Pass/fail rule
Approve Phase 4C only if all required sections below pass.

Reject Phase 4C if it quietly expands into notifications, watcher counts, or unrelated privacy redesign.

---

## A. Scope discipline
### Pass if
- the phase adds block/unblock only
- notifications are still out of scope
- watcher counts are still out of scope
- public-watch sign-in rules are unchanged

### Fail if
- push/notification work appears in this phase
- watcher-count work appears in this phase
- public-lane auth/privacy changes appear here

---

## B. Block storage and endpoints
### Pass if
- block create exists and is idempotent
- unblock exists and is sane/idempotent according to repo conventions
- server authorization is correct

### Fail if
- block/unblock behavior is ambiguous
- repeated block actions are unsafe or inconsistent

---

## C. Relationship cleanup on block
### Pass if
- both follow directions are removed on block
- pending requests are removed on block
- unblock does not silently restore old state

### Fail if
- block leaves behind one side of the relationship
- pending requests survive block
- unblock resurrects old follows or requests

---

## D. Search and list suppression
### Pass if
- blocked users no longer see each other in search
- blocked users are not surfaced in follower/following lists where they should be hidden
- direct-path blocked handling is still coherent

### Fail if
- blocked users still appear in search results
- list suppression is missing or only implemented in UI

---

## E. Live access removal
### Pass if
- blocking immediately removes follower-only live entitlement between the pair
- blocked user can no longer discover/read follower-only sessions that depended on the relationship

### Fail if
- blocked users can still watch follower-only live flights
- access removal is delayed or inconsistent

---

## F. App UX
### Pass if
- block and unblock actions are reachable in sensible surfaces
- blocked-state UI is clear without oversharing
- search/list/profile behavior remains coherent after block

### Fail if
- block actions are buried or unusable
- blocked-state handling is confusing or punitive in tone

---

## G. Non-regression
### Pass if
- public `/api/v1/live/*` is unaffected
- non-blocked private-follow behavior is unaffected
- previous phase list/relationship controls still work

### Fail if
- block work regresses public or ordinary private-follow flows

---

## H. Tests and docs
### Pass if
- server tests cover block, unblock, relationship cleanup, search suppression, and live-access removal
- app tests cover block/unblock state handling
- docs were updated honestly
- exact verification commands are reported

### Fail if
- any of the high-risk block effects are untested
- docs remain stale about the safety boundary

---

## Final decision
### Approve if
- block/unblock is correct and immediate
- search/list/live access all honor block state
- no notification/watcher-count scope drift occurred
- tests/docs are in order

### Reject if
- block semantics are incomplete
- users can still see/watch each other incorrectly
- scope drift occurred
