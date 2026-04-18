# XCPro Subscriptions Kit

This folder is the **authoritative Markdown implementation kit** for introducing subscriptions into XCPro without ad hoc logic, scope churn, or architecture drift.

## Goal

Add a clean, industry-standard monetization system for these tiers:

- Free
- Basic
- Soaring
- XC
- Pro

The app should use **runtime entitlements**, not separate APK flavors for each paid tier.

## Locked commercial and identity decisions

- User-facing tiers:
  - Free
  - Basic
  - Soaring
  - XC
  - Pro
- Paid Google Play subscription products:
  - `xcpro_basic`
  - `xcpro_soaring`
  - `xcpro_xc`
  - `xcpro_pro`
- Base plans:
  - `monthly`
  - `annual`
- Free is **not** a Play subscription product.
- Free is the signed-in XCPro account state with **no active paid subscription**.
- **Every user must have an XCPro account, including Free users.**
- The XCPro account identity is **not** the Google Play account identity.
- The backend is authoritative for entitlements.

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
- the working contract / phased implementation plan

## Recommended execution order

1. Put these files in `SUBSCRIPTIONS/` at the repo root.
2. Read `00_START_HERE.md`.
3. Read `XCPro_Subscription_Change_Plan_v1.md`.
4. Paste `BRIEF_01_CODEX_PLAN_ONLY.md` into Codex.
5. Review and approve the plan output.
6. Paste `BRIEF_02_CODEX_IMPLEMENTATION.md` into Codex.
7. After implementation, paste `BRIEF_03_CODEX_POST_IMPLEMENTATION_AUDIT.md` into Codex.
8. Do not merge until the required verification gates pass.

## Design stance

- Free is the default entitlement state for a **signed-in** XCPro user, not a guest mode.
- Basic, Soaring, XC, and Pro are paid subscription entitlements.
- Backend is authoritative for XCPro entitlements.
- Client cache is allowed, client authority is not.
- Billing logic belongs in a dedicated layer.
- Feature access is controlled by capability checks, not scattered tier-name checks.
- SkySight premium is a second access lane, not an XCPro tier:
  - XCPro tier decides whether SkySight integration surfaces are allowed.
  - Linked SkySight account state decides whether premium SkySight-backed features actually unlock.
- The smallest safe slice wins over a huge first patch.

## Non-negotiables

- no anonymous production use
- no UI-only premium unlocks
- no hardcoded price strings in production logic
- no business logic in composables
- no hidden global mutable state
- no surprise refactors unrelated to subscriptions
- no TODO-based "finish later" logic in production paths
- no treating linked paid SkySight as if it were an XCPro purchase
- no silent attachment of a Play purchase to the wrong XCPro account

## Authority rule for this folder

If any old non-Markdown template, sample, JSON, CSV, or YAML file conflicts with the Markdown files in `SUBSCRIPTIONS/`, **the Markdown files in this folder win**.
Use old non-Markdown files only as optional reference material.
