# Acceptance Criteria and Test Gates

## Authentication and identity acceptance criteria

- The app does not allow anonymous production use.
- A signed-in user with no active paid subscription receives a canonical `FREE` entitlement snapshot.
- The signed-in XCPro account identity is visible in at least one user-facing plan/account surface.
- Paid entitlements are attached to the correct signed-in XCPro account.
- Account mismatch does not silently grant access to the wrong XCPro account.
- The Google Play account is not treated as the XCPro account.

## Functional acceptance criteria

- Free users can use the baseline app **after signing in** without paid entitlements.
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
  - use IGC replay
  - use LiveFollow view/watch
  - use premium exports / advanced sharing
  - use LiveFollow broadcast/share
  - use Scia
  - use Hotspots
  - use advanced vario tuning / premium audio profiles
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
- XC unlocks all lower-tier capabilities plus:
  - IGC replay
  - LiveFollow view/watch
  - premium exports / advanced sharing
- Pro unlocks all subscription-gated XCPro capabilities, including:
  - LiveFollow broadcast/share
  - Scia
  - Hotspots
  - advanced vario tuning / premium audio profiles
- If Hotspots is backed by premium SkySight data, Pro alone is not enough; a valid linked paid SkySight account is also required.
- The UI never grants premium XCPro access solely from local assumptions after a fresh install.
- The UI never grants premium SkySight-backed access solely from stale linked-account assumptions after a fresh install.
- Restore purchases works on a second device using the same XCPro account.
- Subscription expiry revokes access correctly.
- Upgrade and downgrade transitions converge to the correct feature set.
- Same-tier monthly <-> annual transitions converge to the correct feature set and billing-period state.
- SkySight link / unlink / free / paid transitions converge to the correct feature set.
- Locked features show a consistent upgrade or link path instead of failing silently.
- Prices shown in the paywall come from store product details, not hardcoded strings.
- Manage subscription deep link exists.
- Restore path exists.

## Required tests

### Unit tests
- tier -> features mapping
- combined tier + SkySight account-state access policy
- free-signed-in entitlement mapping
- entitlement merge / refresh logic
- stale cache behavior
- upgrade / downgrade state transitions
- monthly -> annual state transitions
- annual -> monthly state transitions
- provider link-state transitions

### ViewModel tests
- paywall state rendering
- purchase in-flight / loading / error states
- restore purchase state
- entitlement refresh on app start / foreground
- signed-out -> sign-in routing state if applicable at the ViewModel layer
- SkySight link prompt visibility
- premium SkySight surface lock / unlock state

### Integration / device tests when relevant
- launch purchase flow
- cancel / resume app during purchase
- process death / restart with cached entitlement
- restore purchase on a different device or clean install
- same-tier monthly <-> annual switch
- cross-tier upgrade
- cross-tier downgrade
- wrong-account restore / account mismatch handling
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
- both monthly and annual paths are represented in verification evidence
- the XC tier matches the locked matrix in the Markdown contract files

## Authority rule

If any old test checklist or non-Markdown artifact conflicts with this file, this file wins.
