# Condor 2 Architecture Decision Draft

## Purpose

This is an ADR-ready draft kept with the Condor planning pack. When
implementation begins, this decision should be promoted into
`docs/ARCHITECTURE/ADR_...`.

## Decision

XCPro will implement Condor 2 as a selected live simulator source, not as a
replay variant and not as a map-owned special case.

## Durable boundary choices

### Ownership

- `feature:simulator` owns Condor transport, parser runtime, stream health, and
  sample mapping.
- `feature:flight-runtime` owns narrow source-selection seams and effective
  live-source resolution.
- `feature:profile` owns persisted desired live mode.
- `feature:map` consumes fused flight data and renders map status only.

### Dependency direction

- UI consumes use cases and mapped state only.
- runtime selection depends on ports, not concrete map or UI classes.
- `feature:simulator` must not depend on `feature:map`.
- `feature:flight-runtime` must not depend on `feature:simulator`
  implementations directly; it depends on ports.

### Source routing

- Condor ownship must enter `FlightDataRepository` through the existing live
  fused path.
- replay binders remain replay-only.
- no second flight-data cache or simulator-only map state is allowed.

### Heading authority

- in `CONDOR2_FULL`, ownship truth on the map comes from Condor-fed fused
  flight data
- phone orientation may remain a camera/display concern only if it does not
  replace ownship truth

### Side effects

- runtime exposes source classification
- IGC and WeGlide keep their own upload/prompt policy
- simulator sessions are non-uploadable by default

## Explicitly rejected options

- reusing replay location binders for Condor
- keeping full Condor runtime under `feature:variometer`
- storing desired live mode inside runtime orchestration
- using one broad selected-runtime dependency bag
- silent fallback from Condor to phone GPS

## Why this decision is needed

Without this cut:

- Condor support would likely work by bypassing stable seams
- replay and live paths would blur
- phone-only assumptions would survive in live startup and status
- `feature:flight-runtime` would risk becoming a broad residual bucket

## Validation expectations

- source-selection unit tests
- simulator parser/runtime tests
- map ownship routing integration proof
- replay restore proof
- no-upload simulator policy proof

## Practical takeaway

The durable design is:

```text
user-selected mode
-> effective live-source resolver
-> selected live source seams
-> existing fused live flight-data path
-> existing map consumers
```

The durable design is not:

```text
Condor special case
-> map bypass
-> replay-like binder
-> hidden fallback
```
