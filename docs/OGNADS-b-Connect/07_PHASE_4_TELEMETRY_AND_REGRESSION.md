# Phase 4 — structured telemetry, UI semantics, and regression hardening

## Current seam evidence

- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficModels.kt:12-34`  
  current OGN snapshot contract is small and coarse.
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeDomainPolicies.kt:386-389`  
  `sanitizeError()` collapses failure information to throwable simple name.
- `feature/traffic/src/main/java/com/example/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt:60-76`  
  map UI semantics for OGN are minimal.
- `feature/traffic/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt:55, 87, 265`  
  existing tests cover policy reconnect and stall error, but not the full degraded-state contract.

## Implementation prompt

Implement the final telemetry and regression pass.

1. Replace coarse failure labeling with a structured OGN connection issue/reason contract if earlier phases did not already do it.
2. Make snapshot semantics clear enough that support and UI can tell the difference between:
   - intentional reconnect
   - unexpected EOF / transport loss
   - offline wait
   - login unverified
   - stall timeout
3. Keep UI logic read-only. UI should map snapshot semantics, not infer transport policy.
4. Expand regression coverage to lock the degraded-state contract down directly.

## Code brief

- Prefer structured reason enums or sealed types over parsing `lastError` strings in UI.
- Keep the user-facing text simple unless product wants different copy.
- It is fine for the UI to keep a generic "OGN connection lost" message, as long as the snapshot carries enough structured detail for tests and support.
- If you add new snapshot fields, update direct constructors/tests carefully.
- Add direct indicator-model tests; do not rely only on runtime tests to prove UI semantics.

## Files likely touched

- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficModels.kt`
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeDomainPolicies.kt`
- `feature/traffic/src/main/java/com/example/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt`
- optional:
  - `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt`
- tests:
  - `feature/traffic/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - `feature/traffic/src/test/java/com/example/xcpro/map/ui/MapTrafficConnectionIndicatorModelTest.kt`

## Tests to add or update

1. unexpected EOF reason is structured and visible
2. offline wait reason is structured and visible
3. login unverified reason is structured and visible
4. intentional reconnect does not misreport as failure
5. indicator model maps each supported degraded state correctly
6. existing stall regression remains green

## Exit criteria

- OGN degraded-state semantics are explicit and structured
- UI mapping is direct and tested
- no remaining critical reconnect behavior depends on parsing throwable class names
- final verification passes

## Post-IP review brief

Review for these points:

1. Snapshot semantics are explicit enough for support and UI without UI-side guesswork.
2. Intentional reconnect and real failure are distinct in the contract.
3. The regression suite covers EOF, offline wait, stall, auth, and policy reconnect.
4. No new architecture drift was introduced while improving telemetry.
