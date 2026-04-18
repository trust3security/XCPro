# XCPro Phase 1 Hardening / Review Brief for Codex

You are doing a **narrow Phase 1 hardening and review pass** only.

Use these active briefs as the source of truth:
- `docs/OGNADS-b-Connect/xcpro-phase-1-post-implementation-brief.md`
- `docs/OGNADS-b-Connect/xcpro-phase-1-qa-hardening-brief-for-codex.md`

Do **not** use the archived copies under `docs/DECLUTTERMAP/archive/2026-03-29/` unless you need them only for historical comparison.

## Scope

Phase 1 is implemented but **not closed**.
The remaining blocker is **manual QA**.

Your job is to:
1. review the current implementation against the active briefs,
2. fix only narrow Phase 1 hardening issues if they are real,
3. run the targeted automated checks,
4. leave clear notes on what is still manual QA only.

This is **not** Phase 2 work.

## Important context you must preserve

There is an intentional behavioral difference between ADS-B and OGN reconnect recovery.
Do **not** “normalize” them in this pass.

### ADS-B recovery is intentionally conservative
ADS-B uses retry floors and a circuit-breaker style recovery pattern.
The current behavior includes:
- hard retry floors after failures,
- `maxOf(jitteredBackoff, retryFloor)` behavior on the network-error path,
- a 30 second circuit-breaker open window after repeated failures.

This means ADS-B taking roughly **15–30 seconds** to recover in some failure cases is **expected policy behavior**, not automatically a bug.

### OGN recovery is intentionally faster
OGN reconnects from a lower backoff and resumes as soon as connectivity returns.
That behavior is connectivity-driven and is also intentional.

### Therefore
In this pass:
- do **not** change ADS-B retry floors,
- do **not** change the ADS-B circuit-breaker/open-window behavior,
- do **not** change OGN reconnect policy just to make it look more like ADS-B,
- do **not** treat ADS-B slower recovery as a Phase 1 declutter bug unless you find a concrete defect that violates the existing policy.

## Phase 1 boundaries you must respect

Do not add or start any of the following:
- no OGN clustering,
- no expand-on-tap behavior,
- no spiderfy/regroup redesign,
- no OGN ranking changes,
- no `MAX_TARGETS` changes,
- no ADS-B / OGN shared declutter-policy extraction,
- no overlay/runtime architecture rewrite,
- no broad cleanup or unrelated refactors.

## Primary review targets

Focus only on these areas:

1. **OGN tap selection**
   - direct target taps must still work,
   - no regression in selected-target handling,
   - no regression caused by restyle-only zoom behavior.

2. **Zoom-band restyle-only behavior**
   - OGN zoom changes must stay restyle-only,
   - zoom changes must not cause unnecessary source rebuilds,
   - icon sizing must still follow the Phase 1 zoom-band logic,
   - label visibility must still follow the Phase 1 zoom-band logic.

3. **Watched-aircraft overlay style-recreation recovery**
   - watched-aircraft overlay must reapply correctly after style recreation,
   - initial render after style load must use the correct zoom-aware scaling,
   - no duplicate layer/source buildup.

4. **Base-size semantics**
   - user-configured OGN icon size remains the base size,
   - rendered size is derived from base size plus viewport multiplier,
   - rendered size must not be persisted back into settings,
   - changing the user base size must still propagate correctly across zoom bands.

5. **Runtime safety**
   - no crash if zoom updates arrive before overlays initialize,
   - no crash after style recreation,
   - no obvious null-lifecycle regressions,
   - no obvious reattachment regressions.

## Files most likely in scope

Review the current implementation in and around these files first:
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTargetRingOverlay.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntimeOgnDelegate.kt`
- `feature/map-runtime/src/main/java/com/trust3/xcpro/map/MapOverlayManagerRuntime.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapOverlayStack.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlaySupport.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayFeatureSupport.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/LiveFollowWatchAircraftOverlay.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapLiveFollowRuntimeLayer.kt`
- `feature/map/src/main/java/com/trust3/xcpro/map/ui/MapScreenContentRuntime.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/TrafficOverlayRuntimeState.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/map/OgnTrafficOverlayConfig.kt`

Also review the ADS-B / OGN connectivity code only to confirm the current behavior is intentional, not to change it:
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntime.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeLoopTransitions.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbPollingHealthPolicy.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/adsb/AdsbTrafficRepositoryRuntimeNetworkWait.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeConnectionPolicies.kt`
- `feature/traffic/src/main/java/com/trust3/xcpro/ogn/OgnTrafficRepositoryRuntimeNetworkWait.kt`

## What to do

### 1) Review first
Before changing code, review the current implementation against the active briefs.
Look for only these kinds of issues:
- narrow correctness bugs,
- style recreation regressions,
- lifecycle/null-safety issues,
- target-tap regressions,
- restyle-only violations,
- base-size semantic mistakes,
- missing tests for the Phase 1 behavior that is already intended.

### 2) Fix only real Phase 1 hardening issues
If you find a real issue, fix it with the **smallest possible change**.
Prefer local fixes over abstractions.
Do not broaden the design.

### 3) Add or adjust tests only where needed
Only add tests that directly cover Phase 1 behavior or a concrete regression you found.
Do not add speculative or broad architecture tests.

## Required automated verification

Run the smallest relevant checks for this pass.
At minimum, run the targeted tests that cover the Phase 1 surface area.
Use the most precise commands that match the actual repo/test layout.

Expected minimum coverage:
- relevant `:feature:traffic:testDebugUnitTest` coverage for the OGN slice,
- `:feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.LiveFollowWatchAircraftOverlayTest"`,
- `enforceRules`.

If you changed additional code outside those areas, run the smallest extra targeted checks needed to justify the change.
Do **not** jump straight to a broad expensive gate unless your fixes genuinely require it.

## Manual QA handling rules

Manual QA is still pending unless you actually execute it yourself.
Do **not** mark manual QA items as complete based on code inspection.

In your final report:
- every manual QA item must be explicitly marked either `DONE` or `NOT RUN`,
- default to `NOT RUN` unless you personally executed it.

Keep this checklist intact in the output:
- dense OGN zoom in/out
- initial launch at wide zoom
- initial launch at close zoom
- OGN icon-size setting change across bands
- watched-aircraft zoom scaling across bands
- close-zoom OGN tap selection
- satellite/contrast icon mode scaling check

## Acceptance standard for this pass

A successful hardening pass means:
- Phase 1 behavior remains intact,
- no Phase 2 scope is introduced,
- any real narrow defects found are fixed,
- targeted tests pass,
- manual QA status is reported honestly,
- ADS-B slower recovery is correctly documented as intentional policy behavior unless you find an actual bug.

## Output format required

Return a concise implementation report with these exact sections:

### Summary
- what you reviewed
- whether you changed code or found no code changes necessary

### Files Changed
- list every file changed
- one line per file saying why

### Tests Run
- exact commands/tests run
- pass/fail result for each

### Manual QA Status
- each required manual QA item marked `DONE` or `NOT RUN`

### Remaining Risks
- only real unresolved risks
- keep this short

### Phase 2 Items Explicitly Not Done
- clustering
- spiderfy / regroup redesign
- ranking changes
- `MAX_TARGETS` changes
- shared ADS-B / OGN policy refactor
- broader runtime / overlay rewrite

### Notes
- mention any deviations from the active briefs
- explicitly note that ADS-B conservative recovery remains intentional unless a concrete defect was identified

## Final instruction

Stay narrow.
Do not “improve” the system beyond Phase 1 hardening.
If the current code already matches the active briefs and passes the targeted checks, say so plainly and avoid unnecessary edits.
