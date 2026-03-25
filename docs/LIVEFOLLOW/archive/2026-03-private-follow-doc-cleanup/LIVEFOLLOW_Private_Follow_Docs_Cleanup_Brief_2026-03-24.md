# LIVEFOLLOW - Private Follow Docs Cleanup Brief
## Clean up `docs/LIVEFOLLOW` after private-follow planning and early implementation

**Status:** Historical cleanup brief from before Phase 3 completion  
**Scope:** Clean up `docs/LIVEFOLLOW` so the folder has a smaller obvious current canon for both the public LiveFollow lane and the private-follow lane, while completed or redundant execution docs move to archive.  
**Non-goal:** No app code changes, no server code changes, no API behavior changes.

Historical note:
- this brief predates Phase 3 completion
- the completed Phase 3 brief/checklist are now archived under `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/`
- use `README.md` for the current canon

## Goal
`docs/LIVEFOLLOW` now contains a mix of:
- current public LiveFollow canon
- current private-follow proposal owners
- current private-follow repo-state docs
- active phase-specific execution docs
- completed older phase execution docs
- one-off setup notes

The goal of this cleanup is to:
- keep the current public LiveFollow canon obvious
- keep the current private-follow canon obvious
- archive completed or redundant one-off execution docs
- avoid duplicate current-owner docs
- make `README.md` the reliable entrypoint again

## Desired end state
After this cleanup, the `docs/LIVEFOLLOW` root should make it obvious which docs are still active.

### A. Keep active - public LiveFollow current canon
These should remain active in root:

1. `README.md`
2. `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
3. `LiveFollow_Current_Deployed_API_Contract_v3.md`
4. `ServerInfo.md`
5. `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`

### B. Keep active - private-follow current canon
These should remain active in root:

1. `Private_Follow_Current_Repo_State_2026-03-24.md`
2. `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
3. `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
4. `XCPro_Private_Follow_Change_Plan_2026-03-23.md`
5. `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md` if it remains uniquely useful

### C. Audit and decide - keep only if still uniquely useful
Audit this doc and keep it active only if it still has unique value as a current setup note:

- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`

Preferred rule:
- keep it active only if it still contains current environment/setup requirements that are not already captured clearly in `Private_Follow_Current_Repo_State_2026-03-24.md` or `README.md`
- otherwise merge the essential setup bullets into the current repo-state doc or README and archive the standalone setup note

### D. Move to archive - completed or redundant docs
These should move out of the active root unless you find a strong reason they still own unique current behavior:

- `XCPro_Private_Follow_Implementation_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Phase1_Accounts_Profile_Codex_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Phase1_Review_Checklist_v1.md`

Preferred rationale:
- the standalone implementation brief is redundant now that the product brief, proposed API contract, and change plan exist
- the Phase 1 Codex brief and Phase 1 review checklist are historical execution docs for a completed earlier phase

## Task 1 - Create a new archive folder for private-follow cleanup
Create a new archive folder under `docs/LIVEFOLLOW/archive/`, preferably:

- `docs/LIVEFOLLOW/archive/2026-03-private-follow-doc-cleanup/`

Do not delete content. Archive only.

## Task 2 - Move completed or redundant docs to the new archive folder
Move these files into the new archive folder:

- `XCPro_Private_Follow_Implementation_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Phase1_Accounts_Profile_Codex_Brief_2026-03-23.md`
- `XCPro_Private_Follow_Phase1_Review_Checklist_v1.md`

Phase 3 later landed fully in repo, so the Phase 3 brief/checklist became
historical and were archived in the same cleanup archive folder.

## Task 3 - Audit the Google auth setup note
Audit:

- `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`

### Preferred outcome
Keep it active only if it is still the clearest current owner for:
- Google Credential Manager sign-in setup
- XCPro-Server token exchange setup
- required Android/server environment variables
- external setup still needed outside repo code

### Alternate outcome
If its content is short and can be folded cleanly into:
- `Private_Follow_Current_Repo_State_2026-03-24.md`, or
- `README.md`

then:
- merge the essential setup bullets
- archive the standalone setup note

Do not leave it active merely because it exists.

## Task 4 - Rewrite `README.md`
Rewrite `README.md` so the current docs are easy to navigate again.

It should clearly separate:

### Public LiveFollow current canon
Point readers to:
1. `LiveFollow_Current_State_and_Next_Slice_2026-03-23.md`
2. `LiveFollow_Current_Deployed_API_Contract_v3.md`
3. `ServerInfo.md`
4. `LIVEFOLLOW_APP_SERVER_CONTRACT_CHECKLIST_v4.md`

### Private-follow current canon
Point readers to:
1. `Private_Follow_Current_Repo_State_2026-03-24.md`
2. `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
3. `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
4. `XCPro_Private_Follow_Change_Plan_2026-03-23.md`
5. `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md` only if it survives the audit

### Archive section
Point readers to both archive areas if they exist:
- `docs/LIVEFOLLOW/archive/2026-03-single-pilot-spectator-mvp/`
- the new private-follow archive folder created in this cleanup

### Important wording rule
`README.md` should make clear that:
- the public deployed contract owner remains `LiveFollow_Current_Deployed_API_Contract_v3.md`
- the private-follow proposal/plan docs do not automatically mean deployed behavior
- the private-follow repo-state doc is the current owner for what is implemented in repo now
- completed phase execution briefs/checklists are historical and archived

## Task 5 - Refresh `Private_Follow_Current_Repo_State_2026-03-24.md`
Update the repo-state doc so it is a better current owner for the private-follow lane.

It should still state:
- what is implemented in repo now
- what remains out of scope / not implemented yet
- that public `/api/v1/live/*` remains separate

Also add a small navigation section such as:
- `Current proposal owners`
- `Current setup note` if the Google auth setup note stays active
- `Related architecture / refactor docs` if they exist

Preferred additions:
- a short note that completed phase execution briefs/checklists are archived once the phase lands
- direct references to any durable architecture/refactor docs created by the completed phase

Do not turn this repo-state doc into the deployed public contract owner.

## Task 6 - Remove duplicate or stale "current owner" ambiguity
Audit the active private-follow docs and make sure each remaining active file has a clear role.

### Desired role split
- `XCPro_Private_Follow_Product_and_UX_Brief_2026-03-23.md`
  - product/UX proposal owner
- `XCPro_Private_Follow_Proposed_API_Contract_v1.md`
  - proposed authenticated API contract owner
- `XCPro_Private_Follow_Change_Plan_2026-03-23.md`
  - multi-phase implementation/change plan owner
- `Private_Follow_Current_Repo_State_2026-03-24.md`
  - current implemented repo-state owner
- completed phase-specific execution briefs/checklists
  - archive them once the phase is complete and newer durable current-owner docs exist

### Specifically remove ambiguity caused by
- the standalone `XCPro_Private_Follow_Implementation_Brief_2026-03-23.md`
- any README wording that makes Phase 1 docs look current
- any wording that treats repo-only private-follow work as the deployed public contract

## Task 7 - Fix references after archive moves
After moving and updating docs:
- fix any broken links or references in `README.md`
- fix any references inside the private-follow docs that still point at active filenames which were archived
- avoid leaving stale references to the archived Phase 1 brief/checklist as if they are still current

## Constraints
- docs-only cleanup
- do not change app code
- do not change server code
- do not rewrite technical facts speculatively
- do not present repo-only private-follow behavior as public deployed contract reality
- do not archive the public LiveFollow canon
- do not archive the private-follow product/API/change-plan owners
- archive completed phase briefs/checklists once the phase is complete and replaced by newer durable current-owner docs
- preserve historical details by archiving rather than deleting

## Acceptance criteria
This cleanup is complete when:
- a new private-follow archive folder exists
- the redundant implementation brief is archived
- the completed Phase 1 brief/checklist are archived
- `README.md` clearly separates public canon from private-follow canon
- `Private_Follow_Current_Repo_State_2026-03-24.md` clearly points to the durable current-owner docs and implemented repo state
- active private-follow docs each have a clear non-overlapping role
- there are no obvious stale Phase 1/current-owner references left in the active root docs
- the report clearly lists:
  - archive folder created
  - exact files moved to archive
  - exact files kept active and why
  - whether `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md` was kept or archived
  - exact files updated

## Report back expected from Codex
When done, report back with:
- exact archive folder created
- exact files moved to archive
- whether `Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md` was kept active or archived
- exact files updated
- any stale references that had to be fixed manually
- the final active-doc canon after cleanup

