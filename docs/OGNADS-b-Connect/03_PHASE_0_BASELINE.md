# Phase 0 — baseline, guardrails, and scaffolding

## Current seam evidence

- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntime.kt:44-149`  
  OGN owns runtime state today and mutates it from several contexts.
- `feature/traffic/src/main/java/com/example/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt:90-140`  
  Current reconnect loop structure.
- `feature/traffic/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt:55, 87, 265`  
  Existing coverage already locks policy reconnect and stall-to-error, but not repeated EOF or offline wait.

## Implementation prompt

Implement only the baseline phase. Do not change production behavior yet.

1. Add the working change plan doc to the repo using `02_CHANGE_PLAN_OGN_RECONNECT_HARDENING.md` as the source.
2. Inventory every exhaustive `when` over `OgnConnectionState` and every direct construction/read of `OgnTrafficSnapshot` that could break when OGN connection semantics are extended.
3. Add or refactor test helpers only if they reduce risk for later phases:
   - scripted socket that can emit clean EOF repeatedly
   - optional fake network availability port in test source
4. Keep this phase behavior-neutral. No user-visible semantic changes.

## Code brief

- Owner remains `OgnTrafficRepositoryRuntime`.
- Use this phase to make later phases smaller, not smarter.
- If you create helpers, keep them test-only.
- If you discover a missing reference file or exhaustive `when`, write it down in the phase summary.

## Files likely touched

- `docs/ARCHITECTURE/...` only if the repo expects the plan to live there
- `feature/traffic/src/test/java/com/example/xcpro/ogn/OgnTrafficRepositoryConnectionTest.kt`
- optional new test helper files under `feature/traffic/src/test/java/com/example/xcpro/ogn/`
- optional new UI model test file path if missing:
  - `feature/traffic/src/test/java/com/example/xcpro/map/ui/MapTrafficConnectionIndicatorModelTest.kt`

## Tests to add or prep

- helper coverage for repeated EOF sequencing
- helper coverage for deterministic backoff timing
- helper coverage for fake online/offline state

## Exit criteria

- change plan is in place
- future touch points are identified
- no production behavior change
- `./gradlew enforceRules` passes

## Post-IP review brief

Review for these points:

1. No production reconnect semantics changed.
2. Any new helper lives in test source only.
3. The phase summary names all likely exhaustive `when` / snapshot touch points that later phases must revisit.
4. Verification output is real, not claimed.
