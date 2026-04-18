# XCPro LiveFollow Change Plan v5

## Purpose

This version removes ambiguity that caused unnecessary back-and-forth during setup.
It is the execution plan Codex should follow for **Phase 1 only**.

This plan is deliberately explicit about:
- module location
- package/path rules
- what must **not** be created
- what must **not** be decided ad hoc
- what the human should do vs what Codex should do

---

## Current decision

### Approved module target
Implement Phase 1 in a **new repo-native module** under:

`feature/livefollow`

### Rejected target
Do **not** implement Phase 1 under:

`domain/livetask`

Reason:
- `domain/livetask` currently exists only as empty folders
- it is not a real Gradle module
- it has no `build.gradle.kts`
- it is not included in `settings.gradle.kts`
- code placed there will not be built or tested

---

## Human responsibility vs Codex responsibility

### Human responsibility
The human should only:
1. create/switch to the correct branch
2. provide Codex this plan and the related docs
3. review the diff
4. run/confirm verification if needed
5. merge the PR when satisfied

### Codex responsibility
Codex should:
1. inspect nearby repo-native feature modules
2. determine the correct namespace/package convention from the repo
3. create the `feature/livefollow` module with the **minimum required wiring**
4. implement Phase 1 code
5. implement Phase 1 tests
6. run verification
7. report files changed, results, and any risks

### Important
The human should **not** be manually creating Kotlin source files or deciding package names.
That belongs to Codex.

---

## Mandatory repo-native rules for Codex

### 1. Package / namespace rule
Codex must **not** invent a generic package such as:
- `com.trust3.xcpro`
- `com.example.*`

Codex must inspect existing nearby feature modules and use the repo’s actual namespace/package convention.

### 2. Module scaffold rule
Codex must create the **smallest buildable module scaffold** that matches existing feature modules.

Allowed:
- minimal `build.gradle.kts`
- minimal module registration in `settings.gradle.kts`
- minimal `AndroidManifest.xml` only if the repo-native feature module pattern requires it

Forbidden:
- resources unless strictly required
- navigation
- UI scaffolding
- DI scaffolding
- fake "future-ready" plumbing

### 3. Ownership rule
This phase adds **pure domain logic only**.
It does **not** add runtime ownership, persistence ownership, or task ownership.

### 4. No duplicate structure rule
Codex must **reuse repo-native structure** and must not create:
- a second top-level domain module
- parallel duplicate package trees
- duplicate identity/state models in multiple places

---

## Phase 1 scope

## Allowed
Implement **only**:
1. typed aircraft identity model + resolution
2. live source arbitration (OGN vs DIRECT)
3. deterministic live-follow session state machine
4. replay-safe pure policy for side-effect blocking
5. unit tests for all of the above

## Forbidden
Do **not** add:
- UI
- Compose
- map rendering
- `MapScreenViewModel` changes
- backend/networking
- Retrofit/WebSocket code
- FCM/notifications
- repository/server wiring unless absolutely required for a pure seam
- task ownership changes
- second ownship pipeline
- direct coordinator access
- sensor usage in ViewModels
- ad hoc wall-clock logic in domain code

---

## SSOT / authority for this phase

Phase 1 does **not** create a new runtime source of truth for the app.
It creates pure decision logic only.

### Authority introduced in this phase
- identity evaluation outputs
- source arbitration outputs
- state-machine transition outputs

### Authority explicitly not introduced in this phase
- no repository persistence authority
- no backend session authority
- no map-owned authority
- no task-owned authority
- no sensor-owned authority

---

## Time-base contract

### Identity resolution
No time base.

### Freshness / stale / offline / hysteresis
Use **monotonic time only** through existing repo-native clock abstractions.
No direct wall-clock usage.

### Wall time
Not used in Phase 1 domain logic.

### Replay determinism
Same inputs + same monotonic clock sequence must produce the same outputs.
No randomness.
No hidden ambient time.

---

## File ownership rules

### Under model/
Only data types.
Examples:
- typed aircraft identity models
- source type enums/models
- confidence / match result models
- session state / event models if the repo-native structure supports that

### Under identity/
Only identity resolution logic.
Examples:
- identity resolver
- identity matching policy

### Under arbitration/
Only source selection logic.
Examples:
- OGN vs DIRECT arbitration
- dwell / hysteresis rules
- freshness evaluation

### Under state/
Only state machine logic and replay block policy.
Examples:
- session state machine
- replay-safe side-effect policy

### Tests
Tests must mirror those responsibilities and remain deterministic.

---

## Expected Phase 1 outputs

Codex should create repo-native files roughly equivalent to:

- identity models
- source models
- session state / event models
- `LiveFollowIdentityResolver`
- `LiveFollowSourceArbitrator`
- `LiveFollowSessionStateMachine`
- `LiveFollowReplayPolicy`
- unit tests covering identity, arbitration, stale/offline, stopped, ambiguity, and replay-safe behavior

### Important note
Exact file names and package paths should follow the repo’s existing naming conventions.
The list above describes responsibilities, not hardcoded package names.

---

## Required behavior

### Identity resolution must support
- exact verified match
- alias verified match only if explicitly supported by repo-native identity conventions
- ambiguous
- no match

### Source arbitration must support
- prefer OGN when valid and fresh
- fall back to DIRECT when OGN is invalid, stale, or unresolved
- avoid rapid flapping through hysteresis / dwell
- expose uncertainty explicitly

### Session state machine must support
- WAITING
- LIVE_OGN
- LIVE_DIRECT
- AMBIGUOUS
- STALE
- OFFLINE
- STOPPED

### Replay policy must support
- pure rule-based blocking of live side effects during replay-related conditions
- deterministic outputs for tests

---

## Required tests

### Identity tests
- exact match
- mismatch
- ambiguous case
- no-match case

### Arbitration tests
- OGN selected when valid and fresh
- DIRECT selected when OGN stale or unresolved
- hysteresis prevents flapping
- uncertainty is explicit

### State-machine tests
- waiting to live transitions
- live to stale transitions
- stale to offline transitions
- stopped state transition
- ambiguous handling
- replay-safe behavior

---

## Verification required

Codex must run and report:

- `./gradlew enforceRules`
- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

If a smaller initial verification is helpful during implementation, that is allowed, but the final report must include the required repo gates above.

---

## Codex execution sequence

Codex should follow this exact order:

1. inspect nearby repo-native feature modules
2. determine correct package/namespace convention
3. determine smallest valid module scaffold for `feature/livefollow`
4. wire the module into `settings.gradle.kts`
5. add only minimum buildable module files
6. implement Phase 1 domain models and logic
7. add deterministic tests
8. run verification
9. report changed files, verification results, and risks

Codex should **not** ask the human to create source folders or Kotlin files manually.

---

## Human next step

Give Codex this plan plus the related LiveFollow docs and tell it to proceed with Phase 1 under `feature/livefollow` using repo-native conventions.

Recommended instruction:

> Implement Phase 1 exactly per `docs/LIVEFOLLOW/XCPro_LiveFollow_Change_Plan_v5.md`. Use `feature/livefollow` as a new repo-native buildable module, determine package names from existing modules, keep the scaffold minimal, and implement only pure domain logic + tests. Do not ask me to create files manually.

---

## Bottom line

Yes — this level of detail should have been in the plan earlier.
This v5 corrects that.
