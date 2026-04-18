# Implementation Phases

## Phase 0 - Contract lock and repo-facing doc sync

### Deliver
- approved 5-tier matrix (`Free`, `Basic`, `Soaring`, `XC`, `Pro`)
- approved product IDs
- approved base-plan model (`monthly`, `annual`)
- approved account-required rule for all tiers
- approved backend authority model
- approved external-provider access model
- approved change plan
- docs updated so Markdown files are the authority

### Exit criteria
- one SSOT owner defined for XCPro entitlements
- provider-linked state ownership is explicit
- auth/session ownership is explicit
- file ownership plan written
- tests and docs plan written
- non-Markdown conflicts are explicitly subordinated to Markdown contract files

## Phase 1 - Auth and identity enforcement alignment

### Deliver
- root "account required for all tiers" rule aligned with the repo's auth lane
- explicit signed-out -> sign-in route
- explicit signed-in + no paid subscription -> `FREE`
- clear account identity display surface for profile / settings / plan screen
- purchase flow precondition: signed-in XCPro account required

### Exit criteria
- no anonymous production flow remains
- signed-in free user path is explicit
- wrong-account purchase attach risk is handled, not ignored
- tests exist for sign-in required behavior

## Phase 2 - Catalog and domain models

### Deliver
- `PlanTier`
- `BillingPeriod`
- `SubscriptionStatus`
- `AppFeature`
- `EntitlementSnapshot`
- `SkySightAccountState`
- `FeatureAccessPolicy`
- product catalog constants

### Exit criteria
- unit tests for tier / provider-state -> feature mapping
- unit tests for free-signed-in state
- no UI or `BillingClient` coupling in domain models

## Phase 3 - Backend authority and lifecycle handling

### Deliver
- purchase sync endpoint integration contract
- entitlement read endpoint contract
- RTDN ingestion contract
- canonical server-backed read model
- linked-provider account-state refresh path
- stale / offline handling policy in code and docs
- token-chain / replacement handling for plan changes

### Exit criteria
- premium XCPro unlock happens only from verified XCPro state
- premium SkySight-backed unlock happens only from allowed plan + validated provider state
- free signed-in users receive canonical `FREE` entitlement state
- downgrade / expiry / hold / revoke is handled
- tests cover stale cache and refresh behavior

## Phase 4 - Android billing and repository slice

### Deliver
- BillingClient adapter
- product-details fetch path
- purchase launch path
- same-tier monthly/annual switch support
- cross-tier upgrade/downgrade launch support
- repository orchestration
- local XCPro entitlement cache schema
- restore and manage-subscription flows

### Exit criteria
- happy-path purchase flow works in internal testing
- restore and refresh paths exist
- no direct UI access to `BillingClient`
- billing flow is blocked when no XCPro account is signed in
- obfuscated account identity is used

## Phase 5 - UI and feature gating

### Deliver
- plan / upgrade screen
- paywall entry points
- provider-link entry points where needed
- route or action-level feature gating
- at least 1-2 representative paid features gated
- at least 1 representative dual-gated feature implemented
- settings/profile plan surface showing current signed-in account

### Exit criteria
- Free users see consistent upgrade UX after sign-in
- paid users access granted features
- provider-linked premium surfaces stay locked until provider state validates
- no premium logic is hidden in random composables
- current account, plan, and renewal/expiry information is visible where relevant

## Phase 6 - Hardening and rollout prep

### Deliver
- docs sync
- support / recovery notes
- analytics / observability hooks
- verification evidence
- internal-test rollout checklist
- license tester and Play Billing Lab checklist

### Exit criteria
- `enforceRules`
- `testDebugUnitTest`
- `assembleDebug`
- optional device tests if required by actual runtime behavior
- post-implementation audit passes
- monthly and annual paths are both covered in test evidence

## Authority rule

If any old non-Markdown phase plan conflicts with this file, this file wins.
