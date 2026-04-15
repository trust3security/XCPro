# Implementation Phases

## Phase 0 — Planning and contract lock

Deliver:
- approved 5-tier matrix (`Free`, `Basic`, `Soaring`, `XC`, `Pro`)
- approved product IDs
- approved backend authority model
- approved external-provider access model
- approved change plan

Exit criteria:
- one SSOT owner defined for XCPro entitlements
- provider-linked state ownership is explicit
- file ownership plan written
- tests and docs plan written

## Phase 1 — Catalog and domain models

Deliver:
- `PlanTier`
- `AppFeature`
- `EntitlementState`
- `SkySightAccountState`
- `FeatureAccessPolicy`
- product catalog constants

Exit criteria:
- unit tests for tier / provider-state -> feature mapping
- no UI or `BillingClient` coupling in domain models

## Phase 2 — Billing and repository slice

Deliver:
- BillingClient adapter
- purchase launch path
- product details fetch path
- repository orchestration
- local XCPro entitlement cache schema

Exit criteria:
- happy-path purchase flow works in internal testing
- restore and refresh paths exist
- no direct UI access to `BillingClient`

## Phase 3 — Backend sync and authority

Deliver:
- verify purchase API call
- entitlement refresh call
- canonical server-backed read model
- linked-provider account-state refresh path
- stale / offline handling policy in code

Exit criteria:
- premium XCPro unlock happens only from verified XCPro state
- premium SkySight-backed unlock happens only from allowed plan + validated provider state
- downgrade / expiry is handled
- tests cover stale cache and refresh behavior

## Phase 4 — UI and feature gating

Deliver:
- upgrade screen
- paywall entry points
- provider-link entry points where needed
- route or action-level feature gating
- at least 1-2 representative paid features gated
- at least 1 representative dual-gated feature implemented

Exit criteria:
- Free users see consistent upgrade UX
- paid users access granted features
- provider-linked premium surfaces stay locked until provider state validates
- no premium logic is hidden in random composables

## Phase 5 — Hardening and rollout prep

Deliver:
- docs sync
- support / recovery notes
- analytics / observability hooks
- verification evidence

Exit criteria:
- `enforceRules`
- `testDebugUnitTest`
- `assembleDebug`
- optional device tests if required by actual runtime behavior
