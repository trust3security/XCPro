# LIVEFOLLOW Docs

Purpose: keep `docs/LIVEFOLLOW` easy to navigate by naming the small set of
documents that are currently authoritative for the public LiveFollow lane and
the private-follow lane.

Any doc in this folder that is not listed below should be treated as supporting
context or historical material rather than a current owner.

## Public LiveFollow current canon

1. `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
   - current product/status summary for the public single-pilot spectator MVP
2. `LiveFollow_Current_Deployed_API_Contract_v3.md`
   - current deployed public app/server wire contract owner
3. `ServerInfo.md`
   - factual server provenance, deployment, and runtime-reality note
4. `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`
   - recurring audit checklist for public contract changes

The deployed public contract owner remains
`LiveFollow_Current_Deployed_API_Contract_v3.md`.

## Private-follow current canon

1. `Private_Follow_Current_Repo_State_2026-03-24.md`
   - current owner for what private follow is implemented in the repo now
2. `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
   - product and UX proposal owner
3. `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
   - proposed authenticated API contract owner
4. `XCPro_Private_Follow_Change_Plan_2026-03-23.md`
   - multi-phase implementation/change-plan owner
5. `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
   - current setup note for Google sign-in, XCPro-Server token exchange, and
     required external environment setup

Private-follow proposal and plan docs do not automatically mean deployed
behavior.
`Private_Follow_Current_Repo_State_2026-03-24.md` remains the implemented-state
owner for what exists in this repo now.
The Google setup note stays active because it still owns unique external setup
requirements that are not duplicated elsewhere in the folder.

## Related architecture / refactor docs

- `docs/refactor/Private_Follow_Live_Entitlement_Phase3_Phased_IP_2026-03-24.md`
- `docs/ARCHITECTURE/ADR_PRIVATE_FOLLOW_LIVE_LANE_SPLIT_2026-03-24.md`

## Archive

Historical and superseded LIVEFOLLOW docs live under:

- `docs/LIVEFOLLOW/archive/2026-03-single-pilot-spectator-mvp/`
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/`

The private-follow cleanup archive holds:

- the redundant implementation brief
- the completed Phase 1 and Phase 3 execution briefs/checklists
- the now-historical cleanup brief used to reorganize the private-follow docs

When the current LIVEFOLLOW canon changes, update this file in the same change.
