# ADR_CONDOR_BRIDGE_RUNTIME_BOUNDARY_2026-04-18.md

## Metadata

- Title: Condor Bridge Runtime Boundary
- Date: 2026-04-18
- Status: Accepted
- Owner: XCPro engineering
- Reviewers: TBD
- Related issue/PR: TBD
- Related change plan: `docs/CONDOR/01_CONDOR2_FULL_INTEGRATION_PHASED_IP_2026-04-18.md`
- Supersedes: None
- Superseded by: None

## Context

- Problem:
  XCPro needs a concrete Condor runtime slice that lets a user select a
  transport, persist Bluetooth bridge and TCP listener settings, connect,
  disconnect, and diagnose Condor from one screen, with `Connect` serving as
  the manual retry action.
- Why now:
  Condor 2 support needs both the existing Bluetooth bridge path and an
  XCSoar-compatible TCP listener path without weakening runtime ownership.
- Constraints:
  Preserve MVVM + UDF + SSOT, keep replay deterministic, avoid hidden runtime
  owners, keep downstream consumers transport-agnostic, and reuse the existing
  Bluetooth settings flow where practical.
- Existing rule/doc references:
  `ARCHITECTURE.md`, `CODING_RULES.md`, `PIPELINE.md`,
  `docs/CONDOR/02_CONDOR2_BOUNDARIES_AND_SOURCE_ROUTING_2026-04-18.md`

## Decision

Condor runtime ownership is split as follows:

- `:feature:bluetooth-devices` owns generic Bluetooth device transport,
  connect-permission seams, and bonded-device models shared by simulator and
  variometer features.
- `:feature:flight-runtime` owns the read-only Condor runtime seam
  `CondorLiveStatePort` and the cross-module models needed to expose Condor
  session state and selected transport kind.
- `:feature:simulator` owns Condor transport selection, persisted Bluetooth
  bridge selection, persisted TCP listen port, active-session state, reconnect
  policy, diagnostics, and all Condor mutations through `CondorBridgeControlPort`.
- `:feature:profile` owns Condor settings projection and rendering only.
- `:app` owns General Settings navigation wiring only.

This slice keeps Bluetooth and TCP listener setup inside one simulator-owned
runtime owner while preserving transport-blind downstream behavior. Condor live
samples feed the selected live runtime path, and `LiveSourceResolver` continues
to expose Condor as one simulator source instead of two separate runtime modes.

Dependency direction impact:

- UI depends on simulator control/read ports through profile.
- Simulator depends on shared Bluetooth transport and on the read-only
  flight-runtime contract.
- Flight-runtime does not depend on simulator implementation details.

API/module surface impact:

- New module: `:feature:bluetooth-devices`
- New module: `:feature:simulator`
- New cross-module read seam: `CondorLiveStatePort`
- New simulator control seam: `CondorBridgeControlPort`
- New simulator-owned transport adapters: Bluetooth bridge runtime and TCP
  listener runtime

Time-base/determinism impact:

- Condor bridge freshness uses injected monotonic time via `Clock.nowMonoMs()`.
- No wall-clock authority is introduced into the runtime bridge owner.
- This slice does not alter replay or fused-flight determinism paths.

Concurrency/buffering/cadence impact:

- Simulator-owned transport runtimes own Bluetooth session collection, TCP
  listener accept/read loops, sentence framing, and reconnect sequencing behind
  simulator-owned coroutine scopes and a mutex.
- Reconnect policy is capped and simulator-owned in this slice with fixed
  backoff delays of 1s, 2s, and 5s.
- UI reads state flows only and does not own retry timing or connection policy.
- `Connect` is the only user-triggered start / retry action; waiting or
  exhausted reconnect remain simulator-owned state rather than a separate
  public retry command. Transport switching disconnects the active session
  before changing the selected transport.

## Alternatives Considered

| Option | Why Considered | Why Rejected / Not Chosen |
|---|---|---|
| Reuse variometer Bluetooth runtime directly for Condor | Existing Bluetooth code already worked | Would leak LX/variometer ownership into simulator runtime and blur feature boundaries |
| Split Bluetooth and TCP into separate screens/controllers | Fastest way to add TCP setup | Would duplicate runtime ownership, increase churn, and weaken the single-source Condor boundary |
| Put Condor selection state in UI/ViewModel | Fastest path for a settings screen | Violates ownership rules; persisted bridge selection and reconnect policy are runtime concerns |

## Consequences

### Benefits
- Condor bridge lifecycle now has a single simulator owner across Bluetooth and TCP.
- Downstream live-source, map, and fusion code stays transport-agnostic.
- Settings UI can expose meaningful diagnostics without becoming a runtime
  authority.

### Costs
- Adds TCP listener/network-info adapters and transport-preference persistence.
- Richer Condor sentence support still requires a later parser expansion slice.

### Risks
- Shared Bluetooth module changes affect variometer imports and tests.
- Reconnect policy is intentionally simple for this slice and may need tuning
  once broader device/runtime evidence is collected.

## Validation

- Tests/evidence required:
  `enforceRules`, root `testDebugUnitTest`, `assembleDebug`, focused simulator
  and profile unit tests for transport selection, bridge persistence, TCP
  listener reconnect states, and settings mapping.
- SLO or latency impact:
  No new UI-frame or map-path SLO impact in this slice. Runtime cadence is
  limited to Bluetooth chunk reads and a 1s freshness ticker inside simulator.
- Rollout/monitoring notes:
  Use the Condor settings surface as the initial operator diagnostics entrypoint
  for transport selection, bridge/TCP settings, connection state, reconnect
  state, and last failure.

## Documentation Updates Required

- `ARCHITECTURE.md`: No change
- `CODING_RULES.md`: No change
- `PIPELINE.md`: No change in this slice
- `CONTRIBUTING.md`: No change
- `KNOWN_DEVIATIONS.md`: No change

## Rollback / Exit Strategy

- What can be reverted independently:
  The Condor settings UI, simulator runtime owner, and TCP listener adapter can
  be reverted without touching fused-flight data contracts.
- What would trigger rollback:
  Variometer regressions from the shared Bluetooth extraction or unstable Condor
  reconnect behavior.
- How this ADR is superseded or retired:
  Supersede it when Condor sentence parsing and live-source resolution are
  promoted into the next accepted runtime/fusion ADR.
