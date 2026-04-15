# Start Here

## Current XCPro assumptions this kit is built around

- The repo is modular and already split into `:app`, `:core:*`, and `:feature:*` modules.
- The app uses Hilt for DI.
- `MainActivityScreen` already collects root screen state before handing off to `AppNavGraph`.
- The app currently uses a placeholder-style Android application ID and should not finalize Play Billing until the real package name is locked.
- The repo already has a strict autonomous-agent workflow. Plan first, then implement, then verify, then audit.

## Product decisions locked so far

- User-facing tiers are:
  - Free
  - Basic
  - Soaring
  - XC
  - Pro
- Free is the default state and is **not** a Play subscription product.
- Paid tiers are separate subscription products:
  - `xcpro_basic`
  - `xcpro_soaring`
  - `xcpro_xc`
  - `xcpro_pro`
- Base plan IDs are:
  - `monthly`
  - `annual`
- Basic is intended to be the low-friction entry tier, with a store-side target of about **USD 5.99**. That is a Play Console pricing decision, not app logic.
- SkySight premium access is **not** an XCPro tier by itself. XCPro tier decides whether SkySight integration surfaces are available; linked SkySight account state decides whether premium SkySight-backed features are actually unlocked.

## The first hard decisions still to lock before coding

1. Final Android package name for production
2. Whether paid plans are monthly only, monthly + annual, or monthly + annual + trial
3. The final XC-only bundle, if XC is kept as a launch tier
4. What backend account identity owns the purchase
5. Whether you are building your own backend verification layer or using a third-party subscription platform
6. What happens when a user is offline and the cached entitlement is stale
7. What support policy applies to refunds, plan changes, restore failures, and linked-provider failures

## What a professional implementation does first

1. Lock the product model
2. Lock the entitlement source of truth
3. Lock the feature matrix
4. Lock the external-provider access rules
5. Create the change plan
6. Implement the smallest end-to-end slice
7. Run verification gates
8. Run a drift audit
9. Only then widen the rollout

## What not to do

- Do not make five separate builds for five price tiers
- Do not bury `if (tier == PRO)` checks across random composables
- Do not trust local client flags as the final entitlement authority
- Do not hardcode prices or price labels in code
- Do not treat linked SkySight premium as equivalent to an XCPro purchase
- Do not start by editing ten unrelated features at once
- Do not change module boundaries casually

## Suggested first implementation slice

1. Introduce plan and capability models
2. Introduce a billing/catalog layer
3. Introduce an entitlement repository with backend authority and local cache
4. Add a single upgrade/paywall entry point
5. Gate a small representative set of features only:
   - Distance Circles (`Basic`)
   - Add Task (`Soaring`)
   - SkySight credential entry (`Soaring+`)
   - one premium SkySight-backed surface using the dual gate
6. Test upgrade, restore, expiry, downgrade, provider link, and provider account-state transitions
7. Expand gating only after the first slice is stable
