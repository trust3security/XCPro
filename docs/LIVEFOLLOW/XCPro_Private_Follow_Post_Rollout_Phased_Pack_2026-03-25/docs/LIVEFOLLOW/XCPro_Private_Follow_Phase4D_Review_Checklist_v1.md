# XCPro Private Follow — Phase 4D Review Checklist v1
## Pass/fail gate for push token foundation and notifications

**Date:** 2026-03-25  
**Purpose:** Review whether Phase 4D was implemented correctly.  
**Scope:** Push token registration/revocation and the first notification set.

## Pass/fail rule
Approve Phase 4D only if all required sections below pass.

Reject Phase 4D if it quietly expands into watcher counts, email discovery, or public-watch sign-in changes.

---

## A. Scope discipline
### Pass if
- push token lifecycle was added
- follow-request and follow-accepted notifications were added
- live-now notifications were either explicitly approved and implemented safely or clearly deferred
- watcher counts were not added
- email discovery was not added
- public-watch sign-in rules were not changed

### Fail if
- scope drift adds watcher counts or broader privacy/auth changes
- live-now is added without explicit decision and safe routing

---

## B. Push token lifecycle
### Pass if
- token registration exists
- token refresh/update is handled
- token revoke/cleanup on sign-out is handled sensibly
- tokens stay bound to the correct authenticated user

### Fail if
- tokens are orphaned or duplicated incorrectly
- sign-out leaves an obviously unsafe stale registration path

---

## C. Notification delivery
### Pass if
- follow-request notifications are delivered/enqueued correctly
- follow-accepted notifications are delivered/enqueued correctly
- blocked/invalid targets do not receive notifications
- notification sends are not obviously duplicated or noisy

### Fail if
- notifications go to the wrong users
- blocked relationships can still trigger sends
- delivery semantics are ambiguous

---

## D. Deep links and app behavior
### Pass if
- tapping a follow-request notification routes to Requests
- tapping a follow-accepted notification routes to a sensible surface
- live-now routing works only if that scope was explicitly included

### Fail if
- notification taps dead-end
- deep links open the wrong surface
- unauthorized live watch is reachable from a notification tap

---

## E. Non-regression
### Pass if
- public `/api/v1/live/*` remains unaffected
- existing private-follow behavior remains unaffected
- current live watch flows still work

### Fail if
- notifications regress sign-in, live watch, or list/relationship behavior

---

## F. Tests and docs
### Pass if
- server tests cover token lifecycle + notification triggers
- app tests cover token handling + deep-link routing
- docs were updated honestly
- exact verification commands are reported

### Fail if
- token/notification behavior is largely untested
- docs ignore new external/provider requirements

---

## Final decision
### Approve if
- token lifecycle is correct
- notification delivery is correct
- deep links are correct
- no watcher-count or auth/privacy scope drift occurred
- tests/docs are in order

### Reject if
- notifications are noisy, wrong, or unsafe
- deep links are broken
- scope drift occurred
