# LIVEFOLLOW — Private Follow Operational Docs Tightening Brief
## Archive one-off cleanup/remediation docs, split operational vs proposal docs, and point the repo-state doc at staging execution

**Status:** Approved docs-only cleanup  
**Scope:** Tighten the current `docs/LIVEFOLLOW` set so the folder reflects the real next move: staging environment validation and rollout execution.  
**Non-goal:** No app code changes, no server code changes, no API behavior changes.

## Goal
The repo-side private-follow implementation and rollout-hardening work are done. The remaining blockers are now operational:
- real staging/prod environment setup
- real-device Google token exchange verification
- staging smoke execution
- production smoke after deploy

The `docs/LIVEFOLLOW` folder should now reflect that reality.

The goal of this cleanup is to:
- keep the public LiveFollow canon unchanged
- keep the private-follow operational docs obvious
- demote or archive one-off cleanup/remediation docs that have already done their job
- keep longer-term private-follow proposal docs available, but clearly marked as reference/proposal rather than current rollout-owner docs
- make `README_current.md` and `Private_Follow_Current_Repo_State_2026-03-24.md` point operators directly at the next execution step

## Desired end state
After this cleanup, the root `docs/LIVEFOLLOW` folder should clearly separate:

### A. Public LiveFollow current canon
Keep these active in root:
- `README_current.md`
- `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
- `LiveFollow_Current_Deployed_API_Contract_v3.md`
- `ServerInfo.md`
- `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`

### B. Private-follow operational docs
These are the docs that should now be easiest to find for the next step:
- `Private_Follow_Current_Repo_State_2026-03-24.md`
- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
- `XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`

### C. Private-follow proposal/reference docs
Keep these active for future unimplemented work, but do not present them as the current rollout-owner set:
- `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
- `XCPro_Private_Follow_Change_Plan_2026-03-23.md`

### D. Archive now — one-off docs that already did their job
Move these out of the active root:
- `LIVEFOLLOW_Private_Follow_Docs_Cleanup_Brief_2026-03-24.md`
- `XCPro_Private_Follow_Rollout_Blocker_Remediation_Brief_2026-03-24.md`
- `XCPro_Private_Follow_Rollout_Blocker_Remediation_Checklist_v1.md`

Preferred rationale:
- the cleanup brief is historical after the previous cleanup pass
- the remediation brief/checklist were for the repo hardening pass and are now historical because those repo-side rollout blockers are fixed

## Task 1 — Create or reuse an archive folder for rollout-hardening docs
Create or reuse a new archive folder under `docs/LIVEFOLLOW/archive/`, preferably:
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-rollout-hardening/`

Do not delete content. Archive only.

If an equivalent archive folder already exists and is clearly the right place, reuse it and explain that choice in the report.

## Task 2 — Move one-off cleanup/remediation docs to archive
Move these files into the archive folder created/reused in Task 1:
- `LIVEFOLLOW_Private_Follow_Docs_Cleanup_Brief_2026-03-24.md`
- `XCPro_Private_Follow_Rollout_Blocker_Remediation_Brief_2026-03-24.md`
- `XCPro_Private_Follow_Rollout_Blocker_Remediation_Checklist_v1.md`

Do not archive:
- `Private_Follow_Current_Repo_State_2026-03-24.md`
- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
- `XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`

Those four are the current private-follow operational docs.

## Task 3 — Rewrite `README_current.md`
Rewrite `README_current.md` so it is easy to navigate under the current reality.

It should clearly separate these sections:

### Public LiveFollow current canon
Point readers to:
1. `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
2. `LiveFollow_Current_Deployed_API_Contract_v3.md`
3. `ServerInfo.md`
4. `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`

### Private-follow operational docs
Point readers to:
1. `Private_Follow_Current_Repo_State_2026-03-24.md`
2. `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
3. `XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
4. `XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`

### Private-follow proposal/reference docs
Point readers to:
1. `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
2. `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
3. `XCPro_Private_Follow_Change_Plan_2026-03-23.md`

### Related architecture / refactor docs
If these files exist, link them as related but not current owners:
- `docs/refactor/Private_Follow_Live_Entitlement_Phase3_Phased_IP_2026-03-24.md`
- `docs/ARCHITECTURE/ADR_PRIVATE_FOLLOW_LIVE_LANE_SPLIT_2026-03-24.md`

### Archive section
Point readers to:
- `docs/LIVEFOLLOW/archive/2026-03-single-pilot-spectator-mvp/`
- `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/`
- the new/used rollout-hardening archive folder from this cleanup

### Important wording rule
`README_current.md` should make clear that:
- the public deployed contract owner remains `LiveFollow_Current_Deployed_API_Contract_v3.md`
- `Private_Follow_Current_Repo_State_2026-03-24.md` is the owner for what private-follow behavior exists in repo now
- the rollout checklist and staging smoke guide are the current operator docs
- the product brief / proposed API contract / change plan are still useful but are proposal/reference docs, not the immediate rollout operator set
- archived one-off cleanup/remediation docs are historical and should not be treated as current owners

## Task 4 — Update `Private_Follow_Current_Repo_State_2026-03-24.md`
Update this doc so it points directly at the current operational reality.

Add or tighten a short top section with:
- current rollout status: repo hardening complete, rollout still paused pending real env setup and staging/prod verification
- current pinned release candidates:
  - server commit/tag
  - app commit/tag
- the next operator step:
  - set staging env
  - run env preflight
  - verify real-device Google exchange
  - deploy server to staging first
  - run the staging smoke guide

Also ensure the doc still clearly states:
- what is implemented in repo now
- what remains manual/external
- what remains out of scope / not yet implemented
- that public `/api/v1/live/*` remains separate

Add a small navigation section that points to:
- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`
- `XCPro_Private_Follow_Staging_Smoke_Guide_2026-03-24.md`
- `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
- `XCPro_Private_Follow_Change_Plan_2026-03-23.md`

### Important rule
Do not turn this repo-state doc into the public deployed contract owner.

## Task 5 — Leave the public LiveFollow canon alone
Do not rewrite these in this cleanup unless a broken reference requires a trivial fix:
- `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
- `LiveFollow_Current_Deployed_API_Contract_v3.md`
- `ServerInfo.md`
- `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`

Reason:
- the current blocker is not public-lane docs
- private-follow is still in repo/release-validation mode, not yet a verified public deployed-contract owner

## Task 6 — Fix references after archive moves
After archiving and updating docs:
- fix any links in `README_current.md`
- fix any links in `Private_Follow_Current_Repo_State_2026-03-24.md`
- avoid leaving the archived cleanup/remediation docs referenced as current active docs
- if any kept-active doc still references the archived one-off briefs as current owners, update that wording

## Constraints
- docs-only cleanup
- do not change app code
- do not change server code
- do not rewrite technical facts speculatively
- do not present private-follow repo behavior as verified public deployed-contract reality unless that has actually happened
- do not archive the public LiveFollow canon
- do not archive the current private-follow operational docs
- do not archive the longer-term proposal/reference docs unless you find a very strong explicit replacement
- preserve historical details by archiving rather than deleting

## Acceptance criteria
This cleanup is complete when:
- the one-off cleanup/remediation docs are archived
- `README_current.md` clearly separates public canon, private-follow operational docs, and private-follow proposal/reference docs
- `Private_Follow_Current_Repo_State_2026-03-24.md` clearly states rollout status, pinned RCs, and the next operator step
- current operator docs are easy to find
- archived one-off docs are no longer implied to be current owners
- the report clearly lists:
  - archive folder created/reused
  - exact files moved to archive
  - exact files updated
  - final active operational docs
  - final active proposal/reference docs

## Report back expected from Codex
When done, report back with:
- exact archive folder created or reused
- exact files moved to archive
- exact files updated
- any stale references fixed manually
- the final active private-follow operational doc set
- the final active private-follow proposal/reference doc set
