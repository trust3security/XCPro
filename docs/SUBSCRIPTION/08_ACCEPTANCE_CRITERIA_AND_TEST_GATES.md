# Acceptance Criteria and Test Gates

## Functional acceptance criteria

- Free users can use the baseline app without paid entitlements.
- Free users can:
  - access airspace
  - select a home waypoint / direct-to-home only
  - select flight mode screens
  - use Essentials cards only
- Free users cannot:
  - add / create / edit tasks
  - use Distance Circles
  - use ADS-B
  - use RainViewer
  - use WeGlide
  - use OGN
  - enter SkySight credentials
  - use Scia
  - use Hotspots
- Basic unlocks:
  - Distance Circles
  - ADS-B
  - RainViewer
  - WeGlide
  - SkySight basic/free surfaces
  - and nothing above Soaring / XC / Pro-only capabilities
- Soaring unlocks:
  - add / create / edit Task
  - OGN
  - SkySight credential entry / account linking
  - SkySight premium surfaces only when a valid linked paid SkySight account exists
- XC unlocks all lower-tier capabilities plus the approved XC-specific bundle from the final feature matrix.
- Pro unlocks all subscription-gated XCPro capabilities, including Scia and Hotspots.
- If Hotspots is backed by premium SkySight data, Pro alone is not enough; a valid linked paid SkySight account is also required.
- The UI never grants premium XCPro access solely from local assumptions after a fresh install.
- The UI never grants premium SkySight-backed access solely from stale linked-account assumptions after a fresh install.
- Restore purchases works on a second device using the same account.
- Subscription expiry revokes access correctly.
- Upgrade and downgrade transitions converge to the correct feature set.
- SkySight link / unlink / free / paid transitions converge to the correct feature set.
- Locked features show a consistent upgrade or link path instead of failing silently.
- Prices shown in the paywall come from store product details, not hardcoded strings.

## Required tests

### Unit tests
- tier -> features mapping
- combined tier + SkySight account-state access policy
- entitlement merge / refresh logic
- stale cache behavior
- upgrade / downgrade state transitions
- provider link-state transitions

### ViewModel tests
- paywall state rendering
- purchase in-flight / loading / error states
- restore purchase state
- entitlement refresh on app start / foreground
- SkySight link prompt visibility
- premium SkySight surface lock / unlock state

### Integration / device tests when relevant
- launch purchase flow
- cancel / resume app during purchase
- process death / restart with cached entitlement
- restore purchase on a different device or clean install
- SkySight link flow with:
  - unlinked account
  - linked free account
  - linked paid account
  - link failure
  - unlink flow

## Required repo commands

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run connected tests only when runtime / device behavior changes make them necessary.

## Release gate

Do not merge until:
- the plan matches the code
- tests match the actual behavior
- docs are updated
- the post-implementation drift audit passes
- the XC tier has a clearly approved bundle, or the launch scope explicitly removes that tier
