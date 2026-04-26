# Start Here

## Current XCPro assumptions this kit is built around

- The repo is modular and already split into `:app`, `:core:*`, and `:feature:*` modules.
- The app uses Hilt for DI.
- `MainActivityScreen` already collects root screen state before handing off to `AppNavGraph`.
- The app currently appears to use a placeholder-style Android application ID and should not finalize Play Billing until the real package name is locked.
- The repo already has a strict autonomous-agent workflow. Plan first, then implement, then verify, then audit.

## Locked product and identity decisions

- User-facing tiers are:
  - Free
  - Basic
  - Soaring
  - XC
  - Pro
- Free is the default entitlement state and is **not** a Play subscription product.
- Paid tiers are separate subscription products:
  - `xcpro_basic`
  - `xcpro_soaring`
  - `xcpro_xc`
  - `xcpro_pro`
- Base plan IDs are:
  - `monthly`
  - `annual`
- Basic is intended to be the low-friction entry tier, with a store-side target of about **USD 5.99**. That is a Play Console pricing decision, not app logic.
- SkySight premium access is **not** an XCPro tier by itself. Free and Basic may see free/public SkySight overlays only. SkySight credential entry/account linking is available only to Soaring, XC, and Pro. Premium SkySight-backed features require both Soaring-or-higher XCPro entitlement and a linked paid SkySight account.
- **Every XCPro user must have an XCPro account, including Free users.**
- Free is **not** guest or anonymous use.
- The Google Play account is **not** the XCPro account.
- Subscription entitlement always attaches to the signed-in XCPro account.

## Remaining implementation decisions that still matter

1. Final Android package name for production
2. Whether the existing repo already has a production-ready email/password auth lane or needs hardening
3. Exact backend verification implementation details and infrastructure ownership
4. Exact support policy for refunds, plan changes, restore failures, and linked-provider failures
5. Exact stale-cache behavior when the device is offline

## What a professional implementation does first

1. Lock the product model
2. Lock the account and identity contract
3. Lock the entitlement source of truth
4. Lock the feature matrix
5. Lock the external-provider access rules
6. Create the change plan
7. Implement the smallest end-to-end slice
8. Run verification gates
9. Run a drift audit
10. Only then widen the rollout

## What not to do

- Do not make five separate builds for five price tiers.
- Do not permit guest use "just for Free users".
- Do not bury `if (tier == PRO)` checks across random composables.
- Do not trust local client flags as the final entitlement authority.
- Do not hardcode prices or price labels in code.
- Do not treat linked SkySight premium as equivalent to an XCPro purchase.
- Do not start by editing ten unrelated features at once.
- Do not change module boundaries casually.
- Do not pass cleartext email into Play Billing identity fields.

## Suggested first implementation slice

1. Introduce plan, billing-period, and capability models.
2. Introduce a billing/catalog layer.
3. Introduce an entitlement repository with backend authority and local cache.
4. Enforce the "account required for all tiers" rule at the root flow.
5. Add a single upgrade/paywall entry point.
6. Gate a small representative set of features only:
   - Distance Circles (`Basic`)
   - Add Task (`Soaring`)
   - SkySight credential entry (`Soaring+`, never Free or Basic)
   - one premium SkySight-backed surface using the dual gate
7. Test sign-in, upgrade, restore, expiry, downgrade, provider link, and provider account-state transitions.
8. Expand gating only after the first slice is stable.

## Folder authority rule

The Markdown files in `SUBSCRIPTIONS/` are authoritative.
If any old non-Markdown template, sample, JSON, CSV, or YAML file conflicts with these Markdown files, ignore the older non-Markdown file.
