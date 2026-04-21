# Condor Documentation Pack

This pack describes the architecture and implementation plan for Condor as a live-source path.

## What changed in this update

This pack was refined by a second-pass seam review.

The earlier phased IP was close, but it still left key ownership and dependency contracts under-specified. This update locks those missing decisions so implementation does not pass functionally while weakening architecture integrity.

## Key decisions now made explicit

- `DesiredLiveMode` is persisted in `feature:profile` and consumed in runtime only through a port declared in `feature:flight-runtime`
- `PhoneLiveCapabilityPort` is declared in `feature:flight-runtime`, implemented at the platform edge, and kept narrower than map-local phone-health diagnostics
- `CondorLiveStatePort` is a read-only runtime-declared port implemented by `feature:simulator`
- `LiveSourceResolver` in `feature:flight-runtime` is the sole selector / policy owner
- `VarioRuntimeControlPort` remains the only public map/replay runtime-control seam
- `VarioRuntimeControlPort` stays caller-agnostic; runtime policy types do not leak into the map-facing API
- `ensureRunningIfPermitted()` is treated as a compatibility name whose semantic contract is "ensure the resolver-selected live runtime is active"
- `app` is a lifecycle, permission, and platform-edge capability adapter only; it does not decide active source policy
- `FlightDataRepository.Source` remains a `LIVE` vs `REPLAY` authority axis; Condor stays inside the selected live branch rather than adding a third repository source
- phone-device health remains a separate map-local diagnostic seam; it is not live-source truth
- user-facing live-source degraded states use typed reasons, not free-form strings
- Condor stream freshness uses injected Android monotonic receive time; payload UTC is not the freshness authority
- Condor bridge connection UX is part of v1 scope: first-run transport setup,
  persisted Bluetooth bridge selection, persisted TCP listen port, reconnect
  states, and minimum diagnostics must be specified
- bridge select / clear / connect / disconnect stays on a simulator-owned
  settings seam rather than `VarioRuntimeControlPort`, with `Connect` acting as
  the manual retry affordance
- Bluetooth and Wi-Fi/TCP share one Condor settings screen, but transport-
  specific operator docs remain separate
- `CONDOR2_FULL` ownship heading / track / marker truth comes from fused live `FlightDataRepository.flightData.gps`
- phone orientation may affect camera/display behavior only
- phone compass is never a Condor ownship fallback
- Condor disconnect must not silently revert to phone GPS
- replay remains authoritative while active and keeps replay-only binder ownership

## Document guide

- `01_CONDOR2_FULL_INTEGRATION_PHASED_IP_2026-04-18.md`
  - normative phased implementation plan
  - state contract, boundary contract, bypass replacements, heading authority, no-fallback, proof requirements

- `02_CONDOR2_BOUNDARIES_AND_SOURCE_ROUTING_2026-04-18.md`
  - seam rules and source-routing constraints
  - ownership matrix and bypass-removal matrix

- `05_CONDOR2_ARCHITECTURE_DECISION_DRAFT_2026-04-18.md`
  - durable architecture decisions to promote into an ADR

## Implementation guardrails

Any implementation claiming to satisfy this pack must prove:

- no `feature:flight-runtime -> feature:profile` back-edge
- no duplicate desired-mode owner
- no separate selection logic outside `LiveSourceResolver`
- no competing public runtime-control seam beside `VarioRuntimeControlPort`
- no third `FlightDataRepository.Source` value for Condor
- no mixed owner for user-facing live-source status and phone-device diagnostics
- no free-form degraded reason strings on the runtime status seam
- Condor ownship uses fused live `flightData.gps`
- phone orientation never becomes Condor ownship fallback
- replay remains authoritative while active
- Condor disconnect does not silently revert to phone GPS
