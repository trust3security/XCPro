# XCPro Subscription Kit

This folder is a **working implementation kit** for introducing subscriptions into XCPro without ad hoc logic, scope churn, or architecture drift.

## Goal

Add a clean, industry-standard monetization system for these tiers:

- Free
- Soar
- XC
- Pro

The app should use **runtime entitlements**, not separate APK flavors for each paid tier.

## What this kit contains

- product catalog and entitlement model
- recommended feature matrix
- Play Console setup notes
- backend verification and RTDN plan
- Android client architecture plan
- XCPro-specific file targets
- phased rollout plan
- test gates and drift guardrails
- ready-to-paste Codex briefs
- starter templates and Kotlin samples

## Recommended execution order

1. Read `00_START_HERE.md`
2. Paste `BRIEF_01_CODEX_PLAN_ONLY.md` into Codex
3. Review and approve the change plan
4. Adjust `02_RECOMMENDED_FEATURE_MATRIX.md` and `templates/feature_matrix.csv` if needed
5. Paste `BRIEF_02_CODEX_IMPLEMENTATION.md` into Codex
6. After implementation, paste `BRIEF_03_CODEX_POST_IMPLEMENTATION_AUDIT.md` into Codex
7. Do not merge until all required verification gates pass

## Design stance

- Free is the default state, not a paid product
- Soar, XC, and Pro are paid subscription entitlements
- Backend is authoritative for entitlements
- Client cache is allowed, client authority is not
- Billing logic belongs in a dedicated layer
- Feature access is controlled by capability checks, not scattered tier-name checks
- The smallest safe slice wins over a huge first patch

## Non-negotiables

- no UI-only premium unlocks
- no hardcoded price strings in production logic
- no business logic in composables
- no hidden global mutable state
- no surprise refactors unrelated to subscriptions
- no TODO-based “finish later” logic in production paths
