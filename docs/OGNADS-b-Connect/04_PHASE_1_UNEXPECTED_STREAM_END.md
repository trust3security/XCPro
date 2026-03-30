# Phase 1 — unexpected stream end becomes visible and backs off correctly

## Current seam evidence

- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt:90-127`  
  `runConnectionLoop()` resets `backoffMs` after any non-throwing `connectAndRead()` return.
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt:150-270`  
  `connectAndRead()` returns `ConnectionExitReason.StreamEnded` on `reader.readLine() == null`.
- `feature/traffic/src/main/java/com/example/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt:60-76`  
  OGN loss is shown only for `OgnConnectionState.ERROR`.

## Implementation prompt

Implement only the unexpected stream-end fix.

Use the smallest safe semantic change:

1. Stop treating clean socket EOF as a normal/disconnected return.
2. Make unexpected stream end flow through the same degraded/retry path as other transport failures so backoff does not reset to the minimum after every EOF.
3. Preserve the existing immediate reconnect path for intentional policy reconnects:
   - center moved enough to require a new filter
   - receive radius changed
4. Do not redesign the whole OGN state model in this phase unless compile pressure forces it.

Recommended approach:
- remove `StreamEnded` as a normal `ConnectionExitReason`
- throw a dedicated exception or `EOFException` for unexpected `readLine() == null`
- let `runConnectionLoop()` treat it as an error path that preserves backoff escalation and visible degraded state

## Code brief

- Keep intentional reconnects as the only non-error normal return from `connectAndRead()`.
- Do not add UI-owned heuristics for "connection lost". If UI changes are needed, they should only map repository snapshot semantics.
- If you add a custom exception class, keep it local to the OGN runtime package and name it for transport semantics, not UI wording.
- Do not over-rotate into structured telemetry yet. Phase 4 can clean that up.

## Files likely touched

- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntime.kt`
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt`
- optional:
  - `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficModels.kt`
  - `feature/traffic/src/main/java/com/example/xcpro/map/ui/MapTrafficConnectionIndicatorModel.kt`
- tests:
  - `feature/traffic/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
  - optional `feature/traffic/src/test/java/com/example/xcpro/map/ui/MapTrafficConnectionIndicatorModelTest.kt`

## Tests to add or update

1. repeated clean EOF escalates backoff instead of sticking at 1 second
2. repeated clean EOF results in a visible degraded/lost state
3. intentional center/radius reconnect still bypasses alarming loss UI and still resets policy state correctly

## Exit criteria

- unexpected EOF no longer returns through the "normal disconnect" path
- repeated EOF does not keep `reconnectBackoffMs` pinned to the start value
- OGN degraded state is visible during retry after EOF
- existing policy reconnect behavior still works

## Post-IP review brief

Review for these points:

1. `readLine() == null` no longer drives `DISCONNECTED` as the user-visible state.
2. `backoffMs` resets only for intentional reconnect policy, not for unexpected EOF.
3. Intentional reconnect tests still pass.
4. No UI-only workaround was introduced to mask broken repository semantics.
