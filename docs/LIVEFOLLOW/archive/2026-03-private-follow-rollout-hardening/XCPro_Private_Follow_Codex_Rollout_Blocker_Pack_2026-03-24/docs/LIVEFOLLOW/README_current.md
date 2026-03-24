# LIVEFOLLOW current docs

Purpose: keep `docs/LIVEFOLLOW` easy to navigate by naming the small set of
documents that are currently authoritative.

## Read These First

1. `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
   - current product/status summary for the single-pilot spectator MVP
   - names the next approved slice

2. `LiveFollow_Current_Deployed_API_Contract_v3.md`
   - current deployed app/server wire contract owner
   - includes `GET /api/v1/live/active`, optional `agl_meters`, and explicit task clear behavior

3. `ServerInfo.md`
   - factual server provenance, deployment, and runtime-reality note
   - not the product plan and not the contract owner

4. `Private_Follow_Current_Repo_State_2026-03-24.md`
   - current implemented repo state for the authenticated private-follow lane
   - keeps private-follow repo behavior separate from public/deployed LiveFollow status

## Keep Active But Secondary

- `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`
  - recurring audit checklist for future contract changes
  - kept active because it is still useful when the deployed wire contract changes again

- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
  - current setup note for the approved non-Firebase Google private-follow auth path

## What Is No Longer A Separate Current Owner

- The older standalone active-pilots contract has been merged into
  `LiveFollow_Current_Deployed_API_Contract_v3.md`.
- Historical slice plans, superseded plans, scaffolding notes, and the older
  deployed contract doc now live under:
  `docs/LIVEFOLLOW/archive/2026-03-single-pilot-spectator-mvp/`

## Archive Rule

Keep a LIVEFOLLOW doc active only if it is one of these:

- the current product/status summary
- the current deployed contract owner
- factual server provenance used by current work
- a recurring checklist still used for future contract changes

Archive a LIVEFOLLOW doc when it mainly records:

- a completed slice plan
- a superseded change plan
- execution scaffolding or review prompts
- a draft architecture note no longer needed as a current owner

## Update Rule

When the current LIVEFOLLOW canon changes, update this file in the same change.
