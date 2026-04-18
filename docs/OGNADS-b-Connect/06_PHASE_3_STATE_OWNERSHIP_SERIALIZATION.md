# Phase 3 — serialize OGN runtime state ownership safely

## Current seam evidence

- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntime.kt:44-149`  
  OGN uses `CoroutineScope(SupervisorJob() + dispatcher)` on the raw injected dispatcher and keeps many mutable fields in the runtime.
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntime.kt:154-245`  
  multiple collectors and public entry points can mutate runtime state.
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeDomainPolicies.kt:235-360`  
  additional runtime state mutation and async DDB refresh work.
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt:20-55`  
  ADS-B uses a serialized writer dispatcher, but its transport path is structurally different.

## Implementation prompt

Implement state-ownership serialization for OGN, but do not blindly move the whole blocking socket loop onto a single-thread writer dispatcher.

1. Establish one authoritative mutation path for OGN runtime state.
2. Route public entry points and collector-driven state changes through that path.
3. Keep blocking socket I/O as an event producer, not the only lane that can mutate shared state.
4. Keep snapshot publication inside the authoritative owner path.
5. Reduce direct caller-thread writes to shared runtime fields.
6. Split files by responsibility if the runtime becomes too mixed or too large.

Preferred architecture:
- a serialized reducer / actor / writer lane for authoritative state mutation
- socket loop and DDB refresh work emit events/results back to that lane
- snapshot building happens on the authoritative lane

Acceptable time-boxed fallback if the preferred architecture is too large:
- a single explicit mutation mutex guarding all authoritative state writes and snapshot composition,
- but only if no mutable authoritative fields remain writable outside that guard.

## Code brief

- The important goal is ownership, not "use `limitedParallelism(1)` everywhere".
- `readLine()` is blocking and already uses socket timeouts. If you serialize that blocking loop with all state ownership, responsiveness can get worse.
- If you introduce a reducer/actor, keep it repository-private.
- If file growth gets messy, split by responsibility:
  - runtime constructor/entrypoints
  - connection policy / socket loop
  - mutation/reducer helpers
  - network wait helper
- Keep `Clock` usage unchanged and explicit.

## Files likely touched

- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntime.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeDomainPolicies.kt`
- possible new focused files:
  - `OgnTrafficRepositoryRuntimeMutations.kt`
  - `OgnTrafficRepositoryRuntimeReducer.kt`
- tests:
  - `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - optional new ordering/contention tests

## Tests to add or update

1. rapid center updates while connected do not produce inconsistent snapshot state
2. radius changes during active loop remain deterministic
3. repeated preference updates do not race snapshot composition
4. DDB refresh completion cannot clobber unrelated runtime state

## Exit criteria

- authoritative OGN runtime state has one clear mutation path
- public entry points no longer mutate shared state ad hoc
- blocking socket I/O is not forced onto the same writer lane as all authoritative state
- snapshot composition is deterministic under churn tests

## Post-IP review brief

Review for these points:

1. There is one obvious authoritative state mutation path.
2. No remaining caller-thread direct writes to authoritative state survive outside the chosen owner path.
3. Socket blocking I/O is not accidentally serialized with all state ownership.
4. Snapshot publication cannot interleave with arbitrary uncontrolled writes anymore.
