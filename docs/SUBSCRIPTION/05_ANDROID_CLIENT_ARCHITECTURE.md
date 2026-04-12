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
- Play Billing adapter/wrapper
- product catalog mapping
- purchase sync coordination
- entitlement state models
- repository interfaces and implementations
- feature access policy

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

### backend
Owns:
- purchase verification
- lifecycle updates
- canonical entitlement grant/revoke decisions

## Recommended file responsibilities

- `PlanTier.kt` -> display-neutral tier model
- `AppFeature.kt` -> stable capability list
- `EntitlementState.kt` -> canonical read model for the app
- `FeatureAccessPolicy.kt` -> pure mapping tier -> features
- `BillingCatalog.kt` -> product IDs/base plans/offers
- `PlayBillingClientAdapter.kt` -> Play Billing wrapper only
- `SubscriptionRepository.kt` -> client orchestration, purchase sync, cache
- `ObserveEntitlementsUseCase.kt` -> UI read path
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
    -> BillingViewModel
    -> MainActivityScreen / AppNavGraph / feature screens
```

## Hard rules

- no direct `BillingClient` calls in composables
- no feature-access calculations hidden in random screens
- no duplicated entitlement state in multiple ViewModels
- no price formatting logic mixed with entitlement enforcement
- local DataStore can cache, but cannot become the source of truth
