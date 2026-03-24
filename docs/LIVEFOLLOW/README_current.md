# LIVEFOLLOW current docs

Purpose: keep `docs/LIVEFOLLOW` easy to navigate by separating the current
public LiveFollow canon, the current private-follow operator docs, the longer
term private-follow proposal/reference set, and archived one-off material.

Any doc in this folder that is not listed below should be treated as supporting
context, a task brief, or historical material rather than a current owner.

## Public LiveFollow current canon

1. `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
   - current product/status summary for the public single-pilot spectator MVP
2. `LiveFollow_Current_Deployed_API_Contract_v3.md`
   - current deployed public app/server wire contract owner
3. `ServerInfo.md`
   - factual server provenance, deployment, and runtime-reality note
4. `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`
   - recurring audit checklist for public contract changes

The public deployed contract owner remains
`LiveFollow_Current_Deployed_API_Contract_v3.md`.

## Private-follow operational docs

1. `Private_Follow_Current_Repo_State_2026-03-24.md`
   - current owner for what private-follow behavior exists in repo now
2. `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
   - current environment and Google sign-in/token-exchange setup note
3. `XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
   - rollout sequencing, validation, rollback, and release-owner checklist
4. `XCPro_Private_Follow_Staging_Execution_Brief_2026-03-24.md`
   - current staging execution runbook
5. `XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`
   - current staging smoke execution guide

These are the current operator docs for the next step in private-follow
rollout execution.
`Private_Follow_Current_Repo_State_2026-03-24.md` owns repo reality for the
authenticated private-follow lane.
The staging execution brief is the current runbook.
The rollout checklist and staging smoke guide remain the detailed operator
owners.
They do not replace the public deployed contract owner.

## Private-follow operator support docs

1. `XCPro_Private_Follow_Staging_Operator_Inputs_Worksheet_2026-03-24.md`
   - worksheet for the real staging inputs, access, accounts, and deploy commands
2. `XCPro_Private_Follow_Staging_Smoke_Results_Template_2026-03-24.md`
   - fixed pass/fail template for staging execution evidence
3. `XCPro_Private_Follow_Operator_Handoff_Message_2026-03-24.md`
   - short handoff note for the staging owner or device operator
4. `XCPro_Private_Follow_Staging_Operator_Pack_OPS_READY_2026-03-24/`
   - current bundled operator pack for staging handoff

Use the worksheet, results template, and handoff note as operator support
material.
Use the `OPS_READY` pack when someone needs a single bundled staging handoff.
Do not use the older `FRESH` or `SYNCED` pack variants.

## Private-follow proposal/reference docs

1. `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
   - product and UX proposal/reference owner
2. `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
   - proposed authenticated API contract/reference owner
3. `XCPro_Private_Follow_Change_Plan_2026-03-23.md`
   - implementation history and future-change reference

These remain useful for future or not-yet-implemented work, but they are
proposal/reference docs rather than the immediate rollout operator set.

## Related architecture / refactor docs

- `docs/refactor/Private_Follow_Live_Entitlement_Phase3_Phased_IP_2026-03-24.md`
- `docs/ARCHITECTURE/ADR_PRIVATE_FOLLOW_LIVE_LANE_SPLIT_2026-03-24.md`

These are related background docs, not current LIVEFOLLOW owner docs.

## Archive

Historical and one-off LIVEFOLLOW docs live under:

- `docs/LIVEFOLLOW/archive/2026-03-single-pilot-spectator-mvp/`
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/`
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-rollout-hardening/`
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-staging-ops-iterations/`

Archived cleanup/remediation briefs are historical and should not be treated as
current owners.
Archived staging-ops pack iterations are historical and should not be used for
the current staging pass.

When the current LIVEFOLLOW owner set changes, update this file in the same
change.
