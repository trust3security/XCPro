# Acceptance Criteria and Test Gates

## Functional acceptance criteria

- Free users can use the baseline app without paid entitlements
- Soar unlocks Soar-only features and nothing above it
- XC unlocks XC and Soar features and nothing above Pro-only capabilities
- Pro unlocks all subscription-gated features
- The UI never grants premium access solely from local assumptions after a fresh install
- Restore purchases works on a second device using the same account
- Subscription expiry revokes access correctly
- Upgrade and downgrade transitions converge to the correct feature set
- Locked features show a consistent upgrade path instead of failing silently
- Prices shown in the paywall come from store product details, not hardcoded strings

## Required tests

### Unit tests
- tier -> features mapping
- feature access policy
- entitlement merge/refresh logic
- stale cache behavior
- upgrade/downgrade state transitions

### ViewModel tests
- paywall state rendering
- purchase in-flight/loading/error states
- restore purchase state
- entitlement refresh on app start / foreground

### Integration / device tests when relevant
- launch purchase flow
- cancel / resume app during purchase
- process death / restart with cached entitlement
- restore purchase on a different device or clean install

## Required repo commands

```bash
./gradlew enforceRules
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Run connected tests only when runtime/device behavior changes make them necessary.

## Release gate

Do not merge until:
- the plan matches the code
- tests match the actual behavior
- docs are updated
- the post-implementation drift audit passes
