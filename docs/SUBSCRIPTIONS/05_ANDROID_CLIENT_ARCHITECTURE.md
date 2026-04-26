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

### existing auth slice
Owns:
- email/password sign-in flow or the repo's existing equivalent authenticated XCPro identity path
- account session state
- sign-in / sign-out behavior
- root "account required" decision before normal app use

### existing SkySight integration slice
Owns:
- SkySight API / auth client
- linked-account verification flow
- narrow read model that exposes `SkySightAccountState` to the access policy seam

### app/root layer
Owns:
- auth-required root gate
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
- manage-subscription CTA
- provider-link CTA surfaces when allowed by plan

### backend
Owns:
- purchase verification
- lifecycle updates
- canonical entitlement grant / revoke decisions
- canonical provider-linked state needed for dual-gated features

## Recommended file responsibilities

- `PlanTier.kt` -> display-neutral tier model
- `BillingPeriod.kt` -> display-neutral billing-period model
- `AppFeature.kt` -> stable capability list
- `EntitlementSnapshot.kt` -> canonical XCPro plan read model for the app
- `SkySightAccountState.kt` -> narrow provider account-state model
- `FeatureAccessPolicy.kt` -> pure mapping from `PlanTier` + provider account/config state to capabilities
- `BillingCatalog.kt` -> product IDs / base plans / offers
- `PlayBillingClientAdapter.kt` -> Play Billing wrapper only
- `SubscriptionRepository.kt` -> client orchestration, purchase sync, XCPro entitlement cache
- `ObserveEntitlementsUseCase.kt` -> UI read path for XCPro plan state
- `ObserveAccessContextUseCase.kt` -> combined read path when screens need both plan state and provider-linked state
- `RefreshEntitlementsUseCase.kt` -> explicit refresh trigger
- `PurchaseSubscriptionUseCase.kt` -> purchase flow orchestration
- `RestorePurchasesUseCase.kt` -> restore/resync orchestration
- `OpenManageSubscriptionUseCase.kt` -> Play management deep link orchestration
- `BillingViewModel.kt` -> screen and top-level billing state
- `UpgradeScreen.kt` -> paywall UI

## Data flow

```text
XCPro auth session
    -> root auth gate
    -> signed-in normal app flow

Google Play BillingClient
    -> PlayBillingClientAdapter
    -> purchase sync API client
    -> SubscriptionRepository
    -> ObserveEntitlementsUseCase
        \
         -> ObserveAccessContextUseCase
        /
SkySight auth / account-state lane
    -> SkySight integration repository
    -> ObserveAccessContextUseCase

ObserveAccessContextUseCase
    -> BillingViewModel / root state holder
    -> MainActivityScreen / AppNavGraph / feature screens
```

## Hard rules

- no anonymous production use
- no direct `BillingClient` calls in composables
- no feature-access calculations hidden in random screens
- no duplicated entitlement state in multiple ViewModels
- no price formatting logic mixed with entitlement enforcement
- local DataStore can cache, but cannot become the source of truth
- do not let `:core:billing` absorb unrelated SkySight or PureTrack networking ownership just because they affect gating
- provider-linked access checks must still go through the same central policy seam
- do not launch purchase flow without a signed-in XCPro account
- do not use cleartext email for Play identity fields; use obfuscated account identifiers
- do not silently attach a Play purchase to a different signed-in XCPro account

## Root flow requirement

The app must establish authenticated XCPro identity before normal feature access.

Recommended root-state order:

1. resolve auth session
2. if signed out -> route to sign-in / create-account flow
3. if signed in -> fetch / observe entitlement snapshot
4. if signed in + no active paid subscription -> treat as `FREE`
5. if signed in + active paid subscription -> treat as paid tier

## Authority rule

If any old Kotlin sample conflicts with this file, this file wins.
