# XCPro Private Follow — Ops Ready Staging Operator Pack
Use this pack for the staging pass.

## Use only these release candidates
- Server: `b696f039540480468195087fa3f44338338f6fba`
- App: `c25d0f6520686643c2670502048fee3a83b91ca9`

## Current operator docs in this pack
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Staging_Execution_Brief_2026-03-24.md`
- `docs/LIVEFOLLOW/Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`
- `docs/LIVEFOLLOW/Private_Follow_Current_Repo_State_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Staging_Operator_Inputs_Worksheet_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Staging_Smoke_Results_Template_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Operator_Handoff_Message_2026-03-24.md`

## Operational goal
- set staging env
- deploy the pinned server RC to staging
- run env preflight
- prove real-device Google sign-in and `/api/v2/auth/google/exchange`
- run the staging smoke matrix
- make a go / no-go call

## Hard rule
Do not make code changes unless staging exposes a real blocker bug.
If a blocker bug is found, stop the rollout pass and create a narrow bug-fix brief for that exact issue.
