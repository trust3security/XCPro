# LiveFollow Current State and Next Slice

Date: 2026-03-23
Status: Current product/status summary for the single-pilot spectator MVP

## Purpose

This is the current product-status owner for LIVEFOLLOW.

Use it to answer:

- what now works
- what is still intentionally out of scope
- what the next approved slice is
- which LIVEFOLLOW docs are still canonical

This is not the wire-contract owner. The deployed contract owner lives in
`docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md`.

---

## What Now Works

The current single-pilot spectator MVP is implemented end to end:

- startup chooser for the LiveFollow entry path
- Flying mode with the compact status indicator
- Friends Flying as a spectator-only mode
- active pilots list from `GET /api/v1/live/active`
- single watched pilot handoff from the list into watch mode
- watched glider rendering on the map
- larger watched-glider icon
- camera centering on the watched pilot
- bottom telemetry strip
- AGL carried end to end on upload and watch reads
- watched task overlay rendering
- explicit task clear/remove path
- viewer task overlay disappearing cleanly after clear
- Friends Flying bottom sheet with an `Active` tab and search
- top watch card removed from the viewer experience
- false stale after task clear fixed so task state no longer drives liveness

---

## Current Intentional Limits

These are still intentionally out of scope:

- no multi-glider spectator mode
- no full pilot-screen clone for the viewer
- no vario / vertical-speed field in the spectator telemetry strip yet
- tab 2 is still a placeholder
- tabs 3 and 4 are still placeholders

Current UI caveats that are still true:

- the `Active` tab still shows both active pilots and a `Recently active` section
- Friends Flying remains map-first and read-only; no viewer-owned sensors or pilot controls are added
- task details are still represented mainly by the map overlay; there is not yet a dedicated read-only task tab

---

## Next Approved Slice

### Friends Flying Viewer UI v2 - Task tab

The next approved slice is to turn the placeholder second tab into a real
read-only `Task` tab for the watched pilot.

That slice should stay within the current spectator boundaries:

- keep single-pilot spectator mode
- keep the viewer sensor-free
- keep map-first layout
- do not turn Friends Flying into a full Flying-mode clone

---

## Canonical Docs Now

Use these as the active LIVEFOLLOW canon:

1. `docs/LIVEFOLLOW/README_current.md`
   - entrypoint for current LIVEFOLLOW docs
2. `docs/LIVEFOLLOW/LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
   - current product/status summary
3. `docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md`
   - deployed app/server wire contract owner
4. `docs/LIVEFOLLOW/ServerInfo.md`
   - factual server provenance and deployment notes
5. `docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`
   - recurring contract-audit checklist kept active for future wire changes

The older standalone active-pilots contract has been merged into the main
deployed contract and archived with the completed MVP slice docs.
