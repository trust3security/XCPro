# XCPro Private Follow — Rollout Blocker Fixes

Work only on the current rollout blockers for private-follow. This is a release-hardening task, not a new-feature task.

## Context
Private-follow is already implemented in repo, but rollout is paused because:
- the server release candidate is not frozen to a clean commit
- fresh-db Alembic upgrade is not bootstrap-safe
- dev auth / dev bearer shortcuts are too close to release/prod paths
- real env/auth setup is still unverified
- staging/prod smoke tests have not been run yet

## Source-of-truth docs to follow
Use these docs as the task owners:
- docs/LIVEFOLLOW/archive/2026-03-private-follow-rollout-hardening/XCPro_Private_Follow_Rollout_Blocker_Remediation_Brief_2026-03-24.md
- docs/LIVEFOLLOW/archive/2026-03-private-follow-rollout-hardening/XCPro_Private_Follow_Rollout_Blocker_Remediation_Checklist_v1.md
- docs/LIVEFOLLOW/XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md
- docs/LIVEFOLLOW/Private_Follow_Current_Repo_State_2026-03-24.md
- docs/LIVEFOLLOW/Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md
- docs/LIVEFOLLOW/README_current.md

Useful context included in this pack:
- docs/LIVEFOLLOW/LiveFollow_Current_Deployed_API_Contract_v3.md
- docs/LIVEFOLLOW/LiveFollow_Current_State_and_Next_Slice_2026-03-23.md
- docs/LIVEFOLLOW/LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md
- docs/LIVEFOLLOW/ServerInfo.md

Also obey any AGENTS.md files in scope.

## Hard scope
Fix only the rollout blockers:
1. freeze a clean server/app release candidate
2. fix the Alembic bootstrap path so a fresh empty DB can upgrade safely
3. harden dev auth so release/prod cannot accidentally use dev bearer shortcuts
4. add env/config preflight checks where appropriate
5. rerun repo-native verification
6. update docs to reflect the blocker fixes honestly

## Do not do
- no new private-follow features
- no notifications
- no follower/following list screens
- no social polish
- no speculative deployed-contract edits
- do not break public /api/v1/live/*

## Specific requirements
- preserve current public LiveFollow behavior
- preserve current private-follow behavior
- if you change migrations, make the migration path explicit and safe
- if you harden dev auth, keep local dev workable but make release/prod safe by default
- if any blocker cannot be fully solved in repo code alone, document the exact external/manual step still required

## Verification required
Run the repo-native checks and report exact commands and results.
At minimum include:
- server tests
- Android enforceRules
- Android unit tests
- Android assembleDebug
- release build verification if applicable

## Report back required
When done, report back with:
- exact files changed
- exact migration/bootstrap fix made
- exact dev-auth hardening changes
- exact verification commands run
- whether the release candidate is now clean/frozen
- what still remains manual/external before rollout can proceed
- exact docs updated
