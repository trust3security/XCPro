# ADR_OGN_RUNTIME_OFFLINE_WAIT_WRITER_LANE_2026-03-30

## Metadata

- Title: OGN transport runtime uses an injected offline-wait seam plus a single writer lane for authoritative state
- Date: 2026-03-30
- Status: Accepted
- Owner: XCPro Team
- Reviewers: XCPro Team
- Related issue/PR: TBD
- Related change plan:
  - `docs/refactor/Ogn_Reconnect_Runtime_Hardening_Phased_IP_2026-03-30.md`
- Supersedes: None
- Superseded by: None

## Context

- Problem:
  - The OGN runtime previously treated clean EOF as an ordinary reconnect case,
    retried from the minimum backoff forever, and mutated authoritative runtime
    state from more than one execution context.
  - The runtime had no explicit offline-wait seam, so transport loss and local
    offline state were blended together and harder to reason about in UI and
    support paths.
  - Blocking socket reads and authoritative snapshot mutation were not modeled
    as separate responsibilities even though they have different concurrency
    needs.
- Why now:
  - The reconnect hardening slice introduced explicit degraded-state semantics,
    an injected connectivity boundary, and a split between raw socket I/O work
    and authoritative runtime mutation. Those are durable concurrency and
    ownership decisions, not one-off task details.
- Constraints:
  - Preserve MVVM + UDF + SSOT.
  - Keep business/runtime policy out of UI.
  - Keep Android connectivity APIs behind a data adapter.
  - Preserve replay determinism and avoid new wall-time behavior in retry/offline
    policy.
  - Keep blocking socket I/O off the authoritative mutation lane.
- Existing rule/doc references:
  - `AGENTS.md`
  - `docs/ARCHITECTURE/ARCHITECTURE.md`
  - `docs/ARCHITECTURE/CODING_RULES.md`
  - `docs/ARCHITECTURE/PIPELINE.md`

## Decision

The OGN repository runtime remains the single authoritative owner for transport
state, retry state, and published OGN snapshot semantics. It now uses two
explicit execution lanes with different responsibilities:

- ownership/boundary choice:
  - `OgnTrafficRepositoryRuntime` is the authoritative owner of
    `connectionState`, `connectionIssue`, `networkOnline`,
    `reconnectBackoffMs`, and related snapshot publication.
  - Android connectivity state is read only through
    `OgnNetworkAvailabilityPort`; runtime policy code must not call Android
    networking APIs directly.
  - UI remains read-only and maps snapshot semantics into indicators/debug
    surfaces without inferring transport policy on its own.
- dependency direction impact:
  - `feature:traffic` runtime policy depends on the domain port
    `OgnNetworkAvailabilityPort`.
  - The Android implementation lives in a data adapter and is bound through DI.
  - No new UI -> data shortcut is introduced.
- API/module surface impact:
  - `OgnNetworkAvailabilityPort` becomes the durable connectivity seam for OGN.
  - `OgnTrafficSnapshot` carries explicit `connectionIssue` and `networkOnline`
    fields for degraded/offline semantics.
  - The change stays within the existing `feature:traffic` boundary; no new
    module is introduced.
- time-base/determinism impact:
  - Retry and offline wait durations use monotonic time only.
  - Wall time remains limited to debug/display timestamps such as
    `lastReconnectWallMs`.
  - Replay behavior is unchanged because the new logic is live transport-only
    and introduces no randomness.
- concurrency/buffering/cadence impact:
  - A single writer lane owns authoritative runtime mutation and snapshot
    publication.
  - A separate I/O lane owns blocking socket reads and DDB refresh work.
  - The I/O lane emits work back to the writer lane instead of mutating shared
    runtime fields directly.
  - Offline recovery is connectivity-driven: when the port reports offline, the
    runtime enters an explicit offline wait state and resumes when online
    returns instead of blind reconnect churn.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Keep the old multi-context mutation model | smallest code churn | preserves ambiguous ownership, makes degraded-state reasoning weaker, and keeps EOF/offline handling brittle |
| Run blocking socket I/O and authoritative mutation on one serialized lane | simpler mental model | blocking reads would stall housekeeping and snapshot mutation, and the lane would become an I/O bottleneck |
| Let UI infer offline vs transport-loss state from strings | avoids snapshot/model changes | violates SSOT/layering and creates duplicated policy logic |
| Call Android connectivity APIs directly from the runtime | fewer types/files | violates dependency direction and makes policy code harder to test |

## Consequences

### Benefits
- OGN transport state has one clear write authority.
- Offline wait is explicit and testable.
- Clean EOF, offline wait, login-unverified, stall, and generic transport
  failures have distinct published semantics.
- Blocking socket I/O no longer competes with authoritative mutation on the
  same lane.

### Costs
- The runtime uses a more explicit two-lane structure.
- Snapshot and UI mapping models grow to carry structured degraded-state data.
- DI adds a new connectivity boundary for OGN.

### Risks
- Writer-lane discipline could regress later if new callsites mutate runtime
  fields directly from the I/O lane.
- The I/O lane could slowly absorb extra policy if future changes are not
  reviewed carefully.
- Local verification noise can obscure confidence because Windows/KSP state has
  been unstable in this repo.

## Validation

- Tests/evidence required:
  - OGN reconnect hardening regressions for EOF, offline wait, and
    login-unverified paths
  - OGN connection regressions for stall and intentional reconnect behavior
  - UI indicator/debug mapping tests for the new snapshot semantics
  - `./gradlew enforceRules`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug`
- SLO or latency impact:
  - No new map/UI SLO target is introduced by this ADR; the main runtime goal is
    to keep blocking socket reads off the authoritative mutation lane.
- Rollout/monitoring notes:
  - Treat clean EOF as degraded transport loss, not silent normal shutdown.
  - Keep OGN and ADS-B recovery policies intentionally distinct unless a later
    ADR changes that decision explicitly.

## Documentation Updates Required

- `ARCHITECTURE.md`: No change required.
- `CODING_RULES.md`: No change required.
- `PIPELINE.md`: Updated to describe the offline-wait seam and writer-vs-I/O
  lane split.
- `CONTRIBUTING.md`: No change required.
- `KNOWN_DEVIATIONS.md`: No change required unless future implementation leaves
  this boundary partially adopted.

## Rollback / Exit Strategy

- What can be reverted independently:
  - the injected connectivity seam
  - structured `connectionIssue` / `networkOnline` snapshot fields
  - the writer/I-O lane split
- What would trigger rollback:
  - proven live transport regression caused by the new concurrency boundary
  - inability to maintain one authoritative runtime mutation owner
- How this ADR is superseded or retired:
  - supersede it only if a later ADR deliberately changes the OGN runtime owner
    model or replaces the current writer-lane/offline-wait transport boundary
    with another explicit ownership design
