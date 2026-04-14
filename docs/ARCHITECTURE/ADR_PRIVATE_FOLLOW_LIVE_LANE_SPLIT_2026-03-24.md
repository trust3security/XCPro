# ADR_PRIVATE_FOLLOW_LIVE_LANE_SPLIT_2026-03-24

## Metadata

- Title: Keep the public v1 LiveFollow lane and add a parallel authenticated v2 entitlement lane
- Date: 2026-03-24
- Status: Accepted
- Owner: Codex
- Reviewers: TBD
- Related issue/PR: Phase 3 private-follow live entitlement
- Related change plan:
  - `docs/refactor/Private_Follow_Live_Entitlement_Phase3_Phased_IP_2026-03-24.md`
- Supersedes:
  - none
- Superseded by:
  - none

## Context

Current note:
- Current repo-wide ViewModel seam policy is defined in `docs/ARCHITECTURE/ARCHITECTURE.md` and `docs/ARCHITECTURE/CODING_RULES.md`.

- Problem:
  - XCPro Phase 1 and Phase 2 introduced signed-in identity, privacy defaults, and follow relationships, but the live-session lane still behaves as an anonymous public session model.
  - Making the existing v1 public lane auth-only would break the preserved public watch/share-code workflow.
- Why now:
  - the approved Phase 3 brief requires follower-only entitlement without redesigning the public spectator MVP
- Constraints:
  - keep anonymous/public watch working
  - reuse the existing write-token upload path for positions/task/end
  - do not let non-public sessions leak into the public active/session/share reads
  - keep replay behavior unchanged
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`

## Decision

XCPro will keep the current public v1 LiveFollow lane and add a parallel authenticated v2 lane for live-session ownership and entitlement.

Required:
- ownership/boundary choice:
  - server `LiveSession` remains the canonical live-session owner and now also owns `owner_user_id` and `visibility`
  - app `LiveFollowSessionRepository` remains the session SSOT and consumes a transport seam that can start/read via either v1 or v2 as appropriate
  - public browse remains a separate owner from signed-in following-live browse
- dependency direction impact:
  - UI still talks to ViewModels; this ADR does not move endpoint/lane selection into UI code
  - endpoint/lane selection stays in data-layer transport owners, not in UI code
- API/module surface impact:
  - new authenticated APIs:
    - `POST /api/v2/live/session/start`
    - `PATCH /api/v2/live/session/{session_id}/visibility`
    - `GET /api/v2/live/following/active`
    - `GET /api/v2/live/users/{user_id}`
    - `GET /api/v2/live/session/{session_id}`
  - existing public APIs remain:
    - `POST /api/v1/session/start`
    - `POST /api/v1/position`
    - `POST /api/v1/task/upsert`
    - `POST /api/v1/session/end`
    - `GET /api/v1/live/active`
    - `GET /api/v1/live/{session_id}`
    - `GET /api/v1/live/share/{share_code}`
  - v1 public reads are explicitly gated to `visibility == public`
- time-base/determinism impact:
  - no replay or fusion time-base changes
  - server ownership/visibility timestamps remain wall-clock persistence data only
- concurrency/buffering/cadence impact:
  - existing pilot upload cadence and direct-watch polling cadence remain unchanged
  - no new long-lived runtime owner is introduced

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Convert the current v1 lane into authenticated-only behavior | smallest endpoint count | breaks preserved public spectator/public-share workflow |
| Add a fully separate authenticated writer path too | cleaner long-term API symmetry | unnecessary risk for Phase 3 because the v1 write-token uploader already works |
| Enforce follower visibility in the app only | less server work | incorrect boundary; entitlement must be server-owned |

## Consequences

### Benefits
- follower-only entitlement becomes server-enforced instead of UI-conventional
- anonymous public watch continues to work
- the app can add signed-in following-live discovery without rewriting the uploader

### Costs
- two live API lanes now exist and must stay clearly documented
- app transport code must understand public vs authenticated read/start behavior

### Risks
- public-v1 regressions if gating is incomplete
- app drift if public browse and following-live browse are collapsed into one mixed owner
- future work might forget that `share_code` is only public when visibility is public

## Validation

- Tests/evidence required:
  - server API tests for public gating, authenticated reads, following-live list, and visibility patch
  - Kotlin tests for gateway/authenticated watch/public fallback behavior
  - Kotlin tests for following-live browse and pilot visibility UI behavior
- SLO or latency impact:
  - no map/render SLO impact expected
- Rollout/monitoring notes:
  - keep signed-out public watch and signed-out public pilot fallback covered during rollout

## Documentation Updates Required

- `ARCHITECTURE.md`:
  - no change required
- `CODING_RULES.md`:
  - no change required
- `PIPELINE.md`:
  - update LiveFollow pilot, browse, and watch runtime wiring
- `CONTRIBUTING.md`:
  - no change required
- `KNOWN_DEVIATIONS.md`:
  - no change required unless a rule exception becomes necessary

## Rollback / Exit Strategy

- What can be reverted independently:
  - app following-live browse lane
  - authenticated v2 start/read UI integration
  - server v2 live endpoints
- What would trigger rollback:
  - broken public watch/public share behavior
  - entitlement leaks through v1 public endpoints
- How this ADR is superseded or retired:
  - supersede only if XCPro intentionally replaces the dual-lane model with one documented live API contract
