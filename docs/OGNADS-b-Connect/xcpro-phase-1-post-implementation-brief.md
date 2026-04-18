# XCPro Phase 1 — Post-Implementation Brief

## Status
Phase 1 looks **implemented in the right direction but not fully closed**.

Based on the implementation report, the code changes stayed inside the intended Phase 1 scope:
- OGN direct target tapping was restored.
- OGN zoom-band icon sizing and label visibility remain restyle-only.
- Cluster-only production files were removed.
- The watched-aircraft overlay now reapplies after style recreation.
- Supporting runtime/config seams were kept narrow.
- Unit tests for the touched slice passed.

That is the right shape for this phase.

## What Phase 1 appears to have achieved
### OGN traffic
- Zoom-band behavior is still style-driven rather than source-regeneration driven.
- Icon sizing and label gating are being handled in the overlay/style support layer.
- Direct tap-selection was restored after the earlier OGN path drift.

### Live Follow watched-aircraft overlay
- The overlay now reapplies after style recreation.
- A style-loaded seam was added so the watched-aircraft overlay can recover without waiting for unrelated runtime events.

### Scope control
- No clustering rollout was kept in production.
- No broader architecture rewrite was done.
- No ranking / MAX_TARGETS / spiderfy redesign was introduced.

## What is verified already
Passed checks reported:
- `:feature:traffic:testDebugUnitTest`
- `:feature:map:testDebugUnitTest --tests "com.trust3.xcpro.map.LiveFollowWatchAircraftOverlayTest"`
- `enforceRules`

Updated tests reported:
- `MapOverlayManagerRuntimeOgnDelegateTargetTapTest.kt`
- `OgnTrafficOverlayFeatureTargetsTest.kt`
- `OgnTrafficOverlayFeatureLabelsTest.kt`
- `LiveFollowWatchAircraftOverlayTest.kt`

## What is **not** verified yet
Manual QA is still the real blocker.

Pending manual QA:
1. Dense OGN zoom in/out
2. Initial launch at wide zoom
3. Initial launch at close zoom
4. OGN icon-size setting change across zoom bands
5. Watched-aircraft zoom scaling across bands
6. Close-zoom OGN tap selection
7. Satellite / contrast icon mode scaling check

## Current assessment
This is **not ready to call complete** until the manual QA list is run.

Reason:
The implemented behavior is highly visual and interactive. Passing unit tests proves the plumbing is more stable, but it does **not** prove:
- the map feels uncluttered when zoomed out,
- OGN labels do not flash unexpectedly,
- watched-aircraft scale feels right on real map transitions,
- tap hit behavior still feels reliable on dense traffic,
- style recreation behaves correctly on real device flows.

## Highest-risk areas to inspect first
### 1. Tap-selection regression risk
Because target tap restoration required touching several runtime/overlay layers, verify that:
- direct taps select the intended glider,
- taps do not get stolen by ring/overlay layers,
- selection still works after zoom changes and style recreation.

### 2. Style recreation recovery risk
Because a new style-loaded seam was added for watched-aircraft recovery, verify that:
- the overlay always returns after style recreation,
- scale is correct immediately after recreation,
- no duplicate layers/sources are created,
- no visible flicker occurs.

### 3. Base-size vs rendered-size behavior
Because the feature depends on user-configured base icon size being preserved, verify that:
- settings changes affect all zoom bands proportionally,
- zoomed-out icons still shrink enough to reduce clutter,
- zoomed-in icons return to expected normal size.

## Recommended go / no-go rule
Use this rule:
- **Go to PR-ready hardening only if all manual QA items pass.**
- If any manual QA item fails, fix that before broader cleanup or Phase 2 work.

## What should happen next
1. Run the manual QA checklist on a real device or emulator with dense traffic scenarios.
2. Record pass/fail for each item with screenshots or short notes.
3. If all pass, run the heavier PR-ready gate.
4. Only after that, prepare the final Phase 1 merge summary.

## PR-ready completion criteria
Phase 1 can be called complete when all of the following are true:
- manual QA items all pass,
- no crash/regression appears around zoom changes,
- no style recreation regression remains,
- direct OGN tap-selection is confirmed working,
- watched-aircraft scaling is visually correct across zoom bands,
- test suite and repo rules are green.

## Phase 2 items still intentionally out of scope
Keep these out unless a proven bug forces them in:
- OGN clustering / expand-on-tap behavior
- spiderfy / regrouping redesign
- OGN ranking changes
- `MAX_TARGETS` changes
- ADS-B / OGN shared declutter-policy extraction
- broader overlay/runtime architecture rewrite

## Bottom line
The report suggests the implementation is **structurally on target**.
The only honest remaining blocker is **manual QA**.
Until that is done, Phase 1 is **implemented but not yet closed**.
