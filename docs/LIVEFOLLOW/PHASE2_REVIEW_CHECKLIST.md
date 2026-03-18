# XCPro LiveFollow — Phase 2 Review Checklist

## Purpose
Use this checklist to review **Phase 2** of LiveFollow before merge.

Phase 2 is **contracts / seams / repository-boundary work only**.
A green CI run is necessary, but **not sufficient**. Manual review is required.

---

## Phase 2 intent
Phase 2 should add the **headless LiveFollow seam layer** that sits between:
- Phase 1 decision logic
- future UI / route wiring
- future backend / direct-watch implementations

It should define buildable, repo-native contracts for:
- ownship export
- local session/backend mirror state
- watch arbitration state
- boundary models needed by those seams

It must **not** jump ahead into UI, map rendering, backend implementation, or notifications.

---

## ✅ REQUIRED — what must be present

Confirm the implementation includes only the following kinds of changes:

### Module / wiring
- `feature/livefollow` remains the implementation target
- module wiring is minimal and repo-native
- dependencies are limited to what Phase 2 actually needs
- no accidental app-level feature enablement beyond what is required for build/test

### Contracts / seams
- `LiveOwnshipSnapshotSource`
- `LiveFollowSessionGateway` (port/interface only)
- `DirectWatchTrafficSource` (port/interface only)
- `LiveFollowSessionRepository`
- `WatchTrafficRepository`

### Boundary / domain models
- ownship snapshot models
- session models
- watch-traffic models
- runtime / eligibility / quality models only where needed
- reuse of Phase 1 models where appropriate instead of duplicate types

### Ownership separation
Confirm these owners remain separate:
- `LiveFollowSessionRepository` owns local session/backend mirror truth
- `WatchTrafficRepository` owns watch arbitration truth
- `feature:flight-runtime` remains ownship truth owner
- `feature:traffic` remains raw OGN/public traffic truth owner
- `feature:tasks` / `feature:map-runtime` remain task truth/render owners

### Time / determinism
- monotonic freshness decisions use a single consistent internal unit
- no wall-clock dependence in decision logic
- replay-safe side-effect blocking remains intact
- tests are deterministic

### Tests
There should be meaningful tests for:
- ownship snapshot seam mapping
- replay blocking / side-effects allowed state
- session repository state transitions
- watch arbitration using the Phase 1 arbitrator + state machine
- exact OGN match
- direct fallback when OGN is unresolved or ineligible
- ambiguous typed identity handling
- stale / offline progression

---

## ❌ FORBIDDEN — reject if any of these appear

Phase 2 must NOT introduce:

### UI / rendering
- UI code
- Compose
- routes/screens
- map rendering logic
- map overlay logic
- camera / trail behavior
- `MapScreenViewModel` changes

### Backend / transport implementation
- Retrofit implementation
- WebSocket implementation
- real backend session client implementation
- real direct-watch network implementation
- notification / FCM work

### Architecture drift
- task ownership changes
- second ownship pipeline
- direct sensor reads from LiveFollow
- direct coordinator / manager bypasses
- watch mode built on ordinary OGN facade / overlay preference state
- LiveFollow taking ownership of OGN stream enablement in Phase 2
- duplicate identity/source/state models that should reuse Phase 1 types

### Wiring drift
- unnecessary app/Hilt production wiring
- dependencies on `:feature:map`, `:feature:map-runtime`, or `:feature:tasks` unless clearly justified and documented
- non-minimal module scaffolding unrelated to Phase 2

### Repo hygiene issues
- committed build artifacts
- generated files checked in
- placeholder package churn that is not repo-native

---

## ⚠️ ARCHITECTURE VALIDATION

Confirm all of the following:

### Seam placement
- Phase 2 stays headless inside `feature/livefollow`
- new seams live in repo-native packages and file locations
- ports remain ports; implementations are limited to repo-local repositories/adapters only where Phase 2 explicitly calls for them

### Ownship export
- ownship snapshot export comes from `feature:flight-runtime`, not from sensors or `feature:map`
- ownship seam is read-only from the perspective of LiveFollow consumers
- freshness uses monotonic time, not presentation/output timestamps

### Watch traffic
- watch arbitration reads raw OGN/public traffic inputs, not ordinary overlay/trail-selected facade state
- direct watch source is abstracted behind a port only
- Phase 1 arbitration/state-machine logic is reused rather than duplicated

### Session truth
- session repository is the local SSOT for LiveFollow session/watch state in this phase
- backend gateway remains a boundary/port, not a full transport implementation
- replay blocking affects side-effect permission, not arbitrary unrelated state

### Naming / units
- one consistent monotonic time unit is used internally
- field names make units explicit where practical
- if repo-native conventions differ from older docs, code should follow repo-native conventions and note doc updates separately

---

## 🧪 TEST QUALITY CHECK

Review tests for quality, not just existence.

Confirm tests are:
- deterministic
- independent
- meaningful for risky behavior
- not duplicate noise
- using fakes/test doubles rather than real network/backend dependencies

Specific coverage to look for:
- ownship snapshot mapping from flight-runtime models
- replay mode causing side-effects to be blocked
- successful session start/join state updates
- failure paths preserving or reporting state correctly
- OGN exact match selection
- unresolved/ambiguous OGN causing correct fallback or ambiguity handling
- stale and offline progression
- no accidental wall-time fallback when monotonic data is missing

---

## 📄 DOC SYNC CHECK

Before merge, confirm whether any docs need follow-up updates.

Typical Phase 2 doc sync items:
- time-unit wording drift (for example nanos vs ms)
- repo-native namespace/package wording
- seam ownership wording if implementation clarified it
- Next Steps doc status update after merge

Doc wording updates should be recorded, but they do not automatically block merge unless they hide a real architecture mismatch.

---

## 📊 FINAL DECISION

### APPROVE if:
- all required Phase 2 seam/repository elements are present
- no forbidden items were introduced
- ownership boundaries remain clean
- tests are meaningful and deterministic
- verification is green

### REJECT if:
- Phase 2 drifted into UI/map/backend implementation
- ordinary OGN overlay/facade state was reused as watch truth
- a second ownship pipeline was introduced
- time-base handling is inconsistent or unclear
- tests are weak, noisy, or missing on risky paths

---

## 🤖 Codex validation prompt

Use this prompt to audit the current implementation:

> Review the current LiveFollow Phase 2 implementation against `docs/LIVEFOLLOW/PHASE2_REVIEW_CHECKLIST.md`.
> Confirm:
> - all REQUIRED items are present
> - no FORBIDDEN items are present
> - architecture ownership constraints are respected
> - tests are meaningful and deterministic
> - any doc wording that should be updated after Phase 2
>
> Provide:
> 1. PASS or FAIL
> 2. exact violations or gaps
> 3. whether code changes are required before merge
> 4. whether any docs should be updated after merge

