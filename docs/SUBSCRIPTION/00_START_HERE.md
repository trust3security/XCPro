# Start Here

## Current XCPro assumptions this kit is built around

- The repo is modular and already split into `:app`, `:core:*`, and `:feature:*` modules.
- The app uses Hilt for DI.
- `MainActivityScreen` already collects root screen state before handing off to `AppNavGraph`.
- The app currently uses a placeholder-style Android application ID and should not finalize Play Billing until the real package name is locked.
- The repo already has a strict autonomous-agent workflow. Plan first, then implement, then verify, then audit.

## The first hard decisions to lock before coding

1. Final Android package name for production
2. Whether paid plans are monthly only, monthly + annual, or monthly + annual + trial
3. Which exact features belong to Soar, XC, and Pro
4. What backend account identity owns the purchase
5. Whether you are building your own backend verification layer or using a third-party subscription platform
6. What happens when a user is offline and the cached entitlement is stale
7. What support policy applies to refunds, plan changes, and restore failures

## What a professional implementation does first

1. Lock the product model
2. Lock the entitlement source of truth
3. Lock the feature matrix
4. Create the change plan
5. Implement the smallest end-to-end slice
6. Run verification gates
7. Run a drift audit
8. Only then widen the rollout

## What not to do

- Do not make four separate builds for four price tiers
- Do not bury `if (tier == PRO)` checks across random composables
- Do not trust local client flags as the final entitlement authority
- Do not hardcode prices or price labels in code
- Do not start by editing ten unrelated features at once
- Do not change module boundaries casually

## Suggested first implementation slice

1. Introduce plan and feature models
2. Introduce a billing/catalog layer
3. Introduce an entitlement repository with backend authority and local cache
4. Add a single upgrade/paywall entry point
5. Gate 1-2 representative premium features only
6. Test upgrade, restore, expired, and downgrade flows
7. Expand gating only after the first slice is stable
