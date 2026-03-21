# LiveFollow Friends Flying Review Checklist v1

## Purpose

Use this checklist before approving the Friends Flying list slice.

This slice is successful only if:
- the server provides a useful active-pilot list
- XCPro presents it in a bottom sheet
- tapping one pilot reuses the existing single-pilot watch flow

---

## ✅ REQUIRED

### Server
- `GET /api/v1/live/active` exists
- returns active or stale public pilots only
- returns `share_code`
- returns a usable `display_label`
- returns latest sample and last_position_at
- has deterministic tests

### XCPro app
- bottom-sheet list exists
- list row uses `display_label`
- tapping a row routes into existing watch-by-share-code flow
- replay remains side-effect free
- map remains render-only
- no task ownership drift
- no second ownship pipeline

### UX
- user is not required to manually type raw session id
- list is understandable even for multiple active pilots
- stale pilots are visibly marked if shown

---

## ❌ FORBIDDEN

- multi-pilot map rendering in this slice
- friend-graph implementation in this slice
- WebSocket/push redesign
- moving session or watch ownership into UI
- map becoming the owner of active-pilot list truth
- exposing write_token in UI
- replacing share_code with raw session_id as the user-facing token

---

## APPROVE if
- server list endpoint is stable
- XCPro bottom-sheet flow works end-to-end
- one selected pilot hands off cleanly to existing single-watch flow
- tests are deterministic
- scope stays narrow

## REJECT if
- list has no usable display label
- selection does not hand off to the watch flow
- user still has to use session ids manually
- ownership drifts into map/UI
