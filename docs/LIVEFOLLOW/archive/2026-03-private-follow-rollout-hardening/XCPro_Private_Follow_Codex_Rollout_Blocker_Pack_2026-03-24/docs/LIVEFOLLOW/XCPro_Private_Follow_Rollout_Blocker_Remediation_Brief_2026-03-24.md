# XCPro Private Follow — Rollout Blocker Remediation Brief
## Freeze the release candidate, harden auth, fix bootstrap migrations, and prepare staging verification

**Status:** Approved remediation brief  
**Scope:** Resolve the concrete blockers that paused rollout of the private-follow lane.  
**Non-goal:** Do not add new product features. Do not redesign UI. Do not expand scope into notifications, follower lists, blocks, or broader social polish.

## What this is
This is **not** a product-design failure. It is a release-hardening failure.

Local code gates passed, but rollout was paused because of four deployment blockers:
1. the server rollout candidate is not frozen to a clean pinned commit
2. real environment/auth setup is not verified
3. the Alembic migration path is not bootstrap-safe on a fresh DB
4. debug/dev auth shortcuts are still present and could be dangerous if misconfigured

The goal of this brief is to fix the repo/process blockers so rollout can move from **2/5 readiness** to **deploy-safe**.

## Outcome required from this pass
After this remediation pass:
- the server rollout candidate is a clean pinned commit/tag
- the server migration chain upgrades a fresh empty DB to head successfully
- release/prod builds do not expose the dev bearer seam or configured dev-account path
- server-side static dev bearer support is fail-closed outside explicit dev mode
- environment preflight requirements are explicit and testable
- staging verification can start with a clean release artifact instead of local-only proof

## Important truth
This pass can fix most of the blockers in repo.

It **cannot** by itself complete the real-environment steps that require:
- actual Google OAuth client configuration
- staging/prod secrets
- a real device or emulator path for Google sign-in
- an actual staging deployment target

So the expected outcome is **rollout-ready repo + clean release candidate + clear manual staging gate**, not fake proof.

## Task 1 — Freeze a clean server rollout candidate
The current server candidate is not deploy-safe because the worktree is dirty.

### Required actions
- inspect server `git status`
- separate intended private-follow rollout changes from unrelated local changes
- either:
  - commit the intended server changes on a dedicated rollout branch, or
  - create a clean release branch and cherry-pick only the intended commits
- ensure the final server worktree is clean
- tag or otherwise pin the exact release candidate commit
- record the exact commit SHA in the final report

### Required pass condition
- `git status` is clean for the server rollout candidate
- the report names one exact pinned server commit/tag

### Fail if
- rollout still depends on uncommitted local files
- unrelated local experiments remain bundled into the release artifact

## Task 2 — Freeze a clean Android rollout candidate
The Android candidate already has a pinned commit in the pause report, but it still needs release-artifact discipline.

### Required actions
- confirm the Android rollout candidate commit really contains the intended private-follow changes and no unrelated partial work
- confirm the Android worktree is clean
- pin the exact app release candidate commit/tag
- report the exact SHA/build artifact used for rollout

### Required pass condition
- Android candidate is also clean and pinned

## Task 3 — Remove or hard-gate dev auth shortcuts from release paths
This is one of the highest-risk blockers.

The report shows:
- Android still compiles a dev bearer seam
- Android still exposes a `Use configured dev account` path
- server still supports static bearer tokens

That is acceptable for local development only. It is not acceptable as an ambiguous production path.

### Android required actions
Audit:
- `feature/livefollow/build.gradle.kts`
- `ConfiguredBuildTokenXcAccountAuthProvider.kt`
- any settings/debug menu or account UI that exposes configured dev account flow

Then make release behavior fail-closed.

### Preferred release rule
For release builds:
- do not show `Use configured dev account`
- do not allow configured static dev bearer auth as a reachable production sign-in path
- gate the dev provider behind debug-only or explicit non-release build variants

### Acceptable implementation patterns
- compile the provider only in debug builds
- keep the class but guard all entry points with `BuildConfig.DEBUG`
- remove the menu entry entirely from release builds
- add tests asserting the release path cannot surface this UI/state

### Server required actions
Audit the static bearer path in `app/main.py`.

Then make production behavior fail-closed.

### Preferred server rule
- static/dev bearer auth is disabled unless an explicit dev-only flag is enabled
- if server is in prod/staging mode and dev bearer auth env is set, either:
  - refuse startup, or
  - log a loud fatal/validation error and keep the dev path disabled

### Required pass condition
- there is no accidental production path that can authenticate via configured static dev bearer tokens
- report the exact rule used for debug vs release vs server runtime

## Task 4 — Fix the Alembic bootstrap path
This is the real code-level rollout blocker.

The pause report says the migration chain is linear, but a fresh empty DB is not bootstrap-safe because:
- the baseline migration is a no-op
- the next migration assumes `live_positions` already exists

That is not acceptable for a clean rollout story.

### Required actions
Audit the early migration chain and identify the full pre-existing schema that the no-op baseline implicitly assumed.

Then make fresh DB upgrade safe.

### Preferred fix
Update the baseline/bootstrap portion of the migration chain so that:
- `alembic upgrade head` succeeds on a fresh empty DB
- all prerequisite tables/columns/indexes expected by later migrations exist before they are altered
- existing upgraded DBs are not broken by the change

### Acceptable implementation options
Choose one durable option and document it clearly:
1. convert the baseline migration into a real bootstrap migration for fresh DB creation
2. add idempotent creation logic to the baseline migration for the pre-existing schema
3. add a clearly documented bootstrap migration path that fresh environments must run before the current chain

### Important rule
Do not hand-wave this away with documentation only if the repo can be fixed cleanly.

### Required verification
After the fix, prove it.

Run a real fresh-DB test such as:
- create a brand-new empty test DB
- run `alembic upgrade head`
- verify success
- run the relevant server tests on that schema

### Required pass condition
- a fresh empty DB can reach head without manual pre-seeding of old tables

## Task 5 — Add environment preflight validation
The current setup depends on external secrets and Google audience matching, but the rollout report had no real proof those were configured correctly.

You cannot solve staging secrets in repo, but you can make misconfiguration more obvious.

### Required actions
Add a lightweight preflight check for private-follow rollout requirements.

This can be one of:
- a server startup validation block
- a dedicated internal check function/CLI
- a small script under `scripts/` used by release owners

### It should validate at minimum
- whether Google server client ID env is present
- whether bearer secret env is present
- whether prod/staging runtime is attempting to allow dev bearer shortcuts
- whether required auth config is obviously missing

### Good outcome
Release owner can run one command and get a clear pass/fail for env preconditions instead of discovering problems mid-rollout.

### Important constraint
Do not make local dev impossible. Keep any hard failure scoped appropriately to staging/prod or an explicit release-validation mode.

## Task 6 — Prepare a deterministic staging smoke harness
Do not try to fake staging proof locally.

Instead, prepare the repo so staging verification is straightforward.

### Required actions
Create or update a small staging smoke-test guide or script bundle for private-follow rollout covering:
- Google sign-in exchange
- `/api/v2/me*`
- follow request / accept path
- live start with `off | followers | public`
- public-vs-followers visibility gating
- visibility switching while live
- existing v1 write-token position/task/end flow

### Preferred artifact
One of:
- a markdown smoke-test doc under `docs/LIVEFOLLOW/`
- a script file plus a short doc

### Important rule
Keep this operational, not aspirational. It must match the implemented endpoints and app behavior.

## Task 7 — Re-run automated verification after remediation
After remediation changes land, rerun the release-candidate verification.

### Server
Run:
- `python -m unittest app.tests.test_livefollow_api`

### Android
Run:
- `./gradlew.bat enforceRules`
- `./gradlew.bat testDebugUnitTest`
- `./gradlew.bat assembleDebug`
- `./gradlew.bat :app:assembleRelease`

### Migration-specific verification
Also run a real fresh-DB upgrade verification and report the exact command(s).

### Fail if
- bootstrap migration is still not proven
- release build still exposes dev auth shortcuts
- clean commit discipline is still missing

## Task 8 — Update docs to match the fixed rollout story
After remediation, update the docs that own setup/repo reality.

### Required docs to audit/update
- `docs/LIVEFOLLOW/Private_Follow_Current_Repo_State_2026-03-24.md`
- `docs/LIVEFOLLOW/Private_Follow_Google_Server_Exchange_Setup_2026-03-24.md`
- `docs/LIVEFOLLOW/XCPro_Private_Follow_Rollout_Release_Checklist_2026-03-24.md`

### Required doc outcomes
- repo-state doc records the bootstrap migration fix and release hardening status
- setup doc reflects the actual required env vars and the fail-closed rules for dev shortcuts
- rollout checklist points at the correct staging preflight and smoke-test artifact

### Important rule
Do not change the public deployed contract owner doc just because rollout hardening changed.

## Constraints
- fix rollout blockers only
- do not add new end-user features
- do not broaden scope into follower list screens, notifications, or watcher counts
- do not remove legitimate local dev capability without replacing it with a clean debug-only path
- do not leave production auth behavior ambiguous
- do not claim staging/prod verification happened unless it actually happened

## Acceptance criteria
This remediation pass is complete when:
- server candidate is pinned to a clean commit/tag
- Android candidate is pinned to a clean commit/tag
- release/prod paths no longer expose dev bearer auth shortcuts
- server-side dev bearer auth is fail-closed outside explicit dev mode
- fresh empty DB upgrade to head succeeds and is proven
- env preflight validation exists or equivalent release validation is added
- staging smoke verification is documented/prepared
- automated verification is rerun and reported
- docs are updated to reflect the hardened rollout path honestly

## Report back expected from Codex
When done, report back with:
- exact pinned server commit/tag
- exact pinned Android commit/tag
- exact files changed for dev-shortcut hardening
- exact migration/bootstrap fix made
- exact fresh-DB verification command(s) and result
- exact env preflight command or validation path added
- exact staging smoke artifact added/updated
- exact docs updated
- exact automated test/build commands rerun
- any remaining blockers that still require real staging secrets/devices/deployment
