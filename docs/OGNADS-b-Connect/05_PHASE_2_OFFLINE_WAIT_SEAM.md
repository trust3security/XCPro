# Phase 2 — add an explicit offline wait seam for OGN

## Current seam evidence

- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt:90-127`  
  OGN retries via delay/backoff but has no explicit network wait.
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt:150-270`  
  OGN only learns about transport failure from connect/read/stall behavior.
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeNetworkWait.kt:24-99`  
  ADS-B already has the explicit offline-wait seam to mirror at the architecture level.

## Implementation prompt

Implement only the offline-wait seam for OGN.

1. Add an OGN-side network availability port similar in shape to ADS-B's:
   - flow/state for online status
   - current snapshot method for immediate checks
2. Add an OGN runtime helper that:
   - detects offline before connect attempts
   - pauses reconnect while offline
   - resumes promptly when online returns
3. Use this seam around:
   - connect attempts
   - reconnect delays / backoff windows
4. Do not introduce direct Android connectivity dependencies into the OGN runtime.
5. Keep the current UI wording simple unless the snapshot contract now supports a better reason cleanly.

## Code brief

- Preferred structure:
  - `ogn/domain/OgnNetworkAvailabilityPort.kt`
  - `OgnTrafficRepositoryRuntimeNetworkWait.kt`
- Keep network availability as a domain/data boundary, not a UI concern.
- If you need a default implementation for tests or convenience wiring, keep it explicit and non-authoritative.
- Preserve existing monotonic retry timing. Offline waiting is a transport gate, not a new wall-time policy.
- If a new offline constant/message is needed, keep it repository-owned.

## Files likely touched

- new: `feature/traffic/src/main/java/com/trust3/xcpro/ogn/domain/OgnNetworkAvailabilityPort.kt`
- new: `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeNetworkWait.kt`
- existing:
  - `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntime.kt`
  - `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt`
  - optional `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficModels.kt`
- tests:
  - `feature/traffic/src/test/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - optional new network-port fake under test source

## Tests to add or update

1. offline at start does not churn socket connects
2. offline during reconnect delay pauses instead of blind retrying
3. coming back online resumes promptly
4. transport error while actually online still uses normal retry behavior

## Exit criteria

- OGN makes no blind reconnect attempts while the port says offline
- reconnect resumes when online returns
- offline is distinguishable from other transport failures in runtime semantics
- tests prove offline wait and resume behavior

## Post-IP review brief

Review for these points:

1. The OGN runtime uses an injected port, not Android APIs directly.
2. Offline state suppresses socket churn.
3. Online transition wakes reconnect promptly.
4. New waiting logic does not break center/radius policy reconnects.
