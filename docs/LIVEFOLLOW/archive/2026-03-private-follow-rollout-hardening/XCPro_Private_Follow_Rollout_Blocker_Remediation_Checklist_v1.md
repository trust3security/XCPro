# XCPro Private Follow — Rollout Blocker Remediation Checklist v1
## Pass/fail gate for release hardening before staging rollout resumes

**Purpose:** Review whether the paused rollout blockers were actually fixed.  
**Scope:** Release-candidate freeze, migration bootstrap safety, dev-auth hardening, environment preflight, and staging-readiness preparation.  
**Not the owner:** This checklist is a gate, not the rollout owner doc.

## Pass/fail rule
Approve rollout resumption only if all required sections below pass.

Reject or keep rollout paused if any blocker remains unresolved or unproven.

---

## A. Release artifact freeze
### Pass if
- the server rollout candidate is pinned to one clean commit/tag
- the Android rollout candidate is pinned to one clean commit/tag
- both worktrees are clean at the point of release-candidate selection
- unrelated local changes are not bundled into the release artifact

### Fail if
- server rollout still depends on dirty local files
- the app/server candidate is ambiguous
- there is no exact SHA/tag to deploy

---

## B. Dev auth shortcut hardening
### Pass if
- release Android builds do not expose `Use configured dev account`
- release Android builds cannot authenticate through a configured static dev bearer shortcut
- server-side static/dev bearer auth is disabled outside explicit dev mode
- prod/staging runtime cannot accidentally enable the dev bearer path silently

### Fail if
- the dev account path is reachable in release builds
- configured static bearer auth can still be used in production by misconfiguration
- the repo has no clear debug vs release behavior rule

---

## C. Fresh-DB migration safety
### Pass if
- `alembic upgrade head` succeeds on a brand-new empty DB
- the baseline/bootstrap path now creates or otherwise guarantees all prerequisite schema expected by later migrations
- the fix does not break upgrade behavior for already-upgraded DBs
- the exact verification command/result is reported

### Fail if
- fresh DB bootstrap still requires manual pre-seeding
- the migration fix is only documented, not proven
- the remediation report omits the actual verification command

---

## D. Environment preflight validation
### Pass if
- there is a clear preflight validation path for required env/config
- Google server client ID presence is checked
- bearer secret presence is checked
- misconfigured dev bearer auth in staging/prod is detected
- release owner can run one clear pass/fail validation before rollout

### Fail if
- env validation still relies on manual guesswork
- critical auth config can be missing without obvious detection
- staging/prod can start with unsafe dev-auth configuration undetected

---

## E. Staging smoke readiness
### Pass if
- a concrete private-follow staging smoke guide or script exists
- it covers auth exchange, follow relationship baseline, `off | followers | public`, visibility switching, and existing v1 write-token upload flow
- it matches current implemented endpoints and app flows

### Fail if
- staging verification is still only implied
- the smoke guide is aspirational or outdated
- key visibility-gating cases are missing

---

## F. Automated verification rerun
### Pass if
- server tests were rerun after remediation
- Android unit/build verification was rerun after remediation
- release build assembly was rerun after remediation
- fresh-DB migration verification was rerun after remediation
- the report lists exact commands and outcomes

### Fail if
- only old test results are reused
- fresh-DB verification is missing
- release build verification is missing

---

## G. Docs accuracy
### Pass if
- repo-state/setup/rollout docs were updated where the remediation changed reality
- docs still separate public deployed-contract reality from private-follow repo state
- docs do not claim staging/prod verification happened unless it actually happened

### Fail if
- rollout hardening changed behavior but docs stayed stale
- docs blur repo proof with deployed proof
- docs hide remaining manual staging requirements

---

## Final decision
### Approve rollout resumption if
- the release candidate is frozen cleanly
- the dev auth shortcuts are sealed off correctly
- fresh DB bootstrap is proven safe
- environment preflight exists
- staging smoke steps are ready
- automated verification was rerun successfully

### Keep rollout paused if
- any of the above remains missing, unproven, or ambiguous
