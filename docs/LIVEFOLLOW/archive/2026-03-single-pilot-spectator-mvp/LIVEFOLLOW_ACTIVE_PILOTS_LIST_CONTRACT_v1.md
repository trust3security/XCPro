# LiveFollow Active Pilots List Contract v1

Date: 2026-03-21
Status: Proposed contract for Friends Flying list slice

## Purpose

This contract defines the first backend-supported list of currently visible public pilots for the
Friends Flying bottom-sheet picker.

It is intentionally designed to support:
- listing active pilots
- selecting one pilot
- reusing the existing single-pilot watch flow

It is not yet a full friend-graph or privacy model.

---

## Endpoint

`GET /api/v1/live/active`

## Auth
Public for this first slice, matching the current public read model.

A future slice may add filtering/auth for real friend relationships.

---

## Response shape

```json
{
  "items": [
    {
      "session": "2e5ed62a-258c-4d06-a5b4-80f579c2f6ad",
      "share_code": "Z4WAA57N",
      "status": "active",
      "display_label": "DF0CBB",
      "last_position_at": "2026-03-21T00:20:05.256382+00:00",
      "latest": {
        "lat": -33.8943201,
        "lon": 151.26927693,
        "alt": 12.268176082996918,
        "speed": 0.0,
        "heading": 0.0,
        "timestamp": "2026-03-21T00:20:05+00:00"
      }
    }
  ],
  "generated_at": "2026-03-21T00:20:10.000000+00:00"
}
```

---

## Field meanings

### session
Internal session identifier.
Return it for internal debugging/compatibility if useful, but do not require the user to use it.

### share_code
The external/public watch token for this slice.
XCPro should use this to open the watch flow after the user selects an item.

### status
`active` or `stale`.
Do not include `ended` sessions by default.

### display_label
Human-readable label for the list row.

Preferred source:
- server-stored `public_label` from session start

Fallback:
- `share_code`

### last_position_at
Server receive time for the most recent accepted position.

### latest
Latest accepted public sample.
This is enough for:
- list preview
- bottom-sheet display
- optional quick preview on selection

---

## Sorting

Recommended default:
1. `active` before `stale`
2. newest `last_position_at` first

---

## Minimal server-side requirements

To make this useful, the server should support a minimal public label model.

Recommended additive start-session extension:
- optional `public_label` on session start
- stored with session
- exposed as `display_label` in this list endpoint

This is additive and should not break existing session-start behavior.

---

## XCPro app usage

The XCPro app should:
- fetch this endpoint
- show a bottom sheet list
- when a row is tapped, route into:
  - watch-by-share-code
- keep map rendering single-pilot only for this slice
