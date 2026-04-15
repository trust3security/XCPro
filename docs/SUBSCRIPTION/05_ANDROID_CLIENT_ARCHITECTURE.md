# Android Client Architecture

## Recommended module shape

Start with one dedicated billing/entitlement core module. Expand only if growth demands it.

### Preferred new module

- `:core:billing`

Optional later:
- `:feature:paywall`

## Ownership model

### `:core:billing`
Owns:
- Play Billing adapter / wrapper
- product catalog mapping
- purchase sync coordination
- XCPro entitlement state models
- feature access policy
- repositories and use cases for subscription-state observation and refresh

### existing SkySight integration slice
Owns:
- SkySight API / auth client
- linked-account verification flow
- narrow read model that exposes `SkySightAccountState` to the access policy seam

### app/root layer
Owns:
- app startup refresh trigger
- top-level state collection
- navigation decisions
- upgrade entry point

### UI/paywall layer
Owns:
- plan cards
- feature comparison display
- purchase button intents
- restore CTA
- provider-link CTA surfaces when allowed by plan

### backend
Owns:
- purchase verification
- lifecycle updates
- canonical entitlement grant / revoke decisions
- canonical provider-linked state needed for dual-gated features

## Recommended file responsibilities

- `PlanTier.kt` -> display-neutral tier model
- `AppFeature.kt` -> stable capability list
- `EntitlementState.kt` -> canonical XCPro plan read model for the app
- `SkySightAccountState.kt` -> narrow provider account-state model
- `FeatureAccessPolicy.kt` -> pure mapping from `PlanTier` + provider account state to capabilities
- `BillingCatalog.kt` -> product IDs / base plans / offers
- `PlayBillingClientAdapter.kt` -> Play Billing wrapper only
- `SubscriptionRepository.kt` -> client orchestration, purchase sync, XCPro entitlement cache
- `ObserveEntitlementsUseCase.kt` -> UI read path for XCPro plan state
- `ObserveAccessContextUseCase.kt` -> combined read path when screens need both plan state and provider-linked state
- `RefreshEntitlementsUseCase.kt` -> explicit refresh trigger
- `PurchaseSubscriptionUseCase.kt` -> purchase flow orchestration
- `BillingViewModel.kt` -> screen and top-level billing state
- `UpgradeScreen.kt` -> paywall UI

## Data flow

```text
Google Play BillingClient
    -> PlayBillingClientAdapter
    -> Purchase sync API client
    -> SubscriptionRepository
    -> ObserveEntitlementsUseCase
        \
         -> ObserveAccessContextUseCase
        /
SkySight auth / account-state lane
    -> SkySight integration repository
    -> ObserveAccessContextUseCase

ObserveAccessContextUseCase
    -> BillingViewModel
    -> MainActivityScreen / AppNavGraph / feature screens
```

## Hard rules

- no direct `BillingClient` calls in composables
- no feature-access calculations hidden in random screens
- no duplicated entitlement state in multiple ViewModels
- no price formatting logic mixed with entitlement enforcement
- local DataStore can cache, but cannot become the source of truth
- do not let `:core:billing` absorb unrelated SkySight networking ownership just because both affect gating
- provider-linked access checks must still go through the same central policy seam
